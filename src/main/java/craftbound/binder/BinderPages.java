package craftbound.binder;

import craftbound.card.CardClass;
import craftbound.card.CardRarity;
import craftbound.card.Cards;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.resources.Identifier;

/** A browsing order only. Never mutates the catalogue or its permanent collector numbers. */
public final class BinderPages {
    public static final int CARDS_PER_SPREAD = 18;
    public record Spread(CardRarity rarity, int sectionPage, int sectionPages, List<Identifier> cards) {
        public Spread { cards = List.copyOf(cards); }
    }

    public static List<Spread> forClass(CardClass cardClass) {
        List<Spread> result = new ArrayList<>();
        Comparator<Identifier> order = Comparator.<Identifier>comparingInt(id -> Cards.get(id).cost())
                .thenComparing(id -> Cards.name(id).getString(), String.CASE_INSENSITIVE_ORDER)
                .thenComparing(Identifier::toString);
        for (CardRarity rarity : CardRarity.values()) {
            List<Identifier> cards = Cards.collectible().stream()
                    .filter(id -> Cards.get(id).cardClass() == cardClass && Cards.get(id).rarity() == rarity)
                    .sorted(order).toList();
            // Empty sections remain reachable, so every rarity button has a clear destination.
            int count = Math.max(1, (cards.size() + CARDS_PER_SPREAD - 1) / CARDS_PER_SPREAD);
            for (int page = 0; page < count; page++) {
                int from = page * CARDS_PER_SPREAD;
                result.add(new Spread(rarity, page + 1, count, cards.subList(from, Math.min(cards.size(), from + CARDS_PER_SPREAD))));
            }
        }
        return List.copyOf(result);
    }

    public static int firstSpread(List<Spread> spreads, CardRarity rarity) {
        for (int i = 0; i < spreads.size(); i++) if (spreads.get(i).rarity() == rarity) return i;
        throw new IllegalArgumentException("No section for " + rarity);
    }

    private BinderPages() {}
}
