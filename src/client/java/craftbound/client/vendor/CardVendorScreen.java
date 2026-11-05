package craftbound.client.vendor;

import com.mojang.blaze3d.platform.InputConstants;
import craftbound.client.card.CardRenderer;
import craftbound.item.*;
import craftbound.network.*;
import craftbound.vendor.*;
import java.util.*;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.*;

public final class CardVendorScreen extends Screen {
    private VendorStatePayload state;
    private long receivedAt;
    private int refresh;
    private boolean pending;
    private final List<Spot> spots=new ArrayList<>();
    private Spot pack;
    private record Spot(int x,int y,int w,int h,int slot){
        boolean contains(double mx,double my){return mx>=x&&mx<x+w&&my>=y&&my<y+h;}
    }
    public CardVendorScreen(VendorStatePayload state){super(Component.literal("Card Vendor"));update(state);}
    public BlockPos pos(){return state.pos();}
    public void update(VendorStatePayload state){this.state=state;receivedAt=System.nanoTime();pending=false;}
    @Override public boolean isPauseScreen(){return false;}
    @Override public void tick(){
        if(minecraft.player==null||minecraft.level==null)return;
        if(!minecraft.level.getBlockState(state.pos()).is(craftbound.block.ModBlocks.CARD_VENDOR)){onClose();return;}
        if(minecraft.player.distanceToSqr(state.pos().getX()+0.5,state.pos().getY()+0.5,state.pos().getZ()+0.5)>CardVendor.BUY_RANGE*CardVendor.BUY_RANGE){onClose();return;}
        if(++refresh>=CardVendor.REFRESH_TICKS){refresh=0;ClientPlayNetworking.send(new VendorRefreshPayload(state.pos()));}
    }
    @Override public void extractRenderState(GuiGraphicsExtractor g,int mouseX,int mouseY,float partialTick){
        super.extractRenderState(g,mouseX,mouseY,partialTick);
        g.fill(5,5,width-5,height-5,0xF0222929);g.outline(5,5,width-10,height-10,0xFF80A58E);
        g.text(font,title,14,14,0xFFFFE7BA,true);
        long ticks=Math.max(0,state.ticks()-(System.nanoTime()-receivedAt)/50_000_000L);
        long seconds=(ticks+19)/20;
        CardRenderer.fitText(g,font,Component.literal("New cards in "+(seconds/60)+":"+String.format(Locale.ROOT,"%02d",seconds%60)),
                (width+135)/2,14,Math.max(70,width-155),0xFFB5D7C0,true,false);
        // Right-align the countdown separately from the title.
        int top=38,bottom=height-32,available=bottom-top;
        int packWidth=Math.max(60,width/7),previewWidth=Math.max(82,width/4);
        int gridLeft=packWidth+24,previewLeft=width-previewWidth-14,gridWidth=previewLeft-gridLeft-14;
        g.fill(gridLeft-7,top-5,gridLeft-6,bottom,0xFF547063);
        g.fill(previewLeft-7,top-5,previewLeft-6,bottom,0xFF547063);
        int money=minecraft.player==null?0:VendorTrading.emeralds(minecraft.player.getInventory());
        CardRenderer.fitText(g,font,Component.literal("Booster pack"),14,top,packWidth-2,0xFFFFE7BA,false,false);
        g.pose().pushMatrix();g.pose().translate(14+(packWidth-32)/2F,top+22);g.pose().scale(2,2);
        g.item(new ItemStack(ModItems.BOOSTER_PACK),0,0);g.pose().popMatrix();
        price(g,14,top+61,state.packPrice(),money,packWidth-4);
        pack=new Spot(14,top+85,packWidth-4,20,-1);
        int packsLeft=craftbound.vendor.CardVendor.packsLeft(state.bought());
        boolean afford=money>=state.packPrice()&&packsLeft>0&&!pending;
        g.fill(pack.x,pack.y,pack.x+pack.w,pack.y+pack.h,afford?0xFF3C7150:0xFF454A46);
        g.outline(pack.x,pack.y,pack.w,pack.h,afford?0xFFADCEA9:0xFF68716C);
        CardRenderer.fitText(g,font,Component.literal(packsLeft>0?"Buy":"SOLD OUT"),pack.x+pack.w/2,pack.y+6,pack.w-8,packsLeft>0?0xFFFFFFFF:0xFFFFD080,true,true);
        CardRenderer.fitText(g,font,Component.literal(packsLeft>0?packsLeft+" left today":"Restocks tomorrow"),pack.x+pack.w/2,pack.y+pack.h+4,pack.w,0xFFB5D7C0,true,false);
        int gap=8,label=21,cellWidth=(gridWidth-gap*2)/3;
        float scale=Math.max(0.1F,Math.min(cellWidth/(float)CardRenderer.WIDTH,(available-label*2-gap)/(2F*CardRenderer.HEIGHT)));
        int cw=Math.round(CardRenderer.WIDTH*scale),ch=Math.round(CardRenderer.HEIGHT*scale);
        spots.clear();int hovered=-1;
        for(int i=0;i<CardVendor.SLOTS;i++){
            int x=gridLeft+(i%3)*(cellWidth+gap)+(cellWidth-cw)/2,y=top+(i/3)*(ch+label+gap);
            if(i>=state.offers().size()){
                g.fill(x,y,x+cw,y+ch,0xFF343B37);continue;
            }
            var offer=state.offers().get(i);
            CardRenderer.draw(g,font,CardItem.of(offer.card(),offer.enchanted()),x,y,scale);
            var spot=new Spot(x,y,cw,ch,i);spots.add(spot);
            boolean sold=(state.bought()&(1<<i))!=0;
            if(sold){
                g.fill(x,y,x+cw,y+ch,0xAA202020);
                g.fill(x+2,y+ch/2-6,x+cw-2,y+ch/2+7,0xEE392A20);
                CardRenderer.fitText(g,font,Component.literal("SOLD"),x+cw/2,y+ch/2-4,cw-8,0xFFFFD080,true,false);
            }
            if(spot.contains(mouseX,mouseY)){hovered=i;g.outline(x-1,y-1,cw+2,ch+2,0xFFFFFFFF);}
            price(g,x,y+ch+3,offer.price(),money,cw);
        }
        if(hovered>=0){
            var offer=state.offers().get(hovered);
            float large=Math.min(previewWidth/(float)CardRenderer.WIDTH,available/(float)CardRenderer.HEIGHT);
            CardRenderer.draw(g,font,CardItem.of(offer.card(),offer.enchanted()),
                    previewLeft+(previewWidth-Math.round(CardRenderer.WIDTH*large))/2,top,large);
        }else{
            CardRenderer.fitText(g,font,Component.literal("Hover to inspect"),previewLeft+previewWidth/2,top+available/2,
                    previewWidth-4,0xFFABC2AF,true,false);
        }
        g.fill(12,height-29,width-12,height-28,0xFF547063);
        g.text(font,"You have: "+money+" emeralds",14,height-21,0xFFE4ECD9,false);
        g.text(font,"Esc: Close",width-14-font.width("Esc: Close"),height-21,0xFFA9BAAE,false);
    }
    private void price(GuiGraphicsExtractor g,int x,int y,int amount,int money,int room){
        g.item(new ItemStack(Items.EMERALD),x,y);
        CardRenderer.fitText(g,font,Component.literal(Integer.toString(amount)),x+18,y+4,Math.max(10,room-18),
                money>=amount?0xFFE9E7CB:0xFFFF7575,false,false);
    }
    @Override public boolean mouseClicked(MouseButtonEvent event,boolean doubleClick){
        if(event.button()!=InputConstants.MOUSE_BUTTON_LEFT)return super.mouseClicked(event,doubleClick);
        if(pending)return true;
        int slot=-2;
        if(pack!=null&&pack.contains(event.x(),event.y()))slot=craftbound.vendor.CardVendor.packsLeft(state.bought())>0?-1:-3;
        if(slot==-3)return true;
        for(var spot:spots)if(spot.contains(event.x(),event.y()))slot=spot.slot;
        if(slot==-2)return super.mouseClicked(event,doubleClick);
        var offer=slot<0?null:state.offers().get(slot);
        pending=true;
        ClientPlayNetworking.send(new VendorBuyPayload(state.pos(),slot,state.day(),
                offer==null?"":offer.card().toString(),offer!=null&&offer.enchanted()));
        return true;
    }
}
