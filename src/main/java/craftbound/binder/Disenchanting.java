package craftbound.binder;

import craftbound.card.CardDefinition;
import craftbound.card.CardRarity;
import craftbound.card.Cards;
import craftbound.card.DeckRules;
import net.minecraft.resources.Identifier;

// A binder holds as many copies of a card as a deck can use: 2 normal and 2 Enchanted (1 of each for a
// Legendary). Copies beyond that are disenchanted into experience as they're filed, like Hearthstone dust.
public class Disenchanting {
    // How many normal copies, and separately how many Enchanted copies, of a card a binder keeps.
    public static int binderLimit(CardRarity rarity) {
        return DeckRules.maxCopies(rarity);
    }

    // Experience points for disenchanting one copy. Commons give barely anything, Legendaries quite a bit,
    // and Enchanted copies give even more.
    public static int xpFor(CardRarity rarity, boolean enchanted) {
        if (enchanted) {
            return switch (rarity) {
                case COMMON -> 5;
                case RARE -> 15;
                case EPIC -> 50;
                case LEGENDARY -> 200;
            };
        }
        return switch (rarity) {
            case COMMON -> 1;
            case RARE -> 4;
            case EPIC -> 15;
            case LEGENDARY -> 50;
        };
    }

    // What happened when cards were filed or cleaned up: the binder's new contents, how many copies were
    // disenchanted, and how much experience they gave.
    public record Result(BinderContents contents, int disenchanted, int xp) {
    }

    // Files `amount` copies of a card: keeps what fits under the limit and disenchants the rest.
    public static Result file(BinderContents contents, Identifier id, boolean enchanted, int amount) {
        CardDefinition card = Cards.get(id);
        int room = Math.max(0, binderLimit(card.rarity()) - contents.count(id, enchanted));
        int kept = Math.min(room, amount);
        int extra = amount - kept;
        return new Result(contents.add(id, enchanted, kept), extra, extra * xpFor(card.rarity(), enchanted));
    }

    // Disenchants anything already over the limit, e.g. in binders filled before limits existed.
    public static Result disenchantExtras(BinderContents contents) {
        BinderContents result = contents;
        int disenchanted = 0;
        int xp = 0;
        for (Identifier id : Cards.collectible()) {
            CardDefinition card = Cards.get(id);
            for (boolean enchanted : new boolean[]{false, true}) {
                int extra = contents.count(id, enchanted) - binderLimit(card.rarity());
                if (extra > 0) {
                    result = result.add(id, enchanted, -extra);
                    disenchanted += extra;
                    xp += extra * xpFor(card.rarity(), enchanted);
                }
            }
        }
        return new Result(result, disenchanted, xp);
    }
}
