package craftbound.game;

import com.google.gson.JsonObject;
import com.google.gson.JsonArray;
import craftbound.network.TrialsStatePayload;
import craftbound.card.Cards;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import com.mojang.blaze3d.platform.InputConstants;
import craftbound.client.card.CardInspectScreen;
import craftbound.client.match.TrialsScreen;
import java.util.List;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.network.chat.Component;

/** Interaction and layout checks for the vertical trail, using the real server journal. */
final class TrialsMapScenario {
    static void initial(ClientGameTestContext ctx, TestSingleplayerContext world) {
        JsonObject view = TrialsTableScenario.snapshot(world).view("");
        var all = view.getAsJsonArray("map");
        String boss = null;
        boolean corridor = false, junction = false, mystery = false;
        for (var raw : all) {
            var n = raw.getAsJsonObject();
            if (n.get("kind").getAsString().equals("BOSS")) boss = n.get("id").getAsString();
            int exits = n.getAsJsonArray("next").size();
            corridor |= exits == 1;
            junction |= exits > 1 && n.get("row").getAsInt() > 0;
            if (n.get("kind").getAsString().equals("EVENT")) {
                hiddenMystery(n);
                mystery = true;
            }
        }
        if (boss == null || !corridor || !junction || !mystery)
            throw new AssertionError("Fixture needs a final boss, forced stretches, junctions and mystery rooms");
        if (view.getAsJsonArray("choices").size() < 2)
            throw new AssertionError("Starting hub must offer a choice of entrances");

        ctx.runOnClient(mc -> {
            var sc = screen(mc.gui.screen());
            button(sc, "Deck (10)");
            button(sc, "Retire");
            button(sc, "Current");
            button(sc, "Fit");
            button(sc, "+");
            button(sc, "-");
            assertRetireSeparate(sc);
            if (has(sc, "Journal")) throw new AssertionError("The map should be the central hub");
            header(sc, view);
            assertMapLabelsFit(sc);
        });
        capture(ctx, "craftbound-trials-map-wide", 1280, 720);
        capture(ctx, "craftbound-trials-map-compact", 640, 480);

        // Check retirement remains discoverable without destroying this test's real run.
        int revision = view.get("revision").getAsInt();
        ctx.runOnClient(mc -> press(mc.gui.screen(), "Retire"));
        capture(ctx, "craftbound-trials-retire-compact", 640, 480);
        ctx.runOnClient(mc -> {
            var sc = mc.gui.screen();
            button(sc, "Retire run");
            press(sc, "Keep playing");
            button(sc, "Current");
        });
        if (TrialsTableScenario.snapshot(world).revision != revision)
            throw new AssertionError("Cancelling retirement changed the run");
        deck(ctx, 10, "craftbound-trials-deck");
        
        navigation(ctx, view);
        String finalBoss = boss;
        ctx.runOnClient(mc -> {
            var sc = screen(mc.gui.screen());
            press(sc, "Current");
            if (sc.mapNodeBounds(finalBoss) != null)
                throw new AssertionError("The boss should start above the initial visible map");
        });
        for (int attempt = 0; attempt < 100 && ctx.computeOnClient(mc -> screen(mc.gui.screen()).mapNodeBounds(finalBoss) == null); attempt++) {
            ctx.runOnClient(mc -> {
                var sc = screen(mc.gui.screen());
                var viewport = sc.mapViewport();
                if (!sc.mouseScrolled(viewport[0] + viewport[2] / 2.0, viewport[1] + viewport[3] / 2.0, 0, 1))
                    throw new AssertionError("The map ignored a vertical wheel input");
            });
            ctx.waitTicks(2);
        }
        ctx.runOnClient(mc -> {
            var sc = screen(mc.gui.screen());
            if (sc.mapNodeBounds(finalBoss) == null)
                throw new AssertionError("Scrolling up could not reach the boss");
            clickNode(sc, finalBoss);
            if (button(sc, "Travel").active)
                throw new AssertionError("Previewing a disconnected boss allowed travel");
        });
        capture(ctx, "craftbound-trials-map-boss-wide", 1280, 720);
        int[] normalWindow = resizeWindow(ctx, 640, 480);
        try {
            ctx.runOnClient(mc -> {
                var sc = screen(mc.gui.screen());
                sc.focusMapNode(finalBoss);
                clickNode(sc, finalBoss);
                press(sc, "Details");
                if (button(sc, "Travel").active)
                    throw new AssertionError("Compact boss details allowed disconnected travel");
            });
            capture(ctx, "craftbound-trials-boss-details-compact", 640, 480);
            ctx.runOnClient(mc -> press(mc.gui.screen(), "Back to map"));
        } finally {
            restoreWindow(ctx, normalWindow);
        }
        ctx.runOnClient(mc -> press(mc.gui.screen(), "Current"));
    }

