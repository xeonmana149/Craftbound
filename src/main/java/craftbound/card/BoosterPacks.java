package craftbound.card;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;

public class BoosterPacks {
    public static final int CARDS_PER_PACK = 5;
    // Each card in a pack has this chance out of 100 to be Enchanted.
    public static final int ENCHANTED_CHANCE = 5;

    public record Pull(Identifier card, boolean enchanted) {
    }

    public static List<Pull> open(RandomSource random) {
        List<Pull> pulls = new ArrayList<>();
        for (int i = 0; i < CARDS_PER_PACK - 1; i++) {
            pulls.add(new Pull(pick(rollRarity(random), random), rollEnchanted(random)));
        }
        // The last card is always Rare or better.
        pulls.add(new Pull(pick(rollGuaranteedRarity(random), random), rollEnchanted(random)));
        return pulls;
    }

    private static boolean rollEnchanted(RandomSource random) {
        return random.nextInt(100) < ENCHANTED_CHANCE;
    }

    // Out of 100: 72 Common, 22 Rare, 5 Epic, 1 Legendary.
    private static CardRarity rollRarity(RandomSource random) {
        int roll = random.nextInt(100);
        if (roll < 1) return CardRarity.LEGENDARY;
        if (roll < 6) return CardRarity.EPIC;
        if (roll < 28) return CardRarity.RARE;
        return CardRarity.COMMON;
    }

    // Out of 100: 75 Rare, 20 Epic, 5 Legendary.
    private static CardRarity rollGuaranteedRarity(RandomSource random) {
        int roll = random.nextInt(100);
        if (roll < 5) return CardRarity.LEGENDARY;
        if (roll < 25) return CardRarity.EPIC;
        return CardRarity.RARE;
    }

    // Picks a random card of that rarity. If none exist yet (e.g. no Legendaries), it steps down a rarity.
    private static Identifier pick(CardRarity rarity, RandomSource random) {
        for (int i = rarity.ordinal(); i >= 0; i--) {
            List<Identifier> options = Cards.ofRarity(CardRarity.values()[i]);
            if (!options.isEmpty()) {
                return options.get(random.nextInt(options.size()));
            }
        }
        throw new IllegalStateException("No cards registered");
    }
}
