package craftbound.client.match;

import craftbound.client.binder.BinderTheme;
import craftbound.client.card.CardRenderer;
import craftbound.game.LayoutPreviewData;
import craftbound.game.LayoutPreviewData.Mode;
import craftbound.game.LayoutPreviewData.Density;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Local, disposable layout samples; never joins a table or sends match actions. */
public final class LayoutPreviewSettingsScreen extends Screen {
    private final TableScreen parent;
    private Mode mode = Mode.DUOS;
    private Density density = Density.POPULATED;
    private int seat;
    private int left, top, bookWidth, bookHeight;
    private String error = "";

    public LayoutPreviewSettingsScreen(TableScreen parent) {
        super(Component.literal("Developer settings"));
        this.parent = parent;
    }

    @Override protected void init() {
        if (!parent.developerAccess()) { minecraft.gui.setScreen(parent); return; }
        bookWidth = Math.min(380, width - 12);
        bookHeight = Math.min(228, height - 12);
        left = (width-bookWidth)/2; top = (height-bookHeight)/2;
        int x=left+12, w=bookWidth-24, row=top+51;
        int tab=(w-8)/3;
        modeButton(x,row,tab,"Player Vs Player",Mode.DUEL);
        modeButton(x+tab+4,row,tab,"Team Duos",Mode.DUOS);
        modeButton(x+2*(tab+4),row,w-2*(tab+4),"Commander",Mode.COMMANDER);
        button(x,row+29,w,"Board: "+switch(density) {
            case EMPTY -> "Empty"; case POPULATED -> "Populated"; case FULL -> "Full";
        },()->{
            density=switch(density) {case EMPTY->Density.POPULATED;case POPULATED->Density.FULL;case FULL->Density.EMPTY;};
            rebuildWidgets();
        });
        button(x,row+56,w,"Seat: "+(seat+1)+" / "+seats(),()->{seat=(seat+1)%seats();rebuildWidgets();});
        button(x,top+bookHeight-28,65,"Back",this::onClose);
        button(left+bookWidth-112,top+bookHeight-28,100,"Open preview",this::open);
    }

    private int seats(){return mode==Mode.DUEL?2:4;}
    private TableBookButton button(int x,int y,int w,String text,Runnable click) {
        return addRenderableWidget(new TableBookButton(x,y,w,20,Component.literal(text),click));
    }
    private void modeButton(int x,int y,int w,String text,Mode choice) {
        var button=button(x,y,w,text,()->{mode=choice;seat%=seats();error="";rebuildWidgets();});
        button.selected=mode==choice;
    }
    private void open() {
        if (!parent.developerAccess()) { onClose(); return; }
        try {
            var state=LayoutPreviewData.create(mode,density,seat);
            Screen preview=switch(mode) {
                case DUEL -> new MatchScreen(state.toString()).layoutPreview(this);
                case DUOS -> new RelayScreen(state).layoutPreview(this);
                case COMMANDER -> new CommanderScreen(state).layoutPreview(this);
            };
            minecraft.gui.setScreen(preview);
        } catch(IllegalArgumentException ex) {
            error="Unable to preview this catalogue: "+ex.getMessage();
        }
    }

    @Override public void extractRenderState(GuiGraphicsExtractor g,int mx,int my,float dt) {
        BinderTheme.cover(g,left,top,bookWidth,bookHeight);
        g.text(font,Component.literal("Developer settings").withStyle(s->s.withBold(true)),
                left+12,top+12,BinderTheme.GOLD,false);
        CardRenderer.fitText(g,font,Component.literal("Preview multiplayer layouts on your own."),
                left+12,top+30,bookWidth-24,0xFFF1DFC3,false,false);
        int y=top+139;
        String hint=error.isEmpty()
            ? "Team Duos shows both teams side by side. In Commander, click a rival at the top to view their field. Right-click a card to inspect it. Esc returns here."
            : error;
        for(var line:font.split(Component.literal(hint),bookWidth-28)) {
            if(y+font.lineHeight>top+bookHeight-36)break;
            g.text(font,line,left+14,y,error.isEmpty()?0xFFE4CFB1:0xFFFFA890,false);
            y+=font.lineHeight+2;
        }
        super.extractRenderState(g,mx,my,dt);
    }
    @Override public void onClose(){minecraft.gui.setScreen(parent);}
    @Override public boolean isPauseScreen(){return false;}
}
