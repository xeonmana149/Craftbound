package craftbound.deck;

import craftbound.card.Cards;

/** Printed XP costs in the complete deck, counting normal and foil copies equally. */
public final class DeckXpCurve {
    public record Curve(int[] counts, int total, double average) {}
    public static Curve of(DeckContents deck) {
        int[] counts = new int[8];
        int total = 0, sum = 0;
        for (var id : Cards.collectible()) {
            int copies = deck.copies(id);
            if (copies <= 0) continue;
            int cost = Cards.get(id).cost();
            counts[Math.min(7, cost)] += copies;
            total += copies;
            sum += cost * copies;
        }
        return new Curve(counts, total, total == 0 ? 0 : sum / (double) total);
    }
    private DeckXpCurve() {}
}
