package craftbound.game;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import craftbound.card.Cards;
import craftbound.card.Keyword;

// What one player is allowed to see of a match, packed as JSON for the match screen.
// You see your own hand; for the other player you only see how many cards they hold.
// `events` are the things that happened since this player's last update, so the screen can animate them.
public class MatchView {
    public static String build(Match match, int viewer, int firstEvent) {
        JsonObject root = new JsonObject();
        root.addProperty("you", viewer);
        root.addProperty("turn", match.turn);
        root.addProperty("current", match.current);
        root.addProperty("phase", match.phase.name());
        root.addProperty("myTurn", !match.over && match.phase == Match.Phase.PLAYING && match.current == viewer);
        root.addProperty("over", match.over);
        root.addProperty("winner", match.winner);
        root.addProperty("powerCost", Match.POWER_COST);
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

        JsonArray events = new JsonArray();
        for (int i = Math.max(0, firstEvent); i < match.events.size(); i++) {
            events.add(event(match.events.get(i), viewer));
        }
        root.add("events", events);

        JsonArray players = new JsonArray();
        for (int side = 0; side < 2; side++) {
            MatchPlayer player = match.player(side);
            JsonObject p = new JsonObject();
            p.addProperty("name", player.name);
            p.addProperty("cls", player.heroClass.name());
            p.addProperty("ai", player.isAi());
            p.addProperty("uuid", player.playerId == null ? "" : player.playerId.toString());
            p.addProperty("sleeve", player.sleeve == null ? "" : player.sleeve.toString());
            p.addProperty("health", player.health);
            p.addProperty("xp", player.xp);
            p.addProperty("maxXp", player.maxXp);
            p.addProperty("deck", player.deck.size());
            p.addProperty("handCount", player.hand.size());
            p.addProperty("mulliganDone", match.mulliganDone[side]);
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
                m.addProperty("atk", match.attackOf(minion));
                m.addProperty("hp", minion.health);
                m.addProperty("maxHp", minion.maxHealth);
                m.addProperty("ready", !match.over && match.phase == Match.Phase.PLAYING && match.current == side
                        && minion.canAttack() && match.attackOf(minion) > 0);
                m.addProperty("block", minion.has(Keyword.BLOCK));
                m.addProperty("shield", minion.has(Keyword.SHIELD));
                m.addProperty("frozen", minion.frozen);
                m.addProperty("withered", minion.withered);
                m.addProperty("totem", minion.totem);
                m.addProperty("invulnerable", minion.invulnerable);
                board.add(m);
            }
            p.add("board", board);
            if (player.tool != null) {
                JsonObject tool = new JsonObject();
                tool.addProperty("id", player.tool.card.id().toString());
                tool.addProperty("ench", player.tool.card.enchanted());
                tool.addProperty("atk", player.tool.attack);
                tool.addProperty("uses", player.tool.uses);
                tool.addProperty("ranged", player.tool.ranged);
                p.add("tool", tool);
            }
            p.addProperty("heroReady", match.heroCanAttack(side));
            JsonObject power = new JsonObject();
            power.addProperty("name", HeroPowers.of(player).name());
            power.addProperty("usable", match.whyCantUsePower(side) == null);
            power.addProperty("used", player.powerUsed);
            p.add("power", power);
            players.add(p);
        }
        root.add("players", players);
        return root.toString();
    }

    private static JsonObject event(MatchEvent event, int viewer) {
        JsonObject e = new JsonObject();
        e.addProperty("t", event.type().name());
        e.addProperty("s", event.side());
        e.addProperty("m", event.mid());
        // You only see the cards the other player draws once they play them.
        boolean hidden = event.type() == MatchEvent.Type.DRAW && event.side() != viewer;
        e.addProperty("c", event.card() == null || hidden ? "" : event.card().id().toString());
        e.addProperty("e", event.card() != null && !hidden && event.card().enchanted());
        e.addProperty("a", event.amount());
        e.addProperty("x", event.extra());
        Target target = event.target();
        boolean aimed = target != null && !target.isNone();
        e.addProperty("ts", aimed ? target.side() : -1);
        e.addProperty("th", aimed && target.hero());
        e.addProperty("tm", aimed ? target.minionId() : -1);
        return e;
    }

    private MatchView() {
    }

    static {
        // Make sure the card list is loaded before any view is built.
        Cards.collectible();
    }
}
