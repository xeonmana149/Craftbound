package craftbound.network;

import craftbound.Craftbound;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.*;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record RelayViewPayload(String json,boolean open) implements CustomPacketPayload {
    public static final Type<RelayViewPayload> TYPE=new Type<>(Craftbound.id("relay_view"));
    public static final StreamCodec<RegistryFriendlyByteBuf,RelayViewPayload> CODEC=StreamCodec.composite(
        ByteBufCodecs.stringUtf8(262144),RelayViewPayload::json,ByteBufCodecs.BOOL,RelayViewPayload::open,RelayViewPayload::new);
    @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
}