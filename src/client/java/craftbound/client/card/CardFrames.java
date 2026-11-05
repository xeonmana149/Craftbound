package craftbound.client.card;

import craftbound.Craftbound;
import craftbound.card.CardDefinition;
import craftbound.card.CardRarity;
import craftbound.card.Keyword;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;

/** Optional full-card skins. Resource packs can add a skin to a single kind and rarity. */
public final class CardFrames {
    private enum Kind { MOB, BLOCK, ACTION, TOOL, ENCHANTMENT }

    public record Frame(Identifier texture) {
        void draw(CardCanvas canvas) {
            canvas.frameRegion(texture, 0, 0, 120, 168, 0, 0, 120, 168);
        }
    }

    private static volatile Map<Kind, EnumMap<CardRarity, Frame>> frames = Map.of();

    private static volatile java.util.Set<Identifier> trialArt = java.util.Set.of();
    private static volatile long generation;
    public static long generation() { return generation; }
    public static boolean hasTrialArt(Identifier path) { return trialArt.contains(path); }
    public static void reload(ResourceManager resources) {
        trialArt = java.util.Set.copyOf(resources.listResources("textures/gui/trials", file ->
                file.getNamespace().equals("craftbound") && file.getPath().endsWith(".png")).keySet());
        var loaded = new EnumMap<Kind, EnumMap<CardRarity, Frame>>(Kind.class);
        for (Kind kind : Kind.values()) {
            var rarities = new EnumMap<CardRarity, Frame>(CardRarity.class);
            for (CardRarity rarity : CardRarity.values()) {
                String name = ("base_" + kind.name() + "_" + rarity.name()).toLowerCase(Locale.ROOT);
                Identifier file = Craftbound.id("textures/gui/card/base/" + name + ".png");
                if (resources.getResource(file).isPresent()) rarities.put(rarity, new Frame(file));
            }
            loaded.put(kind, rarities);
        }
        frames = loaded;
        generation++;
    }

    static Frame forCard(CardDefinition card) {
        if (card.token()) return null;
        Kind kind = switch (card.kind()) {
            case MOB -> card.keywords().contains(Keyword.BLOCK) ? Kind.BLOCK : Kind.MOB;
            case ITEM -> Kind.ACTION;
            case TOOL -> Kind.TOOL;
            case ENCHANTMENT -> Kind.ENCHANTMENT;
        };
        var rarities = frames.get(kind);
        return rarities == null ? null : rarities.get(card.rarity());
    }

    private CardFrames() {}
}
