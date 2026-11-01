package craftbound.game;

// The tool a hero is holding, like a sword. The hero can attack with it once per turn; each attack uses it up a bit.
public class Tool {
    public final CardRef card;
    public int attack;
    // Attacks left before it breaks.
    public int uses;
    // A Ranged tool (the Bow) takes no damage back.
    public final boolean ranged;

    public Tool(CardRef card, int attack, int uses, boolean ranged) {
        this.card = card;
        this.attack = attack;
        this.uses = uses;
        this.ranged = ranged;
    }
}
