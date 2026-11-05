package craftbound.game;

// Something that just happened in a match, in order, so the match screen can animate it step by step
// (a lunge, then the damage, then the death) instead of jumping straight to the result.
// - side: whose hero, mob or card it is. mid: the mob's id, or Match.HERO for the hero.
// - card: the card involved, if any. amount and extra: numbers that depend on the type (see below).
// - target: what an attack, shot or played card was aimed at, if anything.
public record MatchEvent(Type type, int side, int mid, CardRef card, int amount, int extra, Target target, Target source) {
    public MatchEvent(Type type, int side, int mid, CardRef card, int amount, int extra, Target target) {
        this(type, side, mid, card, amount, extra, target, Target.NONE);
    }
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
        // A mob or hero (mid) shoots target. extra: optional wind-up milliseconds.
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
        REMOVE,
        // An enchantment is applied to the equipped tool. card: enchantment; amount/extra: resulting stats.
        ENCHANT,
        // Same board slot/id, entirely new card and state. No Spawn/Drop.
        TRANSFORM,
        HERO_ATTACK,
        // Continuous stat changes, absolute values; no damage/healing trigger or animation delay.
        AURA_ATTACK, // amount: effective attack
        AURA_HEALTH, // amount: current health, extra: maximum health
        TOOL_UPGRADE, // amount: resulting tool Attack, extra: resulting Durability; no enchantment attached
        TNT_DETONATE,
        EFFECT, // A separate authored effect starts; results follow in server order.
        // Hand effects. card is only sent to the players named below; others get an empty card.
        // side looks at target.side()'s hand: one event per card, amount: hand position, extra: hand size.
        // An empty hand sends one event with amount -1. Card visible to side and the revealed player.
        HAND_REVEAL,
        // side (the hand's owner) discards card from position amount; extra: the effect's controller. Public.
        HAND_DISCARD,
        // side takes card from position amount of target.side()'s hand. extra: 1 kept, 0 lost to a full hand.
        // Card visible to side and the robbed player.
        HAND_STEAL,
        // side (the hand's owner) shuffles card from position amount into their deck; extra: the controller.
        // Card visible to side and the controller.
        HAND_SHUFFLE
    }

    public static final int STATUS_FROZEN = 1;
    public static final int STATUS_WITHERED = 2;
    public static final int STATUS_TOTEM = 3;
    public static final int STATUS_INVULNERABLE = 4;
    public static final int STATUS_CLEANSED = 5;
    public static final int STATUS_WEAKENED = 6;
    public static final int STATUS_SHIELD = 7;
    public static final int STATUS_SPRINT = 8;
    public static final int STATUS_RANGED = 9;
    public static final int STATUS_IGNORE_SHIELD = 10;
}
