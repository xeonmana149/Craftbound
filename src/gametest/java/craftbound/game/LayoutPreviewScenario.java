package craftbound.game;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.platform.InputConstants;
import craftbound.block.CardTableBlockEntity;
import craftbound.block.ModBlocks;
import craftbound.card.CardCatalog;
import craftbound.card.Cards;
import craftbound.client.match.CommanderScreen;
import craftbound.client.match.LayoutPreviewSettingsScreen;
import craftbound.client.match.MatchScreen;
import craftbound.client.match.RelayScreen;
import craftbound.client.match.TableScreen;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;

/** Opens disposable multiplayer layouts through the real, permission-gated table menu. */
public final class LayoutPreviewScenario {
    public static void check(ClientGameTestContext ctx, TestSingleplayerContext world) {
        var location = new AtomicReference<BlockPos>();
        var oldBlock = new AtomicReference<BlockState>();
        var originalInventory = new AtomicReference<List<ItemStack>>();
        var inventory = new AtomicReference<List<ItemStack>>();
        var tableState = new AtomicReference<TableState>();
        var catalogue = CardCatalog.current();
        var settings = new AtomicReference<Screen>();

        // The preceding binder rendering fixture can leave a client-only prop.
        ctx.runOnClient(mc -> mc.player.getInventory().clearContent());
        world.getServer().runOnServer(server -> {
            var player = server.getPlayerList().getPlayers().getFirst();
            var pos = player.blockPosition().offset(3, 0, 3);
            if (server.overworld().getBlockEntity(pos) != null)
                throw new AssertionError("Preview fixture would overwrite another block entity");
            location.set(pos);
            oldBlock.set(server.overworld().getBlockState(pos));
            originalInventory.set(copyInventory(player.getInventory()));
            player.getInventory().clearContent();
            player.getInventory().setItem(9, new ItemStack(Items.EMERALD, 17));
            player.inventoryMenu.broadcastChanges();
            inventory.set(copyInventory(player.getInventory()));
            server.overworld().setBlockAndUpdate(pos, ModBlocks.CARD_TABLE.defaultBlockState());
            TableLobby.open(player, pos);
        });
        try {
            ctx.waitTicks(8);
            world.getServer().runOnServer(server -> {
                var player = server.getPlayerList().getPlayers().getFirst();
                tableState.set(TableState.read((CardTableBlockEntity)server.overworld().getBlockEntity(location.get())));
                assertUntouched(world, player, location.get(), tableState.get(), inventory.get());
            });
            ctx.runOnClient(mc -> {
                if (!(mc.gui.screen() instanceof TableScreen table))
                    throw new AssertionError("Table did not open its real lobby");
                if (!((List<?>)field(table, "decks")).isEmpty())
                    throw new AssertionError("Preview must be reachable without a binder or valid deck");
                if (TrialsMapScenario.button(table, "Ready").active)
                    throw new AssertionError("Empty inventory unexpectedly has a ready deck");
                TrialsMapScenario.press(table, "Developer");
                if (!(mc.gui.screen() instanceof LayoutPreviewSettingsScreen))
                    throw new AssertionError("Developer did not open layout settings");
                settings.set(mc.gui.screen());
                TrialsMapScenario.button(settings.get(), "Board: Populated");
                TrialsMapScenario.button(settings.get(), "Seat: 1 / 4");
                if (!field(settings.get(), "mode").toString().equals("DUOS"))
                    throw new AssertionError("The default preview should be Team Duos");
            });
            TrialsMapScenario.capture(ctx, "craftbound-layout-preview-settings-wide", 1280, 720);
            TrialsMapScenario.capture(ctx, "craftbound-layout-preview-settings-compact", 640, 480);
            ctx.runOnClient(mc -> TrialsMapScenario.press(mc.gui.screen(), "Board: Populated"));

            String[] modes = {"Player Vs Player", "Team Duos", "Commander"};
            String[] names = {"duel", "duos", "commander"};
            int[] seats = {1, 3, 2};
            for (int i = 0; i < modes.length; i++) {
                final int mode = i, perspective = seats[i];
                ctx.runOnClient(mc -> {
                    TrialsMapScenario.press(mc.gui.screen(), modes[mode]);
                    setSeat(mc.gui.screen(), perspective);
                    TrialsMapScenario.button(mc.gui.screen(), "Board: Full");
                    TrialsMapScenario.press(mc.gui.screen(), "Open preview");
                });
                ctx.waitTicks(5);
                ctx.runOnClient(mc -> verifyPreview(mc.gui.screen(), mode, perspective, 7, 3, 10));
                TrialsMapScenario.capture(ctx, "craftbound-layout-preview-" + names[i] + "-full-wide", 1280, 720);
                TrialsMapScenario.capture(ctx, "craftbound-layout-preview-" + names[i] + "-full-compact", 640, 480);
                if (mode > 0) {
                    ctx.runOnClient(mc -> MultiplayerBoardScenario.check((craftbound.client.match.MultiplayerBoardScreen)mc.gui.screen()));
                    ctx.waitTicks(2);
                }
                if (mode == 1) inspectDuos(ctx);
                if (mode == 0) ctx.runOnClient(craftbound.client.match.LocalLayoutPreview::catalogueChanged);
                else escape(ctx);
                ctx.runOnClient(mc -> {
                    if (mc.gui.screen() != settings.get())
                        throw new AssertionError("Esc did not return to the same developer settings");
                    if ((int)field(settings.get(), "seat") != perspective)
                        throw new AssertionError("Returning from preview discarded the selected seat");
                });
                // Empty changes the battlefield only; a sample hand remains available.
                ctx.runOnClient(mc -> {
                    TrialsMapScenario.press(mc.gui.screen(), "Board: Full");
                    TrialsMapScenario.press(mc.gui.screen(), "Open preview");
                });
                ctx.waitTicks(3);
                ctx.runOnClient(mc -> verifyPreview(mc.gui.screen(), mode, perspective, 0, 0, 6));
                escape(ctx);
                ctx.runOnClient(mc -> {
                    TrialsMapScenario.press(mc.gui.screen(), "Board: Empty");
                    TrialsMapScenario.press(mc.gui.screen(), "Open preview");
                });
                ctx.waitTicks(3);
                ctx.runOnClient(mc -> verifyPreview(mc.gui.screen(), mode, perspective, 4, 2, 6));
                escape(ctx);
                ctx.runOnClient(mc -> TrialsMapScenario.press(mc.gui.screen(), "Board: Populated"));
                world.getServer().runOnServer(server -> assertUntouched(world,
                        server.getPlayerList().getPlayers().getFirst(), location.get(), tableState.get(), inventory.get()));
            }
            ctx.runOnClient(mc -> {
                TrialsMapScenario.press(mc.gui.screen(), "Back");
                if (!(mc.gui.screen() instanceof TableScreen)) throw new AssertionError("Back did not return to the table");
                assertInventory(mc.player.getInventory(), inventory.get());
                mc.gui.setScreen(new TableScreen(location.get(), "", "", false));
                if (TrialsMapScenario.has(mc.gui.screen(), "Developer"))
                    throw new AssertionError("Developer controls leaked to an unauthorized player");
                mc.gui.setScreen(new TableScreen(location.get(), "", "", true));
                TrialsMapScenario.button(mc.gui.screen(), "Developer");
                mc.gui.setScreen(new TableScreen(location.get(), "", ""));
                if (TrialsMapScenario.has(mc.gui.screen(), "Developer"))
                    throw new AssertionError("Legacy constructor must default to no developer access");
            });
            if (CardCatalog.current() != catalogue)
                throw new AssertionError("Opening previews replaced the live card catalogue");
            world.getServer().runOnServer(server -> assertUntouched(world,
                    server.getPlayerList().getPlayers().getFirst(), location.get(), tableState.get(), inventory.get()));
        } finally {
            ctx.runOnClient(mc -> mc.gui.setScreen(null));
            world.getServer().runOnServer(server -> {
                var player = server.getPlayerList().getPlayers().getFirst();
                for (int slot = 0; slot < originalInventory.get().size(); slot++)
                    player.getInventory().setItem(slot, originalInventory.get().get(slot).copy());
                player.inventoryMenu.broadcastChanges();
                server.overworld().setBlockAndUpdate(location.get(), oldBlock.get());
            });
            ctx.waitTicks(3);
        }
    }

