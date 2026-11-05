package craftbound.sleeve;

import craftbound.Craftbound;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

// Card back designs. A sleeve goes on a Deck Case and sets how the backs of your cards look in matches.
public class Sleeves {
    // Colours for drawing a card back, as 0xAARRGGBB.
    public record Design(int base, int baseDark, int border, int emblem, int emblemDark) {
    }

    private static final Map<Identifier, Design> SLEEVES = new LinkedHashMap<>();

    public static final Identifier CLASSIC = register("classic", new Design(0xFF3B4048, 0xFF262A30, 0xFFA0692A, 0xFFE0782A, 0xFF7A3A10));
    public static final Identifier EMERALD = register("emerald", new Design(0xFF1E5A34, 0xFF123B22, 0xFFC9B05A, 0xFF17DD62, 0xFF0B6A26));
    public static final Identifier REDSTONE = register("redstone", new Design(0xFF4A1414, 0xFF2E0B0B, 0xFF8A8A92, 0xFFFF2A1A, 0xFF7A0A05));
    public static final Identifier ENDER = register("ender", new Design(0xFF14101E, 0xFF0A0812, 0xFF3B2A5A, 0xFF2FD3B5, 0xFF0E5A4C));

    private static final Map<Design, Identifier> TRIAL_BACKS = new java.util.HashMap<>();
    static {
        for (var cls : craftbound.card.CardClass.values()) {
            if (cls == craftbound.card.CardClass.NEUTRAL) continue;
            Design colors = new Design(cls.frameColor(), cls.darkColor(), 0xFFD7B36B, cls.lightColor(), cls.darkColor());
            Identifier id = register("trial_" + cls.name().toLowerCase(java.util.Locale.ROOT), colors);
            TRIAL_BACKS.put(colors, id);
        }
    }

    public static Identifier trialBack(Design design) { return TRIAL_BACKS.get(design); }
    public static boolean isTrial(Identifier id) { return id != null && id.getPath().startsWith("trial_"); }

    private static Identifier register(String name, Design design) {
        Identifier id = Craftbound.id(name);
        SLEEVES.put(id, design);
        return id;
    }

    public static Design get(Identifier id) {
        return SLEEVES.getOrDefault(id, SLEEVES.get(CLASSIC));
    }

    public static Set<Identifier> ids() {
        return SLEEVES.keySet();
    }

    public static Component name(Identifier id) {
        return Component.translatable("sleeve." + id.getNamespace() + "." + id.getPath());
    }
}
