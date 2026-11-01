package craftbound.network;

import craftbound.Craftbound;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

// Client -> server: you've hovered or clicked a new card, so it stops being new in the binders you're carrying.
public record CardSeenPayload(Identifier card) implements CustomPacketPayload {
    public static final Type<CardSeenPayload> TYPE = new Type<>(Craftbound.id("card_seen"));
    public static final StreamCodec<RegistryFriendlyByteBuf, CardSeenPayload> CODEC = StreamCodec.composite(
            Identifier.STREAM_CODEC, CardSeenPayload::card,
            CardSeenPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
