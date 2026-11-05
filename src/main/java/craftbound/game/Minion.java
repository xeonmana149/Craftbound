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
    public int attackDebuff;
    public int killerSide = -1, killerId = -1;
    public record TimedWeakness(int amount, int expiresTurn) {}
    public java.util.List<TimedWeakness> timedWeaknesses = new java.util.ArrayList<>();
    public final java.util.Set<Keyword> grantedKeywords = java.util.EnumSet.noneOf(Keyword.class);
    public int temporaryAttack;
    public int lastBattleHardenedTurn = -1;
    // Freeze may have cleared at turn start while the attack is still skipped.
    public boolean skippedAttackFromFreeze;
    public boolean attackedThisTurn;
    public int health;
    // Goes up when the mob gets +health (e.g. Bone Meal).
    public int maxHealth;
    // Applied continuous bonuses, persisted so loading a paused game cannot apply them twice.
    public int auraAttack, auraHealth;
    // Can it attack this turn? Mobs can't attack the turn they arrive, unless they have Sprint.
    public boolean ready;
    // Sprint allows board attacks immediately, but never a hero attack before our next turn.
    public boolean summonedThisTurn = true;
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
        this.attack = card.training().attack(definition);
        this.health = card.training().health(definition);
        this.maxHealth = this.health;
        this.ready = has(Keyword.SPRINT);
    }

    public CardDefinition definition() {
        return Cards.get(this.card.id());
    }

    public boolean has(Keyword keyword) {
        if (keyword == Keyword.SHIELD && definition().keywords().contains(Keyword.BLOCK)) return false;
        return card.training().keyword() == keyword || grantedKeywords.contains(keyword) || definition().keywords().contains(keyword);
    }

    boolean hasAppliedBenefit() {
        return invulnerable || totem || !grantedKeywords.isEmpty() || attack > card.training().attack(definition())
                || maxHealth > card.training().health(definition()) + auraHealth;
    }

    public boolean isDead() {
        return this.health <= 0;
    }

    public boolean canAttack() {
        // Callers check Match.attackOf, which also includes auras and attack penalties.
        return this.ready && !this.frozen && !isDead() && !has(Keyword.BLOCK);
    }
}
