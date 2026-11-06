package craftbound.game;

import craftbound.card.CardDefinition;
import craftbound.card.CardCatalog;
import craftbound.card.Cards;
import craftbound.card.Keyword;
import java.util.Comparator;
import java.util.List;

// The Card Master's brain. It takes one action at a time so you can watch it play:
// 1. Take a winning board attack first. Otherwise play the highest-priority card it can afford (skipping ones that would be wasted).
// 2. Use its class power if it has the XP.
// 3. Trade for survival or board control, then spend remaining swings on face or mandatory targets.
// 4. End its turn.
public class MatchAI {
    // Look for an early attacker. Keep an affordable answer or support when there is already a body to play.
    public static void mulligan(Match match, int side) {
        MatchPlayer me = match.player(side);
        boolean hasOpener = me.hand.stream().map(c -> Cards.get(c.id()))
                .anyMatch(c -> MatchManager.frontliner(c) && c.cost() <= 2);
        int mask = 0;
        for (int i = 0; i < me.hand.size(); i++) {
            CardDefinition card = Cards.get(me.hand.get(i).id());
            if (card.cost() >= 4 || (!hasOpener && !MatchManager.frontliner(card) && !card.isTool())) {
                mask |= 1 << i;
            }
        }
        match.mulligan(side, mask);
    }

    // Picks a card from victim's hand for a Discard/Steal/Shuffle effect, without using the match's RNG.
    // Taking from the other player: their most expensive card. Giving up your own: your cheapest.
    static int pickFromHand(Match match, CardCatalog.Action action, int owner, int victim, int chooser) {
        List<CardRef> hand = match.player(victim).hand;
        boolean ownHand = chooser == victim;
        int best = 0;
        for (int i = 1; i < hand.size(); i++) {
            int cost = Cards.get(hand.get(i).id()).cost(), bestCost = Cards.get(hand.get(best).id()).cost();
            if (ownHand ? cost < bestCost : cost > bestCost) best = i;
        }
        return best;
    }

    // Does one thing. Returns false when it has nothing left to do and has ended its turn.
    public static boolean step(Match match, int side) {
        if (CombatTactics.takeLethal(match,side) || playBestCard(match, side) || usePower(match, side)
                || CombatTactics.pickTrade(match, side) || attackRemaining(match, side)) {
            return true;
        }
        match.endTurn(side);
        return false;
    }

    private static boolean playBestCard(Match match, int side) {
        MatchPlayer me = match.player(side);
        int best = -1;
        int bestScore = Integer.MIN_VALUE;
        Target bestTarget = Target.NONE;
        for (int i = 0; i < me.hand.size(); i++) {
            CardRef card = me.hand.get(i);
            var definition = Cards.get(card.id());
            if (match.whyCantPlay(side, i) != null || !worthPlaying(match, side, card)) continue;
            int score=TrialsRoute.playPriority(match,side,card);
            Target target = chooseTarget(match, side, card);
            // A legal target may be strategically wrong (e.g. an Arrow with only friendly mobs).
            // Passing NONE to a targeted action would reject the play and stall every later AI tick.
            if (!definition.isMob() && target.isNone() && CardEffects.targetKind(card) != Target.Kind.NONE) continue;
            score += boardPlayPriority(match, side, card, target);
            if (score <= bestScore) continue;
            best = i;
            bestScore = score;
            bestTarget = target;
        }
        if (best < 0) return false;
        match.playCard(side, best, bestTarget);
        return true;
    }

    private static int boardPlayPriority(Match match, int side, CardRef card, Target target) {
        var definition = Cards.get(card.id());
        var me = match.player(side);
        var enemy = match.opponent(side);
        int score = 0;
        if (definition.isMob() && !definition.keywords().contains(Keyword.BLOCK)) {
            long ownMobs = me.board.stream().filter(m -> !m.has(Keyword.BLOCK)).count();
            long enemyMobs = enemy.board.stream().filter(m -> !m.has(Keyword.BLOCK)).count();
            if (ownMobs == 0 || ownMobs < enemyMobs) {
                score += 12 + Math.min(8, card.training().attack(definition));
                if (definition.keywords().contains(Keyword.SHIELD) || definition.keywords().contains(Keyword.SPRINT))
                    score += 5;
            }
        }
        if (!target.isNone() && match.isEnemy(side, target.side())) {
            int damage = chosenDamage(match, side, card, target);
            if (target.hero() && damage >= enemy.health) return 10000;
            Minion mob = target.hero() ? null : match.player(target.side()).minion(target.minionId());
            if (mob != null && damage >= mob.health && !mob.totem)
                score += 15 + Math.min(25, CombatTactics.boardValue(match, mob));
        }
        return score;
    }

