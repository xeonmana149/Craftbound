package craftbound.network;

import craftbound.Craftbound;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.*;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record RelayJoinPayload(BlockPos table,int slot,String deckId,int team) implements CustomPacketPayload {
    public static final Type<RelayJoinPayload> TYPE=new Type<>(Craftbound.id("relay_join"));
    public static final StreamCodec<RegistryFriendlyByteBuf,RelayJoinPayload> CODEC=StreamCodec.composite(
        BlockPos.STREAM_CODEC,RelayJoinPayload::table,ByteBufCodecs.VAR_INT,RelayJoinPayload::slot,
        ByteBufCodecs.stringUtf8(40),RelayJoinPayload::deckId,ByteBufCodecs.VAR_INT,RelayJoinPayload::team,RelayJoinPayload::new);
    @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
}