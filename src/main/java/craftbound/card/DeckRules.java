package craftbound.card;

import net.minecraft.resources.Identifier;

// The rules for building a deck. The Deck Case will check these.
// - A deck is exactly 30 cards.
// - Copies of one card: up to 3 for Common and Rare, 2 for Epic, 1 for Legendary.
//   Enchanted and normal copies count together: an Enchanted Zombie is still a Zombie.
// - Tokens can't go in a deck; they only come from other cards.
// - A deck is one class plus any Neutral cards.
public class DeckRules {
    public static final int DECK_SIZE = 30;

    public static int maxCopies(CardRarity rarity) {
        return switch (rarity) {
            case COMMON, RARE -> 3;
            case EPIC -> 2;
            case LEGENDARY -> 1;
        };
    }

    // Whether a card can go in a deck of the given class at all (ignoring how many copies are already in).
    public static boolean allowed(Identifier id, CardClass deckClass) {
        CardDefinition card = Cards.get(id);
        if (card == null || card.token()) {
            return false;
        }
        return card.cardClass() == CardClass.NEUTRAL || card.cardClass() == deckClass;
    }
}
