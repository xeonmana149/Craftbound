package craftbound.game;

import craftbound.card.CardDefinition;
import craftbound.card.Cards;
import craftbound.card.CardCatalog;
import craftbound.card.Keyword;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;

// The rules of a Craftbound match, from the Mobdeck prototype. Runs on the server; players only send requests.
// - Each hero starts with 30 health. Bring the other hero to 0 to win.
// - A coin toss decides who goes first. The first player starts with 4 cards, the second with 5. Both can
//   swap any of them once (the mulligan), then the second player gets a Bottle o' Enchanting.
// - Your XP max grows by 1 each turn (up to 10) and refills. Cards cost XP. The first player skips their first
//   draw. Drawing from an empty deck causes Hunger: 1 damage, then 2, then 3...
// - Up to 7 mobs each on the board and 10 cards in hand.
// - Mobs can attack the turn after they arrive (Sprint: board targets immediately, heroes next turn). A mob that's attacked hits back,
//   unless the attacker is Ranged or the mob is a Block. Enemy mobs with Shield must be attacked first.
// - Your class power costs 2 XP, once per turn. A hero holding a tool can attack once per turn with it.
// Everything that happens is also recorded as a MatchEvent so the screen can animate it.
public class Match {
    public static final int START_HEALTH = 30;
    public static final int MAX_XP = 10;
    public static final int MAX_MOBS = 7;
    public static final int MAX_BLOCKS = 3;

    public static int rowLimit(boolean block) {
        return block ? MAX_BLOCKS : MAX_MOBS;
    }
    public static final int MAX_HAND = 10;
    public static final int POWER_COST = 2;
    // The id used for a hero when it attacks or is hit (mobs are numbered from 1).
    public static final int HERO = 0;

    public enum Phase { MULLIGAN, PLAYING }

    public final MatchPlayer[] players;
    public Phase phase = Phase.MULLIGAN;
    public final boolean[] mulliganDone;
    public int current;
    craftbound.board.BoardDesign boardDesign = craftbound.board.BoardDesign.CLASSIC;
    String trialsBattle;
    public int turn = 0;
    public static final int TURN_TICKS = 75 * 20;
    public int turnTicksRemaining = TURN_TICKS;
    public final int[] pauseRequestedTurn = {-1,-1};
    /** A completed pause starts a fresh consent cycle without giving either player extra turn time. */
    public void resumeFromPause() {
        java.util.Arrays.fill(pauseRequestedTurn, -1);
    }
    public int turnLimitTicks() { return TURN_TICKS; }
    public int turnWarningTicks() { return 15 * 20; }
    public boolean timed() { return !players[0].isAi() && !players[1].isAi(); }
    /** Called only while this table is active and no pause vote is pending. */
    public boolean tickTurnClock() {
        // The turn clock waits while a hand pick is open; MatchManager times the pick itself.
        if(!timed() || over || phase!=Phase.PLAYING || !pendingChoices.isEmpty())return false;
        if(--turnTicksRemaining>0)return false;
        turnTicksRemaining=0;
        publicNotice(player(current).name+" ran out of time.");
        endTurn(current);
        return true;
    }
    public boolean over = false;
    // 0 or 1, or -1 for a draw.
    public int winner = -1;
    public int concededSide = -1;
    // The battle log. Each line can carry the card it's about, for its icon; turn headers mark each new turn.
    public record LogEntry(String text, Identifier card, int round, String turnOf) {
    }

    public final List<LogEntry> log = new ArrayList<>();
    public final List<MatchEvent> events = new ArrayList<>();
    // Multiplayer history is assembled only from public event kinds, never private draw/give/burn logs.
    public final List<String> publicBattleLog = new ArrayList<>();
    // Public board/hero origin only; no private hand identity is included.
    Target animationSource = Target.NONE;
    final RandomSource random;
    private int nextMinionId = 1;

    // Restores the exact turn without shuffling, dealing, or firing any effects.
    Match(MatchPlayer[] restored, Phase phase, boolean[] mulligan, int current, int turn,
          int nextId, long seed, List<LogEntry> history) {
        this.players = restored.clone(); this.mulliganDone = mulligan.clone();
        this.random = RandomSource.create(seed);
        
        
        this.phase = phase; this.current = current; this.turn = turn; this.nextMinionId = nextId;
        this.log.addAll(history);
    }
    int nextMinionId() { return nextMinionId; }

    public Match(MatchPlayer first, MatchPlayer second, RandomSource random) {
        this.players = new MatchPlayer[2]; this.mulliganDone = new boolean[2];
        // Own the generator: pausing must not reseed a world entity's shared RNG.
        this.random = RandomSource.create(random.nextLong());
        random = this.random;
        // A coin flip decides who goes first: they sit in slot 0.
        boolean swap = random.nextBoolean();
        this.players[0] = swap ? second : first;
        this.players[1] = swap ? first : second;
        for (MatchPlayer player : this.players) {
            Collections.shuffle(player.deck, new java.util.Random(random.nextLong()));
        }
        // Opening hands, which each player can swap cards out of before the game begins.
        for (int i = 0; i < 4; i++) deal(0);
        for (int i = 0; i < 5; i++) deal(1);
        for (MatchPlayer participant : players) {
            if (participant.trialsFoe != null) TrialsOpponentRule.reserveBossOpener(participant);
        }
        log(this.players[0].name + " wins the coin toss and goes first.");
        event(MatchEvent.Type.COIN, 0);
        this.current = 0;
    }

    public MatchPlayer player(int side) {
        return this.players[side];
    }

    public MatchPlayer opponent(int side) {
        return this.players[opponentSide(side)];
    }

    int opponentSide(int side) { return 1 - side; }
    boolean isEnemy(int owner, int candidate) { return owner != candidate; }
    boolean isTurnSeat(int side) { return side == current; }
    int healthPool(int side) { return side; }
    void heroHealthChanged(int side) {}
    boolean inPlay(int side) { return side >= 0 && side < players.length; }
    int nextSeat(int side) { return (side + 1) % players.length; }
    int baseHealth() { return START_HEALTH; }
    void onRevival(int side, int oldId, int newId) {}
    void combatHit(int side, int attackerId, int victim, int amount) {}
    boolean commandUnit(int side, int mid) { return false; }

    public int sideOf(java.util.UUID playerId) {
        for (int side = 0; side < players.length; side++) {
            if (playerId.equals(this.players[side].playerId)) {
                return side;
            }
        }
        return -1;
    }

    // Which side's board a mob is on, or -1 if it's gone.
    int sideOf(Minion minion) {
        for (int side = 0; side < players.length; side++) {
            if (this.players[side].board.contains(minion)) {
                return side;
            }
        }
        return -1;
    }

