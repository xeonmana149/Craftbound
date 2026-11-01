package craftbound.binder;

import craftbound.card.CardDefinition;
import craftbound.card.CardRarity;
import craftbound.card.Cards;
import craftbound.card.DeckRules;
import net.minecraft.resources.Identifier;

// Breaking down extra copies in a binder for experience, like Hearthstone's "disenchant extra cards".
// Extras are copies beyond what a deck can use (2 of a card, 1 of a Legendary). Normal copies go first,
// so your Enchanted copies are the ones you keep.
public class Disenchanting {
    // Experience points per copy. Enchanted copies are worth four times as much.
    public static int xpFor(CardRarity rarity, boolean enchanted) {
        int xp = switch (rarity) {
            case COMMON -> 2;
            case RARE -> 5;
            case EPIC -> 20;
            case LEGENDARY -> 80;
        };
        return enchanted ? xp * 4 : xp;
    }

    public record Result(BinderContents contents, int cards, int xp) {
    }

    public static Result disenchantExtras(BinderContents contents) {
        BinderContents result = contents;
        int cards = 0;
        int xp = 0;
        for (Identifier id : Cards.collectible()) {
            CardDefinition card = Cards.get(id);
            int normal = contents.count(id, false);
            int enchanted = contents.count(id, true);
            int extra = normal + enchanted - DeckRules.maxCopies(card.rarity());
            if (extra <= 0) {
                continue;
            }
            int normalExtra = Math.min(normal, extra);
            int enchantedExtra = extra - normalExtra;
            result = result.add(id, false, -normalExtra).add(id, true, -enchantedExtra);
            cards += extra;
            xp += normalExtra * xpFor(card.rarity(), false) + enchantedExtra * xpFor(card.rarity(), true);
        }
        return new Result(result, cards, xp);
    }
}
