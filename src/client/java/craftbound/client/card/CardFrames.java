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

/**
 * Optional full-card skins. Preferred layout: one base per kind (textures/gui/card/base_mob/base_mob.png ...)
 * with an empty gem socket, plus one gem per rarity (textures/gui/card/gem/gem_common.png ...). A gem is a
 * 120x168 transparent overlay with only the gem pixels painted, so it lands in the same spot on every card.
 * Older per-rarity bases (base_mob/base_mob_rare.png) are still used, without a gem, when no single base exists.
 */
public final class CardFrames {
    private enum Kind { MOB, BLOCK, ACTION, TOOL, ENCHANTMENT }

    /** gem is the rarity overlay, or null when the base already has its gem painted in. */
    public record Frame(Identifier texture, Identifier gem) {
        void draw(CardCanvas canvas) {
            canvas.frameRegion(texture, 0, 0, 120, 168, 0, 0, 120, 168);
            if (gem != null) canvas.frameRegion(gem, 0, 0, 120, 168, 0, 0, 120, 168);
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
                // One folder per kind (base_mob/, base_enchantment/, ...); the old shared base/ folder still works.
                String folder = ("base_" + kind.name()).toLowerCase(Locale.ROOT);
                Identifier single = Craftbound.id("textures/gui/card/" + folder + "/" + folder + ".png");
                Identifier gem = Craftbound.id("textures/gui/card/gem/gem_" + rarity.name().toLowerCase(Locale.ROOT) + ".png");
                if (resources.getResource(single).isPresent()) {
                    rarities.put(rarity, new Frame(single, resources.getResource(gem).isPresent() ? gem : null));
                    continue;
                }
                for (Identifier file : new Identifier[] {
                        Craftbound.id("textures/gui/card/" + folder + "/" + name + ".png"),
                        Craftbound.id("textures/gui/card/base/" + name + ".png") }) {
                    if (resources.getResource(file).isPresent()) { rarities.put(rarity, new Frame(file, null)); break; }
                }
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
