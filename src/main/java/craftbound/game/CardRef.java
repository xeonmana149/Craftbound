package craftbound.game;

import net.minecraft.resources.Identifier;

/** One copy in a match. Old snapshots and ordinary deck cards have no Trials training. */
public record CardRef(Identifier id, boolean enchanted, TrialTraining training) {
    public CardRef {
        if (training == null) training = TrialTraining.NONE;
    }

    public CardRef(Identifier id, boolean enchanted) { this(id, enchanted, TrialTraining.NONE); }
}
