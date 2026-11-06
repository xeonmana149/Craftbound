package craftbound.card;

import java.util.Locale;
import net.minecraft.network.chat.Component;

// Rules words that appear in card text. Hovering one on a card explains it.
public enum Keyword {
    SHIELD, SPRINT, RANGED, SPAWN, DROP, BLOCK, CLEANSE, IGNORE_SHIELD, BAD_OMEN, WITHER, REPAIR,
    // Trident: Throw on the tool, Loyalty and Channeling on enchantments.
    THROW, LOYALTY, CHANNELING;

    // Tags the keyword's letters in card text, so the inspect screen can find where the word was drawn.
    private static final String INSERTION_PREFIX = "craftbound:keyword/";

    public Component displayName() {
        return Component.translatable("keyword.craftbound." + name().toLowerCase(Locale.ROOT))
                .withStyle(net.minecraft.ChatFormatting.BOLD);
    }

    public Component description() {
        return Component.translatable("keyword.craftbound." + name().toLowerCase(Locale.ROOT) + ".desc");
    }

    /** A one-line version for hover notes; the full rule shows when a pinned note is hovered. */
    public Component shortDescription() {
        String key = "keyword.craftbound." + name().toLowerCase(Locale.ROOT) + ".short";
        return net.minecraft.locale.Language.getInstance().has(key) ? Component.translatable(key) : description();
    }

    /** Whether the full rule says more than the short line. */
    public boolean hasMore() {
        return net.minecraft.locale.Language.getInstance().has("keyword.craftbound." + name().toLowerCase(Locale.ROOT) + ".short");
    }

    /** Add the concrete outcome when a status refers to another card. */
    public Component description(CardDefinition card) {
        var result = description().copy();
        if (this == BAD_OMEN && card != null) {
            for (var id : card.createdBy(this).stream().distinct().toList()) {
                result.append("\n").append(Component.translatable("keyword.craftbound.bad_omen.summons", Cards.name(id)));
            }
        }
        return result;
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
