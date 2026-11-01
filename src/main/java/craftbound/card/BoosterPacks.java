package craftbound.card;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;

// Rolling the five cards in a booster pack. Every card rolls on its own, so two Epics, two Legendaries or
// several Enchanted cards in one pack can happen, just rarely.
// - Luck (the Potion of Luck) makes Enchanted cards more likely.
// - A pack enchanted with Fortune rolls rarer cards.
// - Once in a long while a pack is a jackpot: every card is Legendary.
public class BoosterPacks {
    public static final int CARDS_PER_PACK = 5;
    // Each card's chance out of 100 to be Enchanted, plus this much per point of Luck.
    public static final int ENCHANTED_CHANCE = 5;
    public static final int ENCHANTED_CHANCE_PER_LUCK = 10;
    // A jackpot pack happens 1 time in this many (fewer with Fortune).
    public static final int JACKPOT_ODDS = 10_000;

    public record Pull(Identifier card, boolean enchanted) {
    }

    public record Opening(List<Pull> pulls, boolean jackpot) {
    }

    public static Opening open(RandomSource random, double luck, int fortune) {
        int enchantedChance = (int) Math.max(0, Math.min(100, ENCHANTED_CHANCE + ENCHANTED_CHANCE_PER_LUCK * luck));
        boolean jackpot = random.nextInt(Math.max(1, JACKPOT_ODDS / (1 + fortune))) == 0;
        List<Pull> pulls = new ArrayList<>();
        for (int i = 0; i < CARDS_PER_PACK; i++) {
            CardRarity rarity;
            if (jackpot) {
                rarity = CardRarity.LEGENDARY;
            } else if (i < CARDS_PER_PACK - 1) {
                rarity = rollRarity(random, fortune);
            } else {
                // The last card is always Rare or better.
                rarity = rollGuaranteedRarity(random, fortune);
            }
            pulls.add(new Pull(pick(rarity, random), random.nextInt(100) < enchantedChance));
        }
        return new Opening(pulls, jackpot);
    }

    // Out of 100, with no Fortune: 72 Common, 22 Rare, 5 Epic, 1 Legendary.
    // Each level of Fortune adds 1 to Legendary, 2 to Epic and 4 to Rare, taken from Common.
    private static CardRarity rollRarity(RandomSource random, int fortune) {
        int roll = random.nextInt(100);
        int legendary = 1 + fortune;
        int epic = legendary + 5 + 2 * fortune;
        int rare = epic + 22 + 4 * fortune;
        if (roll < legendary) return CardRarity.LEGENDARY;
        if (roll < epic) return CardRarity.EPIC;
        if (roll < rare) return CardRarity.RARE;
        return CardRarity.COMMON;
    }

    // Out of 100, with no Fortune: 75 Rare, 20 Epic, 5 Legendary.
    // Each level of Fortune adds 3 to Legendary and 5 to Epic, taken from Rare.
    private static CardRarity rollGuaranteedRarity(RandomSource random, int fortune) {
        int roll = random.nextInt(100);
        int legendary = 5 + 3 * fortune;
        int epic = legendary + 20 + 5 * fortune;
        if (roll < legendary) return CardRarity.LEGENDARY;
        if (roll < epic) return CardRarity.EPIC;
        return CardRarity.RARE;
    }

    // Picks a random card of that rarity. If none exist yet, it steps down a rarity.
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
