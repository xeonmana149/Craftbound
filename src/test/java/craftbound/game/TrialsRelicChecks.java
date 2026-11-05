package craftbound.game;

import com.google.gson.JsonParser;
import craftbound.card.Cards;
import java.util.*;

/** Save, transaction and vocabulary checks for the expedition's earned item strip. */
final class TrialsRelicChecks {
    static void run() {
        var run = TrialsRun.create(83);
        check(run.view("").getAsJsonArray("acquiredBoons").isEmpty(), "no fake bag before a boon is earned");
        boon(run, "HEART");
        boon(run, "PACK");
        boon(run, "HEART");
        check(run.acquiredBoons.stream().map(TrialBoons.Earned::id).toList().equals(List.of("HEART", "PACK", "HEART")),
                "each acquisition remains in order, including duplicate boons");
        check(run.bonusHealth == 8 && run.pack == 1, "recording icons does not change bonuses");
        check(run.acquiredBoons.getFirst().iconItem().equals("minecraft:golden_apple")
                && run.acquiredBoons.get(1).description().contains("1 extra opening card"), "item icons explain their real bonuses");
        var saved = TrialsRun.JSON.fromJson(TrialsRun.JSON.toJson(run), TrialsRun.class);
        check(saved.acquiredBoons.equals(run.acquiredBoons), "relics survive save and reload");
        TrialsRules.find(TrialsRules.list(saved.rules, "boons"), "HEART").addProperty("title", "Edited later");
        check(saved.view("").getAsJsonArray("acquiredBoons").get(0).getAsJsonObject().get("title").getAsString().equals("Golden Heart"),
                "earned names remain a snapshot");

        int before = run.acquiredBoons.size(), hp = run.health;
        run.deck.add(Cards.ZOMBIE.toString()); run.training.add(TrialTraining.NONE);
        chooseBoon(run, "JOURNEYMAN");
        check(run.acquiredBoons.size() == before && run.health == hp, "pending selection grants neither icon nor benefit");
        check(run.leaveChoice() && run.acquiredBoons.size() == before, "cancelling has no relic side effects");

        var custom = JsonParser.parseString("""
                {"id":"EMBER","title":"Portal Ember","description":"Gain one starting XP.",
                 "iconItem":"minecraft:fire_charge","operations":[{"type":"START_XP","amount":1}]}
                """).getAsJsonObject();
        run.rules.getAsJsonArray("rest").add(custom);
        run.phase = TrialsRun.Phase.REST; run.choices = new ArrayList<>(TrialsRules.ids(run.options(run.phase)));
        check(run.pick(run.choices.indexOf("EMBER")), "take lasting encounter reward");
        check(run.acquiredBoons.getLast().id().equals("EMBER") && run.acquiredBoons.getLast().description().contains("+1 maximum XP"),
                "lasting event bonus recorded separately");

        var capped = TrialsRun.create(22);
        var packOption = TrialsRules.find(TrialsRules.list(capped.rules, "boons"), "PACK");
        packOption.getAsJsonArray("operations").get(0).getAsJsonObject().addProperty("amount", 4);
        boon(capped, "PACK"); boon(capped, "PACK");
        check(capped.pack == 4 && capped.acquiredBoons.getLast().description().contains("already at its limit"),
                "capped authored bonuses never claim an unapplied benefit");

        var old = TrialsRun.create(12);
        old.acquiredBoons = null; old.bonusHealth = 8; old.pack = 2; old.torch = 2;
        old.boonUses.put("HEART", 2); old.boonUses.put("PACK", 2); old.boonUses.put("TORCH", 1);
        check(TrialBoons.ensure(old) && !TrialBoons.ensure(old), "old journals migrate exactly once");
        check(old.acquiredBoons.size() == 6 && old.acquiredBoons.getLast().id().equals("legacy_xp"),
                "recover acquired boons and remaining event bonus");
        check(old.bonusHealth == 8 && old.pack == 2 && old.torch == 2, "legacy recovery is presentation only");

        var currency = JsonParser.parseString("""
                {"startMarks":3,"options":[{"id":"MARKS","title":"Take 2 Marks",
                 "description":"Gain 1 Mark.","operations":[{"type":"MARKS","amount":2}]}],
                 "descriptions":["Spend 3 Marks."],"notes":"Landmarks"}
                """).getAsJsonObject();
        check(TrialsText.migrateCurrency(currency) && !TrialsText.migrateCurrency(currency), "wording migration is idempotent");
        var option = currency.getAsJsonArray("options").get(0).getAsJsonObject();
        check(option.get("title").getAsString().equals("Take 2 Emeralds")
                && option.get("description").getAsString().equals("Gain 1 Emerald."), "singular and plural names");
        check(currency.get("startMarks").getAsInt() == 3 && option.get("id").getAsString().equals("MARKS")
                && option.getAsJsonArray("operations").get(0).getAsJsonObject().get("type").getAsString().equals("MARKS")
                && currency.get("notes").getAsString().equals("Landmarks"), "saved keys and unrelated words remain unchanged");
        System.out.println("PASS: Trials relic order, duplicate icons, save snapshots, cancelled choices, event bonuses, old saves and Emerald wording.");
    }
    private static void chooseBoon(TrialsRun run, String id) {
        run.phase = TrialsRun.Phase.BOON;
        run.choices = new ArrayList<>(TrialsRules.ids(run.options(run.phase)));
        check(run.pick(run.choices.indexOf(id)), "choose " + id);
    }
    private static void boon(TrialsRun run, String id) { chooseBoon(run, id); }
    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError("Trials relics: " + message);
    }
    private TrialsRelicChecks() {}
}
