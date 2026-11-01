package craftbound.game;

import craftbound.card.Cards;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.resources.Identifier;

// What each card actually does in a match: its Spawn, its Drop, what an item does when played, what a mob does
// to whatever it damages, and "at the end of your turn" effects. Cards not listed here have no special effect.
public class CardEffects {
    // What a card has to be aimed at when you play it.
    public static Target.Kind targetKind(Identifier card) {
        if (card.equals(Cards.ARROW) || card.equals(Cards.PISTON)) return Target.Kind.ANY_MOB;
        if (card.equals(Cards.ENDERMAN)) return Target.Kind.ENEMY_MOB;
        if (card.equals(Cards.TOTEM_OF_UNDYING) || card.equals(Cards.ENCHANTED_GOLDEN_APPLE)
                || card.equals(Cards.POTION_OF_STRENGTH) || card.equals(Cards.BONE_MEAL)) return Target.Kind.FRIENDLY_MOB;
        return Target.Kind.NONE;
    }

    // Extra rules for when a card can be played, on top of cost, board space and targets.
    static String whyCantPlay(Match match, int side, Identifier card) {
        if (card.equals(Cards.SWEEPING_EDGE)) {
            if (match.player(side).tool == null) return "you need a tool";
            if (match.opponent(side).board.isEmpty()) return "nothing to hit";
        }
        if (card.equals(Cards.LAVA_BUCKET) && match.opponent(side).tool == null) {
            return "they have no tool";
        }
        return null;
    }

    // Spawn: happens when you play the mob.
    static void onSpawn(Match match, int side, Minion minion, Target target) {
        Identifier id = minion.card.id();
        if (id.equals(Cards.ENDERMAN) && !target.isNone()) {
            // Teleport an enemy mob back to its owner's hand.
            MatchPlayer enemy = match.player(target.side());
            Minion victim = enemy.minion(target.minionId());
            if (victim != null) {
                match.log("The Enderman teleports " + Match.name(victim.card) + " back to " + enemy.name + "'s hand.", Cards.ENDERMAN);
                match.bounce(victim);
            }
        } else if (id.equals(Cards.WARDEN)) {
            // Sonic Boom: 3 damage to every enemy mob.
            match.log("The Warden's Sonic Boom hits every enemy mob for 3.", Cards.WARDEN);
            for (Minion enemy : new ArrayList<>(match.opponent(side).board)) {
                match.damage(enemy, 3, null);
            }
        } else if (id.equals(Cards.WITHER)) {
            match.log("The Wither blasts every other mob for 2.", Cards.WITHER);
            for (Minion other : everyMob(match)) {
                if (other != minion) {
                    match.damage(other, 2, null);
                }
            }
        } else if (id.equals(Cards.EVOKER)) {
            match.log("The Evoker summons two Vexes.", Cards.EVOKER);
            match.summon(side, new CardRef(Cards.VEX, false));
            match.summon(side, new CardRef(Cards.VEX, false));
        } else if (id.equals(Cards.BEE)) {
            if (match.summon(side, new CardRef(Cards.WHEAT, false)) != null) {
                match.log("The Bee plants a Wheat.", Cards.WHEAT);
            }
        } else if (id.equals(Cards.SNIFFER)) {
            int dug = 0;
            for (int i = 0; i < 2; i++) {
                if (match.summon(side, new CardRef(Cards.WHEAT, false)) != null) {
                    dug++;
                }
            }
            if (dug > 0) {
                match.log("The Sniffer digs up " + dug + " Wheat.", Cards.WHEAT);
            }
        }
    }

    // Drop: happens when the mob dies (it's already off the board).
    static void onDrop(Match match, int side, Minion minion) {
        Identifier id = minion.card.id();
        if (id.equals(Cards.ZOMBIE)) {
            match.giveCard(side, new CardRef(Cards.ROTTEN_FLESH, false));
        } else if (id.equals(Cards.SKELETON)) {
            match.giveCard(side, new CardRef(Cards.ARROW, false));
        } else if (id.equals(Cards.CREEPER)) {
            match.log("The Creeper explodes, dealing 2 to every other mob!", Cards.CREEPER);
            for (Minion other : everyMob(match)) {
                match.damage(other, 2, null);
            }
        } else if (id.equals(Cards.CHARGED_CREEPER)) {
            match.log("The Charged Creeper explodes, dealing 4 to every other mob and the enemy hero!", Cards.CHARGED_CREEPER);
            for (Minion other : everyMob(match)) {
                match.damage(other, 4, null);
            }
            match.damageHero(1 - side, 4);
        } else if (id.equals(Cards.EVOKER)) {
            match.giveCard(side, new CardRef(Cards.TOTEM_OF_UNDYING, false));
        } else if (id.equals(Cards.WITHER)) {
            match.giveCard(side, new CardRef(Cards.NETHER_STAR, false));
        } else if (id.equals(Cards.ENDER_DRAGON)) {
            match.giveCard(side, new CardRef(Cards.DRAGON_EGG, false));
        }
    }

