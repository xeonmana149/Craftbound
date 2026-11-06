package craftbound.game;

import craftbound.card.*;
import com.google.gson.*;
import static craftbound.game.WorkshopChecks.*;

// Negate keyword passive: e.g. Soul Sand switches off enemy Sprint while it is on the board.
final class NegateKeywordChecks {
    private static boolean parses(JsonObject root) {
        try { CardCatalog.parse(root.toString()); return true; } catch (IllegalArgumentException ex) { return false; }
    }

    static void run() {
        var original = CardCatalog.current();
        var root = JsonParser.parseString(original.json()).getAsJsonObject();
        var sand = SurvivingKillChecks.unit(root, "test_soul_sand", 0, 4, true);
        sand.addProperty("passive", "NEGATE_KEYWORD");
        sand.addProperty("passiveAmount", 0);
        sand.addProperty("negateKeyword", "SPRINT");
        SurvivingKillChecks.unit(root, "test_sprinter", 2, 2, false).add("keywords", JsonParser.parseString("[\"SPRINT\"]"));
        var breaker = SurvivingKillChecks.unit(root, "test_wall_breaker", 1, 3, false);
        breaker.addProperty("passive", "NEGATE_KEYWORD");
        breaker.addProperty("passiveAmount", 0);
        breaker.addProperty("negateKeyword", "SHIELD");
        breaker.addProperty("auraKind", "BLOCK");
        SurvivingKillChecks.unit(root, "test_shield_block", 0, 3, true).add("keywords", JsonParser.parseString("[\"BLOCK\",\"SHIELD\"]"));
        SurvivingKillChecks.unit(root, "test_shield_mob", 1, 3, false).add("keywords", JsonParser.parseString("[\"SHIELD\"]"));
        try {
            CardCatalog.install(CardCatalog.parse(root.toString()));
            ok(CardCatalog.description(CardCatalog.entry(id("test_soul_sand"))).contains("Enemy mobs and blocks lose Sprint while this is in play."), "Negate keyword rules text");

            var m = match();
            var soulSand = mob(m, 1, "test_soul_sand");
            var runner = mob(m, 0, "test_sprinter");
            ok(!runner.has(Keyword.SPRINT) && !runner.ready, "An enemy Sprint mob played into Soul Sand can't attack");
            ok(m.whyCantAttack(0, runner.id, Target.mob(1, soulSand.id)) != null, "No Sprint attack while Soul Sand is out");
            var friendly = mob(m, 1, "test_sprinter");
            ok(friendly.has(Keyword.SPRINT) && friendly.ready, "Soul Sand doesn't slow its own side");

            m.damage(Target.mob(1, soulSand.id), 10);
            m.resolveDeaths();
            ok(runner.has(Keyword.SPRINT) && runner.ready, "Sprint comes back when Soul Sand leaves play");

            // Played the other way round: Soul Sand arriving takes Sprint from a fresh mob.
            m = match();
            var early = mob(m, 0, "test_sprinter");
            ok(early.ready, "Sprint mob is ready before Soul Sand");
            mob(m, 1, "test_soul_sand");
            ok(!early.has(Keyword.SPRINT) && !early.ready, "Soul Sand arriving removes Sprint from a fresh mob");
            ok(m.events.stream().anyMatch(e -> e.type() == MatchEvent.Type.STATUS && e.amount() == MatchEvent.STATUS_NEGATED
                    && e.extra() == Keyword.SPRINT.ordinal()), "Negation is shown on the board");

            // A granted keyword is negated too.
            m = match();
            mob(m, 1, "test_soul_sand");
            var plain = mob(m, 0, "zombie");
            m.grantKeyword(plain, Keyword.SPRINT);
            ok(!plain.has(Keyword.SPRINT) && !plain.ready, "Granted Sprint is negated as well");

            // "Negate affects" picks mobs, blocks or both.
            ok(CardCatalog.description(CardCatalog.entry(id("test_wall_breaker"))).contains("Enemy blocks lose Shield while this is in play."), "Blocks-only wording");
            m = match();
            mob(m, 1, "test_wall_breaker");
            var shieldBlock = mob(m, 0, "test_shield_block");
            var shieldMob = mob(m, 0, "test_shield_mob");
            ok(!shieldBlock.has(Keyword.SHIELD) && shieldMob.has(Keyword.SHIELD), "A blocks-only negate leaves enemy mobs alone");

            var bad = root.deepCopy();
            for (var c : bad.getAsJsonArray("cards")) {
                var card = c.getAsJsonObject();
                if (card.get("id").getAsString().equals("craftbound:test_soul_sand")) card.addProperty("negateKeyword", "BLOCK");
            }
            ok(!parses(bad), "Negate keyword only takes combat keywords");
        } finally { CardCatalog.install(original); }
    }
}
