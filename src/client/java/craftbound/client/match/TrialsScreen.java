package craftbound.client.match;

import com.google.gson.Gson;
import com.mojang.blaze3d.platform.InputConstants;
import craftbound.card.CardClass;
import craftbound.card.Cards;
import craftbound.client.Tooltips;
import craftbound.client.binder.BinderTheme;
import craftbound.client.card.CardInspectScreen;
import craftbound.client.card.CardRenderer;
import craftbound.game.HeroPowers;
import craftbound.game.TrialTraining;
import craftbound.item.CardItem;
import craftbound.network.TrialsActionPayload;
import craftbound.network.TrialsStatePayload;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import static craftbound.client.binder.BinderTheme.*;

/** Server-owned decisions presented as an ascending travel map and contextual room pages. */
public final class TrialsScreen extends Screen {
    private enum Phase {
        CLASS, DRAFT, MAP, READY, REWARD, ELITE_PRIZE, SHOP, SHOP_CARDS, SHOP_CHARMS, SERVICES,
        REPLACE, REMOVE, COPY, TRAIN_CARD, TRAIN_TYPE, REST, BOON, EVENT, TRADE_OUT, TRADE_IN,
        PAUSED, VICTORY, DEFEAT, UNAVAILABLE
    }
    private enum Page { JOURNEY, DECK, MAP, NODE, RETIRE }
    private record Foe(String name, CardClass heroClass, int health, int bonusXp, int extraCards, int bounty,
                       boolean elite, String style, String abilityName, String abilityText, int deckSize, boolean boss) {}
    private record Node(String id, int row, int lane, String kind, String title, String description,
                        List<String> next, String status, Foe foe, Double mapX, Double mapY) {}
    private record Offer(String key, String title, String description, int price, boolean enabled, String iconItem) {}
    private record Run(String runId, int revision, Phase phase, CardClass heroClass,
                       List<String> choices, List<String> deck, int draftSize, int wins, int lives,
                       int encounters, int heart, int pack, int torch, String message, boolean afterBattle,
                       int marks, List<String> charms, int charmPrice, String pendingCharm, String tradeOut,
                       int health, int maxHealth, int depth, int roomCount, String actName, List<String> training,
                       List<Node> map, String activeNode, List<Integer> eligibleIndices, List<Offer> menuItems,
                       int deckMinimum, int deckLimit, int bonusHealth, int charmSlots, List<Charm> charmDefinitions, List<Charm> acquiredBoons) {}
    private record DeckEntry(Identifier id, int slot, int cost, TrialTraining training) {}
    private record DeckGroup(Identifier id, int cost, TrialTraining training) {}
    private record DeckStack(DeckGroup card, List<Integer> slots) {}
    private record HeaderIcon(int x, int y, ItemStack icon, List<Component> text, String badge) {}
    record Charm(String id, String title, String description, String iconItem) {
        ItemStack icon() {
            Identifier key=Identifier.tryParse(iconItem==null?"minecraft:emerald":iconItem);
            var item=key==null?Items.EMERALD:BuiltInRegistries.ITEM.getValue(key);
            return (item==null||item==Items.AIR?Items.EMERALD:item).getDefaultInstance();
        }
    }
    private static final Gson GSON=new Gson();
    private static final int MUTED=0xFF735A40, LINE=0xFFC7AB7C, CREAM=0xFFFFF2D7, GREEN=0xFF325B44;
    private static final long TRAVEL_MILLIS=550;
    private final Screen returnTo;
    private final BlockPos table;
    private Run run, arrival;
    private Page page=Page.JOURNEY, returnPage=Page.JOURNEY;
    private List<DeckEntry> entries=List.of();
    private List<DeckStack> deckStacks=List.of();
    private final Map<String,Node> nodes=new LinkedHashMap<>();
    private final List<ChoiceButton> choiceButtons=new ArrayList<>();
    private final List<HeaderIcon> headerIcons=new ArrayList<>();
    private Button primary, deckButton;
    private boolean waiting, focusMap=true, wideMap;
    private Float resizeCenter;
    private int selected=-1, offerPage, decisionOffset, decisionRows, decisionRight;
    private int deckScroll,deckScrollMax,deckBottom;
    private int x,y,w,h,left,right,bodyTop,bodyBottom,footer,headerBottom,headerOffset,headerCapacity;
    private int mapLeft,mapRight,mapTop,mapBottom,mapHeight,mapWidth,mapScroll,mapScrollX,laneCount;
    private double zoom=1;
    private boolean fitOverview;
    private Double zoomCenterY,zoomCenterX;
    private boolean draggingMap;
    private Node previewNode, travelFrom, travelTo;
    private String pendingTravel;
    private long travelStarted, arrivedAt;
    private int detailScroll, detailMax;
    private static long now() { return System.nanoTime()/1_000_000L; }

    private TrialsScreen(BlockPos table,Screen returnTo) {
        super(Component.literal("The Trials"));
        this.table=table; this.returnTo=returnTo;
    }

