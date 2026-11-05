package craftbound.card;

import craftbound.Craftbound;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

public class Cards {
    private static final java.util.regex.Pattern RULE_WORDS = java.util.regex.Pattern.compile("\\b((?:After|When|At the end of|At the start of)[^.:\\n]+(?=:)|At the end of your turn|After your hero attacks|After your hero deals attack damage|After your tool kills a mob or block|After this destroys a mob or block|After this kills a mob or block|After this deals damage|After attacking|When attacked|Your tool gains|After attack|End turn|Death|Activate|Play|Ignore Shield|Shield|Sprint|Ranged|Spawn|Drop|Cleanse|(?i:Bad Omen|Wither(?:ed)?)|Harvest|On kill|Card added|Action played|Mined|Healed|Well Fed)\\b");
    public static final Identifier EXCAVATED_PICKAXE = Identifier.parse("craftbound:excavated_pickaxe");
    public static final Identifier WOLF_PUP = Identifier.parse("craftbound:wolf_pup");
    public static final Identifier COAL_SEAM = Identifier.parse("craftbound:coal_seam");
    public static final Identifier MINOR_HARMING_POTION = Identifier.parse("craftbound:minor_harming_potion");
    public static final Identifier CAUGHT_COD = Identifier.parse("craftbound:caught_cod");
    public static final Identifier ROTTEN_FLESH = Identifier.parse("craftbound:rotten_flesh");
    public static final Identifier ARROW = Identifier.parse("craftbound:arrow");
    public static final Identifier NETHER_STAR = Identifier.parse("craftbound:nether_star");
    public static final Identifier DRAGON_EGG = Identifier.parse("craftbound:dragon_egg");
    public static final Identifier VEX = Identifier.parse("craftbound:vex");
    public static final Identifier EXPERIENCE_BOTTLE = Identifier.parse("craftbound:experience_bottle");
    public static final Identifier ZOMBIE = Identifier.parse("craftbound:zombie");
    public static final Identifier SKELETON = Identifier.parse("craftbound:skeleton");
    public static final Identifier CREEPER = Identifier.parse("craftbound:creeper");
    public static final Identifier ENDERMAN = Identifier.parse("craftbound:enderman");
    public static final Identifier CHARGED_CREEPER = Identifier.parse("craftbound:charged_creeper");
    public static final Identifier TOTEM_OF_UNDYING = Identifier.parse("craftbound:totem_of_undying");
    public static final Identifier ENCHANTED_GOLDEN_APPLE = Identifier.parse("craftbound:enchanted_golden_apple");
    public static final Identifier EVOKER = Identifier.parse("craftbound:evoker");
    public static final Identifier RAVAGER = Identifier.parse("craftbound:ravager");
    public static final Identifier PIGLIN_BRUTE = Identifier.parse("craftbound:piglin_brute");
    public static final Identifier SHULKER = Identifier.parse("craftbound:shulker");
    public static final Identifier WITHER_SKELETON = Identifier.parse("craftbound:wither_skeleton");
    public static final Identifier WARDEN = Identifier.parse("craftbound:warden");
    public static final Identifier WITHER = Identifier.parse("craftbound:wither");
    public static final Identifier ENDER_DRAGON = Identifier.parse("craftbound:ender_dragon");
    public static final Identifier CRAFTED_SWORD = Identifier.parse("craftbound:crafted_sword");
    public static final Identifier COBBLESTONE_WALL = Identifier.parse("craftbound:cobblestone_wall");
    public static final Identifier WHEAT = Identifier.parse("craftbound:wheat");
    public static final Identifier IRON_SWORD = Identifier.parse("craftbound:iron_sword");
    public static final Identifier DIAMOND_SWORD = Identifier.parse("craftbound:diamond_sword");
    public static final Identifier POTION_OF_STRENGTH = Identifier.parse("craftbound:potion_of_strength");
    public static final Identifier VINDICATOR = Identifier.parse("craftbound:vindicator");
    public static final Identifier SWEEPING_EDGE = Identifier.parse("craftbound:sweeping_edge");
    public static final Identifier FURNACE = Identifier.parse("craftbound:furnace");
    public static final Identifier DISPENSER = Identifier.parse("craftbound:dispenser");
    public static final Identifier BEACON = Identifier.parse("craftbound:beacon");
    public static final Identifier PISTON = Identifier.parse("craftbound:piston");
    public static final Identifier BOW = Identifier.parse("craftbound:bow");
    public static final Identifier BEE = Identifier.parse("craftbound:bee");
    public static final Identifier BONE_MEAL = Identifier.parse("craftbound:bone_meal");
    public static final Identifier SWEET_BERRY_BUSH = Identifier.parse("craftbound:sweet_berry_bush");
    public static final Identifier BOUNTIFUL_HARVEST = Identifier.parse("craftbound:bountiful_harvest");
    public static final Identifier COMPOSTER = Identifier.parse("craftbound:composter");
    public static final Identifier SNIFFER = Identifier.parse("craftbound:sniffer");
    public static final Identifier LAVA_BUCKET = Identifier.parse("craftbound:lava_bucket");