    int effectDepth, effectSteps;
    void effectLoop() {
        if (!this.over) log("The match ends in a draw: a repeating effect exceeded the resolution limit.");
        this.over = true;
        this.winner = -1;
    }

    /** Explicitly public match announcements. Other log lines can contain private hand information. */
    void publicNotice(String line) { log(line); }

    void log(String line) {
        log(line, (Identifier) null);
    }

    void log(String line, CardRef card) {
        log(line, card == null ? null : card.id());
    }

    void log(String line, Identifier card) {
        this.log.add(new LogEntry(line, card, 0, null));
        if (this.log.size() > 60) {
            this.log.remove(0);
        }
    }

    void event(MatchEvent.Type type, int side) {
        event(type, side, HERO, null, 0, 0, null);
    }

    void event(MatchEvent.Type type, int side, int mid, CardRef card, int amount, int extra, Target target) {
        this.events.add(new MatchEvent(type, side, mid, card, amount, extra, target, this.animationSource));
        if (players.length > 2 && side >= 0 && side < players.length) {
            String who = player(side).name;
            String line = switch (type) {
                case TURN -> who + " begins a turn.";
                case PLAY -> card == null ? null : who + " plays " + name(card) + ".";
                case POWER -> who + " uses " + HeroPowers.of(player(side)).displayName().getString() + ".";
                case ATTACK -> target == null || target.isNone() ? null
                        : who + " attacks " + player(target.side()).name + "'s " + (target.hero() ? "hero." : "board.");
                case DEATH -> card == null ? null : who + "'s " + name(card) + " is destroyed.";
                case BOUNCE -> card == null ? null : name(card) + " returns to its owner's hand.";
                default -> null;
            };
            if (line != null) {
                publicBattleLog.add(line);
                if (publicBattleLog.size() > 32) publicBattleLog.removeFirst();
            }
        }
    }

    // --- Mulligan ---------------------------------------------------------------------------------------

    // A card for the opening hand. No event: the mulligan screen shows the whole hand at once.
    void deal(int side) {
        MatchPlayer player = this.players[side];
        if (!player.deck.isEmpty()) {
            player.hand.add(player.deck.remove(0));
        }
    }

    // Swaps the cards whose bits are set in `mask` (bit 0 = first card) for new ones from the deck.
    public void mulligan(int side, int mask) {
        if (this.phase != Phase.MULLIGAN || this.mulliganDone[side]) {
            return;
        }
        MatchPlayer player = this.players[side];
        List<CardRef> swappedOut = new ArrayList<>();
        int swapped = 0;
        for (int i = 0; i < player.hand.size(); i++) {
            if ((mask & (1 << i)) != 0 && !player.deck.isEmpty()) {
                swappedOut.add(player.hand.get(i));
                player.hand.set(i, player.deck.remove(0));
                swapped |= 1 << i;
            }
        }
        // The old cards go back into the deck at random places, after the new ones are drawn, so you can't
        // get the same card straight back.
        for (CardRef card : swappedOut) {
            player.deck.add(this.random.nextInt(player.deck.size() + 1), card);
        }
        this.mulliganDone[side] = true;
        log(swappedOut.isEmpty() ? player.name + " keeps their starting hand."
                : player.name + " swaps " + swappedOut.size() + (swappedOut.size() == 1 ? " card." : " cards."));
        event(MatchEvent.Type.MULLIGAN, side, HERO, null, swapped, 0, null);
        if (this.mulliganDone[0] && this.mulliganDone[1]) {
            begin();
        }
    }

    private void begin() {
        this.phase = Phase.PLAYING;
        event(MatchEvent.Type.BEGIN, 0);
        // Going second is a disadvantage, so that player gets a Bottle o' Enchanting: 1 extra XP, once.
        log(this.players[1].name + " goes second and gets a Bottle o' Enchanting.", Cards.EXPERIENCE_BOTTLE);
        giveCard(1, new CardRef(Cards.EXPERIENCE_BOTTLE, false));
        this.current = 0;
        startTurn();
    }

    // --- Turns ------------------------------------------------------------------------------------------

    void startTurn() {
        this.turn++;
        this.turnTicksRemaining=turnLimitTicks();
        // A header in the battle log: "TURN 2 - NAME", counting each pair of turns as one round.
        this.log.add(new LogEntry("", null, (this.turn + players.length - 1) / players.length, player(current).name));
        beginTurnFor(current, turn > 1);
    }

    /** Refresh one participant and resolve their own turn triggers. A team calls this once per seat. */
    void beginTurnFor(int side, boolean drawsCard) {
        if (over) return;
        MatchPlayer player = player(side);
        for (Minion mob : new ArrayList<>(player.board)) {
            if (!mob.isDead()) CardEffects.run(this, side, mob.card, mob, CardCatalog.Trigger.START_TURN, Target.NONE);
        }
        resolveDeaths();
        if (this.over) return;
        player.maxXp = Math.min(MAX_XP, player.maxXp + 1);
        player.xp = player.maxXp;
        player.heroAttacked = false;
        player.skippedAttackFromFreeze = player.frozen;
        player.frozen = false;
        player.powerUsed = false;
        for (Minion minion : player.board) {
            minion.summonedThisTurn = false;
            minion.invulnerable = false;
            // A frozen mob skips this turn's attacks, then thaws.
            minion.skippedAttackFromFreeze = minion.frozen;
            minion.attackedThisTurn = false;
            minion.ready = !minion.frozen;
            minion.frozen = false;
        }
        event(MatchEvent.Type.TURN, side, HERO, null, player.xp, player.maxXp, null);
        // Withered mobs take 1 damage.
        for (Minion minion : new ArrayList<>(player.board)) {
            if (minion.withered) {
                damage(minion, 1, null);
            }
        }
        resolveDeaths();
        if (!over && player.trialsFoe != null) TrialsOpponentRule.start(this, side);
        if (drawsCard && !over) draw(side);
        checkWinner();
    }

    void passTurn() { this.current = nextSeat(this.current); startTurn(); }

    public void endTurn(int side) {
        if (this.over || this.phase != Phase.PLAYING || side != this.current || !this.pendingChoices.isEmpty()) {
            return;
        }
        endTurnFor(side);
        clearTurnAttackBonuses();
        resolveDeaths();
        checkWinner();
        if (!this.over && current == side) passTurn();
    }

    void endTurnFor(int side) {
        if (over) return;
        CardEffects.onEndTurn(this, side);
        this.players[side].skippedAttackFromFreeze = false;
        this.players[side].temporaryHeroAttack = 0;
        event(MatchEvent.Type.HERO_ATTACK, side, HERO, null, heroAttackOf(side), 0, null);
        for (Minion mob : this.players[side].board) expireWeakness(mob);
        for (Minion minion : this.players[side].board) minion.skippedAttackFromFreeze = false;
    }