    static void hiddenMystery(JsonObject n) {
        if (n.get("revealed").getAsBoolean()
                || !n.get("title").getAsString().equals("Unknown encounter")
                || !n.get("description").getAsString().equals("Enter this room to discover what awaits.")
                || n.has("event") || n.has("options") || n.has("menuItems"))
            throw new AssertionError("Unvisited mystery leaked its encounter or choices");
    }

    static void travel(ClientGameTestContext ctx, TestSingleplayerContext world, String nodeId) {
        var before = TrialsTableScenario.snapshot(world);
        var pending = before.act().byId.get(nodeId);
        var beforeView = before.view("");
        if (pending.kind() == TrialsAct.Kind.EVENT) {
            var node = beforeView.getAsJsonArray("map").asList().stream().map(v -> v.getAsJsonObject())
                    .filter(n -> n.get("id").getAsString().equals(nodeId)).findFirst().orElseThrow();
            hiddenMystery(node);
        }
        ctx.runOnClient(mc -> {
            var sc = screen(mc.gui.screen());
            sc.focusMapNode(nodeId);
            clickNode(sc, nodeId);
            press(sc, "Travel");
        });
        ctx.waitFor(mc -> mc.gui.screen() instanceof TrialsScreen sc && sc.travelling());
        // Accepted travel is not another click-to-confirm step; interaction is locked during the walk.
        ctx.runOnClient(mc -> {
            var sc = screen(mc.gui.screen());
            if (!sc.travelling()) throw new AssertionError("Travel never entered its walking animation");
            if (buttons(sc).stream().anyMatch(b -> b.active)) throw new AssertionError("Journey controls stayed active during the walk");
            var event = new MouseButtonEvent(sc.width / 2.0, sc.height / 2.0,
                    new MouseButtonInfo(InputConstants.MOUSE_BUTTON_LEFT, 0));
            sc.mouseClicked(event, false);
            sc.mouseReleased(event);
        });
        ctx.waitFor(mc -> mc.gui.screen() instanceof TrialsScreen sc && !sc.travelling());
        ctx.waitTicks(2);
        var after = TrialsTableScenario.snapshot(world);
        if (after.visited.size() != before.visited.size() + 1 || !after.visited.getLast().equals(nodeId))
            throw new AssertionError("A single walk did not commit exactly its selected room");
        if (after.phase == TrialsRun.Phase.MAP)
            throw new AssertionError("Walking did not open the destination's choices");
        if (pending.kind() == TrialsAct.Kind.EVENT) {
            var revealed = after.view("").getAsJsonArray("map").asList().stream().map(v -> v.getAsJsonObject())
                    .filter(n -> n.get("id").getAsString().equals(nodeId)).findFirst().orElseThrow();
            if (!revealed.get("revealed").getAsBoolean() || !revealed.get("title").getAsString().equals(after.room().title())
                    || after.choices.isEmpty())
                throw new AssertionError("Entering the mystery did not reveal its choices");
            capture(ctx, "craftbound-trials-mystery-revealed", 1280, 720);
        }
    }

    static void charms(ClientGameTestContext ctx, TestSingleplayerContext world) {
        var view = TrialsTableScenario.snapshot(world).view("");
        if (view.getAsJsonArray("charms").isEmpty()) throw new AssertionError("No charm was earned in the fixture");
        ctx.runOnClient(mc -> header(screen(mc.gui.screen()), view));
        capture(ctx, "craftbound-trials-charms-compact", 640, 480);
    }

