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

    // Experience for disenchanting, counted in half-points so a single Common can be worth half a point.
    // Per copy that's: normal Common 0.5, Rare 2, Epic 7.5, Legendary 25; Enchanted Common 2.5, Rare 7.5,
    // Epic 25, Legendary 100. The total is rounded down when it's handed out.
    private static int halfPoints(CardRarity rarity, boolean enchanted) {
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
    // The first copy of a card (or of its Enchanted version) is marked new.
    public static Result file(BinderContents contents, Identifier id, boolean enchanted, int amount) {
        CardDefinition card = Cards.get(id);
        int room = Math.max(0, binderLimit(card.rarity()) - contents.count(id, enchanted));
        int kept = Math.min(room, amount);
        int extra = amount - kept;
        BinderContents filed = contents.add(id, enchanted, kept);
        if (kept > 0 && contents.count(id, enchanted) == 0) {
            filed = filed.markNew(id, enchanted);
        }
        return new Result(filed, extra, extra * halfPoints(card.rarity(), enchanted) / 2);
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
                    xp += extra * halfPoints(card.rarity(), enchanted);
                }
            }
        }
        return new Result(result, disenchanted, xp / 2);
    }
}