    // Items: what they do when played.
    static void onItem(Match match, int side, CardRef card, Target target) {
        Identifier id = card.id();
        MatchPlayer player = match.player(side);
        Minion aimed = target.isNone() ? null : match.player(target.side()).minion(target.minionId());
        if (id.equals(Cards.ROTTEN_FLESH)) {
            match.healHero(side, 2);
        } else if (id.equals(Cards.ARROW)) {
            if (aimed != null) {
                match.damage(aimed, 2, null);
            }
        } else if (id.equals(Cards.NETHER_STAR)) {
            match.draw(side);
            match.draw(side);
        } else if (id.equals(Cards.DRAGON_EGG)) {
            match.gainXp(side, 3, card);
        } else if (id.equals(Cards.EXPERIENCE_BOTTLE)) {
            match.gainXp(side, 1, card);
        } else if (id.equals(Cards.TOTEM_OF_UNDYING)) {
            if (aimed != null) {
                aimed.totem = true;
                match.status(aimed, MatchEvent.STATUS_TOTEM);
            }
        } else if (id.equals(Cards.ENCHANTED_GOLDEN_APPLE)) {
            if (aimed != null) {
                aimed.invulnerable = true;
                match.status(aimed, MatchEvent.STATUS_INVULNERABLE);
            }
        } else if (id.equals(Cards.POTION_OF_STRENGTH)) {
            if (aimed != null) {
                match.log(Match.name(aimed.card) + " gets +2 attack.", card);
                match.buff(aimed, 2, 0);
            }
        } else if (id.equals(Cards.BONE_MEAL)) {
            if (aimed != null) {
                match.log(Match.name(aimed.card) + " gets +1/+1 from Bone Meal.", card);
                match.buff(aimed, 1, 1);
            }
        } else if (id.equals(Cards.PISTON)) {
            if (aimed != null) {
                match.log("The Piston pushes " + Match.name(aimed.card) + " back to its owner's hand.", card);
                match.bounce(aimed);
            }
        } else if (id.equals(Cards.SWEEPING_EDGE)) {
            int amount = player.tool == null ? 0 : player.tool.attack;
            match.log(player.name + " sweeps every enemy mob for " + amount + "!", card);
            for (Minion enemy : new ArrayList<>(match.opponent(side).board)) {
                match.damage(enemy, amount, null);
            }
        } else if (id.equals(Cards.LAVA_BUCKET)) {
            // The other hero's tool melts.
            MatchPlayer enemy = match.opponent(side);
            if (enemy.tool != null) {
                match.log(player.name + " pours lava on " + enemy.name + "'s " + Match.name(enemy.tool.card) + "!", card);
                match.event(MatchEvent.Type.BREAK, 1 - side, Match.HERO, enemy.tool.card, 0, 0, null);
                enemy.tool = null;
            }
        } else if (id.equals(Cards.BOUNTIFUL_HARVEST)) {
            int planted = 0;
            for (int i = 0; i < 2; i++) {
                if (match.summon(side, new CardRef(Cards.WHEAT, false)) != null) {
                    planted++;
                }
            }
            match.log(player.name + " plants " + planted + " Wheat and draws a card.", card);
            match.draw(side);
        }
    }

    // Some mobs leave a mark on whatever they damage.
    static void onDamageDealt(Match match, Minion source, Minion victim) {
        Identifier id = source.card.id();
        if (id.equals(Cards.SHULKER) && !victim.frozen) {
            victim.frozen = true;
            match.status(victim, MatchEvent.STATUS_FROZEN);
        } else if (id.equals(Cards.WITHER_SKELETON) && !victim.withered) {
            victim.withered = true;
            match.status(victim, MatchEvent.STATUS_WITHERED);
        }
    }

    // "At the end of your turn" effects, in board order.
    static void onEndTurn(Match match, int side) {
        MatchPlayer player = match.player(side);
        for (Minion minion : new ArrayList<>(player.board)) {
            if (match.over || !player.board.contains(minion)) {
                continue;
            }
            Identifier id = minion.card.id();
            if (id.equals(Cards.FURNACE)) {
                match.log(player.name + "'s Furnace heals them 2.", Cards.FURNACE);
                match.healHero(side, 2);
            } else if (id.equals(Cards.DISPENSER)) {
                // 1 damage to a random enemy: their hero or one of their mobs.
                List<Target> options = new ArrayList<>();
                options.add(Target.hero(1 - side));
                for (Minion enemy : match.opponent(side).board) {
                    options.add(Target.mob(1 - side, enemy.id));
                }
                Target shot = options.get(match.random.nextInt(options.size()));
                match.log(player.name + "'s Dispenser fires for 1.", Cards.DISPENSER);
                match.event(MatchEvent.Type.SHOOT, side, minion.id, minion.card, 1, 0, shot);
                match.damage(shot, 1);
                match.resolveDeaths();
            } else if (id.equals(Cards.COMPOSTER)) {
                List<Minion> others = new ArrayList<>(player.board);
                others.remove(minion);
                if (!others.isEmpty()) {
                    Minion lucky = others.get(match.random.nextInt(others.size()));
                    match.log(player.name + "'s Composter gives " + Match.name(lucky.card) + " +1/+1.", Cards.COMPOSTER);
                    match.buff(lucky, 1, 1);
                }
            }
        }
    }

    private static List<Minion> everyMob(Match match) {
        List<Minion> all = new ArrayList<>(match.player(0).board);
        all.addAll(match.player(1).board);
        return all;
    }
}
