package craftbound.game;

import net.minecraft.resources.Identifier;

// A card in a match (in a deck, a hand, or on the board): which card, and whether it's an Enchanted copy.
public record CardRef(Identifier id, boolean enchanted) {
}