    public static CardDefinition get(Identifier id) {
        var entry = CardCatalog.entry(id);
        return entry == null ? null : entry.definition();
    }

    public static Set<Identifier> ids() {
        return CardCatalog.current().entries().keySet();
    }

    // Every card you can collect, in order. These are the binder's numbered pockets.
    public static List<Identifier> collectible() {
        List<Identifier> result = new ArrayList<>();
        for (var entry : CardCatalog.current().entries().entrySet()) {
            if (!entry.getValue().deleted() && !entry.getValue().definition().token()) {
                result.add(entry.getKey());
            }
        }
        return result;
    }

    // A card's collector number: its pocket number in the binder (1, 2, 3...). Tokens have none (0).
    // New cards should be added at the end of the list above, so existing numbers never change.
    public static int collectorNumber(Identifier id) {
        int number = 0;
        for (var e : CardCatalog.current().entries().entrySet()) {
            if (!e.getValue().definition().token()) { number++; if (e.getKey().equals(id)) return number; }
        }
        return 0;
    }

    public static boolean deleted(Identifier id) {
        var e = CardCatalog.entry(id); return e != null && e.deleted();
    }
    public static int collectorTotal() {
        return (int) CardCatalog.current().entries().values().stream().filter(e -> !e.definition().token()).count();
    }

    // Collectible cards of one rarity. Tokens are left out, so packs never give them.
    public static List<Identifier> ofRarity(CardRarity rarity) {
        List<Identifier> result = new ArrayList<>();
        for (var entry : CardCatalog.current().entries().entrySet()) {
            if (!entry.getValue().deleted() && entry.getValue().definition().rarity() == rarity && !entry.getValue().definition().token()) {
                result.add(entry.getKey());
            }
        }
        return result;
    }

    public static Component name(Identifier id) {
        var e = CardCatalog.entry(id);
        return e == null ? Component.literal(id.toString()) : Component.literal(e.name());
    }

    // The ability text, with the card's keywords dropped into its %s slots in bold.
    public static Component text(Identifier id) {
        var e = CardCatalog.entry(id);
        if (e == null) return Component.empty();
        return styledText(CardCatalog.description(e));
    }

    public static Component styledText(String text) {
        var result = Component.empty();
        var matcher = RULE_WORDS.matcher(text);
        int cursor = 0;
        while (matcher.find()) {
            result.append(Component.literal(text.substring(cursor, matcher.start())));
            try {
                Keyword keyword = matcher.group().equalsIgnoreCase("Withered") ? Keyword.WITHER
                        : Keyword.valueOf(matcher.group().toUpperCase(java.util.Locale.ROOT).replace(' ', '_'));
                result.append(Component.literal(matcher.group()).setStyle(keyword.inline().getStyle()));
            }
            catch (IllegalArgumentException ignored) { result.append(Component.literal(matcher.group()).withStyle(net.minecraft.ChatFormatting.BOLD)); }
            cursor = matcher.end();
        }
        return result.append(Component.literal(text.substring(cursor)));
    }
}
