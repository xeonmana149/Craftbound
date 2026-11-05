package craftbound.game;

import craftbound.card.Cards;
import craftbound.card.MobType;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

// Each class's power costs Match.POWER_COST XP, once per turn.
// Retired enum values stay in place so persisted/network ordinals remain stable.
public class HeroPowers {
    public enum Power {
        CRAFT, BUILD, SOW, REAP, NONE, FISH, BREW, EXCAVATE, FEED, CALL_PUP, STRIKE, THROW_POTION, PROSPECT, ACTIVATE;

        public Component displayName() {
            return Component.translatable("power.craftbound." + name().toLowerCase(Locale.ROOT));
        }

        public Component description() {
            return Component.translatable("power.craftbound." + name().toLowerCase(Locale.ROOT) + ".desc");
        }

        // The Minecraft item that stands in for the power's icon.
        public Identifier icon() {
            return Identifier.withDefaultNamespace(switch (this) {
                case CRAFT -> "wooden_sword";
                case BUILD -> "cobblestone_wall";
                case SOW -> "wheat_seeds";
                case REAP -> "iron_hoe";
                case NONE -> "barrier";
                case FISH -> "fishing_rod";
                case BREW -> "brewing_stand";
                case EXCAVATE -> "iron_pickaxe";
                case FEED -> "cooked_porkchop";
                case CALL_PUP -> "bone";
                case STRIKE -> "iron_sword";
                case THROW_POTION -> "splash_potion";
                case PROSPECT -> "iron_pickaxe";
                case ACTIVATE -> "redstone";
            });
        }
    }

    public static Power of(MatchPlayer player) {
        return of(player.heroClass);
    }

    public static Power of(craftbound.card.CardClass heroClass) {
        return switch (heroClass) {
            case FIGHTER -> Power.CRAFT;
            case BUILDER -> Power.BUILD;
            case FARMER -> Power.SOW;
            case NEUTRAL -> Power.NONE;
            case FISHERMAN -> Power.FISH;
            case ALCHEMIST -> Power.THROW_POTION;
            case MINER -> Power.PROSPECT;
            case CHEF -> Power.FEED;
            case TAMER -> Power.CALL_PUP;
            case REDSTONER -> Power.ACTIVATE;
        };
    }

    // Why the class part of the power can't be used right now (XP and once-per-turn are checked by Match).
    static String whyCantUse(Match match, int side) {
        MatchPlayer player = match.player(side);
        return switch (of(player)) {
            case NONE -> "no power";
            case BUILD, SOW -> player.rowFullReason(true);
            case CALL_PUP, FISH -> player.rowFullReason(false);
            case CRAFT, REAP, FEED, EXCAVATE, STRIKE, THROW_POTION -> null;
            case ACTIVATE -> player.board.stream().noneMatch(HeroPowers::canActivate) ? "you need a friendly block with Activate" : null;
            case PROSPECT, BREW -> player.hand.size() >= Match.MAX_HAND ? "your hand is full" : null;
        };
    }

