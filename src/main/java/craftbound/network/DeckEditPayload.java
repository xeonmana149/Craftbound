package craftbound.network;

import craftbound.Craftbound;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

// Sent by the Deck Case screen to add a card to the deck (from a binder) or remove one (back to a binder).
public record DeckEditPayload(boolean mainHand, Identifier card, boolean enchanted, boolean add) implements CustomPacketPayload {
    public static final Type<DeckEditPayload> TYPE = new Type<>(Craftbound.id("deck_edit"));
    public static final StreamCodec<RegistryFriendlyByteBuf, DeckEditPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, DeckEditPayload::mainHand,
            Identifier.STREAM_CODEC, DeckEditPayload::card,
            ByteBufCodecs.BOOL, DeckEditPayload::enchanted,
            ByteBufCodecs.BOOL, DeckEditPayload::add,
            DeckEditPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
