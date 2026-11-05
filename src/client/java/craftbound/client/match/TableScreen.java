package craftbound.client.match;

import com.mojang.blaze3d.platform.InputConstants;
import craftbound.card.CardCatalog;
import craftbound.card.Cards;
import craftbound.card.DeckRules;
import craftbound.client.card.CardRenderer;
import craftbound.client.binder.BinderTheme;
import craftbound.client.deck.DeckCaseScreen;
import net.minecraft.world.item.Items;
import craftbound.component.ModComponents;
import craftbound.deck.DeckBuilding;
import craftbound.deck.DeckContents;
import craftbound.game.HeroPowers;
import craftbound.game.MatchManager;
import craftbound.item.CardItem;
import craftbound.item.ModItems;
import craftbound.network.TableActionPayload;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/** Deck preparation and matchmaking share the binder that the player carries to the table. */
public class TableScreen extends Screen {
    private static int lastSlot = -1;
    private static String lastDeckId = "";
    private static int lastMode = TableActionPayload.PLAYER;
    private static final int INK = BinderTheme.TEXT_DARK, MUTED = 0xFF70563D, LINE = 0xFFC5A479;
    private int left, top, bookWidth, bookHeight, listLeft, listWidth, detailsLeft, detailsWidth, bodyTop, bodyBottom;
    private int preferredSlot = lastSlot;
    private String preferredId = lastDeckId;
    private int binderSlot = -1;
    private int refreshTicks;
    private CardCatalog.State catalog;
    private List<ItemStack> inventorySnapshot = List.of();
    private List<CardRow> detailRows = List.of();
    private final int[] costBins = new int[8];
    private int peakCount = 1;
    private boolean decksInitialized;
    private TableBookButton duel, solo, ready, edit, collection, create;

    private final BlockPos table;
    private final int tableMode;
    private String waiting;
    private String error;
    private boolean sent;
    private boolean developerAccess;
    private long sentAt;

    private record DeckEntry(int slot, String deckId, ItemStack stack, DeckContents deck, Component name, String problem) {
    }

    private record CardRow(Identifier id, boolean enchanted, int count, int missing) {
    }

    private record CardSpot(int x, int y, int width, int height, Identifier id, boolean enchanted) {
    }

    private final List<DeckEntry> decks = new ArrayList<>();
    private final List<int[]> deckSpots = new ArrayList<>();
    private final List<CardSpot> cardSpots = new ArrayList<>();
    private int selected;
    private int cardScroll;
    private int deckScroll;
    private int[] deckListArea;
    private int[] cardListArea;

    public TableScreen(BlockPos table, String waiting, String error) {
        this(table, waiting, error, false);
    }

    public TableScreen(BlockPos table, String waiting, String error, boolean developerAccess) {
        this(table,waiting,error,developerAccess,0);
    }
    public TableScreen(BlockPos table, String waiting, String error, boolean developerAccess,int tableMode) {
        super(Component.translatable("table_ui.craftbound.title"));
        this.table = table;
        this.tableMode = Math.clamp(tableMode,0,2);
        this.developerAccess = developerAccess;
        this.waiting = waiting;
        this.error = error;
        if (!waiting.isEmpty()) {
            lastMode = TableActionPayload.PLAYER;
        }
    }

    public boolean commanderTable() { return tableMode == 2; }
    public int tableMode() { return tableMode; }
    public craftbound.deck.DeckFormat deckFormat() { return craftbound.deck.DeckFormat.table(tableMode); }
    public BlockPos table() {
        return this.table;
    }

    // The server's answer: someone's waiting here, or why Ready didn't work.
    public boolean developerAccess() { return developerAccess; }

    public void update(String waiting, String error, boolean developerAccess) {
        boolean changed = this.developerAccess != developerAccess;
        this.developerAccess = developerAccess;
        if (changed) rebuildWidgets();
        this.waiting = waiting;
        this.error = error;
        this.sent = false;
    }

