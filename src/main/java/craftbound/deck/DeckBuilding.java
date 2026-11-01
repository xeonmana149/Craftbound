package craftbound.deck;

import craftbound.binder.BinderContents;
import craftbound.card.CardClass;
import craftbound.card.CardDefinition;
import craftbound.card.Cards;
import craftbound.card.DeckRules;
import craftbound.component.ModComponents;
import craftbound.item.CardItem;
import craftbound.item.ModItems;
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
        deckCase.set(ModComponents.DECK_CONTENTS, deck.add(id, enchanted, 1));
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

    // A deck can only switch class if every card in it is still allowed afterwards.
    public static void setClass(ItemStack deckCase, CardClass newClass) {
        DeckContents deck = deckCase.getOrDefault(ModComponents.DECK_CONTENTS, DeckContents.EMPTY);
        if (!canSwitchTo(deck, newClass)) {
            return;
        }
        deckCase.set(ModComponents.DECK_CONTENTS, deck.withClass(newClass));
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
