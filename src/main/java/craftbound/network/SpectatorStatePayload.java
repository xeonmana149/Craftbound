package craftbound.network;
import craftbound.Craftbound;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.*;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
public record SpectatorStatePayload(BlockPos table,String json,boolean open,String notice) implements CustomPacketPayload {
 public static final Type<SpectatorStatePayload> TYPE=new Type<>(Craftbound.id("spectator_state"));
 public static final StreamCodec<RegistryFriendlyByteBuf,SpectatorStatePayload> CODEC=StreamCodec.composite(
  BlockPos.STREAM_CODEC,SpectatorStatePayload::table,ByteBufCodecs.STRING_UTF8,SpectatorStatePayload::json,
  ByteBufCodecs.BOOL,SpectatorStatePayload::open,ByteBufCodecs.STRING_UTF8,SpectatorStatePayload::notice,SpectatorStatePayload::new);
 public Type<? extends CustomPacketPayload> type(){return TYPE;}
}