    @Override
    protected void init() {
        bookWidth = Math.min(width - 10, 540);
        bookHeight = Math.min(height - 10, 315);
        left = (width-bookWidth)/2; top = (height-bookHeight)/2;
        listLeft = left+10;
        listWidth = Math.min(160,(bookWidth-26)*37/100);
        detailsLeft = listLeft+listWidth+4;
        detailsWidth = left+bookWidth-10-detailsLeft;
        bodyTop = top+55;
        bodyBottom = top+bookHeight-37;
        int navWidth = (bookWidth-24)/3;
        if(tableMode==0) {
            duel = button(listLeft, top+29, navWidth, 19, "Player Vs Player", ()->chooseMode(TableActionPayload.PLAYER));
            solo = button(listLeft+navWidth+2,top+29,navWidth,19,"Card Master",()->chooseMode(TableActionPayload.CARD_MASTER));
            button(listLeft+2*(navWidth+2),top+29,bookWidth-24-2*navWidth,19,"The Trials",this::openTrials);
        } else {
            duel=button(listLeft,top+29,bookWidth-20,19,tableMode==1?"Team Duos · two players on each team":"Commander · four players, one survivor",
                    ()->{});
            duel.active=false;solo=null;
        }
        if (developerAccess) button(left+bookWidth-170, top+7, 72, 17, "Developer",
                () -> minecraft.gui.setScreen(new LayoutPreviewSettingsScreen(this)));
        collection = button(left+bookWidth-94, top+7, 84, 17, "Collection", ()->openBinder(false,false));
        create = button(listLeft+4,bodyBottom-20,listWidth-8,17,"New Deck",()->openBinder(true,false));
        edit = button(detailsLeft+detailsWidth-61,bodyTop+4,56,16,"Edit Deck",()->openBinder(false,true));
        ready = button(left+bookWidth-91,top+bookHeight-28,81,19,"Ready",this::ready);
        updateDecks(true);
    }

    private TableBookButton button(int x,int y,int w,int h,String label,Runnable onClick) {
        return addRenderableWidget(new TableBookButton(x,y,w,h,Component.literal(label),onClick));
    }

