package craftbound.game;

import com.mojang.blaze3d.platform.InputConstants;
import craftbound.card.CardClass;
import craftbound.card.Cards;
import craftbound.client.match.MultiplayerBoardScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import java.util.*;
import net.minecraft.client.input.*;

/** Checks the real match screen at a full board, including pinned hands and Commander focus. */
final class MultiplayerBoardScenario {
    static void check(MultiplayerBoardScreen sc) {
        int[] viewport=sc.boardViewport();
        var data=sc.snapshot();
        var players=data.getAsJsonArray("players");
        var rects=new ArrayList<int[]>();
        for(int side=0;side<4;side++){
            int[] area=sc.seatAreaBounds(side);
            if(area==null){
                if(sc.rivalTabBounds(side)==null || sc.targetBounds(side,0)!=null)throw new AssertionError("Hidden rival must have only a public tab");
                continue;
            }
            inside(area,viewport,"Seat "+side+" is clipped");
            inside(sc.targetBounds(side,0),viewport,"Hero "+side+" is clipped at Fit");
            for(var raw:players.get(side).getAsJsonObject().getAsJsonArray("board")){
                int mid=raw.getAsJsonObject().get("mid").getAsInt();
                var bounds=sc.targetBounds(side,mid);
                inside(bounds,viewport,"Board card is clipped at Fit");
                for(var other:rects)if(overlap(bounds,other))
                    throw new AssertionError("Board cards overlap at Fit");
                rects.add(bounds);
            }
        }
        int[] hand=sc.handCardBounds(0);
        if(hand==null)throw new AssertionError("Fixed local hand missing");
        int own=data.get("you").getAsInt();
        checkHoverFaces(sc, own);
        checkLinkedHandHover(sc, own);
        int[] ownBefore=sc.targetBounds(own,0);
        sc.mouseScrolled(viewport[0]+viewport[2]/2.0,viewport[1]+viewport[3]/2.0,0,3);
        if(!Arrays.equals(hand,sc.handCardBounds(0)) || !Arrays.equals(ownBefore,sc.targetBounds(own,0)))
            throw new AssertionError("Scroll displaced the fixed match");
        int next=(sc.focusedRival()+1)%4;if(next==own)next=(next+1)%4;
        if(sc.rivalTabBounds(next)!=null){
            click(sc,sc.rivalTabBounds(next),InputConstants.MOUSE_BUTTON_LEFT);
            if(sc.focusedRival()!=next || !Arrays.equals(hand,sc.handCardBounds(0)))throw new AssertionError("Commander rival tab moved the hand");
        }
    }
    private static void checkHoverFaces(MultiplayerBoardScreen sc, int viewer) {
        var snapshot = sc.snapshot();
        var players = snapshot.getAsJsonArray("players");
        var hand = snapshot.has("hand") ? snapshot.getAsJsonArray("hand")
                : players.get(viewer).getAsJsonObject().getAsJsonArray("hand");
        int last = hand.size() - 1;
        expectHover(sc, sc.handCardBounds(last), name(hand.get(last).getAsJsonObject().get("id").getAsString()));
        for (int seat = 0; seat < players.size(); seat++) {
            if (sc.seatAreaBounds(seat) == null) continue;
            var player = players.get(seat).getAsJsonObject();
            var power = HeroPowers.of(CardClass.valueOf(player.get("cls").getAsString()));
            expectHover(sc, sc.powerBounds(seat), power.displayName().getString());
            if (player.has("tool"))
                expectHover(sc, sc.toolBounds(seat), name(player.getAsJsonObject("tool").get("id").getAsString()));
            if (sc.commandBounds(seat) != null)
                expectHover(sc, sc.commandBounds(seat), name(player.get("leader").getAsString()));
            // Full developer boards include both rows. Check one card from each.
            var seen = new HashSet<Boolean>();
            for (var raw : player.getAsJsonArray("board")) {
                var unit = raw.getAsJsonObject();
                if (seen.add(unit.get("block").getAsBoolean()))
                    expectHover(sc, sc.targetBounds(seat, unit.get("mid").getAsInt()), name(unit.get("id").getAsString()));
            }
        }
        countTitle(sc, "", 0, 0);
    }

