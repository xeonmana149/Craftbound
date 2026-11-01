package craftbound.game;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

// One side of a match: a real player or the Card Master AI.
public class MatchPlayer {
    public final String name;
    // Shown on the player's nameplate: their deck's class, or null for the Card Master.
    public String deckClass;
    // The real player's id, or null for the AI.
    public final UUID playerId;
    public int health = Match.START_HEALTH;
    public int xp = 0;
    public int maxXp = 0;
    // How much the next Hunger hit does (drawing from an empty deck): 1, then 2, then 3...
    public int hunger = 0;
    public final List<CardRef> deck;
    public final List<CardRef> hand = new ArrayList<>();
    public final List<Minion> board = new ArrayList<>();

    public MatchPlayer(String name, UUID playerId, List<CardRef> deck) {
        this.name = name;
        this.playerId = playerId;
        this.deck = new ArrayList<>(deck);
    }

    public boolean isAi() {
        return this.playerId == null;
    }

    public Minion minion(int id) {
        for (Minion minion : this.board) {
            if (minion.id == id) {
                return minion;
            }
        }
        return null;
    }
}
