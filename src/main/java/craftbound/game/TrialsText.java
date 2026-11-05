package craftbound.game;

import com.google.gson.*;
import java.util.Map;

/** Presentation-only vocabulary migration; persisted operation and currency keys stay compatible. */
final class TrialsText {
    static String currency(String text) {
        return text == null ? "" : text.replaceAll("\\bMarks\\b", "Emeralds").replaceAll("\\bMark\\b", "Emerald");
    }
    static boolean migrateCurrency(JsonObject root) { return migrate(root, ""); }
    private static boolean migrate(JsonElement value, String field) {
        boolean changed = false;
        if (value.isJsonArray()) {
            var array = value.getAsJsonArray();
            for (int i = 0; i < array.size(); i++) {
                var child = array.get(i);
                if ("descriptions".equals(field) && child.isJsonPrimitive() && child.getAsJsonPrimitive().isString()) {
                    String before = child.getAsString(), after = currency(before);
                    if (!before.equals(after)) { array.set(i, new JsonPrimitive(after)); changed = true; }
                } else changed |= migrate(child, field);
            }
        } else if (value.isJsonObject()) {
            for (Map.Entry<String, JsonElement> entry : value.getAsJsonObject().entrySet()) {
                var child = entry.getValue();
                if (("title".equals(entry.getKey()) || "description".equals(entry.getKey()))
                        && child.isJsonPrimitive() && child.getAsJsonPrimitive().isString()) {
                    String before = child.getAsString(), after = currency(before);
                    if (!before.equals(after)) { entry.setValue(new JsonPrimitive(after)); changed = true; }
                } else changed |= migrate(child, entry.getKey());
            }
        }
        return changed;
    }
    private TrialsText() {}
}
