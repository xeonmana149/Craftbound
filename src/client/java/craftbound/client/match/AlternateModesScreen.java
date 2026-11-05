package craftbound.client.match;

import craftbound.client.binder.BinderTheme;
import craftbound.client.card.CardRenderer;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public final class AlternateModesScreen extends Screen {
    private final TableScreen table;
    private int left, top, bookWidth, bookHeight;
    private record Format(String name, String seats, String description, Item icon, Runnable open) {}
    AlternateModesScreen(TableScreen table) {
        super(Component.literal("Alternate Modes"));
        this.table = table;
    }

    @Override protected void init() {
        bookWidth = Math.min(402, width-12);
        bookHeight = Math.min(236, height-10);
        left = (width-bookWidth)/2; top = (height-bookHeight)/2;
        int gap = 4, start = top+33;
        int rowHeight = (bookHeight-65-2*gap)/3;
        var modes = List.of(
            new Format("Team Duos", "2 vs 2", "Take turns as a team. Share 30 Health and play your own deck alongside your partner.", Items.SHIELD, () -> table.openRelay(this)),
            new Format("Commander", "4 players", "Lead a deck of 20 different cards. Be the last hero standing.", Items.OMINOUS_BOTTLE, () -> table.openCommander(this)),
            new Format("The Trials", "Solo PvE", "Draft a deck and chart a route through fights, villages and camps to a final guardian.", Items.TRIAL_KEY, table::openTrials)
        );
        for (var mode : modes) {
            int y = start;
            var button = new Button(left+10,y,bookWidth-20,rowHeight,Component.literal(mode.name()),b->mode.open().run(), message->message.get()) {
                @Override protected void extractContents(GuiGraphicsExtractor g,int mx,int my,float dt) {
                    int x=getX(), row=getY(), right=getRight();
                    g.fill(x,row,right,getBottom(),isHoveredOrFocused()?0xFFFFEDCD:BinderTheme.PAGE);
                    g.outline(x,row,getWidth(),getHeight(),isHoveredOrFocused()?BinderTheme.GOLD:0xFFAB8252);
                    int ix=x+7, iy=row+(getHeight()-20)/2;
                    g.fill(ix,iy,ix+20,iy+20,BinderTheme.WOOD_DARK);
                    g.item(new ItemStack(mode.icon()),ix+2,iy+2);
                    int tx=x+33;
                    g.text(font,Component.literal(mode.name()).withStyle(s->s.withBold(true)),tx,row+5,BinderTheme.TEXT_DARK,false);
                    g.text(font,mode.seats(),right-7-font.width(mode.seats()),row+5,0xFF735237,false);
                    int dy=row+17;
                    for(var line:font.split(Component.literal(mode.description()),right-tx-9)) {
                        if(dy+8>getBottom()-1)break;
                        g.text(font,line,tx,dy,0xFF60452F,false);dy+=10;
                    }
                }
            };
            addRenderableWidget(button);
            start += rowHeight+gap;
        }
        addRenderableWidget(new TableBookButton(left+10,top+bookHeight-25,62,17,Component.literal("Back"),this::onClose));
    }
    @Override public void extractRenderState(GuiGraphicsExtractor g,int mx,int my,float dt) {
        BinderTheme.cover(g,left,top,bookWidth,bookHeight);
        g.text(font,Component.literal("Alternate Modes").withStyle(s->s.withBold(true)),left+12,top+12,BinderTheme.GOLD,false);
        super.extractRenderState(g,mx,my,dt);
    }
    @Override public void onClose(){ minecraft.gui.setScreen(table); }
    @Override public boolean isPauseScreen(){ return false; }
}
