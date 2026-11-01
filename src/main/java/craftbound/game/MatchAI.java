package craftbound.game;

import craftbound.card.CardDefinition;
import craftbound.card.Cards;
import craftbound.card.Keyword;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

// The Card Master's brain. It takes one action at a time so you can watch it play:
// 1. Play the most expensive card it can afford (skipping ones that would be wasted).
// 2. Use its class power if it has the XP.
// 3. Attack with each ready mob, then its hero: kill an enemy mob if it can do it and survive, otherwise
//    go for your hero.
// 4. End its turn.
public class MatchAI {
    // Swaps out cards that cost 4 or more: they'd sit in hand for the first few turns.
    public static void mulligan(Match match, int side) {
        MatchPlayer me = match.player(side);
        int mask = 0;
        for (int i = 0; i < me.hand.size(); i++) {
            if (Cards.get(me.hand.get(i).id()).cost() >= 4) {
                mask |= 1 << i;
            }
        }
        match.mulligan(side, mask);
    }

    // Does one thing. Returns false when it has nothing left to do and has ended its turn.
    public static boolean step(Match match, int side) {
        if (playBestCard(match, side) || usePower(match, side) || attackWithAMob(match, side) || attackWithHero(match, side)) {
            return true;
        }
        match.endTurn(side);
        return false;
    }

    private static boolean playBestCard(Match match, int side) {
        MatchPlayer me = match.player(side);
        int best = -1;
        for (int i = 0; i < me.hand.size(); i++) {
            if (match.whyCantPlay(side, i) != null || !worthPlaying(match, side, me.hand.get(i))) {
                continue;
            }
            if (best < 0 || Cards.get(me.hand.get(i).id()).cost() > Cards.get(me.hand.get(best).id()).cost()) {
                best = i;
            }
        }
        if (best < 0) {
            return false;
        }
        CardRef card = me.hand.get(best);
        match.playCard(side, best, chooseTarget(match, side, card));
        return true;
    }

    // Skips cards that would be wasted, like healing at full health.
    private static boolean worthPlaying(Match match, int side, CardRef card) {
        MatchPlayer me = match.player(side);
        MatchPlayer enemy = match.opponent(side);
        CardDefinition definition = Cards.get(card.id());
        if (card.id().equals(Cards.ROTTEN_FLESH)) {
            return me.health < Match.START_HEALTH;
        }
        if (card.id().equals(Cards.EXPERIENCE_BOTTLE)) {
            // Only drink it if the extra XP lets it play something it otherwise couldn't.
            return me.hand.stream().anyMatch(c -> Cards.get(c.id()).cost() == me.xp + 1);
        }
        if (definition.isTool()) {
            return me.tool == null || me.tool.attack < definition.attack();
        }
        if (card.id().equals(Cards.PISTON)) {
            return enemy.board.stream().anyMatch(m -> m.definition().cost() >= 4);
        }
        if (card.id().equals(Cards.SWEEPING_EDGE)) {
            int amount = me.tool == null ? 0 : me.tool.attack;
            return enemy.board.stream().filter(m -> m.health <= amount).count() >= 2 || enemy.board.size() >= 3;
        }
        return true;
    }

    private static Target chooseTarget(Match match, int side, CardRef card) {
        Target.Kind kind = CardEffects.targetKind(card.id());
        if (kind == Target.Kind.NONE) {
            return Target.NONE;
        }
        List<Target> options = match.validTargets(side, kind);
        if (options.isEmpty()) {
            return Target.NONE;
        }
        // Friendly buffs go on its strongest mob that can attack; anything aimed at enemies goes at their strongest mob.
        int wantedSide = kind == Target.Kind.FRIENDLY_MOB ? side : 1 - side;
        Target best = null;
        int bestScore = Integer.MIN_VALUE;
        for (Target target : options) {
            if (target.side() != wantedSide && kind == Target.Kind.ANY_MOB) {
                continue;
            }
            Minion minion = match.player(target.side()).minion(target.minionId());
            int score = match.attackOf(minion) + minion.health + (minion.has(Keyword.BLOCK) && wantedSide == side ? -20 : 0);
            if (score > bestScore) {
                bestScore = score;
                best = target;
            }
        }
        return best == null ? options.get(0) : best;
    }

    private static boolean usePower(Match match, int side) {
        MatchPlayer me = match.player(side);
        if (match.whyCantUsePower(side) != null) {
            return false;
        }
        switch (HeroPowers.of(me)) {
            case CRAFT -> {
                // Only craft a sword when it isn't already holding a tool.
                if (me.tool != null) {
                    return false;
                }
            }
            case REAP -> {
                // Reap once there are 2 or more crops, or its hand is running low.
                if (HeroPowers.crops(me).size() < 2 && me.hand.size() > 2) {
                    return false;
                }
            }
            default -> {
            }
        }
        match.usePower(side);
        return true;
    }

    private static boolean attackWithAMob(Match match, int side) {
        MatchPlayer me = match.player(side);
        List<Minion> attackers = new ArrayList<>(me.board);
        attackers.sort(Comparator.comparingInt((Minion m) -> match.attackOf(m)).reversed());
        for (Minion attacker : attackers) {
            if (!attacker.canAttack() || match.attackOf(attacker) <= 0) {
                continue;
            }
            Target target = chooseAttack(match, side, attacker.id, match.attackOf(attacker), attacker.has(Keyword.RANGED), attacker.health);
            if (target != null) {
                match.attack(side, attacker.id, target);
                return true;
            }
        }
        return false;
    }

    private static boolean attackWithHero(Match match, int side) {
        MatchPlayer me = match.player(side);
        if (!match.heroCanAttack(side)) {
            return false;
        }
        // The hero is more careful than its mobs: it keeps 6 health spare when trading.
        Target target = chooseAttack(match, side, Match.HERO, me.tool.attack, me.tool.ranged, me.health - 6);
        if (target == null) {
            return false;
        }
        match.attack(side, Match.HERO, target);
        return true;
    }

    // A good trade (kill an enemy mob and live, or take nothing back), otherwise the enemy hero, otherwise a
    // Shield mob that's in the way. Null if there's nothing it can attack.
    private static Target chooseAttack(Match match, int side, int attackerId, int attack, boolean ranged, int health) {
        MatchPlayer enemy = match.opponent(side);
        Minion bestTrade = null;
        for (Minion defender : enemy.board) {
            boolean kills = attack >= defender.health && !defender.invulnerable;
            boolean survives = ranged || defender.has(Keyword.BLOCK) || match.attackOf(defender) < health;
            if (kills && survives && match.whyCantAttack(side, attackerId, Target.mob(1 - side, defender.id)) == null) {
                if (bestTrade == null || match.attackOf(defender) > match.attackOf(bestTrade)) {
                    bestTrade = defender;
                }
            }
        }
        if (bestTrade != null) {
            return Target.mob(1 - side, bestTrade.id);
        }
        if (match.whyCantAttack(side, attackerId, Target.hero(1 - side)) == null) {
            return Target.hero(1 - side);
        }
        for (Minion defender : enemy.board) {
            if (match.whyCantAttack(side, attackerId, Target.mob(1 - side, defender.id)) == null
                    && (ranged || match.attackOf(defender) < health || attackerId != Match.HERO)) {
                return Target.mob(1 - side, defender.id);
            }
        }
        return null;
    }
}