    private static void setSeat(Screen screen, int seat) {
        for (int attempt = 0; attempt < 4 && (int)field(screen, "seat") != seat; attempt++) {
            var button = TrialsMapScenario.buttons(screen).stream()
                    .filter(b -> b.getMessage().getString().startsWith("Seat: ")).findFirst().orElseThrow();
            TrialsMapScenario.click(screen, button, InputConstants.MOUSE_BUTTON_LEFT);
        }
        if ((int)field(screen, "seat") != seat) throw new AssertionError("Seat selector cannot reach " + seat);
    }

    private static void verifyPreview(Screen screen, int mode, int you, int mobs, int blocks, int handSize) {
        Class<?> expected = switch (mode) {
            case 0 -> MatchScreen.class; case 1 -> RelayScreen.class; default -> CommanderScreen.class;
        };
        if (screen.getClass() != expected) throw new AssertionError("Expected real " + expected + ", got " + screen);
        if (!(((craftbound.client.match.LocalLayoutPreview)screen).layoutPreviewParent() instanceof LayoutPreviewSettingsScreen))
            throw new AssertionError("The actual match screen lacks its safe return destination");
        if (mode == 0) {
            if (!(boolean)field(screen, "readOnly")) throw new AssertionError("Duel preview permits gameplay");
            var state = field(screen, "shown");
            if ((int)field(state, "you") != you || (boolean)field(state, "myTurn"))
                throw new AssertionError("Duel perspective or read-only turn is wrong");
            var sides = (Object[])field(state, "sides");
            if (sides.length != 2) throw new AssertionError("Duel must have two seats");
            for (int seat = 0; seat < sides.length; seat++) {
                var side = sides[seat];
                var board = (List<?>)field(side, "board");
                long count = board.stream().filter(unit -> (boolean)field(unit, "block")).count();
                if (count != blocks || board.size() - count != mobs)
                    throw new AssertionError("Wrong duel preview row populations");
                for (var unit : board) {
                    if ((boolean)field(unit, "ready")) throw new AssertionError("A preview mob can attack");
                    requireCard((Identifier)field(unit, "id"));
                }
                var hand = (List<?>)field(side, "hand");
                if (hand.size() != (seat == you ? handSize : 0))
                    throw new AssertionError("Duel preview exposed the wrong private hand");
                for (var card : hand) {
                    requireCard((Identifier)field(card, "id"));
                    if ((boolean)field(card, "playable")) throw new AssertionError("Preview card is playable");
                }
            }
            return;
        }
        var data = ((craftbound.client.match.MultiplayerBoardScreen)screen).snapshot();
        if (!data.get("layoutPreview").getAsBoolean() || data.get("you").getAsInt() != you
                || data.get("myTurn").getAsBoolean() || !data.getAsJsonObject("moves").isEmpty())
            throw new AssertionError("Multiplayer preview has the wrong perspective or actionable moves");
        var players = data.getAsJsonArray("players");
        if (players.size() != 4) throw new AssertionError("Four-seat preview lost a player");
        for (int seat = 0; seat < players.size(); seat++) {
            var player = players.get(seat).getAsJsonObject();
            var board = player.getAsJsonArray("board");
            long count = board.asList().stream().filter(v -> v.getAsJsonObject().get("block").getAsBoolean()).count();
            if (count != blocks || board.size() - count != mobs)
                throw new AssertionError("Wrong four-seat preview row populations");
            for (var unit : board) requireCard(Identifier.parse(unit.getAsJsonObject().get("id").getAsString()));
            var hand = player.getAsJsonArray("hand");
            int size = hand == null ? 0 : hand.size();
            if (seat != you && size != 0 || seat == you && (mode == 1 ? size != 0 && size != handSize : size != handSize))
                throw new AssertionError("Four-seat preview exposed another player's private hand");
            if (hand != null) checkHand(hand);
        }
        if (mode == 1) {
            if (data.getAsJsonArray("hand").size() != handSize) throw new AssertionError("Duos lost the focused hand");
            checkHand(data.getAsJsonArray("hand"));
            if (TrialsMapScenario.button(screen, "Ready").active || TrialsMapScenario.has(screen, "Concede"))
                throw new AssertionError("Duos preview has live gameplay controls");
        }
    }

