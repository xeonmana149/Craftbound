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
    private final CardRef leader;
    private final String issue;
    CommanderSetupScreen(Screen parent,BlockPos table,int slot,String deckId,DeckContents deck,String original){
        super(Component.literal("Your commander"));this.parent=parent;this.table=table;this.slot=slot;this.deckId=deckId;
        var id=deck==null?null:deck.commander().orElse(null);
        leader=id!=null && CommanderMatch.eligible(id)?new CardRef(id,deck.cards().count(id,true)>0):null;
        issue=original.isEmpty() && leader==null?"Choose a legendary commander in the deck builder.":original;
    }
    @Override protected void init(){
        int x=width/2,y=height-29;
        button(x-108,y,58,"Back",this::onClose);
        button(x-46,y,154,"Ready for Commander",()->ClientPlayNetworking.send(new CommanderJoinPayload(table,slot,deckId,leader.id().toString()))).active=issue.isEmpty()&&leader!=null;
    }
    private Button button(int x,int y,int w,String text,Runnable action){return addRenderableWidget(new TableBookButton(x,y,w,20,Component.literal(text),action));}
    @Override public void extractRenderState(GuiGraphicsExtractor g,int mx,int my,float dt){
        int w=Math.min(width-8,400);
        BinderTheme.cover(g,(width-w)/2,3,w,height-6);
        int cx=width/2;
        g.centeredText(font,"Commander   /   Four players",cx,10,BinderTheme.GOLD);
        g.centeredText(font,"Your deck’s legendary commander",cx,25,0xffebe6d5);
        if(leader!=null){
            float scale=Math.min(1f,(height-114f)/CardRenderer.HEIGHT);
            var r=leader;
            CardRenderer.draw(g,font,CardItem.of(r.id(),r.enchanted()),cx-Math.round(CardRenderer.WIDTH*scale)/2,41,scale);
            
        }
        var lines=font.split(Component.literal(issue.isEmpty()?"40 different cards  /  40 Health  /  Last hero standing wins":issue),width-20);
        int y=height-52;for(var line:lines){g.text(font,line,(width-font.width(line))/2,y,issue.isEmpty()?0xfff0dfae:0xffffa08b,false);y+=10;}
        super.extractRenderState(g,mx,my,dt);
    }
    @Override public void onClose(){minecraft.gui.setScreen(parent);}
    @Override public boolean isPauseScreen(){return false;}
}
