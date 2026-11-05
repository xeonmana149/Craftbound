package craftbound.client.match;

import craftbound.game.CommanderHistory.Entry;
import craftbound.game.CommanderHistory.Kind;
import java.util.ArrayList;
import java.util.List;
import java.util.NavigableMap;
import java.util.TreeMap;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import static craftbound.client.match.MultiplayerBoardLayout.Rect;

/** The public journal stays available while the table plays on. Round 0 means follow the live round. */
final class CommanderLogPanel {
    private static final int TEXT=0xFFF4EDDE, MUTED=0xFFBAC4BB, GOLD=0xFFF2CF83;
    private final NavigableMap<Integer,List<Entry>> rounds = new TreeMap<>();
    private record Line(int y, Kind kind, FormattedCharSequence text) {}
    private List<Line> lines = List.of();
    private int received, latest, selected, scroll, contentHeight, lastWidth;
    private boolean details, dirty = true, followBottom = true;
    private Language language;

    int latestRound() { return latest; }

    void append(int offset, List<Entry> entries) {
        if (offset < 0 || offset > received) return;
        for (int i = received-offset; i < entries.size(); i++) {
            Entry entry = entries.get(i);
            if (selected == 0 && entry.round() > latest && !followBottom && latest > 0) selected = latest;
            latest = Math.max(latest, entry.round());
            rounds.computeIfAbsent(entry.round(), unused -> new ArrayList<>()).add(entry);
            if (selected == 0 || selected == entry.round()) dirty = true;
            received++;
        }
    }
    void preview(Entry entry) { append(received, List.of(entry)); }
    int round() { return selected == 0 ? latest : selected; }
    private void select(int value) {
        selected = value == latest ? 0 : value;
        scroll = 0;
        followBottom = selected == 0;
        dirty = true;
    }

    void stepRound(boolean forward) {
        Integer step = forward ? rounds.higherKey(round()) : rounds.lowerKey(round());
        if (step != null) select(step);
    }
    private static Rect panel(int width, int height) {
        int w = Math.min(352,width-12), h = Math.min(258,height-30);
        return new Rect(6,height-24-h,w,h);
    }
    private static Rect previous(Rect p) { return new Rect(p.x()+8,p.y()+23,18,15); }
    private static Rect next(Rect p) { return new Rect(p.x()+87,p.y()+23,18,15); }
    private static Rect toggle(Rect p) { return new Rect(p.x()+p.w()-83,p.y()+23,75,15); }
    private static Rect close(Rect p) { return new Rect(p.x()+p.w()-24,p.y()+4,16,15); }

    void draw(GuiGraphicsExtractor g, Font font, int width, int height) {
        Rect p = panel(width,height);
        int x=(int)p.x(), y=(int)p.y(), w=(int)p.w(), h=(int)p.h(), bodyY=y+42, bodyH=h-48;
        if (dirty || lastWidth != w || language != Language.getInstance()) rebuild(font,w);
        int limit = Math.max(0,contentHeight-bodyH);
        scroll = followBottom ? limit : Math.clamp(scroll,0,limit);
        g.nextStratum();
        g.fill(x,y,x+w,y+h,0xFF252D29);
        g.outline(x,y,w,h,0xFFAC8755);
        g.text(font,"Battle log",x+9,y+7,GOLD,false);
        button(g,font,close(p),"×",true);
        button(g,font,previous(p),"<",rounds.lowerKey(round())!=null);
        g.centeredText(font,"Round "+Math.max(1,round()),x+56,y+26,TEXT);
        button(g,font,next(p),">",rounds.higherKey(round())!=null);
        button(g,font,toggle(p),details?"Details: on":"Details: off",true);
        g.fill(x+6,bodyY-2,x+w-6,bodyY-1,0xFF6A6450);
        g.enableScissor(x+5,bodyY,x+w-5,bodyY+bodyH);
        if (lines.isEmpty()) g.text(font,"Waiting for the first turn.",x+10,bodyY+4,MUTED,false);
        for (Line line : lines) {
            int at = bodyY + line.y() - scroll;
            if (at > bodyY+bodyH) break;
            if (at+font.lineHeight < bodyY) continue;
            boolean turn = line.kind()==Kind.TURN, detail = line.kind()==Kind.DETAIL;
            if (turn) g.fill(x+7,at-2,x+w-13,at+font.lineHeight+1,0xFF38443D);
            g.text(font,line.text(),x+(detail || line.kind()==Kind.OUTCOME?18:10),at,turn?GOLD:detail?MUTED:TEXT,false);
        }
        g.disableScissor();
        if (limit>0) {
            int thumb=Math.max(9,bodyH*bodyH/contentHeight);
            int at=bodyY+(bodyH-thumb)*scroll/limit;
            g.fill(x+w-6,bodyY,x+w-4,bodyY+bodyH,0xFF424C45);
            g.fill(x+w-6,at,x+w-4,at+thumb,0xFFB7AC87);
        }
    }
    private void rebuild(Font font,int width) {
        List<Line> wrapped = new ArrayList<>();
        int y=3;
        for (Entry e : rounds.getOrDefault(round(),List.of())) {
            if (e.kind()==Kind.DETAIL && !details) continue;
            boolean turn=e.kind()==Kind.TURN;
            if (turn && !wrapped.isEmpty()) y+=5;
            String text=turn?"Turn "+e.turn()+" · "+e.text():e.text();
            for (var line : font.split(Component.literal(text),width-(e.kind()==Kind.DETAIL || e.kind()==Kind.OUTCOME?34:26))) {
                wrapped.add(new Line(y,e.kind(),line));
                y+=font.lineHeight+1;
            }
            y+=turn?4:2;
        }
        lines=wrapped; contentHeight=y; lastWidth=width; dirty=false; language=Language.getInstance();
    }
    private static void button(GuiGraphicsExtractor g,Font font,Rect r,String label,boolean enabled) {
        int x=(int)r.x(),y=(int)r.y(),w=(int)r.w(),h=(int)r.h();
        g.fill(x,y,x+w,y+h,enabled?0xFF454F45:0xFF303832);
        g.centeredText(font,label,x+w/2,y+3,enabled?TEXT:0xFF929990);
    }
    /** Return whether to keep the journal open. Never pass a panel click through to the field. */
    boolean click(int width,int height,double x,double y,boolean left) {
        Rect p=panel(width,height);
        if (!p.contains(x,y) || left && close(p).contains(x,y)) return false;
        if (left && previous(p).contains(x,y)) stepRound(false);
        else if (left && next(p).contains(x,y)) stepRound(true);
        else if (left && toggle(p).contains(x,y)) { details=!details; dirty=true; }
        return true;
    }
    void scroll(int width,int height,double delta) {
        if (delta==0) return;
        int limit=Math.max(0,contentHeight-((int)panel(width,height).h()-48));
        scroll = Math.clamp(scroll+(delta<0?1:-1)*Math.max(1,(int)Math.round(Math.abs(delta)*22)),0,limit);
        followBottom=scroll==limit;
    }
}
