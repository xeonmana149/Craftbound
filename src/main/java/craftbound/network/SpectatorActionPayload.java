package craftbound.network;
import craftbound.Craftbound;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.*;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
public record SpectatorActionPayload(BlockPos table,int action,int side,String request) implements CustomPacketPayload {
 public static final int CLOSE=0,REQUEST=1,ACCEPT=2,DENY=3,REVOKE=4;
 public static final Type<SpectatorActionPayload> TYPE=new Type<>(Craftbound.id("spectator_action"));
 public static final StreamCodec<RegistryFriendlyByteBuf,SpectatorActionPayload> CODEC=StreamCodec.composite(
  BlockPos.STREAM_CODEC,SpectatorActionPayload::table,ByteBufCodecs.VAR_INT,SpectatorActionPayload::action,
  ByteBufCodecs.VAR_INT,SpectatorActionPayload::side,ByteBufCodecs.STRING_UTF8,SpectatorActionPayload::request,SpectatorActionPayload::new);
 public Type<? extends CustomPacketPayload> type(){return TYPE;}
}
