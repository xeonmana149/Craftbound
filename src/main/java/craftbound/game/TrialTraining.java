package craftbound.game;

import craftbound.card.CardDefinition;
import craftbound.card.Cards;
import craftbound.card.Keyword;
import net.minecraft.resources.Identifier;

/** A single permanent change to one borrowed copy for this expedition. */
public enum TrialTraining {
    NONE("Untrained", null), TEMPER("Temper", null), SHIELD("Shield", Keyword.SHIELD), SPRINT("Sprint", Keyword.SPRINT);

    private final String label;
    private final Keyword keyword;

    TrialTraining(String label, Keyword keyword) {
        this.label = label;
        this.keyword = keyword;
    }

    public String label() { return label; }
    public Keyword keyword() { return keyword; }

    public static TrialTraining fromName(String name) {
        if (name != null) for (var training : values()) if (training.name().equals(name)) return training;
        return NONE;
    }

    public boolean supports(Identifier id) {
        CardDefinition card = id == null ? null : Cards.get(id);
        if (card == null || this == NONE) return false;
        if (this == TEMPER) return card.isMob() || card.isTool();
        return card.isMob() && !card.keywords().contains(Keyword.BLOCK) && !card.keywords().contains(keyword);
    }

    public int attack(CardDefinition card) {
        return card.attack() + (this == TEMPER && (card.isTool() || (card.isMob() && !card.keywords().contains(Keyword.BLOCK))) ? 1 : 0);
    }

    public int health(CardDefinition card) {
        if (this != TEMPER || !card.isMob()) return card.health();
        return card.health() + (card.keywords().contains(Keyword.BLOCK) ? 2 : 1);
    }

    public String description(Identifier id) {
        if (!supports(id)) return label;
        if (keyword != null) return "Gains " + label + ".";
        CardDefinition card = Cards.get(id);
        if (card.isTool()) return "+1 Attack";
        return card.keywords().contains(Keyword.BLOCK) ? "+2 Health" : "+1 Attack and +1 Health";
    }
}
