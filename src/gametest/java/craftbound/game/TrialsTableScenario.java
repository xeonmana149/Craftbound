package craftbound.game;

import com.mojang.blaze3d.platform.InputConstants;
import craftbound.block.ModBlocks;
import craftbound.client.card.CardInspectScreen;
import craftbound.client.match.MatchScreen;
import craftbound.client.match.TrialsScreen;
import craftbound.network.MatchActionPayload;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import static craftbound.game.TrialsMapScenario.*;

/** Drive the actual menu, draft, seeded route, shop and PvE settlement of a fresh test-world run. */
public final class TrialsTableScenario {
    public static void check(ClientGameTestContext ctx, TestSingleplayerContext world) {
        TrialsMapScenario.curveMath();
        var location = new AtomicReference<BlockPos>();
        var trail = new AtomicReference<List<String>>();
        var rewardProbe = new TrialsRewardScenario();
        world.getServer().runOnServer(server -> {
            var player = server.getPlayerList().getPlayers().getFirst();
            var level = server.overworld();
            var table = player.blockPosition().offset(-3, 0, 0);
            location.set(table);
            for (var floor : BlockPos.betweenClosed(table.offset(-1,-1,-1), table.offset(1,-1,1)))
                level.setBlockAndUpdate(floor, Blocks.OAK_PLANKS.defaultBlockState());
            level.setBlockAndUpdate(table, ModBlocks.CARD_TABLE.defaultBlockState());
            // Seed only this fresh test journal. Choose a genuine generated route covering all services under test.
            for (long seed = 0; seed < 2048; seed++) {
                var fixture = TrialsRun.create(seed);
                var path = route(fixture.act());
                if (path != null) {
                    saveFixture(player, fixture);
                    trail.set(path);
                    break;
                }
            }
            if (trail.get() == null) throw new AssertionError("No seeded route covers early shop, mystery and elite");
            TableLobby.open(player, table);
        });
        ctx.waitTicks(6);
        ctx.runOnClient(mc -> {
            press(mc.gui.screen(), "The Trials");
        });
        ctx.waitTicks(6);
        capture(ctx, "craftbound-trials-classes", 1280, 720);
        ctx.runOnClient(mc -> {
            var sc = screen(mc.gui.screen());
            var offered = choices(sc);
            if (offered.size() != 3) throw new AssertionError("Expected three server-offered classes");
            click(sc, offered.getFirst(), InputConstants.MOUSE_BUTTON_LEFT);
            press(sc, "Choose class");
        });
        ctx.waitTicks(6);
        capture(ctx, "craftbound-trials-draft", 1280, 720);
        ctx.runOnClient(mc -> {
            var sc = screen(mc.gui.screen());
            click(sc, choices(sc).getFirst(), InputConstants.MOUSE_BUTTON_RIGHT);
            if (!(mc.gui.screen() instanceof CardInspectScreen)) throw new AssertionError("Offered card could not be inspected");
            mc.gui.screen().onClose();
            if (mc.gui.screen() != sc) throw new AssertionError("Inspect lost the draft");
        });
        for (int pick = 0; pick < 10; pick++) {
            int previous = pick;
            ctx.runOnClient(mc -> {
                var sc = screen(mc.gui.screen());
                button(sc, "Deck (" + previous + ")");
                var offered = choices(sc);
                if (offered.size() != 3) throw new AssertionError("Expected three actual draft cards");
                var choice = offered.stream().filter(b -> craftbound.card.Cards.collectible().stream().anyMatch(id ->
                        craftbound.card.Cards.name(id).getString().equals(b.getMessage().getString()) && TrialTraining.TEMPER.supports(id)))
                        .findFirst().orElse(offered.get(previous % 3));
                click(sc, choice, InputConstants.MOUSE_BUTTON_LEFT);
                var curve=sc.deckCurve();
                if(curve.size()!=previous||curve.projectedSize()!=previous+1||!curve.changed())
                    throw new AssertionError("Draft choice must preview one added copy without changing the deck");
                press(sc, "Take card");
            });
            ctx.waitTicks(4);
            if (pick == 3) {
                ctx.runOnClient(mc -> {
                    var sc = screen(mc.gui.screen());
                    press(sc, "Deck (4)");
                    press(sc, "Back to Trials");
                    press(sc, "Close");
                    press(mc.gui.screen(), "The Trials");
                });
                ctx.waitTicks(6);
            }
        }
        TrialsMapScenario.initial(ctx, world);
        boolean trained = false, sawMystery = false, sawCharm = false;
        for (int step = 0; step < trail.get().size(); step++) {
            String id = trail.get().get(step);
            TrialsMapScenario.travel(ctx, world, id);
            var run = snapshot(world);
            var kind = run.room().kind();
            if (run.room().combat()) {
                if (step == 0) capture(ctx, "craftbound-trials-rival", 1280, 720);
                fight(ctx, world, location.get(), trained, rewardProbe);
                run = snapshot(world);
                if (run.phase == TrialsRun.Phase.VICTORY) break;
                if (run.phase == TrialsRun.Phase.ELITE_PRIZE) {
                    if (sawCharm) ctx.runOnClient(mc -> press(mc.gui.screen(), "Skip"));
                    else {
                        String charmTitle = run.view("").getAsJsonArray("menuItems").get(0).getAsJsonObject().get("title").getAsString();
                        ctx.runOnClient(mc -> {
                            var sc = mc.gui.screen();
                            press(sc, charmTitle);
                            press(sc, "Take charm");
                        });
                    }
                    ctx.waitTicks(5);
                    TrialsMapScenario.charms(ctx, world);
                    sawCharm = true;
                }
                if (step == 0) capture(ctx, "craftbound-trials-reward", 1280, 720);
                ctx.runOnClient(mc -> {
                    var sc = mc.gui.screen();
                    button(sc, "Take card");
                    if (stepIsFirstReward(sc)) press(sc, "Skip");
                    else {click(sc, choices(sc).getFirst(), InputConstants.MOUSE_BUTTON_LEFT); press(sc, "Take card");}
                });
                ctx.waitTicks(5);
            } else if (kind == TrialsAct.Kind.SHOP && !trained) {
                shop(ctx, world);
                trained = true;
            } else if (kind == TrialsAct.Kind.EVENT) {
                sawMystery = true;
                ctx.runOnClient(mc -> {
                    var sc = mc.gui.screen();
                    if (has(sc, "Pass by")) {press(sc, "Pass by");press(sc, "Choose");}
                    else press(sc, "Leave");
                });
                ctx.waitTicks(5);
            } else {
                ctx.runOnClient(mc -> press(mc.gui.screen(), "Leave"));
                ctx.waitTicks(5);
            }
            if (snapshot(world).phase != TrialsRun.Phase.MAP)
                throw new AssertionError("Room completion did not return to the central map");
        }
        if (!trained || !sawMystery || !sawCharm) throw new AssertionError("Fixture missed training, mystery or charm coverage");
        ctx.runOnClient(mc -> {
            var sc = mc.gui.screen();
            button(sc, "New run");
            if (has(sc, "Journal")) throw new AssertionError("Obsolete journal button returned");
        });
        capture(ctx, "craftbound-trials-act-clear", 1280, 720);
        rewardProbe.after(ctx, world);
        ctx.runOnClient(mc -> mc.gui.setScreen(null));
        world.getServer().runOnServer(server -> server.overworld().removeBlock(location.get(), false));
    }

