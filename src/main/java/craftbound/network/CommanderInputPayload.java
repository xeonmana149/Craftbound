package craftbound.network;

import craftbound.Craftbound;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.*;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record CommanderInputPayload(String session, int revision, int action, int index, int rival, int targetSide, int targetId) implements CustomPacketPayload {
    public static final Type<CommanderInputPayload> TYPE = new Type<>(Craftbound.id("commander_input"));
    public static final StreamCodec<RegistryFriendlyByteBuf,CommanderInputPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.stringUtf8(36), CommanderInputPayload::session,
            ByteBufCodecs.VAR_INT, CommanderInputPayload::revision,
            ByteBufCodecs.VAR_INT, CommanderInputPayload::action,
            ByteBufCodecs.VAR_INT, CommanderInputPayload::index,
            ByteBufCodecs.VAR_INT, CommanderInputPayload::rival,
            ByteBufCodecs.VAR_INT, CommanderInputPayload::targetSide,
            ByteBufCodecs.VAR_INT, CommanderInputPayload::targetId,
            CommanderInputPayload::new);
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
