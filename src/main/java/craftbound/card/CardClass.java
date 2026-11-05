package craftbound.card;

import java.util.Locale;
import net.minecraft.network.chat.Component;

public enum CardClass {
    NEUTRAL(0xFF756B62, 0xFFA99B8A, 0xFF3C3530),
    FIGHTER(0xFFA83749, 0xFFE56C78, 0xFF4C1828),
    BUILDER(0xFF3C63B5, 0xFF7B9FE7, 0xFF1B2D60),
    FARMER(0xFF528D32, 0xFF96C75C, 0xFF274B1A),
    FISHERMAN(0xFF208E9E, 0xFF63D2D8, 0xFF104653),
    ALCHEMIST(0xFF8051B5, 0xFFBE8FE9, 0xFF402462),
    MINER(0xFFB18B22, 0xFFEAD16B, 0xFF57420E),
    CHEF(0xFFCF713A, 0xFFFFB578, 0xFF703719),
    TAMER(0xFFAD558A, 0xFFE89CC3, 0xFF592A49),
    REDSTONER(0xFFB14428, 0xFFFF9665, 0xFF571F17);

    private final int frameColor, lightColor, darkColor;
    CardClass(int frameColor, int lightColor, int darkColor) {
        this.frameColor = frameColor; this.lightColor = lightColor; this.darkColor = darkColor;
    }
    public int frameColor() { return frameColor; }
    public int lightColor() { return lightColor; }
    public int darkColor() { return darkColor; }

    public Component displayName() {
        return Component.translatable("card_class.craftbound." + name().toLowerCase(Locale.ROOT));
    }

    public Component description() {
        return Component.translatable("card_class.craftbound." + name().toLowerCase(Locale.ROOT) + ".desc");
    }
}
