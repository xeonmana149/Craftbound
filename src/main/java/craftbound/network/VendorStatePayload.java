package craftbound.network;

import craftbound.Craftbound;
import craftbound.vendor.CardVendor;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record VendorStatePayload(BlockPos pos,long day,List<CardVendor.Offer> offers,int bought,int packPrice,long ticks,boolean open)
        implements CustomPacketPayload {
    public static final Type<VendorStatePayload> TYPE=new Type<>(Craftbound.id("vendor_state"));
    public static final StreamCodec<RegistryFriendlyByteBuf,VendorStatePayload> CODEC=new StreamCodec<>(){
        public VendorStatePayload decode(RegistryFriendlyByteBuf b){
            BlockPos pos=b.readBlockPos();long day=b.readLong();int count=b.readVarInt();
            if(count<0||count>CardVendor.SLOTS)throw new IllegalArgumentException("Invalid vendor lineup");
            var offers=new ArrayList<CardVendor.Offer>();
            for(int i=0;i<count;i++)offers.add(new CardVendor.Offer(b.readIdentifier(),b.readBoolean(),b.readVarInt()));
            return new VendorStatePayload(pos,day,List.copyOf(offers),b.readVarInt(),b.readVarInt(),b.readLong(),b.readBoolean());
        }
        public void encode(RegistryFriendlyByteBuf b,VendorStatePayload v){
            b.writeBlockPos(v.pos);b.writeLong(v.day);b.writeVarInt(v.offers.size());
            for(var o:v.offers){b.writeIdentifier(o.card());b.writeBoolean(o.enchanted());b.writeVarInt(o.price());}
            b.writeVarInt(v.bought);b.writeVarInt(v.packPrice);b.writeLong(v.ticks);b.writeBoolean(v.open);
        }
    };
    @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
}
