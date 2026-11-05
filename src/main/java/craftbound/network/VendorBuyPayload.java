package craftbound.network;

import craftbound.Craftbound;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.*;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Expected day/card prevent a stale screen from buying a different dawn/catalogue offer. */
public record VendorBuyPayload(BlockPos pos,int slot,long day,String card,boolean enchanted) implements CustomPacketPayload {
    public static final Type<VendorBuyPayload> TYPE=new Type<>(Craftbound.id("vendor_buy"));
    public static final StreamCodec<RegistryFriendlyByteBuf,VendorBuyPayload> CODEC=StreamCodec.composite(
            BlockPos.STREAM_CODEC,VendorBuyPayload::pos,ByteBufCodecs.VAR_INT,VendorBuyPayload::slot,
            ByteBufCodecs.LONG,VendorBuyPayload::day,ByteBufCodecs.stringUtf8(120),VendorBuyPayload::card,
            ByteBufCodecs.BOOL,VendorBuyPayload::enchanted,VendorBuyPayload::new);
    @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
}
