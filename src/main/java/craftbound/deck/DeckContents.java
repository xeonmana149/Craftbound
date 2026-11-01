package craftbound.deck;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import craftbound.binder.BinderContents;
import craftbound.card.CardClass;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;

// What's in a Deck Case: the deck's class and the real cards in it (normal and Enchanted counts, like a binder).
public record DeckContents(CardClass deckClass, BinderContents cards) {
    public static final DeckContents EMPTY = new DeckContents(CardClass.NEUTRAL, BinderContents.EMPTY);

    private static final Codec<CardClass> CLASS_CODEC = Codec.STRING.xmap(DeckContents::classFromName, CardClass::name);
    private static final StreamCodec<ByteBuf, CardClass> CLASS_STREAM_CODEC =
            ByteBufCodecs.idMapper(i -> CardClass.values()[i], CardClass::ordinal);

    public static final Codec<DeckContents> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            CLASS_CODEC.optionalFieldOf("class", CardClass.NEUTRAL).forGetter(DeckContents::deckClass),
            BinderContents.CODEC.optionalFieldOf("cards", BinderContents.EMPTY).forGetter(DeckContents::cards)
    ).apply(instance, DeckContents::new));

    public static final StreamCodec<ByteBuf, DeckContents> STREAM_CODEC = StreamCodec.composite(
            CLASS_STREAM_CODEC, DeckContents::deckClass,
            BinderContents.STREAM_CODEC, DeckContents::cards,
            DeckContents::new);

    // Unknown class names in old saves fall back to Neutral instead of breaking the item.
    private static CardClass classFromName(String name) {
        try {
            return CardClass.valueOf(name);
        } catch (IllegalArgumentException e) {
            return CardClass.NEUTRAL;
        }
    }

    public int size() {
        return cards.total();
    }

    // Normal and Enchanted copies together, which is what the deck rules count.
    public int copies(Identifier id) {
        return cards.count(id, false) + cards.count(id, true);
    }

    public DeckContents withClass(CardClass newClass) {
        return new DeckContents(newClass, cards);
    }

    public DeckContents add(Identifier id, boolean enchanted, int amount) {
        return new DeckContents(deckClass, cards.add(id, enchanted, amount));
    }
}