    private static void inspectDuos(ClientGameTestContext ctx) {
        ctx.runOnClient(mc -> {
            var sc = (craftbound.client.match.MultiplayerBoardScreen)mc.gui.screen();
            var data=sc.snapshot();int seat=data.get("you").getAsInt();
            int mid=data.getAsJsonArray("players").get(seat).getAsJsonObject().getAsJsonArray("board").get(0).getAsJsonObject().get("mid").getAsInt();
            var bounds=sc.targetBounds(seat,mid);
            MultiplayerBoardScenario.click(sc,bounds,InputConstants.MOUSE_BUTTON_LEFT);
            if (!sc.selectedMove().isEmpty()) throw new AssertionError("Preview started an attack");
            MultiplayerBoardScenario.click(sc,bounds,InputConstants.MOUSE_BUTTON_RIGHT);
            if (!sc.inspecting()) throw new AssertionError("Preview card inspection failed");
        });
        TrialsMapScenario.capture(ctx, "craftbound-layout-preview-card-inspect", 1280, 720);
        escape(ctx);
        ctx.runOnClient(mc -> {
            if (!(mc.gui.screen() instanceof RelayScreen sc) || sc.inspecting())
                throw new AssertionError("First Esc must dismiss inspection without leaving the preview");
        });
    }

