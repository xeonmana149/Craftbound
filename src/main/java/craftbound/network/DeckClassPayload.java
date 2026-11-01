package craftbound.network;

import craftbound.Craftbound;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

// Sent by the Deck Case screen to change the deck's class. The class is sent as its position in CardClass.
public record DeckClassPayload(boolean mainHand, int deckClass) implements CustomPacketPayload {
    public static final Type<DeckClassPayload> TYPE = new Type<>(Craftbound.id("deck_class"));
    public static final StreamCodec<RegistryFriendlyByteBuf, DeckClassPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, DeckClassPayload::mainHand,
            ByteBufCodecs.VAR_INT, DeckClassPayload::deckClass,
            DeckClassPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
