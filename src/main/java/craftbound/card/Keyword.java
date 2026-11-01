package craftbound.card;

import java.util.Locale;
import net.minecraft.network.chat.Component;

// Rules words that appear in card text. Hovering one on a card explains it.
public enum Keyword {
    SHIELD, SPRINT, RANGED, SPAWN, DROP, BLOCK;

    // Tags the keyword's letters in card text, so the inspect screen can find where the word was drawn.
    private static final String INSERTION_PREFIX = "craftbound:keyword/";

    public Component displayName() {
        return Component.translatable("keyword.craftbound." + name().toLowerCase(Locale.ROOT));
    }

    public Component description() {
        return Component.translatable("keyword.craftbound." + name().toLowerCase(Locale.ROOT) + ".desc");
    }

    // The keyword as it appears inside card text: bold and tagged.
    public Component inline() {
        return displayName().copy().withStyle(style -> style.withBold(true).withInsertion(INSERTION_PREFIX + name()));
    }

    // Which keyword a piece of text belongs to, or null if it isn't one.
    public static Keyword fromInsertion(String insertion) {
        if (insertion == null || !insertion.startsWith(INSERTION_PREFIX)) {
            return null;
        }
        return valueOf(insertion.substring(INSERTION_PREFIX.length()));
    }
}
