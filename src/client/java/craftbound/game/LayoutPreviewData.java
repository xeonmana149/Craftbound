package craftbound.game;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import craftbound.card.CardClass;
import craftbound.card.Cards;
import craftbound.card.DeckRules;
import craftbound.card.Keyword;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;

/**
 * Disposable client-only scenes for the production multiplayer layouts.
 * No table registration, card plays, owned inventory, network messages or rewards.
 */
public final class LayoutPreviewData {
    public enum Mode { DUEL, DUOS, COMMANDER }
    public enum Density { EMPTY, POPULATED, FULL }
    private static final long SEED = 0x4C41594F55544CL;
    private static final String[] NAMES = {"Preview One", "Preview Two", "Preview Three", "Preview Four"};
    private static final CardClass[] CLASSES = {CardClass.FIGHTER, CardClass.BUILDER, CardClass.CHEF, CardClass.ALCHEMIST};
    private static final Identifier BACK = Identifier.parse("craftbound:classic");

    /** Only the selected seat's sample hand is exposed, as in a real player view. */
    public static JsonObject create(Mode requestedMode, Density requestedDensity, int perspective) {
        Mode mode = requestedMode == null ? Mode.DUEL : requestedMode;
        Density density = requestedDensity == null ? Density.POPULATED : requestedDensity;
        int count = mode == Mode.DUEL ? 2 : 4, viewer = Math.floorMod(perspective, count);
        // Prefer collectibles, but required system tokens make a tiny catalogue useful too.
        List<Identifier> ids = Cards.ids().stream().filter(id -> Cards.get(id) != null && !Cards.deleted(id))
                .sorted(Comparator.<Identifier, Boolean>comparing(id -> Cards.get(id).token())
                        .thenComparing(Identifier::toString)).toList();
        List<Identifier> mobs = ids.stream().filter(id -> Cards.get(id).isMob()
                && !Cards.get(id).keywords().contains(Keyword.BLOCK)).toList();
        List<Identifier> blocks = ids.stream().filter(id -> Cards.get(id).isMob()
                && Cards.get(id).keywords().contains(Keyword.BLOCK)).toList();
        List<Identifier> tools = ids.stream().filter(id -> Cards.get(id).isTool()).toList();
        var players = new MatchPlayer[count];
        for (int s = 0; s < count; s++) players[s] = new MatchPlayer(NAMES[s], previewId(s), CLASSES[s], BACK,
                seedDeck(CLASSES[s], ids, mode));
        Match match = match(mode, players);
        CardRef[] leaders = leaders(match, mobs);
        populate(match, mode, density, ids, mobs, blocks, tools, leaders);
        // Suppress legality computation before obtaining the view; the final JSON is PLAYING.
        match.phase = Match.Phase.MULLIGAN;
        JsonObject result;
        if (match instanceof RelayMatch duos) result = RelayTables.view(duos, duos.player(viewer).playerId);
        else if (match instanceof CommanderMatch commander) result = CommanderTables.view(commander, viewer);
        else {
            result = JsonParser.parseString(MatchView.build(match, viewer, match.events.size())).getAsJsonObject();
            if (mode == Mode.DUOS) duosFields(result, match, viewer);
            if (mode == Mode.COMMANDER) commanderFields(result, match, leaders);
        }
        result.addProperty("layoutPreview", true);
        result.addProperty("previewMode", mode.name());
        result.addProperty("previewDensity", density.name());
        result.addProperty("session", "layout-preview-" + mode.name().toLowerCase(Locale.ROOT));
        result.addProperty("revision", 0); result.addProperty("stateRevision", 0);
        result.addProperty("phase", Match.Phase.PLAYING.name());
        result.addProperty("you", viewer); result.addProperty("myTurn", false);
        result.addProperty("clockTicks", -1); result.addProperty("over", false);
        result.addProperty("winner", -1); result.addProperty("paused", false); result.addProperty("pauseRequest", -1);
        result.add("moves", new JsonObject()); result.add("events", new JsonArray()); result.add("log", new JsonArray());
        if (mode == Mode.DUEL) result.addProperty("spectator", true);
        readOnly(result, viewer);
        return result;
    }

    private static UUID previewId(int seat) {
        return UUID.nameUUIDFromBytes(("craftbound:layout-preview:" + seat).getBytes(StandardCharsets.UTF_8));
    }