    // Score each printed hit separately: armor applies to each hit before the next effect resolves.
    private static int chosenDamage(Match match, int side, CardRef card, Target target) {
        Minion mob = target.hero() ? null : match.player(target.side()).minion(target.minionId());
        if (mob != null && mob.invulnerable) return 0;
        MatchPlayer me = match.player(side);
        int total = 0;
        for (var e : CardCatalog.entry(card.id()).effects()) {
            if (!chosen(e) || (e.action() != CardCatalog.Action.DAMAGE && e.action() != CardCatalog.Action.TOOL_DAMAGE)
                    || !CardEffects.gateOpen(me, e.gate(), me.hand.size() - 1)
                    || !CardEffects.possibleTargets(match, side, null, e, target).contains(target)) continue;
            int count = CardEffects.multiplier(match, side, null, e);
            if (count == 0) continue;
            int amount = e.action() == CardCatalog.Action.DAMAGE ? count * e.amount() : me.tool == null ? 0 : me.tool.attack;
            total += mob == null ? amount : mob.afterArmor(amount);
        }
        return total;
    }

    private static boolean grantsChosenTotem(CardRef card) {
        return CardCatalog.entry(card.id()).effects().stream().anyMatch(e ->
                e.trigger() == CardCatalog.Trigger.PLAY && e.action() == CardCatalog.Action.TOTEM
                        && e.target().name().startsWith("CHOSEN"));
    }

    private static boolean chosen(CardCatalog.Effect effect) {
        return effect.trigger() == CardCatalog.Trigger.PLAY && effect.target().name().startsWith("CHOSEN");
    }

    private static boolean statusUtility(CardCatalog.Action action) {
        return switch (action) {
            case HEAL, WELL_FED, GRANT_KEYWORD, BUFF, FREEZE, CLEANSE, TOTEM, RETURN_AND_COPY -> true;
            default -> false;
        };
    }

    private static boolean usefulEffect(Match match, int side, CardCatalog.Effect effect, Target target) {
        Minion mob = target.hero() ? null : match.player(target.side()).minion(target.minionId());
        if (!target.hero() && (mob == null || mob.isDead())) return false;
        MatchPlayer hero = match.player(target.side());
        if (CardEffects.multiplier(match, side, null, effect) == 0) return false;
        return switch (effect.action()) {
            case RETURN_AND_COPY -> mob != null && match.player(side).hand.size() - 1 + effect.amount()
                    + (target.side() == side && !mob.definition().token() ? 1 : 0) <= Match.MAX_HAND;
            case WELL_FED -> target.side() == side && (target.hero() ? hero.maxHealth : mob.maxHealth) < 1000;
            case GRANT_KEYWORD -> target.side() == side && mob != null && !CardEffects.grantOptions(effect, mob).isEmpty();
            case BUFF -> mob != null && (effect.attack() < 0 ? target.side() != side && match.attackOf(mob) > 0
                    : target.side() == side && (effect.attack() > 0 || effect.health() > 0
                    || effect.keyword() != null && !mob.has(effect.keyword())));
            case HEAL -> target.side() == side && effect.amount() > 0
                    && (target.hero() ? hero.health < hero.maxHealth : mob.health < mob.maxHealth);
            case FREEZE -> target.side() != side && (target.hero()
                    ? !hero.frozen && !hero.skippedAttackFromFreeze : !mob.frozen && !mob.skippedAttackFromFreeze);
            case CLEANSE -> cleanseValue(match, side, target) > 0;
            case TOTEM -> target.side() == side && mob != null && !mob.totem;
            default -> true; // A combined damage/buff effect may still make a target worthwhile.
        };
    }

