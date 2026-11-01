package craftbound.game;

import craftbound.card.CardDefinition;
import craftbound.card.Cards;
import craftbound.card.Keyword;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;

// The rules of a Craftbound match, from the Mobdeck prototype. Runs on the server; players only send requests.
// - Each hero starts with 20 health. Bring the other hero to 0 to win.
// - A coin toss decides who goes first. The first player starts with 4 cards, the second with 5. Both can
//   swap any of them once (the mulligan), then the second player gets a Bottle o' Enchanting.
// - Your XP max grows by 1 each turn (up to 10) and refills. Cards cost XP. The first player skips their first
//   draw. Drawing from an empty deck causes Hunger: 1 damage, then 2, then 3...
// - Up to 6 mobs each on the board and 10 cards in hand.
// - Mobs can attack the turn after they arrive (Sprint: straight away). A mob that's attacked hits back,
//   unless the attacker is Ranged or the mob is a Block. Enemy mobs with Shield must be attacked first.
// - Your class power costs 2 XP, once per turn. A hero holding a tool can attack once per turn with it.
// Everything that happens is also recorded as a MatchEvent so the screen can animate it.
public class Match {
    public static final int START_HEALTH = 20;
    public static final int MAX_XP = 10;
    public static final int MAX_BOARD = 6;
    public static final int MAX_HAND = 10;
    public static final int POWER_COST = 2;
    // The id used for a hero when it attacks or is hit (mobs are numbered from 1).
    public static final int HERO = 0;

    public enum Phase { MULLIGAN, PLAYING }

    public final MatchPlayer[] players = new MatchPlayer[2];
    public Phase phase = Phase.MULLIGAN;
    public final boolean[] mulliganDone = new boolean[2];
    public int current;
    public int turn = 0;
    public boolean over = false;
    // 0 or 1, or -1 for a draw.
    public int winner = -1;
    // The battle log. Each line can carry the card it's about, for its icon; turn headers mark each new turn.
    public record LogEntry(String text, Identifier card, int round, String turnOf) {
    }

    public final List<LogEntry> log = new ArrayList<>();
    public final List<MatchEvent> events = new ArrayList<>();
    final RandomSource random;
    private int nextMinionId = 1;

    public Match(MatchPlayer first, MatchPlayer second, RandomSource random) {
        this.random = random;
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
        log(this.players[0].name + " wins the coin toss and goes first.");
        event(MatchEvent.Type.COIN, 0);
        this.current = 0;
    }

    public MatchPlayer player(int side) {
        return this.players[side];
    }

    public MatchPlayer opponent(int side) {
        return this.players[1 - side];
    }

    public int sideOf(java.util.UUID playerId) {
        for (int side = 0; side < 2; side++) {
            if (playerId.equals(this.players[side].playerId)) {
                return side;
            }
        }
        return -1;
    }

