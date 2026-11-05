package craftbound.client.match;
import craftbound.network.*;
import net.minecraft.core.BlockPos;
import net.minecraft.client.gui.*;
import net.minecraft.client.input.MouseButtonEvent;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

/** A small, non-pausing consent prompt. Server owns every grant and timeout. */
public final class HandConsentOverlay {
 private static HandConsentPayload state=new HandConsentPayload("","",0);
 public static void update(HandConsentPayload p){state=p;}
 public static void clear(){state=new HandConsentPayload("","",0);}
 public static void draw(GuiGraphicsExtractor g,Font font,int width){
  if(state.request().isEmpty()&&state.sharing()==0)return;
  int w=Math.min(240,width-16),x=8,y=8;
  g.fill(x,y,x+w,y+48,0xF0202925);g.outline(x,y,w,48,0xFFCFBA87);
  if(!state.request().isEmpty()){
   g.text(font,font.plainSubstrByWidth(state.viewer()+" wants to see your hand",w-12),x+6,y+6,0xFFF4E6C5,false);
   button(g,font,"Allow",x+6,y+25,70,0xFF3C694E);button(g,font,"Deny",x+82,y+25,70,0xFF70463C);
  }else{
   g.text(font,"Hand shared with "+state.sharing()+" spectator(s)",x+6,y+6,0xFFF4E6C5,false);
   button(g,font,"Stop sharing",x+6,y+25,130,0xFF70463C);
  }
 }
 private static void button(GuiGraphicsExtractor g,Font f,String text,int x,int y,int w,int color){g.fill(x,y,x+w,y+17,color);g.centeredText(f,text,x+w/2,y+4,0xFFFFFFFF);}
 public static boolean click(MouseButtonEvent e,int width){
  if(state.request().isEmpty()&&state.sharing()==0)return false;
  if(e.x()<8||e.x()>8+Math.min(240,width-16)||e.y()<8||e.y()>56)return false;
  if(e.button()!=InputConstants.MOUSE_BUTTON_LEFT||e.y()<33||e.y()>50)return true;
  int action=-1;
  if(!state.request().isEmpty()){
   if(e.x()>=14&&e.x()<84)action=SpectatorActionPayload.ACCEPT;
   else if(e.x()>=90&&e.x()<160)action=SpectatorActionPayload.DENY;
  }else if(e.x()>=14&&e.x()<144)action=SpectatorActionPayload.REVOKE;
  if(action>=0){ClientPlayNetworking.send(new SpectatorActionPayload(BlockPos.ZERO,action,0,state.request()));
   state=new HandConsentPayload("","",state.sharing());}
  return true;
 }
 private HandConsentOverlay(){}
}