    static void deck(ClientGameTestContext ctx, int cards, String name) {
        var hover = ctx.computeOnClient(mc -> {
            var b = button(mc.gui.screen(), "Deck (" + cards + ")");
            return new double[]{(b.getX() + b.getWidth() / 2.0) * mc.getWindow().getScreenWidth() / mc.getWindow().getGuiScaledWidth(),
                    (b.getY() + b.getHeight() / 2.0) * mc.getWindow().getScreenHeight() / mc.getWindow().getGuiScaledHeight()};
        });
        ctx.getInput().setCursorPos(hover[0], hover[1]);
        ctx.waitTicks(8);
        ctx.runOnClient(mc -> mc.gui.toastManager().clear());
        ctx.takeScreenshot(name + "-hover");
        ctx.runOnClient(mc -> {
            var sc = mc.gui.screen();
            press(sc, "Deck (" + cards + ")");
            assertDeckStacks(screen(sc), cards);
            var trials=screen(sc);
            if(trials.deckCurve().size()!=cards||trials.deckCurve().changed())
                throw new AssertionError("Browsing the deck must show current copy counts without a preview change");
            if(has(sc,"Prev")||has(sc,"Next"))throw new AssertionError("The deck must scroll instead of paging");
            var copy = buttons(sc).stream().filter(b -> b.getMessage().getString().startsWith("#")).findFirst().orElseThrow();
            click(sc, copy, InputConstants.MOUSE_BUTTON_LEFT);
            if (!(mc.gui.screen() instanceof CardInspectScreen))
                throw new AssertionError("A deck copy did not open inspection");
            mc.gui.screen().onClose();
            if (mc.gui.screen() != sc || buttons(sc).stream().noneMatch(b -> b.getMessage().getString().startsWith("#")))
                throw new AssertionError("Inspection did not return to the deck list");
        });
        capture(ctx, name + "-list-wide", 1280, 720);
        capture(ctx, name + "-list-compact", 640, 480);
        ctx.runOnClient(mc -> press(mc.gui.screen(), "Back to Trials"));
    }

    /** Layout-only network view: no purchases, cards or bonuses are written to the real run. */
    static void presentationFixture(ClientGameTestContext ctx, TestSingleplayerContext world) {
        JsonObject original = TrialsTableScenario.snapshot(world).view("");
        BlockPos table = ctx.computeOnClient(mc -> (BlockPos)field(screen(mc.gui.screen()), "table"));
        JsonObject fixture = original.deepCopy();
        fixture.addProperty("runId", original.get("runId").getAsString() + "-layout-qa");
        fixture.addProperty("phase", "MAP");
        fixture.addProperty("afterBattle", false);
        var available = Cards.collectible().stream().filter(id -> TrialTraining.TEMPER.supports(id))
                .sorted(java.util.Comparator.comparingInt(id -> Cards.get(id).cost())).toList();
        if (available.isEmpty()) throw new AssertionError("No permanent card is available for the upgraded layout fixture");
        Identifier trained = available.getFirst();
        JsonArray deck = new JsonArray(), training = new JsonArray();
        for (int i = 0; i < 3; i++) {deck.add(trained.toString());training.add(i == 2 ? "TEMPER" : "NONE");}
        for (var id : available.stream().filter(id -> !id.equals(trained)).limit(12).toList()) {
            deck.add(id.toString());training.add("NONE");
        }
        fixture.add("deck", deck); fixture.add("training", training);
        JsonArray boons = new JsonArray();
        for (int i = 0; i < 24; i++) {
            JsonObject boon = new JsonObject();
            boon.addProperty("id", "qa_acquisition_" + i);
            boon.addProperty("title", List.of("Golden Heart", "Field Supplies", "Expedition Spark").get(i % 3));
            boon.addProperty("description", List.of("+4 maximum Health.", "+1 opening card.", "+1 starting XP.").get(i % 3)
                    + " Lasts for this expedition. Acquisition " + (i + 1) + ".");
            boon.addProperty("iconItem", List.of("minecraft:golden_apple", "minecraft:book", "minecraft:torch").get(i % 3));
            boons.add(boon);
        }
        fixture.add("acquiredBoons", boons);
        try {
            ctx.runOnClient(mc -> TrialsScreen.receive(mc, new TrialsStatePayload(table, fixture.toString())));
            int[] size = resizeWindow(ctx, 640, 480);
            try {
                ctx.runOnClient(mc -> header(screen(mc.gui.screen()), fixture));
                capture(ctx, "craftbound-trials-acquired-boons-compact", 640, 480);
            } finally {restoreWindow(ctx, size);}
            deck(ctx, deck.size(), "craftbound-trials-upgraded-stacks");
            ctx.runOnClient(mc -> {
                press(mc.gui.screen(), "Deck (" + deck.size() + ")");
                assertScrollingDeck(screen(mc.gui.screen()));
            });
            capture(ctx,"craftbound-trials-deck-scrolled-wide",1280,720);
            ctx.runOnClient(mc -> scrollDeck(screen(mc.gui.screen()),1000));
            for (int page = 0; page < deck.size() && ctx.computeOnClient(mc -> buttons(mc.gui.screen()).stream()
                    .noneMatch(b -> b.getMessage().getString().startsWith("#3 "))); page++) {
                ctx.runOnClient(mc -> scrollDeck(screen(mc.gui.screen()), -1));
                ctx.waitTicks(2);
            }
            ctx.runOnClient(mc -> {
                var sc = screen(mc.gui.screen());
                var stacks = (List<?>)field(sc, "deckStacks");
                var same = stacks.stream().filter(st -> field(field(st, "card"), "id").equals(trained)).toList();
                if (same.size() != 2 || same.stream().noneMatch(st -> ((List<?>)field(st, "slots")).size() == 2)
                        || same.stream().noneMatch(st -> field(field(st, "card"), "training") == TrialTraining.TEMPER))
                    throw new AssertionError("Normal duplicates and the trained copy must be separate visual stacks");
                var upgraded = buttons(sc).stream().filter(b -> b.getMessage().getString().startsWith("#3 ")).findFirst().orElseThrow();
                click(sc, upgraded, InputConstants.MOUSE_BUTTON_LEFT);
                if (!(mc.gui.screen() instanceof CardInspectScreen) || field(mc.gui.screen(), "training") != TrialTraining.TEMPER)
                    throw new AssertionError("Inspection discarded the chosen copy's upgrade");
            });
            capture(ctx, "craftbound-trials-upgraded-inspect", 1280, 720);
            ctx.runOnClient(mc -> mc.gui.screen().onClose());
        } finally {
            ctx.runOnClient(mc -> TrialsScreen.receive(mc, new TrialsStatePayload(table, original.toString())));
        }
        if (!TrialsTableScenario.snapshot(world).view("").equals(original))
            throw new AssertionError("Read-only layout inspection changed the saved run");
    }

