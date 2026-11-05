package craftbound.client.match;

import craftbound.card.Cards;
import craftbound.client.binder.BinderTheme;
import craftbound.client.card.CardRenderer;
import craftbound.deck.DeckContents;
import craftbound.game.*;
import craftbound.item.CardItem;
import craftbound.network.CommanderJoinPayload;
import java.util.*;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

final class CommanderSetupScreen extends Screen {
    private final Screen parent;
    private final BlockPos table;
    private final int slot;
    private final String deckId;
    private final List<CardRef> leaders=new ArrayList<>();
    private int pick;
    private String issue;
    CommanderSetupScreen(Screen parent,BlockPos table,int slot,String deckId,DeckContents deck,String original){
        super(Component.literal("Choose your commander"));this.parent=parent;this.table=table;this.slot=slot;this.deckId=deckId;
        issue=original;
        if(deck!=null){
            List<CardRef> all=new ArrayList<>();
            for(var id:Cards.collectible())for(boolean ench:new boolean[]{false,true})for(int i=0;i<deck.cards().count(id,ench);i++){
                var ref=new CardRef(id,ench);all.add(ref);if(CommanderMatch.eligible(id)&&leaders.stream().noneMatch(c->c.id().equals(id)))leaders.add(ref);
            }
            String why=CommanderMatch.problem(deck.deckClass(),all,leaders.isEmpty()?null:leaders.getFirst().id());if(why!=null)issue=why;
        }
    }
    @Override protected void init(){
        int x=width/2,y=height-29;
        button(x-143,y,58,"Back",this::onClose);
        button(x-81,y,32,"<",()->pick=Math.floorMod(pick-1,leaders.size())).active=leaders.size()>1;
        button(x-46,y,32,">",()->pick=(pick+1)%leaders.size()).active=leaders.size()>1;
        button(x-8,y,148,"Ready for Commander",()->ClientPlayNetworking.send(new CommanderJoinPayload(table,slot,deckId,leaders.get(pick).id().toString()))).active=issue.isEmpty()&&!leaders.isEmpty();
    }
    private Button button(int x,int y,int w,String text,Runnable action){return addRenderableWidget(new TableBookButton(x,y,w,20,Component.literal(text),action));}
    @Override public void extractRenderState(GuiGraphicsExtractor g,int mx,int my,float dt){
        int w=Math.min(width-8,400);
        BinderTheme.cover(g,(width-w)/2,3,w,height-6);
        int cx=width/2;
        g.centeredText(font,"Commander   /   Four players",cx,10,BinderTheme.GOLD);
        g.centeredText(font,"Any collectible mob can lead, at any rarity.",cx,25,0xffebe6d5);
        if(!leaders.isEmpty()){
            float scale=Math.min(1f,(height-114f)/CardRenderer.HEIGHT);
            var r=leaders.get(pick);
            CardRenderer.draw(g,font,CardItem.of(r.id(),r.enchanted()),cx-Math.round(CardRenderer.WIDTH*scale)/2,41,scale);
            g.centeredText(font,(pick+1)+" / "+leaders.size()+" eligible mobs",cx,height-68,0xffedd6ae);
        }
        var lines=font.split(Component.literal(issue.isEmpty()?"20 different cards  /  40 Health  /  Last hero standing wins":issue),width-20);
        int y=height-52;for(var line:lines){g.text(font,line,(width-font.width(line))/2,y,issue.isEmpty()?0xfff0dfae:0xffffa08b,false);y+=10;}
        super.extractRenderState(g,mx,my,dt);
    }
    @Override public void onClose(){minecraft.gui.setScreen(parent);}
    @Override public boolean isPauseScreen(){return false;}
}
