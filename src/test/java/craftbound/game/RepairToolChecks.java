package craftbound.game;

import craftbound.card.*;
import com.google.gson.*;
import static craftbound.game.WorkshopChecks.*;

// Repair: a tool keyword that lets the hero spend its attack healing one of its own damaged blocks.
final class RepairToolChecks {
    private static boolean parses(JsonObject root) {
        try { CardCatalog.parse(root.toString()); return true; } catch (IllegalArgumentException ex) { return false; }
    }

    static void run() {
        var original = CardCatalog.current();
        var root = JsonParser.parseString(original.json()).getAsJsonObject();
        var hammer = AuraAndClassCardChecks.copy(root, "iron_sword", "test_repair_hammer");
        hammer.addProperty("attack", 2);
        hammer.addProperty("health", 2);
        hammer.add("keywords", JsonParser.parseString("[\"REPAIR\"]"));
        SurvivingKillChecks.unit(root, "test_repair_wall", 0, 6, true);
        SurvivingKillChecks.unit(root, "test_repair_mob", 1, 6, false);
        SurvivingKillChecks.unit(root, "test_shield_wall", 0, 5, true).add("keywords", JsonParser.parseString("[\"BLOCK\",\"SHIELD\"]"));
        try {
            CardCatalog.install(CardCatalog.parse(root.toString()));
            ok(Cards.get(id("test_repair_hammer")).keywords().contains(Keyword.REPAIR), "Tools can have Repair");

            var m = match();
            m.equip(0, new CardRef(id("test_repair_hammer"), false));
            var wall = mob(m, 0, "test_repair_wall");
            var ownMob = mob(m, 0, "test_repair_mob");
            var enemyWall = mob(m, 1, "test_repair_wall");
            ok(m.whyCantAttack(0, Match.HERO, Target.mob(0, wall.id)) != null, "An undamaged block can't be repaired");
            wall.health = 3;
            ownMob.health = 3;
            ok(m.whyCantAttack(0, Match.HERO, Target.mob(0, ownMob.id)) != null, "Repair only works on blocks");
            ok(m.whyCantAttack(0, Match.HERO, Target.mob(0, wall.id)) == null, "A damaged own block can be repaired");
            int enemyHealth = m.player(1).health, heroHealth = m.player(0).health;
            m.attack(0, Match.HERO, Target.mob(0, wall.id));
            ok(wall.health == 5, "Repair heals by the tool's Attack: " + wall.health);
            ok(m.player(0).tool != null && m.player(0).tool.uses == 1, "Repair costs 1 Durability");
            ok(m.player(0).heroAttacked && !m.heroCanAttack(0), "Repair uses the hero's attack");
            ok(m.player(1).health == enemyHealth && m.player(0).health == heroHealth && enemyWall.health == 6, "Repair hurts nobody");
            ok(m.events.stream().anyMatch(e -> e.type() == MatchEvent.Type.HEAL && e.mid() == wall.id && e.amount() == 2), "Repair sends a heal");
            ok(m.events.stream().anyMatch(e -> e.type() == MatchEvent.Type.ATTACK && e.extra() == MatchEvent.ATTACK_REPAIR), "Repair is marked for the screen");

            // Healing never goes over the block's maximum, and the last use breaks the tool.
            m = match();
            m.equip(0, new CardRef(id("test_repair_hammer"), false));
            m.player(0).tool.uses = 1;
            var nearlyFull = mob(m, 0, "test_repair_wall");
            nearlyFull.health = 5;
            m.attack(0, Match.HERO, Target.mob(0, nearlyFull.id));
            ok(nearlyFull.health == 6 && m.player(0).tool == null, "Repair caps at max Health and the last use breaks the tool");

            // The hammer still attacks enemies normally.
            m = match();
            m.equip(0, new CardRef(id("test_repair_hammer"), false));
            int before = m.player(1).health;
            m.attack(0, Match.HERO, Target.hero(1));
            ok(m.player(1).health == before - 2, "A Repair tool still attacks enemies");

            // Without Repair, your own blocks are never a target.
            m = match();
            m.equip(0, new CardRef(id("iron_sword"), false));
            var plain = mob(m, 0, "test_repair_wall");
            plain.health = 2;
            ok(m.whyCantAttack(0, Match.HERO, Target.mob(0, plain.id)) != null, "Ordinary tools can't hit your own blocks");

            // Shield blocks: printed Shield sticks. Row-limited Shield (front row stays open) is in RowShieldChecks.
            m = match();
            var front = mob(m, 1, "test_repair_mob");
            var shieldWall = mob(m, 1, "test_shield_wall");
            var attacker = mob(m, 0, "test_repair_mob");
            attacker.ready = true; attacker.summonedThisTurn = false;
            ok(shieldWall.has(Keyword.SHIELD), "Blocks keep a printed Shield");
            ok(m.whyCantAttack(0, attacker.id, Target.mob(1, front.id)) == null, "A Shield block doesn't guard the front row");
            ok(m.whyCantAttack(0, attacker.id, Target.mob(1, shieldWall.id)) != null, "A Shield block still sits behind the front row");

            var bad = root.deepCopy();
            SurvivingKillChecks.unit(bad, "test_bad_repair_mob", 1, 1, false).add("keywords", JsonParser.parseString("[\"REPAIR\"]"));
            ok(!parses(bad), "Repair is rejected on mobs");
        } finally { CardCatalog.install(original); }
    }
}
