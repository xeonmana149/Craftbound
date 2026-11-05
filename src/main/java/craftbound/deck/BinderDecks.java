package craftbound.deck;

import craftbound.binder.BinderContents;
import craftbound.card.CardClass;
import craftbound.component.ModComponents;
import craftbound.item.ModItems;
import craftbound.network.BinderDeckPayload;
import java.util.UUID;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

public final class BinderDecks {
    public static SavedDecks get(ItemStack binder) {
        return binder.getOrDefault(ModComponents.SAVED_DECKS, SavedDecks.EMPTY);
    }

    // Empty id selects a legacy case; a nonempty id selects a list in the specified binder.
    public static ItemStack resolve(Inventory inventory, int slot, String id) {
        if (slot < 0 || slot >= inventory.getContainerSize()) return ItemStack.EMPTY;
        ItemStack holder = inventory.getItem(slot);
        if (id.isEmpty()) return holder.is(ModItems.DECK_CASE) ? holder : ItemStack.EMPTY;
        if (!holder.is(ModItems.BINDER)) return ItemStack.EMPTY;
        SavedDecks.Entry entry = get(holder).find(id);
        return entry == null ? ItemStack.EMPTY : entry.asStack();
    }

    // 0 and 1 retain the original offhand/main hand wire values. Table screens bind to an inventory slot.
    public static int locationForSlot(int slot) { return slot + 2; }

    public static ItemStack holder(net.minecraft.world.entity.player.Player player, int location) {
        if (location == 0) return player.getOffhandItem();
        if (location == 1) return player.getMainHandItem();
        int slot = location - 2;
        var inventory = player.getInventory();
        return location >= 2 && slot < inventory.getContainerSize() ? inventory.getItem(slot) : ItemStack.EMPTY;
    }

    public static void handle(ServerPlayer player, BinderDeckPayload packet) {
        ItemStack binder = holder(player, packet.location());
        if (!binder.is(ModItems.BINDER)) return;
        try {
            // Creation IDs are random UUIDs supplied by the UI; never accept unbounded arbitrary keys.
            UUID.fromString(packet.deckId());
            SavedDecks saved = get(binder);
            SavedDecks.Entry entry = saved.find(packet.deckId());
            int action = packet.action();
            if (action == BinderDeckPayload.CREATE || action == BinderDeckPayload.IMPORT) {
                if (entry != null || saved.entries().size() >= SavedDecks.LIMIT) return;
                ItemStack stack;
                if (action == BinderDeckPayload.IMPORT) {
                    int slot = Integer.parseInt(packet.value());
                    stack = resolve(player.getInventory(), slot, "");
                    if (stack.isEmpty()) return;
                    DeckBuilding.migrate(player, stack);
                } else {
                    CardClass cardClass = CardClass.valueOf(packet.value());
                    if (cardClass == CardClass.NEUTRAL) return;
                    stack = new ItemStack(ModItems.DECK_CASE);
                    stack.set(ModComponents.DECK_CONTENTS, new DeckContents(cardClass, BinderContents.EMPTY, true));
                }
                SavedDecks.Entry imported = SavedDecks.Entry.fromStack(packet.deckId(), stack);
                if (action == BinderDeckPayload.IMPORT && saved.entries().stream().anyMatch(e ->
                        e.deck().equals(imported.deck()) && e.name().equals(imported.name()) && e.sleeve().equals(imported.sleeve()))) return;
                binder.set(ModComponents.SAVED_DECKS, saved.put(imported));
                return;
            }
            if (entry == null) return;
            if (action == BinderDeckPayload.DELETE) {
                binder.set(ModComponents.SAVED_DECKS, saved.remove(entry.id()));
                return;
            }
            ItemStack stack = entry.asStack();
            switch (action) {
                case BinderDeckPayload.ADD -> DeckBuilding.add(player, stack, Identifier.parse(packet.value()), packet.enchanted());
                case BinderDeckPayload.REMOVE -> DeckBuilding.remove(stack, Identifier.parse(packet.value()), packet.enchanted());
                case BinderDeckPayload.RENAME -> DeckBuilding.rename(stack, packet.value());
                case BinderDeckPayload.CLASS -> DeckBuilding.setClass(stack, CardClass.valueOf(packet.value()));
                case BinderDeckPayload.SLEEVE -> DeckBuilding.setSleeve(player, stack, Identifier.parse(packet.value()));
                case BinderDeckPayload.CLEAR -> stack.set(ModComponents.DECK_CONTENTS,
                        new DeckContents(entry.deck().deckClass(), BinderContents.EMPTY, true));
                default -> { return; }
            }
            binder.set(ModComponents.SAVED_DECKS, saved.put(SavedDecks.Entry.fromStack(entry.id(), stack)));
        } catch (IllegalArgumentException ignored) {
            // Invalid network input leaves both the collection and all lists unchanged.
        }
    }

    private BinderDecks() {}
}
