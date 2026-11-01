package craftbound.game;

import craftbound.card.Cards;
import craftbound.card.Keyword;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

// The Card Master's brain. It takes one action at a time so you can watch it play:
// 1. Play the most expensive card it can afford.
// 2. Attack with each ready mob: kill an enemy mob if it can do it and survive, otherwise go for your hero.
// 3. End its turn.
public class MatchAI {
    // Does one thing. Returns false when it has nothing left to do and has ended its turn.
    public static boolean step(Match match, int side) {
        if (playBestCard(match, side) || attackWithAMob(match, side)) {
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
        if (card.id().equals(Cards.ROTTEN_FLESH)) {
            return match.player(side).health < Match.START_HEALTH;
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
        // Friendly buffs go on its strongest mob; anything aimed at enemies goes at their strongest mob.
        int wantedSide = kind == Target.Kind.FRIENDLY_MOB ? side : 1 - side;
        Target best = null;
        int bestScore = Integer.MIN_VALUE;
        for (Target target : options) {
            if (target.side() != wantedSide && kind == Target.Kind.ANY_MOB) {
                continue;
            }
            Minion minion = match.player(target.side()).minion(target.minionId());
            int score = minion.attack + minion.health;
            if (score > bestScore) {
                bestScore = score;
                best = target;
            }
        }
        return best == null ? options.get(0) : best;
    }

    private static boolean attackWithAMob(Match match, int side) {
        MatchPlayer me = match.player(side);
        MatchPlayer enemy = match.opponent(side);
        List<Minion> attackers = new ArrayList<>(me.board);
        attackers.sort(Comparator.comparingInt((Minion m) -> m.attack).reversed());
        for (Minion attacker : attackers) {
            if (!attacker.canAttack()) {
                continue;
            }
            // A good trade: kill an enemy mob and live (or not get hit back at all).
            Minion bestTrade = null;
            for (Minion defender : enemy.board) {
                boolean kills = attacker.attack >= defender.health && !defender.invulnerable;
                boolean survives = attacker.has(Keyword.RANGED) || defender.attack < attacker.health;
                if (kills && survives && match.whyCantAttack(side, attacker.id, Target.mob(1 - side, defender.id)) == null) {
                    if (bestTrade == null || defender.attack > bestTrade.attack) {
                        bestTrade = defender;
                    }
                }
            }
            if (bestTrade != null) {
                match.attack(side, attacker.id, Target.mob(1 - side, bestTrade.id));
                return true;
            }
            if (match.whyCantAttack(side, attacker.id, Target.hero(1 - side)) == null) {
                match.attack(side, attacker.id, Target.hero(1 - side));
                return true;
            }
            // Shield is in the way: hit a Shield mob.
            for (Minion defender : enemy.board) {
                if (match.whyCantAttack(side, attacker.id, Target.mob(1 - side, defender.id)) == null) {
                    match.attack(side, attacker.id, Target.mob(1 - side, defender.id));
                    return true;
                }
            }
        }
        return false;
    }
}
