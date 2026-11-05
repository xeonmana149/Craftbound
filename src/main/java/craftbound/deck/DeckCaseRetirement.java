package craftbound.deck;

import craftbound.binder.BinderContents;
import craftbound.card.CardClass;
import craftbound.component.ModComponents;
import craftbound.item.ModItems;
import craftbound.sleeve.SleeveCollection;
import craftbound.sleeve.Sleeves;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;

// Retain the hidden registry ID so old saves can load, then retire real legacy stacks.
// SavedDecks' temporary view stacks never enter inventories and are not affected.
public final class DeckCaseRetirement {
    private static int ticks;

    public static void initialize() {
        ServerPlayConnectionEvents.JOIN.register((listener, sender, server) -> migratePlayer(listener.player));
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (++ticks % 20 != 0) return;
            for (ServerPlayer player : server.getPlayerList().getPlayers()) migratePlayer(player);
        });
        ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
            if (entity instanceof ItemEntity item && item.getItem().is(ModItems.DECK_CASE)) {
                ItemStack replacement = standalone(item.getItem());
                if (replacement.isEmpty()) item.discard();
                else item.setItem(replacement);
            }
        });
    }

    // Open containers and ender storage retire cases without force-loading or rewriting world chunks.
    private static void migratePlayer(ServerPlayer player) {
        scan(player, player.getInventory());
        scan(player, player.getEnderChestInventory());
        for (var slot : player.containerMenu.slots) {
            if (slot.getItem().is(ModItems.DECK_CASE)) slot.set(retire(player, slot.getItem()));
        }
        ItemStack carried = player.containerMenu.getCarried();
        if (carried.is(ModItems.DECK_CASE)) player.containerMenu.setCarried(retire(player, carried));
    }

    private static void scan(ServerPlayer player, Container container) {
        for (int i = 0; i < container.getContainerSize(); i++) {
            ItemStack old = container.getItem(i);
            if (old.is(ModItems.DECK_CASE)) container.setItem(i, retire(player, old));
        }
    }

    public static boolean meaningful(DeckContents deck, String name) {
        return deck.deckClass() != CardClass.NEUTRAL || deck.size() > 0 || !name.isBlank();
    }

    public static boolean sameDeck(SavedDecks.Entry a, SavedDecks.Entry b) {
        return a.name().equals(b.name()) && a.deck().equals(b.deck()) && a.sleeve().equals(b.sleeve());
    }

    public static boolean accepts(SavedDecks saved, SavedDecks.Entry entry) {
        return saved.entries().size() < SavedDecks.LIMIT || saved.entries().stream().anyMatch(e -> sameDeck(e, entry));
    }

    public static SavedDecks preserve(SavedDecks saved, SavedDecks.Entry entry) {
        return saved.entries().stream().anyMatch(e -> sameDeck(e, entry)) ? saved : saved.put(entry);
    }

    private static ItemStack retire(ServerPlayer player, ItemStack old) {
        SleeveCollection.unlock(player, old.getOrDefault(ModComponents.SLEEVE_ID, Sleeves.CLASSIC));
        var entry = SavedDecks.Entry.fromStack(UUID.randomUUID().toString(), old);
        if (!meaningful(entry.deck(), entry.name())) return ItemStack.EMPTY;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack binder = player.getInventory().getItem(i);
            if (!binder.is(ModItems.BINDER)) continue;
            SavedDecks saved = BinderDecks.get(binder);
            if (!accepts(saved, entry)) continue;
            // Cases predating list-based decks still own physical cards; return those exactly once.
            DeckBuilding.migrate(player, old);
            binder.set(ModComponents.SAVED_DECKS, preserve(saved, entry));
            return ItemStack.EMPTY;
        }
        // No free destination: replace in the same slot, so even a full inventory cannot lose a deck.
        return standalone(old);
    }

    public static BinderContents physicalContents(DeckContents original) {
        return original.list() ? BinderContents.EMPTY : original.cards();
    }

    private static ItemStack standalone(ItemStack old) {
        DeckContents original = old.getOrDefault(ModComponents.DECK_CONTENTS, DeckContents.EMPTY);
        var entry = SavedDecks.Entry.fromStack(UUID.randomUUID().toString(), old);
        if (!meaningful(entry.deck(), entry.name())) {
            // A dropped empty case can still carry an old physical sleeve; preserve it.
            var sleeve = old.get(ModComponents.SLEEVE_ID);
            return sleeve == null || sleeve.equals(Sleeves.CLASSIC) ? ItemStack.EMPTY : craftbound.item.SleeveItem.of(sleeve);
        }
        ItemStack binder = new ItemStack(ModItems.BINDER);
        binder.set(ModComponents.BINDER_CONTENTS, physicalContents(original));
        binder.set(ModComponents.SAVED_DECKS, SavedDecks.EMPTY.put(entry));
        return binder;
    }

    private DeckCaseRetirement() {}
}
