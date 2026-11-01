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
    // LinkedHashMap keeps cards in the order they're registered, so the creative tab lists them in this order.
    private static final Map<Identifier, CardDefinition> CARDS = new LinkedHashMap<>();

    // Tokens come first, because the cards below point to them.
    public static final Identifier ROTTEN_FLESH = register("rotten_flesh", CardDefinition.item(CardRarity.COMMON, 0).token());
    public static final Identifier ARROW = register("arrow", CardDefinition.item(CardRarity.COMMON, 1).token());

    public static final Identifier ZOMBIE = register("zombie", CardDefinition.mob(CardRarity.COMMON, 2, 2, 3)
            .type(MobType.UNDEAD).keywords(Keyword.DROP).creates(Keyword.DROP, ROTTEN_FLESH));
    public static final Identifier SKELETON = register("skeleton", CardDefinition.mob(CardRarity.COMMON, 3, 2, 2)
            .type(MobType.UNDEAD).keywords(Keyword.RANGED, Keyword.DROP).creates(Keyword.DROP, ARROW));
    public static final Identifier CREEPER = register("creeper", CardDefinition.mob(CardRarity.RARE, 3, 2, 2)
            .keywords(Keyword.DROP));
    public static final Identifier ENDERMAN = register("enderman", CardDefinition.mob(CardRarity.EPIC, 5, 4, 4)
            .type(MobType.ENDER).keywords(Keyword.SPAWN));

    private static Identifier register(String name, CardDefinition.Builder card) {
        Identifier id = Craftbound.id(name);
        CARDS.put(id, card.build());
        return id;
    }

    public static CardDefinition get(Identifier id) {
        return CARDS.get(id);
    }

    public static Set<Identifier> ids() {
        return CARDS.keySet();
    }

    // Every card you can collect, in order. These are the binder's numbered pockets.
    public static List<Identifier> collectible() {
        List<Identifier> result = new ArrayList<>();
        for (Map.Entry<Identifier, CardDefinition> entry : CARDS.entrySet()) {
            if (!entry.getValue().token()) {
                result.add(entry.getKey());
            }
        }
        return result;
    }

    // Collectible cards of one rarity. Tokens are left out, so packs never give them.
    public static List<Identifier> ofRarity(CardRarity rarity) {
        List<Identifier> result = new ArrayList<>();
        for (Map.Entry<Identifier, CardDefinition> entry : CARDS.entrySet()) {
            if (entry.getValue().rarity() == rarity && !entry.getValue().token()) {
                result.add(entry.getKey());
            }
        }
        return result;
    }

    public static Component name(Identifier id) {
        return Component.translatable("card." + id.getNamespace() + "." + id.getPath());
    }

    // The ability text, with the card's keywords dropped into its %s slots in bold.
    public static Component text(Identifier id) {
        CardDefinition card = get(id);
        Object[] keywords = card == null ? new Object[0] : card.keywords().stream().map(Keyword::inline).toArray();
        return Component.translatable("card." + id.getNamespace() + "." + id.getPath() + ".text", keywords);
    }
}
