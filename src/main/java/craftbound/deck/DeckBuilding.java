package craftbound.deck;

import craftbound.ModAdvancements;
import craftbound.binder.BinderContents;
import craftbound.binder.Disenchanting;
import craftbound.item.BinderItem;
import craftbound.card.CardClass;
import craftbound.card.CardDefinition;
import craftbound.card.Cards;
import craftbound.card.DeckRules;
import craftbound.component.ModComponents;
import craftbound.item.CardItem;
import craftbound.item.ModItems;
import craftbound.sleeve.SleeveCollection;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import craftbound.sleeve.Sleeves;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Prediction;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

// Building decks, the Hearthstone way: a Deck Case stores a list of cards, and the cards themselves stay in
// your binder. Every Deck Case uses the same collection, so one pair of Zombies can be in all your decks.
// You can only add copies you own, and a deck needs the binder that owns its cards to be played.
// Runs on the server only, except the checks the screen borrows to grey cards out.
public class DeckBuilding {
    // How many copies of a card (normal or Enchanted) the binders in this inventory own.
    public static int owned(Inventory inventory, Identifier id, boolean enchanted) {
        int total = 0;
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (stack.is(ModItems.BINDER)) {
                total += stack.getOrDefault(ModComponents.BINDER_CONTENTS, BinderContents.EMPTY).count(id, enchanted);
            }
        }
        return total;
    }

    // How many copies the deck lists that the binders you're carrying don't have. A deck missing cards can't be played.
    public static int missing(Inventory inventory, DeckContents deck, Identifier id, boolean enchanted) {
        return Math.max(0, deck.cards().count(id, enchanted) - owned(inventory, id, enchanted));
    }

    public static final int MAX_NAME_LENGTH = 32;

    // Saved decks are edited and checked as a temporary stack carrying DECK_CONTENTS, SLEEVE_ID and the deck's name.
    // It is never given to a player, so the item underneath doesn't matter.
    public static ItemStack deckStack() { return new ItemStack(net.minecraft.world.item.Items.PAPER); }

    // The deck's own name, or its default one.
    public static Component name(ItemStack deck) {
        Component custom = deck.get(DataComponents.CUSTOM_NAME);
        return custom != null ? custom : defaultName(deck.getOrDefault(ModComponents.DECK_CONTENTS, DeckContents.EMPTY));
    }

    // A deck without a name of its own is called after its class: "Farmer Deck" ("New Deck" before a class is picked).
    public static Component defaultName(DeckContents deck) {
        return deck.deckClass() == CardClass.NEUTRAL ? Component.translatable("deck_ui.craftbound.new_deck")
                : Component.translatable("deck_ui.craftbound.deck_title", deck.deckClass().displayName());
    }

    // The name you gave a deck, or "" if it still has its default name.
    public static String customName(ItemStack deckCase) {
        Component name = deckCase.get(DataComponents.CUSTOM_NAME);
        return name == null ? "" : name.getString();
    }

    // Names a deck. The name is the Deck Case's own item name (not in italics like an anvil name), so it shows
    // everywhere the case does: your inventory, its tooltip, the table's lobby and matches. A blank name, or the
    // default one, goes back to the default.
    public static void rename(ItemStack deckCase, String name) {
        String clean = name.replace("§", "").strip();
        if (clean.length() > MAX_NAME_LENGTH) {
            clean = clean.substring(0, MAX_NAME_LENGTH).strip();
        }
        DeckContents deck = deckCase.getOrDefault(ModComponents.DECK_CONTENTS, DeckContents.EMPTY);
        if (clean.isEmpty() || clean.equals(defaultName(deck).getString())) {
            deckCase.remove(DataComponents.CUSTOM_NAME);
        } else {
            deckCase.set(DataComponents.CUSTOM_NAME, Component.literal(clean).withStyle(style -> style.withItalic(false)));
        }
    }

    // Why this Deck Case can't be played at a card table, as a translation key, or null if it can. The table's
    // lobby uses it to grey out decks; the server checks again when you press Ready.
    public static String whyCantPlay(Inventory inventory, ItemStack deckCase) {
        if (!deckCase.has(ModComponents.DECK_CONTENTS)) {
            return "match_ui.craftbound.no_deck";
        }
        DeckContents deck = deckCase.getOrDefault(ModComponents.DECK_CONTENTS, DeckContents.EMPTY);
        return whyCantPlay(deck, (id, enchanted) -> owned(inventory, id, enchanted));
    }

    // Shared by the table and ownership regression checks. Counts are read at match start,
    // never reserved by a saved list, and distinguish normal from Enchanted copies.
    public static String whyCantPlay(DeckContents deck, java.util.function.BiFunction<Identifier, Boolean, Integer> ownedCopies) {
        var selected=new java.util.HashSet<>(deck.cards().cards().keySet());
        selected.addAll(deck.cards().enchanted().keySet());
        for(Identifier id:selected) if(deck.copies(id)>0 && !DeckRules.allowed(id,deck.deckClass()))
            return "match_ui.craftbound.deck_missing";
        if (deck.deckClass() == CardClass.NEUTRAL || deck.format()!=DeckFormat.COMMANDER && deck.size() < craftbound.game.MatchManager.PRACTICE_MIN_DECK) {
            return "match_ui.craftbound.deck_too_small";
        }
        // Old Deck Cases hold their own cards; they turn into lists when played.
        if (deck.format()==DeckFormat.COMMANDER) {
            if (!craftbound.game.CommanderMatch.eligible(deck.commander().orElse(null)) || deck.copies(deck.commander().get())!=1)
                return "match_ui.craftbound.commander_missing";
            if (deck.size()!=deck.format().size()) return "match_ui.craftbound.commander_size";
            if (selected.stream().anyMatch(id->deck.copies(id)>1)) return "match_ui.craftbound.commander_singleton";
        }
        if (!deck.list()) {
            return null;
        }
        for (Identifier id : selected) {
            for (boolean enchanted : new boolean[]{false, true}) {
                if (deck.cards().count(id, enchanted) > ownedCopies.apply(id, enchanted)) {
                    return "match_ui.craftbound.deck_missing";
                }
            }
        }
        return null;
    }

    // Deck Cases from before decks became lists hold real cards taken out of a binder. The first time one is
    // used, those cards go back into a binder you're carrying (or into your inventory) and the deck stays as a list.
    public static void migrate(ServerPlayer player, ItemStack deckCase) {
        DeckContents deck = deckCase.getOrDefault(ModComponents.DECK_CONTENTS, DeckContents.EMPTY);
        if (deck.list()) {
            return;
        }
        ItemStack binder = firstBinder(player.getInventory());
        int disenchanted = 0;
        int xp = 0;
        for (Identifier id : Cards.collectible()) {
            for (boolean enchanted : new boolean[]{false, true}) {
                int count = deck.cards().count(id, enchanted);
                if (count <= 0) {
                    continue;
                }
                if (binder.isEmpty()) {
                    ItemStack cards = CardItem.of(id, enchanted);
                    cards.setCount(count);
                    player.getInventory().placeItemBackInInventory(cards, Prediction.SERVER_ONLY);
                } else {
                    Disenchanting.Result result = Disenchanting.file(
                            binder.getOrDefault(ModComponents.BINDER_CONTENTS, BinderContents.EMPTY), id, enchanted, count);
                    binder.set(ModComponents.BINDER_CONTENTS, result.contents());
                    disenchanted += result.disenchanted();
                    xp += result.xp();
                }
            }
        }
        deckCase.set(ModComponents.DECK_CONTENTS, deck.asList());
        if (!binder.isEmpty()) {
            BinderItem.afterFiling(player, binder, disenchanted, xp);
        }
    }
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
        if (deck.size() >= deck.format().size()) {
            return "deck_ui.craftbound.full";
        }
        if (deck.copies(id) >= (deck.format()==DeckFormat.COMMANDER ? 1 : DeckRules.maxCopies(card.rarity()))) {
            return "deck_ui.craftbound.max_copies";
        }
        return null;
    }

    // Adds a copy to the deck list, as long as the deck rules allow it and you own a copy not already listed.
    public static void add(ServerPlayer player, ItemStack deckCase, Identifier id, boolean enchanted) {
        DeckContents deck = deckCase.getOrDefault(ModComponents.DECK_CONTENTS, DeckContents.EMPTY);
        if (whyNot(deck, id) != null || owned(player.getInventory(), id, enchanted) <= deck.cards().count(id, enchanted)) {
            return;
        }
        DeckContents updated = deck.add(id, enchanted, 1);
        deckCase.set(ModComponents.DECK_CONTENTS, updated);
        if (updated.size() == updated.format().size()) {
            ModAdvancements.deckCompleted(player);
        }
    }

    // Takes a copy off the deck list. The card itself never left your binder.
    public static void remove(ItemStack deckCase, Identifier id, boolean enchanted) {
        DeckContents deck = deckCase.getOrDefault(ModComponents.DECK_CONTENTS, DeckContents.EMPTY);
        if (!deck.leads(id) && deck.cards().count(id, enchanted) > 0) {
            deckCase.set(ModComponents.DECK_CONTENTS, deck.add(id, enchanted, -1));
        }
    }

    // Switching classes keeps compatible entries. Cards remain in binders; only the deck list changes.
    public static void setClass(ItemStack deckCase, CardClass newClass) {
        DeckContents deck = deckCase.getOrDefault(ModComponents.DECK_CONTENTS, DeckContents.EMPTY);
        if (newClass == CardClass.NEUTRAL) return;
        deckCase.set(ModComponents.DECK_CONTENTS, forClass(deck, newClass));
    }

    public static DeckContents forClass(DeckContents deck, CardClass newClass) {
        if (deck.format()==DeckFormat.COMMANDER && craftbound.game.CommanderMatch.eligible(deck.commander().orElse(null))
                && !DeckRules.allowed(deck.commander().get(),newClass)) return deck;
        BinderContents kept = BinderContents.EMPTY;
        for (Identifier id : Cards.collectible()) if (DeckRules.allowed(id, newClass)) {
            for (boolean enchanted : new boolean[]{false, true}) {
                int count = deck.cards().count(id, enchanted);
                if (count > 0) kept = kept.add(id, enchanted, count);
            }
        }
        return new DeckContents(newClass, kept, true,deck.format(),deck.commander());
    }

    // Unlocked designs can be used on any deck without moving or consuming more sleeve items.
    public static void setSleeve(ServerPlayer player, ItemStack deckCase, Identifier sleeve) {
        SleeveCollection.unlockEquipped(player);
        if (!Sleeves.ids().contains(sleeve) || !SleeveCollection.owns(player, sleeve)) return;
        Identifier current = deckCase.get(ModComponents.SLEEVE_ID);
        Identifier currentOrClassic = current == null ? Sleeves.CLASSIC : current;
        if (sleeve.equals(currentOrClassic)) {
            return;
        }
        if (sleeve.equals(Sleeves.CLASSIC)) {
            deckCase.remove(ModComponents.SLEEVE_ID);
        } else {
            deckCase.set(ModComponents.SLEEVE_ID, sleeve);
        }
    }

    public static boolean canSwitchTo(DeckContents deck, CardClass newClass) {
        for (Identifier id : Cards.collectible()) {
            if (deck.copies(id) > 0 && !DeckRules.allowed(id, newClass)) {
                return false;
            }
        }
        return true;
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