    // Which side's board a mob is on, or -1 if it's gone.
    int sideOf(Minion minion) {
        for (int side = 0; side < 2; side++) {
            if (this.players[side].board.contains(minion)) {
                return side;
            }
        }
        return -1;
    }

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
        this.events.add(new MatchEvent(type, side, mid, card, amount, extra, target));
    }

    // --- Mulligan ---------------------------------------------------------------------------------------

    // A card for the opening hand. No event: the mulligan screen shows the whole hand at once.
    private void deal(int side) {
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

    private void startTurn() {
        this.turn++;
        MatchPlayer player = this.players[this.current];
        // A header in the battle log: "TURN 2 - NAME", counting each pair of turns as one round.
        this.log.add(new LogEntry("", null, (this.turn + 1) / 2, player.name));
        player.maxXp = Math.min(MAX_XP, player.maxXp + 1);
        player.xp = player.maxXp;
        player.heroAttacked = false;
        player.powerUsed = false;
        for (Minion minion : player.board) {
            minion.invulnerable = false;
            // A frozen mob skips this turn's attacks, then thaws.
            minion.ready = !minion.frozen;
            minion.frozen = false;
        }
        event(MatchEvent.Type.TURN, this.current, HERO, null, player.xp, player.maxXp, null);
        // Withered mobs take 1 damage.
        for (Minion minion : new ArrayList<>(player.board)) {
            if (minion.withered) {
                damage(minion, 1, null);
            }
        }
        resolveDeaths();
        // The first player skips their very first draw.
        if (this.turn > 1) {
            draw(this.current);
        }
        checkWinner();
    }

    public void endTurn(int side) {
        if (this.over || this.phase != Phase.PLAYING || side != this.current) {
            return;
        }
        // "At the end of your turn" effects: Furnace, Dispenser, Composter.
        CardEffects.onEndTurn(this, side);
        resolveDeaths();
        checkWinner();
        if (this.over) {
            return;
        }
        this.current = 1 - this.current;
        startTurn();
    }

    public void concede(int side) {
        if (this.over) {
            return;
        }
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
        CardRef card = player.deck.remove(0);
        if (player.hand.size() >= MAX_HAND) {
            log(player.name + "'s hand is full, so " + name(card) + " is lost.", card);
            event(MatchEvent.Type.BURN, side, HERO, card, 0, 0, null);
            return;
        }
        player.hand.add(card);
        event(MatchEvent.Type.DRAW, side, HERO, card, 0, 0, null);
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
    }

    private boolean yourMove(int side) {
        return !this.over && this.phase == Phase.PLAYING && side == this.current;
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
        if (definition.isMob() && player.board.size() >= MAX_BOARD) {
            return "your board is full";
        }
        // Items aimed at a mob need a mob to aim at. (Mobs that pick a target can still be played without one.)
        Target.Kind kind = CardEffects.targetKind(card.id());
        if (!definition.isMob() && kind != Target.Kind.NONE && validTargets(side, kind).isEmpty()) {
            return "nothing to target";
        }
        return CardEffects.whyCantPlay(this, side, card.id());
    }

    public List<Target> validTargets(int side, Target.Kind kind) {
        List<Target> targets = new ArrayList<>();
        for (int s = 0; s < 2; s++) {
            boolean friendly = s == side;
            if ((kind == Target.Kind.FRIENDLY_MOB && !friendly) || (kind == Target.Kind.ENEMY_MOB && friendly) || kind == Target.Kind.NONE) {
                continue;
            }
            for (Minion minion : this.players[s].board) {
                targets.add(Target.mob(s, minion.id));
            }
        }
        return targets;
    }

    public void playCard(int side, int handIndex, Target target) {
        if (whyCantPlay(side, handIndex) != null) {
            return;
        }
        MatchPlayer player = this.players[side];
        CardRef card = player.hand.get(handIndex);
        CardDefinition definition = Cards.get(card.id());
        Target.Kind kind = CardEffects.targetKind(card.id());
        boolean targetOk = kind != Target.Kind.NONE && validTargets(side, kind).contains(target);
        // Items that need a target must have a valid one.
        if (!definition.isMob() && kind != Target.Kind.NONE && !targetOk) {
            return;
        }
        Target aim = targetOk ? target : Target.NONE;
        player.xp -= definition.cost();
        player.hand.remove(handIndex);
        event(MatchEvent.Type.PLAY, side, HERO, card, handIndex, 0, aim);
        if (definition.isMob()) {
            log(player.name + " summons " + name(card) + ".", card);
            Minion minion = summon(side, card);
            if (minion != null) {
                CardEffects.onSpawn(this, side, minion, aim);
            }
        } else if (definition.isTool()) {
            equip(side, card);
        } else {
            log(player.name + " plays " + name(card) + ".", card);
            CardEffects.onItem(this, side, card, aim);
        }
        resolveDeaths();
        checkWinner();
    }

    // Puts a mob on the board. Returns null if the board is full.
    Minion summon(int side, CardRef card) {
        MatchPlayer player = this.players[side];
        if (player.board.size() >= MAX_BOARD) {
            return null;
        }
        Minion minion = new Minion(this.nextMinionId++, card);
        player.board.add(minion);
        event(MatchEvent.Type.SUMMON, side, minion.id, card, minion.attack, minion.health, null);
        return minion;
    }

    // Puts a tool in the hero's hand, replacing the one they held.
    void equip(int side, CardRef card) {
        MatchPlayer player = this.players[side];
        CardDefinition definition = Cards.get(card.id());
        if (player.tool != null) {
            log(player.name + "'s " + name(player.tool.card) + " is replaced.", player.tool.card);
        }
        player.tool = new Tool(card, definition.attack(), definition.health(), definition.keywords().contains(Keyword.RANGED));
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

    public void usePower(int side) {
        if (whyCantUsePower(side) != null) {
            return;
        }
        MatchPlayer player = this.players[side];
        player.xp -= POWER_COST;
        player.powerUsed = true;
        event(MatchEvent.Type.POWER, side, HERO, null, HeroPowers.of(player).ordinal(), 0, null);
        HeroPowers.use(this, side);
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
                if (other != minion && other.card.id().equals(Cards.BEACON)) {
                    bonus++;
                }
            }
        }
        return minion.attack + bonus;
    }

    public boolean heroCanAttack(int side) {
        MatchPlayer player = this.players[side];
        return yourMove(side) && player.tool != null && !player.heroAttacked && player.tool.attack > 0;
    }

    // Why this mob (or the hero, attackerId == HERO) can't attack that target, or null if it can.
    public String whyCantAttack(int side, int attackerId, Target target) {
        if (!yourMove(side)) {
            return "not your turn";
        }
        if (attackerId == HERO) {
            if (!heroCanAttack(side)) {
                return "your hero can't attack right now";
            }
        } else {
            Minion attacker = this.players[side].minion(attackerId);
            if (attacker == null || !attacker.canAttack() || attackOf(attacker) <= 0) {
                return "that mob can't attack right now";
            }
        }
        if (target.isNone() || target.side() != 1 - side) {
            return "pick an enemy";
        }
        MatchPlayer enemy = this.players[1 - side];
        Minion defender = target.hero() ? null : enemy.minion(target.minionId());
        if (!target.hero() && defender == null) {
            return "pick an enemy";
        }
        boolean enemyHasShield = enemy.board.stream().anyMatch(m -> m.has(Keyword.SHIELD));
        if (enemyHasShield && (defender == null || !defender.has(Keyword.SHIELD))) {
            return "a mob with Shield must be attacked first";
        }
        return null;
    }

    public void attack(int side, int attackerId, Target target) {
        if (whyCantAttack(side, attackerId, target) != null) {
            return;
        }
        MatchPlayer me = this.players[side];
        MatchPlayer enemy = this.players[1 - side];
        boolean hero = attackerId == HERO;
        Minion attacker = hero ? null : me.minion(attackerId);
        Tool tool = me.tool;
        int power = hero ? tool.attack : attackOf(attacker);
        boolean ranged = hero ? tool.ranged : attacker.has(Keyword.RANGED);
        String who = hero ? me.name : name(attacker.card);
        CardRef icon = hero ? tool.card : attacker.card;
        if (hero) {
            me.heroAttacked = true;
        } else {
            attacker.ready = false;
        }
        event(MatchEvent.Type.ATTACK, side, attackerId, icon, power, 0, target);
        if (target.hero()) {
            log(who + " hits " + enemy.name + " for " + power + ".", icon);
            damageHero(1 - side, power);
        } else {
            Minion defender = enemy.minion(target.minionId());
            log(who + " attacks " + name(defender.card) + ".", icon);
            int hitBack = attackOf(defender);
            damage(defender, power, attacker);
            // The Sweet Berry Bush pricks anything that attacks it.
            if (defender.card.id().equals(Cards.SWEET_BERRY_BUSH)) {
                log("The Sweet Berry Bush pricks " + who + " for 1.", Cards.SWEET_BERRY_BUSH);
                hitAttacker(side, attacker, 1, defender);
            }
            // The defender hits back, unless the attacker is Ranged or the defender is a Block.
            if (!ranged && !defender.has(Keyword.BLOCK) && hitBack > 0) {
                hitAttacker(side, attacker, hitBack, defender);
            }
        }
        if (hero) {
            tool.uses--;
            if (tool.uses <= 0) {
                log(me.name + "'s " + name(tool.card) + " breaks.", tool.card);
                event(MatchEvent.Type.BREAK, side, HERO, tool.card, 0, 0, null);
                me.tool = null;
            }
        }
        resolveDeaths();
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
    void damage(Minion minion, int amount, Minion source) {
        if (amount <= 0) {
            return;
        }
        int side = sideOf(minion);
        if (minion.invulnerable) {
            event(MatchEvent.Type.DAMAGE, side, minion.id, null, 0, 0, null);
            return;
        }
        // The Piglin Brute's armour takes 1 off every hit.
        if (minion.card.id().equals(Cards.PIGLIN_BRUTE)) {
            amount = Math.max(0, amount - 1);
        }
        if (amount <= 0) {
            event(MatchEvent.Type.DAMAGE, side, minion.id, null, 0, 0, null);
            return;
        }
        minion.health -= amount;
        event(MatchEvent.Type.DAMAGE, side, minion.id, null, amount, 0, null);
        if (source != null) {
            CardEffects.onDamageDealt(this, source, minion);
        }
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
        event(MatchEvent.Type.DAMAGE, side, HERO, null, amount, 0, null);
    }

    void healHero(int side, int amount) {
        MatchPlayer player = this.players[side];
        int healed = Math.min(amount, START_HEALTH - player.health);
        if (healed > 0) {
            player.health += healed;
            event(MatchEvent.Type.HEAL, side, HERO, null, healed, 0, null);
        }
    }

    // +attack and +health (which also raises its max health).
    void buff(Minion minion, int attack, int health) {
        minion.attack += attack;
        minion.health += health;
        minion.maxHealth += health;
        event(MatchEvent.Type.BUFF, sideOf(minion), minion.id, minion.card, attack, health, null);
    }

    void status(Minion minion, int status) {
        event(MatchEvent.Type.STATUS, sideOf(minion), minion.id, minion.card, status, 0, null);
    }

    void gainXp(int side, int amount, CardRef from) {
        MatchPlayer player = this.players[side];
        player.xp += amount;
        event(MatchEvent.Type.XP, side, HERO, from, player.xp, amount, null);
    }

    // Sends a mob back to its owner's hand. Tokens can't go back, so they're destroyed instead.
    void bounce(Minion minion) {
        int side = sideOf(minion);
        if (side < 0) {
            return;
        }
        this.players[side].board.remove(minion);
        if (minion.definition().token()) {
            log(this.players[side].name + "'s " + name(minion.card) + " is destroyed.", minion.card);
            event(MatchEvent.Type.REMOVE, side, minion.id, minion.card, 0, 0, null);
            return;
        }
        event(MatchEvent.Type.BOUNCE, side, minion.id, minion.card, 0, 0, null);
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
    }

    // Removes dead mobs and runs their Drops. A Drop can kill more mobs (like a Creeper), so it repeats
    // until nothing else dies.
    void resolveDeaths() {
        boolean anyDied = true;
        while (anyDied) {
            anyDied = false;
            for (int offset = 0; offset < 2; offset++) {
                int side = (this.current + offset) % 2;
                for (Minion minion : new ArrayList<>(this.players[side].board)) {
                    if (!minion.isDead()) {
                        continue;
                    }
                    anyDied = true;
                    this.players[side].board.remove(minion);
                    log(name(minion.card) + " dies.", minion.card);
                    event(MatchEvent.Type.DEATH, side, minion.id, minion.card, 0, 0, null);
                    CardEffects.onDrop(this, side, minion);
                    if (minion.totem) {
                        log("The Totem of Undying brings " + name(minion.card) + " back!", Cards.TOTEM_OF_UNDYING);
                        summon(side, minion.card);
                    }
                }
            }
        }
    }

    private void checkWinner() {
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