    /** Use real format objects when the catalogue supports a legal seed deck. */
    private static Match match(Mode mode, MatchPlayer[] players) {
        if (mode == Mode.DUOS && Arrays.stream(players).allMatch(p -> RelayMatch.deckProblem(p.heroClass, p.deck) == null))
            return new RelayMatch(new MatchPlayer[][]{{players[0], players[2]}, {players[1], players[3]}}, RandomSource.create(SEED));
        if (mode == Mode.COMMANDER) {
            var picks = new HashMap<UUID, Identifier>();
            for (var p : players) {
                Identifier leader = p.deck.stream().map(CardRef::id).filter(CommanderMatch::eligible).findFirst().orElse(null);
                if (leader != null && CommanderMatch.problem(p.heroClass, p.deck, leader) == null) picks.put(p.playerId, leader);
            }
            if (picks.size() == players.length) {
                var commander = new CommanderMatch(Arrays.asList(players), picks, RandomSource.create(SEED));
                // Gameplay randomizes seats; previews keep their labels fixed across modes.
                var chosen = new HashMap<UUID, CardRef>();
                for (int s = 0; s < players.length; s++) chosen.put(commander.player(s).playerId, commander.leaders[s]);
                Arrays.sort(commander.players, Comparator.comparingInt(p -> Arrays.asList(NAMES).indexOf(p.name)));
                for (int s = 0; s < players.length; s++) {
                    commander.leaders[s] = chosen.get(commander.player(s).playerId);
                    commander.focus[s] = (s + 1) % players.length;
                }
                return commander;
            }
        }
        // No-effects restoration also accepts zero cards. Preview repetition never relaxes live deck rules.
        boolean[] done = new boolean[players.length]; Arrays.fill(done, true);
        return new Match(players, Match.Phase.PLAYING, done, 0, 8, 1000, SEED, List.of());
    }

    private static List<CardRef> seedDeck(CardClass cls, List<Identifier> ids, Mode mode) {
        var allowed = ids.stream().filter(id -> DeckRules.allowed(id, cls)).toList();
        var deck = new ArrayList<CardRef>();
        int limit = mode == Mode.COMMANDER ? CommanderMatch.DECK_SIZE : DeckRules.DECK_SIZE;
        if (mode == Mode.COMMANDER)
            allowed.stream().filter(CommanderMatch::eligible).findFirst().ifPresent(id -> deck.add(new CardRef(id, false)));
        for (Identifier id : allowed) {
            if (mode == Mode.COMMANDER && deck.stream().anyMatch(c -> c.id().equals(id))) continue;
            int copies = mode == Mode.COMMANDER ? 1 : DeckRules.maxCopies(Cards.get(id).rarity());
            for (int i = 0; i < copies && deck.size() < limit; i++) deck.add(new CardRef(id, i % 2 != 0));
            if (deck.size() == limit) break;
        }
        return deck;
    }

    private static CardRef[] leaders(Match match, List<Identifier> mobs) {
        var result = new CardRef[match.players.length];
        for (int s = 0; s < result.length; s++) {
            if (match instanceof CommanderMatch commander) result[s] = commander.leaders[s];
            else if (!mobs.isEmpty()) {
                CardClass cls = match.player(s).heroClass;
                var candidates = mobs.stream().filter(id -> DeckRules.allowed(id, cls)).toList();
                if (candidates.isEmpty()) candidates = mobs;
                result[s] = new CardRef(candidates.get(s % candidates.size()), s % 2 != 0);
            }
        }
        return result;
    }

    private static void populate(Match match, Mode mode, Density density, List<Identifier> ids,
                                 List<Identifier> mobs, List<Identifier> blocks, List<Identifier> tools, CardRef[] leaders) {
        match.current = 0; match.turn = density == Density.EMPTY ? 1 : 8; match.turnTicksRemaining = 0;
        match.events.clear(); match.log.clear();
        int mobCount = density == Density.EMPTY ? 0 : density == Density.FULL ? Match.MAX_MOBS : 4;
        int blockCount = density == Density.EMPTY ? 0 : density == Density.FULL ? Match.MAX_BLOCKS : 2;
        int handCount = density == Density.FULL ? Match.MAX_HAND : 6;
        for (int s = 0; s < match.players.length; s++) {
            var p = match.player(s);
            p.deck.clear(); p.hand.clear(); p.board.clear(); p.tool = null;
            p.deckName = "Preview " + p.heroClass.displayName().getString();
            p.maxHealth = mode == Mode.COMMANDER ? CommanderMatch.HEALTH : Match.START_HEALTH;
            int damage = density == Density.EMPTY ? 0 : mode == Mode.DUOS ? 6 + (s % 2) * 6 : 4 + s * 3;
            p.health = p.maxHealth - damage;
            p.maxXp = density == Density.EMPTY ? 1 : 8; p.xp = density == Density.EMPTY ? 1 : 6 - s;
            p.heroAttacked = true; p.powerUsed = s % 2 != 0; match.mulliganDone[s] = true;
            for (int i = 0; i < mobCount && !mobs.isEmpty(); i++) {
                CardRef ref = mode == Mode.COMMANDER && i == 0 && leaders[s] != null ? leaders[s]
                        : sample(mobs, s * 3 + i, i % 2 != 0);
                var unit = new Minion(1 + s * 20 + i, ref);
                unit.ready = false; unit.summonedThisTurn = false; p.board.add(unit);
            }
            for (int i = 0; i < blockCount && !blocks.isEmpty(); i++) {
                var unit = new Minion(11 + s * 20 + i, sample(blocks, s * 2 + i, i % 2 != 0));
                unit.ready = false; unit.summonedThisTurn = false; p.board.add(unit);
            }
            for (int i = 0; i < handCount && !ids.isEmpty(); i++) p.hand.add(sample(ids, s * 5 + i, i % 2 != 0));
            if (!tools.isEmpty()) {
                CardRef ref = sample(tools, s, s % 2 != 0); var d = Cards.get(ref.id());
                p.tool = new Tool(ref, d.attack(), d.health(), d.keywords().contains(Keyword.RANGED));
            }
            int remaining = Math.max(0, (mode == Mode.COMMANDER ? 19 : 30) - p.board.size() - p.hand.size() - (p.tool == null ? 0 : 1));
            for (int i = 0; i < remaining && !ids.isEmpty(); i++) p.deck.add(sample(ids, s * 7 + i, false));
            if (match instanceof CommanderMatch commander) {
                commander.casts[s] = density == Density.EMPTY ? 0 : 1;
                commander.unit[s] = p.board.isEmpty() ? 0 : p.board.getFirst().id;
                for (int other = 0; other < match.players.length; other++)
                    commander.strikes[s][other] = other == s ? 0 : (s + other) % 3 * 2;
            }
            if (match instanceof RelayMatch duos) duos.ready[s] = false;
        }
    }