    void clearTurnAttackBonuses() {
        for (int owner = 0; owner < players.length; owner++) for (Minion minion : player(owner).board) {
            if (minion.temporaryAttack != 0) {
                int before = attackOf(minion);
                minion.attack = Math.max(0, minion.attack - minion.temporaryAttack);
                event(MatchEvent.Type.BUFF, owner, minion.id, minion.card, attackOf(minion) - before, 0, null);
                minion.temporaryAttack = 0;
            }
        }
    }

    // Retired action: keep old callers inert without changing saved trigger or packet IDs.
    public void dismantle(int side, int minionId) { }

    public void concede(int side) {
        if (this.over) {
            return;
        }
        this.concededSide = side;
        log(this.players[side].name + " conceded.");
        this.over = true;
        this.winner = 1 - side;
    }

    // --- Cards ------------------------------------------------------------------------------------------

    void draw(int side) {
        MatchPlayer player = this.players[side];
        if (player.deck.isEmpty()) {
            player.hunger++;
            log(player.name + " is out of cards and takes " + player.hunger + " Hunger damage.", Cards.ROTTEN_FLESH);
            event(MatchEvent.Type.HUNGER, side, HERO, null, player.hunger, 0, null);
            damageHero(side, player.hunger);
            return;
        }
        drawFromDeck(side, 0);
    }

    /** Draw an actual deck entry, including its cosmetic variant and persistent upgrades. */
    private void drawFromDeck(int side, int index) {
        MatchPlayer player = player(side);
        if (index < 0 || index >= player.deck.size()) return;
        CardRef card = player.deck.remove(index);
        if (player.hand.size() >= MAX_HAND) {
            log(player.name + "'s hand is full, so " + name(card) + " is lost.", card);
            event(MatchEvent.Type.BURN, side, HERO, card, 0, 0, null);
            return;
        }
        player.hand.add(card);
        event(MatchEvent.Type.DRAW, side, HERO, card, 0, 0, null);
        CardEffects.notifyEvent(this, CardCatalog.Trigger.CARD_ADDED, side, card, Target.NONE);
    }

    // Gives a card straight to someone's hand (e.g. a Drop). Lost if their hand is full.
    void giveCard(int side, CardRef card) {
        MatchPlayer player = this.players[side];
        if (player.hand.size() >= MAX_HAND) {
            log(player.name + "'s hand is full, so " + name(card) + " is lost.", card);
            event(MatchEvent.Type.BURN, side, HERO, card, 0, 0, null);
            return;
        }
        player.hand.add(card);
        event(MatchEvent.Type.GIVE, side, HERO, card, 0, 0, null);
        CardEffects.notifyEvent(this, CardCatalog.Trigger.CARD_ADDED, side, card, Target.NONE);
    }

    boolean yourMove(int side) {
        // While someone picks a card from a hand, nobody else may act.
        return inPlay(side) && !this.over && this.phase == Phase.PLAYING && isTurnSeat(side) && this.pendingChoices.isEmpty();
    }

    // --- Hand effects: reveal, discard, steal, shuffle back --------------------------------------------

    /** A card someone must pick from a hand. owner controls the effect; the card leaves victim's hand. */
    public record HandChoice(CardCatalog.Action action, int owner, int victim, int chooser, int count, CardRef source, List<CardRef> offers) {
        public HandChoice(CardCatalog.Action action, int owner, int victim, int chooser, int count, CardRef source) {
            this(action, owner, victim, chooser, count, source, List.of());
        }
        public HandChoice { offers = List.copyOf(offers); }
        boolean fromDeck() { return action == CardCatalog.Action.CHOOSE_FROM_DECK; }
    }
    // Picks wait here in order. Each is resolved by chooseFromHand, or at random when CHOICE_TICKS run out.
    final java.util.ArrayDeque<HandChoice> pendingChoices = new java.util.ArrayDeque<>();
    public static final int CHOICE_TICKS = 30 * 20;
    public int choiceTicksRemaining;

    public HandChoice pendingChoice() { return this.pendingChoices.peekFirst(); }

    void pruneChoices() {
        HandChoice first = pendingChoice();
        pendingChoices.removeIf(c -> over || !inPlay(c.chooser()) || !inPlay(c.victim()) || choiceCards(c).isEmpty());
        if (pendingChoice() != first) choiceTicksRemaining = CHOICE_TICKS;
    }

    /** Two-seat matches have a picker on the match screen; team tables resolve picks at random. */
    boolean interactiveChoices() { return players.length == 2; }

    /** Build distinct-ID offers without changing the deck or exposing unchosen cards to other players. */
    void chooseFromDeck(int side, CardCatalog.Effect effect, CardRef source) {
        if (over || !inPlay(side)) return;
        var shuffled = new ArrayList<>(player(side).deck);
        for (int i = shuffled.size() - 1; i > 0; i--) java.util.Collections.swap(shuffled, i, random.nextInt(i + 1));
        var offers = new ArrayList<CardRef>();
        var seen = new java.util.HashSet<Identifier>();
        for (CardRef ref : shuffled) {
            var card = Cards.get(ref.id());
            if (card == null || !card.isMob() || !CardEffects.matches(effect, card) || !seen.add(ref.id())) continue;
            offers.add(ref);
            if (offers.size() >= effect.amount()) break;
        }
        if (offers.isEmpty()) return; // Searching an empty/unsuitable deck never causes Hunger.
        if (player(side).isAi()) {
            CardRef best = offers.stream().max(java.util.Comparator.comparingInt(ref -> {
                var card = Cards.get(ref.id());
                return (card.cost() <= player(side).xp ? 100 : 0) + card.cost();
            })).orElseThrow();
            drawFromDeck(side, player(side).deck.indexOf(best));
        } else {
            if (pendingChoices.isEmpty()) choiceTicksRemaining = CHOICE_TICKS;
            pendingChoices.addLast(new HandChoice(CardCatalog.Action.CHOOSE_FROM_DECK, side, side, side, 1, source, offers));
        }
    }

    /** Only the chooser receives this list. Stale offers are omitted if another effect drew them first. */
    public List<CardRef> choiceCards(HandChoice choice) {
        return choice.fromDeck() ? choice.offers().stream().filter(player(choice.victim()).deck::contains).toList()
                : player(choice.victim()).hand;
    }