    static void capture(ClientGameTestContext ctx, String name, int width, int height) {
        int[] previous = resizeWindow(ctx, width, height);
        try {
            ctx.runOnClient(mc -> {
                mc.gui.toastManager().clear();
                assertControlsFit(mc.gui.screen());
            });
            ctx.getInput().setCursorPos(0, 0);
            ctx.waitTicks(2);
            // The render target AND window are this size, so GUI scissors use the same bounds.
            ctx.takeScreenshot(name);
        } finally {
            restoreWindow(ctx, previous);
        }
    }

    private static int[] resizeWindow(ClientGameTestContext ctx, int width, int height) {
        int[] previous = ctx.computeOnClient(mc -> new int[]{
                mc.getWindow().getScreenWidth(), mc.getWindow().getScreenHeight(), mc.options.guiScale().get()});
        ctx.runOnClient(mc -> mc.options.guiScale().set(2));
        ctx.getInput().resizeWindow(width, height);
        // Also apply the requested GUI scale if resizeWindow was a no-op at the same resolution.
        ctx.runOnClient(mc -> mc.resizeGui());
        ctx.waitTicks(2);
        ctx.runOnClient(mc -> {
            var window = mc.getWindow();
            var sc = mc.gui.screen();
            if (window.getScreenWidth() != width || window.getScreenHeight() != height
                    || window.getGuiScaledWidth() != width / 2 || window.getGuiScaledHeight() != height / 2
                    || sc.width != width / 2 || sc.height != height / 2)
                throw new AssertionError("The test window, GUI and screenshot dimensions disagree");
        });
        return previous;
    }

    private static void restoreWindow(ClientGameTestContext ctx, int[] previous) {
        ctx.runOnClient(mc -> mc.options.guiScale().set(previous[2]));
        ctx.getInput().resizeWindow(previous[0], previous[1]);
        ctx.runOnClient(mc -> mc.resizeGui());
        ctx.waitTicks(2);
    }

