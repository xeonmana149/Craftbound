package craftbound.client.match;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.mojang.blaze3d.platform.InputConstants;
import craftbound.board.BoardDesign;
import craftbound.card.*;
import craftbound.client.AnimationClock;
import craftbound.client.binder.BinderTheme;
import craftbound.client.card.CardCanvas;
import craftbound.client.card.CardInspectScreen;
import craftbound.client.card.CardRenderer;
import craftbound.game.*;
import craftbound.item.CardItem;
import craftbound.network.*;
import craftbound.sleeve.Sleeves;
import java.util.*;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.PlayerFaceExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import static craftbound.client.match.MultiplayerBoardLayout.*;

/** Shared four-seat match UI: a fixed two-column team field or a duel against a selected rival. */
public abstract class MultiplayerBoardScreen extends Screen implements LocalLayoutPreview {
    private static final int TEXT=0xFFF1E8D4, QUIET=0xFFC3C9BF, GOLD=0xFFFFD56B, INK=0xFF1C211E;
    private static final int CARD_W=CARD_WIDTH,CARD_H=CARD_HEIGHT,HERO=48,SOCKET=36;
    private static final int EMBER=0xFFE79D89, TIDE=0xFF9FC9E0, IMPACT=0xFFF59B83;
    private final CommanderLogPanel commanderLog = new CommanderLogPanel();
    private final boolean teams;
    protected JsonObject data;
    protected Screen previewReturn;
    private Mat mat;
    private List<Seat> rigs=List.of();
    private record Hit(Rect rect,int side,int mid,String kind,String art,boolean enchanted,String hint,MatchState.Mob unit) {}
    private final List<Hit> hits=new ArrayList<>();
    private final List<HandSlot> handSlots=new ArrayList<>();
    private final Map<String,Integer> damage=new HashMap<>();
    private final Map<Integer,List<MatchState.Mob>> rows=new HashMap<>();
    private final MatchState.Tool[] equipped = new MatchState.Tool[4];
    private final MatchCardHover cardHover = new MatchCardHover();
    private List<String> publicLines=List.of();
    private CardCatalog.State cachedCatalogue;
    private final Deque<BoardAttack> pendingAttacks = new ArrayDeque<>();
    private BoardAttack attack;
    private long attackAt;
    private record EggThrow(BoardAttack.End from, BoardAttack.End to, long started) {}
    private final List<EggThrow> eggs = new ArrayList<>();
    private long received=System.currentTimeMillis(),flashAt;
    private boolean clockHeld,menu,confirm,showLog,targetDrag;
    private int rival=-1,action,index,dragHand=-1,hoverHand=-1,handHeight;
    private double pressX,pressY,mouseX,mouseY;
    private String selection="",notice="";
    private ItemStack inspected=ItemStack.EMPTY;
    private Identifier inspectedId;
    private CardCatalog.State inspectedCatalogue;
    private net.minecraft.locale.Language inspectedLanguage;
    private List<ItemStack> inspectedCreates=List.of();
    private int inspectedPage,inspectedPages=1;
    private Rect inspectedMain,inspectedPane,inspectedPrevious,inspectedNext;
    private Button endButton;
    private final HandPickOverlay cardChoice = new HandPickOverlay();
    private MatchState choiceState;
    private boolean choosing() { return choiceState != null && choiceState.choice != null && !choiceState.over; }

