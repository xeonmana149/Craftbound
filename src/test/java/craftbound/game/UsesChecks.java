package craftbound.game;

import craftbound.card.*;
import com.google.gson.*;
import static craftbound.game.WorkshopChecks.*;

// Limited uses: a Cake heals a random friendly 4 times at the end of your turn, then is destroyed.
final class UsesChecks {
    private static boolean parses(JsonObject root) {
        try { CardCatalog.parse(root.toString()); return true; } catch (IllegalArgumentException ex) { return false; }
    }

    static void run() {
        var original = CardCatalog.current();
        var root = JsonParser.parseString(original.json()).getAsJsonObject();
        var cake = SurvivingKillChecks.unit(root, "test_cake", 0, 3, true);
        var effects = new JsonArray();
        var heal = newEffect("END_TURN", "HEAL", "RANDOM_FRIENDLY");
        heal.addProperty("amount", 2);
        effects.add(heal);
        cake.add("effects", effects);
        cake.addProperty("autoText", true);
        cake.addProperty("uses", 4);
        try {
            CardCatalog.install(CardCatalog.parse(root.toString()));
            String text = CardCatalog.description(CardCatalog.entry(id("test_cake")));
            ok(text.endsWith("After 4 uses, this is destroyed."), "Uses text: " + text);

            var m = match();
            var placed = mob(m, 0, "test_cake");
            ok(placed.usesLeft() == 4, "A fresh Cake has 4 uses");
            for (int i = 1; i <= 3; i++) {
                CardEffects.run(m, 0, placed.card, placed, CardCatalog.Trigger.END_TURN, Target.NONE);
                m.resolveDeaths();
                ok(placed.usesLeft() == 4 - i && m.player(0).board.contains(placed), "Use " + i + " leaves " + (4 - i));
            }
            ok(m.events.stream().anyMatch(e -> e.type() == MatchEvent.Type.STATUS && e.amount() == MatchEvent.STATUS_USES), "Spending a use tells the client");
            CardEffects.run(m, 0, placed.card, placed, CardCatalog.Trigger.END_TURN, Target.NONE);
            m.resolveDeaths();
            ok(!m.player(0).board.contains(placed), "The 4th use destroys the Cake");
            var zombie = mob(m, 0, "zombie");
            ok(zombie.usesLeft() == -1, "Ordinary cards have no use limit");

            var bad = root.deepCopy();
            for (var c : bad.getAsJsonArray("cards")) {
                var o = c.getAsJsonObject();
                if (o.get("id").getAsString().equals("craftbound:bone_meal")) o.addProperty("uses", 2);
            }
            ok(!parses(bad), "Actions can't have limited uses");
            bad = root.deepCopy();
            SurvivingKillChecks.unit(bad, "test_bad_uses", 0, 3, true).addProperty("uses", 2);
            ok(!parses(bad), "Limited uses need a triggered ability");
        } finally { CardCatalog.install(original); }
    }
}
