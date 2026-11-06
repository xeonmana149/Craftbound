package craftbound.game;

import net.minecraft.resources.Identifier;

/** One copy in a match. Old snapshots and ordinary deck cards have no Trials training or Brewing Stand upgrades. */
public record CardRef(Identifier id, boolean enchanted, TrialTraining training, craftbound.card.Brew brew) {
    public CardRef {
        if (training == null) training = TrialTraining.NONE;
        if (brew == null) brew = craftbound.card.Brew.NONE;
    }

    public CardRef(Identifier id, boolean enchanted, TrialTraining training) { this(id, enchanted, training, craftbound.card.Brew.NONE); }
    public CardRef(Identifier id, boolean enchanted) { this(id, enchanted, TrialTraining.NONE); }
    public CardRef brewed(craftbound.card.Brew next) { return new CardRef(id, enchanted, training, next); }
}
