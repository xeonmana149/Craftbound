package craftbound.game;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import craftbound.card.Cards;
import craftbound.card.Keyword;

// What one player is allowed to see of a match, packed as JSON for the match screen.
// You see your own hand; for the other player you only see how many cards they hold.
public class MatchView {
    public static String build(Match match, int viewer) {
        JsonObject root = new JsonObject();
        root.addProperty("you", viewer);
        root.addProperty("turn", match.turn);
        root.addProperty("myTurn", !match.over && match.current == viewer);
        root.addProperty("over", match.over);
        root.addProperty("winner", match.winner);
        // The battle log: plain lines (with the card they're about, for an icon) and turn headers.
        JsonArray log = new JsonArray();
        for (Match.LogEntry entry : match.log) {
            JsonObject line = new JsonObject();
            line.addProperty("text", entry.text());
            line.addProperty("card", entry.card() == null ? "" : entry.card().toString());
            line.addProperty("round", entry.round());
            line.addProperty("turnOf", entry.turnOf() == null ? "" : entry.turnOf());
            log.add(line);
        }
        root.add("log", log);

        JsonArray players = new JsonArray();
        for (int side = 0; side < 2; side++) {
            MatchPlayer player = match.player(side);
            JsonObject p = new JsonObject();
            p.addProperty("name", player.name);
            p.addProperty("cls", player.deckClass == null ? "" : player.deckClass);
            p.addProperty("ai", player.isAi());
            p.addProperty("health", player.health);
            p.addProperty("xp", player.xp);
            p.addProperty("maxXp", player.maxXp);
            p.addProperty("deck", player.deck.size());
            p.addProperty("handCount", player.hand.size());
            if (side == viewer) {
                JsonArray hand = new JsonArray();
                for (int i = 0; i < player.hand.size(); i++) {
                    CardRef card = player.hand.get(i);
                    JsonObject c = new JsonObject();
                    c.addProperty("id", card.id().toString());
                    c.addProperty("ench", card.enchanted());
                    c.addProperty("playable", match.whyCantPlay(side, i) == null);
                    c.addProperty("target", CardEffects.targetKind(card.id()).name());
                    hand.add(c);
                }
                p.add("hand", hand);
            }
            JsonArray board = new JsonArray();
            for (Minion minion : player.board) {
                JsonObject m = new JsonObject();
                m.addProperty("mid", minion.id);
                m.addProperty("id", minion.card.id().toString());
                m.addProperty("ench", minion.card.enchanted());
                m.addProperty("atk", minion.attack);
                m.addProperty("hp", minion.health);
                m.addProperty("maxHp", minion.maxHealth);
                m.addProperty("ready", !match.over && match.current == side && minion.canAttack());
                m.addProperty("shield", minion.has(Keyword.SHIELD));
                m.addProperty("frozen", minion.frozen);
                m.addProperty("withered", minion.withered);
                m.addProperty("totem", minion.totem);
                m.addProperty("invulnerable", minion.invulnerable);
                board.add(m);
            }
            p.add("board", board);
            players.add(p);
        }
        root.add("players", players);
        return root.toString();
    }

    private MatchView() {
    }

    static {
        // Make sure the card list is loaded before any view is built.
        Cards.collectible();
    }
}
