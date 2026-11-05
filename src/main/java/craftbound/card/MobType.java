package craftbound.card;

import java.util.Locale;
import net.minecraft.network.chat.Component;

// The family a mob belongs to. Cards can build around a type, e.g. "your other Undead get +1 attack".
public enum MobType {
    ANIMAL, UNDEAD, ARTHROPOD, VILLAGER, ILLAGER, ENDER, NETHER, GOLEM, SLIME, CROP, AQUATIC, ORE, TAMEABLE;

    public Component displayName() {
        return Component.translatable("mob_type.craftbound." + name().toLowerCase(Locale.ROOT));
    }
}
