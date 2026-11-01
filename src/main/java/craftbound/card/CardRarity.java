package craftbound.card;

import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

// Lowest to highest. Packs rely on this order when they step down a rarity.
public enum CardRarity {
    COMMON(ChatFormatting.GRAY, 0xAAAAAA),
    RARE(ChatFormatting.BLUE, 0x5555FF),
    EPIC(ChatFormatting.DARK_PURPLE, 0xAA00AA),
    LEGENDARY(ChatFormatting.GOLD, 0xFFAA00);

    private final ChatFormatting color;
    private final int rgb;

    CardRarity(ChatFormatting color, int rgb) {
        this.color = color;
        this.rgb = rgb;
    }

    // For text, e.g. card names.
    public ChatFormatting color() {
        return color;
    }

    // The same colour as a plain number, for drawing shapes like the rarity gem.
    public int rgb() {
        return rgb;
    }

    public Component displayName() {
        return Component.translatable("rarity.craftbound." + name().toLowerCase(Locale.ROOT));
    }
}
