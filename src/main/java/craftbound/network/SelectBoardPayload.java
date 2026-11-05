package craftbound.network;

import craftbound.Craftbound;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record SelectBoardPayload(String design) implements CustomPacketPayload {
    public static final Type<SelectBoardPayload> TYPE = new Type<>(Craftbound.id("select_board"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SelectBoardPayload> CODEC =
            StreamCodec.composite(ByteBufCodecs.stringUtf8(32), SelectBoardPayload::design, SelectBoardPayload::new);
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
