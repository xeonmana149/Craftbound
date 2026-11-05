package craftbound.block;

import craftbound.vendor.CardVendor;
import craftbound.vendor.VendorPurchases;
import com.mojang.serialization.Codec;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

public final class CardVendorBlockEntity extends BlockEntity {
    private final VendorPurchases purchases=new VendorPurchases();
    private static final Codec<Map<String,Integer>> RECORDS=Codec.unboundedMap(Codec.STRING,Codec.INT);
    public CardVendorBlockEntity(BlockPos pos,BlockState state){super(ModBlocks.CARD_VENDOR_ENTITY,pos,state);}
    public int bought(UUID player,long day){
        boolean changed=purchases.day()!=day;int mask=purchases.bought(player,day);if(changed)setChanged();return mask;
    }
    public void markBought(UUID player,long day,int slot){purchases.mark(player,day,slot);setChanged();}
    public void markPackBought(UUID player,long day){purchases.markPack(player,day);setChanged();}
    @Override protected void loadAdditional(ValueInput input){
        super.loadAdditional(input);
        purchases.load(input.getLongOr("day",Long.MIN_VALUE),input.read("purchases",RECORDS).orElse(Map.of()));
    }
    @Override protected void saveAdditional(ValueOutput output){
        super.saveAdditional(output);
        long day=level==null?purchases.day():CardVendor.day(level.getOverworldClockTime());
        var entries=purchases.snapshot(day);
        output.putLong("day",day);output.store("purchases",RECORDS,entries);
    }
}
