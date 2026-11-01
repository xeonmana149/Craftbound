package craftbound.game;

// What a card or attack is aimed at: a player's hero, or a mob (by its id). `side` is 0 or 1.
public record Target(boolean hero, int side, int minionId) {
    public static final Target NONE = new Target(false, -1, -1);

    public static Target hero(int side) {
        return new Target(true, side, -1);
    }

    public static Target mob(int side, int minionId) {
        return new Target(false, side, minionId);
    }

    public boolean isNone() {
        return this.side < 0;
    }

    // What a card needs to be aimed at when you play it.
    public enum Kind { NONE, ANY_MOB, FRIENDLY_MOB, ENEMY_MOB }
}
