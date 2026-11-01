package craftbound.game;

import craftbound.card.Cards;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.resources.Identifier;

// What each card actually does in a match: its Spawn, its Drop, what an item does when played, and what a
// mob does to whatever it damages. Cards not listed here have no special effect.
public class CardEffects {
    // What a card has to be aimed at when you play it.
    public static Target.Kind targetKind(Identifier card) {
        if (card.equals(Cards.ARROW)) return Target.Kind.ANY_MOB;
        if (card.equals(Cards.ENDERMAN)) return Target.Kind.ENEMY_MOB;
        if (card.equals(Cards.TOTEM_OF_UNDYING) || card.equals(Cards.ENCHANTED_GOLDEN_APPLE)) return Target.Kind.FRIENDLY_MOB;
        return Target.Kind.NONE;
    }

    // Spawn: happens when you play the mob.
    static void onSpawn(Match match, int side, Minion minion, Target target) {
        Identifier id = minion.card.id();
        if (id.equals(Cards.ENDERMAN) && !target.isNone()) {
            // Teleport an enemy mob back to its owner's hand.
            MatchPlayer enemy = match.player(target.side());
            Minion victim = enemy.minion(target.minionId());
            if (victim != null) {
                enemy.board.remove(victim);
                match.log("The Enderman teleports " + Match.name(victim.card) + " back to " + enemy.name + "'s hand.");
                match.giveCard(target.side(), victim.card);
            }
        } else if (id.equals(Cards.WARDEN)) {
            // Sonic Boom: 3 damage to every enemy mob.
            match.log("The Warden's Sonic Boom hits every enemy mob for 3.");
            for (Minion enemy : new ArrayList<>(match.opponent(side).board)) {
                match.damage(enemy, 3, null);
            }
        } else if (id.equals(Cards.WITHER)) {
            match.log("The Wither blasts every other mob for 2.");
            for (Minion other : everyMob(match)) {
                if (other != minion) {
                    match.damage(other, 2, null);
                }
            }
        } else if (id.equals(Cards.EVOKER)) {
            match.log("The Evoker summons two Vexes.");
            match.summon(side, new CardRef(Cards.VEX, false));
            match.summon(side, new CardRef(Cards.VEX, false));
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
            match.log("The Creeper explodes, dealing 2 to every other mob!");
            for (Minion other : everyMob(match)) {
                match.damage(other, 2, null);
            }
        } else if (id.equals(Cards.CHARGED_CREEPER)) {
            match.log("The Charged Creeper explodes, dealing 4 to every other mob and the enemy hero!");
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
        if (id.equals(Cards.ROTTEN_FLESH)) {
            match.healHero(side, 2);
        } else if (id.equals(Cards.ARROW)) {
            Minion victim = match.player(target.side()).minion(target.minionId());
            if (victim != null) {
                match.damage(victim, 2, null);
            }
        } else if (id.equals(Cards.NETHER_STAR)) {
            match.draw(side);
            match.draw(side);
        } else if (id.equals(Cards.DRAGON_EGG)) {
            player.xp += 3;
        } else if (id.equals(Cards.EXPERIENCE_BOTTLE)) {
            player.xp += 1;
        } else if (id.equals(Cards.TOTEM_OF_UNDYING)) {
            Minion friend = player.minion(target.minionId());
            if (friend != null) {
                friend.totem = true;
            }
        } else if (id.equals(Cards.ENCHANTED_GOLDEN_APPLE)) {
            Minion friend = player.minion(target.minionId());
            if (friend != null) {
                friend.invulnerable = true;
            }
        }
    }

    // Some mobs leave a mark on whatever they damage.
    static void onDamageDealt(Match match, Minion source, Minion victim) {
        Identifier id = source.card.id();
        if (id.equals(Cards.SHULKER)) {
            victim.frozen = true;
        } else if (id.equals(Cards.WITHER_SKELETON)) {
            victim.withered = true;
        }
    }

    private static List<Minion> everyMob(Match match) {
        List<Minion> all = new ArrayList<>(match.player(0).board);
        all.addAll(match.player(1).board);
        return all;
    }
}
