package craftbound.game;

import craftbound.block.ModBlocks;
import craftbound.card.CardClass;
import craftbound.client.deck.DeckCaseScreen;
import craftbound.client.match.TableScreen;
import craftbound.deck.BinderDecks;
import craftbound.item.BinderItem;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import net.fabricmc.fabric.api.client.gametest.v1.context.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;

/** The table edits a carried binder in place, even when both hands are empty. */
public final class TableBinderScenario {
    private static Object field(Object obj, String name) {
        try {
            var field = obj.getClass().getDeclaredField(name);
            field.setAccessible(true);
            return field.get(obj);
        } catch (ReflectiveOperationException ex) { throw new AssertionError(ex); }
    }

    private static void capture(ClientGameTestContext ctx, String name, int width, int height) {
        int[] original=ctx.computeOnClient(mc -> {
            var screen=mc.gui.screen();int[] old={screen.width,screen.height};
            mc.gui.toastManager().clear();screen.resize(width/2,height/2);return old;
        });
        ctx.waitTicks(2);
        ctx.takeScreenshot(net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions.of(name).withSize(width,height));
        ctx.runOnClient(mc -> mc.gui.screen().resize(original[0],original[1]));
        ctx.waitTicks(2);
    }

    public static void check(ClientGameTestContext ctx, TestSingleplayerContext world) {
        var server = world.getServer();
        var location = new AtomicReference<BlockPos>();
        var original = new AtomicReference<ItemStack>();
        var id = new AtomicReference<String>();
        int slot = 12;
        // Earlier rendering checks put a client-only prop in the main hand.
        ctx.runOnClient(mc -> mc.player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, ItemStack.EMPTY));
        server.runOnServer(ms -> {
            var player = ms.getPlayerList().getPlayers().getFirst();
            var pos = player.blockPosition().offset(-2, 0, 0);
            location.set(pos);
            original.set(player.getInventory().getItem(slot));
            player.getInventory().setItem(slot, BinderItem.creativeBinder());
            player.inventoryMenu.broadcastChanges();
            ms.overworld().setBlockAndUpdate(pos, ModBlocks.CARD_TABLE.defaultBlockState());
            TableLobby.open(player, pos);
        });
        ctx.waitTicks(8);
        ctx.runOnClient(mc -> CommanderTableScenario.clickButton(mc.gui.screen(), "Collection"));
        ctx.waitTicks(4);
        capture(ctx,"craftbound-binder-collection-large",1280,674);
        capture(ctx,"craftbound-binder-collection-compact",640,480);
        ctx.runOnClient(mc -> {
            var binder=mc.gui.screen();
            int[] filter=(int[])field(binder,"filtersButton"), rules=(int[])field(binder,"rulesButton");
            int[] list=(int[])field(binder,"listArea"); int headingSpace=rules[0]-list[0]-4;
            if (headingSpace < mc.font.width("My Decks") || rules[1]+rules[3] >= list[1])
                throw new AssertionError("Rules and deck heading must have disjoint space above deck list");
            CommanderTableScenario.click(binder,filter[0]+3,filter[1]+3);
        });
        ctx.waitTicks(2);
        ctx.runOnClient(mc -> {
            var binder=mc.gui.screen();int[] button=(int[])field(binder,"enchantedVersionsButton");
            if (!((List<?>)field(binder,"cardSpots")).isEmpty()) throw new AssertionError("Filter drawer must intercept card clicks");
            CommanderTableScenario.click(binder,button[0]+3,button[1]+3);
            int[] filter=(int[])field(binder,"filtersButton");CommanderTableScenario.click(binder,filter[0]+3,filter[1]+3);
        });
        ctx.waitTicks(2);
        ctx.runOnClient(mc -> {
            var cards=(List<?>)field(mc.gui.screen(),"cardSpots");
            if(cards.size()<2 || !field(cards.get(0),"id").equals(field(cards.get(1),"id")) || (boolean)field(cards.get(0),"enchanted") || !(boolean)field(cards.get(1),"enchanted"))
                throw new AssertionError("Normal and enchanted variants must fit in adjacent slots");
        });
        ctx.runOnClient(mc -> {
            if (!(mc.gui.screen() instanceof DeckCaseScreen binder) || !Integer.valueOf(slot+2).equals(field(binder,"holderLocation")))
                throw new AssertionError("Table did not open carried binder: " + mc.gui.screen() + " / " + (mc.gui.screen() instanceof DeckCaseScreen d ? field(d,"holderLocation") : "none"));
            binder.onClose();
        });
        ctx.waitTicks(3);
        ctx.runOnClient(mc -> CommanderTableScenario.clickButton(mc.gui.screen(), "New Deck"));
        ctx.waitTicks(4);
        ctx.runOnClient(mc -> {
            var picker = mc.gui.screen();
            var options = (List<?>)field(picker, "classChoices");
            int[] option = options.stream().map(int[].class::cast).filter(x -> x[4] == CardClass.FIGHTER.ordinal()).findFirst().orElseThrow();
            CommanderTableScenario.click(picker, option[0]+4, option[1]+4);
        });
        ctx.waitTicks(8);
        ctx.runOnClient(mc -> {
            Screen editor = mc.gui.screen();
            id.set((String)field(editor,"savedDeckId"));
            var spot = ((List<?>)field(editor, "cardSpots")).getFirst();
            CommanderTableScenario.click(editor, (int)field(spot, "x")+3, (int)field(spot, "y")+3);
        });
        ctx.waitTicks(7);
        capture(ctx,"craftbound-binder-deck-large",1280,674);
        server.runOnServer(ms -> {
            var held = ms.getPlayerList().getPlayers().getFirst().getInventory().getItem(slot);
            var entry = BinderDecks.get(held).entries().stream().filter(e -> e.id().equals(id.get())).findFirst().orElseThrow();
            if (entry.deck().deckClass()!=CardClass.FIGHTER || entry.deck().size()!=1)
                throw new AssertionError("Deck edits were not saved to the carried binder");
        });
        ctx.runOnClient(mc -> mc.gui.screen().onClose());
        ctx.waitTicks(4);
        ctx.runOnClient(mc -> {
            if (!(mc.gui.screen() instanceof TableScreen)) throw new AssertionError("Editor did not return to table");
            CommanderTableScenario.clickButton(mc.gui.screen(), "Edit Deck");
            if (!(mc.gui.screen() instanceof DeckCaseScreen editor) || !id.get().equals(field(editor,"savedDeckId")))
                throw new AssertionError("Edit Deck opened a different deck");
            editor.onClose();
        });
        ctx.runOnClient(mc -> mc.gui.setScreen(null));
        // Earlier rendering checks put a client-only prop in the main hand.
        ctx.runOnClient(mc -> mc.player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, ItemStack.EMPTY));
        server.runOnServer(ms -> {
            var player = ms.getPlayerList().getPlayers().getFirst();
            player.getInventory().setItem(slot,original.get());
            player.inventoryMenu.broadcastChanges();
            ms.overworld().removeBlock(location.get(),false);
        });
    }
    private TableBinderScenario() {}
}
