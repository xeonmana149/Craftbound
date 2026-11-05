package craftbound.client.match;
import craftbound.client.card.CardCanvas;
import net.minecraft.client.gui.Font;

/** Pixel TNT cord: shown only during the configured final warning period. */
public final class TurnFuse {
    public static void draw(CardCanvas g,Font font,int left,int right,int y,float seconds,long now) {
        draw(g,font,left,right,y,seconds,15,now);
    }
    public static void draw(CardCanvas g,Font font,int left,int right,int y,float seconds,float warningSeconds,long now) {
        if(seconds<0 || warningSeconds<=0 || seconds>warningSeconds || right<=left)return;
        int ember=left+Math.round((right-left)*(1-Math.clamp(seconds/warningSeconds,0,1)));
        g.fill(left,y-2,right+1,y+3,0xFF201710);
        g.fill(left,y,ember,y+1,0xFF504039);
        g.fill(ember,y-1,right,y+2,0xFFB69660);
        for(int x=ember;x<right;x+=5)g.fill(x,y-1,Math.min(x+2,right),y,0xFFE9CB89);
        int flicker=(int)(now/90%2);
        g.fill(ember-3,y-3-flicker,ember+4,y+4,0xFFE56B22);
        g.fill(ember-1,y-2,ember+2,y+2,0xFFFFE59B);
        g.fill(ember+4,y-5,ember+5,y-3,0xFFFFCA55);
        g.text(font,""+(int)Math.ceil(seconds),right+5,y-4,0xFFFFD787,true);
    }
    private TurnFuse(){}
}