    private static void header(TrialsScreen sc, JsonObject view) {
        var expected = new java.util.ArrayList<JsonObject>();
        if (view.has("acquiredBoons")) for (var raw : view.getAsJsonArray("acquiredBoons")) expected.add(raw.getAsJsonObject());
        for (var raw : view.getAsJsonArray("charms")) {
            String id = raw.getAsString();
            expected.add(view.getAsJsonArray("charmDefinitions").asList().stream().map(v -> v.getAsJsonObject())
                    .filter(c -> c.get("id").getAsString().equals(id)).findFirst().orElseThrow());
        }
        if (expected.isEmpty()) {
            if (!((List<?>)field(sc, "headerIcons")).isEmpty()) throw new AssertionError("An unearned generic boon icon appeared");
            return;
        }
        var texts = new java.util.HashSet<String>();
        int startOffset = (int)field(sc, "headerOffset");
        for (int step = 0; step < expected.size() + 1; step++)
            sc.mouseScrolled(20, (int)field(sc, "y") + 54, 0, 1);
        int seen = 0;
        for (int page = 0; page <= expected.size(); page++) {
            var icons = (List<?>)field(sc, "headerIcons");
            if (expected.isEmpty() && !icons.isEmpty()) throw new AssertionError("An unearned generic boon icon appeared");
            if (!expected.isEmpty() && icons.isEmpty()) throw new AssertionError("Earned boon/charm icons are absent");
            for (int i = 0; i < icons.size(); i++) {
                var icon = icons.get(i);
                int x = (int)field(icon, "x"), y = (int)field(icon, "y");
                if (x < 0 || y < 0 || x + 18 > sc.width || y + 18 > sc.height)
                    throw new AssertionError("A boon/charm header icon is outside the screen");
                if (i > 0 && x < (int)field(icons.get(i - 1), "x") + 18)
                    throw new AssertionError("Header icons overlap");
                for (var line : (List<?>)field(icon, "text")) texts.add(((Component)line).getString());
            }
            seen = Math.max(seen, (int)field(sc, "headerOffset") + icons.size());
            int before = (int)field(sc, "headerOffset");
            sc.mouseScrolled(20, (int)field(sc, "y") + 54, 0, -1);
            if ((int)field(sc, "headerOffset") == before) break;
        }
        if (seen != expected.size()) throw new AssertionError("The header cannot reach every acquired boon/charm");
        for (var item : expected)
            if (!texts.contains(item.get("title").getAsString()) || !texts.contains(item.get("description").getAsString()))
                throw new AssertionError("An earned boon/charm has no readable tooltip");
        while ((int)field(sc, "headerOffset") > startOffset)
            sc.mouseScrolled(20, (int)field(sc, "y") + 54, 0, 1);
    }

    private static void assertRetireSeparate(TrialsScreen sc) {
        var retire = button(sc, "Retire");
        if (retire.getY() < sc.height / 2) throw new AssertionError("Retire belongs away from header navigation");
        for (var b : buttons(sc)) if (b != retire && b.getY() == retire.getY()
                && Math.min(Math.abs(retire.getX() - b.getRight()), Math.abs(b.getX() - retire.getRight())) < 8)
            throw new AssertionError("Retire needs a clear gap from routine actions");
    }

    private static void assertDeckStacks(TrialsScreen sc, int expectedCount) {
        var stacks = (List<?>)field(sc, "deckStacks");
        var entries = (List<?>)field(sc, "entries");
        var keys = new java.util.HashSet<Object>();
        var seen = new java.util.HashSet<Integer>();
        for (var stack : stacks) {
            Object card = field(stack, "card");
            if (!keys.add(card)) throw new AssertionError("Identical copies were not grouped");
            var slots = (List<?>)field(stack, "slots");
            if (slots.isEmpty()) throw new AssertionError("An empty stack was shown");
            for (var raw : slots) {
                int slot = (int)raw;
                if (!seen.add(slot)) throw new AssertionError("One copy appeared in two deck stacks");
                var copy = entries.stream().filter(e -> (int)field(e, "slot") == slot).findFirst().orElseThrow();
                if (!field(copy, "id").equals(field(card, "id")) || field(copy, "training") != field(card, "training"))
                    throw new AssertionError("An upgraded copy was merged with an ordinary card");
            }
        }
        if (seen.size() != expectedCount) throw new AssertionError("Grouped deck counts lost a copy");
        for (var tile : buttons(sc).stream().filter(b -> b.getMessage().getString().startsWith("#")).toList()) {
            var stack = field(tile, "stack");
            int count = ((List<?>)field(stack, "slots")).size();
            if (!tile.getMessage().getString().contains("\u00d7" + count)) throw new AssertionError("Stack count absent from card tile");
        }
        var xPositions=buttons(sc).stream().filter(b->b.getMessage().getString().startsWith("#"))
                .map(Button::getX).distinct().count();
        if(stacks.size()>=5&&xPositions!=5)throw new AssertionError("The deck must use five columns");
    }

