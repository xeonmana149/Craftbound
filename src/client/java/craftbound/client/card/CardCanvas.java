package craftbound.client.card;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix3x2fStack;

/** Drawing targets for one shared card layout, on screen or on a physical display. */
public interface CardCanvas {
    Matrix3x2fStack pose();
    void fill(int x0, int y0, int x1, int y1, int color);
    void text(Font font, FormattedCharSequence text, int x, int y, int color, boolean shadow);
    void item(ItemStack stack, int x, int y);
    /** Source coordinates are measured on the 120 by 168 card template. */
    void frameRegion(net.minecraft.resources.Identifier texture, int u, int v, int sw, int sh,
                     int x, int y, int width, int height);
    void shimmer(int x, int y, int width, int height, float diagonal, int halfWidth, int alpha);
    default void fillGradient(int x0,int y0,int x1,int y1,int top,int bottom) {
        int h=y1-y0;if(h<=0)return;
        for(int step=0;step<8;step++) {
            int color=0;for(int shift:new int[]{0,8,16,24}) {
                int a=(top>>>shift)&255,b=(bottom>>>shift)&255;
                color|=Math.round(a+(b-a)*step/7F)<<shift;
            }
            fill(x0,y0+h*step/8,x1,y0+h*(step+1)/8,color);
        }
    }
    default void outline(int x, int y, int w, int h, int color) {
        fill(x,y,x+w,y+1,color); fill(x,y+h-1,x+w,y+h,color);
        fill(x,y+1,x+1,y+h-1,color); fill(x+w-1,y+1,x+w,y+h-1,color);
    }
    default void text(Font font, Component text, int x, int y, int color, boolean shadow) {
        text(font,text.getVisualOrderText(),x,y,color,shadow);
    }
    default void text(Font font, String text, int x, int y, int color, boolean shadow) {
        text(font,Component.literal(text),x,y,color,shadow);
    }
    default void centeredText(Font font, String text, int x, int y, int color) {
        text(font,text,x-font.width(text)/2,y,color,true);
    }
    record Gui(GuiGraphicsExtractor graphics) implements CardCanvas {
        public Matrix3x2fStack pose() { return graphics.pose(); }
        public void fillGradient(int x0,int y0,int x1,int y1,int top,int bottom) { graphics.fillGradient(x0,y0,x1,y1,top,bottom); }
        public void fill(int x0,int y0,int x1,int y1,int color) { graphics.fill(x0,y0,x1,y1,color); }
        public void text(Font font,FormattedCharSequence text,int x,int y,int color,boolean shadow) {
            graphics.text(font,text,x,y,color,shadow);
        }
        public void item(ItemStack stack,int x,int y) { graphics.item(stack,x,y); }
        public void frameRegion(net.minecraft.resources.Identifier texture, int u, int v, int sw, int sh,
                                int x, int y, int width, int height) {
            graphics.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, texture,
                    x, y, u, v, width, height, sw, sh, CardRenderer.WIDTH, CardRenderer.HEIGHT, 0xFFFFFFFF);
        }
        public void shimmer(int x,int y,int w,int h,float diagonal,int halfWidth,int alpha) {
            ShimmerRenderState.submit(graphics,x,y,w,h,diagonal,halfWidth,alpha);
        }
    }
}