    void handEffect(CardCatalog.Action action, int owner, int victim, int count, CardCatalog.HandChooser who, CardRef source) {
        if (over || !inPlay(victim)) return;
        MatchPlayer target = player(victim);
        if (action == CardCatalog.Action.REVEAL_HAND) {
            log(player(owner).name + " looks at " + target.name + "'s hand.", source);
            if (target.hand.isEmpty()) event(MatchEvent.Type.HAND_REVEAL, owner, HERO, null, -1, 0, Target.hero(victim));
            for (int i = 0; i < target.hand.size(); i++)
                event(MatchEvent.Type.HAND_REVEAL, owner, HERO, target.hand.get(i), i, target.hand.size(), Target.hero(victim));
            return;
        }
        if (target.hand.isEmpty()) return;
        int picks = Math.min(count, target.hand.size());
        int chooser = who == CardCatalog.HandChooser.YOU ? owner : who == CardCatalog.HandChooser.OPPONENT ? victim : -1;
        if (chooser >= 0 && interactiveChoices() && !player(chooser).isAi()) {
            if (this.pendingChoices.isEmpty()) this.choiceTicksRemaining = CHOICE_TICKS;
            this.pendingChoices.addLast(new HandChoice(action, owner, victim, chooser, picks, source));
            return;
        }
        for (int i = 0; i < picks && !target.hand.isEmpty() && !over; i++) {
            int index = chooser < 0 ? this.random.nextInt(target.hand.size())
                    : MatchAI.pickFromHand(this, action, owner, victim, chooser);
            moveFromHand(action, owner, victim, index, source);
        }
    }

    // Takes the card at index out of victim's hand and discards, steals or shuffles it.
    void moveFromHand(CardCatalog.Action action, int owner, int victim, int index, CardRef source) {
        MatchPlayer from = player(victim);
        if (index < 0 || index >= from.hand.size()) return;
        CardRef card = from.hand.remove(index);
        switch (action) {
            case DISCARD_FROM_HAND -> {
                log(from.name + " discards " + name(card) + ".", card);
                event(MatchEvent.Type.HAND_DISCARD, victim, HERO, card, index, owner, null);
            }
            case STEAL_FROM_HAND -> {
                MatchPlayer thief = player(owner);
                boolean kept = thief.hand.size() < MAX_HAND;
                // The log is public, so it names the effect, not the stolen card.
                log(thief.name + " steals a card from " + from.name + "'s hand" + (kept ? "." : ", but their hand is full."), source);
                event(MatchEvent.Type.HAND_STEAL, owner, HERO, card, index, kept ? 1 : 0, Target.hero(victim));
                if (kept) {
                    thief.hand.add(card);
                    CardEffects.notifyEvent(this, CardCatalog.Trigger.CARD_ADDED, owner, card, Target.NONE);
                }
            }
            case SHUFFLE_FROM_HAND -> {
                log(from.name + " shuffles a card from their hand into their deck.", source);
                from.deck.add(this.random.nextInt(from.deck.size() + 1), card);
                event(MatchEvent.Type.HAND_SHUFFLE, victim, HERO, card, index, owner, null);
            }
            default -> from.hand.add(index, card);
        }
    }

    /** The chooser picks the card at index of the victim's hand. Returns false if the pick isn't allowed. */
    public boolean chooseFromHand(int side, int index) {
        HandChoice choice = pendingChoice();
        if (choice == null || over || side != choice.chooser()) return false;
        if (index < 0 || index >= choiceCards(choice).size()) return false;
        resolveChoice(choice, index);
        return true;
    }

    /** Time ran out (or the match is being paused): pick at random for the waiting chooser. */
    public void resolveChoiceRandomly() {
        pruneChoices();
        HandChoice choice = pendingChoice();
        if (choice == null) return;
        var cards = choiceCards(choice);
        if (over) { this.pendingChoices.clear(); return; }
        if (cards.isEmpty()) { pendingChoices.pollFirst(); choiceTicksRemaining = CHOICE_TICKS; return; }
        resolveChoice(choice, this.random.nextInt(cards.size()));
    }

    private void resolveChoice(HandChoice choice, int index) {
        this.pendingChoices.pollFirst();
        if (choice.fromDeck()) {
            CardRef selected = choiceCards(choice).get(index);
            drawFromDeck(choice.victim(), player(choice.victim()).deck.indexOf(selected));
        } else moveFromHand(choice.action(), choice.owner(), choice.victim(), index, choice.source());
        if (!choice.fromDeck() && choice.count() > 1 && !player(choice.victim()).hand.isEmpty())
            this.pendingChoices.addFirst(new HandChoice(choice.action(), choice.owner(), choice.victim(),
                    choice.chooser(), choice.count() - 1, choice.source()));
        // A later pick from a hand that has since emptied has nothing left to take.
        pruneChoices();
        if (over) this.pendingChoices.clear();
        this.choiceTicksRemaining = CHOICE_TICKS;
        resolveDeaths();
        checkWinner();
    }

    // Why a card in hand can't be played right now, or null if it can.
    public String whyCantPlay(int side, int handIndex) {
        MatchPlayer player = this.players[side];
        if (!yourMove(side) || handIndex < 0 || handIndex >= player.hand.size()) {
            return "not your turn";
        }
        CardRef card = player.hand.get(handIndex);
        CardDefinition definition = Cards.get(card.id());
        if (definition.cost() > player.xp) {
            return "not enough XP";
        }
        if (definition.isMob()) {
            String reason = player.rowFullReason(definition.keywords().contains(Keyword.BLOCK));
            if (reason != null) return reason;
        }
        // Items aimed at a mob need a mob to aim at. (Mobs that pick a target can still be played without one.)
        Target.Kind kind = CardEffects.targetKind(card.id());
        if (!definition.isMob() && kind != Target.Kind.NONE && validTargets(side, kind, card.id()).isEmpty()) {
            return "nothing to target";
        }
        return CardEffects.whyCantPlay(this, side, card.id());
    }

    public List<Target> validTargets(int side, Target.Kind kind) {
        List<Target> targets = new ArrayList<>();
        for (int s = 0; s < players.length; s++) {
            if (!inPlay(s)) continue;
            boolean friendly = s == side;
            if ((kind == Target.Kind.FRIENDLY_MOB && !friendly) || (kind == Target.Kind.ENEMY_MOB && !isEnemy(side, s)) || kind == Target.Kind.NONE) {
                continue;
            }
            if (kind == Target.Kind.ANY_CHARACTER) targets.add(Target.hero(s));
            for (Minion minion : this.players[s].board) {
                targets.add(Target.mob(s, minion.id));
            }
        }
        return targets;
    }

    public List<Target> validTargets(int side, Target.Kind kind, Identifier card) {
        return validTargets(side, kind).stream().filter(t -> t.hero() ? CardEffects.canTargetHero(card) : CardEffects.canTarget(card, player(t.side()).minion(t.minionId()).card.id())).toList();
    }

    public void playCard(int side, int handIndex, Target target) {
        if (whyCantPlay(side, handIndex) != null) {
            return;
        }
        MatchPlayer player = this.players[side];
        CardRef card = player.hand.get(handIndex);
        CardDefinition definition = Cards.get(card.id());
        resolvePlay(side, handIndex, card, definition.cost(), target);
    }