    static void scrollDeck(TrialsScreen sc,double wheel) {
        int[] viewport=sc.deckViewport();
        if(!sc.mouseScrolled(viewport[0]+viewport[2]/2.0,viewport[1]+viewport[3]/2.0,0,wheel))
            throw new AssertionError("Deck ignored scrolling");
    }
    private static void assertScrollingDeck(TrialsScreen sc) {
        int maximum=(int)field(sc,"deckScrollMax");
        if(maximum<=0)throw new AssertionError("Scrolling fixture needs more than one deck row");
        int count=sc.deckCurve().size();
        var seen=new java.util.HashSet<Integer>();
        for(int step=0;step<100;step++) {
            for(var tile:buttons(sc))if(tile.getMessage().getString().startsWith("#"))
                for(var slot:(List<?>)field(field(tile,"stack"),"slots"))seen.add((Integer)slot);
            int before=(int)field(sc,"deckScroll");
            scrollDeck(sc,-1);
            if((int)field(sc,"deckScroll")==before)break;
        }
        if((int)field(sc,"deckScroll")!=maximum||seen.size()!=count||sc.deckCurve().size()!=count)
            throw new AssertionError("Scrolling cannot reach every unchanged deck copy");
        var viewport=sc.deckViewport();
        for(var tile:buttons(sc))if(tile.getMessage().getString().startsWith("#")) {
            var outside=new MouseButtonEvent(tile.getX()+tile.getWidth()/2.0,viewport[1]-1,
                    new MouseButtonInfo(InputConstants.MOUSE_BUTTON_LEFT,0));
            if(tile.mouseClicked(outside,false))throw new AssertionError("A clipped deck tile accepted a header click");
        }
    }
    static void curveMath() {
        var costs=List.of(0,2,2,7,10,12);
        var unchanged=craftbound.client.match.TrialsDeckCurve.of(costs,null,null);
        if(unchanged.size()!=6||unchanged.current()[2]!=2||unchanged.current()[10]!=2||unchanged.changed())
            throw new AssertionError("XP curve must include every copy and group 10+");
        var added=craftbound.client.match.TrialsDeckCurve.of(costs,2,null);
        if(added.projectedSize()!=7||added.projected()[2]!=3||added.current()[2]!=2)
            throw new AssertionError("Addition preview altered the current curve");
        var removed=craftbound.client.match.TrialsDeckCurve.of(costs,null,2);
        if(removed.projectedSize()!=5||removed.projected()[2]!=1)
            throw new AssertionError("Removal must preview one copy");
        var swapped=craftbound.client.match.TrialsDeckCurve.of(costs,5,2);
        if(swapped.projectedSize()!=6||swapped.projected()[5]!=1||swapped.projected()[2]!=1)
            throw new AssertionError("Trade must preview replacement, not an extra card");
        var high=craftbound.client.match.TrialsDeckCurve.of(List.of(10),12,10);
        if(!high.changed()||high.projectedAverage()!=12)throw new AssertionError("10+ trades must update average");
        var empty=craftbound.client.match.TrialsDeckCurve.of(List.of(),null,null);
        if(empty.average()!=0||empty.projectedAverage()!=0)throw new AssertionError("Empty curve average must be zero");
    }