    protected MultiplayerBoardScreen(JsonObject state,boolean teams) {
        super(Component.literal(teams?"Team Duos":"Commander"));
        this.teams=teams;
        update(state);
    }
    @Override public Screen layoutPreviewParent(){return previewReturn;}
    public final JsonObject snapshot(){return data;}
    public final boolean same(JsonObject other){return str(data,"session").equals(str(other,"session"));}
    public final boolean inspecting(){return !inspected.isEmpty();}
    public final String selectedMove(){return selection;}
    public final int[] boardViewport(){return ints(mat.viewport());}
    public final int[] seatAreaBounds(int seat){
        return rigs.stream().filter(r->r.owner()==seat).findFirst().map(r->ints(mat.screen(r.bounds()))).orElse(null);
    }
    public final int[] targetBounds(int side,int mid) {
        return hits.stream().filter(h->h.side==side&&h.mid==mid&&h.kind.equals(mid==0?"hero":"unit"))
                .findFirst().map(h->ints(mat.screen(h.rect))).orElse(null);
    }
    public final int[] handCardBounds(int i){return i>=0&&i<handSlots.size()?ints(handPose(i).bounds()):null;}
    private static int[] ints(Rect r){return new int[]{(int)Math.floor(r.x()),(int)Math.floor(r.y()),Math.max(1,(int)Math.ceil(r.w())),Math.max(1,(int)Math.ceil(r.h()))};}
    static String str(JsonObject o,String k){return o!=null&&o.has(k)&&!o.get(k).isJsonNull()?o.get(k).getAsString():"";}
    static int num(JsonObject o,String k){return o!=null&&o.has(k)&&!o.get(k).isJsonNull()?o.get(k).getAsInt():0;}
    static boolean flag(JsonObject o,String k){return o!=null&&o.has(k)&&!o.get(k).isJsonNull()&&o.get(k).getAsBoolean();}
    private int me(){return data.has("you")?num(data,"you"):-1;}
    private int home(){return me()>=0?me():0;}
    private JsonObject seat(int s){return data.getAsJsonArray("players").get(s).getAsJsonObject();}
    private boolean enemy(int s){return teams?(s&1)!=(home()&1):s!=home();}
    private boolean alive(int s){return !seat(s).has("alive")||flag(seat(s),"alive");}
    private boolean canAct(){return previewReturn==null&&!flag(data,"layoutPreview")&&me()>=0&&flag(data,"myTurn")&&!flag(data,"over")&&alive(me());}
    private JsonArray hand(){
        if(me()<0||!data.has("players"))return new JsonArray();
        JsonArray value=teams?data.getAsJsonArray("hand"):seat(me()).getAsJsonArray("hand");
        return value==null?new JsonArray():value;
    }
    private JsonArray options(String key){
        JsonObject moves=data.getAsJsonObject("moves");
        return moves!=null&&moves.has(key)?moves.getAsJsonArray(key):new JsonArray();
    }
    private boolean hasMove(String key){return data.has("moves")&&data.getAsJsonObject("moves").has(key);}
    private boolean valid(int s,int mid){return !selection.isEmpty()&&options(selection).contains(new JsonPrimitive(s+":"+mid));}
    private void reset(){selection="";dragHand=-1;targetDrag=false;confirm=false;notice="";}
    private static int stateRevision(JsonObject view){return num(view,view.has("stateRevision")?"stateRevision":"revision");}
    private void refreshCatalogue(){
        if(cachedCatalogue!=CardCatalog.current()){visuals.clear();icons.clear();cachedCatalogue=CardCatalog.current();}
    }
    public final void update(JsonObject next) {
        JsonObject old=data;
        boolean changed=old==null||num(old,"turn")!=num(next,"turn")||num(old,"revision")!=num(next,"revision");
        damage.clear();
        if(old!=null&&old.has("players")&&next.has("players")&&stateRevision(old)!=stateRevision(next)){
            for(int s=0;s<4;s++){
                var before=old.getAsJsonArray("players").get(s).getAsJsonObject();
                var after=next.getAsJsonArray("players").get(s).getAsJsonObject();
                damage.put(s+":0",num(after,"health")-num(before,"health"));
                Map<Integer,Integer> hp=new HashMap<>();
                for(var raw:before.getAsJsonArray("board"))hp.put(num(raw.getAsJsonObject(),"mid"),num(raw.getAsJsonObject(),"hp"));
                for(var raw:after.getAsJsonArray("board")){
                    var u=raw.getAsJsonObject();damage.put(s+":"+num(u,"mid"),num(u,"hp")-hp.getOrDefault(num(u,"mid"),num(u,"hp")));
                }
            }
            flashAt=AnimationClock.millis();
            if (next.has("publicCast")) {
                var cast = next.getAsJsonObject("publicCast");
                if (CombatVfx.egg(Identifier.tryParse(str(cast, "card")))) {
                    int from = num(cast, "from"), to = num(cast, "to");
                    var players = old.getAsJsonArray("players");
                    eggs.add(new EggThrow(BoardAttack.end(players.get(from).getAsJsonObject(), from, Match.HERO),
                            BoardAttack.end(players.get(to).getAsJsonObject(), to, num(cast, "target")), flashAt));
                    EggCastAnimation.launchSound();
                }
            }
            if(next.has("publicAttack")){
                var path=next.getAsJsonObject("publicAttack");
                queueAttack(BoardAttack.read(old, path), flashAt);
            }
        }
        if (!teams && flag(next,"layoutPreview") && next.has("players") && commanderLog.latestRound()==0) {
            int owner = num(next,"current");
            commanderLog.preview(new CommanderHistory.Entry(num(next,"round"),num(next,"turn"),owner,
                    CommanderHistory.Kind.TURN,str(next.getAsJsonArray("players").get(owner).getAsJsonObject(),"name")));
        }
        data=next;received=System.currentTimeMillis();clockHeld=flag(next,"clockHeld")||next.has("choice");
        choiceState=MatchState.choiceView(next);
        cardChoice.sync(choiceState,received);
        if(choosing()){reset();menu=false;showLog=false;inspected=ItemStack.EMPTY;}
        refreshCatalogue();
        var lines=new ArrayList<String>();
        if(next.has("publicLog"))for(var e:next.getAsJsonArray("publicLog"))lines.add(e.getAsString());
        else if(!str(next,"lastMove").isEmpty())lines.add(str(next,"lastMove"));
        publicLines=List.copyOf(lines);rows.clear();
        if(next.has("players"))for(int s=0;s<4;s++){
            var mobs = new ArrayList<MatchState.Mob>();
            var blocks = new ArrayList<MatchState.Mob>();
            for (var raw : seat(s).getAsJsonArray("board")) {
                var unit = MatchState.readMob(raw.getAsJsonObject());
                (unit.block ? blocks : mobs).add(unit);
            }
            JsonObject tool = seat(s).getAsJsonObject("tool");
            equipped[s] = tool == null ? null : MatchState.readTool(tool);
            rows.put(s*2,mobs);rows.put(s*2+1,blocks);
        }
        if(changed||!canAct()||!selection.isEmpty()&&!hasMove(selection))reset();
        if(next.has("notice"))notice=str(next,"notice");
        if(next.has("players")){
            if(rival<0||!enemy(rival)||!alive(rival)){
                for(int s=0;s<4;s++)if(enemy(s)&&alive(s)){rival=s;break;}
            }
            if(rival<0)rival=(home()+1)%4;
            updateSeats();
        }
        if(width>0&&height>0)rebuildWidgets();
    }
    private void updateSeats(){
        if(mat!=null && rival>=0)rigs=mat.seats(teams,home(),rival);
    }
    private void focusRival(int side){
        if(!enemy(side) || !alive(side) || side==rival)return;
        rival=side;notice="";
        cardHover.clear();
        hits.clear();  // Hitboxes from the previous field may not be used until the next draw.
        updateSeats();
    }
    public final int focusedRival(){return rival;}
    /** The three public seat tabs always have the same positions, independent of focus or current turn. */
    public final int[] rivalTabBounds(int side){
        Rect tab = teams ? null : rivalTab(width, home(), side);
        return tab == null ? null : ints(tab);
    }
    private int rivalAt(double x,double y){
        if(teams || !data.has("players") || y<18 || y>=42)return -1;
        for(int s=0;s<4;s++){
            int[] r=rivalTabBounds(s);
            if(r!=null && x>=r[0] && x<r[0]+r[2])return s;
        }
        return -1;
    }
    public final void clock(TurnClockPayload clock) {
        if(previewReturn!=null||flag(data,"layoutPreview")||!data.has("players")||clock.turn()!=num(data,"turn")
                ||clock.side()!=num(data,teams?"activeTeam":"current"))return;
        data.addProperty("clockTicks",clock.ticks());clockHeld=clock.held();received=System.currentTimeMillis();
    }
    private float seconds(){
        if(!data.has("clockTicks")||num(data,"clockTicks")<0||previewReturn!=null||flag(data,"layoutPreview")||flag(data,"over"))return -1;
        return Math.max(0,(num(data,"clockTicks")*50L-(clockHeld?0:System.currentTimeMillis()-received))/1000F);
    }
    private void send(int a,int i,int side,int mid) {
        if(previewReturn!=null||flag(data,"layoutPreview")){notice="Preview only. Esc returns to settings.";return;}
        if(teams)ClientPlayNetworking.send(new RelayInputPayload(str(data,"session"),num(data,"revision"),a,i,rival,side,mid));
        else ClientPlayNetworking.send(new CommanderInputPayload(str(data,"session"),num(data,"revision"),a,i,rival,side,mid));
        reset();
    }
    private void select(int a,int i,String move,boolean targeted) {
        if(!canAct())return;
        if(!hasMove(move)){notice="That action is not available.";return;}
        if(!targeted){send(a,i,-1,0);return;}
        if(options(move).isEmpty()){notice="No legal target.";return;}
        action=a;index=i;selection=move;confirm=false;
        notice="Choose a highlighted target. Right-click to cancel.";
    }
    private void aim(Hit hit){
        if(hit!=null&&valid(hit.side,hit.mid)){
            if(enemy(hit.side))focusRival(hit.side);
            if(selection.startsWith("play") && !legalRival(hand().get(index).getAsJsonObject(),rival)
                    || selection.equals("cast") && !legalRival(seat(me()),rival)){
                notice="Choose a different rival for this card.";return;
            }
            send(action,index,hit.side,hit.mid);
        }
    }
    private boolean legalRival(JsonObject card,int target){
        return !card.has("rivals")||card.getAsJsonArray("rivals").contains(new JsonPrimitive(target));
    }
    private boolean playable(int i){
        if(!canAct()||i<0||i>=hand().size()||!hasMove("play"+i))return false;
        var raw=hand().get(i).getAsJsonObject();var card=Cards.get(Identifier.parse(str(raw,"id")));
        if(card==null||card.cost()>num(seat(me()),"xp"))return false;
        if(raw.has("rivals")&&raw.getAsJsonArray("rivals").isEmpty())return false;
        if(!raw.has("rivals")&&raw.has("playable")&&!flag(raw,"playable"))return false;
        return true;
    }
    private void play(int i,Hit target){
        if(!playable(i)){notice="This card cannot be played right now.";return;}
        var raw=hand().get(i).getAsJsonObject();
        if(!legalRival(raw,rival)){
            if(target!=null&&enemy(target.side)&&legalRival(raw,target.side))focusRival(target.side);
            else {
                if(raw.has("rivals")&&!raw.getAsJsonArray("rivals").isEmpty())focusRival(raw.getAsJsonArray("rivals").get(0).getAsInt());
                notice="Rival changed to "+str(seat(rival),"name")+". Play the card again to confirm.";
                return;
            }
        }
        // Board cards may still be summoned if their optional Spawn target does not exist.
        boolean targeted=CardEffects.targetKind(Identifier.parse(str(raw,"id")))!=Target.Kind.NONE&&!options("play"+i).isEmpty();
        select(MatchActionPayload.PLAY,i,"play"+i,targeted);
        if(targeted)aim(target);
    }
    @Override protected void init(){
        handHeight=me()<0?0:Math.clamp((int)(height*.18),37,84);
        int boardTop=teams?21:45;
        mat=new Mat(new Rect(4,boardTop,Math.max(40,width-8),Math.max(48,height-handHeight-22-boardTop)),teams);
        updateSeats();
        if(data.has("waiting")){
            button(width/2-48,height-24,96,"Leave queue",()->send(teams?RelayMatch.LEAVE_QUEUE:CommanderTables.LEAVE,0,-1,0));
            return;
        }
        if (!teams && previewReturn != null) {
            addRenderableWidget(new TableBookButton(width-84, 2, 80, 14, Component.literal("Preview attack"),
                    this::previewRemoteAttack));
        }
        button(6,height-20,Math.min(78,width/5),"Battle log",()->{
            if (!showLog && !teams) reset();
            showLog=!showLog;
        });
        endButton=button(width-Math.min(96,width/4)-5,height-21,Math.min(96,width/4),teams?"Ready":"End Turn",()->{
            if(me()<0||flag(data,"over")||previewReturn!=null){onClose();return;}
            send(MatchActionPayload.END_TURN,0,-1,0);
        });
    }
    /** Exercise the normal snapshot/attack path without needing another player in a layout preview. */
    private void previewRemoteAttack() {
        if (teams || previewReturn == null || !flag(data,"layoutPreview")) return;
        var hidden = new ArrayList<Integer>(2);
        for (int s = 0; s < 4; s++) if (s != home() && s != rival) hidden.add(s);
        int from = hidden.get(num(data,"current") == hidden.get(0) ? 1 : 0);
        int to = hidden.get(from == hidden.get(0) ? 1 : 0);
        var next = data.deepCopy();
        var path = new JsonObject();
        path.addProperty("from", from);
        path.addProperty("source", previewAttacker(from));
        path.addProperty("to", to);
        path.addProperty("target", Match.HERO);
        next.add("publicAttack", path);
        next.addProperty("turn", num(data,"turn") + 1);
        next.addProperty("current", from);
        next.addProperty("stateRevision", stateRevision(data) + 1);
        int round = Math.max(1,num(data,"round")) + (from <= num(data,"current") ? 1 : 0);
        next.addProperty("round",round);
        int turn = num(next,"turn");
        commanderLog.preview(new CommanderHistory.Entry(round,turn,from,CommanderHistory.Kind.TURN,str(seat(from),"name")));
        commanderLog.preview(new CommanderHistory.Entry(round,turn,from,CommanderHistory.Kind.DETAIL,
                str(seat(from),"name")+" has "+num(seat(from),"xp")+"/"+num(seat(from),"maxXp")+" XP."));
        commanderLog.preview(new CommanderHistory.Entry(round,turn,from,CommanderHistory.Kind.ACTION,BoardAttack.read(data,path).caption()+"."));
        update(next);
    }

    private int previewAttacker(int side) {
        var mobs = rows.get(side * 2);
        return mobs.isEmpty() ? Match.HERO : mobs.getFirst().mid;
    }

