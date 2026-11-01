package craftbound.card;

import java.util.Locale;
import net.minecraft.network.chat.Component;

public enum CardClass {
    NEUTRAL, FIGHTER, BUILDER, FARMER;

    public Component displayName() {
        return Component.translatable("card_class.craftbound." + name().toLowerCase(Locale.ROOT));
    }

    public Component description() {
        return Component.translatable("card_class.craftbound." + name().toLowerCase(Locale.ROOT) + ".desc");
    }
}