    // The opening reward leaves the original ten copies, so the first shop purchase is easy to audit.
    private static boolean stepIsFirstReward(net.minecraft.client.gui.screens.Screen sc) {
        return has(sc, "Deck (10)");
    }

    private static void shop(ClientGameTestContext ctx, TestSingleplayerContext world) {
        capture(ctx, "craftbound-trials-villager", 1280, 720);
        selectMenu(ctx, "Card shelf");
        ctx.runOnClient(mc -> {
            var sc = mc.gui.screen();
            click(sc, choices(sc).getFirst(), InputConstants.MOUSE_BUTTON_LEFT);
            press(sc, "Buy");
        });
        ctx.waitTicks(5);
        var current = snapshot(world);
        if (current.deck.size() != 11) throw new AssertionError("Shop purchase failed");
        selectMenu(ctx, "Train a card");
        int slot = -1;
        for (int i = 0; i < current.deck.size(); i++)
            if (TrialTraining.TEMPER.supports(net.minecraft.resources.Identifier.parse(current.deck.get(i)))) {slot = i;break;}
        if (slot < 0) throw new AssertionError("Draft has no trainable permanent");
        String prefix = "#" + (slot + 1) + " ";
        // Copies sort by XP and name; scroll down to the selected original copy.
        for (int page = 0; page < current.deck.size() && ctx.computeOnClient(mc -> buttons(mc.gui.screen()).stream()
                .noneMatch(b -> b.getMessage().getString().startsWith(prefix))); page++) {
            ctx.runOnClient(mc -> {
                var sc = mc.gui.screen();
                scrollDeck(screen(sc), -1);
            });
            ctx.waitTicks(2);
        }
        ctx.runOnClient(mc -> {
            var sc = mc.gui.screen();
            var copy = buttons(sc).stream().filter(b -> b.getMessage().getString().startsWith(prefix)).findFirst().orElseThrow();
            click(sc, copy, InputConstants.MOUSE_BUTTON_LEFT);
            press(sc, "Choose copy");
        });
        ctx.waitTicks(5);
        ctx.runOnClient(mc -> {var sc = mc.gui.screen();press(sc, "Temper");press(sc, "Train card");});
        ctx.waitTicks(5);
        if (snapshot(world).training.stream().noneMatch(t -> t == TrialTraining.TEMPER))
            throw new AssertionError("Training was not saved");
        TrialsMapScenario.deck(ctx, 11, "craftbound-trials-trained-deck");
        TrialsMapScenario.presentationFixture(ctx, world);
        ctx.runOnClient(mc -> press(mc.gui.screen(), "Leave"));
        ctx.waitTicks(5);
    }

