package craftbound.binder;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;

// What's filed in a binder: how many normal and Enchanted copies of each card it holds, and which ones are new.
// A card is new when its first copy is filed, and stays new (glowing blue in the deck builder and binder) until
// you hover or click it, like Hearthstone's new cards.
public record BinderContents(Map<Identifier, Integer> cards, Map<Identifier, Integer> enchanted,
                             Set<Identifier> newCards, Set<Identifier> newEnchanted) {
    public static final BinderContents EMPTY = new BinderContents(Map.of(), Map.of(), Set.of(), Set.of());

    private static final Codec<Map<Identifier, Integer>> COUNTS_CODEC = Codec.unboundedMap(Identifier.CODEC, Codec.INT);
    private static final StreamCodec<ByteBuf, Map<Identifier, Integer>> COUNTS_STREAM_CODEC =
            ByteBufCodecs.map(HashMap::new, Identifier.STREAM_CODEC, ByteBufCodecs.VAR_INT);
    private static final Codec<Set<Identifier>> SET_CODEC = Identifier.CODEC.listOf().xmap(Set::copyOf, List::copyOf);
    private static final StreamCodec<ByteBuf, Set<Identifier>> SET_STREAM_CODEC =
            ByteBufCodecs.collection(HashSet::new, Identifier.STREAM_CODEC);

    public static final Codec<BinderContents> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            COUNTS_CODEC.optionalFieldOf("cards", Map.of()).forGetter(BinderContents::cards),
            COUNTS_CODEC.optionalFieldOf("enchanted", Map.of()).forGetter(BinderContents::enchanted),
            SET_CODEC.optionalFieldOf("new", Set.of()).forGetter(BinderContents::newCards),
            SET_CODEC.optionalFieldOf("new_enchanted", Set.of()).forGetter(BinderContents::newEnchanted)
    ).apply(instance, BinderContents::new));

    public static final StreamCodec<ByteBuf, BinderContents> STREAM_CODEC = StreamCodec.composite(
            COUNTS_STREAM_CODEC, BinderContents::cards,
            COUNTS_STREAM_CODEC, BinderContents::enchanted,
            SET_STREAM_CODEC, BinderContents::newCards,
            SET_STREAM_CODEC, BinderContents::newEnchanted,
            BinderContents::new);

    // Components must never change after they're made, so keep read-only copies.
    public BinderContents {
        cards = Map.copyOf(cards);
        enchanted = Map.copyOf(enchanted);
        newCards = Set.copyOf(newCards);
        newEnchanted = Set.copyOf(newEnchanted);
    }

    public int count(Identifier id, boolean isEnchanted) {
        return (isEnchanted ? enchanted : cards).getOrDefault(id, 0);
    }

    public boolean owns(Identifier id) {
        return count(id, false) + count(id, true) > 0;
    }

    public int total() {
        int total = 0;
        for (int count : cards.values()) total += count;
        for (int count : enchanted.values()) total += count;
        return total;
    }

    public static BinderContents fullCollection() {
        Map<Identifier, Integer> copies = new HashMap<>();
        for (Identifier id : craftbound.card.Cards.collectible()) {
            copies.put(id, craftbound.card.DeckRules.maxCopies(craftbound.card.Cards.get(id).rarity()));
        }
        return new BinderContents(copies, copies, Set.of(), Set.of());
    }

    public int collected(List<Identifier> collection) {
        int collected = 0;
        for (Identifier id : collection) {
            if (owns(id)) collected++;
        }
        return collected;
    }

    // How many cards of the collection you own an Enchanted copy of.
    public int collectedEnchanted(List<Identifier> collection) {
        int collected = 0;
        for (Identifier id : collection) {
            if (count(id, true) > 0) collected++;
        }
        return collected;
    }

    // Returns new contents with `amount` copies added (or removed, if negative). A card with no copies left
    // stops being new.
    public BinderContents add(Identifier id, boolean isEnchanted, int amount) {
        Map<Identifier, Integer> changed = new HashMap<>(isEnchanted ? enchanted : cards);
        Set<Identifier> fresh = new HashSet<>(isEnchanted ? newEnchanted : newCards);
        int newCount = changed.getOrDefault(id, 0) + amount;
        if (newCount > 0) {
            changed.put(id, newCount);
        } else {
            changed.remove(id);
            fresh.remove(id);
        }
        return isEnchanted ? new BinderContents(cards, changed, newCards, fresh) : new BinderContents(changed, enchanted, fresh, newEnchanted);
    }

    // New: either kind of copy (normal or Enchanted) hasn't been looked at yet.
    public boolean isNew(Identifier id) {
        return newCards.contains(id) || newEnchanted.contains(id);
    }

    public BinderContents markNew(Identifier id, boolean isEnchanted) {
        Set<Identifier> fresh = new HashSet<>(isEnchanted ? newEnchanted : newCards);
        fresh.add(id);
        return isEnchanted ? new BinderContents(cards, enchanted, newCards, fresh) : new BinderContents(cards, enchanted, fresh, newEnchanted);
    }

    // You've looked at the card: neither kind of copy is new any more.
    public BinderContents markSeen(Identifier id) {
        Set<Identifier> normal = new HashSet<>(newCards);
        Set<Identifier> shiny = new HashSet<>(newEnchanted);
        normal.remove(id);
        shiny.remove(id);
        return new BinderContents(cards, enchanted, normal, shiny);
    }
}
