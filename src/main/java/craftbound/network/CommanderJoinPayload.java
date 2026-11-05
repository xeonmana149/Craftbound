package craftbound.network;

import craftbound.Craftbound;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.*;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record CommanderJoinPayload(BlockPos table, int slot, String deckId, String leader) implements CustomPacketPayload {
    public static final Type<CommanderJoinPayload> TYPE = new Type<>(Craftbound.id("commander_join"));
    public static final StreamCodec<RegistryFriendlyByteBuf,CommanderJoinPayload> CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, CommanderJoinPayload::table,
            ByteBufCodecs.VAR_INT, CommanderJoinPayload::slot,
            ByteBufCodecs.stringUtf8(40), CommanderJoinPayload::deckId,
            ByteBufCodecs.stringUtf8(180), CommanderJoinPayload::leader,
            CommanderJoinPayload::new);
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