    private Button button(int x,int y,int w,String name,Runnable action){
        return addRenderableWidget(new TableBookButton(x,y,w,18,Component.literal(name),action));
    }
    @Override public void extractRenderState(GuiGraphicsExtractor g,int mx,int my,float dt) {
        refreshCatalogue();
        mouseX=mx;mouseY=my;hits.clear();
        layoutHand(mx,my);
        BoardDesign design=BoardDesign.fromId(str(data,"boardDesign"));
        backdrop(g,design);
        if(data.has("waiting")){waiting(g);super.extractRenderState(g,mx,my,dt);return;}
        if(endButton!=null){
            boolean close=me()<0||flag(data,"over");
            boolean canReady=teams&&me()>=0&&(me()&1)==num(data,"activeTeam");
            boolean interactive=previewReturn==null&&!flag(data,"layoutPreview")&&!choosing();
            endButton.setMessage(Component.literal(close?"Close":teams?(flag(data,"ready")?"Unready":"Ready"):"End Turn"));
            endButton.active=!menu&&(close||interactive&&(canReady||canAct()));
        }
        advanceAttacks(AnimationClock.millis());
        header(g);
        Rect viewport=mat.viewport();
        g.enableScissor((int)viewport.x(),(int)viewport.y(),(int)(viewport.x()+viewport.w()),(int)(viewport.y()+viewport.h()));
        g.pose().pushMatrix();
        Point zero=mat.screen(0,0);g.pose().translate((float)zero.x(),(float)zero.y());g.pose().scale((float)mat.scale(),(float)mat.scale());
        table(g,design);
        g.nextStratum(); // Cards and their HUD sit above the felt tint and turn trim.
        for(var rig:rigs)drawSeat(g,rig);
        if (teams) {
            drawTeamHealth(g, false);
            drawTeamHealth(g, true);
        }
        g.pose().popMatrix();g.disableScissor();
        drawSeatResources(g);
        drawFuse(g);
        drawSwing(g);
        drawEggs(g);
        drawFooter(g);
        drawHand(g,mx,my);
        if(!selection.isEmpty()){
            Point origin=selectionOrigin();
            MatchArt.arrow(g,(int)origin.x(),(int)origin.y(),mx,my,GOLD,AnimationClock.millis());
        }
        super.extractRenderState(g,mx,my,dt);
        if (canHover()) drawMatchHover(g, mx, my);
        else cardHover.clear();
        if(showLog)drawLog(g);
        if(dragHand>=0&&Math.hypot(mx-pressX,my-pressY)>4)drawDragged(g,dragHand,mx,my);
        if(!inspected.isEmpty())drawInspect(g,mx,my);
        if(menu)drawMenu(g,mx,my);
        cardChoice.draw(g,font,choiceState,width,height,mx,my,System.currentTimeMillis());
    }
    private boolean canHover() {
        return data.has("players") && !data.has("waiting") && !flag(data, "over")
                && !menu && !showLog && !inspecting() && !choosing()
                && dragHand < 0 && !targetDrag && selection.isEmpty();
    }

    private void drawMatchHover(GuiGraphicsExtractor g, int mx, int my) {
        cardHover.prepare(font, width, height, data);
        g.nextStratum();
        if (hoverHand >= 0 && hoverHand < hand().size()) {
            JsonObject card = hand().get(hoverHand).getAsJsonObject();
            cardHover.hand(g, Identifier.parse(str(card, "id")), flag(card, "ench"), MatchState.readTraining(card), MatchState.readBrew(card),
                    (int) Math.round(handPose(hoverHand).cx()), 4, playable(hoverHand), mx, my);
            return;
        }
        if (cardHover.drawRetained(g, mx, my)) return;
        Hit hit = hit(mx, my);
        if (hit == null) return;
        JsonObject owner = seat(hit.side);
        int[] anchor = ints(mat.screen(hit.rect));
        boolean near = hit.side == home() || teams && !enemy(hit.side);
        switch (hit.kind) {
            case "unit" -> cardHover.unit(g, hit.unit, hit.side == me() && flag(data, "myTurn"), anchor, mx, my);
            case "tool" -> cardHover.tool(g, equipped[hit.side], anchor, near);
            case "power" -> {
                CardClass cls = cardClass(owner);
                int cost = data.has("powerCost") ? num(data, "powerCost") : Match.POWER_COST;
                cardHover.power(g, HeroPowers.of(cls), cls, cost,
                        flag(owner.getAsJsonObject("power"), "used"), anchor, near);
            }
            case "cast" -> {
                String status = flag(owner, "deployed") ? "Commander in play" : "Summon for " + num(owner, "commandCost") + " XP";
                cardHover.besideCard(g, Identifier.parse(hit.art), hit.enchanted, TrialTraining.NONE,
                        List.of(Component.literal(status)), anchor, mx, my);
            }
        }
    }

    private void backdrop(GuiGraphicsExtractor g,BoardDesign design){
        g.fill(0,0,width,height,0xFF111315);
        for(int y=0,row=0;y<height;y+=12,row++)for(int x=-(row%2)*12;x<width;x+=24)
            g.fill(x+1,y+1,x+23,y+11,(row+x/24)%2==0?design.brick:design.alternateBrick);
    }
    private void table(GuiGraphicsExtractor g,BoardDesign design){
        int w=(int)mat.width(),h=(int)mat.height();
        g.fill(0,0,w,h,MatchArt.WOOD_DARK);
        g.fill(2,2,w-2,h-2,MatchArt.WOOD_LIGHT);
        g.fill(5,5,w-5,h-5,design.mat);
        BoardTrim.draw(new CardCanvas.Gui(g),design,4,4,w-8,h-8);
        g.nextStratum();
        for (int half = 0; half < 2; half++) {
            boolean near = half == 1;
            int side = near ? home() : (teams ? home() ^ 1 : rival);
            int top = near ? h / 2 + 2 : 6, bottom = near ? h - 6 : h / 2 - 2;
            if (teams) g.fill(6, top, w - 6, bottom, (side & 1) == 0 ? 0x29BB5948 : 0x294479AC);
            boolean active = teams ? (side & 1) == num(data, "activeTeam") : side == num(data, "current");
            if (active && !flag(data, "over")) turnTrim(g, 6, top, w - 6, bottom);
        }
        g.fill(6,h/2-1,w-6,h/2+1,0xFF97805D);
        if(teams)g.fill(w/2-1,6,w/2+1,h-6,0xFF757A70);
    }
    /** A slow sage pulse for the turn. Gold still denotes the selected rival. */
    private static int turnColor() {
        double phase = (AnimationClock.millis() % 2400) * Math.PI * 2 / 2400;
        int alpha = 171 + (int)Math.round(39 * Math.cos(phase));
        return alpha << 24 | 0xB5CFA3;
    }
    private static void turnTrim(GuiGraphicsExtractor g, int left, int top, int right, int bottom) {
        int accent = turnColor();
        g.outline(left, top, right - left, bottom - top, 0x6098B492);
        for (int x : new int[]{left, right - 2}) {
            boolean onLeft = x == left;
            for (int y : new int[]{top, bottom - 2}) {
                g.fill(onLeft ? x : x - 22, y, onLeft ? x + 24 : x + 2, y + 2, accent);
                g.fill(x, y == top ? y : y - 10, x + 2, y == top ? y + 12 : y + 2, accent);
            }
        }
    }

    private void drawTeamHealth(GuiGraphicsExtractor g, boolean near) {
        int team = RelayMatch.teamOf(near ? home() : home() ^ 1);
        Rect badge = mat.teamBadge(near);
        int x = (int)Math.round(badge.cx()), y = (int)Math.round(badge.y());
        int value = num(seat(team), "health"), color = team == 0 ? EMBER : TIDE;
        MatchArt.heart(g, font, x - 21, y, 42, value, 0xFFFFFFFF);
        label(g, RelayMatch.teamName(team), x, y + 44, (int)badge.w(), color);
        flash(g, team, Match.HERO, new Point(x + 26, y + 18));
    }

    private void queueAttack(BoardAttack cue, long now) {
        advanceAttacks(now);
        if (attack == null || now - attackAt >= 950 && pendingAttacks.isEmpty()) {
            attack = cue;
            attackAt = now;
        } else {
            if (pendingAttacks.size() == 3) pendingAttacks.removeFirst();
            pendingAttacks.addLast(cue);
        }
    }
    private void advanceAttacks(long now) {
        if (attack == null) return;
        long age = now - attackAt;
        if (age >= 950 && !pendingAttacks.isEmpty()) {
            attack = pendingAttacks.removeFirst();
            attackAt = now;
        } else if (age >= (teams ? 950 : 2350)) attack = null;
    }

    private long lastEggFrame;
    private void drawEggs(GuiGraphicsExtractor g) {
        long now = AnimationClock.millis();
        for (var it = eggs.iterator(); it.hasNext();) {
            EggThrow egg = it.next();
            long age = now - egg.started();
            if (age >= EggCastAnimation.FLIGHT + EggCastAnimation.SPLAT) {
                it.remove();
                continue;
            }
            Point to = egg.to().point(rigs, mat, home(), width);
            if (age < EggCastAnimation.FLIGHT) {
                Point from = egg.from().point(rigs, mat, home(), width);
                EggCastAnimation.fly(g, (float)from.x(), (float)from.y(), (float)to.x(), (float)to.y(),
                        age / (float)EggCastAnimation.FLIGHT);
            } else {
                if (lastEggFrame < egg.started() + EggCastAnimation.FLIGHT) EggCastAnimation.impactSound();
                EggCastAnimation.splat(g, (float)to.x(), (float)to.y(),
                        (age - EggCastAnimation.FLIGHT) / (float)EggCastAnimation.SPLAT);
            }
        }
        lastEggFrame = now;
    }