    public static void receive(Minecraft mc,TrialsStatePayload payload) {
        Run next=GSON.fromJson(payload.json(),Run.class);
        if(next==null||next.phase==null)return;
        Screen current=mc.gui.screen();
        boolean existing=current instanceof TrialsScreen book && book.table.equals(payload.table());
        TrialsScreen book=existing?(TrialsScreen)current:new TrialsScreen(payload.table(),
                current instanceof AlternateModesScreen?current:null);
        if(existing && java.util.Objects.equals(book.run.runId,next.runId) && next.revision<book.run.revision)return;
        if(existing && book.pendingTravel!=null && book.pendingTravel.equals(next.activeNode) && next.phase!=Phase.MAP) {
            book.arrival=next;
            book.travelStarted=now();
            book.travelFrom=book.nodes.get(book.run.activeNode);
            book.travelTo=book.nodes.get(book.pendingTravel);
            book.disableButtons();
            mc.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                    net.minecraft.sounds.SoundEvents.BOOK_PAGE_TURN,0.85F,0.25F));
            return;
        }
        book.apply(next);
        if(next.afterBattle) {
            MatchScreen match=current instanceof MatchScreen m?m:current instanceof MatchPauseScreen p?p.match:null;
            if(match!=null)match.returnToAfterResult(book);
            return;
        }
        if(existing)book.rebuildWidgets(); else mc.gui.setScreen(book);
    }
    private void apply(Run next) {
        run=next; waiting=false; pendingTravel=null; arrival=null; selected=-1; page=Page.JOURNEY;
        nodes.clear();
        if(next.map!=null)for(Node node:next.map)nodes.put(node.id,node);
        previewNode=nodes.get(next.activeNode);
        if(next.phase==Phase.MAP)previewNode=nodes.values().stream().filter(n->"OPEN".equals(n.status)).findFirst().orElse(previewNode);
        focusMap=true; deckScroll=0; decisionOffset=0; offerPage=0; detailScroll=0;
        arrivedAt=now();
        var deck=new ArrayList<DeckEntry>();
        if(next.deck!=null)for(int slot=0;slot<next.deck.size();slot++) {
            Identifier id=Identifier.tryParse(next.deck.get(slot));
            if(id==null)continue;
            var card=Cards.get(id);
            TrialTraining training=TrialTraining.fromName(next.training!=null&&slot<next.training.size()?next.training.get(slot):"NONE");
            deck.add(new DeckEntry(id,slot,card==null?-1:card.cost(),training));
        }
        deck.sort(Comparator.comparingInt(DeckEntry::cost).thenComparing(e->cardName(e.id)).thenComparingInt(DeckEntry::slot));
        entries=deck;
        var stacks=new LinkedHashMap<DeckGroup,List<Integer>>();
        for(DeckEntry entry:entries)stacks.computeIfAbsent(new DeckGroup(entry.id,entry.cost,entry.training),key->new ArrayList<>()).add(entry.slot);
        deckStacks=stacks.entrySet().stream().map(entry->new DeckStack(entry.getKey(),List.copyOf(entry.getValue()))).toList();
        headerOffset=Math.max(0,headerItems().size()-Math.max(1,headerCapacity));
    }
    public boolean travelling() { return arrival!=null; }
    @Override public void tick() {
        if(arrival!=null && now()-travelStarted>=TRAVEL_MILLIS) {
            apply(arrival); rebuildWidgets();
            minecraft.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                    net.minecraft.sounds.SoundEvents.AMETHYST_BLOCK_CHIME,1.3F,0.18F));
        }
        super.tick();
    }

    private static String currency(int count) { return count+(count==1?" Emerald":" Emeralds"); }
    private static String cardName(Identifier id) { return Cards.get(id)==null?id.getPath().replace('_',' '):Cards.name(id).getString(); }
    private String className(CardClass type) { return type==null?"":type.displayName().getString(); }
    private int deckSize() { return run.deck==null?0:run.deck.size(); }
    private boolean hasRun() { return run.runId!=null&&!run.runId.isBlank(); }
    private boolean terminal() { return run.phase==Phase.VICTORY||run.phase==Phase.DEFEAT; }
    private boolean deckOffers() { return switch(run.phase){case REMOVE,COPY,TRAIN_CARD,TRADE_OUT->true;default->false;}; }
    private boolean cardOffers() { return switch(run.phase){case DRAFT,REWARD,SHOP_CARDS,TRADE_IN->true;default->false;}; }
    private boolean picking() { return switch(run.phase){case READY,PAUSED,VICTORY,DEFEAT,UNAVAILABLE->false;default->true;}; }
    private boolean decisions() { return picking()&&run.phase!=Phase.MAP&&!deckOffers()&&!cardOffers()&&run.phase!=Phase.CLASS; }
    private boolean isMap() { return page==Page.MAP||page==Page.JOURNEY&&run.phase==Phase.MAP; }
    private List<String> choices() { return run.choices==null?List.of():run.choices; }
    private List<String> charms() { return run.charms==null?List.of():run.charms; }
    private List<Charm> headerItems() {
        var items=new ArrayList<Charm>();
        if(run.acquiredBoons!=null)items.addAll(run.acquiredBoons);
        for(String id:charms())items.add(charm(id));
        return items;
    }
    private boolean canRetire() { return hasRun()&&!terminal()&&run.phase!=Phase.PAUSED&&page!=Page.RETIRE; }
    private int footerRight() { return right-(canRetire()?font.width("Retire")+22:0); }
    private Offer offer(int index) { return run.menuItems!=null&&index>=0&&index<run.menuItems.size()?run.menuItems.get(index):null; }
    private Charm charm(String id) {
        if(run.charmDefinitions!=null)for(Charm c:run.charmDefinitions)if(c.id.equals(id))return c;
        return new Charm(id,id,"","minecraft:emerald");
    }
    private boolean canPick() {
        if(selected<0)return false;
        if(deckOffers())return selected<deckSize()&&(run.eligibleIndices==null||run.eligibleIndices.contains(selected));
        return selected<choices().size()&&(offer(selected)==null||offer(selected).enabled);
    }
    private void show(Page next) {
        if(waiting||travelling())return;
        returnPage=page; page=next; deckScroll=0; detailScroll=0; rebuildWidgets();
    }
    private void back() {
        page=returnPage==Page.DECK||returnPage==Page.RETIRE||returnPage==Page.NODE?Page.JOURNEY:returnPage;
        returnPage=Page.JOURNEY; rebuildWidgets();
    }
    private void journey() { page=Page.JOURNEY; rebuildWidgets(); }

    @Override public void resize(int width,int height) {
        if(run!=null && isMap() && mapHeight>0 && (width!=this.width || height!=this.height)) {
            if(previewNode!=null && mapNodeBounds(previewNode.id)!=null)focusMap=true;
            else resizeCenter=mapScroll+(mapBottom-mapTop)/2f;
        }
        super.resize(width,height);
    }

    @Override protected void init() {
        w=Math.min(width-8,850);h=Math.min(height-8,620);
        x=(width-w)/2;y=(height-h)/2;left=x+11;right=x+w-11;
        headerBottom=y+48+(headerItems().isEmpty()?0:23);
        footer=y+h-25;bodyTop=headerBottom+(isMap()?5:24);bodyBottom=footer-7;
        primary=null;deckButton=null;choiceButtons.clear();headerIcons.clear();
        if(hasRun()) {
            String label="Deck ("+deckSize()+")";int bw=font.width(label)+12;
            deckButton=button(right-bw,y+7,bw,label,()->{if(page==Page.DECK)back();else show(Page.DECK);});
            buildHeaderIcons();
        }
        if(page==Page.RETIRE) {
            button(left,footer,75,"Keep playing",this::back);
            primary=button(right-82,footer,82,"Retire run",()->send("retire",-1));return;
        }
        if(canRetire())button(right-font.width("Retire")-12,footer,font.width("Retire")+12,"Retire",()->show(Page.RETIRE));
        if(page==Page.DECK){buildDeck(false);return;}
        if(page==Page.NODE) {
            button(left,footer,78,"Back to map",this::back);
            buildPrimary("Travel",()->send("pick",selected));
            primary.active=run.phase==Phase.MAP&&canPick()&&!waiting;return;
        }
        if(isMap())buildMap();
        else if(deckOffers())buildDeck(true);
        else if(picking())buildChoices();
        int pos=left;
        pos+=footerButton(pos,page==Page.MAP?"Back to room":"Close",page==Page.MAP?this::journey:this::onClose);
        if(page==Page.MAP)return;
        if(run.phase!=Phase.MAP&&run.phase!=Phase.CLASS&&run.phase!=Phase.DRAFT&&!nodes.isEmpty())
            pos+=footerButton(pos,"Map",()->show(Page.MAP));
        String exit=switch(run.phase){
            case REWARD,ELITE_PRIZE->"Skip";case EVENT,REST,SHOP->"Leave";
            case SHOP_CARDS,SHOP_CHARMS,SERVICES,REPLACE,REMOVE,COPY,TRAIN_CARD,TRAIN_TYPE,TRADE_OUT,TRADE_IN,BOON->"Back";
            default->"";};
        if(!exit.isEmpty())footerButton(pos,exit,()->send("leave",-1));
        String label=switch(run.phase){
            case CLASS->"Choose class";case DRAFT,REWARD->"Take card";case MAP->"Travel";case READY->"Enter trial";
            case SHOP_CARDS,SHOP_CHARMS->"Buy";case ELITE_PRIZE,REPLACE->"Take charm";case TRAIN_CARD->"Choose copy";
            case TRAIN_TYPE->"Train card";case COPY->"Duplicate";case REMOVE->"Remove";case TRADE_OUT->"Offer card";
            case TRADE_IN->"Swap card";case BOON->"Take boon";case VICTORY,DEFEAT->"New run";
            case UNAVAILABLE->"Check again";case PAUSED->"";default->"Choose";};
        if(!label.isBlank()){
            buildPrimary(label,this::commit);
            primary.active=!waiting&&(!picking()||canPick());
        }
        if(waiting||travelling())disableButtons();
    }
    private void buildPrimary(String label,Runnable action) {
        int bw=font.width(label)+16;primary=button(footerRight()-bw,footer,bw,label,action);
    }
    private int footerButton(int xx,String label,Runnable action) {
        int bw=font.width(label)+12;button(xx,footer,bw,label,action);return bw+4;
    }
    private void disableButtons() {
        children().stream().filter(Button.class::isInstance).map(Button.class::cast).forEach(b->b.active=false);
    }
    private void choose(int index) {
        if(waiting||travelling())return;selected=index;detailScroll=0;
        if(primary!=null)primary.active=canPick();
    }
    private void commit() {
        if(picking()){if(canPick())send("pick",selected);}
        else if(run.phase==Phase.READY)send("battle",-1);
        else if(terminal())send("new",-1);
        else if(run.phase==Phase.UNAVAILABLE)send("open",-1);
    }
    private void send(String action,int index) {
        if(waiting||travelling())return;
        if("pick".equals(action)&&run.phase==Phase.MAP&&index>=0&&index<choices().size()) {
            pendingTravel=choices().get(index);page=Page.JOURNEY;previewNode=nodes.get(pendingTravel);
        }
        waiting=true;
        if(pendingTravel!=null)rebuildWidgets();
        disableButtons();
        ClientPlayNetworking.send(new TrialsActionPayload(table,run.runId==null?"":run.runId,run.revision,action,index));
    }

    private void buildHeaderIcons() {
        List<Charm> items=headerItems();
        if(items.isEmpty())return;
        int plainCapacity=Math.max(1,(right-left)/19);
        boolean overflow=items.size()>plainCapacity;
        headerCapacity=Math.max(1,(right-left-(overflow?30:0))/19);
        headerOffset=Math.clamp(headerOffset,0,Math.max(0,items.size()-headerCapacity));
        int start=left+(overflow?15:0),top=y+50;
        if(overflow) {
            button(left,top,12,"<",()->scrollHeader(-3)).active=headerOffset>0;
            button(right-12,top,12,">",()->scrollHeader(3)).active=headerOffset+headerCapacity<items.size();
        }
        for(int index=headerOffset;index<Math.min(items.size(),headerOffset+headerCapacity);index++) {
            Charm item=items.get(index);
            var text=new ArrayList<Component>();
            text.add(Component.literal(item.title).withStyle(ChatFormatting.BOLD));
            text.add(Component.literal(item.description));
            if(overflow)text.add(Component.literal("Scroll this row to see more.").withStyle(ChatFormatting.GRAY));
            headerIcons.add(new HeaderIcon(start+(index-headerOffset)*19,top,item.icon(),text,""));
        }
    }
    private void scrollHeader(int amount) {
        if(waiting||travelling())return;
        int next=Math.clamp(headerOffset+amount,0,Math.max(0,headerItems().size()-headerCapacity));
        if(next!=headerOffset){headerOffset=next;rebuildWidgets();}
    }
    private String choiceName(int index) {
        String value=choices().get(index);
        if(run.phase==Phase.CLASS)return className(CardClass.valueOf(value));
        if(cardOffers()){Identifier id=Identifier.tryParse(value);return id==null?value:cardName(id);}
        return offer(index)==null?value:offer(index).title;
    }
    private static net.minecraft.world.item.Item optionIcon(String id) {
        return switch(id){
            case "BUY_CARDS","TRADE"->Items.BOOK;case "BUY_CHARMS","MARKS"->Items.EMERALD;
            case "SERVICES","TRAIN","TEMPER"->Items.ANVIL;case "REMOVE"->Items.GRINDSTONE;
            case "DUPLICATE","COPY"->Items.WRITABLE_BOOK;case "SHIELD"->Items.SHIELD;case "SPRINT"->Items.LEATHER_BOOTS;
            case "HEAL","REST","HEART"->Items.GOLDEN_APPLE;case "BOON","PACK"->Items.BUNDLE;
            case "TORCH"->Items.TORCH;case "SKIP"->Items.LEATHER_BOOTS;default->Items.MAP;
        };
    }
    private ItemStack menuIcon(Offer offer,String key) {
        if(offer!=null&&offer.iconItem!=null){
            Identifier id=Identifier.tryParse(offer.iconItem);
            if(id!=null&&BuiltInRegistries.ITEM.containsKey(id))return BuiltInRegistries.ITEM.getValue(id).getDefaultInstance();
        }
        if(run.phase==Phase.SHOP_CHARMS||run.phase==Phase.ELITE_PRIZE||run.phase==Phase.REPLACE)return charm(key).icon();
        return optionIcon(key).getDefaultInstance();
    }

    private void buildChoices() {
        if(decisions()){buildDecisions();return;}
        if(choices().isEmpty())return;
        int pages=(choices().size()+2)/3;
        offerPage=Math.clamp(offerPage,0,pages-1);
        if(pages>1){
            button(right-69,headerBottom+2,32,"Prev",()->{offerPage--;rebuildWidgets();}).active=offerPage>0;
            button(right-35,headerBottom+2,35,"Next",()->{offerPage++;rebuildWidgets();}).active=offerPage+1<pages;
        }
        int count=Math.min(3,choices().size()-offerPage*3),gap=w<350?4:8;
        int cw=Math.min(170,(right-left-gap*(count-1))/count);
        int bottom=contentBottom();
        int ch=Math.min(bottom-bodyTop,cardOffers()?240:210);
        int xx=(width-(cw*count+gap*(count-1)))/2,yy=bodyTop+(bottom-bodyTop-ch)/2;
        for(int i=0;i<count;i++)choiceButtons.add(addRenderableWidget(new ChoiceButton(xx+i*(cw+gap),yy,cw,ch,offerPage*3+i)));
    }
    private final class ChoiceButton extends Button {
        final int index;
        final Identifier cardId;
        final ItemStack icon;
        final String name,detail,caption;
        ChoiceButton(int xx,int yy,int ww,int hh,int index){
            super(xx,yy,ww,hh,Component.literal(choiceName(index)),b->choose(index),supplier -> supplier.get());
            this.index=index;name=getMessage().getString();
            String value=choices().get(index);Offer entry=offer(index);
            cardId=cardOffers()?Identifier.tryParse(value):null;
            if(run.phase==Phase.CLASS){
                var power=HeroPowers.of(CardClass.valueOf(value));
                icon=BuiltInRegistries.ITEM.getValue(power.icon()).getDefaultInstance();
                caption=power.displayName().getString()+" · 2 XP";detail=power.description().getString();
            }else{
                icon=cardId==null||Cards.get(cardId)==null?ItemStack.EMPTY:CardItem.of(cardId);
                caption=entry!=null&&entry.price>0?currency(entry.price):"";detail="";
            }
        }
        @Override protected void extractContents(GuiGraphicsExtractor g,int mx,int my,float tick){
            int xx=getX(),yy=getY(),ww=getWidth(),hh=getHeight(),cx=xx+ww/2;
            boolean chosen=selected==index;
            g.fill(xx,yy,xx+ww,yy+hh,chosen?0xFFFFEBC5:0xFFE5D1AA);
            g.outline(xx,yy,ww,hh,chosen?GREEN:isHoveredOrFocused()?WOOD:LINE);
            if(chosen)g.outline(xx+1,yy+1,ww-2,hh-2,GREEN);
            if(cardId!=null&&!icon.isEmpty()){
                float scale=Math.min((ww-8f)/CardRenderer.WIDTH,(hh-23f)/CardRenderer.HEIGHT);
                int cardHeight=Math.round(CardRenderer.HEIGHT*scale);
                CardRenderer.draw(g,font,icon,cx-Math.round(CardRenderer.WIDTH*scale)/2,yy+3+(hh-21-cardHeight)/2,scale);
            }else{
                g.item(icon,cx-8,yy+8);
                centered(g,name,cx,yy+29,ww-8,TEXT_DARK,true);
                paragraph(g,caption,xx+5,yy+43,ww-10,yy+64);
                paragraph(g,detail,xx+6,yy+69,ww-12,yy+hh-20);
            }
            int band=yy+hh-16;
            g.fill(xx+3,band,xx+ww-3,yy+hh-3,chosen?GREEN:0xFF71563F);
            Offer entry=offer(index);
            String label=entry!=null&&!entry.enabled?"Unavailable":chosen?"Selected":"Select";
            if(cardOffers()&&!caption.isBlank())label+=" · "+caption;
            centered(g,label,cx,band+3,ww-10,CREAM,false);
        }
    }

    private void buildDecisions() {
        decisionRight=left+Math.min(230,(right-left)*45/100);
        decisionRows=Math.max(1,(bodyBottom-bodyTop-20)/21);
        int pages=Math.max(1,(choices().size()+decisionRows-1)/decisionRows);
        decisionOffset=Math.clamp(decisionOffset,0,pages-1);
        for(int row=0;row<decisionRows&&decisionOffset*decisionRows+row<choices().size();row++){
            int i=decisionOffset*decisionRows+row,yy=bodyTop+row*21,ww=decisionRight-left;
            Offer offer=offer(i);ItemStack icon=menuIcon(offer,choices().get(i));
            addRenderableWidget(new Button(left,yy,ww,20,Component.literal(choiceName(i)),b->choose(i),supplier -> supplier.get()){
                @Override protected void extractContents(GuiGraphicsExtractor g,int mx,int my,float tick){
                    boolean enabled=offer==null||offer.enabled,chosen=selected==i;
                    g.fill(left,yy,left+ww,yy+20,chosen?0xFFFFEBC5:enabled?0xFFE4D0AC:0xFFD5C6AA);
                    g.outline(left,yy,ww,20,chosen?GREEN:isHoveredOrFocused()?WOOD:LINE);
                    g.item(icon,left+2,yy+2);
                    CardRenderer.fitText(g,font,getMessage(),left+22,yy+6,ww-26,enabled?TEXT_DARK:MUTED,false,false);
                }
            });
        }
        if(pages>1){
            button(left,bodyBottom-18,32,"Prev",()->{decisionOffset--;rebuildWidgets();}).active=decisionOffset>0;
            button(decisionRight-35,bodyBottom-18,35,"Next",()->{decisionOffset++;rebuildWidgets();}).active=decisionOffset+1<pages;
        }
    }
    private void drawDecision(GuiGraphicsExtractor g) {
        if(choices().isEmpty())return;
        int i=selected>=0?selected:Math.min(choices().size()-1,decisionOffset*decisionRows);
        Offer offer=offer(i);
        int xx=decisionRight+8,ww=right-xx;
        g.fill(xx,bodyTop,right,bodyBottom,0xFFEBDCBB);g.outline(xx,bodyTop,ww,bodyBottom-bodyTop,LINE);
        var text=new ArrayList<Component>();
        Node current=nodes.get(run.activeNode);
        if((run.phase==Phase.EVENT||run.phase==Phase.REST||run.phase==Phase.SHOP)&&current!=null&&current.description!=null&&!current.description.isBlank()){
            text.add(Component.literal(current.description));
            text.add(Component.empty());
        }
        text.add(Component.literal(choiceName(i)).withStyle(ChatFormatting.BOLD));
        if(offer!=null&&offer.price>0)text.add(Component.literal(currency(offer.price)).withStyle(ChatFormatting.DARK_GREEN));
        if(offer!=null&&offer.description!=null)text.add(Component.literal(offer.description));
        text.add(Component.empty());
        if(offer!=null&&!offer.enabled)text.add(Component.literal("Unavailable").withStyle(ChatFormatting.DARK_RED));
        else if(selected<0)text.add(Component.literal("Select an option on the left.").withStyle(ChatFormatting.DARK_GRAY));
        drawInfoText(g,text,xx+7,bodyTop+8,ww-14,bodyBottom-6);
    }

    private void drawInfoText(GuiGraphicsExtractor g,List<Component> text,int xx,int yy,int ww,int bottom) {
        var lines=new ArrayList<net.minecraft.util.FormattedCharSequence>();
        for(Component part:text) {
            if(part.getString().isEmpty())lines.add(net.minecraft.util.FormattedCharSequence.EMPTY);
            else lines.addAll(font.split(part,Math.max(10,ww-4)));
        }
        int lineHeight=font.lineHeight+2,available=Math.max(1,bottom-yy-12);
        detailMax=Math.max(0,lines.size()*lineHeight-available);
        detailScroll=Math.clamp(detailScroll,0,detailMax);
        g.enableScissor(xx,yy,xx+ww,bottom-12);
        for(int i=0;i<lines.size();i++){
            int top=yy+i*lineHeight-detailScroll;
            if(top+font.lineHeight>=yy&&top<bottom-12)g.text(font,lines.get(i),xx,top,TEXT_DARK,false);
        }
        g.disableScissor();
        if(detailMax>0){
            int thumb=Math.max(8,available*available/(available+detailMax));
            int top=yy+detailScroll*(available-thumb)/detailMax;
            g.fill(xx+ww-1,top,xx+ww+1,top+thumb,MUTED);
            centered(g,"Scroll for more",xx+ww/2,bottom-9,ww,MUTED,false);
        }
    }

    private boolean hasCurve() {
        return page==Page.DECK || page==Page.JOURNEY&&(cardOffers()||deckOffers());
    }
    private int curveHeight() { return height>=350?58:46; }
    private int curveTop() { return bodyBottom-curveHeight(); }
    private int contentBottom() { return hasCurve()?curveTop()-6:bodyBottom; }
    public int[] deckViewport() { return new int[]{left,bodyTop,right-left,Math.max(1,deckBottom-bodyTop)}; }
    private boolean insideDeck(double xx,double yy) {
        return xx>=left&&xx<right&&yy>=bodyTop&&yy<deckBottom;
    }
    private void buildDeck(boolean choosing) {
        int columns=5,gap=w<350?3:7;
        deckBottom=contentBottom();
        int cw=(right-left-gap*(columns-1))/columns;
        int ch=Math.max(60,Math.min(260,Math.round((cw-10)*CardRenderer.HEIGHT/(float)CardRenderer.WIDTH)+31));
        int rows=(deckStacks.size()+columns-1)/columns;
        deckScrollMax=Math.max(0,rows*(ch+gap)-gap-(deckBottom-bodyTop));
        deckScroll=Math.clamp(deckScroll,0,deckScrollMax);
        for(int i=0;i<deckStacks.size();i++) {
            DeckStack stack=deckStacks.get(i);
            int xx=left+(i%columns)*(cw+gap),yy=bodyTop+(i/columns)*(ch+gap)-deckScroll;
            if(yy+ch<=bodyTop||yy>=deckBottom)continue;
            int slot=stack.slots.stream().filter(index->!choosing||run.eligibleIndices==null||run.eligibleIndices.contains(index))
                    .findFirst().orElse(stack.slots.getFirst());
            boolean allowed=!choosing||run.eligibleIndices==null||run.eligibleIndices.contains(slot);
            addRenderableWidget(new DeckTile(xx,yy,cw,ch,stack,slot,allowed,choosing));
        }
        if(!choosing)button(left,footer,82,"Back to Trials",this::back);
    }
    /** Counts physical copies, keeping all preview changes local to this screen. */
    public TrialsDeckCurve.Snapshot deckCurve() { return curveFor(selected); }
    private TrialsDeckCurve.Snapshot curveFor(int candidate) {
        Integer add=null,remove=null;
        if(page==Page.JOURNEY&&cardOffers()&&candidate>=0&&candidate<choices().size()) {
            var id=Identifier.tryParse(choices().get(candidate));
            var card=id==null?null:Cards.get(id);
            if(card!=null) {
                add=card.cost();
                if(run.phase==Phase.TRADE_IN) {
                    var old=Identifier.tryParse(run.tradeOut==null?"":run.tradeOut);
                    var oldCard=old==null?null:Cards.get(old);
                    if(oldCard!=null)remove=oldCard.cost();
                }
            }
        } else if(page==Page.JOURNEY&&deckOffers()&&candidate>=0) {
            var card=entries.stream().filter(e->e.slot==candidate).findFirst().orElse(null);
            if(card!=null) {
                if(run.phase==Phase.COPY)add=card.cost;
                if(run.phase==Phase.REMOVE||run.phase==Phase.TRADE_OUT)remove=card.cost;
            }
        }
        return TrialsDeckCurve.of(entries.stream().map(DeckEntry::cost).toList(),add,remove);
    }
    private void drawCurve(GuiGraphicsExtractor g,int mx,int my) {
        int candidate=selected;
        if(page==Page.JOURNEY) {
            for(ChoiceButton choice:choiceButtons)if(choice.cardId!=null&&choice.isMouseOver(mx,my))candidate=choice.index;
            for(var child:children())if(child instanceof DeckTile tile&&tile.isMouseOver(mx,my))candidate=tile.slot;
        }
        var curve=curveFor(candidate);
        int top=curveTop(),hh=curveHeight(),baseline=bodyBottom-11,plotTop=top+22;
        g.fill(left,top,right,bodyBottom,0xFFF1E0BD);
        g.outline(left,top,right-left,hh,LINE);
        String title="XP curve · "+curve.size()+" cards · Avg "+String.format(java.util.Locale.ROOT,"%.1f",curve.average());
        if(curve.changed())title="XP curve · "+curve.size()+" → "+curve.projectedSize()+" cards · Avg "+
                String.format(java.util.Locale.ROOT,"%.1f → %.1f",curve.average(),curve.projectedAverage());
        CardRenderer.fitText(g,font,Component.literal(title),left+5,top+4,right-left-10,TEXT_DARK,false,false);
        if(curve.changed()) {
            g.fill(left+6,top+15,left+11,top+20,GREEN);
            CardRenderer.fitText(g,font,Component.literal("Current"),left+14,top+14,44,MUTED,false,false);
            g.fill(left+63,top+15,left+68,top+20,0xFF91C763);
            CardRenderer.fitText(g,font,Component.literal("Added"),left+71,top+14,38,MUTED,false,false);
            g.fill(left+113,top+15,left+118,top+20,0xFFB46A56);
            CardRenderer.fitText(g,font,Component.literal("Removed"),left+121,top+14,52,MUTED,false,false);
        }
        int max=1;
        for(int count:curve.current())max=Math.max(max,count);
        for(int count:curve.projected())max=Math.max(max,count);
        for(int i=0;i<TrialsDeckCurve.BUCKETS;i++) {
            int cellLeft=left+5+(right-left-10)*i/TrialsDeckCurve.BUCKETS;
            int cellRight=left+5+(right-left-10)*(i+1)/TrialsDeckCurve.BUCKETS;
            int cx=(cellLeft+cellRight)/2,barLeft=cellLeft+3,barRight=Math.max(barLeft+1,cellRight-3);
            int current=curve.current()[i],after=curve.projected()[i],plotHeight=Math.max(2,baseline-plotTop);
            int currentY=baseline-(int)Math.round(current*plotHeight/(double)max);
            int afterY=baseline-(int)Math.round(after*plotHeight/(double)max);
            g.fill(barLeft,baseline,barRight,baseline+1,LINE);
            if(current>0)g.fill(barLeft,currentY,barRight,baseline,GREEN);
            if(after>current)g.fill(barLeft,afterY,barRight,currentY,0xFF91C763);
            if(after<current)g.fill(barLeft,currentY,barRight,afterY,0xFFB46A56);
            centered(g,i==10?"10+":Integer.toString(i),cx,baseline+2,cellRight-cellLeft-1,MUTED,false);
            if(mx>=cellLeft&&mx<cellRight&&my>=top+20&&my<bodyBottom) {
                String cost=i==10?"10+ XP":i+" XP";
                String amount=current+" "+(current==1?"card":"cards");
                if(after!=current)amount+=" → "+after+" after choice";
                Tooltips.show(g,font,List.of(Component.literal(cost).withStyle(ChatFormatting.BOLD),Component.literal(amount)),mx,my);
            }
        }
    }

    private final class DeckTile extends Button {
        final DeckStack stack;
        final int slot;
        final boolean allowed,choosing;
        DeckTile(int xx,int yy,int ww,int hh,DeckStack stack,int slot,boolean allowed,boolean choosing) {
            super(xx,yy,ww,hh,Component.literal("#"+(slot+1)+" "+cardName(stack.card.id)+" ×"+stack.slots.size()+
                    (stack.card.training==TrialTraining.NONE?"":" · "+stack.card.training.label())),b->{
                if(waiting||travelling())return;
                if(choosing){if(allowed)choose(slot);}
                else inspect(stack.card.id,stack.card.training);
            },supplier->supplier.get());
            this.stack=stack;this.slot=slot;this.allowed=allowed;this.choosing=choosing;
        }
        @Override public boolean isMouseOver(double mx,double my) {
            return insideDeck(mx,my)&&super.isMouseOver(mx,my);
        }
        @Override public boolean mouseClicked(MouseButtonEvent event,boolean twice) {
            if(!insideDeck(event.x(),event.y()))return false;
            if(!waiting&&!travelling()&&event.button()==InputConstants.MOUSE_BUTTON_RIGHT&&isMouseOver(event.x(),event.y())) {
                inspect(stack.card.id,stack.card.training);return true;
            }
            return super.mouseClicked(event,twice);
        }
        @Override protected void extractContents(GuiGraphicsExtractor g,int mx,int my,float tick) {
            int xx=getX(),yy=getY(),ww=getWidth(),hh=getHeight();
            boolean picked=choosing&&stack.slots.contains(selected),trained=stack.card.training!=TrialTraining.NONE;
            boolean hovered=isMouseOver(mx,my);
            g.enableScissor(left,bodyTop,right,deckBottom);
            g.fill(xx,yy,xx+ww,yy+hh,picked?0xFFF9E7BF:0xFFCCB58C);
            g.outline(xx,yy,ww,hh,picked?GREEN:isHoveredOrFocused()?WOOD:0xFFB29365);
            g.fill(xx+2,yy+2,xx+ww-2,yy+hh-2,0xFFE2CDA7);
            float scale=Math.min((ww-10f)/CardRenderer.WIDTH,(hh-31f)/CardRenderer.HEIGHT);
            int cardW=Math.round(CardRenderer.WIDTH*scale),cardH=Math.round(CardRenderer.HEIGHT*scale);
            int cardX=xx+(ww-cardW)/2,cardY=yy+4+(hh-31-cardH)/2;
            if(stack.slots.size()>1) {
                g.fill(cardX+3,cardY+3,cardX+cardW+3,cardY+cardH+3,0xFFA88B60);
                g.outline(cardX+3,cardY+3,cardW,cardH,0xFF7D623E);
            }
            CardRenderer.drawTrial(g,font,stack.card.id,false,stack.card.training,cardX,cardY,scale);
            if(Cards.get(stack.card.id)==null)paragraph(g,cardName(stack.card.id),xx+4,yy+8,ww-8,yy+hh-30);
            int band=yy+hh-25;
            if(trained) {
                g.fill(xx+3,band,xx+ww-3,band+11,0xFFDBE8BC);
                centered(g,stack.card.training.label()+" upgrade",xx+ww/2,band+2,ww-8,GREEN,true);
            }
            centered(g,"×"+stack.slots.size(),xx+ww/2,yy+hh-12,ww-8,TEXT_DARK,true);
            if(!allowed)g.fill(xx+2,yy+2,xx+ww-2,yy+hh-2,0x77524C42);
            g.disableScissor();
            if(hovered) {
                var text=new ArrayList<Component>();
                text.add(Component.literal(cardName(stack.card.id)).withStyle(ChatFormatting.BOLD));
                if(trained)text.add(Component.literal("Upgrade · "+stack.card.training.description(stack.card.id)).withStyle(ChatFormatting.GREEN));
                text.add(Component.literal(stack.slots.size()+" identical "+(stack.slots.size()==1?"copy":"copies")));
                text.add(Component.literal(choosing?(allowed?"Click to choose one copy.":"This copy is unavailable."):"Click to inspect."));
                if(choosing)text.add(Component.literal("Right-click to inspect."));
                Tooltips.show(g,font,text,mx,my);
            }
        }
    }
    private void inspect(Identifier id,TrialTraining training) {
        if(id!=null&&Cards.get(id)!=null)minecraft.gui.setScreen(new CardInspectScreen(CardItem.of(id),this,training));
    }

    private static net.minecraft.world.item.Item nodeIcon(String kind) {
        return switch(kind){case "FIGHT"->Items.IRON_SWORD;case "ELITE"->Items.GOLDEN_AXE;
            case "BOSS"->Items.OMINOUS_BOTTLE;case "SHOP"->Items.EMERALD;case "REST"->Items.CAMPFIRE;default->Items.MAP;};
    }
    private static String kindName(String kind) {
        return switch(kind){case "FIGHT"->"Battle";case "ELITE"->"Elite";case "BOSS"->"Boss";
            case "SHOP"->"Villager shop";case "REST"->"Camp";case "EVENT"->"Mystery";default->kind;};
    }
    private boolean mystery(Node node) {
        return "EVENT".equals(node.kind)&&!"DONE".equals(node.status)&&!"CURRENT".equals(node.status);
    }
    private String nodeTitle(Node node) { return mystery(node)?"Mystery":node.title; }
    private int nodeSize(Node node) {
        int overview=Math.clamp((mapBottom-mapTop-36)/Math.max(1,run.roomCount-1)-1,3,9);
        return Math.max("BOSS".equals(node.kind)?Math.max(5,overview+2):overview,
                (int)Math.round(("BOSS".equals(node.kind)?36:26)*Math.min(1.4,zoom)));
    }
    public double mapZoom() { return zoom; }
    public int[] mapViewport() { return new int[]{mapLeft,mapTop,mapRight-mapLeft,mapBottom-mapTop}; }
    private int baseMapHeight() { return Math.max(0,run.roomCount-1)*68+100; }
    private double minimumZoom() { return Math.min(0.5,Math.max(0.025,(mapBottom-mapTop-10.0)/baseMapHeight())); }
    private void zoomMap(double next) {
        if(!isMap()||waiting||travelling())return;
        next=Math.clamp(next,minimumZoom(),2);
        // The last minus step must reveal the whole route, just like Fit.
        // Otherwise the separate horizontal extent can leave branches off-screen.
        if(next<=minimumZoom()+0.001){fitMap();return;}
        if(Math.abs(next-zoom)<0.001)return;
        fitOverview=false;
        zoomCenterY=(mapScroll+(mapBottom-mapTop)/2.0)/mapHeight;
        zoomCenterX=(mapScrollX+(mapRight-mapLeft)/2.0)/mapWidth;
        zoom=next;focusMap=false;rebuildWidgets();
    }
    private void fitMap() {
        if(waiting||travelling())return;
        fitOverview=true;focusMap=false;resizeCenter=null;zoomCenterY=null;zoomCenterX=null;
        mapScroll=0;mapScrollX=0;rebuildWidgets();
    }
    private void focusReadable(Node node) {
        previewNode=node;selected=-1;detailScroll=0;fitOverview=false;zoom=1;
        focusMap=true;zoomCenterY=null;zoomCenterX=null;resizeCenter=null;rebuildWidgets();
    }
    private void buildMap() {
        wideMap=w>=460;mapLeft=left;mapRight=wideMap?right-Math.clamp(w/3,158,220)-8:right;
        mapTop=bodyTop+22;mapBottom=bodyBottom-(wideMap?0:26);
        if(fitOverview)zoom=minimumZoom();
        laneCount=1;
        for(Node node:nodes.values())laneCount=Math.max(laneCount,node.lane+1);
        mapHeight=Math.max(mapBottom-mapTop,(int)Math.round(baseMapHeight()*zoom));
        mapWidth=fitOverview?mapRight-mapLeft:Math.max(mapRight-mapLeft,(int)Math.round(Math.max(420,(mapRight-mapLeft)*1.4)*(0.65+0.35*zoom)));
        if(focusMap) {
            Node focus=previewNode!=null?previewNode:nodes.values().stream().findFirst().orElse(null);
            if(focus!=null) {
                mapScroll=Math.round(worldY(focus))-(mapBottom-mapTop)/2;
                mapScrollX=Math.round(worldX(focus))-(mapRight-mapLeft)/2;
            }
            focusMap=false;
        }
        if(resizeCenter!=null) { mapScroll=Math.round(resizeCenter)-(mapBottom-mapTop)/2;resizeCenter=null; }
        if(zoomCenterY!=null) {
            mapScroll=(int)Math.round(zoomCenterY*mapHeight)-(mapBottom-mapTop)/2;
            mapScrollX=(int)Math.round(zoomCenterX*mapWidth)-(mapRight-mapLeft)/2;
            zoomCenterY=null;zoomCenterX=null;
        }
        if(fitOverview){mapScroll=0;mapScrollX=0;}
        mapScroll=Math.clamp(mapScroll,0,maxMapScroll());
        mapScrollX=Math.clamp(mapScrollX,0,maxMapScrollX());
        Button zoomOut=button(mapLeft,bodyTop,19,"-",()->zoomMap(zoom/1.3));
        zoomOut.active=zoom>minimumZoom()+0.001;
        zoomOut.setTooltip(Tooltip.create(Component.literal("Zoom out. Keep clicking to see the whole route.")));
        Button zoomIn=button(mapLeft+23,bodyTop,19,"+",()->zoomMap(zoom*1.3));
        zoomIn.active=zoom<1.999;
        zoomIn.setTooltip(Tooltip.create(Component.literal("Zoom in for room details. Drag empty parchment to pan.")));
        button(mapLeft+46,bodyTop,27,"Fit",this::fitMap)
                .setTooltip(Tooltip.create(Component.literal("Show every path from the start to the boss.")));
        button(mapLeft+77,bodyTop,47,"Current",()->{
            Node current=nodes.get(run.activeNode);
            if(current==null)current=nodes.values().stream().filter(n->"OPEN".equals(n.status)).findFirst().orElse(null);
            if(current!=null)focusReadable(current);
        });
        button(mapLeft+128,bodyTop,33,"Boss",()->{
            Node boss=nodes.values().stream().filter(n->"BOSS".equals(n.kind)).max(Comparator.comparingInt(Node::row)).orElse(null);
            if(boss!=null)focusReadable(boss);
        });
        if(!wideMap)button(mapRight-47,bodyBottom-19,47,"Details",()->{if(previewNode!=null)show(Page.NODE);});
        for(Node node:nodes.values()){
            int[] bounds=mapNodeBounds(node.id);
            if(bounds==null)continue;
            String state=switch(node.status){case "OPEN"->"Open";case "DONE"->"Cleared";case "CURRENT"->"Current";default->"Preview";};
            String label=state+" room "+(node.row+1)+": "+nodeTitle(node)+" ["+kindName(node.kind)+"]";
            addRenderableWidget(new Button(bounds[0],bounds[1],bounds[2],bounds[3],Component.literal(label),b->{
                if(waiting||travelling())return;
                previewNode=node;detailScroll=0;selected=run.phase==Phase.MAP&&page!=Page.MAP?choices().indexOf(node.id):-1;
                if(primary!=null)primary.active=canPick();
            },supplier -> supplier.get()){
                @Override protected void extractContents(GuiGraphicsExtractor g,int mx,int my,float tick){
                    drawNode(g,node,getX(),getY(),getWidth(),isHoveredOrFocused());
                }
            });
        }
    }
    private int maxMapScroll() { return Math.max(0,mapHeight-(mapBottom-mapTop)); }
    private int maxMapScrollX() { return Math.max(0,mapWidth-(mapRight-mapLeft)); }
    private float worldY(Node node) {
        double amount=node.mapY!=null?node.mapY:run.roomCount<=1?1:(double)node.row/(run.roomCount-1);
        double topMargin=Math.max(28,50*zoom),bottomMargin=Math.max(12,50*zoom);
        return (float)(mapHeight-bottomMargin-Math.clamp(amount,0,1)*Math.max(1,mapHeight-topMargin-bottomMargin));
    }
    private float worldX(Node node) {
        double amount=node.mapX!=null?node.mapX:(node.lane+0.5)/laneCount;
        int inset=Math.min(40,mapWidth/5);
        return (float)(inset+Math.clamp(amount,0,1)*(mapWidth-2*inset));
    }
    private int nodeX(Node node) { return mapLeft+Math.round(worldX(node))-mapScrollX; }
    private int nodeY(Node node) { return mapTop+Math.round(worldY(node))-mapScroll; }
    private void panMap(int horizontal,int vertical) {
        if(!isMap()||waiting||travelling())return;
        int nextY=Math.clamp(mapScroll+vertical,0,maxMapScroll());
        int nextX=Math.clamp(mapScrollX+horizontal,0,maxMapScrollX());
        if(nextY!=mapScroll||nextX!=mapScrollX){mapScroll=nextY;mapScrollX=nextX;rebuildWidgets();}
    }
    /** Locate a room for keyboard navigation and real-client route verification. */
    public void focusMapNode(String id) {
        Node node=nodes.get(id);
        if(node==null||!isMap()||waiting||travelling())return;
        mapScroll=Math.clamp(Math.round(worldY(node))-(mapBottom-mapTop)/2,0,maxMapScroll());
        mapScrollX=Math.clamp(Math.round(worldX(node))-(mapRight-mapLeft)/2,0,maxMapScrollX());
        rebuildWidgets();
    }
    public int[] mapNodeBounds(String id) {
        Node node=nodes.get(id);
        if(node==null||!isMap())return null;
        int size=nodeSize(node),cy=nodeY(node),cx=nodeX(node);
        if(cy-size/2<mapTop+2||cy+size/2+(zoom>=0.55?14:2)>mapBottom||cx-size/2<mapLeft+2||cx+size/2>mapRight-2)return null;
        return new int[]{cx-size/2,cy-size/2,size,size};
    }
    /** Visible label bounds are separate from the icon hit box. At overview zoom only the boss is named. */
    public int[] mapNodeLabelBounds(String id) {
        Node node=nodes.get(id);
        int[] icon=mapNodeBounds(id);
        if(node==null||icon==null||zoom<0.55&&!"BOSS".equals(node.kind))return null;
        String label="BOSS".equals(node.kind)?nodeTitle(node):kindName(node.kind);
        int labelWidth=Math.min(font.width(label)+8,"BOSS".equals(node.kind)?Math.min(190,mapRight-mapLeft-12):
                Math.max(icon[2],Math.min(85,(mapRight-mapLeft-40)/Math.max(1,laneCount)-4)));
        int labelTop=zoom<0.55?icon[1]-13:icon[1]+icon[3]+3;
        if(labelTop<mapTop+2||labelTop+11>=mapBottom)return null;
        int cx=Math.clamp(icon[0]+icon[2]/2,mapLeft+labelWidth/2+4,mapRight-labelWidth/2-5);
        return new int[]{cx-labelWidth/2-2,labelTop-1,labelWidth+5,11};
    }
    private static boolean within(double x,double y,int[] rect) {
        return rect!=null&&x>=rect[0]&&x<rect[0]+rect[2]&&y>=rect[1]&&y<rect[1]+rect[3];
    }
    private void drawMap(GuiGraphicsExtractor g) {
        if(nodes.isEmpty())return;
        TrialsAtlasBackdrop.draw(g,mapLeft,mapTop,mapRight,mapBottom,mapScroll,run.runId==null?0:run.runId.hashCode());
        g.outline(mapLeft,mapTop,mapRight-mapLeft,mapBottom-mapTop,LINE);
        g.enableScissor(mapLeft+1,mapTop+1,mapRight-1,mapBottom-1);
        for(Node node:nodes.values())for(String id:node.next){
            Node target=nodes.get(id);if(target==null)continue;
            int from=nodeX(node),to=nodeX(target),a=nodeY(node),b=nodeY(target);
            if(Math.max(a,b)<mapTop||Math.min(a,b)>mapBottom)continue;
            boolean taken=("DONE".equals(node.status)||"CURRENT".equals(node.status))&&
                    ("DONE".equals(target.status)||"CURRENT".equals(target.status));
            boolean open="OPEN".equals(target.status)&&("CURRENT".equals(node.status)||"DONE".equals(node.status));
            int color=target==previewNode&&open?GREEN:open?0xFF6A8258:taken?0xFF8C6D3F:0xFFAA987B;
            path(g,from,a,to,b,0xFFDDC9A5,zoom<0.55?2:5,false);
            path(g,from,a,to,b,color,zoom<0.55?1:taken||open?2:1,!taken&&!open);
        }
        if(run.activeNode==null||run.activeNode.isBlank()){
            int markerY=mapTop+mapHeight-(int)Math.round(13*zoom)-mapScroll,cx=mapLeft+mapWidth/2-mapScrollX;
            for(Node node:nodes.values())if(node.row==0)path(g,cx,markerY,nodeX(node),nodeY(node),0xFF6A8258,2,false);
            g.fill(cx-3,markerY-3,cx+4,markerY+4,GREEN);
        }
        g.disableScissor();
        centered(g,Math.round(zoom*100)+"%",(mapLeft+160+mapRight)/2,bodyTop+5,mapRight-mapLeft-165,MUTED,false);
        if(previewNode!=null){
            if(wideMap)drawNodeInfo(g,previewNode,mapRight+8,bodyTop,right,bodyBottom,false);
            else{
                String status=switch(previewNode.status){case "OPEN"->"Open";case "CURRENT"->"Current";case "DONE"->"Cleared";default->"Ahead";};
                CardRenderer.fitText(g,font,Component.literal(nodeTitle(previewNode)),mapLeft,bodyBottom-22,mapRight-mapLeft-52,TEXT_DARK,false,false);
                g.text(font,Component.literal(status+" · Room "+(previewNode.row+1)),mapLeft,bodyBottom-10,MUTED,false);
            }
        }
    }
    private void path(GuiGraphicsExtractor g,int x1,int y1,int x2,int y2,int color,int thickness,boolean dashed) {
        int length=(int)Math.ceil(Math.hypot(x2-x1,y2-y1));
        g.pose().pushMatrix();g.pose().translate(x1,y1);g.pose().rotate((float)Math.atan2(y2-y1,x2-x1));
        if(dashed)for(int step=0;step<length;step+=8)g.fill(step,-thickness/2,Math.min(length,step+4),thickness-thickness/2,color);
        else g.fill(0,-thickness/2,length,thickness-thickness/2,color);
        g.pose().popMatrix();
    }
    private void drawNode(GuiGraphicsExtractor g,Node node,int xx,int yy,int size,boolean hovered) {
        boolean open="OPEN".equals(node.status),done="DONE".equals(node.status),here="CURRENT".equals(node.status),picked=node==previewNode;
        int cx=xx+size/2,cy=yy+size/2;
        int accent=switch(node.kind){case "BOSS"->0xFF783C35;case "ELITE"->0xFF94662E;case "REST"->GREEN;
            case "SHOP"->0xFF497D70;case "EVENT"->0xFF75537E;default->0xFF5E665C;};
        int pulse=size>=14&&open&&Math.sin(now()/390.0)>0.4?1:0;
        g.fill(xx-1,yy+2,xx+size+1,yy+size+2,0xFFB5A180);
        g.fill(xx,yy,xx+size,yy+size,done?0xFFCEBC96:here?0xFFFFE3A0:open?0xFFF7ECCF:0xFFE3D3B4);
        g.outline(xx,yy,size,size,accent);
        if(size>=14&&(open||picked||hovered||here))
            g.outline(xx-2-pulse,yy-2-pulse,size+4+pulse*2,size+4+pulse*2,here?0xFFAC792D:picked||open?GREEN:WOOD);
        if(size<14&&(picked||hovered))g.outline(xx-1,yy-1,size+2,size+2,GREEN);
        if(mystery(node)||size<8) {
            String glyph=mystery(node)?"?":switch(node.kind){case "SHOP"->"S";case "REST"->"C";case "ELITE"->"E";case "BOSS"->"!";default->"B";};
            float scale=Math.min(1,(size-2)/9F);
            g.pose().pushMatrix();g.pose().translate(cx-font.width(glyph)*scale/2,cy-4*scale);g.pose().scale(scale,scale);
            g.text(font,Component.literal(glyph).withStyle(ChatFormatting.BOLD),0,0,accent,false);g.pose().popMatrix();
        } else {
            float scale=Math.min(size-(size<14?2:6),("BOSS".equals(node.kind)?24:16))/16F;
            g.pose().pushMatrix();g.pose().translate(cx-8*scale,cy-8*scale);g.pose().scale(scale,scale);
            g.item(nodeIcon(node.kind).getDefaultInstance(),0,0);g.pose().popMatrix();
        }
        if(done&&size>=14){
            g.fill(xx+size-6,yy+1,xx+size-3,yy+4,GREEN);
            g.fill(xx+size-3,yy-2,xx+size,yy+4,GREEN);
        }
        if(here)g.fill(cx-2,yy+size-1,cx+3,yy+size+3,0xFFBF8229);
        int[] labelBounds=mapNodeLabelBounds(node.id);
        if(labelBounds!=null) {
            String label="BOSS".equals(node.kind)?nodeTitle(node):kindName(node.kind);
            g.fill(labelBounds[0],labelBounds[1],labelBounds[0]+labelBounds[2],labelBounds[1]+labelBounds[3],0xFFE8D6B3);
            centered(g,label,labelBounds[0]+labelBounds[2]/2,labelBounds[1]+2,labelBounds[2]-5,accent,"BOSS".equals(node.kind));
        }
    }
    private void drawTravel(GuiGraphicsExtractor g) {
        if(travelTo==null)return;
        float t=Math.clamp((now()-travelStarted)/(float)TRAVEL_MILLIS,0,1);
        float eased=t*t*(3-2*t);
        int tx=nodeX(travelTo),ty=nodeY(travelTo);
        int fx=travelFrom==null?mapLeft+mapWidth/2-mapScrollX:nodeX(travelFrom);
        int fy=travelFrom==null?mapTop+mapHeight-(int)Math.round(13*zoom)-mapScroll:nodeY(travelFrom);
        int xx=Math.round(fx+(tx-fx)*eased),yy=Math.round(fy+(ty-fy)*eased);
        g.enableScissor(mapLeft,mapTop,mapRight,mapBottom);
        path(g,fx,fy,xx,yy,GREEN,3,false);
        g.fill(xx-9,yy-9,xx+9,yy+9,CREAM);g.outline(xx-9,yy-9,18,18,GREEN);
        g.item(Items.LEATHER_BOOTS.getDefaultInstance(),xx-8,yy-8);
        g.disableScissor();
    }
    private void drawNodeInfo(GuiGraphicsExtractor g,Node node,int ll,int tt,int rr,int bb,boolean battle) {
        int ww=rr-ll,cx=(rr+ll)/2;
        g.fill(ll,tt,rr,bb,0xFFE8D6B3);g.outline(ll,tt,ww,bb-tt,LINE);
        int shade=switch(node.kind){case "BOSS","ELITE"->0xFF7E3E34;case "REST","SHOP"->GREEN;default->0xFF624730;};
        g.fill(ll+1,tt+1,rr-1,tt+16,shade);
        String state=switch(node.status){case "DONE"->"Cleared";case "CURRENT"->"Here";case "OPEN"->"Open";default->"Ahead";};
        centered(g,kindName(node.kind)+" · "+state,cx,tt+4,ww-6,CREAM,true);
        paragraph(g,nodeTitle(node),ll+6,tt+22,ww-12,tt+44);
        StringBuilder text=new StringBuilder();
        Foe foe=node.foe;
        if(foe!=null){
            text.append(className(foe.heroClass)).append(" · ").append(foe.health).append(" Health");
            if(foe.deckSize>0)text.append(" · ").append(foe.deckSize).append(" cards");
            if(foe.bonusXp>0)text.append(" · +").append(foe.bonusXp).append(" starting XP");
            if(foe.extraCards>0)text.append(" · +").append(foe.extraCards).append(" opening cards");
            if(foe.style!=null&&!foe.style.isBlank())text.append("\n").append(foe.style);
            if(foe.abilityName!=null&&!foe.abilityName.isBlank())text.append("\n\n").append(foe.abilityName).append(" — ").append(foe.abilityText);
            if(battle&&foe.heroClass!=null){
                var power=HeroPowers.of(foe.heroClass);
                text.append("\n\n").append(power.displayName().getString()).append(" · 2 XP\n").append(power.description().getString());
            }
            if(foe.bounty>0)text.append("\n\nWin ").append(currency(foe.bounty)).append(".");
            if("ELITE".equals(node.kind))text.append(" Choose a charm and a card.");
            else if(!"BOSS".equals(node.kind))text.append(" Choose a card or skip.");
        }else if(mystery(node))text.append("An unknown encounter awaits. Its choices are revealed when you arrive.");
        else if(node.description!=null)text.append(node.description);
        drawInfoText(g,List.of(Component.literal(text.toString())),ll+7,tt+47,ww-14,bb-5);
    }

    private String title() {
        if(page==Page.RETIRE)return "Retire this run?";
        if(page==Page.DECK)return className(run.heroClass)+" deck · "+deckSize()+" cards";
        if(page==Page.NODE)return "Room details";
        if(isMap())return (run.actName==null?"Expedition map":run.actName)+" · "+run.depth+"/"+run.roomCount;
        Node current=nodes.get(run.activeNode);
        return switch(run.phase){
            case CLASS->"Choose your class";case DRAFT->"Draft pick "+(deckSize()+1)+" of "+run.draftSize;
            case READY->current==null?"Next encounter":current.title;case REWARD->"Spoils of battle";case ELITE_PRIZE->"Elite prize";
            case SHOP->"Villager shop";case SHOP_CARDS->"Card counter";case SHOP_CHARMS->"Charm counter";
            case SERVICES->"Villager services";case REPLACE->"Choose a charm to replace";case REMOVE->"Remove one copy";
            case COPY->"Duplicate one copy";case TRAIN_CARD->"Choose a copy to train";case TRAIN_TYPE->"Choose its upgrade";
            case TRADE_OUT->"Offer a card";case TRADE_IN->"Pick a replacement";
            case REST,EVENT->current==null?"Trail stop":current.title;case BOON->"Choose a boon";
            case PAUSED->"Encounter in progress";case VICTORY->"Act complete";case DEFEAT->"Journey's end";
            case UNAVAILABLE->"No draft available";default->"The Trials";
        };
    }
    private String hint() {
        if(page==Page.DECK)return "Scroll down to browse. Identical cards stack; upgrades stay separate. Click to inspect.";
        if(isMap())return "Scroll: move. Drag empty parchment: pan. + / -: zoom. Fit: whole route.";
        if(page!=Page.JOURNEY)return "";
        return switch(run.phase){
            case CLASS->"Choose one class, then draft its starter deck.";
            case DRAFT->"Pick one card. Right-click a card to inspect.";
            case READY->"Your current Health carries into this fight.";
            case REWARD->"Take one card for your deck, or skip the reward.";
            case ELITE_PRIZE->"Choose a charm or skip it.";
            case SHOP->"Spend Emeralds or leave to continue.";
            case SHOP_CARDS->"Your run deck has "+deckSize()+"/"+run.deckLimit+" cards.";
            case SHOP_CHARMS->"Carry up to "+run.charmSlots+" charms.";
            case REPLACE->"Choose which equipped charm to give up.";
            case REMOVE->"Remove one copy. Keep at least "+run.deckMinimum+" cards.";
            case COPY->"Keep the original and add a copy.";
            case TRAIN_CARD->"Each copy can be trained once. Right-click to inspect.";
            case TRAIN_TYPE->"Choose one permanent upgrade for this run copy.";
            case TRADE_OUT->"Select the copy you are willing to swap.";
            case TRADE_IN->"Replace the offered copy with your chosen card.";
            case REST,EVENT->"Choose an option or leave this stop.";
            case BOON->"Take one boon for the rest of this run.";
            case PAUSED->"Return to the table where you began the fight.";
            case VICTORY->"Your expedition reached the end of the act.";
            case DEFEAT->"Your next journey begins with a fresh draft.";
            default->"";
        };
    }
    private Button button(int xx,int yy,int ww,String label,Runnable action) {
        return addRenderableWidget(new Button(xx,yy,ww,18,Component.literal(label),b->action.run(),supplier -> supplier.get()){
            @Override protected void extractContents(GuiGraphicsExtractor g,int mx,int my,float tick){
                boolean main=this==primary,retire=label.equals("Retire")||label.equals("Retire run");
                int fill=!active?0xFF504339:retire?(isHoveredOrFocused()?0xFF9B4439:0xFF76352F):main?(isHoveredOrFocused()?0xFF477858:GREEN):isHoveredOrFocused()?0xFF865E3F:0xFF63402B;
                g.fill(getX(),getY(),getRight(),getBottom(),fill);
                g.outline(getX(),getY(),getWidth(),getHeight(),isHoveredOrFocused()||main&&active?GOLD:WOOD_DARK);
                String text=main&&travelling()?"Travelling":label;
                centered(g,text,getX()+getWidth()/2,getY()+5,getWidth()-4,active?CREAM:0xFFC4B29B,false);
            }
        });
    }

    @Override public void extractRenderState(GuiGraphicsExtractor g,int mx,int my,float tick) {
        g.fill(0,0,width,height,0xDF17130E);
        BinderTheme.cover(g,x,y,w,h);
        g.fill(left-6,headerBottom,right+6,footer-4,PAGE);
        g.fill(left-6,headerBottom,left-4,footer-4,0xFFBA9E73);
        g.text(font,Component.literal("THE TRIALS").withStyle(ChatFormatting.BOLD),left,y+12,GOLD,false);
        if(hasRun()){
            int classRight=deckButton==null?right:deckButton.getX()-7;
            CardRenderer.fitText(g,font,Component.literal(className(run.heroClass)),left+73,y+12,classRight-left-76,CREAM,false,false);
            int statusRight=right;
            String stats=run.health+"/"+run.maxHealth+" HP  ·  "+run.lives+" lives  ·  "+run.marks+" Emeralds";
            CardRenderer.fitText(g,font,Component.literal(stats),left,y+33,statusRight-left,CREAM,false,false);
            for(HeaderIcon icon:headerIcons){
                g.fill(icon.x,icon.y,icon.x+17,icon.y+17,WOOD_DARK);g.item(icon.icon,icon.x,icon.y);
                if(!icon.badge.isBlank())centered(g,icon.badge,icon.x+9,icon.y+9,17,GOLD,true);
            }
        }
        boolean paging=page==Page.JOURNEY&&!deckOffers()&&!decisions()&&choices().size()>3;
        if(!isMap()) {
            CardRenderer.fitText(g,font,Component.literal(title()).withStyle(ChatFormatting.BOLD),left+2,headerBottom+5,right-left-4-(paging?76:0),TEXT_DARK,false,false);
            g.fill(left,headerBottom+19,right,headerBottom+20,LINE);
        }
        if(page==Page.RETIRE)
            statement(g,Items.BOOK.getDefaultInstance(),"Leave the trail","This ends your run and gives up its deck and supplies. Close this screen to keep the run for later.");
        else if(isMap())drawMap(g);
        else if(page==Page.NODE&&previewNode!=null)drawNodeInfo(g,previewNode,left,bodyTop,right,bodyBottom,false);
        else if(page==Page.DECK&&entries.isEmpty())
            centered(g,"Cards appear here as you draft them.",(left+right)/2,bodyTop+12,right-left-8,MUTED,false);
        else if(page==Page.JOURNEY&&decisions())drawDecision(g);
        else if(page==Page.JOURNEY&&!picking())drawCamp(g);
        super.extractRenderState(g,mx,my,tick);
        if(hasCurve())drawCurve(g,mx,my);
        if(travelling())drawTravel(g);
        else{
            if(deckButton!=null&&deckButton.isHoveredOrFocused()&&page!=Page.DECK)drawDeckHover(g);
            for(HeaderIcon icon:headerIcons)
                if(mx>=icon.x&&mx<icon.x+17&&my>=icon.y&&my<icon.y+17)Tooltips.show(g,font,icon.text,mx,my);
            if(!isMap()&&mx>=left&&mx<=right&&my>=headerBottom+1&&my<headerBottom+20&&!hint().isBlank())
                Tooltips.show(g,font,List.of(Component.literal(hint())),mx,my);
        }
        long elapsed=now()-arrivedAt;
        if(!isMap()&&page==Page.JOURNEY&&elapsed<350){
            int alpha=(int)(70*(1-elapsed/350f));
            g.outline(left-2,bodyTop-2,right-left+4,bodyBottom-bodyTop+4,(alpha<<24)|0xAC792D);
        }
    }
    private void drawDeckHover(GuiGraphicsExtractor g) {
        var grouped=new LinkedHashMap<DeckGroup,Integer>();
        for(DeckEntry entry:entries)grouped.merge(new DeckGroup(entry.id,entry.cost,entry.training),1,Integer::sum);
        int maxRows=Math.max(1,(height-y-55)/12),columns=Math.max(1,(grouped.size()+maxRows-1)/maxRows);
        int rows=Math.max(1,(grouped.size()+columns-1)/columns);
        int panelWidth=Math.min(width-16,Math.max(180,columns*196));
        int panelLeft=Math.max(8,Math.min(right-panelWidth,width-panelWidth-8)),top=y+28,cw=(panelWidth-12)/columns;
        g.fill(panelLeft-2,top-2,panelLeft+panelWidth+2,top+rows*12+25,WOOD_DARK);
        g.fill(panelLeft,top,panelLeft+panelWidth,top+rows*12+23,PAGE);
        g.outline(panelLeft,top,panelWidth,rows*12+23,LINE);
        centered(g,deckSize()+" cards · click Deck to inspect",panelLeft+panelWidth/2,top+5,panelWidth-10,TEXT_DARK,true);
        int i=0;
        for(var entry:grouped.entrySet()){
            int xx=panelLeft+6+(i/rows)*cw,yy=top+20+(i%rows)*12;
            DeckGroup card=entry.getKey();
            String name=(card.cost<0?"?":card.cost)+" XP  "+cardName(card.id);
            if(card.training!=TrialTraining.NONE)name+=" · "+card.training.label();
            CardRenderer.fitText(g,font,Component.literal(entry.getValue()+"× "+name),xx,yy,cw-5,TEXT_DARK,false,false);
            i++;
        }
        if(grouped.isEmpty())centered(g,"Your draft begins here",panelLeft+panelWidth/2,top+20,panelWidth-12,MUTED,false);
    }
    private void drawCamp(GuiGraphicsExtractor g) {
        if(run.phase==Phase.READY){
            Node node=nodes.get(run.activeNode);
            if(node!=null){
                int ww=Math.min(440,right-left),xx=(width-ww)/2;
                drawNodeInfo(g,node,xx,bodyTop,xx+ww,bodyBottom,true);
            }
        }else{
            var icon=switch(run.phase){case VICTORY->Items.TOTEM_OF_UNDYING;case DEFEAT->Items.CAMPFIRE;case PAUSED->Items.CLOCK;default->Items.BOOK;};
            String label=switch(run.phase){case VICTORY->"Champion of the Trials";case DEFEAT->run.wins+" fights won";case PAUSED->"A fight awaits";default->"Check the card catalogue";};
            statement(g,icon.getDefaultInstance(),label,run.message);
        }
    }
    private void statement(GuiGraphicsExtractor g,ItemStack icon,String heading,String message) {
        int cx=(left+right)/2,yy=bodyTop+Math.max(0,(bodyBottom-bodyTop-84)/2);
        g.item(icon,cx-8,yy);
        centered(g,heading,cx,yy+23,right-left-8,TEXT_DARK,true);
        if(message!=null)paragraph(g,message,left+9,yy+39,right-left-18,bodyBottom);
    }
    private void paragraph(GuiGraphicsExtractor g,String raw,int xx,int yy,int ww,int bottom) {
        int room=bottom-yy;
        if(room<1||ww<1||raw==null)return;
        Component text=Component.literal(raw);
        float scale=1;
        var lines=font.split(text,ww);
        while(scale>0.7F&&lines.size()*(font.lineHeight+1)*scale>room){
            scale-=0.04F;lines=font.split(text,Math.round(ww/scale));
        }
        g.pose().pushMatrix();g.pose().translate(xx+ww/2f,yy);g.pose().scale(scale,scale);
        int rowY=0;
        for(var line:lines){
            if((rowY+font.lineHeight)*scale>room)break;
            g.text(font,line,-font.width(line)/2,rowY,TEXT_DARK,false);rowY+=font.lineHeight+1;
        }
        g.pose().popMatrix();
    }
    private void centered(GuiGraphicsExtractor g,String raw,int cx,int yy,int maxWidth,int color,boolean bold) {
        if(maxWidth<1)return;
        Component text=Component.literal(raw==null?"":raw);
        if(bold)text=text.copy().withStyle(ChatFormatting.BOLD);
        CardRenderer.fitText(g,font,text,cx,yy,maxWidth,color,true,false);
    }
    @Override public boolean mouseClicked(MouseButtonEvent event,boolean doubleClick) {
        if(waiting||travelling())return true;
        if(event.button()==InputConstants.MOUSE_BUTTON_RIGHT){
            for(ChoiceButton choice:choiceButtons)if(choice.cardId!=null&&choice.isMouseOver(event.x(),event.y())){
                inspect(choice.cardId,TrialTraining.NONE);return true;
            }
        }
        if(super.mouseClicked(event,doubleClick))return true;
        if(isMap()&&event.button()==InputConstants.MOUSE_BUTTON_LEFT&&insideMap(event.x(),event.y())) {
            for(Node node:nodes.values()) {
                if(within(event.x(),event.y(),mapNodeLabelBounds(node.id))) {
                    previewNode=node;detailScroll=0;selected=run.phase==Phase.MAP&&page!=Page.MAP?choices().indexOf(node.id):-1;
                    if(primary!=null)primary.active=canPick();
                    return true;
                }
                int[] rect=mapNodeBounds(node.id);
                if(rect!=null&&event.x()>=rect[0]-3&&event.x()<=rect[0]+rect[2]+3&&
                        event.y()>=rect[1]-3&&event.y()<=rect[1]+rect[3]+(zoom>=0.55?15:3))return true;
            }
            draggingMap=true;
            return true;
        }
        return false;
    }
    private boolean insideMap(double xx,double yy) {
        return xx>mapLeft&&xx<mapRight&&yy>mapTop&&yy<mapBottom;
    }
    @Override public boolean mouseDragged(MouseButtonEvent event,double dragX,double dragY) {
        if(draggingMap) {
            if(!waiting&&!travelling())panMap(-(int)Math.round(dragX),-(int)Math.round(dragY));
            return true;
        }
        return super.mouseDragged(event,dragX,dragY);
    }
    @Override public boolean mouseReleased(MouseButtonEvent event) {
        if(draggingMap){draggingMap=false;return true;}
        return super.mouseReleased(event);
    }
    @Override public boolean mouseScrolled(double xx,double yy,double horizontal,double vertical) {
        if(waiting||travelling())return true;
        if(!headerItems().isEmpty()&&yy>=y+48&&yy<y+70&&(vertical!=0||horizontal!=0)) {
            scrollHeader((vertical!=0?vertical:horizontal)>0?-1:1);return true;
        }
        boolean info=page==Page.NODE||page==Page.JOURNEY&&run.phase==Phase.READY||
                page==Page.JOURNEY&&decisions()&&xx>decisionRight+5||isMap()&&wideMap&&xx>mapRight+5;
        if(info&&vertical!=0){
            detailScroll=Math.clamp(detailScroll-(int)Math.round(vertical*22),0,detailMax);return true;
        }
        if(isMap()&&insideMap(xx,yy)&&(vertical!=0||horizontal!=0)){
            panMap(0,(int)Math.round(-(vertical!=0?vertical:horizontal)*45));return true;
        }
        if(!(page==Page.DECK||page==Page.JOURNEY&&deckOffers())||vertical==0||!insideDeck(xx,yy))
            return super.mouseScrolled(xx,yy,horizontal,vertical);
        int next=Math.clamp(deckScroll-(int)Math.round(vertical*42),0,deckScrollMax);
        if(next!=deckScroll){deckScroll=next;rebuildWidgets();}
        return true;
    }
    @Override public boolean keyPressed(net.minecraft.client.input.KeyEvent event) {
        return waiting||travelling()||super.keyPressed(event);
    }
    @Override public void onClose() {
        if(waiting||travelling())return;
        if(page==Page.DECK||page==Page.NODE||page==Page.RETIRE)back();
        else if(page==Page.MAP)journey();
        else minecraft.gui.setScreen(returnTo);
    }
    @Override public boolean isPauseScreen(){return false;}
}