    private static void checkHand(JsonArray hand) {
        for (var raw : hand) {
            var card = raw.getAsJsonObject();
            requireCard(Identifier.parse(card.get("id").getAsString()));
            if (card.has("playable") && card.get("playable").getAsBoolean())
                throw new AssertionError("A sample card is playable");
        }
    }
    private static void requireCard(Identifier id) {
        if (Cards.get(id) == null || Cards.deleted(id)) throw new AssertionError("Preview invented or revived card " + id);
    }
    private static void escape(ClientGameTestContext ctx) { ctx.getInput().pressKey(InputConstants.KEY_ESCAPE); ctx.waitTicks(2); }
    private static Object field(Object object, String name) { return TrialsMapScenario.field(object, name); }
    private static List<ItemStack> copyInventory(net.minecraft.world.Container inventory) {
        var copy = new ArrayList<ItemStack>();
        for (int i = 0; i < inventory.getContainerSize(); i++) copy.add(inventory.getItem(i).copy());
        return copy;
    }
    private static void assertInventory(net.minecraft.world.Container actual, List<ItemStack> expected) {
        for (int i = 0; i < expected.size(); i++) if (!ItemStack.matches(actual.getItem(i), expected.get(i)))
            throw new AssertionError("Preview changed inventory slot " + i);
    }
    private static void assertUntouched(TestSingleplayerContext world, net.minecraft.server.level.ServerPlayer player,
                                        BlockPos pos, TableState expected, List<ItemStack> inventory) {
        var level = (net.minecraft.server.level.ServerLevel)player.level();
        assertInventory(player.getInventory(), inventory);
        if (!expected.equals(TableState.read((CardTableBlockEntity)level.getBlockEntity(pos)))
                || MatchManager.inMatch(player) || MatchManager.matchAt(level, pos) != null
                || RelayTables.matchAt(level, pos) != null || CommanderTables.matchAt(level, pos) != null
                || TableLobby.waiting(player, pos) || !RelayTables.waiting(level, pos).isEmpty()
                || !CommanderTables.waiting(level, pos).isEmpty())
            throw new AssertionError("A local layout preview wrote server table or matchmaking state");
        if (((CardTableBlockEntity)level.getBlockEntity(pos)).isReady(player.getUUID(), level.getGameTime()))
            throw new AssertionError("Preview readied the player at the real table");
    }
    private record TableState(String match, String board, float yaw, java.util.UUID master) {
        static TableState read(CardTableBlockEntity table) {
            return new TableState(table.savedMatch(), table.publicBoard(), table.boardYaw(), table.waitingMaster());
        }
    }
    private LayoutPreviewScenario() {}
}

