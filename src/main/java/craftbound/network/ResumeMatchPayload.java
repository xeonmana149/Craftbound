package craftbound.network;

import craftbound.Craftbound;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Offers a saved match to its participant; opening this prompt does not consent to resuming. */
public record ResumeMatchPayload(BlockPos table, boolean multiplayer) implements CustomPacketPayload {
    public static final Type<ResumeMatchPayload> TYPE = new Type<>(Craftbound.id("resume_match"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ResumeMatchPayload> CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, ResumeMatchPayload::table,
            ByteBufCodecs.BOOL, ResumeMatchPayload::multiplayer,
            ResumeMatchPayload::new);
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
