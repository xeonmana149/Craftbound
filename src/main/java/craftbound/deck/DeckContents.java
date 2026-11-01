package craftbound.deck;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import craftbound.binder.BinderContents;
import craftbound.card.CardClass;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;

// What's in a Deck Case: the deck's class and its list of cards (normal and Enchanted counts, like a binder).
// The cards themselves stay in your binder. list is false only for Deck Cases from before decks were lists,
// which still hold real cards until DeckBuilding.migrate hands them back.
public record DeckContents(CardClass deckClass, BinderContents cards, boolean list) {
    public static final DeckContents EMPTY = new DeckContents(CardClass.NEUTRAL, BinderContents.EMPTY, true);

    private static final Codec<CardClass> CLASS_CODEC = Codec.STRING.xmap(DeckContents::classFromName, CardClass::name);
    private static final StreamCodec<ByteBuf, CardClass> CLASS_STREAM_CODEC =
            ByteBufCodecs.idMapper(i -> CardClass.values()[i], CardClass::ordinal);

    public static final Codec<DeckContents> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            CLASS_CODEC.optionalFieldOf("class", CardClass.NEUTRAL).forGetter(DeckContents::deckClass),
            BinderContents.CODEC.optionalFieldOf("cards", BinderContents.EMPTY).forGetter(DeckContents::cards),
            Codec.BOOL.optionalFieldOf("list", false).forGetter(DeckContents::list)
    ).apply(instance, DeckContents::new));

    public static final StreamCodec<ByteBuf, DeckContents> STREAM_CODEC = StreamCodec.composite(
            CLASS_STREAM_CODEC, DeckContents::deckClass,
            BinderContents.STREAM_CODEC, DeckContents::cards,
            ByteBufCodecs.BOOL, DeckContents::list,
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

    public DeckContents asList() {
        return new DeckContents(deckClass, cards, true);
    }

    public DeckContents withClass(CardClass newClass) {
        return new DeckContents(newClass, cards, list);
    }

    public DeckContents add(Identifier id, boolean enchanted, int amount) {
        return new DeckContents(deckClass, cards.add(id, enchanted, amount), list);
    }
}
