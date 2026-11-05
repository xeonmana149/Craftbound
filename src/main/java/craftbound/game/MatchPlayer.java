package craftbound.game;

import craftbound.card.CardClass;
import craftbound.card.Keyword;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.resources.Identifier;

// One side of a match: a real player or the Card Master AI.
public class MatchPlayer {
    public final String name;
    // The deck's class, which decides the hero's power.
    public final CardClass heroClass;
    // The real player's id, or null for the AI.
    public final UUID playerId;
    // The card backs this player's deck uses.
    public final Identifier sleeve;
    // The name the player gave their deck, or "" (the screen then calls it after its class).
    public String deckName = "";
    public int health = Match.START_HEALTH;
    public int maxHealth = Match.START_HEALTH;
    // A trial's printed starting Health survives Cleanse. Zero uses the format's ordinary hero Health.
    int startingHealth;
    // The active encounter owns its rule, even across a Workshop reload or a paused battle.
    TrialsOpponents.Profile trialsFoe;
    int trialsFoeTurns, trialsFoeUsedTurn;
    List<String> trialsCharms;
    int trialsCharmTurn, spentTrialsCharms;
    public int xp = 0;
    public int maxXp = 0;
    // How much the next Hunger hit does (drawing from an empty deck): 1, then 2, then 3...
    public int hunger = 0;
    public final List<CardRef> deck;
    public final List<CardRef> hand = new ArrayList<>();
    public final List<Minion> board = new ArrayList<>();
    // The tool the hero holds, or null.
    public Tool tool;
    // Each turn the hero can attack once (with a tool) and use their power once.
    public boolean heroAttacked;
    public int temporaryHeroAttack;
    public Identifier badOmenSummon;
    public Integer badOmenSource; // Missing in old duel snapshots.
    public boolean frozen;
    public boolean skippedAttackFromFreeze;
    public boolean powerUsed;
    // Farmer: after a Sow, the next use of the power is Reap.
    public boolean reap;
    // How many of the match's events this player's screen has been sent.
    int eventsSent;

    public MatchPlayer(String name, UUID playerId, CardClass heroClass, Identifier sleeve, List<CardRef> deck) {
        this.name = name;
        this.playerId = playerId;
        this.heroClass = heroClass;
        this.sleeve = sleeve;
        this.deck = new ArrayList<>(deck);
    }

    String rowFullReason(boolean block) {
        int limit = Match.rowLimit(block);
        long count = board.stream().filter(m -> m.has(Keyword.BLOCK) == block).count();
        if (count < limit) return null;
        return (block ? "Block" : "Mob") + " row full (" + count + "/" + limit + ")";
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
