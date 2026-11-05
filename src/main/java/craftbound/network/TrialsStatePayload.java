package craftbound.network;

import craftbound.Craftbound;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record TrialsStatePayload(BlockPos table, String json) implements CustomPacketPayload {
    public static final Type<TrialsStatePayload> TYPE = new Type<>(Craftbound.id("trials_state"));
    public static final StreamCodec<RegistryFriendlyByteBuf, TrialsStatePayload> CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, TrialsStatePayload::table,
            ByteBufCodecs.stringUtf8(262144), TrialsStatePayload::json, TrialsStatePayload::new);
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
