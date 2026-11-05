package craftbound.client.match;
import com.google.gson.*;
import craftbound.card.*;
import craftbound.client.card.*;
import craftbound.game.HeroPowers;
import net.minecraft.client.gui.Font;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.*;
import java.util.*;

/** A real tabletop: each owner's pieces and dashboard face their chair. */
public final class TableBoardFace {
    public static final int WIDTH=640, HEIGHT=640;
    public record CardSpot(int side,int index,boolean hand,float x,float y,float w,float h) {
        public boolean contains(float px,float py){return px>=x&&py>=y&&px<x+w&&py<y+h;}
    }
    private static final int MOB_Y=352, MOB_PITCH=96;
    public static float rowScale(int count){return Math.min(1.1F,(WIDTH-40F)/(Math.max(1,count)*MOB_PITCH));}
    private static CardSpot boardSpot(JsonArray board, int index) {
        boolean block = flag(board.get(index).getAsJsonObject(), "block");
        int before = 0, count = 0;
        boolean split = false;
        for (int i = 0; i < board.size(); i++) {
            boolean isBlock = flag(board.get(i).getAsJsonObject(), "block");
            split |= isBlock;
            if (isBlock == block) {
                count++;
                if (i < index) before++;
            }
        }
        float scale = Math.min(rowScale(count), split ? .55F : 1.1F);
        float pitch = MOB_PITCH * scale;
        return new CardSpot(0, index, false, WIDTH/2F - count*pitch/2 + before*pitch + 12*scale,
                block ? 407 : MOB_Y, 72*scale, 88*scale);
    }
    public static List<CardSpot> spots(JsonObject board) {
        var result=new ArrayList<CardSpot>();
        var players=board.getAsJsonArray("players");
        if(flag(board,"commander") && players.size()==4) {
            for(int seat=0;seat<4;seat++) {
                var units=players.get(seat).getAsJsonObject().getAsJsonArray("board");
                for(int i=0;i<units.size();i++)result.add(commanderSpot(seat,commanderCell(seat,units,i)));
            }
            return result;
        }
        if (flag(board,"teamDuos") && players.size()==4) {
            for (int seat=0; seat<4; seat++) {
                var units=players.get(seat).getAsJsonObject().getAsJsonArray("board");
                for(int i=0;i<units.size();i++) {
                    var cell=teamCell(seat,units,i);
                    result.add(seat%2==0 ? cell : new CardSpot(seat,i,false,WIDTH-cell.x()-cell.w(),HEIGHT-cell.y()-cell.h(),cell.w(),cell.h()));
                }
            }
            return result;
        }
        for(int side=0;side<2;side++){
            var p=players.get(side).getAsJsonObject();int count=p.getAsJsonArray("board").size();
            for(int i=0;i<count;i++){
                CardSpot s = boardSpot(p.getAsJsonArray("board"), i);
                result.add(spot(side,i,false,s.x(),s.y(),s.w(),s.h()));
            }
            if(p.has("hand"))for(int i=0;i<p.getAsJsonArray("hand").size();i++){
                int n=p.getAsJsonArray("hand").size();
                result.add(spot(side,i,true,WIDTH/2F-n*36/2F+i*36,589,30,42));
            }
        }
        return result;
    }
    private static CardSpot spot(int side,int index,boolean hand,float x,float y,float w,float h) {
        return side==0?new CardSpot(side,index,hand,x,y,w,h):new CardSpot(side,index,hand,WIDTH-x-w,HEIGHT-y-h,w,h);
    }
    public static void draw(CardCanvas g,Font font,JsonObject board,long now) {
        var design = craftbound.board.BoardDesign.fromId(board.has("boardDesign") ? board.get("boardDesign").getAsString() : "");
        g.fill(0,0,WIDTH,HEIGHT,design.brick);
        g.outline(1,1,WIDTH-2,HEIGHT-2,design.accent);
        g.fill(6,6,WIDTH-6,HEIGHT-6,design.alternateBrick);
        // A quiet background lets the pieces carry the detail.
        for(int row=0;row<20;row++)for(int col=0;col<11;col++){
            int x=8+col*64-(row%2)*32,y=8+row*32;
            int left=Math.max(8,x),right=Math.min(WIDTH-8,x+61),bottom=Math.min(HEIGHT-8,y+29);
            if(right>left&&bottom>y)g.fill(left,y,right,bottom,design.mat);
        }
        BoardTrim.draw(g,design,8,8,WIDTH-16,HEIGHT-16);
        g.fill(12,318,WIDTH-12,322,design.accent);
        boolean paused=flag(board,"paused");
        var players=board.getAsJsonArray("players");
        if(flag(board,"commander") && players.size()==4) {
            drawCommanderTable(g,font,board,players,now);
            return;
        }
        if (flag(board,"teamDuos") && players.size()==4) {
            drawTeamTable(g,font,board,players,now);
            return;
        }
        for(int side=0;side<2;side++){
            g.pose().pushMatrix();
            if(side==1){g.pose().translate(WIDTH,HEIGHT);g.pose().rotate((float)Math.PI);}
            drawSide(g,font,players.get(side).getAsJsonObject(),number(board,"current")==side&&!paused,now);
            g.pose().popMatrix();
        }
        String label=paused?"PAUSED":flag(board,"over")?"FINISHED":"TURN "+number(board,"turn");
        // Two fixed labels, one facing each chair. Changing turns never flips either label.
        for(int side=0;side<2;side++){
            g.pose().pushMatrix();
            if(side==1){g.pose().translate(WIDTH,HEIGHT);g.pose().rotate((float)Math.PI);}
            label(g,font,label,320,326,1.2F,0xFFFFD886);
            g.pose().popMatrix();
        }
        if(!paused && board.has("clockTicks"))
            TurnFuse.draw(g,font,22,232,320,number(board,"clockTicks")/20F,now);
    }
    private static CardSpot commanderCell(int seat,JsonArray units,int index) {
        boolean block=flag(units.get(index).getAsJsonObject(),"block");
        int count=0,before=0;
        for(int i=0;i<units.size();i++)if(flag(units.get(i).getAsJsonObject(),"block")==block){count++;if(i<index)before++;}
        float pitch=Math.min(80,280f/Math.max(1,count)), scale=Math.min(.75f,(pitch-6)/72f);
        return new CardSpot(seat,index,false,160+(before+.5f-count/2f)*pitch-36*scale,block?152:70,72*scale,88*scale);
    }
    private static CardSpot commanderSpot(int seat,CardSpot local) {
        float x=local.x(),y=local.y(),w=local.w(),h=local.h();
        return switch(seat) {
            case 0 -> new CardSpot(seat,local.index(),false,x,320+y,w,h);
            case 1 -> new CardSpot(seat,local.index(),false,320+y,640-x-w,h,w);
            case 2 -> new CardSpot(seat,local.index(),false,640-x-w,320-y-h,w,h);
            default -> new CardSpot(seat,local.index(),false,320-y-h,x,h,w);
        };
    }
    private static void drawCommanderTable(CardCanvas g,Font font,JsonObject board,JsonArray players,long now) {
        for(int seat=0;seat<4;seat++) {
            g.pose().pushMatrix();
            switch(seat) {
                case 0 -> g.pose().translate(0,320);
                case 1 -> {g.pose().translate(320,640);g.pose().rotate(-(float)Math.PI/2);}
                case 2 -> {g.pose().translate(640,320);g.pose().rotate((float)Math.PI);}
                case 3 -> {g.pose().translate(320,0);g.pose().rotate((float)Math.PI/2);}
            }
            var owner=players.get(seat).getAsJsonObject();
            boolean turn=number(board,"current")==seat,alive=!owner.has("alive")||flag(owner,"alive");
            int accent=turn?0xFFFFD886:0xFF96724F;
            g.outline(6,6,308,308,accent);
            label(g,font,font.plainSubstrByWidth(owner.get("name").getAsString(),220),160,20,1.3f,0xFFFFE8C5);
            label(g,font,!alive?"ELIMINATED":turn?"YOUR TURN":"COMMANDER",160,39,1,accent);
            g.fill(18,137,302,139,0xFF635F53);
            var units=owner.getAsJsonArray("board");
            for(int i=0;i<units.size();i++) {
                var cell=commanderCell(seat,units,i);
                g.pose().pushMatrix();g.pose().translate(cell.x(),cell.y());g.pose().scale(cell.w()/72f);
                drawMob(g,font,units.get(i).getAsJsonObject(),0,0,now);
                g.pose().popMatrix();
            }
            g.fill(10,230,310,310,0xFF38291F);g.fill(10,230,310,233,accent);
            label(g,font,number(owner,"health")+" HP   "+number(owner,"xp")+"/"+number(owner,"maxXp")+" XP",160,248,1.5f,0xFFFFE7BC);
            label(g,font,"Hand "+number(owner,"handCount")+"    Deck "+number(owner,"deck"),160,278,1.25f,0xFFE5D4B5);
            g.pose().popMatrix();
        }
    }

