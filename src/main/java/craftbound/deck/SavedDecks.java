package craftbound.deck;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import craftbound.component.ModComponents;
import craftbound.item.ModItems;
import craftbound.sleeve.Sleeves;
import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

// Lists live with the physical binder; deleting or copying a list never creates physical cards.
public record SavedDecks(List<Entry> entries) {
    public static final int LIMIT = 32;
    public static final SavedDecks EMPTY = new SavedDecks(List.of());
    public SavedDecks { entries = List.copyOf(entries); }

    public record Entry(String id, String name, DeckContents deck, Identifier sleeve) {
        public static final Codec<Entry> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("id").forGetter(Entry::id),
                Codec.STRING.optionalFieldOf("name", "").forGetter(Entry::name),
                DeckContents.CODEC.fieldOf("deck").forGetter(Entry::deck),
                Identifier.CODEC.optionalFieldOf("sleeve", Sleeves.CLASSIC).forGetter(Entry::sleeve)
        ).apply(i, Entry::new));
        public static final StreamCodec<ByteBuf, Entry> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, Entry::id, ByteBufCodecs.STRING_UTF8, Entry::name,
                DeckContents.STREAM_CODEC, Entry::deck, Identifier.STREAM_CODEC, Entry::sleeve, Entry::new);

        public ItemStack asStack() {
            ItemStack stack = new ItemStack(ModItems.DECK_CASE);
            stack.set(ModComponents.DECK_CONTENTS, deck.asList());
            stack.set(ModComponents.SLEEVE_ID, sleeve);
            DeckBuilding.rename(stack, name);
            return stack;
        }

        public static Entry fromStack(String id, ItemStack stack) {
            return new Entry(id, DeckBuilding.customName(stack),
                    stack.getOrDefault(ModComponents.DECK_CONTENTS, DeckContents.EMPTY).asList(),
                    stack.getOrDefault(ModComponents.SLEEVE_ID, Sleeves.CLASSIC));
        }
    }

    public static final Codec<SavedDecks> CODEC = Entry.CODEC.listOf().xmap(SavedDecks::new, SavedDecks::entries);
    public static final StreamCodec<ByteBuf, SavedDecks> STREAM_CODEC =
            ByteBufCodecs.collection(ArrayList::new, Entry.STREAM_CODEC).map(SavedDecks::new, d -> new ArrayList<>(d.entries));

    public Entry find(String id) {
        for (Entry entry : entries) if (entry.id.equals(id)) return entry;
        return null;
    }

    public SavedDecks put(Entry entry) {
        List<Entry> next = new ArrayList<>(entries);
        for (int i = 0; i < next.size(); i++) if (next.get(i).id.equals(entry.id)) {
            next.set(i, entry);
            return new SavedDecks(next);
        }
        if (next.size() < LIMIT) next.add(entry);
        return new SavedDecks(next);
    }

    public SavedDecks remove(String id) {
        return new SavedDecks(entries.stream().filter(e -> !e.id.equals(id)).toList());
    }
}