    private static int cleanseValue(Match match, int side, Target target) {
        MatchPlayer owner = match.player(target.side());
        Minion mob = target.hero() ? null : owner.minion(target.minionId());
        int gains, losses;
        if (mob == null) {
            gains = (owner.badOmenSummon != null ? 3 : 0) + (owner.frozen || owner.skippedAttackFromFreeze ? 2 : 0);
            losses = owner.temporaryHeroAttack + Math.max(0, owner.maxHealth - (owner.startingHealth > 0 ? owner.startingHealth : match.baseHealth()));
        } else {
            gains = mob.attackDebuff + (mob.withered ? 2 : 0) + (mob.frozen || mob.skippedAttackFromFreeze ? 2 : 0);
            losses = Math.max(0, mob.attack - mob.card.training().attack(mob.definition())) + Math.max(0, mob.maxHealth - mob.auraHealth - mob.card.training().health(mob.definition()))
                    + mob.grantedKeywords.size() * 2 + (mob.totem ? 4 : 0) + (mob.invulnerable ? 3 : 0);
        }
        return target.side() == side ? gains - losses : losses - gains;
    }

    private static List<Target> usefulTargets(Match match, int side, CardRef card) {
        List<Target> options = match.validTargets(side, CardEffects.targetKind(card), card.id());
        var effects = CardCatalog.entry(card.id()).effects().stream().filter(MatchAI::chosen).toList();
        if (effects.stream().noneMatch(e -> statusUtility(e.action()))) return options;
        return options.stream().filter(target -> effects.stream().anyMatch(e ->
                CardEffects.possibleTargets(match, side, null, e, target).contains(target)
                        && usefulEffect(match, side, e, target))).toList();
    }

