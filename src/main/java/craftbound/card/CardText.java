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
    public static List<MutableComponent> clauses(Component text) {
        List<MutableComponent> clauses = new ArrayList<>();
        MutableComponent[] current = {Component.empty()};
        boolean[] hasContent = {false};
        text.visit((style, piece) -> {
            boolean startsKeyword = Keyword.fromInsertion(style.getInsertion()) != null;
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