    private void drawSwing(GuiGraphicsExtractor g){
        if (attack == null) return;
        long age = AnimationClock.millis() - attackAt;
        if (age < 0 || age > 920) return;
        Point from = attack.from().point(rigs, mat, home(), width), to = attack.to().point(rigs, mat, home(), width);
        boolean betweenTabs = !teams
                && attack.from().side() != home() && attack.from().side() != rival
                && attack.to().side() != home() && attack.to().side() != rival;
        int color = attack.from().side() == home() ? MatchArt.GLOW : GOLD;
        double head = Math.min(1, age / 500.0);
        // When both seats are hidden, keep the tracer below their names. The leading spark shows direction.
        for (int i = 0; i <= 20; i++) {
            double f = head * i / 20;
            int x = (int)Math.round(from.x() + (to.x() - from.x()) * f);
            int y = (int)Math.round(from.y() + (to.y() - from.y()) * f
                    + (betweenTabs ? 10 * 4 * f * (1-f) : 0));
            g.fill(x-1,y-1,x+2,y+2,0xFF25251F);
            g.fill(x,y,x+2,y+1,color);
            if (i == 20) g.fill(x-2,y-2,x+3,y+3,head >= 1 ? IMPACT : color);
        }
        if (head >= 1) {
            int radius = 3 + (int)((age-500)/85);
            g.outline((int)to.x()-radius,(int)to.y()-radius,radius*2+1,radius*2+1,IMPACT);
        }
    }
    private void drawFuse(GuiGraphicsExtractor g){
        float remaining=seconds();
        float warning=num(data,"clockWarningTicks")>0?num(data,"clockWarningTicks")/20F:teams?15:20;
        Rect view=mat.viewport();
        if(remaining>=0 && remaining<=warning && !clockHeld)
            TurnFuse.draw(new CardCanvas.Gui(g),font,(int)view.x()+12,(int)(view.x()+view.w())-25,
                    (int)view.cy(),remaining,warning,AnimationClock.millis());
    }
    private void waiting(GuiGraphicsExtractor g){
        BinderTheme.cover(g,width/2-130,Math.max(28,height/2-82),260,155);
        g.centeredText(font,teams?"TEAM DUOS":"COMMANDER",width/2,height/2-60,GOLD);
        var queue=data.getAsJsonArray("waiting");
        for(int i=0;i<4;i++){
            String text="Open seat";
            if(i<queue.size())text=queue.get(i).isJsonObject()?str(queue.get(i).getAsJsonObject(),"name"):queue.get(i).getAsString();
            g.centeredText(font,text,width/2,height/2-35+i*17,TEXT);
        }
        g.centeredText(font,"Waiting for four ready players",width/2,height/2+40,QUIET);
    }