    static void use(Match match, int side, Target target) {
        MatchPlayer player = match.player(side);
        switch (of(player)) {
            case CRAFT -> {
                match.log(player.name + " crafts a Wooden Sword.", Cards.CRAFTED_SWORD);
                match.equip(side, new CardRef(Cards.CRAFTED_SWORD, false));
            }
            case BUILD -> {
                match.log(player.name + " builds a Cobblestone Wall.", Cards.COBBLESTONE_WALL);
                match.summon(side, new CardRef(Cards.COBBLESTONE_WALL, false));
            }
            case SOW -> {
                match.log(player.name + " plants a Crop.", Cards.WHEAT);
                match.summon(side, new CardRef(Cards.WHEAT, false));
                player.reap = false;
            }
            case REAP -> {
                List<Minion> crops = crops(player);
                match.log(player.name + " reaps " + crops.size() + (crops.size() == 1 ? " crop" : " crops")
                        + " and draws a card for each.", Cards.WHEAT);
                for (Minion crop : crops) {
                    match.remove(crop);
                    CardEffects.run(match, side, crop.card, crop, craftbound.card.CardCatalog.Trigger.HARVEST, Target.NONE);
                }
                player.reap = false;
                for (int i = 0; i < crops.size(); i++) {
                    match.draw(side);
                }
            }
            case NONE -> {
            }
            case FISH -> {
                var pool = List.of(Cards.CAUGHT_COD, Identifier.parse("craftbound:caught_salmon"), Identifier.parse("craftbound:caught_pufferfish"));
                match.summon(side, new CardRef(pool.get(match.random.nextInt(pool.size())), false));
            }
            case STRIKE -> {
                player.temporaryHeroAttack += 2;
                match.event(MatchEvent.Type.HERO_ATTACK, side, Match.HERO, null, match.heroAttackOf(side), 0, null);
            }
            case THROW_POTION -> match.damage(target, 1);
            case PROSPECT -> match.giveCard(side, new CardRef(Identifier.parse("craftbound:iron_ore_token"), false));
            case ACTIVATE -> {
                Minion block = player.minion(target.minionId());
                CardEffects.run(match, side, block.card, block, craftbound.card.CardCatalog.Trigger.ACTIVATE, Target.NONE);
            }
            case BREW -> match.giveCard(side, new CardRef(Cards.MINOR_HARMING_POTION, false));
            case EXCAVATE -> {
                if (target.isNone()) match.equip(side, new CardRef(Cards.EXCAVATED_PICKAXE, false));
                else match.mine(player.minion(target.minionId()));
            }
            case CALL_PUP -> match.summon(side, new CardRef(Cards.WOLF_PUP, false));
            case FEED -> {
                match.heal(target, 2, false);
            }
        }
    }

    // No target explicitly selects the pickaxe option. Any supplied target must be a living friendly Ore.
    public static boolean validExcavateTarget(Match match, int side, Target target) {
        if (target == null) return false;
        if (target.equals(Target.NONE)) return true;
        if (target.hero() || target.side() != side) return false;
        Minion ore = match.player(side).minion(target.minionId());
        return ore != null && !ore.isDead() && ore.has(craftbound.card.Keyword.BLOCK)
                && ore.definition().mobType() == MobType.ORE;
    }

    public static boolean validFeedTarget(Match match, Target target) {
        if (target == null || target.isNone() || !match.inPlay(target.side())) return false;
        if (target.hero()) return true;
        Minion mob = match.player(target.side()).minion(target.minionId());
        return mob != null && !mob.isDead();
    }

    public static boolean canActivate(Minion mob) {
        return !mob.isDead() && mob.has(craftbound.card.Keyword.BLOCK)
                && craftbound.card.CardCatalog.entry(mob.card.id()).effects().stream().anyMatch(e -> e.trigger() == craftbound.card.CardCatalog.Trigger.ACTIVATE);
    }

    public static boolean targeted(Power power) { return power == Power.FEED || power == Power.THROW_POTION || power == Power.ACTIVATE; }

    public static boolean validTarget(Match match, int side, Target target) {
        Power power = of(match.player(side));
        if (power == Power.EXCAVATE) return validExcavateTarget(match, side, target);
        if (!targeted(power)) return target == null || target.isNone();
        if (target == null || target.isNone() || target.side() < 0 || !match.inPlay(target.side())) return false;
        if (power == Power.FEED || power == Power.THROW_POTION) return validFeedTarget(match, target);
        Minion mob = target.hero() ? null : match.player(side).minion(target.minionId());
        return target.side() == side && mob != null && canActivate(mob);
    }

    public static boolean isCrop(Minion minion) {
        return minion.definition().mobType() == MobType.CROP;
    }

    public static List<Minion> crops(MatchPlayer player) {
        List<Minion> crops = new ArrayList<>();
        for (Minion minion : player.board) {
            if (isCrop(minion)) {
                crops.add(minion);
            }
        }
        return crops;
    }

    private HeroPowers() {
    }
}
