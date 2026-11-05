package craftbound.vendor;

import craftbound.block.*;
import craftbound.item.*;
import craftbound.network.*;
import net.fabricmc.fabric.api.networking.v1.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.*;
import net.minecraft.sounds.*;
import net.minecraft.util.Prediction;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

public final class VendorTrading {
    public static void initialize(){
        PayloadTypeRegistry.clientboundPlay().register(VendorStatePayload.TYPE,VendorStatePayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(VendorBuyPayload.TYPE,VendorBuyPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(VendorRefreshPayload.TYPE,VendorRefreshPayload.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(VendorBuyPayload.TYPE,(p,c)->buy(c.player(),p));
        ServerPlayNetworking.registerGlobalReceiver(VendorRefreshPayload.TYPE,(p,c)->send(c.player(),p.pos(),false));
    }
    private static CardVendorBlockEntity vendor(ServerPlayer player,BlockPos pos){
        if(!player.isAlive()||player.isSpectator()||player.distanceToSqr(pos.getX()+0.5,pos.getY()+0.5,pos.getZ()+0.5)>CardVendor.BUY_RANGE*CardVendor.BUY_RANGE
                ||!player.level().hasChunkAt(pos))return null;
        var state=player.level().getBlockState(pos);
        if(!state.is(ModBlocks.CARD_VENDOR)||state.getValue(CardVendorBlock.HALF)!=DoubleBlockHalf.LOWER)return null;
        return player.level().getBlockEntity(pos) instanceof CardVendorBlockEntity vendor?vendor:null;
    }
    public static int emeralds(Inventory inventory){
        int total=0;for(int i=0;i<inventory.getContainerSize();i++)if(inventory.getItem(i).is(Items.EMERALD))total+=inventory.getItem(i).getCount();
        return total;
    }
    public static boolean pay(Inventory inventory,int price){
        if(price<0||emeralds(inventory)<price)return false;
        int left=price;
        for(int i=0;i<inventory.getContainerSize()&&left>0;i++){
            var stack=inventory.getItem(i);if(!stack.is(Items.EMERALD))continue;
            int take=Math.min(left,stack.getCount());stack.shrink(take);left-=take;
        }
        inventory.setChanged();return true;
    }
    public static void send(ServerPlayer player,BlockPos pos,boolean open){
        var vendor=vendor(player,pos);if(vendor==null)return;
        var level=(ServerLevel)player.level();long ticks=level.getOverworldClockTime(),day=CardVendor.day(ticks);
        ServerPlayNetworking.send(player,new VendorStatePayload(pos,day,CardVendor.lineup(level.getSeed(),pos.asLong(),day),
                vendor.bought(player.getUUID(),day),CardVendor.PACK_PRICE,CardVendor.untilRestock(ticks),open));
    }
    private static void buy(ServerPlayer player,VendorBuyPayload request){
        var vendor=vendor(player,request.pos());if(vendor==null)return;
        var level=(ServerLevel)player.level();long day=CardVendor.day(level.getOverworldClockTime());
        var offers=CardVendor.lineup(level.getSeed(),request.pos().asLong(),day);
        int slot=request.slot();
        if(slot==-1 && craftbound.card.Cards.collectible().isEmpty()) {
            player.sendOverlayMessage(net.minecraft.network.chat.Component.literal("Packs are unavailable until collectible cards are added."));
            level.playSound(null,request.pos(),SoundEvents.DISPENSER_FAIL,SoundSource.BLOCKS,0.65F,0.75F);
            send(player,request.pos(),false);return;
        }
        boolean valid=CardVendor.canBuy(slot,request.day(),day,vendor.bought(player.getUUID(),day),
                emeralds(player.getInventory()),offers,request.card(),request.enchanted());
        if(valid){
            var item=slot==-1?new ItemStack(ModItems.BOOSTER_PACK):CardItem.of(offers.get(slot).card(),offers.get(slot).enchanted());
            int price=slot==-1?CardVendor.PACK_PRICE:offers.get(slot).price();
            if(pay(player.getInventory(),price)){
                if(slot>=0)vendor.markBought(player.getUUID(),day,slot);
                else vendor.markPackBought(player.getUUID(),day);
                player.getInventory().placeItemBackInInventory(item,Prediction.SERVER_ONLY);
                player.containerMenu.broadcastChanges();
            }else valid=false;
        }
        if(valid){
            level.playSound(null,request.pos(),SoundEvents.DISPENSER_DISPENSE,SoundSource.BLOCKS,0.55F,1.1F);
            level.playSound(null,request.pos(),SoundEvents.AMETHYST_BLOCK_CHIME,SoundSource.BLOCKS,0.65F,1.25F);
        }else{
            level.playSound(null,request.pos(),SoundEvents.DISPENSER_FAIL,SoundSource.BLOCKS,0.65F,0.75F);
        }
        send(player,request.pos(),false);
    }
    private VendorTrading(){}
}