    // Skips cards that would be wasted, like healing at full health.
    private static boolean worthPlaying(Match match, int side, CardRef card) {
        MatchPlayer me = match.player(side);
        MatchPlayer enemy = match.opponent(side);
        CardDefinition definition = Cards.get(card.id());
        var playEffects = CardCatalog.entry(card.id()).effects().stream()
                .filter(e -> e.trigger() == CardCatalog.Trigger.PLAY).toList();
        // Bodies can still be summoned with no useful battlecry target. Targeted items cannot.
        if (!definition.isMob() && CardEffects.targetKind(card) != Target.Kind.NONE
                && usefulTargets(match, side, card).isEmpty()) return false;
        // Includes friendly-hero healing, splash healing and random effects, without rolling RNG.
        if (!definition.isMob() && !definition.isTool() && !playEffects.isEmpty()
                && playEffects.stream().allMatch(e -> statusUtility(e.action()))) {
            boolean useful = playEffects.stream().anyMatch(e -> {
                if (chosen(e)) return !usefulTargets(match, side, card).isEmpty();
                return CardEffects.possibleTargets(match, side, null, e, Target.NONE).stream()
                        .anyMatch(t -> usefulEffect(match, side, e, t));
            });
            if (!useful) return false;
        }
        // Discard/Steal/Shuffle Actions do nothing against an empty hand.
        if (!definition.isMob() && !definition.isTool() && !playEffects.isEmpty()
                && playEffects.stream().allMatch(e -> CardCatalog.handPick(e.action())) && enemy.hand.isEmpty()) {
            return false;
        }
        if (card.id().equals(Cards.ROTTEN_FLESH)) {
            return me.health < me.maxHealth;
        }
        if (card.id().equals(Cards.EXPERIENCE_BOTTLE) || card.id().getPath().equals("mined_coal")) {
            // Bank XP resources until they can pay for a card; several Coal can fund one larger turn.
            int storedXp = me.hand.stream().mapToInt(c -> craftbound.card.CardCatalog.entry(c.id()).effects().stream()
                    .filter(e -> e.trigger() == craftbound.card.CardCatalog.Trigger.PLAY
                            && e.action() == craftbound.card.CardCatalog.Action.GAIN_XP).mapToInt(e -> e.amount()).sum()).sum();
            return me.hand.stream().anyMatch(c -> Cards.get(c.id()).cost() > me.xp
                    && Cards.get(c.id()).cost() <= me.xp + storedXp
                    && (!Cards.get(c.id()).isMob() || me.rowFullReason(Cards.get(c.id()).keywords().contains(Keyword.BLOCK)) == null));
        }
        if (definition.isTool()) {
            return me.tool == null || me.tool.attack < definition.attack();
        }
        if (card.id().equals(Cards.LAVA_BUCKET)) {
            // Only worth it on a tool that would do real damage.
            return enemy.tool != null && enemy.tool.attack * enemy.tool.uses >= 3;
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
        Target.Kind kind = CardEffects.targetKind(card);
        if (kind == Target.Kind.NONE) {
            return Target.NONE;
        }
        List<Target> options = usefulTargets(match, side, card);
        if (options.isEmpty()) {
            return Target.NONE;
        }
        // Friendly buffs go on its strongest mob that can attack; anything aimed at enemies goes at their strongest mob.
        boolean healing = craftbound.card.CardCatalog.entry(card.id()).effects().stream()
                .anyMatch(e -> chosen(e) && (e.action() == craftbound.card.CardCatalog.Action.HEAL || e.action() == craftbound.card.CardCatalog.Action.WELL_FED));
        boolean cleansing = craftbound.card.CardCatalog.entry(card.id()).effects().stream()
                .anyMatch(e -> chosen(e) && e.action() == craftbound.card.CardCatalog.Action.CLEANSE);
        boolean freezing = CardCatalog.entry(card.id()).effects().stream().anyMatch(e -> chosen(e) && e.action() == CardCatalog.Action.FREEZE);
        boolean beneficial = CardCatalog.entry(card.id()).effects().stream().anyMatch(e -> chosen(e)
                && (e.action() == CardCatalog.Action.GRANT_KEYWORD || e.action() == CardCatalog.Action.BUFF && e.attack() >= 0));
        boolean directDamage = CardCatalog.entry(card.id()).effects().stream().anyMatch(e -> chosen(e)
                && (e.action() == CardCatalog.Action.DAMAGE || e.action() == CardCatalog.Action.TOOL_DAMAGE));
        int wantedSide = kind == Target.Kind.FRIENDLY_MOB || healing || cleansing || beneficial || grantsChosenTotem(card) ? side : 1 - side;
        Target best = null;
        int bestScore = Integer.MIN_VALUE;
        for (Target target : options) {
            if (!cleansing && target.side() != wantedSide && (kind == Target.Kind.ANY_MOB || kind == Target.Kind.ANY_CHARACTER)) {
                continue;
            }
            if (target.hero()) {
                int score = healing ? (match.player(target.side()).maxHealth - match.player(target.side()).health) * 5 : 1;
                if (directDamage && match.isEnemy(side, target.side())) {
                    int damage = chosenDamage(match, side, card, target);
                    if (damage >= match.player(target.side()).health) score += 10000;
                    else if (damage > 0 && match.player(target.side()).health <= 8) score += damage * 4;
                }
                if (freezing && !match.player(target.side()).frozen && !match.player(target.side()).skippedAttackFromFreeze) score += 40;
                if (cleansing) score += 12 * cleanseValue(match, side, target);
                if (score > bestScore) { bestScore = score; best = target; }
                continue;
            }
            Minion minion = match.player(target.side()).minion(target.minionId());
            int score = match.attackOf(minion) + minion.health + (minion.has(Keyword.BLOCK) && wantedSide == side ? -20 : 0);
            if (directDamage && match.isEnemy(side, target.side())) {
                int damage = chosenDamage(match, side, card, target);
                if (damage == 0) score = -100;
                else if (damage >= minion.health)
                    score = 40 + CombatTactics.boardValue(match, minion) / (minion.totem ? 2 : 1);
                else score = Math.min(damage, minion.health) + Math.min(8, match.attackOf(minion));
            }
            if (healing) score = (minion.maxHealth - minion.health) * 5;
            if (freezing && !minion.frozen && !minion.skippedAttackFromFreeze) score += 40;
            if (cleansing) score += 12 * cleanseValue(match, side, target);
            if (score > bestScore) {
                bestScore = score;
                best = target;
            }
        }
        return best == null ? Target.NONE : best;
    }

    private static boolean usePower(Match match, int side) {
        MatchPlayer me = match.player(side);
        if (match.whyCantUsePower(side) != null) {
            return false;
        }
        switch (HeroPowers.of(me)) {
            case FEED -> {
                Target best = null;int missing = 0;
                if (me.health < me.maxHealth) { best = Target.hero(side);missing = me.maxHealth - me.health; }
                for (Minion mob : me.board) if (mob.maxHealth - mob.health > missing) {
                    best = Target.mob(side, mob.id);missing = mob.maxHealth - mob.health;
                }
                if (best == null) return false;
                match.usePower(side, best);return true;
            }
            case THROW_POTION -> {
                int rival = match.opponentSide(side);
                Target aim = Target.hero(rival);
                if (match.player(rival).health > 1) {
                    Minion victim = match.player(rival).board.stream()
                            .filter(m -> !m.invulnerable && m.health <= m.afterArmor(1))
                            .max(Comparator.comparingInt(m -> CombatTactics.boardValue(match, m))).orElse(null);
                    if (victim != null) aim = Target.mob(rival, victim.id);
                }
                match.usePower(side, aim);
                return true;
            }
            case ACTIVATE -> {
                Minion block = me.board.stream().filter(HeroPowers::canActivate).findFirst().orElse(null);
                if (block == null) return false;
                match.usePower(side, Target.mob(side, block.id));return true;
            }
            case STRIKE -> { if (me.heroAttacked || me.frozen || me.skippedAttackFromFreeze) return false; }
            case EXCAVATE -> {
                // Prefer an Ore payout, but avoid burning it into a full hand.
                Minion ore = me.board.stream().filter(m -> HeroPowers.validExcavateTarget(match, side, Target.mob(side, m.id)))
                        .max(Comparator.comparingInt(m -> m.definition().cost())).orElse(null);
                if (ore != null && me.hand.size() < Match.MAX_HAND) {
                    match.usePower(side, Target.mob(side, ore.id));
                    return true;
                }
                // Never overwrite an upgraded/usable tool just to spend spare XP.
                if (me.tool != null) return false;
            }
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

    // Profitable kills and combinations have already been tried. Prefer face for remaining swings.
    private static boolean attackRemaining(Match match, int side) {
        Target face = Target.hero(match.opponentSide(side));
        MatchPlayer me = match.player(side);
        int bestScore = 0, bestAttacker = Match.HERO;
        Target best = Target.NONE;
        for (int i = 0; i <= me.board.size(); i++) {
            Minion mob = i < me.board.size() ? me.board.get(i) : null;
            int attacker = mob == null ? Match.HERO : mob.id;
            int power = mob == null ? match.heroAttackOf(side) : match.attackOf(mob);
            if (power <= 0 || (mob == null ? !match.heroCanAttack(side) : !mob.canAttack())) continue;
            if (match.whyCantAttack(side, attacker, face) == null) {
                match.attack(side, attacker, face);
                return true;
            }
            // A Shield or fresh Sprint can force a board target. Pass up worthless or fatal swings.
            for (Minion defender : match.opponent(side).board) {
                Target target = Target.mob(face.side(), defender.id);
                if (defender.invulnerable || match.whyCantAttack(side, attacker, target) != null) continue;
                int hit = Math.min(defender.health, defender.afterArmor(power));
                if (hit == 0) continue;
                int counter = match.retaliationOf(defender);
                if (mob == null && counter >= me.health) continue;
                int cost = mob == null ? counter : mob.invulnerable ? 0 : Math.min(mob.health, mob.afterArmor(counter));
                int score = 3 * hit - 2 * cost;
                if (mob != null && cost >= mob.health && !mob.totem) score -= power;
                if (score > bestScore) { bestScore = score; bestAttacker = attacker; best = target; }
            }
        }
        if (best.isNone()) return false;
        match.attack(side, bestAttacker, best);
        return true;
    }
}