    private static CardRef sample(List<Identifier> pool, int index, boolean enchanted) {
        return new CardRef(pool.get(Math.floorMod(index, pool.size())), enchanted);
    }

    /** Only format-specific view fields need a fallback when a live deck cannot be built. */
    private static void duosFields(JsonObject out, Match match, int viewer) {
        out.addProperty("team", RelayMatch.teamOf(viewer)); out.addProperty("activeTeam", 0);
        out.addProperty("ready", false); out.addProperty("partnerReady", false);
        out.addProperty("winnerTeam", -1); out.addProperty("resultReason", ""); out.addProperty("lastMove", "");
        for (int s = 0; s < match.players.length; s++) {
            var p = out.getAsJsonArray("players").get(s).getAsJsonObject();
            p.addProperty("team", RelayMatch.teamOf(s)); p.addProperty("partnerSeat", RelayMatch.partnerSeat(s));
            p.addProperty("ready", false); p.addProperty("me", s == viewer); p.addProperty("focus", 1 - RelayMatch.teamOf(s));
        }
        out.add("hand", hand(match.player(viewer)));
    }

    private static void commanderFields(JsonObject out, Match match, CardRef[] leaders) {
        for (int s = 0; s < match.players.length; s++) {
            var p = out.getAsJsonArray("players").get(s).getAsJsonObject();
            CardRef leader = leaders[s]; boolean deployed = leader != null && !match.player(s).board.isEmpty();
            p.addProperty("alive", true); p.addProperty("leader", leader == null ? "" : leader.id().toString());
            p.addProperty("leaderEnch", leader != null && leader.enchanted());
            p.addProperty("commandUnit", deployed ? match.player(s).board.getFirst().id : 0);
            p.addProperty("commandCost", leader == null ? 0 : Math.min(Match.MAX_XP,
                    Cards.get(leader.id()).cost() + (deployed ? CommanderMatch.TAX : 0)));
            p.addProperty("deployed", deployed);
            var strikes = new JsonArray();
            for (int other = 0; other < match.players.length; other++) strikes.add(other == s ? 0 : (s + other) % 3 * 2);
            p.add("strikes", strikes);
        }
    }

    private static JsonArray hand(MatchPlayer player) {
        var hand = new JsonArray();
        for (CardRef ref : player.hand) {
            var card = new JsonObject(); card.addProperty("id", ref.id().toString()); card.addProperty("ench", ref.enchanted());
            card.addProperty("playable", false); card.addProperty("target", Target.Kind.NONE.name()); hand.add(card);
        }
        return hand;
    }

    private static void readOnly(JsonObject out, int viewer) {
        var seats = out.getAsJsonArray("players");
        for (int s = 0; s < seats.size(); s++) {
            var p = seats.get(s).getAsJsonObject();
            p.addProperty("myTurn", false); p.addProperty("heroReady", false); p.addProperty("mulliganDone", true);
            p.addProperty("handVisible", s == viewer); p.addProperty("canRequestHand", false); p.addProperty("handPending", false);
            p.getAsJsonObject("power").addProperty("usable", false);
            if (s != viewer) p.remove("hand");
            for (var raw : p.getAsJsonArray("board")) {
                var unit = raw.getAsJsonObject(); unit.addProperty("ready", false); unit.addProperty("faceReady", false);
            }
            if (p.has("hand")) disableHand(p.getAsJsonArray("hand"));
        }
        if (out.has("hand")) disableHand(out.getAsJsonArray("hand"));
    }

    private static void disableHand(JsonArray hand) {
        for (var raw : hand) {
            var card = raw.getAsJsonObject(); card.addProperty("playable", false);
            card.addProperty("target", Target.Kind.NONE.name()); card.remove("rivals");
        }
    }
    private LayoutPreviewData() {}
}
