package craftbound.network;
import craftbound.Craftbound;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
public record VendorRefreshPayload(BlockPos pos) implements CustomPacketPayload {
    public static final Type<VendorRefreshPayload> TYPE=new Type<>(Craftbound.id("vendor_refresh"));
    public static final StreamCodec<RegistryFriendlyByteBuf,VendorRefreshPayload> CODEC=StreamCodec.composite(BlockPos.STREAM_CODEC,VendorRefreshPayload::pos,VendorRefreshPayload::new);
    @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
}
