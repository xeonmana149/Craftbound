package craftbound.game;

import craftbound.card.CardDefinition;
import craftbound.card.Cards;
import craftbound.card.Keyword;

// A mob on the board during a match.
public class Minion {
    // Unique within the match, so the client can say "this mob attacks that mob".
    public final int id;
    public final CardRef card;
    public int attack;
    public int health;
    // Goes up when the mob gets +health (e.g. Bone Meal).
    public int maxHealth;
    // Can it attack this turn? Mobs can't attack the turn they arrive, unless they have Sprint.
    public boolean ready;
    // Frozen by a Shulker: it skips its owner's next turn of attacking.
    public boolean frozen;
    // Withered by a Wither Skeleton: takes 1 damage at the start of each of its owner's turns.
    public boolean withered;
    // Has a Totem of Undying: summoned again when it dies.
    public boolean totem;
    // Fed an Enchanted Golden Apple: takes no damage until its owner's next turn.
    public boolean invulnerable;

    public Minion(int id, CardRef card) {
        CardDefinition definition = Cards.get(card.id());
        this.id = id;
        this.card = card;
        this.attack = definition.attack();
        this.health = definition.health();
        this.maxHealth = definition.health();
        this.ready = has(Keyword.SPRINT);
    }

    public CardDefinition definition() {
        return Cards.get(this.card.id());
    }

    public boolean has(Keyword keyword) {
        return definition().keywords().contains(keyword);
    }

    public boolean isDead() {
        return this.health <= 0;
    }

    public boolean canAttack() {
        return this.ready && !this.frozen && this.attack > 0 && !has(Keyword.BLOCK);
    }
}
