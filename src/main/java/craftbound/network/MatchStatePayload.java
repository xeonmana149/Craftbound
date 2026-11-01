package craftbound.network;

import craftbound.Craftbound;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

// The server sends this after anything changes in your match: everything you can see, as JSON.
public record MatchStatePayload(String json) implements CustomPacketPayload {
    public static final Type<MatchStatePayload> TYPE = new Type<>(Craftbound.id("match_state"));
    public static final StreamCodec<RegistryFriendlyByteBuf, MatchStatePayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.stringUtf8(262144), MatchStatePayload::json,
            MatchStatePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
