package craftbound.game;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import craftbound.card.CardDefinition;
import craftbound.card.CardKind;
import craftbound.card.Keyword;
import java.util.*;
import net.minecraft.resources.Identifier;

public record TrialCharmSpec(String id, String title, String iconItem, Trigger trigger, Action action, int amount, int cost) {
    public enum Trigger { MOB, BLOCK, ACTION, TOOL, ENCHANTMENT, COST_AT_LEAST, COST_AT_MOST, ANY_CARD }
    public enum Action { BUFF_ATTACK, BUFF_HEALTH, DAMAGE_ENEMY, TOOL_ATTACK, DRAW, HEAL_SELF }

    public static List<TrialCharmSpec> parse(JsonArray rows) {
        if (rows.size() > 100) throw new IllegalArgumentException("Use at most 100 charms");
        Set<String> ids = new HashSet<>();
        List<TrialCharmSpec> result = new ArrayList<>();
        for (var element : rows) {
            var spec = TrialsContent.GSON.fromJson(element, TrialCharmSpec.class);
            if (spec == null || spec.id == null || !spec.id.matches("[A-Za-z0-9_]{1,48}") || !ids.add(spec.id)
                    || spec.title == null || spec.title.isBlank() || spec.title.length() > 80
                    || spec.iconItem == null || Identifier.tryParse(spec.iconItem) == null
                    || spec.trigger == null || spec.action == null || spec.cost < 0 || spec.cost > 20 || spec.amount < 1 || spec.amount > 10)
                throw new IllegalArgumentException("Invalid or duplicate charm at row " + (result.size()+1));
            boolean board = spec.trigger == Trigger.MOB || spec.trigger == Trigger.BLOCK;
            if (spec.action == Action.BUFF_HEALTH && !board || spec.action == Action.BUFF_ATTACK && spec.trigger != Trigger.MOB
                    || spec.action == Action.TOOL_ATTACK && spec.trigger != Trigger.TOOL)
                throw new IllegalArgumentException(spec.id + ": choose a matching mob, block or tool trigger for this buff");
            result.add(spec);
        }
        return List.copyOf(result);
    }

    public static TrialCharmSpec find(String id) {
        return TrialsContent.charms().stream().filter(charm -> charm.id.equals(id)).findFirst().orElse(null);
    }
    boolean matches(CardDefinition card) {
        return switch (trigger) {
            case MOB -> card.isMob() && !card.keywords().contains(Keyword.BLOCK);
            case BLOCK -> card.keywords().contains(Keyword.BLOCK);
            case TOOL -> card.isTool();
            case ACTION -> card.kind() == CardKind.ITEM;
            case ENCHANTMENT -> card.kind() == CardKind.ENCHANTMENT;
            case COST_AT_LEAST -> card.cost() >= cost;
            case COST_AT_MOST -> card.cost() <= cost;
            case ANY_CARD -> true;
        };
    }
    public String description() {
        String when = switch (trigger) {
            case COST_AT_LEAST -> "card costing " + cost + " XP or more";
            case COST_AT_MOST -> "card costing " + cost + " XP or less";
            case ANY_CARD -> "card";
            default -> trigger.name().toLowerCase(Locale.ROOT);
        };
        String then = switch (action) {
            case BUFF_ATTACK -> "give it +" + amount + " Attack";
            case BUFF_HEALTH -> "give it +" + amount + " Health";
            case DAMAGE_ENEMY -> "deal " + amount + " damage to the enemy hero";
            case TOOL_ATTACK -> "give the equipped tool +" + amount + " Attack";
            case DRAW -> "draw " + amount + (amount == 1 ? " card" : " cards");
            case HEAL_SELF -> "restore " + amount + " Health to your hero";
        };
        return "After your first " + when + " each turn, " + then + ".";
    }
    public JsonObject view() {
        var view = new JsonObject();
        view.addProperty("id",id); view.addProperty("title",title);
        view.addProperty("description",description()); view.addProperty("iconItem",iconItem);
        return view;
    }
}
