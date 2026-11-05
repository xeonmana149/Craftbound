package craftbound.deck;

import craftbound.card.*;
import java.util.Set;

/** OR within a keyword/type group, AND between groups and the cost/rarity filters. */
public final class DeckCardFilter {
    /** Display categories keep the existing catalogue's Block marker compatible. */
    public enum CardType {
        MOB("Mobs"), BLOCK("Blocks"), ITEM("Actions"), TOOL("Tools"), ENCHANTMENT("Enchantments");
        public final String label;
        CardType(String label) { this.label = label; }
    }
    public static CardType cardType(CardDefinition card) {
        return switch (card.kind()) {
            case MOB -> card.keywords().contains(Keyword.BLOCK) ? CardType.BLOCK : CardType.MOB;
            case ITEM -> CardType.ITEM;
            case TOOL -> CardType.TOOL;
            case ENCHANTMENT -> CardType.ENCHANTMENT;
        };
    }
    public static boolean matches(CardDefinition card, int cost, CardRarity rarity,
                                  Set<Keyword> keywords, Set<MobType> types) {
        return matches(card, cost, rarity, keywords, types, Set.of());
    }
    public static boolean matches(CardDefinition card, int cost, CardRarity rarity,
                                  Set<Keyword> keywords, Set<MobType> types, Set<CardType> cardTypes) {
        return (cost < 0 || (cost == 7 ? card.cost() >= 7 : card.cost() == cost))
                && (rarity == null || card.rarity() == rarity)
                && (keywords.isEmpty() || card.keywords().stream().anyMatch(keywords::contains))
                && (types.isEmpty() || card.mobType() != null && types.contains(card.mobType()))
                && (cardTypes.isEmpty() || cardTypes.contains(cardType(card)));
    }
    private DeckCardFilter() {}
}
