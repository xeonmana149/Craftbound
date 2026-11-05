package craftbound.game;

import com.google.gson.JsonObject;
import java.util.*;

/** Earned relics are snapshots, so later Workshop edits cannot change what a run's icons describe. */
final class TrialBoons {
    record Earned(String id, String title, String description, String iconItem) {}

    static List<Earned> visible(TrialsRun run) {
        return run.acquiredBoons == null ? recover(run) : List.copyOf(run.acquiredBoons);
    }
    static boolean ensure(TrialsRun run) {
        if (run.acquiredBoons != null) return false;
        run.acquiredBoons = new ArrayList<>(recover(run));
        return true;
    }

    static void record(TrialsRun run, JsonObject option, int healthBefore, int drawBefore, int xpBefore) {
        int health = run.bonusHealth - healthBefore, draw = run.pack - drawBefore, xp = run.torch - xpBefore;
        boolean boon = option.has("boon");
        if (!boon && health == 0 && draw == 0 && xp == 0) return;
        String id = TrialsRules.s(option, "id", "boon");
        String title = TrialsRules.s(option, "title", "Expedition boon");
        if (!boon && run.room() != null && run.room().kind() == TrialsAct.Kind.EVENT) {
            id = run.room().event() + "/" + id;
            title = run.room().title();
        }
        String description = bonusDescription(option, health, draw, xp);
        if (description.isEmpty()) description = "Received: " + TrialsText.currency(TrialsRules.s(option, "description", title));
        else if (health > 0 || draw > 0 || xp > 0) description += " Lasts for this expedition.";
        run.acquiredBoons.add(new Earned(id, TrialsText.currency(title), description, icon(option)));
    }

    /** Old journals tracked counts and totals, not acquisition order. Reconstruct without granting anything twice. */
    private static List<Earned> recover(TrialsRun run) {
        var out = new ArrayList<Earned>();
        int health = Math.max(0, run.bonusHealth), draw = Math.max(0, run.pack), xp = Math.max(0, run.torch);
        if (run.rules != null && run.rules.has("boons")) {
            for (var option : TrialsRules.list(run.rules, "boons")) {
                String id = TrialsRules.s(option, "id", "");
                for (int i = 0; i < run.boonUses.getOrDefault(id, 0); i++) {
                    int h = Math.min(health, amount(option, "MAX_HEALTH"));
                    int d = Math.min(draw, amount(option, "OPENING_DRAW"));
                    int x = Math.min(xp, amount(option, "START_XP"));
                    health -= h; draw -= d; xp -= x;
                    String description = bonusDescription(option, h, d, x);
                    if (description.isEmpty()) description = "Received earlier: " + TrialsText.currency(TrialsRules.s(option, "description", ""));
                    else if (h > 0 || d > 0 || x > 0) description += " Lasts for this expedition.";
                    out.add(new Earned(id, TrialsText.currency(TrialsRules.s(option, "title", id)), description, icon(option)));
                }
            }
        }
        if (health > 0) out.add(new Earned("legacy_health", "Golden Heart", permanentDescription(health, 0, 0), "minecraft:golden_apple"));
        if (draw > 0) out.add(new Earned("legacy_draw", "Field Supplies", permanentDescription(0, draw, 0), "minecraft:book"));
        if (xp > 0) out.add(new Earned("legacy_xp", "Expedition Spark", permanentDescription(0, 0, xp), "minecraft:torch"));
        return out;
    }
    private static String bonusDescription(JsonObject option, int health, int draw, int xp) {
        String description = permanentDescription(health, draw, xp);
        if (draw == 0 && amount(option, "OPENING_DRAW") > 0)
            description += " Opening draw was already at its limit; no extra cards gained.";
        if (xp == 0 && amount(option, "START_XP") > 0)
            description += " Starting XP was already at its limit; no extra XP gained.";
        return description.trim();
    }
    private static int amount(JsonObject option, String type) {
        return TrialsRules.list(option, "operations").stream().filter(op -> type.equals(TrialsRules.s(op, "type", "")))
                .mapToInt(op -> TrialsRules.n(op, "amount", 1)).sum();
    }
    static String icon(JsonObject option) {
        String configured = TrialsRules.s(option, "iconItem", "");
        if (!configured.isBlank()) return configured;
        for (var op : TrialsRules.list(option, "operations")) {
            String found = switch (TrialsRules.s(op, "type", "")) {
                case "MAX_HEALTH" -> "minecraft:golden_apple";
                case "OPENING_DRAW" -> "minecraft:book";
                case "START_XP" -> "minecraft:torch";
                case "MARKS" -> "minecraft:emerald";
                case "TRAIN_CARD" -> "minecraft:anvil";
                default -> "";
            };
            if (!found.isEmpty()) return found;
        }
        return "minecraft:amethyst_shard";
    }
    private static String permanentDescription(int health, int draw, int xp) {
        var parts = new ArrayList<String>();
        if (health > 0) parts.add("+" + health + " maximum Health.");
        if (draw > 0) parts.add("Draw " + draw + " extra opening " + (draw == 1 ? "card" : "cards") + " each fight.");
        if (xp > 0) parts.add("Start each fight with +" + xp + " maximum XP.");
        return String.join(" ", parts);
    }
    private TrialBoons() {}
}
