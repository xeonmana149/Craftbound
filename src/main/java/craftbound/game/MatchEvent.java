package craftbound.game;

// Something that just happened in a match, in order, so the match screen can animate it step by step
// (a lunge, then the damage, then the death) instead of jumping straight to the result.
// - side: whose hero, mob or card it is. mid: the mob's id, or Match.HERO for the hero.
// - card: the card involved, if any. amount and extra: numbers that depend on the type (see below).
// - target: what an attack, shot or played card was aimed at, if anything.
public record MatchEvent(Type type, int side, int mid, CardRef card, int amount, int extra, Target target) {
    public enum Type {
        // The coin toss: side goes first.
        COIN,
        // side finished their mulligan. amount: which hand positions were swapped (one bit each).
        MULLIGAN,
        // Both mulligans are done and the game proper begins.
        BEGIN,
        // side's turn starts. amount: their XP, extra: their max XP.
        TURN,
        // side draws a card (hidden from the other player).
        DRAW,
        // side gets a card from an effect, like a Drop or a mob sent back to hand.
        GIVE,
        // side's hand was full, so the card was lost.
        BURN,
        // side has no cards left and takes amount Hunger damage.
        HUNGER,
        // side plays card from hand position amount, aimed at target (if any).
        PLAY,
        // side uses their class power.
        POWER,
        // A mob arrives on side's board. amount: attack, extra: health.
        SUMMON,
        // side's hero equips card. amount: attack, extra: uses.
        EQUIP,
        // side's tool breaks.
        BREAK,
        // A mob or hero (mid) attacks target.
        ATTACK,
        // A mob (mid) shoots target without moving, like the Dispenser.
        SHOOT,
        // A mob or hero (mid) takes amount damage. 0 means it was protected (Enchanted Golden Apple).
        DAMAGE,
        // side's hero heals amount.
        HEAL,
        // A mob gets +amount attack and +extra health.
        BUFF,
        // A mob gets a status: amount is one of the STATUS_ numbers below.
        STATUS,
        // side's XP is now amount (from a Bottle o' Enchanting or a Dragon Egg).
        XP,
        // A mob dies.
        DEATH,
        // A mob goes back to its owner's hand (a GIVE follows).
        BOUNCE,
        // A mob leaves the board without dying, like crops being harvested.
        REMOVE
    }

    public static final int STATUS_FROZEN = 1;
    public static final int STATUS_WITHERED = 2;
    public static final int STATUS_TOTEM = 3;
    public static final int STATUS_INVULNERABLE = 4;
}