    private static void checkLinkedHandHover(MultiplayerBoardScreen sc, int viewer) {
        var original = sc.snapshot();
        var linked = Cards.collectible().stream().filter(id -> !craftbound.client.card.CardInspectScreen.associations(id).isEmpty())
                .findFirst().orElseThrow();
        var other = Cards.collectible().stream().filter(id -> craftbound.client.card.CardInspectScreen.associations(id).isEmpty())
                .findFirst().orElseThrow();
        var sample = original.deepCopy();
        var hand = new com.google.gson.JsonArray();
        for (var id : List.of(other,linked,other)) {
            var card = new com.google.gson.JsonObject();
            card.addProperty("id",id.toString()); card.addProperty("ench",false);
            hand.add(card);
        }
        var seat = sample.getAsJsonArray("players").get(viewer).getAsJsonObject();
        if (sample.has("hand")) sample.add("hand",hand); else seat.add("hand",hand);
        seat.addProperty("handCount",hand.size());
        try {
            sc.update(sample);
            String parent = Cards.name(linked).getString(), neighbor = Cards.name(other).getString();
            int normalParent = countTitle(sc,parent,0,0);
            int normalNeighbor = countTitle(sc,neighbor,0,0);
            int[] src=sc.handCardBounds(1), dst=sc.handCardBounds(0);
            int sx=src[0]+src[2]/2, sy=src[1]+src[3]/2;
            int above=countTitle(sc,parent,sx,src[1]-8);
            if (countTitle(sc,parent,sx,sy)<=normalParent) throw new AssertionError("Missing linked hand preview");
            if (countTitle(sc,parent,sx,src[1]-8)!=above) throw new AssertionError("Enlarged source retained hover above the hand");
            countTitle(sc,parent,sx,sy);
            if (countTitle(sc,neighbor,dst[0]+dst[2]/2,dst[1]+dst[3]/2)<=normalNeighbor)
                throw new AssertionError("Linked preview intercepted the next hand slot");
        } finally {
            sc.update(original);
            countTitle(sc,"",0,0);
        }
    }

    private static String name(String id) {
        return Cards.name(Identifier.parse(id)).getString();
    }

    private static void expectHover(MultiplayerBoardScreen sc, int[] bounds, String title) {
        if (bounds == null) throw new AssertionError("Missing hover target for " + title);
        int base = countTitle(sc, title, 0, 0);
        int hovered = countTitle(sc, title, bounds[0] + bounds[2] / 2, bounds[1] + bounds[3] / 2);
        if (hovered <= base || sc.inspecting()) throw new AssertionError("No hover face for " + title);
    }

    private static int countTitle(MultiplayerBoardScreen sc, String title, int x, int y) {
        int[] count = {0};
        var capture = new GuiGraphicsExtractor(Minecraft.getInstance(), new GuiRenderState(), x, y) {
            @Override public void text(Font font, FormattedCharSequence text, int left, int top, int color, boolean shadow) {
                StringBuilder value = new StringBuilder();
                text.accept((i, style, codepoint) -> { value.appendCodePoint(codepoint); return true; });
                if (value.toString().equals(title)) count[0]++;
                super.text(font, text, left, top, color, shadow);
            }
        };
        sc.extractRenderState(capture, x, y, 0);
        return count[0];
    }

    private static boolean overlap(int[]a,int[]b){
        // Floor/ceil of separate transformed edges can share a one-pixel rounding seam.
        return a[0]+1<b[0]+b[2] && a[0]+a[2]-1>b[0] && a[1]+1<b[1]+b[3] && a[1]+a[3]-1>b[1];
    }
    private static void inside(int[]a,int[]v,String error){
        if(a==null||a[2]<=0||a[3]<=0||a[0]<v[0]-1||a[1]<v[1]-1||a[0]+a[2]>v[0]+v[2]+1||a[1]+a[3]>v[1]+v[3]+1)
            throw new AssertionError(error+" "+Arrays.toString(a)+" viewport "+Arrays.toString(v));
    }
    static void click(MultiplayerBoardScreen sc,int[]rect,int button){
        if(rect==null)throw new AssertionError("Missing board hit target");
        var e=new MouseButtonEvent(rect[0]+rect[2]/2.0,rect[1]+rect[3]/2.0,new MouseButtonInfo(button,0));
        if(!sc.mouseClicked(e,false))throw new AssertionError("Board target did not consume click");
        sc.mouseReleased(e);
    }
    private MultiplayerBoardScenario(){}
}