    // Team-mates sit beside one another. Left/right reverses naturally across the table.
    private static int teamLeft(int seat) { return seat == 0 || seat == 3 ? 12 : 324; }

    private static CardSpot teamCell(int seat, JsonArray units, int i) {
        boolean block = flag(units.get(i).getAsJsonObject(),"block");
        int count=0, index=0;
        for (int j=0;j<units.size();j++) if(flag(units.get(j).getAsJsonObject(),"block")==block) {
            if(j<i) index++;
            count++;
        }
        float pitch=Math.min(68f,296f/count), scale=Math.min(.73f,pitch/90f);
        float x=teamLeft(seat)+152f+(index+.5f-count/2f)*pitch;
        return new CardSpot(seat,i,false,x-36*scale,(block?466:376)-44*scale,72*scale,88*scale);
    }

    private static void drawTeamTable(CardCanvas g,Font font,JsonObject board,JsonArray players,long now) {
        for(int team=0;team<2;team++){
            g.pose().pushMatrix();
            if(team==1){g.pose().translate(WIDTH,HEIGHT);g.pose().rotate((float)Math.PI);}
            int ink=team==0?0xFFFFB38F:0xFFA2DEE6;
            g.fill(318,342,322,625,0xFF554D40);
            for(int seat=team;seat<4;seat+=2) {
                var owner=players.get(seat).getAsJsonObject();
                int x=teamLeft(seat),center=x+152;
                g.fill(x,424,x+304,426,0xFF635F53);
                var units=owner.getAsJsonArray("board");
                for(int i=0;i<units.size();i++){
                    var cell=teamCell(seat,units,i);
                    g.pose().pushMatrix();g.pose().translate(cell.x(),cell.y());g.pose().scale(cell.w()/72F);
                    drawMob(g,font,units.get(i).getAsJsonObject(),0,0,now);
                    g.pose().popMatrix();
                }
                g.fill(x,509,x+304,626,0xFF38291F);
                g.fill(x,509,x+304,513,ink);
                label(g,font,font.plainSubstrByWidth(owner.get("name").getAsString(),185),center,523,1.5f,0xFFFFECD1);
                var power=HeroPowers.Power.valueOf(owner.getAsJsonObject("power").get("name").getAsString());
                icon(g,new ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.getValue(power.icon())),x+34,572,35);
                label(g,font,number(owner,"xp")+"/"+number(owner,"maxXp")+" XP",center,553,1.65f,0xFFA6E783);
                label(g,font,"Hand "+number(owner,"handCount")+"   Deck "+number(owner,"deck"),center,585,1.45f,0xFFE5D4B5);
                if(owner.has("tool") && owner.get("tool").isJsonObject()){
                    var id=Identifier.tryParse(owner.getAsJsonObject("tool").get("id").getAsString());
                    if(id!=null && Cards.get(id)!=null)icon(g,CardRenderer.placeholderArt(id,Cards.get(id)),x+267,572,35);
                }
            }
            String name=team==0?"Ember":"Tide";
            int health=number(players.get(team).getAsJsonObject(),"health");
            String status=team==number(board,"current")%2?"  /  Your turn":"";
            label(g,font,name+"  "+health+" HP"+status,320,326,1.3f,ink);
            g.pose().popMatrix();
        }
    }

    private static void drawSide(CardCanvas g,Font font,JsonObject p,boolean current,long now) {
        var mobs=p.getAsJsonArray("board");
        for(int i=0;i<mobs.size();i++){
            CardSpot spot = boardSpot(mobs, i);
            g.pose().pushMatrix();g.pose().translate(spot.x(),spot.y());g.pose().scale(spot.w()/72F);
            drawMob(g,font,mobs.get(i).getAsJsonObject(),0,0,now);
            g.pose().popMatrix();
        }
        g.fill(12,460,WIDTH-12,584,0xFF38291F);
        g.fill(12,460,WIDTH-12,462,current?0xFFEBC36D:0xFF745638);
        label(g,font,font.plainSubstrByWidth(p.get("name").getAsString(),190),320,466,1.4F,0xFFFFE8C5);
        MatchArt.octagon(g,279,486,82,current?0xFFE8BE62:0xFF826644);
        MatchArt.octagon(g,283,490,74,CardClass.valueOf(p.get("cls").getAsString()).frameColor());
        // Class emblem rather than an untextured generic player skull.
        var power=p.has("power")?HeroPowers.Power.valueOf(p.getAsJsonObject("power").get("name").getAsString()):HeroPowers.Power.NONE;
        icon(g,new ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.getValue(power.icon())),320,522,48);
        if(flag(p,"frozen"))g.outline(282,489,76,76,0xFF9DEFFF);
        MatchArt.heart(g,font,304,486,32,number(p,"health"),0xFFFFFFFF);
        if(current && p.has("heroAttack") && number(p,"heroAttack")>0) MatchArt.attack(g,font,270,540,32,number(p,"heroAttack"),0xFFFFFFFF);
        if(p.has("tool")){
            var t=p.getAsJsonObject("tool");var id=Identifier.parse(t.get("id").getAsString());
            g.fill(179,494,237,552,0xFF211D1A);g.outline(179,494,58,58,flag(t,"ench")?0xFFCB88FF:0xFFAA8954);
            icon(g,CardRenderer.placeholderArt(id,Cards.get(id)),208,519,42);
            MatchArt.attack(g,font,173,536,27,number(t,"atk"),0xFFFFFFFF,flag(t,"ranged"));
            MatchArt.uses(g,font,220,536,25,number(t,"uses"));
            label(g,font,"TOOL",208,568,1,0xFFCFC4B1);
        }
        boolean used=p.has("power")&&flag(p.getAsJsonObject("power"),"used");
        MatchArt.octagon(g,400,497,50,used?0xFF514B43:0xFFA37B38);
        icon(g,new ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.getValue(power.icon())),425,521,34);
        label(g,font,power.displayName().getString(),425,551,1.1F,used?0xFF999387:0xFFFFD98A);
        g.fill(536,493,587,554,0xFF201710);
        var sleeve=craftbound.sleeve.Sleeves.get(Identifier.tryParse(p.get("sleeve").getAsString()));
        g.fill(540,489,591,550,sleeve.base());g.outline(540,489,51,61,sleeve.border());
        label(g,font,Integer.toString(number(p,"deck")),565,513,2,0xFFFFFFFF);
        label(g,font,"DECK",565,559,1.1F,0xFFCFC4B1);
        label(g,font,number(p,"xp")+"/"+number(p,"maxXp")+" XP",82,514,1.6F,0xFF9CEF64);
        for(int i=0;i<10;i++)MatchArt.orb(g,36+i*10,539,7,i<number(p,"xp")?MatchArt.Orb.FULL:MatchArt.Orb.SPENT);
        int hand=number(p,"handCount");
        for(int i=0;i<hand;i++){
            int x=WIDTH/2-hand*18+i*36;
            if(p.has("hand")){
                var card=p.getAsJsonArray("hand").get(i).getAsJsonObject();
                CardRenderer.drawFace(g,font,Identifier.parse(card.get("id").getAsString()),flag(card,"ench"),x,589,.25F,-1,-1);
            }else{
                g.fill(x,589,x+30,631,sleeve.base());g.outline(x,589,30,42,sleeve.border());
                g.fill(x+11,602,x+19,618,sleeve.emblem());g.fill(x+8,606,x+22,614,sleeve.emblem());
            }
        }
        label(g,font,Integer.toString(hand),565,604,1.7F,0xFFEDE1CC);
    }
    private static void drawMob(CardCanvas g,Font font,JsonObject m,int x,int y,long now) {
        Identifier id=Identifier.parse(m.get("id").getAsString());CardDefinition card=Cards.get(id);
        if(card==null)return;
        final int w=72,h=88;
        g.pose().pushMatrix();g.pose().translate(x,y);
        if(flag(m,"shield"))MatchArt.shieldFrame(g,w,h);
        g.fill(-1,-1,w+1,h+1,0xFF000000);
        g.fill(0,0,w,h,card.rarity()==CardRarity.LEGENDARY?0xFFE8B84A:flag(m,"ench")?0xFFB57AE0:card.cardClass().frameColor());
        CardRenderer.drawBoardArt(g,id,card,3,3,w-6,h-6);
        if(flag(m,"ench"))g.shimmer(2,2,w-4,h-4,(now%2400)/2400F*(w+h+40)-20,8,90);
        if(flag(m,"frozen"))MatchArt.frozenCoating(g,w,h,now,number(m,"mid"));
        if(flag(m,"withered"))MatchArt.witherDecay(g,w,h,now,number(m,"mid"));
        if(flag(m,"invulnerable"))MatchArt.invulnerableBarrier(g,w,h,now);
        if(flag(m,"totem"))MatchArt.totemBlessing(g,w,h,now,number(m,"mid"));
        if(flag(m,"shield"))MatchArt.shieldBadge(g,w/2-5,-8);
        if (!flag(m,"block")) MatchArt.attack(g,font,-6,h-18,30,number(m,"atk"),0xFFFFFFFF,flag(m,"ranged"));
        MatchArt.heart(g,font,w-24,h-18,30,number(m,"hp"),number(m,"hp")<number(m,"maxHp")?0xFFFF8888:0xFFFFFFFF);
        g.pose().popMatrix();
    }

    public static void icon(CardCanvas g,ItemStack item,int x,int y,int size) {
        g.pose().pushMatrix();g.pose().translate(x,y);g.pose().scale(size/16F,size/16F);g.item(item,-8,-8);g.pose().popMatrix();
    }
    private static void label(CardCanvas g,Font f,String text,int x,int y,float scale,int color){
        g.pose().pushMatrix();g.pose().translate(x,y);g.pose().scale(scale);g.centeredText(f,text,0,0,color);g.pose().popMatrix();
    }
    private static int number(JsonObject o,String key){return o.get(key).getAsInt();}
    private static boolean flag(JsonObject o,String key){return o.has(key)&&o.get(key).getAsBoolean();}
    private TableBoardFace(){}
}
