package craftbound.client.binder;

import net.minecraft.client.gui.GuiGraphicsExtractor;

/** The binder cover and chapter palette, also used by its rulebook. */
public final class BinderTheme {
    public static final int WOOD = 0xFF5A2E1E;
    public static final int WOOD_DARK = 0xFF3A1C12;
    public static final int PAGE = 0xFFF2E3C2;
    public static final int TEXT_DARK = 0xFF3E231D;
    public static final int GOLD = 0xFFFFD27A;
    public static void cover(GuiGraphicsExtractor g,int panelX,int panelY,int panelWidth,int panelHeight) {
        g.fill(panelX, panelY, panelX + panelWidth, panelY + panelHeight, WOOD);
        g.outline(panelX, panelY, panelWidth, panelHeight, WOOD_DARK);
        // Stitched leather cover with inset brass corner caps.
        g.outline(panelX+2,panelY+2,panelWidth-4,panelHeight-4,0xFF935F3F);
        for (int offset=8; offset<panelWidth-8; offset+=8) {
            g.fill(panelX+offset,panelY+3,panelX+offset+3,panelY+4,0xFFA87B54);
            g.fill(panelX+offset,panelY+panelHeight-4,panelX+offset+3,panelY+panelHeight-3,0xFFA87B54);
        }
        for (int side=0;side<2;side++) for(int end=0;end<2;end++) {
            int x=panelX+(side==0?1:panelWidth-8), y=panelY+(end==0?1:panelHeight-8);
            g.fill(x,y+(end==0?0:5),x+7,y+(end==0?2:7),0xFFCA9E60);
            g.fill(x+(side==0?0:5),y,x+(side==0?2:7),y+7,0xFFCA9E60);
        }
    }
    private BinderTheme(){}
}
