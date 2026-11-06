package craftbound.game;

import craftbound.card.*;
import com.google.gson.*;
import static craftbound.game.WorkshopChecks.*;

// Grant keyword: a Random option, and no granting a keyword to something that already has it.
final class KeywordGrantChecks {
    private static boolean parses(JsonObject root) {
        try { CardCatalog.parse(root.toString()); return true; } catch (IllegalArgumentException ex) { return false; }
    }
    private static boolean targetable(Match m, String card, Minion minion) {
        var id = id(card);
        return m.validTargets(0, CardEffects.targetKind(id), id).contains(Target.mob(0, minion.id));
    }

    static void run() {
        var original = CardCatalog.current();
        var root = JsonParser.parseString(original.json()).getAsJsonObject();
        var cards = root.getAsJsonArray("cards");
        var random = newEffect("PLAY", "GRANT_KEYWORD", "CHOSEN_FRIENDLY");
        random.addProperty("keyword", "RANDOM");
        fixture(cards, "bone_meal", "test_grant_random_kw", random);
        var sprint = newEffect("PLAY", "GRANT_KEYWORD", "CHOSEN_FRIENDLY");
        sprint.addProperty("keyword", "SPRINT");
        sprint.addProperty("boardKind", "MOB");
        fixture(cards, "bone_meal", "test_grant_sprint_kw", sprint);
        SurvivingKillChecks.unit(root, "test_kw_mob", 1, 3, false);
        SurvivingKillChecks.unit(root, "test_kw_wall", 0, 3, true);
        try {
            CardCatalog.install(CardCatalog.parse(root.toString()));
            ok(CardCatalog.description(CardCatalog.entry(id("test_grant_random_kw"))).contains("a random keyword it doesn't have"), "Random grant rules text");

            // Random only rolls keywords the target is missing.
            var m = match();
            var mob = mob(m, 0, "test_kw_mob");
            m.grantKeyword(mob, Keyword.SHIELD);
            m.grantKeyword(mob, Keyword.SPRINT);
            m.grantKeyword(mob, Keyword.RANGED);
            ok(targetable(m, "test_grant_random_kw", mob), "A mob missing one keyword can get a random one");
            play(m, "test_grant_random_kw", Target.mob(0, mob.id));
            ok(mob.has(Keyword.IGNORE_SHIELD), "Random grant picks the one keyword the mob lacks");
            ok(!targetable(m, "test_grant_random_kw", mob), "A mob with every keyword can't be targeted by a random grant");

            // A specific keyword can't go to something that already has it.
            m = match();
            var fast = mob(m, 0, "test_kw_mob");
            ok(targetable(m, "test_grant_sprint_kw", fast), "A mob without Sprint can be given Sprint");
            m.grantKeyword(fast, Keyword.SPRINT);
            ok(!targetable(m, "test_grant_sprint_kw", fast), "A mob that has Sprint can't be given Sprint");

            // Blocks only roll Shield, then are no longer a target.
            m = match();
            var wall = mob(m, 0, "test_kw_wall");
            play(m, "test_grant_random_kw", Target.mob(0, wall.id));
            ok(wall.has(Keyword.SHIELD) && !wall.has(Keyword.SPRINT) && !wall.has(Keyword.RANGED) && !wall.has(Keyword.IGNORE_SHIELD), "Random grant gives blocks Shield");
            ok(!targetable(m, "test_grant_random_kw", wall), "A Shield block can't get another random keyword");

            // Many rolls never repeat a keyword and stay within the four combat keywords.
            for (int i = 0; i < 20; i++) {
                m = match();
                var roll = mob(m, 0, "test_kw_mob");
                play(m, "test_grant_random_kw", Target.mob(0, roll.id));
                long count = java.util.stream.Stream.of(Keyword.SHIELD, Keyword.SPRINT, Keyword.RANGED, Keyword.IGNORE_SHIELD).filter(roll::has).count();
                ok(count == 1, "Random grant adds exactly one keyword");
            }

            var bad = root.deepCopy();
            var buff = newEffect("PLAY", "BUFF", "CHOSEN_FRIENDLY");
            buff.addProperty("attack", 1);
            buff.addProperty("keyword", "RANDOM");
            fixture(bad.getAsJsonArray("cards"), "bone_meal", "test_bad_random_buff", buff);
            ok(!parses(bad), "Only Grant keyword can pick a random keyword");
        } finally { CardCatalog.install(original); }
    }
}