    void resolvePlay(int side, int handIndex, CardRef card, int cost, Target target) {
        MatchPlayer player = player(side);
        CardDefinition definition = Cards.get(card.id());
        Target.Kind kind = CardEffects.targetKind(card.id());
        boolean targetOk = kind != Target.Kind.NONE && validTargets(side, kind, card.id()).contains(target);
        // Items that need a target must have a valid one.
        if (!definition.isMob() && kind != Target.Kind.NONE && !targetOk) {
            return;
        }
        Target aim = targetOk ? target : Target.NONE;
        Identifier incomingRaid = definition.isMob() && !definition.keywords().contains(Keyword.BLOCK)
                ? player.badOmenSummon : null;
        int raidSide = player.badOmenSource == null ? opponentSide(side) : player.badOmenSource;
        if (incomingRaid != null) { player.badOmenSummon = null; player.badOmenSource = null; }
        player.xp -= cost;
        if (handIndex >= 0) player.hand.remove(handIndex);
        event(MatchEvent.Type.PLAY, side, HERO, card, handIndex, 0, aim);
        Minion playedMob = null;
        if (definition.isMob()) {
            log(player.name + " summons " + name(card) + ".", card);
            Minion minion = summon(side, card);
            playedMob = minion;
            if (minion != null) {
                CardEffects.onSpawn(this, side, minion, aim);
                CardEffects.onMobPlayed(this, side, minion);
            }
        } else if (definition.isTool()) {
            equip(side, card);
            CardEffects.onItem(this, side, card, aim);
        } else {
            log(player.name + " plays " + name(card) + ".", card);
            CardEffects.onItem(this, side, card, aim);
        }
        if (definition.kind() == craftbound.card.CardKind.ITEM)
            CardEffects.notifyEvent(this, CardCatalog.Trigger.ITEM_PLAYED, side, card, aim);
        resolveDeaths();
        checkWinner();
        if (!over && incomingRaid != null && inPlay(raidSide)) {
            var raider = new CardRef(incomingRaid, false);
            boolean arrived = summon(raidSide, raider) != null;
            log(arrived ? "Bad Omen summons " + name(raider) + "." : "Bad Omen fades. The opposing board is full.", raider);
        }
        if (!over && player.trialsFoe != null) TrialsOpponentRule.afterPlay(this, side, card, playedMob);
        if (player.trialsCharms != null) TrialsCharm.afterPlay(this, side, card, playedMob);
    }

    // Puts a mob on the board. Returns null if the board is full.
    Minion summon(int side, CardRef card) {
        MatchPlayer player = this.players[side];
        if (player.rowFullReason(Cards.get(card.id()).keywords().contains(Keyword.BLOCK)) != null) {
            return null;
        }
        Minion minion = new Minion(this.nextMinionId++, card);
        player.board.add(minion);
        event(MatchEvent.Type.SUMMON, side, minion.id, card, minion.attack, minion.health, null);
        refreshAuras();
        return minion;
    }

    // Puts a tool in the hero's hand, replacing the one they held.
    void equip(int side, CardRef card) {
        MatchPlayer player = this.players[side];
        CardDefinition definition = Cards.get(card.id());
        if (player.tool != null) {
            log(player.name + "'s " + name(player.tool.card) + " is replaced.", player.tool.card);
        }
        player.tool = new Tool(card, card.training().attack(definition), definition.health(), definition.keywords().contains(Keyword.RANGED));
        log(player.name + " equips " + name(card) + ".", card);
        event(MatchEvent.Type.EQUIP, side, HERO, card, player.tool.attack, player.tool.uses, null);
    }

    // --- Class power ------------------------------------------------------------------------------------

    public String whyCantUsePower(int side) {
        if (!yourMove(side)) {
            return "not your turn";
        }
        MatchPlayer player = this.players[side];
        if (player.powerUsed) {
            return "already used this turn";
        }
        if (player.xp < POWER_COST) {
            return "not enough XP";
        }
        return HeroPowers.whyCantUse(this, side);
    }

    public void usePower(int side) { usePower(side, Target.NONE); }

    public void usePower(int side, Target target) {
        if (whyCantUsePower(side) != null) {
            return;
        }
        MatchPlayer player = this.players[side];
        if (!HeroPowers.validTarget(this, side, target)) return;
        player.xp -= POWER_COST;
        player.powerUsed = true;
        event(MatchEvent.Type.POWER, side, HERO, null, HeroPowers.of(player).ordinal(), 0, target);
        HeroPowers.use(this, side, target);
        resolveDeaths();
        checkWinner();
    }

    // --- Combat -----------------------------------------------------------------------------------------

    // A mob's attack right now: Blocks never attack, and each other Beacon its owner has adds 1.
    public int attackOf(Minion minion) {
        if (minion.has(Keyword.BLOCK)) {
            return 0;
        }
        int bonus = 0;
        int side = sideOf(minion);
        if (side >= 0) {
            for (Minion other : this.players[side].board) {
                if (other != minion && !other.isDead() && CardCatalog.entry(other.card.id()).hasAura()
                        && CardCatalog.entry(other.card.id()).auraMatches(minion.definition())) {
                    bonus += CardCatalog.passiveAmount(other.card.id());
                }
            }
        }
        return Math.max(0, minion.attack + bonus - minion.attackDebuff);
    }

    /** Reconcile continuous Health without stacking it on every refresh or firing healing effects. */
    void refreshAuras() {
        for (int side = 0; side < players.length; side++) for (Minion mob : players[side].board) {
            if (mob.isDead()) continue; // A newly summoned aura must never revive a dying mob.
            int attack = 0, health = 0;
            for (Minion source : players[side].board) {
                var entry = CardCatalog.entry(source.card.id());
                if (source == mob || source.isDead() || !entry.hasAura()
                        || !entry.auraMatches(mob.definition())) continue;
                if (!mob.has(Keyword.BLOCK)) attack += entry.passiveAmount();
                health += entry.auraHealthBonus();
            }
            if (health != mob.auraHealth) {
                int delta = health - mob.auraHealth;
                mob.maxHealth += delta;
                // Gaining a bonus grants current Health; losing one only caps it to the new maximum.
                mob.health = delta > 0 ? mob.health + delta : Math.min(mob.health, mob.maxHealth);
                mob.auraHealth = health;
                event(MatchEvent.Type.AURA_HEALTH, side, mob.id, mob.card, mob.health, mob.maxHealth, null);
            }
            if (attack != mob.auraAttack) {
                mob.auraAttack = attack;
                event(MatchEvent.Type.AURA_ATTACK, side, mob.id, mob.card, attackOf(mob), 0, null);
            }
        }
    }

