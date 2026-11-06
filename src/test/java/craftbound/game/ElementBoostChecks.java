package craftbound.game;

import craftbound.card.*;
import com.google.gson.*;
import static craftbound.game.WorkshopChecks.*;

// Elements, "If ..." boosts on effects, and Amplify element passives (Flint and Steel + Netherrack).
final class ElementBoostChecks {
    private static boolean parses(JsonObject root) {
        try { CardCatalog.parse(root.toString()); return true; } catch (IllegalArgumentException ex) { return false; }
    }
    private static JsonObject boost(String when, String card, String how, int amount) {
        var b = new JsonObject();
        b.addProperty("when", when);
        b.addProperty("card", card == null ? "" : "craftbound:" + card);
        b.addProperty("kind", "BOTH");
        b.addProperty("type", "");
        b.addProperty("how", how);
        if (how.equals("PLUS")) b.addProperty("amount", amount);
        return b;
    }

    static void run() {
        var original = CardCatalog.current();
        var root = JsonParser.parseString(original.json()).getAsJsonObject();
        var cards = root.getAsJsonArray("cards");
        SurvivingKillChecks.unit(root, "test_netherrack", 0, 5, true);
        var amp = SurvivingKillChecks.unit(root, "test_hellrack", 0, 5, true);
        amp.addProperty("passive", "AMPLIFY_ELEMENT");
        amp.addProperty("passiveAmount", 0);
        amp.addProperty("ampElement", "FIRE");
        amp.addProperty("ampHow", "DOUBLE");
        SurvivingKillChecks.unit(root, "test_dummy", 0, 30, false);

        var burn = newEffect("PLAY", "DAMAGE", "ENEMY_MOBS");
        burn.addProperty("amount", 3);
        burn.add("boost", boost("YOU_CONTROL", "test_netherrack", "DOUBLE", 0));
        fixture(cards, "bone_meal", "test_flint", burn).addProperty("element", "FIRE");

        var shatter = newEffect("PLAY", "DAMAGE", "CHOSEN_ENEMY");
        shatter.addProperty("amount", 2);
        shatter.add("boost", boost("TARGET_FROZEN", null, "TRIPLE", 0));
        fixture(cards, "bone_meal", "test_shatter", shatter).addProperty("element", "FROST");

        var smite = newEffect("PLAY", "DAMAGE", "CHOSEN_ENEMY");
        smite.addProperty("amount", 1);
        var undead = boost("TARGET_IS", null, "PLUS", 2);
        undead.addProperty("type", "UNDEAD");
        undead.addProperty("kind", "MOB");
        smite.add("boost", undead);
        fixture(cards, "bone_meal", "test_smite", smite);
        try {
            CardCatalog.install(CardCatalog.parse(root.toString()));
            String flintText = CardCatalog.description(CardCatalog.entry(id("test_flint")));
            ok(!flintText.startsWith("Fire.") && flintText.contains("damage to ") && flintText.contains("(doubled if you control "), "Flint text: " + flintText);
            ok(CardCatalog.description(CardCatalog.entry(id("test_hellrack"))).contains("Your Fire effects deal double damage."), "Amplify text");
            ok(CardCatalog.entry(id("test_flint")).element() == CardCatalog.Element.FIRE && CardCatalog.Element.ENDER.displayName().equals("Ender"), "Action element is kept for its type label");
            ok(CardCatalog.description(CardCatalog.entry(id("test_shatter"))).contains("(tripled against Frozen targets)"), "Frozen boost text");
            ok(CardCatalog.description(CardCatalog.entry(id("test_smite"))).contains("(+2 against Undead mobs)"), "Type boost text");

            // Flint and Steel: 3 damage, 6 with your Netherrack.
            var m = match();
            var dummy = mob(m, 1, "test_dummy");
            play(m, "test_flint");
            ok(dummy.health == 27, "Flint deals 3 without Netherrack: " + dummy.health);
            m = match();
            dummy = mob(m, 1, "test_dummy");
            mob(m, 0, "test_netherrack");
            play(m, "test_flint");
            ok(dummy.health == 24, "Flint deals 6 with your Netherrack: " + dummy.health);
            m = match();
            dummy = mob(m, 1, "test_dummy");
            mob(m, 1, "test_netherrack");
            play(m, "test_flint");
            ok(dummy.health == 27, "An enemy Netherrack doesn't count for If you control: " + dummy.health);

            // Amplify element: your Fire effects deal double damage, and stacks with the boost.
            m = match();
            dummy = mob(m, 1, "test_dummy");
            mob(m, 0, "test_hellrack");
            play(m, "test_flint");
            ok(dummy.health == 24, "Amplify doubles Fire damage: " + dummy.health);
            m = match();
            dummy = mob(m, 1, "test_dummy");
            mob(m, 0, "test_hellrack");
            mob(m, 0, "test_netherrack");
            play(m, "test_flint");
            ok(dummy.health == 18, "Boost and Amplify both apply (3 x2 x2): " + dummy.health);
            m = match();
            var wall = mob(m, 1, "test_netherrack");
            mob(m, 0, "test_hellrack");
            play(m, "test_smite", Target.mob(1, wall.id));
            ok(wall.health == 4, "Amplify ignores cards of other elements, and a mob-only boost skips blocks: " + wall.health);

            // Target conditions are checked per target.
            m = match();
            var frozen = mob(m, 1, "test_dummy");
            frozen.frozen = true;
            play(m, "test_shatter", Target.mob(1, frozen.id));
            ok(frozen.health == 24, "Triple against a Frozen target: " + frozen.health);
            m = match();
            var warm = mob(m, 1, "test_dummy");
            play(m, "test_shatter", Target.mob(1, warm.id));
            ok(warm.health == 28, "No boost against an unfrozen target: " + warm.health);
            if (Cards.get(id("zombie")).mobType() == MobType.UNDEAD) {
                m = match();
                var zombie = mob(m, 1, "zombie");
                int before = zombie.health;
                zombie.maxHealth = zombie.health = 20;
                play(m, "test_smite", Target.mob(1, zombie.id));
                ok(zombie.health == 17, "+2 against Undead mobs: " + zombie.health + " (was " + before + ")");
            }

            var bad = root.deepCopy();
            var summon = newEffect("PLAY", "DRAW", "SELF");
            summon.add("boost", boost("TARGET_FROZEN", null, "DOUBLE", 0));
            fixture(bad.getAsJsonArray("cards"), "bone_meal", "test_bad_boost", summon);
            ok(!parses(bad), "Target conditions need an action that hits targets");
            bad = root.deepCopy();
            var give = newEffect("PLAY", "GIVE", "SELF");
            give.addProperty("card", "craftbound:zombie");
            give.add("boost", boost("YOU_CONTROL", "test_netherrack", "DOUBLE", 0));
            fixture(bad.getAsJsonArray("cards"), "bone_meal", "test_bad_boost", give);
            ok(!parses(bad), "Boosts only go on number effects");
            bad = root.deepCopy();
            var unknown = newEffect("PLAY", "DAMAGE", "ENEMY_MOBS");
            unknown.add("boost", boost("YOU_CONTROL", "no_such_card", "DOUBLE", 0));
            fixture(bad.getAsJsonArray("cards"), "bone_meal", "test_bad_boost", unknown);
            ok(!parses(bad), "Boost cards must exist");
        } finally { CardCatalog.install(original); }
    }
}