    private record CardVisual(Identifier id,CardDefinition definition,ItemStack stack,ItemStack icon,String name) {}
    private final Map<String,CardVisual> visuals=new HashMap<>();
    private final Map<String,ItemStack> icons=new HashMap<>();
    private CardVisual visual(String id,boolean enchanted){
        if(id.isEmpty())return null;
        return visuals.computeIfAbsent(id+"#"+enchanted,key->{
            Identifier rid=Identifier.tryParse(id);CardDefinition card=rid==null?null:Cards.get(rid);
            if(card==null)return null;
            ItemStack icon=CardRenderer.placeholderArt(rid,card).copy();
            if(enchanted)icon.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE,true);
            return new CardVisual(rid,card,CardItem.of(rid,enchanted),icon,Cards.name(rid).getString());
        });
    }
    private ItemStack icon(String id){
        return icons.computeIfAbsent(id,key->{
            var item=BuiltInRegistries.ITEM.getValue(Identifier.parse(key));
            return new ItemStack(item==null?Items.PAPER:item);
        });
    }
    private boolean visible(Rect world){
        Rect screen=mat.screen(world),v=mat.viewport();
        return screen.x()+screen.w()>=v.x()&&screen.y()+screen.h()>=v.y()&&screen.x()<v.x()+v.w()&&screen.y()<v.y()+v.h();
    }
    private Hit add(Rect rect,int s,int mid,String kind,String art,boolean enchanted,String hint){
        Hit h=new Hit(rect,s,mid,kind,art,enchanted,hint,null);hits.add(h);return h;
    }
    private boolean hovered(Hit hit){
        Point p=mat.board(mouseX,mouseY);
        return mat.viewport().contains(mouseX,mouseY)&&hit.rect.contains(p.x(),p.y());
    }
    private void drawSeat(GuiGraphicsExtractor g,Seat rig){
        if(!visible(rig.bounds()))return;
        int s=rig.owner();JsonObject p=seat(s);
        boolean active=teams?(s&1)==num(data,"activeTeam"):s==num(data,"current");
        drawRow(g,rig,false);drawRow(g,rig,true);
        Point hero=rig.hero();
        int hx=(int)hero.x()-HERO/2,hy=(int)hero.y()-HERO/2;
        int cls=cardClass(p).frameColor();
        Hit h=add(new Rect(hx-4,hy-4,HERO+8,HERO+11),s,0,"hero","",false,
                str(p,"name")+" - "+num(p,"health")+" Health"+(teams?" - shared team Health":""));
        if(visible(h.rect)){
            MatchArt.octagon(g,hx-4,hy-4,HERO+8,0xFF101511);
            MatchArt.octagon(g,hx-3,hy-3,HERO+6,cardClass(p).lightColor());
            MatchArt.octagon(g,hx-1,hy-1,HERO+2,cls);
            MatchArt.octagon(g,hx+4,hy+4,HERO-8,0xFF252A28);
            drawFace(g,p,hx+8,hy+8,HERO-16);
            if(active&&!flag(data,"over"))MatchArt.octagonOutline(g,hx-6,hy-6,HERO+12,turnColor(),2);
            if(s==me()&&canAct()&&!options("attack0").isEmpty())MatchArt.octagonOutline(g,hx-5,hy-5,HERO+10,MatchArt.GLOW,2);
            if(flag(p,"frozen")){MatchArt.octagon(g,hx,hy,HERO,0x5559BFE8);MatchArt.octagonOutline(g,hx-2,hy-2,HERO+4,0xFFB7E9FF,2);}
            if(valid(s,0))MatchArt.octagonOutline(g,hx-7,hy-7,HERO+14,hovered(h)?0xFFFFFFFF:GOLD,3);
            else if(hovered(h))MatchArt.octagonOutline(g,hx-5,hy-5,HERO+10,0xFFFFFFFF,1);
            if(!alive(s)){MatchArt.octagon(g,hx,hy,HERO,0xC0202321);label(g,"OUT",hx+HERO/2,hy+HERO/2-4,HERO,TEXT);}
            if(!teams)MatchArt.heart(g,font,hx+(HERO-24)/2,hy-6,24,num(p,"health"),num(p,"health")<num(p,"maxHealth")?0xFFFF8A82:0xFFFFFFFF);
            if(num(p,"heroAttack")>0)MatchArt.attack(g,font,hx-8,hy+HERO-16,24,num(p,"heroAttack"),0xFFFFFFFF);
            if (!teams) flash(g,s,0,hero);
        }
        String role = s == me() ? "You" : teams ? (enemy(s) ? "Enemy" : "Partner") : cardClass(p).displayName().getString();
        if (teams && flag(p,"ready")) role = "Ready";
        String name = str(p,"name");
        Rect namePlate = rig.nameplate(teams, Math.max(font.width(name), font.width(role)));
        int left = (int)namePlate.x(), top = (int)namePlate.y(), w = (int)namePlate.w();
        g.fill(left, top, left+w, top+30, 0xB527302B);
        label(g, name, (int)namePlate.cx(), top+4, w-8, TEXT);
        label(g, role, (int)namePlate.cx(), top+16, w-8, GOLD);
        if (teams && enemy(s)) {
            add(namePlate,s,-1,"focus","",false,"Choose "+name+" for effects against one enemy.");
            if(s==rival)g.outline(left,top,w,30,GOLD);
        }
        drawSocket(g,rig,false);drawSocket(g,rig,true);
        if(!teams)drawCommander(g,rig);

    }
    private void drawSeatResources(GuiGraphicsExtractor g){
        for(Seat rig:rigs){
            if(rig.owner()==me())continue;
            drawResources(g, seat(rig.owner()), mat.resources(rig, teams), -1);
        }
    }

    /** Same orb states and hand glyph as the duel, with each seat's own card back on its deck pile. */
    private void drawResources(GuiGraphicsExtractor g,JsonObject p,Rect space,int cost){
        int xp=num(p,"xp"),max=num(p,"maxXp"),deck=num(p,"deck"),held=num(p,"handCount");
        String number=xp+"/"+max+" XP";
        int cells=Math.clamp(Math.max(max,xp),0,Match.MAX_XP+2);
        int labelW=font.width(number),meterW=labelW+(cells>0?5+7*cells:0);
        String handText=Integer.toString(held),deckText=Integer.toString(deck);
        int countersW=11+font.width(handText)+13+15+font.width(deckText);
        int w=Math.max(meterW,countersW)+10;
        float scale=(float)Math.min(1,Math.min(space.w()/w,space.h()/31));
        if(scale<=0)return;
        g.pose().pushMatrix();
        g.pose().translate((float)space.cx()-w*scale/2,(float)space.cy()-31*scale/2);
        g.pose().scale(scale,scale);
        g.fill(0,0,w,31,0xEE242822);g.outline(0,0,w,31,0xFF6C5C43);
        int x=(w-meterW)/2;
        g.text(font,number,x,3,cost>xp?0xFFFF6861:xp>max?MatchArt.GOLD_LIGHT:MatchArt.XP,false);
        int first=x+labelW+5;
        boolean blink=cost>0 && (AnimationClock.millis()/260)%2==0;
        for(int i=0;i<cells;i++){
            MatchArt.Orb state=i>=max?MatchArt.Orb.BONUS:i<xp?MatchArt.Orb.FULL:MatchArt.Orb.SPENT;
            if(blink && i<xp){
                if(cost>xp)state=MatchArt.Orb.MISSING;
                else if(i>=xp-cost)state=MatchArt.Orb.PREVIEW;
            }
            MatchArt.orb(g,first+i*7,4,5,state);
        }
        x=(w-countersW)/2;
        MatchArt.handIcon(g,x,16);
        g.text(font,handText,x+11,17,held>=Match.MAX_HAND?0xFFFF6861:TEXT,false);
        int pile=x+11+font.width(handText)+13;
        if(deck>0){
            g.fill(pile+2,13,pile+12,26,MatchArt.WOOD_LIGHT);
            g.fill(pile+1,14,pile+11,27,MatchArt.WOOD_DARK);
            var sleeve=Identifier.tryParse(str(p,"sleeve"));
            CardRenderer.drawBack(g,Sleeves.get(sleeve==null?Sleeves.CLASSIC:sleeve),pile,15,9F/CardRenderer.WIDTH);
        }else g.outline(pile,15,9,13,QUIET);
        g.text(font,deckText,pile+15,17,TEXT,false);
        g.pose().popMatrix();
    }
    private int previewCost(){
        if(menu || inspecting() || choosing() || me()<0)return -1;
        int i=dragHand>=0?dragHand:selection.startsWith("play")?index:hoverHand;
        if(i>=0 && i<hand().size()){
            var card=Cards.get(Identifier.parse(str(hand().get(i).getAsJsonObject(),"id")));
            if(card!=null)return card.cost();
        }
        return -1;
    }
    private CardClass cardClass(JsonObject p){
        try{return CardClass.valueOf(str(p,"cls"));}catch(IllegalArgumentException ignored){return CardClass.NEUTRAL;}
    }
    private void drawSocket(GuiGraphicsExtractor g,Seat rig,boolean power){
        int s=rig.owner();var p=seat(s);
        Point hero=rig.hero();Point at=new Point(hero.x()+(power?57:-57),hero.y());
        int x=(int)at.x()-SOCKET/2,y=(int)at.y()-SOCKET/2;
        if(!visible(new Rect(x-5,y-9,SOCKET+10,SOCKET+18)))return;
        JsonObject tool=p.getAsJsonObject("tool");String art=power||tool==null?"":str(tool,"id");
        var heroPower=HeroPowers.of(cardClass(p));
        boolean ready=power&&s==me()&&canAct()&&flag(p.getAsJsonObject("power"),"usable")&&hasMove("power");
        int top = power && !rig.near() ? y-8 : y-2;
        int bottom = power && rig.near() ? y+SOCKET+8 : y+SOCKET+(tool!=null && !power ? 6 : 2);
        int pad = !power && tool!=null ? 4 : 2;
        Hit hit=add(new Rect(x-pad,top,SOCKET+2*pad,bottom-top),s,-1,power?"power":"tool",art,
                tool!=null&&flag(tool,"ench"),power?heroPower.displayName().getString()+": "+heroPower.description().getString():
                tool==null?"No tool equipped":"Tool - "+cardName(art)+" - "+num(tool,"atk")+" Attack / "+num(tool,"uses")+" Durability");
        MatchArt.octagon(g,x-2,y-2,SOCKET+4,0xFF0F1511);
        MatchArt.octagon(g,x,y,SOCKET,power?MatchArt.GOLD:tool==null?0xFF2A2B2F:flag(tool,"ench")?0xFFBD83EF:MatchArt.GOLD_DARK);
        MatchArt.octagon(g,x+3,y+3,SOCKET-6,0xFF323735);
        boolean hover = hovered(hit);
        if (hover || ready) MatchArt.octagonOutline(g,x,y,SOCKET,hover?0xFFFFFFFF:MatchArt.GLOW,hover?1:2);
        if(power){
            drawItem(g,icon(heroPower.icon().toString()),(int)at.x(),(int)at.y(),29);
            if(flag(p.getAsJsonObject("power"),"used"))MatchArt.octagon(g,x+2,y+2,SOCKET-4,0x99414942);
            if (hover || ready) g.nextStratum();
            MatchArt.cost(g,font,x+SOCKET/2-8,rig.near()?y+SOCKET-8:y-8,16,Match.POWER_COST);
        }else if(tool!=null){
            CardVisual v=visual(art,flag(tool,"ench"));
            if(v!=null)drawItem(g,v.icon,(int)at.x(),(int)at.y(),29);
            if (hover) g.nextStratum();
            MatchArt.attack(g,font,x-4,y+SOCKET-12,18,num(tool,"atk"),0xFFFFFFFF);
            MatchArt.uses(g,font,x+SOCKET-14,y+SOCKET-12,18,num(tool,"uses"));
        }
    }
    private void drawCommander(GuiGraphicsExtractor g,Seat rig){
        var p=seat(rig.owner());String id=str(p,"leader");CardVisual v=visual(id,flag(p,"leaderEnch"));if(v==null)return;
        Point hero=rig.hero();Point at=new Point(hero.x()-110,hero.y()-4);int x=(int)at.x()-19,y=(int)at.y()-26;
        Rect rect=new Rect(x,y,38,53);
        Hit hit=add(rect,rig.owner(),-1,"cast",id,flag(p,"leaderEnch"),"Commander: "+v.name+" - "+num(p,"commandCost")+" XP");
        if(!visible(rect))return;
        g.fill(x-2,y-2,x+40,y+55,0xFF352B15);
        CardRenderer.draw(g,font,v.stack,x,y,38F/CardRenderer.WIDTH);
        g.outline(x-2,y-2,42,57,flag(p,"deployed")?0xFF8C927E:GOLD);
        if(flag(p,"deployed"))g.fill(x,y,x+38,y+53,0x992D342D);
        label(g,flag(p,"deployed")?"In play":num(p,"commandCost")+" XP",(int)at.x(),y+54,70,GOLD);
        if(hovered(hit))g.outline(x-3,y-3,44,59,0xFFFFFFFF);
    }
    private void drawRow(GuiGraphicsExtractor g,Seat rig,boolean block){
        int s=rig.owner();var units=rows.getOrDefault(s*2+(block?1:0),List.of());
        Rect row=rig.row(block);
        int x=(int)row.x(),y=(int)row.y(),w=(int)row.w(),h=(int)row.h();
        g.fill(x,y,x+w,y+h,block?0x45434D50:0x185E705E);
        g.outline(x,y,w,h,block?0xFF697677:0xFF657267);
        label(g,block?"Blocks":"Mobs",x+20,(int)row.cy()-10,37,QUIET);
        label(g,units.size()+"/"+Match.rowLimit(block),x+20,(int)row.cy()+2,36,TEXT);
        for(int i=0;i<units.size();i++){
            Point at=rig.unit(block,units.size(),i);
            drawUnit(g,s,units.get(i),(int)at.x()-CARD_W/2,(int)at.y()-CARD_H/2);
        }
    }
    private void drawUnit(GuiGraphicsExtractor g,int s,MatchState.Mob unit,int x,int y){
        int mid=unit.mid;String id=unit.id.toString();boolean foil=unit.enchanted,block=unit.block;
        var v=visual(id,foil);if(v==null)return;
        Hit hit=new Hit(new Rect(x-4,y-4,CARD_W+8,CARD_H+12),s,mid,"unit",id,foil,v.name+" - "+(block?"":unit.attack+" Attack / ")+unit.health+" Health",unit);
        hits.add(hit);
        if(!visible(hit.rect))return;
        boolean hover=hovered(hit),ready=s==me()&&canAct()&&!options("attack"+mid).isEmpty();
        g.pose().pushMatrix();g.pose().translate(x,y);
        if(unit.shield)MatchArt.shieldFrame(g,CARD_W,CARD_H);
        g.fill(-1,-1,CARD_W+1,CARD_H+1,0xFF101510);
        g.fill(0,0,CARD_W,CARD_H,foil?0xFFB67BE0:v.definition.cardClass().frameColor());
        CardRenderer.drawBoardArt(new CardCanvas.Gui(g),v.id,v.definition,2,2,CARD_W-4,CARD_H-4);
        if(v.definition.rarity()==CardRarity.LEGENDARY)CardRenderer.drawLegendaryArtFrame(g,0,0,CARD_W,CARD_H);
        if(block)for(int xx:new int[]{0,CARD_W-5})for(int yy:new int[]{0,CARD_H-5}){
            g.fill(xx,yy,xx+5,yy+5,0xFFB7BBAE);g.fill(xx+2,yy+2,xx+3,yy+3,0xFF424B45);
        }
        if(foil)CardRenderer.drawDiagonalShimmer(g,2,2,CARD_W-4,CARD_H-4,6,0x65);
        long now=AnimationClock.millis();
        if(unit.frozen)MatchArt.frozenCoating(g,CARD_W,CARD_H,now,mid);
        if(unit.withered)MatchArt.witherDecay(g,CARD_W,CARD_H,now,mid);
        if(unit.totem)MatchArt.totemBlessing(g,CARD_W,CARD_H,now,mid);
        if(unit.invulnerable)MatchArt.invulnerableBarrier(g,CARD_W,CARD_H,now);
        if(ready)g.outline(-3,-3,CARD_W+6,CARD_H+6,MatchArt.GLOW);
        if(valid(s,mid))g.outline(-4,-4,CARD_W+8,CARD_H+8,hover?0xFFFFFFFF:GOLD);
        else if(hover||selection.equals("attack"+mid)&&s==me())g.outline(-2,-2,CARD_W+4,CARD_H+4,0xFFFFFFFF);
        if(!block)MatchArt.attack(g,font,-6,CARD_H-12,20,unit.attack,unit.attack>v.definition.attack()?MatchArt.GLOW:0xFFFFFFFF);
        MatchArt.heart(g,font,CARD_W-14,CARD_H-12,20,unit.health,unit.health<unit.maxHealth?0xFFFF8A82:0xFFFFFFFF);
        if(unit.shield)MatchArt.shieldBadge(g,CARD_W/2-5,-8);
        g.pose().popMatrix();
        flash(g,s,mid,new Point(x+CARD_W/2.0,y+CARD_H/2.0));
    }
    private void flash(GuiGraphicsExtractor g,int s,int mid,Point at){
        int value=damage.getOrDefault(s+":"+mid,0);long time=AnimationClock.millis()-flashAt;
        if(value==0||time>650)return;
        MatchArt.outlined(g,font,(value>0?"+":"")+value,(float)at.x(),(float)(at.y()-time/50.0),
                value>0?MatchArt.GLOW:0xFFFF7F70,1.5F,true);
    }
    private void drawItem(GuiGraphicsExtractor g,ItemStack item,int cx,int cy,int size){
        g.pose().pushMatrix();g.pose().translate(cx-size/2F,cy-size/2F);g.pose().scale(size/16F,size/16F);g.item(item,0,0);g.pose().popMatrix();
    }
    private void label(GuiGraphicsExtractor g,String value,int center,int y,int available,int color){
        CardRenderer.fitText(g,font,Component.literal(value),center,y,Math.max(10,available),color,true,false);
    }
    private String cardName(String id){var v=visual(id,false);return v==null?"Unknown card":v.name;}


    public final int[] commandBounds(int side){return specialBounds(side,"cast");}
    public final int[] powerBounds(int side){return specialBounds(side,"power");}
    public final int[] toolBounds(int side){return specialBounds(side,"tool");}
    private int[] specialBounds(int side,String kind){
        return hits.stream().filter(h->h.side==side&&h.kind.equals(kind)).findFirst().map(h->ints(mat.screen(h.rect))).orElse(null);
    }
    private int handTop(){return height-19-handHeight;}
    private int handBottom(){return height-14;} // Leave a line for battle history and targeting hints.
    private boolean insideHandBand(double y){return y>=handTop() && y<handBottom();}
    private HandSlot handPose(int i){
        HandSlot slot=handSlots.get(i);
        if(i==hoverHand){
            HandSlot raised=slot.raised(handTop());
            if(raised.contains(mouseX,mouseY))return raised;
        }
        return slot;
    }
    private void layoutHand(int mx,int my){
        int count=hand().size(),previous=hoverHand;
        handSlots.clear();
        for(int i=0;i<count;i++)handSlots.add(MultiplayerBoardLayout.handSlot(width,handTop(),handHeight,count,i));
        hoverHand=-1;
        if(dragHand>=0 || menu || inspecting() || choosing() || !insideHandBand(my))return;
        if(previous>=0 && previous<count && handSlots.get(previous).raised(handTop()).contains(mx,my))hoverHand=previous;
        else for(int i=count-1;i>=0;i--)if(handSlots.get(i).contains(mx,my)){hoverHand=i;break;}
    }
    private void drawHand(GuiGraphicsExtractor g,int mx,int my){
        if(handSlots.isEmpty())return;
        g.nextStratum();
        g.enableScissor(0,handTop(),width,handBottom());
        for(int i=0;i<handSlots.size();i++)if(i!=hoverHand&&i!=dragHand)drawHandCard(g,i,handSlots.get(i),false);
        if(hoverHand>=0)drawHandCard(g,hoverHand,handPose(hoverHand),true);
        if(dragHand>=0 && dragHand<handSlots.size() && Math.hypot(mx-pressX,my-pressY)<=4)
            drawHandCard(g,dragHand,handSlots.get(dragHand),false);
        g.disableScissor();
    }
    private void drawHandCard(GuiGraphicsExtractor g,int i,HandSlot slot,boolean hover){
        var raw=hand().get(i).getAsJsonObject();var v=visual(str(raw,"id"),flag(raw,"ench"));if(v==null)return;
        g.pose().pushMatrix();
        g.pose().translate((float)slot.cx(),(float)slot.cy());
        g.pose().rotate((float)slot.angle());
        g.pose().translate((float)-slot.w()/2,(float)-slot.h()/2);
        int w=(int)Math.round(slot.w()),h=(int)Math.round(slot.h());
        if(playable(i))g.outline(-2,-2,w+4,h+4,MatchArt.GLOW);
        CardRenderer.draw(g,font,v.stack,0,0,(float)slot.w()/CardRenderer.WIDTH);
        if(hover||selection.equals("play"+i))g.outline(-2,-2,w+4,h+4,hover?0xFFFFFFFF:GOLD);
        g.pose().popMatrix();
    }
    private void drawDragged(GuiGraphicsExtractor g,int i,int mx,int my){
        if(i<0||i>=hand().size())return;
        var raw=hand().get(i).getAsJsonObject();var v=visual(str(raw,"id"),flag(raw,"ench"));if(v==null)return;
        float scale=Math.min(.75F,Math.max(.4F,handHeight/(float)CardRenderer.HEIGHT));
        CardRenderer.draw(g,font,v.stack,mx-Math.round(CardRenderer.WIDTH*scale)/2,my-Math.round(CardRenderer.HEIGHT*scale)/2,scale);
    }
    private String turnTitle(){
        if(flag(data,"over")){
            int winner=data.has("winnerTeam")?num(data,"winnerTeam"):num(data,"winner");
            return winner<0?"Draw":teams?"Team "+RelayMatch.teamName(winner)+" wins!":str(seat(winner),"name")+" wins!";
        }
        if(teams)return "Turn "+num(data,"turn")+" - "+RelayMatch.teamName(num(data,"activeTeam"))+" acts";
        return "Round "+Math.max(1,num(data,"round"))+" · Turn "+num(data,"turn")+" - "+str(seat(num(data,"current")),"name");
    }
    private void header(GuiGraphicsExtractor g){
        g.fill(0,0,width,teams?20:44,0xFF232B27);
        String title=turnTitle(),timer=seconds()<0?"":(" · "+(int)Math.ceil(seconds())+"s");
        if(teams){
            label(g, "Team Duos", 44, 6, 80, GOLD);
            label(g, title+timer, width/2, 6, width-166, TEXT);
        }else{
            String left = attack == null ? "Commander" : "Turn " + num(data,"turn") + timer;
            label(g, left, 44, 4, 80, GOLD);
            int right = previewReturn == null ? width - 6 : width - 88;
            label(g, attack == null ? title+timer : attack.caption(), (88+right)/2, 4, right-88,
                    attack == null ? TEXT : GOLD);
            for(int s=0;s<4;s++)if(s!=home())drawRival(g,s);
        }
    }
    private void drawFace(GuiGraphicsExtractor g,JsonObject p,int x,int y,int size){
        var connection=minecraft.getConnection();
        String uuid=str(p,"uuid");
        var info=connection==null || uuid.isEmpty()?null:connection.getPlayerInfo(UUID.fromString(uuid));
        if(info!=null)PlayerFaceExtractor.extractRenderState(g,info.getSkin(),x,y,size);
        else drawItem(g,icon(HeroPowers.of(cardClass(p)).icon().toString()),x+size/2,y+size/2,size);
    }
    private void drawRival(GuiGraphicsExtractor g,int side){
        int[] rect=rivalTabBounds(side);int x=rect[0],y=rect[1],w=rect[2],h=rect[3];
        JsonObject p=seat(side);boolean selected=side==rival;
        boolean hasTarget=!selection.isEmpty() && options(selection).asList().stream()
                .anyMatch(t->t.getAsString().startsWith(side+":"));
        g.fill(x,y,x+w,y+h,selected?0xFF475249:0xFF303A34);
        boolean acting = side == num(data, "current") && !flag(data, "over");
        g.outline(x,y,w,h,acting ? turnColor() : hasTarget ? MatchArt.GLOW : 0xFF687467);
        if (selected) g.fill(x+2,y+h-3,x+w-2,y+h-1,GOLD);
        int portrait=Math.min(18,h-4);
        drawFace(g,p,x+3,y+(h-portrait)/2,portrait);
        int center=x+(w+portrait)/2;
        String name=(acting?"▶ ":"")+str(p,"name");
        label(g,name+(alive(side)?"  ♥ "+num(p,"health"):" · Out"),center,y+2,w-portrait-7,TEXT);
        String detail;
        int color = QUIET;
        if (attack != null && attack.from().side() == side) {
            detail = "Attacks · " + attack.from().subject();
            color = GOLD;
        } else if (attack != null && attack.to().side() == side) {
            detail = "Target · " + attack.to().subject();
            color = IMPACT;
        } else {
            detail = rows.getOrDefault(side*2,List.of()).size()+" mobs · "+rows.getOrDefault(side*2+1,List.of()).size()+" blocks";
        }
        label(g,detail,center,y+12,w-portrait-7,color);
    }
    private void drawFooter(GuiGraphicsExtractor g){
        int top=(int)(mat.viewport().y()+mat.viewport().h());
        g.fill(0,top,width,height,0xFF262925);
        int bank=Math.min(104,width/4);
        if(me()>=0){
            drawResources(g,seat(me()),new Rect(4,top+2,bank-8,height-24-top),previewCost());
        }
        String turn=teams?(num(data,"activeTeam")== (home()&1)?"Your team’s turn":"Enemy turn"):
                (num(data,"current")==me()?"Your turn":str(seat(num(data,"current")),"name")+"’s turn");
        if(me()<0)turn="Spectating";
        if(flag(data,"over"))turn="Match ended";
        int controlWidth=Math.min(96,width/4);
        if(height-33>top)label(g,turn,width-controlWidth/2-5,height-33,controlWidth,GOLD);
        String hint=notice;
        int tab=rivalAt(mouseX,mouseY);
        if(tab>=0){
            JsonObject p=seat(tab);
            hint=str(p,"name")+" · XP "+num(p,"xp")+"/"+num(p,"maxXp")+" · Hand "+num(p,"handCount")+" · Deck "+num(p,"deck");
            if(p.has("strikes") && me()>=0)hint+=" · Commander damage to you "+p.getAsJsonArray("strikes").get(me()).getAsInt()+"/"+CommanderMatch.LETHAL;
        }else if(hint.isEmpty()){
            Hit hover=hit(mouseX,mouseY);
            if(hover!=null)hint=hover.hint;
            else if(!selection.isEmpty())hint="Choose a highlighted target";
            else if(teams&&flag(data,"ready"))hint="Waiting for your partner";
            else if(!publicLines.isEmpty())hint=publicLines.getLast();
            else if(previewReturn!=null)hint="Layout preview · Esc to return";
        }
        if(!hint.isEmpty())label(g,hint,width/2,height-12,Math.max(30,width-2*bank-10),QUIET);
    }
    private List<String> publicLog(){return publicLines;}
    public final void receiveHistory(CommanderHistoryPayload packet) {
        if (!teams && packet.session().equals(str(data,"session"))) commanderLog.append(packet.offset(),packet.entries());
    }
    private void drawLog(GuiGraphicsExtractor g){
        if (!teams) { commanderLog.draw(g,font,width,height); return; }
        int w=Math.min(220,width-30),x=6,bottom=height-24,y=Math.max(24,bottom-132);
        g.nextStratum();g.fill(x,y,x+w,bottom,0xF0232B25);g.outline(x,y,w,bottom-y,0xFFAC8755);
        g.text(font,"Battle log",x+7,y+7,GOLD,false);
        var log=publicLog();int at=bottom-4;
        for(int i=log.size()-1;i>=0&&at>y+22;i--){
            var wrapped=font.split(Component.literal(log.get(i)),w-14);
            int total=wrapped.size()*10+3;if(at-total<y+19)break;
            at-=total;int lineY=at;
            for(var line:wrapped){g.text(font,line,x+7,lineY,TEXT,false);lineY+=10;}
        }
        if(log.isEmpty())g.text(font,"No public actions yet.",x+7,y+25,QUIET,false);
    }
    private void refreshInspectedCreates(){
        Identifier id=inspected.get(craftbound.component.ModComponents.CARD_ID);
        var catalogue=CardCatalog.current();
        var language=net.minecraft.locale.Language.getInstance();
        if(Objects.equals(id,inspectedId)&&catalogue==inspectedCatalogue&&language==inspectedLanguage)return;
        inspectedId=id;inspectedCatalogue=catalogue;inspectedLanguage=language;
        // One preview per outcome, even when several effects create the same card.
        Set<Identifier> outcomes=new LinkedHashSet<>();
        for(var association:CardInspectScreen.associations(id))outcomes.add(association.id());
        inspectedCreates=outcomes.stream().map(CardItem::of).toList();
        inspectedPage=0;
    }
    private void openInspect(ItemStack card){
        inspected=card.copy();inspectedPage=0;inspectedPages=1;
        inspectedMain=inspectedPane=inspectedPrevious=inspectedNext=null;
        dragHand=-1;targetDrag=false;
    }
    private void closeInspect(){
        inspected=ItemStack.EMPTY;
        inspectedMain=inspectedPane=inspectedPrevious=inspectedNext=null;
    }
    private void drawInspect(GuiGraphicsExtractor g,int mouseX,int mouseY){
        refreshInspectedCreates();
        g.nextStratum();g.fill(0,0,width,height,0xDB101612);
        boolean related=!inspectedCreates.isEmpty();
        float availableWidth=related?(width-68F)/(CardRenderer.WIDTH*1.78F):(width-40F)/CardRenderer.WIDTH;
        float scale=Math.max(.1F,Math.min(1.5F,Math.min((height-36F)/CardRenderer.HEIGHT,availableWidth)));
        int w=Math.round(CardRenderer.WIDTH*scale),h=Math.round(CardRenderer.HEIGHT*scale);
        float previewScale=Math.min(1F,scale*.78F);
        int pw=Math.round(CardRenderer.WIDTH*previewScale),ph=Math.round(CardRenderer.HEIGHT*previewScale);
        int mainX=(width-w-(related?pw+28:0))/2,mainY=Math.max(6,(height-h)/2-6);
        inspectedMain=new Rect(mainX,mainY,w,h);
        inspectedPane=inspectedPrevious=inspectedNext=null;
        List<CardRenderer.HoverRegion> regions=new ArrayList<>();
        if(related){
            int perPage=Math.max(1,Math.min(2,(height-62)/(ph+12)));
            inspectedPages=Math.max(1,(inspectedCreates.size()+perPage-1)/perPage);
            inspectedPage=Math.clamp(inspectedPage,0,inspectedPages-1);
            int first=inspectedPage*perPage,last=Math.min(inspectedCreates.size(),first+perPage),count=last-first;
            int panelH=20+count*ph+Math.max(0,count-1)*12+(inspectedPages>1?24:8);
            int paneX=mainX+w+16,paneY=Math.max(6,(height-22-panelH)/2),paneW=pw+12;
            inspectedPane=new Rect(paneX,paneY,paneW,panelH);
            g.fill(paneX,paneY,paneX+paneW,paneY+panelH,0xF0222923);
            g.outline(paneX,paneY,paneW,panelH,0xFF96774B);
            g.text(font,Component.translatable("card_ui.craftbound.creates"),paneX+6,paneY+6,GOLD);
            for(int i=first;i<last;i++)
                regions.addAll(CardRenderer.draw(g,font,inspectedCreates.get(i),paneX+6,paneY+20+(i-first)*(ph+12),previewScale));
            if(inspectedPages>1){
                int controlsY=paneY+panelH-20;
                inspectedPrevious=new Rect(paneX+4,controlsY,18,16);
                inspectedNext=new Rect(paneX+paneW-22,controlsY,18,16);
                drawInspectPageButton(g,inspectedPrevious,"<",inspectedPage>0,mouseX,mouseY);
                drawInspectPageButton(g,inspectedNext,">",inspectedPage+1<inspectedPages,mouseX,mouseY);
                label(g,(inspectedPage+1)+" / "+inspectedPages,paneX+paneW/2,controlsY+4,Math.max(16,paneW-48),QUIET);
            }
        }else inspectedPages=1;
        regions.addAll(CardRenderer.draw(g,font,inspected,mainX,mainY,scale));
        label(g,related&&inspectedPages>1?"Wheel: more cards - Click outside or Esc to return":"Click outside or Esc to return",
                width/2,height-15,Math.max(20,width-16),TEXT);
        // Keep the match screen and its network routing alive while inspecting either column.
        for(var region:regions)if(region.contains(mouseX,mouseY)){
            craftbound.client.Tooltips.show(g,font,region.lines(),mouseX,mouseY);break;
        }
    }
    private void drawInspectPageButton(GuiGraphicsExtractor g,Rect rect,String text,boolean enabled,int mx,int my){
        int x=(int)rect.x(),y=(int)rect.y(),w=(int)rect.w(),h=(int)rect.h();
        g.fill(x,y,x+w,y+h,enabled&&rect.contains(mx,my)?0xFF665239:0xFF332E24);
        g.outline(x,y,w,h,enabled?0xFFA28453:0xFF514D42);
        g.centeredText(font,text,x+w/2,y+4,enabled?TEXT:0xFF777777);
    }
    private boolean inspectClick(MouseButtonEvent event){
        if(event.button()==InputConstants.MOUSE_BUTTON_RIGHT){closeInspect();return true;}
        if(event.button()!=InputConstants.MOUSE_BUTTON_LEFT)return true;
        if(inspectedPrevious!=null&&inspectedPrevious.contains(event.x(),event.y())){
            inspectedPage=Math.max(0,inspectedPage-1);return true;
        }
        if(inspectedNext!=null&&inspectedNext.contains(event.x(),event.y())){
            inspectedPage=Math.min(inspectedPages-1,inspectedPage+1);return true;
        }
        if(inspectedMain!=null&&inspectedMain.contains(event.x(),event.y())
                ||inspectedPane!=null&&inspectedPane.contains(event.x(),event.y()))return true;
        closeInspect();return true;
    }
    private Hit hit(double x,double y){
        if(!mat.viewport().contains(x,y))return null;
        Point p=mat.board(x,y);
        for(int i=hits.size()-1;i>=0;i--)if(hits.get(i).rect.contains(p.x(),p.y()))return hits.get(i);
        return null;
    }
    private int handAt(double x,double y){
        if(!insideHandBand(y))return -1;
        if(hoverHand>=0 && hoverHand<handSlots.size() && handPose(hoverHand).contains(x,y))return hoverHand;
        for(int i=handSlots.size()-1;i>=0;i--)if(i!=hoverHand && handSlots.get(i).contains(x,y))return i;
        return -1;
    }
    private Point selectionOrigin(){
        if(selection.startsWith("play")&&index<handSlots.size()){
            HandSlot slot=handPose(index);return new Point(slot.cx(),slot.cy());
        }
        String kind=selection.equals("power")?"power":selection.equals("cast")?"cast":index==0?"hero":"unit";
        return hits.stream().filter(h->h.side==me()&&h.kind.equals(kind)&&(kind.equals("unit")?h.mid==index:true))
                .findFirst().map(h->mat.screen(h.rect.x()+h.rect.w()/2,h.rect.y()+h.rect.h()/2))
                .orElse(new Point(width/2.0,height-40));
    }
    private void inspect(Hit h){
        if(h==null||h.art.isEmpty())return;
        var v=visual(h.art,h.enchanted);if(v!=null)openInspect(v.stack);
    }
    private void press(Hit h){
        if(h==null)return;
        if(h.kind.equals("focus")){
            if(enemy(h.side)&&alive(h.side)){
                focusRival(h.side);
                notice="Focused rival: "+str(seat(rival),"name");
            }
            return;
        }
        if(!selection.isEmpty()&&(h.kind.equals("hero")||h.kind.equals("unit"))){aim(h);return;}
        if(h.side!=me()||!canAct())return;
        switch(h.kind){
            case "hero","unit" -> {select(MatchActionPayload.ATTACK,h.mid,"attack"+h.mid,true);targetDrag=!selection.isEmpty();}
            case "power" -> {if(!flag(seat(me()).getAsJsonObject("power"),"usable")){notice="Your hero power is not available.";return;}var power=HeroPowers.of(cardClass(seat(me())));select(MatchActionPayload.POWER,0,"power",HeroPowers.targeted(power));targetDrag=!selection.isEmpty();}
            case "cast" -> {
                var p=seat(me());
                if(flag(p,"deployed")){notice="Your commander is already in play.";return;}
                if(!hasMove("cast")){notice="Your commander cannot be summoned right now.";return;}
                if(num(p,"commandCost")>num(p,"xp")){notice="Not enough XP to summon your commander.";return;}
                if(!legalRival(p,rival)){
                    if(p.has("rivals")&&!p.getAsJsonArray("rivals").isEmpty())focusRival(p.getAsJsonArray("rivals").get(0).getAsInt());
                    notice="Rival changed. Select your commander again to confirm.";return;
                }
                select(CommanderTables.CAST,0,"cast",CardEffects.targetKind(Identifier.parse(str(p,"leader")))!=Target.Kind.NONE&&!options("cast").isEmpty());
                targetDrag=!selection.isEmpty();
            }
            default -> {}
        }
    }
    @Override public boolean mouseClicked(MouseButtonEvent event,boolean twice){
        if(choosing()){
            if(event.button()==InputConstants.MOUSE_BUTTON_LEFT){
                int picked=cardChoice.click(choiceState,event.x(),event.y());
                if(picked>=0)send(teams?MatchActionPayload.CHOOSE_CARD:CommanderTables.CHOOSE_CARD,picked,-1,0);
            }
            return true;
        }
        if(!inspected.isEmpty())return inspectClick(event);
        if(menu)return menuClick(event);
        if (showLog && !teams) {
            showLog = commanderLog.click(width,height,event.x(),event.y(),event.button()==InputConstants.MOUSE_BUTTON_LEFT);
            return true;
        }
        if (canHover() && cardHover.valid(data, width, height)
                && handAt(event.x(),event.y()) < 0 && cardHover.click(event.x(), event.y(), event.button() == InputConstants.MOUSE_BUTTON_LEFT)) return true;
        cardHover.clear();
        if(super.mouseClicked(event,twice))return true;
        int tab=rivalAt(event.x(),event.y());
        if(tab>=0 && event.button()==InputConstants.MOUSE_BUTTON_LEFT){focusRival(tab);return true;}
        if(showLog&&event.x()>=6&&event.x()<6+Math.min(220,width-30)&&event.y()>=Math.max(24,height-156)&&event.y()<height-24){showLog=false;return true;}
        int hand=handAt(event.x(),event.y());Hit hit=hit(event.x(),event.y());
        if(event.button()==InputConstants.MOUSE_BUTTON_RIGHT){
            if(!selection.isEmpty()||dragHand>=0){reset();return true;}
            if(hand>=0){var raw=hand().get(hand).getAsJsonObject();var v=visual(str(raw,"id"),flag(raw,"ench"));if(v!=null)openInspect(v.stack);}
            else inspect(hit);
            return true;
        }
        if(event.button()!=InputConstants.MOUSE_BUTTON_LEFT)return false;
        pressX=event.x();pressY=event.y();
        if(hand>=0){dragHand=hand;return true;}
        if(hit!=null){press(hit);return true;}
        if(mat.viewport().contains(event.x(),event.y()))return true;
        return false;
    }
    @Override public boolean mouseDragged(MouseButtonEvent event,double dx,double dy){
        if(choosing())return true;
        if(!inspected.isEmpty())return true;
        if(menu)return true;
        int tab=rivalAt(event.x(),event.y());
        if(tab>=0 && (targetDrag||dragHand>=0))focusRival(tab);
        if(dragHand>=0||targetDrag)return true;
        return super.mouseDragged(event,dx,dy);
    }
    @Override public boolean mouseReleased(MouseButtonEvent event){
        if(choosing())return true;
        if(!inspected.isEmpty())return true;
        if(menu)return true;
        if(dragHand>=0){
            int card=dragHand;dragHand=-1;
            boolean dragged=Math.hypot(event.x()-pressX,event.y()-pressY)>4;
            int tab=rivalAt(event.x(),event.y());
            if(tab>=0){
                focusRival(tab);
                if(playable(card)&&!options("play"+card).isEmpty())
                    select(MatchActionPayload.PLAY,card,"play"+card,true);
            }else if(!dragged||mat.viewport().contains(event.x(),event.y()))play(card,hit(event.x(),event.y()));
            return true;
        }
        if(targetDrag){targetDrag=false;if(Math.hypot(event.x()-pressX,event.y()-pressY)>4)aim(hit(event.x(),event.y()));return true;}
        return super.mouseReleased(event);
    }
    @Override public boolean mouseScrolled(double x,double y,double horizontal,double vertical){
        if(choosing())return true;
        if(!inspected.isEmpty()){
            if(vertical!=0&&inspectedPages>1)inspectedPage=Math.clamp(inspectedPage+(vertical<0?1:-1),0,inspectedPages-1);
            return true;
        }
        if (showLog && !teams) { commanderLog.scroll(width,height,vertical); return true; }
        if (canHover() && cardHover.valid(data, width, height) && handAt(x,y) < 0 && cardHover.scroll(x, y, vertical)) return true;
        if(menu||mat.viewport().contains(x,y))return true;
        return super.mouseScrolled(x,y,horizontal,vertical);
    }
    @Override public boolean keyPressed(KeyEvent event){
        if (showLog && !teams) {
            if (event.isEscape()) showLog=false;
            else if (event.key()==InputConstants.KEY_LEFT) commanderLog.stepRound(false);
            else if (event.key()==InputConstants.KEY_RIGHT) commanderLog.stepRound(true);
            else if (event.key()==InputConstants.KEY_UP) commanderLog.scroll(width,height,1);
            else if (event.key()==InputConstants.KEY_DOWN) commanderLog.scroll(width,height,-1);
            return true;
        }
        if(choosing()){if(event.isEscape())onClose();return true;}
        if(!inspected.isEmpty()){
            if(event.isEscape())closeInspect();
            return true;
        }
        if(event.isEscape()){
            if(dragHand>=0||!selection.isEmpty()){reset();return true;}
            if(showLog){showLog=false;return true;}
            if(previewReturn!=null||me()<0||flag(data,"over")||data.has("waiting"))onClose();
            else {menu=!menu;confirm=false;}
            return true;
        }
        return menu||super.keyPressed(event);
    }
    private Rect menuButton(int row){return new Rect(width/2-75,height/2-23+row*24,150,20);}
    private void drawMenu(GuiGraphicsExtractor g,int mx,int my){
        g.nextStratum();g.fill(0,0,width,height,0xB0000000);
        BinderTheme.cover(g,width/2-88,height/2-47,176,106);
        label(g,"Match menu",width/2,height/2-39,160,GOLD);
        String[] names={"Resume","Back to world",confirm?"Confirm concede":"Concede"};
        for(int i=0;i<names.length;i++){
            Rect rect=menuButton(i);int x=(int)rect.x(),y=(int)rect.y();
            g.fill(x,y,x+150,y+20,rect.contains(mx,my)?0xFF665239:0xFF332E24);
            g.outline(x,y,150,20,0xFFA28453);
            label(g,names[i],width/2,y+6,145,i==2?0xFFFFAAAA:TEXT);
        }
    }
    private boolean menuClick(MouseButtonEvent event){
        if(event.button()!=InputConstants.MOUSE_BUTTON_LEFT)return true;
        if(menuButton(0).contains(event.x(),event.y())){menu=false;confirm=false;}
        else if(menuButton(1).contains(event.x(),event.y())){menu=false;onClose();}
        else if(menuButton(2).contains(event.x(),event.y())){
            if(confirm){menu=false;send(teams?RelayMatch.RETIRE:MatchActionPayload.CONCEDE,0,-1,0);}else confirm=true;
        }
        return true;
    }
    @Override public void onClose(){
        if(!inspected.isEmpty()){closeInspect();return;}
        if(!selection.isEmpty()){reset();return;}
        if(previewReturn!=null){minecraft.gui.setScreen(previewReturn);return;}
        super.onClose();
    }
    @Override public boolean isPauseScreen(){return false;}
}