    public int heroAttackOf(int side) {
        MatchPlayer p = player(side);
        return Math.max(0, (p.tool == null ? 0 : p.tool.attack) + p.temporaryHeroAttack);
    }

    public boolean heroCanAttack(int side) {
        MatchPlayer player = this.players[side];
        return yourMove(side) && !player.heroAttacked && !player.frozen && !player.skippedAttackFromFreeze && heroAttackOf(side) > 0;
    }

    // Why this mob (or the hero, attackerId == HERO) can't attack that target, or null if it can.
    public String whyCantAttack(int side, int attackerId, Target target) {
        if (!yourMove(side)) {
            return "not your turn";
        }
        if (attackerId == HERO) {
            if (player(side).frozen || player(side).skippedAttackFromFreeze) return "your hero is Frozen";
            if (!heroCanAttack(side)) {
                return "your hero can't attack right now";
            }
        } else {
            Minion attacker = this.players[side].minion(attackerId);
            if (attacker == null || !attacker.canAttack() || attackOf(attacker) <= 0) {
                return "that mob can't attack right now";
            }
            if (target.hero() && attacker.summonedThisTurn) {
                return "Sprint can only attack mobs or blocks on its first turn";
            }
        }
        if (target.isNone() || !inPlay(target.side()) || !isEnemy(side, target.side())) {
            return "pick an enemy";
        }
        MatchPlayer enemy = this.players[target.side()];
        Minion defender = target.hero() ? null : enemy.minion(target.minionId());
        if (!target.hero() && defender == null) {
            return "pick an enemy";
        }
        boolean enemyHasShield = enemy.board.stream().anyMatch(m -> m.has(Keyword.SHIELD));
        boolean ignoresShield = attackerId == HERO
                ? player(side).tool != null && Cards.get(player(side).tool.card.id()).keywords().contains(Keyword.IGNORE_SHIELD)
                : player(side).minion(attackerId).has(Keyword.IGNORE_SHIELD);
        if (!ignoresShield && enemyHasShield && (defender == null || !defender.has(Keyword.SHIELD))) {
            return "a mob with Shield must be attacked first";
        }
        if (defender != null && defender.has(Keyword.BLOCK)
                && enemy.board.stream().anyMatch(m -> !m.isDead() && !m.has(Keyword.BLOCK))) {
            boolean ranged = attackerId == HERO ? player(side).tool != null && player(side).tool.ranged
                    : player(side).minion(attackerId).has(Keyword.RANGED);
            if (!ranged) return "clear the front row or use a Ranged attack to hit this block";
        }
        return null;
    }

    public int retaliationOf(Minion defender) {
        return defender.has(Keyword.BLOCK) ? 0 : attackOf(defender);
    }

    public void attack(int side, int attackerId, Target target) {
        if (whyCantAttack(side, attackerId, target) != null) {
            return;
        }
        MatchPlayer me = this.players[side];
        MatchPlayer enemy = this.players[target.side()];
        boolean hero = attackerId == HERO;
        Minion attacker = hero ? null : me.minion(attackerId);
        Tool tool = me.tool;
        int power = hero ? heroAttackOf(side) : attackOf(attacker);
        String who = hero ? me.name : name(attacker.card);
        CardRef icon = hero ? tool == null ? null : tool.card : attacker.card;
        if (hero) {
            me.heroAttacked = true;
        } else {
            attacker.ready = false;
            attacker.attackedThisTurn = true;
        }
        event(MatchEvent.Type.ATTACK, side, attackerId, icon, power, 0, target);
        boolean killedAttackTarget = false;
        if (target.hero()) {
            log(who + " hits " + enemy.name + " for " + power + ".", icon);
            int healthBefore = enemy.health;
            damageHero(target.side(), power);
            combatHit(side, attackerId, target.side(), healthBefore - enemy.health);
            if (enemy.health < healthBefore) {
                if (hero) CardEffects.onToolTrigger(this, side, tool, CardCatalog.Trigger.DAMAGE_DEALT, target);
                else CardEffects.run(this, side, icon, attacker, CardCatalog.Trigger.DAMAGE_DEALT, target);
            }
        } else {
            Minion defender = enemy.minion(target.minionId());
            log(who + " attacks " + name(defender.card) + ".", icon);
            int hitBack = retaliationOf(defender);
            int healthBefore = defender.health;
            boolean killedByHit = damage(defender, power, attacker);
            killedAttackTarget = killedByHit;
            if (hero && defender.health < healthBefore) {
                CardEffects.onToolTrigger(this, side, tool, CardCatalog.Trigger.DAMAGE_DEALT, target);
            }
            // The Sweet Berry Bush pricks anything that attacks it.
            CardEffects.run(this, target.side(), defender.card, defender, CardCatalog.Trigger.ATTACKED,
                    hero ? Target.hero(side) : Target.mob(side, attacker.id));
            if (hitBack > 0) {
                hitAttacker(side, attacker, hitBack, defender);
            }
        }
        if (hero && killedAttackTarget) {
            // Finish the attack exchange and remove its casualty before the tool's
            // kill payoff chooses a new target. Keep the attacking tool for its last use.
            resolveDeaths();
            CardEffects.onToolTrigger(this, side, tool, CardCatalog.Trigger.ON_KILL, target);
        }
        if (hero) CardEffects.onToolTrigger(this, side, tool, CardCatalog.Trigger.AFTER_ATTACK, target);
        else CardEffects.run(this, side, icon, attacker, CardCatalog.Trigger.AFTER_ATTACK, target);
        if (hero && tool != null) {
            tool.uses--;
            if (tool.uses <= 0) {
                log(me.name + "'s " + name(tool.card) + " breaks.", tool.card);
                event(MatchEvent.Type.BREAK, side, HERO, tool.card, 0, 0, null);
                me.tool = null;
            }
        }
        resolveDeaths();
        // Wait for retaliation, thorns and Death effects. Only the original, still-present attacker
        // qualifies; a Totem revival or transformed replacement is a different mob.
        if (!hero && killedAttackTarget && !attacker.isDead() && me.board.contains(attacker) && !this.over) {
            CardEffects.run(this, side, icon, attacker, CardCatalog.Trigger.AFTER_SURVIVING_KILL, Target.NONE);
            resolveDeaths();
        }
        checkWinner();
    }

    // Damage back to whoever attacked: their mob, or their hero if the hero attacked.
    private void hitAttacker(int side, Minion attacker, int amount, Minion source) {
        if (attacker == null) {
            damageHero(side, amount);
        } else {
            damage(attacker, amount, source);
        }
    }

