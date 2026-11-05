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
public record DeckContents(CardClass deckClass, BinderContents cards, boolean list, DeckFormat format,
                           java.util.Optional<Identifier> commander) {
    public DeckContents(CardClass deckClass,BinderContents cards,boolean list) {
        this(deckClass,cards,list,DeckFormat.DUEL,java.util.Optional.empty());
    }
    public static final DeckContents EMPTY = new DeckContents(CardClass.NEUTRAL, BinderContents.EMPTY, true);

    private static final Codec<CardClass> CLASS_CODEC = Codec.STRING.xmap(DeckContents::classFromName, CardClass::name);
    private static final StreamCodec<ByteBuf, CardClass> CLASS_STREAM_CODEC =
            ByteBufCodecs.idMapper(i -> CardClass.values()[i], CardClass::ordinal);

    public static final Codec<DeckContents> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            CLASS_CODEC.optionalFieldOf("class", CardClass.NEUTRAL).forGetter(DeckContents::deckClass),
            BinderContents.CODEC.optionalFieldOf("cards", BinderContents.EMPTY).forGetter(DeckContents::cards),
            Codec.BOOL.optionalFieldOf("list", false).forGetter(DeckContents::list),
            DeckFormat.CODEC.optionalFieldOf("format",DeckFormat.DUEL).forGetter(DeckContents::format),
            Identifier.CODEC.optionalFieldOf("commander").forGetter(DeckContents::commander)
    ).apply(instance, DeckContents::new));

    public static final StreamCodec<ByteBuf, DeckContents> STREAM_CODEC = StreamCodec.composite(
            CLASS_STREAM_CODEC, DeckContents::deckClass,
            BinderContents.STREAM_CODEC, DeckContents::cards,
            ByteBufCodecs.BOOL, DeckContents::list,
            ByteBufCodecs.idMapper(i -> DeckFormat.values()[i], DeckFormat::ordinal), DeckContents::format,
            Identifier.STREAM_CODEC.apply(ByteBufCodecs::optional), DeckContents::commander,
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

    public boolean leads(Identifier id) { return format==DeckFormat.COMMANDER && commander.filter(id::equals).isPresent(); }

    public DeckContents clearCards() {
        BinderContents kept = BinderContents.EMPTY;
        if (format==DeckFormat.COMMANDER && commander.isPresent() && copies(commander.get())>0)
            kept=kept.add(commander.get(), cards.count(commander.get(),true)>0,1);
        return new DeckContents(deckClass,kept,true,format,commander);
    }

    public DeckContents withCommander(Identifier id, boolean enchanted) {
        if (format!=DeckFormat.COMMANDER || !craftbound.game.CommanderMatch.eligible(id) || !craftbound.card.DeckRules.allowed(id,deckClass)) return this;
        BinderContents next=cards;
        // Remove the previous leader and any existing copy of the chosen leader before seating it.
        for (Identifier leader : java.util.stream.Stream.concat(commander.stream(),java.util.stream.Stream.of(id)).distinct().toList())
            for (boolean foil : new boolean[]{false,true}) next=next.add(leader,foil,-next.count(leader,foil));
        if (next.total()>=format.size()) return this;
        return new DeckContents(deckClass,next.add(id,enchanted,1),true,format,java.util.Optional.of(id));
    }

    public DeckContents asList() {
        return new DeckContents(deckClass, cards, true,format,commander);
    }

    public DeckContents withClass(CardClass newClass) {
        return new DeckContents(newClass, cards, list,format,commander);
    }

    public DeckContents add(Identifier id, boolean enchanted, int amount) {
        return new DeckContents(deckClass, cards.add(id, enchanted, amount), list,format,commander);
    }
}
