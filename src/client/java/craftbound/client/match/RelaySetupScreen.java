package craftbound.client.match;

import craftbound.client.binder.BinderTheme;
import craftbound.client.card.CardRenderer;
import craftbound.network.RelayJoinPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

public final class RelaySetupScreen extends Screen {
    private final Screen parent;
    private final BlockPos table;
    private final int slot;
    private final String deckId, problem;
    private String notice="";
    public RelaySetupScreen(Screen parent,BlockPos table,int slot,String deckId,String problem) {
        super(Component.literal("Team Duos"));
        this.parent=parent;this.table=table;this.slot=slot;this.deckId=deckId;this.problem=problem==null?"":problem;
    }
    void notice(String text) {notice=text;}
    private void join() {
        ClientPlayNetworking.send(new RelayJoinPayload(table,slot,deckId,-1));
        notice="Requesting a seat…";
    }
    @Override protected void init() {
        int w=Math.min(310,width-28),x=(width-w)/2,y=height/2+25;
        addRenderableWidget(new TableBookButton(x,y,w,20,Component.literal("Take my seat"),this::join)).active=problem.isEmpty();
        addRenderableWidget(new TableBookButton(width/2-35,y+26,70,18,Component.literal("Back"),this::onClose));
    }
    @Override public void extractRenderState(GuiGraphicsExtractor g,int mx,int my,float dt) {
        int w=Math.min(width-8,370),h=Math.min(height-8,170),x=(width-w)/2,y=(height-h)/2;
        BinderTheme.cover(g,x,y,w,h);
        line(g,"TEAM DUOS",y+12,w-20,BinderTheme.GOLD);
        line(g,"Two players per team. Share 30 Health.",y+27,w-20,BinderTheme.PAGE);
        line(g,"Team partners play together on the same turn.",y+46,w-20,BinderTheme.PAGE);
        line(g,"Use your own deck, hand, XP, class power and battlefield.",y+58,w-20,BinderTheme.PAGE);
        line(g,"Choose Ready when finished. Both partners end the turn together.",y+70,w-20,BinderTheme.PAGE);
        line(g,problem.isEmpty()?notice.isEmpty()?"Sit beside your partner; the opposite side is the enemy team.":notice:problem,height/2+10,w-18,problem.isEmpty()?BinderTheme.GOLD:0xffffa397);
        super.extractRenderState(g,mx,my,dt);
    }
    private void line(GuiGraphicsExtractor g,String s,int y,int w,int color) {
        CardRenderer.fitText(g,font,Component.literal(s),width/2,y,w,color,true,false);
    }
    @Override public void onClose() {minecraft.gui.setScreen(parent);}
    @Override public boolean isPauseScreen() {return false;}
}