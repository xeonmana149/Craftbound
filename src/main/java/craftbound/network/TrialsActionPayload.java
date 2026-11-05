package craftbound.network;

import craftbound.Craftbound;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record TrialsActionPayload(BlockPos table, String runId, int revision, String action, int index) implements CustomPacketPayload {
    public static final Type<TrialsActionPayload> TYPE = new Type<>(Craftbound.id("trials_action"));
    public static final StreamCodec<RegistryFriendlyByteBuf, TrialsActionPayload> CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, TrialsActionPayload::table, ByteBufCodecs.stringUtf8(40), TrialsActionPayload::runId,
            ByteBufCodecs.VAR_INT, TrialsActionPayload::revision, ByteBufCodecs.stringUtf8(12), TrialsActionPayload::action,
            ByteBufCodecs.VAR_INT, TrialsActionPayload::index, TrialsActionPayload::new);
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