    // Damages a mob. `source` is the mob dealing it, if any, for effects like freezing and withering.
    // Returns whether this exact damage killed it, before any triggered damage or other effects.
    boolean damage(Minion minion, int amount, Minion source) {
        if (amount <= 0 || minion.isDead() || sideOf(minion) < 0) {
            return false;
        }
        int side = sideOf(minion);
        if (minion.invulnerable) {
            event(MatchEvent.Type.DAMAGE, side, minion.id, null, 0, 0, null);
            return false;
        }
        // The Piglin Brute's armour takes 1 off every hit.
        amount = minion.afterArmor(amount);
        if (amount <= 0) {
            event(MatchEvent.Type.DAMAGE, side, minion.id, null, 0, 0, null);
            return false;
        }
        minion.health -= amount;
        if (minion.isDead()) {
            minion.killerSide = source == null ? -1 : sideOf(source);
            minion.killerId = source == null ? -1 : source.id;
        }
        event(MatchEvent.Type.DAMAGE, side, minion.id, null, amount, 0, null);
        if (minion.isDead()) refreshAuras();
        if (!minion.isDead() && minion.lastBattleHardenedTurn != this.turn
                && CardCatalog.passive(minion.card.id(), CardCatalog.Passive.BATTLE_HARDENED)) {
            minion.lastBattleHardenedTurn = this.turn;
            buff(minion, CardCatalog.passiveAmount(minion.card.id()), 0);
        }
        boolean killed = minion.isDead();
        if (source != null) {
            CardEffects.onDamageDealt(this, source, minion);
            if (killed) CardEffects.onKill(this, source, minion);
        }
        return killed;
    }

    // Damages whatever a target points at: a hero or a mob.
    void damage(Target target, int amount) {
        if (target.hero()) {
            damageHero(target.side(), amount);
        } else {
            Minion minion = this.players[target.side()].minion(target.minionId());
            if (minion != null) {
                damage(minion, amount, null);
            }
        }
    }

    void damageHero(int side, int amount) {
        if (amount <= 0) {
            return;
        }
        this.players[side].health -= amount;
        heroHealthChanged(side);
        event(MatchEvent.Type.DAMAGE, side, HERO, null, amount, 0, null);
    }

    void healHero(int side, int amount) { heal(Target.hero(side), amount, false); }

    void heal(Target target, int amount, boolean wellFed) {
        if (amount <= 0 || target == null || target.isNone()) return;
        MatchPlayer player = player(target.side());
        Minion mob = target.hero() ? null : player.minion(target.minionId());
        if (!target.hero() && (mob == null || mob.isDead())) return;
        int health = mob == null ? player.health : mob.health;
        int max = mob == null ? player.maxHealth : mob.maxHealth;
        if (health <= 0) return;
        int growth = wellFed ? Math.min(Math.max(0, amount - Math.max(0, max - health)), Math.max(0, 1000 - max)) : 0;
        int healed = Math.max(0, Math.min(amount, max + growth - health));
        if (healed == 0) return;
        if (mob == null) { player.health += healed; player.maxHealth += growth; heroHealthChanged(target.side()); }
        else { mob.health += healed; mob.maxHealth += growth; }
        event(MatchEvent.Type.HEAL, target.side(), target.hero() ? HERO : mob.id, mob == null ? null : mob.card, healed, max + growth, null);
        CardEffects.notifyEvent(this, CardCatalog.Trigger.HEALED, target.side(), mob == null ? null : mob.card, target);
    }

    void buff(Minion minion, int attack, int health) {
        if (minion == null || minion.isDead() || sideOf(minion) < 0) return;
        if (minion.has(Keyword.BLOCK)) attack = 0;
        int before = attackOf(minion);
        if (attack < 0) minion.attackDebuff = Math.min(1000, minion.attackDebuff - attack);
        else minion.attack = Math.min(1000, minion.attack + attack);
        minion.health += health;
        minion.maxHealth += health;
        event(MatchEvent.Type.BUFF, sideOf(minion), minion.id, minion.card, attackOf(minion) - before, health, null);
        if (attack < 0) event(MatchEvent.Type.STATUS, sideOf(minion), minion.id, minion.card, MatchEvent.STATUS_WEAKENED, minion.attackDebuff, null);
    }

    void cleanseHero(int side) {
        MatchPlayer hero = player(side);
        hero.badOmenSummon = null;
        hero.badOmenSource = null;
        hero.temporaryHeroAttack = 0;
        hero.maxHealth = hero.startingHealth > 0 ? hero.startingHealth : baseHealth();
        hero.health = Math.min(hero.health, hero.maxHealth);
        heroHealthChanged(side);
        hero.frozen = false;
        hero.skippedAttackFromFreeze = false;
        event(MatchEvent.Type.STATUS, side, HERO, null, MatchEvent.STATUS_CLEANSED, 0, null);
    }

    void clearAppliedEffects(Minion mob) {
        boolean lostSprint = mob.grantedKeywords.contains(Keyword.SPRINT);
        mob.frozen = mob.withered = mob.invulnerable = mob.totem = false;
        if (mob.skippedAttackFromFreeze && !mob.attackedThisTurn) mob.ready = true;
        mob.skippedAttackFromFreeze = false;
        mob.grantedKeywords.clear();
        if (lostSprint && mob.summonedThisTurn && !mob.has(Keyword.SPRINT)) mob.ready = false;
        mob.attackDebuff = mob.temporaryAttack = 0;
        mob.timedWeaknesses.clear();
        mob.attack = mob.card.training().attack(mob.definition());
        // An aura still supplied by another card remains in force. Removing bonus Health never heals.
        mob.maxHealth = mob.card.training().health(mob.definition()) + mob.auraHealth;
        mob.health = Math.min(mob.health, mob.maxHealth);
        event(MatchEvent.Type.AURA_ATTACK, sideOf(mob), mob.id, mob.card, attackOf(mob), 0, null);
        event(MatchEvent.Type.AURA_HEALTH, sideOf(mob), mob.id, mob.card, mob.health, mob.maxHealth, null);
    }

    void grantKeyword(Minion minion, Keyword keyword) {
        if (minion == null || minion.isDead() || minion.has(keyword) || minion.has(Keyword.BLOCK) && keyword == Keyword.SHIELD) return;
        minion.grantedKeywords.add(keyword);
        if (keyword == Keyword.SPRINT && minion.summonedThisTurn && !minion.attackedThisTurn
                && !minion.frozen && !minion.skippedAttackFromFreeze) minion.ready = true;
        int status = keyword == Keyword.SHIELD ? MatchEvent.STATUS_SHIELD : keyword == Keyword.SPRINT ? MatchEvent.STATUS_SPRINT : keyword == Keyword.IGNORE_SHIELD ? MatchEvent.STATUS_IGNORE_SHIELD : MatchEvent.STATUS_RANGED;
        event(MatchEvent.Type.STATUS, sideOf(minion), minion.id, minion.card, status, minion.ready ? 1 : 0, null);
    }

