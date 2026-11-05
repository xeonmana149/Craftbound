package craftbound.network;

import craftbound.Craftbound;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.*;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record CommanderViewPayload(String json, boolean open) implements CustomPacketPayload {
    public static final Type<CommanderViewPayload> TYPE = new Type<>(Craftbound.id("commander_view"));
    public static final StreamCodec<RegistryFriendlyByteBuf,CommanderViewPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.stringUtf8(262144), CommanderViewPayload::json,
            ByteBufCodecs.BOOL, CommanderViewPayload::open,
            CommanderViewPayload::new);
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