    private static void navigation(ClientGameTestContext ctx, JsonObject view) {
        int[] previous = resizeWindow(ctx, 1280, 720);
        try {
            ctx.runOnClient(mc -> {
                var sc = screen(mc.gui.screen());
                press(sc, "Current");
                String root = view.getAsJsonArray("choices").get(0).getAsString();
                sc.focusMapNode(root);
                clickNode(sc, root);
                if (!button(sc, "Travel").active) throw new AssertionError("A root room could not be chosen");
                int beforeX = (int)field(sc, "mapScrollX"), beforeY = (int)field(sc, "mapScroll");
                var r = sc.mapNodeBounds(root);
                var nodeDrag = new MouseButtonEvent(r[0] + r[2] / 2.0, r[1] + r[3] / 2.0,
                        new MouseButtonInfo(InputConstants.MOUSE_BUTTON_LEFT, 0));
                sc.mouseClicked(nodeDrag, false);
                sc.mouseDragged(nodeDrag, 15, 15);
                sc.mouseReleased(nodeDrag);
                if ((int)field(sc, "mapScrollX") != beforeX || (int)field(sc, "mapScroll") != beforeY)
                    throw new AssertionError("Dragging a room should not pan the map");

                double[] empty = emptyMapPoint(sc);
                var down = new MouseButtonEvent(empty[0], empty[1], new MouseButtonInfo(InputConstants.MOUSE_BUTTON_LEFT, 0));
                if (!sc.mouseClicked(down, false) || !(boolean)field(sc, "draggingMap"))
                    throw new AssertionError("Dragging empty parchment did not begin a pan");
                int dx = beforeX > 0 ? 20 : -20;
                var move = new MouseButtonEvent(empty[0] + dx, empty[1] + 35, new MouseButtonInfo(InputConstants.MOUSE_BUTTON_LEFT, 0));
                sc.mouseDragged(move, dx, 35);
                sc.mouseReleased(move);
                if ((int)field(sc, "mapScrollX") == beforeX || (int)field(sc, "mapScroll") == beforeY)
                    throw new AssertionError("Panning must move the route in both axes");
                double beforeWheelZoom = sc.mapZoom();
                int wheelBefore = (int)field(sc, "mapScroll");
                int[] viewport = sc.mapViewport();
                sc.mouseScrolled(viewport[0] + viewport[2] / 2.0, viewport[1] + viewport[3] / 2.0, 0, 1);
                if ((int)field(sc, "mapScroll") >= wheelBefore || sc.mapZoom() != beforeWheelZoom)
                    throw new AssertionError("The wheel must pan the route without changing zoom");
                int after = (int)field(sc, "mapScroll");
                sc.mouseDragged(move, 0, 10);
                if ((int)field(sc, "mapScroll") != after || (boolean)field(sc, "draggingMap"))
                    throw new AssertionError("The map kept dragging after release");
                for (var raw : view.getAsJsonArray("map")) {
                    sc.focusMapNode(raw.getAsJsonObject().get("id").getAsString());
                    assertMapLabelsFit(sc);
                    assertControlsFit(sc);
                }
                // Repeated zoom-out must end at the same complete overview as Fit,
                // including the horizontal branches, not only the vertical span.
                for (int n = 0; n < 40 && button(sc, "-").active; n++) press(sc, "-");
                if (button(sc, "-").active) throw new AssertionError("Zoom out never reached a full overview");
                double minimum = sc.mapZoom();
                for (var raw : view.getAsJsonArray("map"))
                    if (sc.mapNodeBounds(raw.getAsJsonObject().get("id").getAsString()) == null)
                        throw new AssertionError("Zoom out left a branch outside the map");
                if ((int)field(sc, "mapScrollX") != 0 || (int)field(sc, "mapScroll") != 0)
                    throw new AssertionError("The overview should not need additional panning");
                press(sc, "Fit");
                double fitted = sc.mapZoom();
                if (minimum != fitted) throw new AssertionError("Minus and Fit disagree about the full-route scale");
                for (var raw : view.getAsJsonArray("map"))
                    if (sc.mapNodeBounds(raw.getAsJsonObject().get("id").getAsString()) == null)
                        throw new AssertionError("Fit left a room outside the map");
                press(sc, "+");
                if (sc.mapZoom() <= fitted) throw new AssertionError("Zoom in did not increase the scale");
                double enlarged = sc.mapZoom();
                press(sc, "-");
                if (sc.mapZoom() >= enlarged) throw new AssertionError("Zoom out did not decrease the scale");
            });
            capture(ctx, "craftbound-trials-map-fit-wide", 1280, 720);
            ctx.runOnClient(mc -> {
                var sc = screen(mc.gui.screen());
                while (sc.mapZoom() < 0.99 && button(sc, "+").active) press(sc, "+");
                press(sc, "Current");
                assertMapLabelsFit(sc);
            });
        } finally { restoreWindow(ctx, previous); }
    }

    private static double[] emptyMapPoint(TrialsScreen sc) {
        int[] area = sc.mapViewport();
        for (int y = area[1] + 20; y < area[1] + area[3] - 40; y += 8)
            for (int x = area[0] + 25; x < area[0] + area[2] - 25; x += 8) {
                int xx = x, yy = y;
                if (buttons(sc).stream().noneMatch(b -> xx >= b.getX() - 5 && xx <= b.getRight() + 5
                        && yy >= b.getY() - 5 && yy <= b.getBottom() + 18)) return new double[]{x, y};
            }
        throw new AssertionError("Map has no empty space to start dragging");
    }