    void transform(Minion old, Identifier into) {
        int side = old == null ? -1 : sideOf(old);
        if (side < 0 || old.isDead() || !Cards.get(into).isMob()) return;
        Minion replacement = new Minion(old.id, new CardRef(into, old.card.enchanted()));
        boolean block = replacement.has(Keyword.BLOCK);
        if (block != old.has(Keyword.BLOCK) && player(side).rowFullReason(block) != null) return;
        replacement.summonedThisTurn = old.summonedThisTurn;
        replacement.attackedThisTurn = old.attackedThisTurn;
        // Transform cannot refresh a spent attack, but the new form may have Sprint.
        replacement.ready = !old.attackedThisTurn && (!old.summonedThisTurn || replacement.has(Keyword.SPRINT));
        player(side).board.set(player(side).board.indexOf(old), replacement);
        event(MatchEvent.Type.TRANSFORM, side, replacement.id, replacement.card, attackOf(replacement), replacement.health, null);
        refreshAuras();
    }

    void status(Minion minion, int status) {
        event(MatchEvent.Type.STATUS, sideOf(minion), minion.id, minion.card, status, 0, null);
    }

    void weakenUntilNextTurn(Minion mob, int amount) {
        if (mob == null || mob.isDead() || mob.has(Keyword.BLOCK)) return;
        int before = mob.attackDebuff;
        buff(mob, -amount, 0);
        mob.timedWeaknesses.add(new Minion.TimedWeakness(mob.attackDebuff - before, this.turn + (isTurnSeat(sideOf(mob)) ? 2 : 1)));
    }

    private void expireWeakness(Minion mob) {
        int before = attackOf(mob);
        int expired = mob.timedWeaknesses.stream().filter(w -> w.expiresTurn() <= this.turn).mapToInt(Minion.TimedWeakness::amount).sum();
        mob.timedWeaknesses.removeIf(w -> w.expiresTurn() <= this.turn);
        if (expired == 0) return;
        mob.attackDebuff = Math.max(0, mob.attackDebuff - expired);
        event(MatchEvent.Type.BUFF, sideOf(mob), mob.id, mob.card, attackOf(mob)-before, 0, null);
        event(MatchEvent.Type.STATUS, sideOf(mob), mob.id, mob.card, MatchEvent.STATUS_WEAKENED, mob.attackDebuff, null);
    }

    void gainMaxXp(int side, int amount, CardRef from) {
        MatchPlayer p = player(side);
        int gained = Math.min(MAX_XP - p.maxXp, amount);
        p.maxXp += gained;p.xp += gained;
        event(MatchEvent.Type.XP, side, HERO, from, p.xp, p.maxXp, null);
    }

    void gainXp(int side, int amount, CardRef from) {
        MatchPlayer player = this.players[side];
        player.xp += amount;
        event(MatchEvent.Type.XP, side, HERO, from, player.xp, player.maxXp, null);
    }

    // Sends a mob back to its owner's hand. Tokens can't go back, so they're destroyed instead.
    void bounce(Minion minion) {
        int side = sideOf(minion);
        if (side < 0) {
            return;
        }
        this.players[side].board.remove(minion);
        if (commandUnit(side, minion.id)) {
            publicNotice(player(side).name + "’s " + name(minion.card) + " returned to command."); refreshAuras(); return;
        }
        if (minion.definition().token()) {
            log(this.players[side].name + "'s " + name(minion.card) + " is destroyed.", minion.card);
            event(MatchEvent.Type.REMOVE, side, minion.id, minion.card, 0, 0, null);
            refreshAuras();
            return;
        }
        event(MatchEvent.Type.BOUNCE, side, minion.id, minion.card, 0, 0, null);
        refreshAuras();
        giveCard(side, minion.card);
    }

    // Takes a mob off the board without it dying (no Drop), like a harvested crop.
    void remove(Minion minion) {
        int side = sideOf(minion);
        if (side < 0) {
            return;
        }
        this.players[side].board.remove(minion);
        event(MatchEvent.Type.REMOVE, side, minion.id, minion.card, 0, 0, null);
        refreshAuras();
    }

    // Mining consumes a living Ore without damage/retaliation or Totem resurrection.
    // Its Drop still pays out; harvesting remains a separate removal path.
    void mine(Minion ore) {
        int side = sideOf(ore);
        if (side < 0 || ore.isDead() || !ore.has(Keyword.BLOCK) || ore.definition().mobType() != craftbound.card.MobType.ORE) return;
        remove(ore);
        log(player(side).name + " mines " + name(ore.card) + ".", ore.card);
        CardEffects.onDrop(this, side, ore);
        CardEffects.notifyEvent(this, CardCatalog.Trigger.MINED, side, ore.card, Target.mob(side, ore.id));
    }

    // Removes dead mobs and runs their Drops. A Drop can kill more mobs (like a Creeper), so it repeats
    // until nothing else dies.
    void resolveDeaths() {
        refreshAuras();
        boolean anyDied = true;
        int resolutions = 0;
        while (anyDied) {
            if (++resolutions > 128) {
                log("The match ends in a draw: a repeating card effect exceeded the resolution limit.");
                this.over = true;
                this.winner = -1;
                return;
            }
            anyDied = false;
            for (int offset = 0; offset < players.length; offset++) {
                int side = (this.current + offset) % players.length;
                for (Minion minion : new ArrayList<>(this.players[side].board)) {
                    if (!minion.isDead()) {
                        continue;
                    }
                    anyDied = true;
                    this.players[side].board.remove(minion);
                    log(name(minion.card) + " dies.", minion.card);
                    event(MatchEvent.Type.DEATH, side, minion.id, minion.card, 0, 0, null);
                    CardEffects.onDrop(this, side, minion);
                    if (!over && player(side).trialsFoe != null) TrialsOpponentRule.onDeath(this, side, minion);
                    if (minion.totem) {
                        log("The Totem of Undying brings " + name(minion.card) + " back!", Cards.TOTEM_OF_UNDYING);
                        Target previous = this.animationSource;
                        this.animationSource = Target.mob(side, minion.id);
                        try { Minion revived = summon(side, minion.card); if (revived != null) onRevival(side, minion.id, revived.id); } finally { this.animationSource = previous; }
                    }
                }
            }
        }
    }

    void checkWinner() {
        if (this.over) {
            return;
        }
        boolean firstDead = this.players[0].health <= 0;
        boolean secondDead = this.players[1].health <= 0;
        if (firstDead || secondDead) {
            this.over = true;
            this.winner = firstDead && secondDead ? -1 : firstDead ? 1 : 0;
            log(this.winner < 0 ? "It's a draw!" : this.players[this.winner].name + " wins!");
        }
    }

    static String name(CardRef card) {
        return Cards.name(card.id()).getString();
    }
}