    private static void fight(ClientGameTestContext ctx, TestSingleplayerContext world, BlockPos pos,
                              boolean expectTraining, TrialsRewardScenario rewardProbe) {
        ctx.runOnClient(mc -> press(mc.gui.screen(), "Enter trial"));
        ctx.waitTicks(10);
        world.getServer().runOnServer(server -> {
            var player = server.getPlayerList().getPlayers().getFirst();
            var match = MatchManager.matchAt(server.overworld(), pos);
            if (match == null) throw new AssertionError("Failed to enter live Trials combat");
            int us = match.sideOf(player.getUUID());
            rewardProbe.before(player, match);
            if (expectTraining) {
                var pile = new ArrayList<>(match.player(us).hand);
                pile.addAll(match.player(us).deck);
                if (pile.stream().noneMatch(c -> c.training() == TrialTraining.TEMPER))
                    throw new AssertionError("The trained copy did not enter combat");
            }
            match.phase = Match.Phase.PLAYING;
            match.current = us;
            match.turn = 6;
            match.opponent(us).health = 1;
            match.opponent(us).deck.clear();
        });
        ctx.runOnClient(mc -> {
            if (!(mc.gui.screen() instanceof MatchScreen)) throw new AssertionError("Missing actual fight screen");
            ClientPlayNetworking.send(new MatchActionPayload(MatchActionPayload.END_TURN, 0, false, -1, -1));
        });
        ctx.waitTicks(10);
        ctx.runOnClient(mc -> {
            if (!(mc.gui.screen() instanceof MatchScreen)) throw new AssertionError("Trials replaced victory prematurely");
            mc.gui.screen().onClose();
            if (!(mc.gui.screen() instanceof TrialsScreen)) throw new AssertionError("Missing post-battle Trials hub");
        });
    }

    private static void selectMenu(ClientGameTestContext ctx, String option) {
        ctx.runOnClient(mc -> {var sc = mc.gui.screen();press(sc, option);press(sc, "Choose");});
        ctx.waitTicks(5);
    }

    /** Select a seeded path for coverage, following exits exactly rather than bypassing locked rooms. */
    private static List<String> route(TrialsAct act) {
        for (String root : act.next(List.of())) {
            var result = route(act, new ArrayList<>(List.of(root)));
            if (result != null) return result;
        }
        return null;
    }
    private static List<String> route(TrialsAct act, ArrayList<String> path) {
        var room = act.byId.get(path.getLast());
        if (room.row() == 1 && room.kind() != TrialsAct.Kind.SHOP) return null;
        if (room.kind() == TrialsAct.Kind.BOSS) {
            boolean event = path.stream().anyMatch(id -> act.byId.get(id).kind() == TrialsAct.Kind.EVENT);
            boolean elite = path.stream().anyMatch(id -> act.byId.get(id).kind() == TrialsAct.Kind.ELITE);
            return event && elite ? List.copyOf(path) : null;
        }
        for (String next : room.next()) {
            path.add(next);
            var result = route(act, path);
            path.removeLast();
            if (result != null) return result;
        }
        return null;
    }

    static TrialsRun snapshot(TestSingleplayerContext world) {
        var result = new AtomicReference<TrialsRun>();
        world.getServer().runOnServer(server -> {
            try {
                var method = TrialsTables.class.getDeclaredMethod("load", ServerPlayer.class);
                method.setAccessible(true);
                result.set((TrialsRun)method.invoke(null, server.getPlayerList().getPlayers().getFirst()));
            } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
        });
        return result.get();
    }
    private static void saveFixture(ServerPlayer player, TrialsRun run) {
        try {
            var method = TrialsTables.class.getDeclaredMethod("save", ServerPlayer.class, TrialsRun.class);
            method.setAccessible(true);
            method.invoke(null, player, run);
        } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
    }
    private TrialsTableScenario() {}
}

