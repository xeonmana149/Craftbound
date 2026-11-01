package craftbound.game;

import craftbound.card.CardDefinition;
import craftbound.card.Cards;
import craftbound.card.Keyword;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraft.util.RandomSource;

// The rules of a Craftbound match, from the Mobdeck prototype. Runs on the server; players only send requests.
// - Each hero starts with 20 health. Bring the other hero to 0 to win.
// - Your XP max grows by 1 each turn (up to 10) and refills. Cards cost XP.
// - The first player starts with 4 cards and skips their first draw; the second starts with 5 and a
//   Bottle o' Enchanting. Drawing from an empty deck causes Hunger: 1 damage, then 2, then 3...
// - Up to 6 mobs each on the board and 10 cards in hand.
// - Mobs can attack the turn after they arrive (Sprint: straight away). A mob that's attacked hits back,
//   unless the attacker is Ranged. Enemy mobs with Shield must be attacked first.
public class Match {
    public static final int START_HEALTH = 20;
    public static final int MAX_XP = 10;
    public static final int MAX_BOARD = 6;
    public static final int MAX_HAND = 10;

    public final MatchPlayer[] players = new MatchPlayer[2];
    public int current;
    public int turn = 0;
    public boolean over = false;
    // 0 or 1, or -1 for a draw.
    public int winner = -1;
    public final List<String> log = new ArrayList<>();
    final RandomSource random;
    private int nextMinionId = 1;

    public Match(MatchPlayer first, MatchPlayer second, RandomSource random) {
        this.random = random;
        // A coin flip decides who goes first.
        boolean swap = random.nextBoolean();
        this.players[0] = swap ? second : first;
        this.players[1] = swap ? first : second;
        for (MatchPlayer player : this.players) {
            Collections.shuffle(player.deck, new java.util.Random(random.nextLong()));
        }
        for (int i = 0; i < 4; i++) draw(0);
        for (int i = 0; i < 5; i++) draw(1);
        this.players[1].hand.add(new CardRef(Cards.EXPERIENCE_BOTTLE, false));
        log(this.players[0].name + " goes first.");
        this.current = 0;
        startTurn();
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

    void log(String line) {
        this.log.add(line);
        if (this.log.size() > 40) {
            this.log.remove(0);
        }
    }

    // --- Turns ------------------------------------------------------------------------------------------

    private void startTurn() {
        this.turn++;
        MatchPlayer player = this.players[this.current];
        player.maxXp = Math.min(MAX_XP, player.maxXp + 1);
        player.xp = player.maxXp;
        for (Minion minion : player.board) {
            minion.invulnerable = false;
            // A frozen mob skips this turn's attacks, then thaws.
            minion.ready = !minion.frozen;
            minion.frozen = false;
        }
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
        if (this.over || side != this.current) {
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
            player.health -= player.hunger;
            log(player.name + " is out of cards and takes " + player.hunger + " Hunger damage.");
            return;
        }
        CardRef card = player.deck.remove(0);
        if (player.hand.size() >= MAX_HAND) {
            log(player.name + "'s hand is full, so " + name(card) + " is lost.");
            return;
        }
        player.hand.add(card);
    }

    // Gives a card straight to someone's hand (e.g. a Drop). Lost if their hand is full.
    void giveCard(int side, CardRef card) {
        MatchPlayer player = this.players[side];
        if (player.hand.size() >= MAX_HAND) {
            log(player.name + "'s hand is full, so " + name(card) + " is lost.");
            return;
        }
        player.hand.add(card);
    }

    // Why a card in hand can't be played right now, or null if it can.
    public String whyCantPlay(int side, int handIndex) {
        MatchPlayer player = this.players[side];
        if (this.over || side != this.current || handIndex < 0 || handIndex >= player.hand.size()) {
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
        return null;
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
        player.xp -= definition.cost();
        player.hand.remove(handIndex);
        if (definition.isMob()) {
            Minion minion = summon(side, card);
            log(player.name + " summons " + name(card) + ".");
            if (minion != null) {
                CardEffects.onSpawn(this, side, minion, targetOk ? target : Target.NONE);
            }
        } else {
            log(player.name + " plays " + name(card) + ".");
            CardEffects.onItem(this, side, card, targetOk ? target : Target.NONE);
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
        return minion;
    }

    // --- Combat -----------------------------------------------------------------------------------------

    // Why this mob can't attack that target, or null if it can.
    public String whyCantAttack(int side, int attackerId, Target target) {
        if (this.over || side != this.current) {
            return "not your turn";
        }
        Minion attacker = this.players[side].minion(attackerId);
        if (attacker == null || !attacker.canAttack()) {
            return "that mob can't attack right now";
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
        Minion attacker = this.players[side].minion(attackerId);
        MatchPlayer enemy = this.players[1 - side];
        attacker.ready = false;
        if (target.hero()) {
            enemy.health -= attacker.attack;
            log(name(attacker.card) + " hits " + enemy.name + " for " + attacker.attack + ".");
        } else {
            Minion defender = enemy.minion(target.minionId());
            log(name(attacker.card) + " attacks " + name(defender.card) + ".");
            damage(defender, attacker.attack, attacker);
            // The defender hits back, unless the attacker is Ranged or the defender is a Block.
            if (!attacker.has(Keyword.RANGED) && !defender.has(Keyword.BLOCK)) {
                damage(attacker, defender.attack, defender);
            }
        }
        resolveDeaths();
        checkWinner();
    }

    // Damages a mob. `source` is the mob dealing it, if any, for effects like freezing and withering.
    void damage(Minion minion, int amount, Minion source) {
        if (amount <= 0 || minion.invulnerable) {
            return;
        }
        if (minion.card.id().equals(Cards.PIGLIN_BRUTE)) {
            amount = Math.max(0, amount - 1);
        }
        if (amount <= 0) {
            return;
        }
        minion.health -= amount;
        if (source != null) {
            CardEffects.onDamageDealt(this, source, minion);
        }
    }

    void damageHero(int side, int amount) {
        this.players[side].health -= amount;
    }

    void healHero(int side, int amount) {
        MatchPlayer player = this.players[side];
        player.health = Math.min(START_HEALTH, player.health + amount);
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
                    log(name(minion.card) + " dies.");
                    CardEffects.onDrop(this, side, minion);
                    if (minion.totem) {
                        log("The Totem of Undying brings " + name(minion.card) + " back!");
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
