package craftbound.binder;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;

// What's filed in a binder: how many normal and Enchanted copies of each card it holds.
public record BinderContents(Map<Identifier, Integer> cards, Map<Identifier, Integer> enchanted) {
    public static final BinderContents EMPTY = new BinderContents(Map.of(), Map.of());

    private static final Codec<Map<Identifier, Integer>> COUNTS_CODEC = Codec.unboundedMap(Identifier.CODEC, Codec.INT);
    private static final StreamCodec<ByteBuf, Map<Identifier, Integer>> COUNTS_STREAM_CODEC =
            ByteBufCodecs.map(HashMap::new, Identifier.STREAM_CODEC, ByteBufCodecs.VAR_INT);

    public static final Codec<BinderContents> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            COUNTS_CODEC.optionalFieldOf("cards", Map.of()).forGetter(BinderContents::cards),
            COUNTS_CODEC.optionalFieldOf("enchanted", Map.of()).forGetter(BinderContents::enchanted)
    ).apply(instance, BinderContents::new));

    public static final StreamCodec<ByteBuf, BinderContents> STREAM_CODEC = StreamCodec.composite(
            COUNTS_STREAM_CODEC, BinderContents::cards,
            COUNTS_STREAM_CODEC, BinderContents::enchanted,
            BinderContents::new);

    // Components must never change after they're made, so keep read-only copies.
    public BinderContents {
        cards = Map.copyOf(cards);
        enchanted = Map.copyOf(enchanted);
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

    // Returns new contents with `amount` copies added (or removed, if negative).
    public BinderContents add(Identifier id, boolean isEnchanted, int amount) {
        Map<Identifier, Integer> changed = new HashMap<>(isEnchanted ? enchanted : cards);
        int newCount = changed.getOrDefault(id, 0) + amount;
        if (newCount > 0) {
            changed.put(id, newCount);
        } else {
            changed.remove(id);
        }
        return isEnchanted ? new BinderContents(cards, changed) : new BinderContents(changed, enchanted);
    }
}
