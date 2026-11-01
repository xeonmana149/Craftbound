package craftbound.network;

import craftbound.Craftbound;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

// Sent by the Deck Case screen's Card Backs tab to put a different sleeve on the case.
public record DeckSleevePayload(boolean mainHand, Identifier sleeve) implements CustomPacketPayload {
    public static final Type<DeckSleevePayload> TYPE = new Type<>(Craftbound.id("deck_sleeve"));
    public static final StreamCodec<RegistryFriendlyByteBuf, DeckSleevePayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, DeckSleevePayload::mainHand,
            Identifier.STREAM_CODEC, DeckSleevePayload::sleeve,
            DeckSleevePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
