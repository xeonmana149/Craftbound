package craftbound.card;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;

// Breaks card text into clauses, one per keyword, like Hearthstone:
//   "Ranged. Drop: add an Arrow to your hand."  ->  "Ranged."  /  "Drop: add an Arrow to your hand."
// Each clause starts on its own line with its keyword on the left, in the order the keywords are listed.
public class CardText {
    // Only standalone keywords qualify. Short effects such as "Draw 1 card"
    // or a keyword followed by its ability retain the normal prose layout.
    public static boolean centerKeywords(Component text) {
        List<MutableComponent> parts = clauses(text);
        if (parts.isEmpty() || parts.size() > 2) return false;
        for (Component part : parts) {
            String word = part.getString().strip().replaceFirst("[.]$", "");
            boolean matched = false;
            for (Keyword keyword : Keyword.values()) {
                if (keyword != Keyword.BLOCK && keyword != Keyword.SPAWN && keyword != Keyword.DROP
                        && word.equals(keyword.displayName().getString())) {
                    matched = true;
                    break;
                }
            }
            if (!matched) return false;
        }
        return true;
    }

    public static List<MutableComponent> clauses(Component text) {
        List<MutableComponent> clauses = new ArrayList<>();
        MutableComponent[] current = {Component.empty()};
        boolean[] hasContent = {false};
        text.visit((style, piece) -> {
            Keyword keyword = Keyword.fromInsertion(style.getInsertion());
            // These status names are often objects inside a sentence, not new timing clauses.
            boolean startsKeyword = keyword != null && keyword != Keyword.BAD_OMEN && keyword != Keyword.WITHER;
            // A keyword that comes after other text starts a new clause.
            if (startsKeyword && hasContent[0]) {
                clauses.add(current[0]);
                current[0] = Component.empty();
                hasContent[0] = false;
            }
            String trimmed = hasContent[0] ? piece : piece.stripLeading();
            if (!trimmed.isEmpty()) {
                current[0].append(Component.literal(trimmed).withStyle(style));
                hasContent[0] = true;
            }
            return Optional.empty();
        }, Style.EMPTY);
        if (hasContent[0]) {
            clauses.add(current[0]);
        }
        return clauses;
    }
}
