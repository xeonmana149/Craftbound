package craftbound.client.match;

import com.google.gson.*;
import craftbound.network.*;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

/** The actual match screen, embedded read-only. Server consent controls which hand data exists. */
public final class SpectatorScreen extends Screen {
    private final BlockPos table;
    private JsonObject board;
    private MatchScreen game;
    private float renderScale = 1F;
    private int view=0, renderedSide=-1, visibleMask=-1;
    private boolean followTurn;
    private String notice="";
    private final Button[] requests=new Button[2];
    // Slim bars so the watched player's hand stays visible: top buttons, then one bottom row for notice + requests.
    private static final int TOP=22,BOTTOM=18,BUTTON_H=16,REQUEST_H=14;
    private int requestW(){return Math.max(70,Math.min(150,width/4));}
    public SpectatorScreen(SpectatorStatePayload p){super(Component.literal("Spectating"));table=p.table();board=JsonParser.parseString(p.json()).getAsJsonObject();}
    public BlockPos table(){return table;}
    public void update(SpectatorStatePayload p){board=JsonParser.parseString(p.json()).getAsJsonObject();if(!p.notice().isEmpty())notice=p.notice();refresh(false);buttons();}
    @Override protected void init(){
        int w=Math.max(50,Math.min(130,(width-88)/3));
        for(int i=0;i<2;i++){
            final int side=i;String name=board.getAsJsonArray("players").get(i).getAsJsonObject().get("name").getAsString();
            addRenderableWidget(Button.builder(Component.literal(font.plainSubstrByWidth(name,w-10)),b->{view=side;followTurn=false;refresh(true);}).bounds(6+i*(w+4),3,w,BUTTON_H).build());
            requests[i]=addRenderableWidget(Button.builder(Component.empty(),b->{ClientPlayNetworking.send(new SpectatorActionPayload(table,SpectatorActionPayload.REQUEST,side,""));notice="Request sent. The player can allow or deny it.";}).bounds(width-6-(2-i)*(requestW()+4)+4,height-BOTTOM+2,requestW(),REQUEST_H).build());
        }
        addRenderableWidget(Button.builder(Component.literal("Follow turn"),b->{followTurn=true;refresh(true);}).bounds(6+2*(w+4),3,w,BUTTON_H).build());
        addRenderableWidget(Button.builder(Component.literal("Done"),b->onClose()).bounds(width-60,3,54,BUTTON_H).build());
        refresh(true);buttons();
    }
    private void buttons(){
        for(int i=0;i<2;i++)if(requests[i]!=null){var p=board.getAsJsonArray("players").get(i).getAsJsonObject();
            boolean visible=p.has("handVisible")&&p.get("handVisible").getAsBoolean(),pending=p.has("handPending")&&p.get("handPending").getAsBoolean();
            String name=p.get("name").getAsString();String text=visible?name+": hand shared":pending?name+": awaiting permission":"Request "+name+"'s hand";
            requests[i].setMessage(Component.literal(font.plainSubstrByWidth(text,requests[i].getWidth()-10)));
            requests[i].active=p.has("canRequestHand")&&p.get("canRequestHand").getAsBoolean()&&!pending;
        }
    }
    private void refresh(boolean force){
        if(width<=0)return;int side=followTurn?board.get("current").getAsInt():view,mask=0;
        for(int i=0;i<2;i++)if(board.getAsJsonArray("players").get(i).getAsJsonObject().has("hand"))mask|=1<<i;
        JsonObject state=board.deepCopy();state.addProperty("you",side);state.addProperty("spectator",true);state.addProperty("myTurn",board.get("current").getAsInt()==side);
        if(!state.has("phase"))state.addProperty("phase","PLAYING");if(!state.has("winner"))state.addProperty("winner",-1);
        if(!state.has("events"))state.add("events",new JsonArray());if(!state.has("log"))state.add("log",new JsonArray());
        for(var element:state.getAsJsonArray("players")){var p=element.getAsJsonObject();
            if(!p.has("ai"))p.addProperty("ai",false);if(!p.has("uuid"))p.addProperty("uuid","");if(!p.has("deckName"))p.addProperty("deckName","");if(!p.has("mulliganDone"))p.addProperty("mulliganDone",true);if(!p.has("heroReady"))p.addProperty("heroReady",false);
        }
        // A perspective/permission change discards queued private visuals immediately, including revocations.
        if(force||game==null||side!=renderedSide||mask!=visibleMask){
            state.add("events",new JsonArray());game=new MatchScreen(state.toString());
            // Preserve the full match layout at small GUI resolutions instead of compressing its rows.
            int availableHeight=Math.max(120,height-TOP-BOTTOM);
            renderScale=Math.min(1F,availableHeight/480F);
            game.init(Math.round(width/renderScale),Math.round(availableHeight/renderScale));
            renderedSide=side;visibleMask=mask;
        }
        else game.update(state.toString());
    }
    @Override public void extractRenderState(GuiGraphicsExtractor g,int mx,int my,float tick){
        g.fill(0,0,width,height,0xFF151B19);
        if(game!=null){g.pose().pushMatrix();g.pose().translate(0,TOP);g.pose().scale(renderScale,renderScale);game.extractRenderState(g,Math.round(mx/renderScale),Math.round((my-TOP)/renderScale),tick);g.pose().popMatrix();}
        g.nextStratum();g.fill(0,0,width,TOP,0xFF151B19);g.fill(0,height-BOTTOM,width,height,0xFF151B19);
        String message=notice.isEmpty()?"Watching "+board.getAsJsonArray("players").get(renderedSide<0?0:renderedSide).getAsJsonObject().get("name").getAsString()+" · Read-only · Hands require permission":notice;
        int noticeWidth=Math.max(40,width-12-2*(requestW()+4)-6);
        g.text(font,font.plainSubstrByWidth(message,noticeWidth),6,height-BOTTOM+5,0xFFE2D5B9,false);
        super.extractRenderState(g,mx,my,tick);
    }
    @Override public boolean isPauseScreen(){return false;}
    @Override public void removed(){if(ClientPlayNetworking.canSend(SpectatorActionPayload.TYPE))ClientPlayNetworking.send(new SpectatorActionPayload(table,SpectatorActionPayload.CLOSE,0,""));}
}
