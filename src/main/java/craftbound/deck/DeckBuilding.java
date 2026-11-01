package craftbound.deck;

import craftbound.ModAdvancements;
import craftbound.binder.BinderContents;
import craftbound.card.CardClass;
import craftbound.card.CardDefinition;
import craftbound.card.Cards;
import craftbound.card.DeckRules;
import craftbound.component.ModComponents;
import craftbound.item.CardItem;
import craftbound.item.ModItems;
import craftbound.item.SleeveItem;
import craftbound.sleeve.Sleeves;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Prediction;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

// Moving real cards between the binders in your inventory and a Deck Case. Runs on the server only.
// Adding a card takes it out of a binder; removing it puts it back into a binder.
public class DeckBuilding {
    // Why a card can't be added right now, or null if it can. The screen uses the same check to grey cards out.
    public static String whyNot(DeckContents deck, Identifier id) {
        CardDefinition card = Cards.get(id);
        if (card == null || card.token()) {
            return "deck_ui.craftbound.not_allowed";
        }
        // A deck has no class until you pick one, and you can't add anything before that.
        if (deck.deckClass() == CardClass.NEUTRAL) {
            return "deck_ui.craftbound.pick_class";
        }
        if (!DeckRules.allowed(id, deck.deckClass())) {
            return "deck_ui.craftbound.wrong_class";
        }
        if (deck.size() >= DeckRules.DECK_SIZE) {
            return "deck_ui.craftbound.full";
        }
        if (deck.copies(id) >= DeckRules.maxCopies(card.rarity())) {
            return "deck_ui.craftbound.max_copies";
        }
        return null;
    }

    public static void add(ServerPlayer player, ItemStack deckCase, Identifier id, boolean enchanted) {
        DeckContents deck = deckCase.getOrDefault(ModComponents.DECK_CONTENTS, DeckContents.EMPTY);
        if (whyNot(deck, id) != null) {
            return;
        }
        ItemStack binder = findBinderWith(player.getInventory(), id, enchanted);
        if (binder.isEmpty()) {
            return;
        }
        BinderContents contents = binder.getOrDefault(ModComponents.BINDER_CONTENTS, BinderContents.EMPTY);
        binder.set(ModComponents.BINDER_CONTENTS, contents.add(id, enchanted, -1));
        DeckContents updated = deck.add(id, enchanted, 1);
        deckCase.set(ModComponents.DECK_CONTENTS, updated);
        if (updated.size() == DeckRules.DECK_SIZE) {
            ModAdvancements.deckCompleted(player);
        }
    }

    public static void remove(ServerPlayer player, ItemStack deckCase, Identifier id, boolean enchanted) {
        DeckContents deck = deckCase.getOrDefault(ModComponents.DECK_CONTENTS, DeckContents.EMPTY);
        if (deck.cards().count(id, enchanted) <= 0) {
            return;
        }
        deckCase.set(ModComponents.DECK_CONTENTS, deck.add(id, enchanted, -1));
        ItemStack binder = firstBinder(player.getInventory());
        if (binder.isEmpty()) {
            // No binder to put it back in, so hand over the card itself.
            player.getInventory().placeItemBackInInventory(CardItem.of(id, enchanted), Prediction.SERVER_ONLY);
        } else {
            BinderContents contents = binder.getOrDefault(ModComponents.BINDER_CONTENTS, BinderContents.EMPTY);
            binder.set(ModComponents.BINDER_CONTENTS, contents.add(id, enchanted, 1));
        }
    }

    // A deck's class is one of the real classes, and it can only change if every card already in the deck is
    // still allowed afterwards. Neutral means "not picked yet": an empty deck can go back to that to pick again.
    public static void setClass(ItemStack deckCase, CardClass newClass) {
        DeckContents deck = deckCase.getOrDefault(ModComponents.DECK_CONTENTS, DeckContents.EMPTY);
        if (newClass == CardClass.NEUTRAL ? deck.size() > 0 : !canSwitchTo(deck, newClass)) {
            return;
        }
        deckCase.set(ModComponents.DECK_CONTENTS, deck.withClass(newClass));
    }

    // Puts a sleeve from your inventory on the Deck Case; the sleeve it had goes back to your inventory.
    // Classic is the default card back, so picking it just takes the current sleeve off.
    public static void setSleeve(ServerPlayer player, ItemStack deckCase, Identifier sleeve) {
        Identifier current = deckCase.get(ModComponents.SLEEVE_ID);
        Identifier currentOrClassic = current == null ? Sleeves.CLASSIC : current;
        if (sleeve.equals(currentOrClassic)) {
            return;
        }
        Inventory inventory = player.getInventory();
        if (!sleeve.equals(Sleeves.CLASSIC)) {
            ItemStack owned = findSleeve(inventory, sleeve);
            if (owned.isEmpty()) {
                return;
            }
            owned.shrink(1);
        }
        if (current != null) {
            inventory.placeItemBackInInventory(SleeveItem.of(current), Prediction.SERVER_ONLY);
        }
        if (sleeve.equals(Sleeves.CLASSIC)) {
            deckCase.remove(ModComponents.SLEEVE_ID);
        } else {
            deckCase.set(ModComponents.SLEEVE_ID, sleeve);
        }
    }

    private static ItemStack findSleeve(Inventory inventory, Identifier sleeve) {
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (stack.is(ModItems.SLEEVE) && sleeve.equals(stack.get(ModComponents.SLEEVE_ID))) {
                return stack;
            }
        }
        return ItemStack.EMPTY;
    }

    public static boolean canSwitchTo(DeckContents deck, CardClass newClass) {
        for (Identifier id : Cards.collectible()) {
            if (deck.copies(id) > 0 && !DeckRules.allowed(id, newClass)) {
                return false;
            }
        }
        return true;
    }

    private static ItemStack findBinderWith(Inventory inventory, Identifier id, boolean enchanted) {
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (stack.is(ModItems.BINDER)
                    && stack.getOrDefault(ModComponents.BINDER_CONTENTS, BinderContents.EMPTY).count(id, enchanted) > 0) {
                return stack;
            }
        }
        return ItemStack.EMPTY;
    }

    private static ItemStack firstBinder(Inventory inventory) {
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            if (inventory.getItem(i).is(ModItems.BINDER)) {
                return inventory.getItem(i);
            }
        }
        return ItemStack.EMPTY;
    }
}
