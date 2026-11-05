package craftbound.client.match;

import net.minecraft.client.gui.GuiGraphicsExtractor;

/** Quiet cartographic scenery, independent of room hitboxes and the authoritative route. */
final class TrialsAtlasBackdrop {
    private static final int PAPER = 0xFFE3D1AA, EDGE = 0xFFC5AC7F;
    static void draw(GuiGraphicsExtractor g, int left, int top, int right, int bottom, float scroll, long seed) {
        if (right <= left || bottom <= top) return;
        int width = right - left, height = bottom - top;
        g.fill(left, top, right, bottom, PAPER);
        g.enableScissor(left, top, right, bottom);
        // Broad paper washes stay nearly flat so route silhouettes remain the strongest marks.
        for (int k=0;k<7;k++) {
            int xx=left+k*width/7;
            g.fill(xx,top,left+(k+1)*width/7,bottom,k%3==0?0xFFE0CDA4:k%3==1?0xFFE6D5B1:PAPER);
        }
        g.fill(left,top,left+3,bottom,EDGE); g.fill(right-3,top,right,bottom,EDGE);
        g.fill(left+3,top,left+7,bottom,0xFFD4BE94); g.fill(right-7,top,right-3,bottom,0xFFD4BE94);
        // A fixed world coordinate anchors cliff contours and landmarks while the paper scrolls.
        int base=(int)Math.floor(scroll/80)-1;
        int rows=height/80+3;
        for(int row=base;row<base+rows;row++) {
            int yy=top+row*80-Math.round(scroll);
            long roll=mix(seed+row*0x9E3779B97F4A7C15L);
            int span=Math.max(12,Math.min(34,width/10));
            for(int side=0;side<2;side++){
                int dir=side==0?1:-1, origin=side==0?left+11:right-11;
                int peak=(int)((roll>>>(side*7))&15);
                int[] points={0,9+peak/2,15,span,span-6,span-4,7,0};
                for(int contour=0;contour<3;contour++)for(int p=0;p<points.length-1;p++){
                    int xa=origin+dir*(points[p]+contour*3);
                    int xb=origin+dir*(points[p+1]+contour*3);
                    line(g,xa,yy+p*8+contour*2,xb,yy+(p+1)*8+contour*2,0xFFCEB98E);
                }
                int mx=origin+dir*(9+peak/3), my=yy+20;
                if(((row+side)&3)==0) mountain(g,mx,my,dir,0xFFBDA984);
                if(((row+side)&3)==1) trees(g,mx,my);
                if(((row+side)&3)==2) ruin(g,mx,my,dir);
                if(((row+side)&3)==3) {
                    for(int k=0;k<3;k++){
                        int xx=mx+dir*(k*5),cy=my+(k%2)*5;
                        g.fill(xx,cy,xx+3,cy+2,0xFFC5A276);
                        g.fill(xx+1,cy-1,xx+3,cy,0xFFD7BC86);
                    }
                }
            }
        }
        // Sparse grain, never transparent animation or a per-pixel full-screen pass.
        for(int i=0;i<42;i++){
            long n=mix(seed+i*7919L);
            int xx=left+8+(int)Math.floorMod(n,Math.max(1,width-16));
            int yy=top+(int)Math.floorMod((n>>>20)-Math.round(scroll),Math.max(1,height));
            g.fill(xx,yy,xx+1+(i%3),yy+1,0xFFD8C49B);
        }
        // Folds sit under the route, not over the nodes.
        int fold=left+width/2;
        g.fill(fold,top,fold+1,bottom,0xFFDCC79E);
        g.fill(fold+1,top,fold+2,bottom,0xFFEAD9B5);
        g.disableScissor();
        g.outline(left,top,width,height,0xFFB89C70);
    }
    private static long mix(long x) {
        x=(x^(x>>>30))*0xBF58476D1CE4E5B9L;
        x=(x^(x>>>27))*0x94D049BB133111EBL;
        return x^(x>>>31);
    }
    private static void line(GuiGraphicsExtractor g,int x1,int y1,int x2,int y2,int color){
        int steps=Math.max(Math.abs(x2-x1),Math.abs(y2-y1))/3+1;
        for(int i=0;i<=steps;i++){
            int x=x1+(x2-x1)*i/steps,y=y1+(y2-y1)*i/steps;
            g.fill(x,y,x+2,y+2,color);
        }
    }
    private static void mountain(GuiGraphicsExtractor g,int x,int y,int direction,int color){
        line(g,x-10,y+15,x,y-4,color);line(g,x,y-4,x+12,y+15,color);
        line(g,x,y-4,x+1,y+14,0xFFCDB88F);
        line(g,x-10,y+15,x+12,y+15,color);
        line(g,x+direction*7,y+15,x+direction*17,y+2,color);
        line(g,x+direction*17,y+2,x+direction*24,y+15,color);
    }
    private static void trees(GuiGraphicsExtractor g,int x,int y){
        for(int i=0;i<3;i++){
            int xx=x+(i-1)*6, yy=y+(i%2)*6;
            g.fill(xx,yy,xx+1,yy+11,0xFFB2AC88);
            g.fill(xx-2,yy+2,xx+3,yy+4,0xFFC4BD98);
            g.fill(xx-3,yy+5,xx+4,yy+7,0xFFC4BD98);
        }
    }
    private static void ruin(GuiGraphicsExtractor g,int x,int y,int dir){
        g.fill(x-9,y+1,x+10,y+3,0xFFC2AD86);
        g.fill(x-8,y+3,x-5,y+15,0xFFC2AD86);g.fill(x+6,y+3,x+9,y+15,0xFFC2AD86);
        g.fill(x-5,y+5,x+6,y+15,0xFFD2BC92);
        g.fill(x-11,y+15,x+12,y+17,0xFFC8AE80);
        for(int k=0;k<3;k++)g.fill(x-3+k*4,y+4,x-2+k*4,y+11,0xFFCCB68B);
    }
    private TrialsAtlasBackdrop() {}
}