    private void chooseMode(int mode) { lastMode=mode; error=""; }
    private void updateDecks(boolean force) {
        Inventory inventory = minecraft.player.getInventory();
        if (!force && catalog == CardCatalog.current() && inventorySnapshot.size() == inventory.getContainerSize()) {
            boolean same = true;
            for (int slot = 0; slot < inventorySnapshot.size(); slot++)
                if (!ItemStack.matches(inventorySnapshot.get(slot), inventory.getItem(slot))) { same = false; break; }
            if (same) return;
        }
        catalog = CardCatalog.current();
        inventorySnapshot = java.util.stream.IntStream.range(0, inventory.getContainerSize())
                .mapToObj(slot -> inventory.getItem(slot).copy()).toList();
        var next = new ArrayList<DeckEntry>();
        int firstBinder = -1;
        for (int slot=0; slot<inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.is(ModItems.BINDER)) {
                if (firstBinder<0) firstBinder=slot;
                for(var entry:craftbound.deck.BinderDecks.get(stack).entries()) {
                    if (entry.deck().format()!=deckFormat()) continue;
                    var view=entry.asStack();
                    next.add(new DeckEntry(slot,entry.id(),view,entry.deck(),DeckBuilding.name(view),tableIssue(inventory,view)));
                }
            }
        }
        binderSlot = firstBinder;
        decks.clear(); decks.addAll(next); selected=0;
        for(int i=0;i<decks.size();i++) {
            DeckEntry entry=decks.get(i);
            if(entry.slot()==preferredSlot && entry.deckId().equals(preferredId)) { selected=i;break; }
            if(entry.problem()==null && decks.get(selected).problem()!=null) selected=i;
        }
        if(!decksInitialized || force) deckScroll=Math.max(0,selected-visibleDecks()+1);
        decksInitialized=true;
        cacheDetails();
    }

    private String tableIssue(Inventory inventory,ItemStack stack) {
        var deck=stack.getOrDefault(ModComponents.DECK_CONTENTS,DeckContents.EMPTY);
        String issue=DeckBuilding.whyCantPlay(inventory,stack);
        if ("match_ui.craftbound.commander_size".equals(issue) && deck.size()<deck.format().size())
            return "Add "+(deck.format().size()-deck.size())+" more distinct cards to reach 40.";
        return issue;
    }
    private void cacheDetails() {
        detailRows = chosen() == null ? List.of() : rows(chosen());
        java.util.Arrays.fill(costBins, 0);
        peakCount = 1;
        for (var row : detailRows) {
            int cost = Math.clamp(Cards.get(row.id()).cost(), 0, 7);
            costBins[cost] += row.count();
            peakCount = Math.max(peakCount, costBins[cost]);
        }
    }

    private int visibleDecks(){return Math.max(1,(bodyBottom-25-(bodyTop+18))/24);}
    private DeckEntry chosen(){return decks.isEmpty()?null:decks.get(selected);}

    public void returnFromBinder(int slot, String deckId) {
        if(deckId!=null) { preferredSlot=slot; preferredId=deckId; cardScroll=0; }
        minecraft.gui.setScreen(this);
    }

    private int collectionSlot() {
        var choice=chosen();
        if(choice!=null && !choice.deckId().isEmpty())return choice.slot();
        if(preferredSlot>=0 && preferredSlot<minecraft.player.getInventory().getContainerSize()
                && minecraft.player.getInventory().getItem(preferredSlot).is(ModItems.BINDER))return preferredSlot;
        return binderSlot;
    }
    private void openBinder(boolean newDeck,boolean editDeck) {
        int slot=collectionSlot(); var choice=chosen();
        if(slot<0 || editDeck && (choice==null || choice.deckId().isEmpty()))return;
        minecraft.gui.setScreen(DeckCaseScreen.fromTable(this,slot,editDeck?choice.deckId():null,newDeck));
    }

    private String deckIssue() {
        var choice=chosen();
        return choice==null?"Choose a saved deck at the table first."
                :choice.problem()==null?"":Component.translatable(choice.problem(),MatchManager.PRACTICE_MIN_DECK).getString();
    }

    void openCommander(Screen parent) {
        var e=chosen(); minecraft.gui.setScreen(new CommanderSetupScreen(parent,table,
                e==null?-1:e.slot(),e==null?"":e.deckId(),e==null?null:e.deck(),deckIssue()));
    }
    void openRelay(Screen parent) {
        var e=chosen(); minecraft.gui.setScreen(new RelaySetupScreen(parent,table,e==null?-1:e.slot(),e==null?"":e.deckId(),deckIssue()));
    }
    void openTrials() { ClientPlayNetworking.send(new craftbound.network.TrialsActionPayload(table,"",0,"open",-1)); }

    private void ready() {
        if(tableMode==1) {openRelay(this);return;}
        if(tableMode==2) {openCommander(this);return;}
        var e=chosen();
        if(e==null||e.problem()!=null||sent)return;
        lastSlot=e.slot(); lastDeckId=e.deckId(); sent=true; sentAt=System.currentTimeMillis(); error="";
        ClientPlayNetworking.send(new TableActionPayload(table,lastMode,e.slot(),e.deckId()));
    }

    @Override public void extractRenderState(GuiGraphicsExtractor g,int mx,int my,float dt) {
        BinderTheme.cover(g,left,top,bookWidth,bookHeight);
        g.text(font,Component.literal(tableMode==1?"Team Table":tableMode==2?"Commander":"Card Table").withStyle(ChatFormatting.BOLD),listLeft+1,top+11,BinderTheme.GOLD,false);
        var entry=chosen();
        duel.selected=tableMode!=0 || lastMode==TableActionPayload.PLAYER; if(solo!=null)solo.selected=!duel.selected;
        collection.active=collectionSlot()>=0;
        create.active=collection.active && craftbound.deck.BinderDecks.get(minecraft.player.getInventory().getItem(collectionSlot())).count(deckFormat())<craftbound.deck.SavedDecks.LIMIT;
        edit.active=entry!=null && !entry.deckId().isEmpty(); edit.visible=entry!=null;
        ready.active=entry!=null && entry.problem()==null && !sent;
        ready.selected=ready.active; ready.setMessage(Component.literal(sent?"Joining...":"Ready"));
        cardSpots.clear(); deckSpots.clear(); cardListArea=null;
        drawDeckList(g,mx,my);
        drawDetails(g,mx,my);
        String hint=lastMode==TableActionPayload.PLAYER?"Ready up with another player at this table.":"Challenge the Card Master at this table.";
        if(tableMode==1)hint="Choose a deck. Sit beside your partner, opposite the other team.";
        if(tableMode==2)hint="Bring 40 different cards, including your legendary commander.";
        if(entry==null)hint=tableMode==0?"The Trials lets you draft a deck at the table.":"Create a deck in your binder, or choose a saved deck.";
        if(entry!=null && entry.problem()!=null)hint=deckIssue();
        if(!waiting.isEmpty())hint=tableMode!=0?waiting+"  |  Ready to join.":waiting+" is looking for a duel.";
        if(!error.isEmpty())hint=Component.translatable(error,MatchManager.PRACTICE_MIN_DECK).getString();
        CardRenderer.fitText(g,font,Component.literal(hint),listLeft,top+bookHeight-22,bookWidth-111,error.isEmpty()?0xFFFFE7BF:0xFFFFAB97,false,false);
        super.extractRenderState(g,mx,my,dt);
        drawHoveredCard(g,left,bookWidth,mx,my);
    }

    private void drawDeckList(GuiGraphicsExtractor g,int mx,int my) {
        int x=listLeft,w=listWidth;
        g.fill(x,bodyTop,x+w,bodyBottom,BinderTheme.WOOD_DARK);
        g.outline(x,bodyTop,w,bodyBottom-bodyTop,0xFF996E47);
        CardRenderer.fitText(g,font,Component.literal("My "+deckFormat().title+" Decks"),x+5,bodyTop+5,w-10,BinderTheme.GOLD,false,false);
        int y=bodyTop+18,last=bodyBottom-24;
        deckListArea=new int[]{x,y,w,last-y};
        if(decks.isEmpty()){
            paragraph(g,Component.literal("Your saved decks appear here."),x+6,y+4,w-12,last,0xFFE3CDB0); return;
        }
        deckScroll=Math.clamp(deckScroll,0,Math.max(0,decks.size()-visibleDecks()));
        for(int i=deckScroll;i<decks.size()&&y+22<=last;i++,y+=24) {
            var e=decks.get(i);boolean on=i==selected;int[] hit={x+3,y,w-6,22};deckSpots.add(hit);
            g.fill(x+3,y,x+w-3,y+22,on?BinderTheme.PAGE:inside(hit,mx,my)?0xFF845633:0xFF5E392B);
            g.fill(x+3,y,x+6,y+22,e.deck().deckClass().frameColor());
            CardRenderer.fitText(g,font,e.name(),x+9,y+2,w-16,on?INK:0xFFFFF1D7,false,false);
            String detail=e.deck().size()+" cards";
            g.text(font,detail,x+9,y+12,on?MUTED:0xFFE6CEAB,false);
        }
        if (decks.size()>visibleDecks()) {
            int track=last-bodyTop-18,thumb=Math.max(4,track*visibleDecks()/decks.size());
            int bar=bodyTop+18+(track-thumb)*deckScroll/(decks.size()-visibleDecks());
            g.fill(x+w-2,bar,x+w-1,bar+thumb,BinderTheme.GOLD);
        }
    }

    private int paragraph(GuiGraphicsExtractor g,Component text,int x,int y,int width,int bottom,int color){
        for(var line:font.split(text,Math.max(1,width))){
            if(y+font.lineHeight>bottom)break;
            g.text(font,line,x,y,color,false); y+=font.lineHeight+1;
        }
        return y;
    }

    private void drawDetails(GuiGraphicsExtractor g,int mx,int my) {
        int x=detailsLeft,w=detailsWidth,y=bodyTop;
        g.fill(x,y,x+w,bodyBottom,BinderTheme.PAGE);
        g.fill(x,y,x+3,bodyBottom,LINE);
        int tx=x+8,usable=w-16;
        var e=chosen();
        if(e==null) {
            g.item(new ItemStack(collectionSlot()<0?Items.BOOK:ModItems.BINDER),tx,y+8);
            g.text(font,Component.literal("Your first deck").withStyle(ChatFormatting.BOLD),tx+21,y+12,INK,false);
            int next=paragraph(g,Component.literal(collectionSlot()<0?"Bring a Card Binder to browse your collection and save decks here.":(commanderTable()?"Choose New Deck, pick a legendary commander, then add cards from your collection.":"Choose New Deck, pick a class, then add cards from your collection.")),tx,y+34,usable,bodyBottom-5,MUTED);
            paragraph(g,Component.literal(tableMode==0?"To play solo with borrowed cards, choose The Trials.":"Use New Deck to prepare a deck before joining this table."),tx,next+8,usable,bodyBottom-5,MUTED);
            return;
        }
        CardRenderer.fitText(g,font,e.name().copy().withStyle(ChatFormatting.BOLD),tx,y+8,usable-59,INK,false,false);
        var cardClass=e.deck().deckClass();
        var power=HeroPowers.of(cardClass);
        String line=cardClass.displayName().getString()+"  /  "+e.deck().size()+" of "+e.deck().format().size()+" cards";
        CardRenderer.fitText(g,font,Component.literal(line),tx,y+23,usable,MUTED,false,false);
        int cursor=y+37;
        if (commanderTable() && e.deck().commander().isPresent() && bodyBottom-cursor>=29) {
            var leader=e.deck().commander().get();
            g.fill(tx,cursor,x+w-8,cursor+23,0xFF453427);
            g.outline(tx,cursor,usable,23,0xFFD9AD5A);
            CardRenderer.fitText(g,font,Component.literal("Commander"),tx+4,cursor+2,usable-8,0xFFFFD27A,false,false);
            CardRenderer.fitText(g,font,Cards.name(leader),tx+4,cursor+12,usable-8,0xFFFFF1D7,false,false);
            cardSpots.add(new CardSpot(tx,cursor,usable,23,leader,e.deck().cards().count(leader,true)>0));
            cursor+=28;
        }
        boolean tall=bodyBottom-bodyTop>=156;
        if(power!=HeroPowers.Power.NONE && bodyBottom-cursor>=36) {
            g.item(new ItemStack(BuiltInRegistries.ITEM.getValue(power.icon())),tx,cursor-3);
            CardRenderer.fitText(g,font,power.displayName(),tx+20,cursor,usable-20,INK,false,false);
            cursor+=15;
            if(tall) cursor=paragraph(g,power.description(),tx,cursor,usable,cursor+21,MUTED)+3;
        }
        var rows = detailRows;
        if(bodyBottom-cursor>=54){
            int binWidth=usable/8,barHeight=tall?15:8;
            g.text(font,"XP curve",tx,cursor,MUTED,false);cursor+=11;
            for(int i=0;i<8;i++){
                int bx=tx+i*binWidth,h=Math.round(barHeight*costBins[i]/(float)peakCount);
                g.fill(bx,cursor+barHeight-h,bx+binWidth-3,cursor+barHeight,0xFF527343);
                g.fill(bx,cursor+barHeight,bx+binWidth-3,cursor+barHeight+1,LINE);
                g.text(font,i==7?"7+":String.valueOf(i),bx,cursor+barHeight+2,MUTED,false);
            }
            cursor+=barHeight+14;
        }
        if (commanderTable()) rows=rows.stream().filter(row->!e.deck().leads(row.id())).toList();
        g.fill(tx,cursor-3,x+w-7,cursor-2,LINE);
        int columns=w>=245?2:1,rowHeight=13,visible=Math.max(0,(bodyBottom-cursor-3)/rowHeight);
        cardListArea=new int[]{tx,cursor,usable,Math.max(0,bodyBottom-cursor-3)};
        int totalRows=(rows.size()+columns-1)/columns;
        cardScroll=Math.clamp(cardScroll,0,Math.max(0,totalRows-visible));
        if(rows.isEmpty()) paragraph(g,Component.literal("Edit this deck to add cards."),tx,cursor+2,usable,bodyBottom-3,MUTED);
        for(int r=0;r<visible;r++)for(int col=0;col<columns;col++){
            int index=(cardScroll+r)*columns+col;if(index>=rows.size())break;
            var row=rows.get(index);int rx=tx+col*(usable+4)/columns,ry=cursor+r*rowHeight,cw=(usable+4)/columns-4;
            cardSpots.add(new CardSpot(rx,ry,cw,rowHeight-1,row.id(),row.enchanted()));
            boolean hover=mx>=rx&&mx<rx+cw&&my>=ry&&my<ry+rowHeight-1;
            g.fill(rx,ry,rx+cw,ry+rowHeight-1,hover?0xFFDAB786:0xFFE6D3AE);
            MatchArt.cost(g,font,rx+1,ry+1,9,Cards.get(row.id()).cost());
            CardRenderer.fitText(g,font,Cards.name(row.id()),rx+13,ry+2,cw-31,row.enchanted()?0xFF623B86:INK,false,false);
            String count="x"+row.count();g.text(font,count,rx+cw-2-font.width(count),ry+2,row.missing()>0?0xFFA32F21:MUTED,false);
        }
    }

    private List<CardRow> rows(DeckEntry entry) {
        List<CardRow> rows = new ArrayList<>();
        Inventory inventory = this.minecraft.player.getInventory();
        for (Identifier id : Cards.collectible()) {
            for (boolean enchanted : new boolean[]{false, true}) {
                int count = entry.deck().cards().count(id, enchanted);
                if (count > 0) {
                    int missing = entry.deck().list() ? DeckBuilding.missing(inventory, entry.deck(), id, enchanted) : 0;
                    rows.add(new CardRow(id, enchanted, count, missing));
                }
            }
        }
        rows.sort(Comparator.comparingInt((CardRow row) -> Cards.get(row.id()).cost()).thenComparing(row -> Cards.name(row.id()).getString()));
        return rows;
    }

    // Hovering a card in the list shows it full size beside the panel.
    private void drawHoveredCard(GuiGraphicsExtractor g, int panelX, int panelWidth, int mouseX, int mouseY) {
        for (CardSpot spot : this.cardSpots) {
            if (mouseX < spot.x() || mouseX >= spot.x() + spot.width() || mouseY < spot.y() || mouseY >= spot.y() + spot.height()) {
                continue;
            }
            float scale = Math.min(1.0F, (this.height - 20) / (float) CardRenderer.HEIGHT);
            int w = Math.round(CardRenderer.WIDTH * scale);
            int h = Math.round(CardRenderer.HEIGHT * scale);
            int x = panelX + panelWidth + 8 + w <= this.width ? panelX + panelWidth + 8
                    : panelX - 8 - w >= 0 ? panelX - 8 - w : Math.min(this.width - w - 4, mouseX + 12);
            int y = Math.max(4, Math.min(this.height - h - 4, mouseY - h / 2));
            CardRenderer.draw(g, this.font, CardItem.of(spot.id(), spot.enchanted()), x, y, scale);
            return;
        }
    }


    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() != InputConstants.MOUSE_BUTTON_LEFT) {
            return true;
        }
        double mouseX = event.x();
        double mouseY = event.y();
        for (int i = 0; i < this.deckSpots.size(); i++) {
            if (inside(this.deckSpots.get(i), mouseX, mouseY)) {
                this.selected = this.deckScroll + i;
                cacheDetails();
                preferredSlot=chosen().slot(); preferredId=chosen().deckId();
                this.cardScroll = 0;
                this.error = "";
                MatchSounds.play("ui.button.click", 1.2F, 0.4F);
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (inside(this.deckListArea, mouseX, mouseY)) {
            this.deckScroll -= (int) Math.signum(scrollY);
            return true;
        }
        if (inside(this.cardListArea, mouseX, mouseY)) {
            this.cardScroll -= (int) Math.signum(scrollY);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public void tick() {
        if (++refreshTicks % 10 == 0) updateDecks(false);
        if (this.sent && System.currentTimeMillis() - this.sentAt > 5000) {
            this.sent = false;
        }
    }

    private static boolean inside(int[] rect, double mouseX, double mouseY) {
        return rect != null && mouseX >= rect[0] && mouseX < rect[0] + rect[2] && mouseY >= rect[1] && mouseY < rect[1] + rect[3];
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
