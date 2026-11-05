package craftbound.client.match;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import craftbound.card.Cards;
import craftbound.game.Match;
import java.util.ArrayList;
import java.util.List;
import static craftbound.client.match.MultiplayerBoardLayout.*;

/** Full rows must fit at every GUI scale. Swapping Commander rivals must never move the local board. */
public final class MultiplayerBoardGeometryChecks {
    public static void run(){
        checkCommanderAttack();
        for(int[] screen:List.of(new int[]{320,240},new int[]{640,360},new int[]{1280,720},new int[]{960,320})){
            int width=screen[0],height=screen[1],hand=Math.clamp((int)(height*.18),37,84);
            for(boolean teams:new boolean[]{false,true})for(int viewer=0;viewer<4;viewer++){
                int top=teams?21:45;
                Mat mat=new Mat(new Rect(4,top,width-8,height-hand-22-top),teams);
                Rect home=null;
                for(int rival=0;rival<4;rival++){
                    if(rival==viewer)continue;
                    var seats=mat.seats(teams,viewer,rival);
                    require(seats.getFirst().owner()==viewer && seats.getFirst().near(),"Local seat must stay on the near side");
                    if(home!=null)require(home.equals(seats.getFirst().bounds()),"Viewing another rival moved home");
                    home=seats.getFirst().bounds();
                    if(teams){
                        require(seats.size()==4&&seats.get(1).owner()==(viewer^2)&&seats.get(1).near(),"Partner must be beside you");
                        require(seats.get(2).owner()==(viewer^1)&&seats.get(3).owner()==(viewer^3),"Opposing seats must align across the table");
                    }else require(seats.size()==2&&seats.get(1).owner()==rival&&!seats.get(1).near(),"Only the chosen rival is displayed");
                    if (teams) checkTeamHud(mat, seats, width, height, hand);
                    else checkTabs(width, viewer);
                    var targets=new ArrayList<Rect>();
                    for(Seat seat:seats)for(boolean block:new boolean[]{false,true}){
                        Rect row=seat.row(block);int count=block?3:7;
                        for(int i=0;i<count;i++){
                            Point c=seat.unit(block,count,i);
                            Rect target=new Rect(c.x()-CARD_WIDTH/2-4,c.y()-CARD_HEIGHT/2-4,CARD_WIDTH+8,CARD_HEIGHT+12);
                            require(contains(row,target),"Full row clips or spills an interactive card into another row");
                            Rect hit=mat.screen(target);
                            require(contains(mat.viewport(),hit),"Card target overlaps the HUD");
                            for(Rect other:targets)require(!overlap(hit,other),"Full-board targets overlap");
                            targets.add(hit);
                            Point cursor=mat.board(hit.cx(),hit.cy());
                            require(target.contains(cursor.x(),cursor.y()),"A card center does not hit its displayed card");
                        }
                    }
                    require(targets.size()==(teams?40:20),"Wrong number of full-field cards");
                }
                checkFan(width,height,hand,mat.viewport());
            }
        }
    }
    private static void checkCommanderAttack() {
        JsonObject before = new JsonObject();
        var players = new JsonArray();
        before.add("players", players);
        for (int s = 0; s < 4; s++) {
            var player = new JsonObject();
            player.addProperty("name", "Player " + s);
            player.add("board", new JsonArray());
            players.add(player);
        }
        var id = Cards.ids().stream().filter(k -> Cards.get(k).isMob()).findFirst().orElseThrow();
        JsonObject unit = new JsonObject();
        unit.addProperty("mid", 9); unit.addProperty("id", id.toString()); unit.addProperty("block", false);
        var board = players.get(2).getAsJsonObject().getAsJsonArray("board");
        board.add(unit);
        JsonObject path = new JsonObject();
        path.addProperty("from", 2); path.addProperty("source", 9); path.addProperty("to", 3); path.addProperty("target", Match.HERO);
        var hit = BoardAttack.read(before,path);
        board.remove(0);
        String subject = Cards.name(id).getString();
        require(hit.caption().equals("Player 2’s " + subject + " attacked Player 3’s hero"),"Off-screen attack loses its public names");
        var mat = new Mat(new Rect(4,45,632,228),false);
        var seats = mat.seats(false,0,1);
        Point from = hit.from().point(seats,mat,0,640), to = hit.to().point(seats,mat,0,640);
        require(rivalTab(640,0,2).contains(from.x(),from.y()) && rivalTab(640,0,3).contains(to.x(),to.y()),
                "Attack between two hidden players must connect their respective tabs");
        seats = mat.seats(false,0,2);
        var anchor = seats.get(1).unit(false,1,0);
        require(hit.from().point(seats,mat,0,640).equals(mat.screen(anchor.x(),anchor.y())), "Focused attacker must anchor to its original card");
        require(hit.to().point(seats,mat,0,640).equals(to),"Changing focus must not move the hidden target’s tab");
    }
    private static void checkTeamHud(Mat mat, List<Seat> seats, int width, int height, int hand) {
        Rect far = mat.screen(mat.teamBadge(false)), near = mat.screen(mat.teamBadge(true));
        require(Math.abs(near.cx()-width/2.0)<.01 && Math.abs(near.cx()-far.cx())<.01, "Team hearts must share the center seam");
        require(Math.abs((near.cy()+far.cy())/2-mat.viewport().cy())<.01, "Heart positions must mirror across the mat");
        require(contains(mat.viewport(),near) && contains(mat.viewport(),far), "Shared Health leaves the mat");
        for (Seat seat : seats) {
            Rect badge = seat.near() ? near : far;
            require(!overlap(mat.screen(seat.nameplate(true, 600)), badge), "Long player name overlaps the team heart");
            require(!overlap(mat.resources(seat,true), badge), "XP panel overlaps the team heart");
            for (boolean block : new boolean[]{false,true})
                require(!overlap(mat.screen(seat.row(block)),badge), "Board row overlaps shared Health");
        }
        for (int i = 0; i < 10; i++) {
            HandSlot card = handSlot(width,height-19-hand,hand,10,i);
            require(!overlap(near,card.bounds()) && !overlap(near,card.raised(height-19-hand).bounds()), "Hand hides shared Health");
        }
    }
    private static void checkTabs(int width, int viewer) {
        Rect first = rivalTab(width, viewer, (viewer+1)%4), middle = rivalTab(width,viewer,(viewer+2)%4);
        Rect last = rivalTab(width,viewer,(viewer+3)%4);
        require(rivalTab(width,viewer,viewer)==null && !overlap(first,middle) && !overlap(middle,last),"Rival tab targets overlap");
        require(Math.abs(middle.cx()-width/2.0)<=1 && first.x()>0 && last.x()+last.w()<width, "Compact rival tabs must fit and stay centered");
    }
    private static void checkFan(int width,int height,int hand,Rect board){
        int top=height-19-hand,bottom=height-14,inset=Math.min(104,width/4);
        for(int count=1;count<=10;count++)for(int i=0;i<count;i++){
            HandSlot slot=handSlot(width,top,hand,count,i),mirror=handSlot(width,top,hand,count,count-1-i);
            Rect box=slot.bounds();
            require(box.x()>=inset-1 && box.x()+box.w()<=width-inset+1,"Turn control or bank overlaps rotated art");
            require(box.y()>=board.y()+board.h() && slot.cy()<bottom,"Fan leaves the dock or hides a card center");
            require(Math.abs(slot.cx()+mirror.cx()-width)<.01 && Math.abs(slot.cy()-mirror.cy())<.01 && Math.abs(slot.angle()+mirror.angle())<.01,"Fan must be symmetric");
            require(slot.contains(slot.cx(),slot.cy()),"Aim and drag origins must be pickable");
            require(slot.raised(top).bounds().y()+slot.h()<=bottom,"Lifted card must fit above the hint strip");
            if(Math.abs(slot.angle())>.001){
                require(!slot.contains(box.x()+.01,box.y()+.01),"A rotated card's empty bounding-box corner was clickable");
                require(slot.cy()+.001>=handSlot(width,top,hand,count,count/2).cy(),"Middle cards must sit highest");
            }
        }
        var single=handSlot(width,top,hand,1,0);
        require(single.angle()==0 && single.cx()==width*.5,"Single card is upright and centered");
    }
    private static boolean contains(Rect a,Rect b){
        return b.x()>=a.x()-.01&&b.y()>=a.y()-.01&&b.x()+b.w()<=a.x()+a.w()+.01&&b.y()+b.h()<=a.y()+a.h()+.01;
    }
    private static boolean overlap(Rect a,Rect b){return a.x()<b.x()+b.w()&&a.x()+a.w()>b.x()&&a.y()<b.y()+b.h()&&a.y()+a.h()>b.y();}
    private static void require(boolean condition,String message){if(!condition)throw new AssertionError(message);}
    private MultiplayerBoardGeometryChecks(){}
}
