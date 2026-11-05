package craftbound.client.match;

import java.util.List;

/** A fitted, stationary mat. The local coordinate system is shared by art, hitboxes and attack paths. */
public final class MultiplayerBoardLayout {
    public static final int CARD_WIDTH=48, CARD_HEIGHT=64;
    private static final int EDGE=6, SEAM=6, HERO_BAND=72, FIELD_HEIGHT=496;
    public record Point(double x,double y) {}
    public record Rect(double x,double y,double w,double h) {
        public boolean contains(double px,double py){return px>=x && py>=y && px<x+w && py<y+h;}
        public double cx(){return x+w/2;}
        public double cy(){return y+h/2;}
    }
    public record Seat(int owner, Rect bounds, boolean near) {
        /** v starts at the center seam and increases toward the owner's hand. Text and art always face the viewer. */
        public Point point(double offset,double v) {
            return new Point(bounds.cx()+offset,near?bounds.y()+v:bounds.y()+bounds.h()-v);
        }
        public Point hero(){return point(0,bounds.h()-HERO_BAND/2);}
        public Rect nameplate(boolean teams, int textWidth) {
            double end = hero().x() - (teams ? 82 : 136);
            double width = Math.min(end - bounds.x() - 8, Math.clamp(textWidth + 18, 84, teams ? 126 : 148));
            return new Rect(end - width, hero().y() - 15, width, 30);
        }
        public Rect row(boolean block) {
            double h=(bounds.h()-HERO_BAND)/2;
            Point center=point(0,(block?1.5:.5)*h);
            return new Rect(bounds.x()+2,center.y()-h/2+1,bounds.w()-4,h-2);
        }
        public Point unit(boolean block,int count,int index) {
            Rect row=row(block);
            double step=Math.min(72,(row.w()-70)/7.0);
            return new Point(row.cx()+(index-(count-1)*.5)*step,row.cy());
        }
    }
    public static final class Mat {
        private final Rect viewport;
        private final double scale,width,height;
        public Mat(Rect viewport, boolean teams) {
            this.viewport=viewport;
            // Each half-table reserves space for seven non-overlapping targets plus the row count.
            scale=Math.min(viewport.h()/FIELD_HEIGHT,viewport.w()/(teams?1040:520));
            width=viewport.w()/scale;height=viewport.h()/scale;
        }
        public Rect viewport(){return viewport;}
        public double scale(){return scale;}
        public double width(){return width;}
        public double height(){return height;}
        public Point screen(double x,double y){return new Point(viewport.x()+x*scale,viewport.y()+y*scale);}
        public Point board(double x,double y){return new Point((x-viewport.x())/scale,(y-viewport.y())/scale);}
        public Rect screen(Rect r){Point p=screen(r.x(),r.y());return new Rect(p.x(),p.y(),r.w()*scale,r.h()*scale);}
        /** One badge per team, on the seam between partners, aligned with both portraits. */
        public Rect teamBadge(boolean near) {
            double centerY = EDGE + HERO_BAND / 2.0;
            return new Rect(width / 2 - 31, (near ? height - centerY : centerY) - 29, 62, 58);
        }
        public Rect resources(Seat seat, boolean teams) {
            double left = seat.hero().x() + 86;
            double right = seat.bounds().x() + seat.bounds().w() - 6;
            if (teams && seat.bounds().cx() < width / 2) right = teamBadge(seat.near()).x() - 6;
            return screen(new Rect(left, seat.hero().y() - 30, right - left, 60));
        }
        public List<Seat> seats(boolean teams,int home,int rival) {
            double half=height/2-SEAM-EDGE;
            double left=EDGE,w=width-2*EDGE;
            if(!teams)return List.of(new Seat(home,new Rect(left,height/2+SEAM,w,half),true),
                    new Seat(rival,new Rect(left,EDGE,w,half),false));
            double col=(w-SEAM)/2,right=left+col+SEAM;
            int across=home^1;
            return List.of(new Seat(home,new Rect(left,height/2+SEAM,col,half),true),
                    new Seat(home^2,new Rect(right,height/2+SEAM,col,half),true),
                    new Seat(across,new Rect(left,EDGE,col,half),false),
                    new Seat(across^2,new Rect(right,EDGE,col,half),false));
        }
    }
    /** Compact, centered Commander selectors. Picking and animation use these same bounds. */
    public static Rect rivalTab(int width, int home, int side) {
        if (side == home) return null;
        int gap = 4, tabWidth = Math.max(1, Math.min(158, (width - 18) * 2 / 7));
        int slot = Math.floorMod(side - home, 4) - 1;
        return new Rect((width - 3 * tabWidth - 2 * gap) / 2 + slot * (tabWidth + gap), 18, tabWidth, 24);
    }
    /** Center-pivoted card pose, used for both drawing and inverse-rotated mouse picking. */
    public record HandSlot(double cx,double cy,double w,double h,double angle) {
        public Rect bounds(){
            double c=Math.abs(Math.cos(angle)),s=Math.abs(Math.sin(angle));
            double width=w*c+h*s,height=h*c+w*s;
            return new Rect(cx-width/2,cy-height/2,width,height);
        }
        public boolean contains(double x,double y){
            double dx=x-cx,dy=y-cy,c=Math.cos(angle),s=Math.sin(angle);
            return Math.abs(dx*c+dy*s)<=w/2 && Math.abs(dy*c-dx*s)<=h/2;
        }
        public HandSlot raised(int top){
            return new HandSlot(cx,top+h/2,w,h,0);
        }
    }
    public static HandSlot handSlot(int width,int top,int height,int count,int index) {
        int inset=Math.min(104,width/4),space=width-2*inset;
        double w=Math.min(space,height*5.0/7),h=w*7/5;
        if(count<=1)return new HandSlot(width/2.0,top+h/2,w,h,0);
        double maxAngle=Math.toRadians(Math.min(26,6.5*(count-1)));
        double edgeWidth=Math.max(w,w*Math.cos(maxAngle)+h*Math.sin(maxAngle));
        double span=Math.min((space-edgeWidth)/2,w*(count-1)*.49);
        // A very wide window must not push the end cards' faces below the bottom strip.
        double outer=Math.min(maxAngle,2*Math.atan2(h*.38,span));
        double apart=2*outer/(count-1),radius=span/Math.sin(outer);
        HandFan fan=HandFan.at(index,count,radius*apart,Math.toDegrees(apart));
        double halfHeight=(h*Math.cos(fan.angle())+w*Math.abs(Math.sin(fan.angle())))/2;
        return new HandSlot(width/2.0+fan.x(),top+Math.max(halfHeight,h/2+fan.drop()),w,h,fan.angle());
    }
    private MultiplayerBoardLayout(){}
}
