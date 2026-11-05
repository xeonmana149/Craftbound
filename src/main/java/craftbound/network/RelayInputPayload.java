package craftbound.network;

import craftbound.Craftbound;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.*;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record RelayInputPayload(String session,int revision,int action,int index,int rival,int targetSide,int targetId) implements CustomPacketPayload {
    public static final Type<RelayInputPayload> TYPE=new Type<>(Craftbound.id("relay_input"));
    public static final StreamCodec<RegistryFriendlyByteBuf,RelayInputPayload> CODEC=StreamCodec.composite(
        ByteBufCodecs.stringUtf8(36),RelayInputPayload::session,ByteBufCodecs.VAR_INT,RelayInputPayload::revision,
        ByteBufCodecs.VAR_INT,RelayInputPayload::action,ByteBufCodecs.VAR_INT,RelayInputPayload::index,
        ByteBufCodecs.VAR_INT,RelayInputPayload::rival,
        ByteBufCodecs.VAR_INT,RelayInputPayload::targetSide,ByteBufCodecs.VAR_INT,RelayInputPayload::targetId,RelayInputPayload::new);
    @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
}