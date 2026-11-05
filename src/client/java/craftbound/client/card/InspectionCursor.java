package craftbound.client.card;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;

/** Remember an inspection position in window proportions so resizing does not put it off screen. */
final class InspectionCursor {
    private static boolean remembered;
    private static double x, y;
    static void remember(Minecraft client) {
        var window=client.getWindow();
        if(window.getScreenWidth()<=0 || window.getScreenHeight()<=0)return;
        x=Math.clamp(client.mouseHandler.xpos()/window.getScreenWidth(),0.0,1.0);
        y=Math.clamp(client.mouseHandler.ypos()/window.getScreenHeight(),0.0,1.0);
        remembered=true;
    }
    static void restore(Minecraft client) {
        if(!remembered)return;
        var window=client.getWindow();
        InputConstants.releaseMouse(window,Math.min(x*window.getScreenWidth(),Math.max(0,window.getScreenWidth()-1)),
                Math.min(y*window.getScreenHeight(),Math.max(0,window.getScreenHeight()-1)));
        client.mouseHandler.resyncMousePosition();
    }
    private InspectionCursor() {}
}