    private static void assertMapLabelsFit(TrialsScreen sc) {
        var keys = ((java.util.Map<?,?>)field(sc, "nodes")).keySet().stream().map(Object::toString).toList();
        var viewport = sc.mapViewport();
        for (String id : keys) {
            int[] label = sc.mapNodeLabelBounds(id);
            if (label == null) continue;
            if (label[0] < viewport[0] || label[1] < viewport[1]
                    || label[0] + label[2] > viewport[0] + viewport[2]
                    || label[1] + label[3] > viewport[1] + viewport[3])
                throw new AssertionError("A map label is clipped outside the map");
            for (String other : keys) if (!id.equals(other)) {
                if (overlap(label, sc.mapNodeBounds(other)) || overlap(label, sc.mapNodeLabelBounds(other)))
                    throw new AssertionError("Map room labels overlap: " + id + " / " + other);
            }
        }
    }

    private static boolean overlap(int[] a, int[] b) {
        return a != null && b != null && a[0] < b[0] + b[2] && a[0] + a[2] > b[0]
                && a[1] < b[1] + b[3] && a[1] + a[3] > b[1];
    }

    private static void assertControlsFit(Screen sc) {
        var buttons = buttons(sc);
        for (var b : buttons) {
            if (b.getX() < 0 || b.getY() < 0 || b.getRight() > sc.width || b.getBottom() > sc.height)
                throw new AssertionError("Off-screen control: " + b.getMessage().getString());
            for (var other : buttons) if (b != other && b.getX() < other.getRight() && b.getRight() > other.getX()
                    && b.getY() < other.getBottom() && b.getBottom() > other.getY())
                throw new AssertionError("Overlapping controls: " + b.getMessage().getString() + " / " + other.getMessage().getString());
        }
    }
    static Object field(Object object, String name) {
        try { var f = object.getClass().getDeclaredField(name); f.setAccessible(true); return f.get(object); }
        catch (ReflectiveOperationException e) { throw new AssertionError(e); }
    }
    static TrialsScreen screen(Screen sc) {
        if (!(sc instanceof TrialsScreen trials)) throw new AssertionError("Missing Trials screen: " + sc);
        return trials;
    }
    static List<Button> buttons(Screen sc) {
        return sc.children().stream().filter(Button.class::isInstance).map(Button.class::cast).filter(b -> b.visible).toList();
    }
    static List<Button> choices(Screen sc) {
        return buttons(sc).stream().filter(b -> b.getHeight() > 25).toList();
    }
    static boolean has(Screen sc, String name) {
        return buttons(sc).stream().anyMatch(b -> b.getMessage().getString().equals(name));
    }
    static Button button(Screen sc, String name) {
        return buttons(sc).stream().filter(b -> b.getMessage().getString().equals(name)).findFirst()
                .orElseThrow(() -> new AssertionError("Missing " + name + " on " + sc.getTitle().getString()));
    }
    static void press(Screen sc, String name) { click(sc, button(sc, name), InputConstants.MOUSE_BUTTON_LEFT); }
    static void clickNode(TrialsScreen sc, String nodeId) {
        var rect = sc.mapNodeBounds(nodeId);
        if (rect == null) throw new AssertionError("Room is not visible: " + nodeId);
        var event = new MouseButtonEvent(rect[0] + rect[2] / 2.0, rect[1] + rect[3] / 2.0,
                new MouseButtonInfo(InputConstants.MOUSE_BUTTON_LEFT, 0));
        if (!sc.mouseClicked(event, false)) throw new AssertionError("Could not select room " + nodeId);
        sc.mouseReleased(event);
    }
    static void click(Screen sc, Button button, int mouse) {
        if (!button.active) throw new AssertionError("Inactive: " + button.getMessage().getString());
        double mouseY=button.getY()+button.getHeight()/2.0;
        if(sc instanceof TrialsScreen trials&&button.getMessage().getString().startsWith("#")) {
            int[] viewport=trials.deckViewport();
            mouseY=(Math.max(button.getY(),viewport[1])+Math.min(button.getBottom(),viewport[1]+viewport[3]))/2.0;
        }
        var event = new MouseButtonEvent(button.getX() + button.getWidth() / 2.0, mouseY,
                new MouseButtonInfo(mouse, 0));
        if (!sc.mouseClicked(event, false)) throw new AssertionError("Missed " + button.getMessage().getString());
        sc.mouseReleased(event);
    }
    private TrialsMapScenario() {}
}

