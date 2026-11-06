package craftbound.game;

import com.google.gson.*;
import craftbound.card.Cards;
import java.util.*;
import net.minecraft.resources.Identifier;

/** Private server save, never sent to clients: includes both hands and ordered decks. */
final class MatchSnapshot {
    private static final Gson JSON = new GsonBuilder()
            .registerTypeAdapter(Identifier.class, new TypeAdapter<Identifier>() {
                public void write(com.google.gson.stream.JsonWriter out, Identifier id) throws java.io.IOException {
                    if (id == null) out.nullValue(); else out.value(id.toString());
                }
                public Identifier read(com.google.gson.stream.JsonReader in) throws java.io.IOException {
                    if (in.peek() == com.google.gson.stream.JsonToken.NULL) { in.nextNull(); return null; }
                    return Identifier.parse(in.nextString());
                }
            }).create();
    private record State(int version, MatchPlayer[] players, Match.Phase phase, boolean[] mulligan,
                         int current, int turn, int nextId, long seed, List<Match.LogEntry> log,
                         Integer turnTicksRemaining,int[] pauseRequestedTurn, String boardDesign, String trialsBattle) {}
    static JsonObject capture(Match match) {
        if (match.over) throw new IllegalStateException("Cannot pause a finished match");
        // Establish a saved continuation seed; failed disk writes also continue from this seed.
        long seed = match.random.nextLong();
        match.random.setSeed(seed);
        return JSON.toJsonTree(new State(1, match.players, match.phase, match.mulliganDone,
                match.current, match.turn, match.nextMinionId(), seed, match.log,
                match.turnTicksRemaining,match.pauseRequestedTurn, match.boardDesign.id(), match.trialsBattle)).getAsJsonObject();
    }
    static Match restore(JsonObject json) {
        State s = JSON.fromJson(json, State.class);
        if (s.version != 1 || s.players == null || s.players.length != 2 || s.phase == null
                || s.mulligan == null || s.mulligan.length != 2 || s.current < 0 || s.current > 1
                || s.nextId < 1 || s.log == null) throw new IllegalArgumentException("Invalid saved match");
        Set<Integer> ids = new HashSet<>();
        for (MatchPlayer p : s.players) {
            if (p == null || p.heroClass == null || p.hand.size() > Match.MAX_HAND || p.board.size() > Match.MAX_MOBS + Match.MAX_BLOCKS)
                throw new IllegalArgumentException("Invalid saved participant");
            if (p.badOmenSummon != null && (Cards.get(p.badOmenSummon) == null || !Cards.get(p.badOmenSummon).isMob())) p.badOmenSummon = null;
            p.deck.forEach(MatchSnapshot::checkCard); p.hand.forEach(MatchSnapshot::checkCard);
            for (Minion m : p.board) {
                checkCard(m.card);
                if (m.impaled != null) checkCard(m.impaled.card);
                if (m.timedWeaknesses == null) m.timedWeaknesses = new ArrayList<>();
                if (!Cards.get(m.card.id()).isMob() || m.id <= 0 || m.id >= s.nextId || !ids.add(m.id))
                    throw new IllegalArgumentException("Invalid saved mob");
            }
            if (p.tool != null) {
                checkCard(p.tool.card);
                p.tool.enchantments.forEach(e -> checkCard(e.card()));
                p.tool.triggerEnchantments.forEach(MatchSnapshot::checkCard);
            }
            if (p.brewing == null) p.brewing = new ArrayList<>();
            p.brewing.forEach(r -> checkCard(r.card()));
            p.eventsSent = 0;
        }
        if (s.players[0].isAi() && s.players[1].isAi()
                || s.players[0].playerId != null && s.players[0].playerId.equals(s.players[1].playerId))
            throw new IllegalArgumentException("Invalid saved players");
        var match=new Match(s.players, s.phase, s.mulligan, s.current, s.turn, s.nextId, s.seed, s.log);
        match.boardDesign = craftbound.board.BoardDesign.fromId(s.boardDesign);
        match.trialsBattle = s.trialsBattle;
        match.turnTicksRemaining=s.turnTicksRemaining==null?Match.TURN_TICKS:Math.clamp(s.turnTicksRemaining,0,Match.TURN_TICKS);
        if(s.pauseRequestedTurn!=null && s.pauseRequestedTurn.length==2)
            System.arraycopy(s.pauseRequestedTurn,0,match.pauseRequestedTurn,0,2);
        match.refreshAuras();
        return match;
    }
    static boolean participant(JsonObject json, UUID id) {
        for (JsonElement p : json.getAsJsonArray("players")) {
            JsonElement value = p.getAsJsonObject().get("playerId");
            if (value != null && !value.isJsonNull() && value.getAsString().equals(id.toString())) return true;
        }
        return false;
    }
    private static void checkCard(CardRef ref) {
        if (ref == null || Cards.get(ref.id()) == null) throw new IllegalArgumentException("A saved card is missing from the catalogue");
    }
}
