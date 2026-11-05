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
        return buildJson(match, viewer, firstEvent).toString();
    }

    /** The same view as {@link #build}, without a string round trip for callers that add fields. */
    public static JsonObject buildJson(Match match, int viewer, int firstEvent) {
        return view(match, viewer, firstEvent, true);
    }

    // `withLog` false skips the battle log, which the public tabletop never shows.
    private static JsonObject view(Match match, int viewer, int firstEvent, boolean withLog) {
        JsonObject root = new JsonObject();
        root.addProperty("boardDesign", match.boardDesign.id());
        root.addProperty("you", viewer);
        root.addProperty("turn", match.turn);
        root.addProperty("clockTicks", match.timed() && match.phase==Match.Phase.PLAYING && !match.over?match.turnTicksRemaining:-1);
        root.addProperty("clockLimitTicks", match.turnLimitTicks());
        root.addProperty("clockWarningTicks", match.turnWarningTicks());
        root.addProperty("current", match.current);
        root.addProperty("phase", match.phase.name());
        root.addProperty("myTurn", match.yourMove(viewer));
        root.addProperty("over", match.over);
        root.addProperty("winner", match.winner);
        root.addProperty("powerCost", Match.POWER_COST);
        if (match.players.length > 2) {
            JsonArray history = new JsonArray();
            for (String line : match.publicBattleLog) history.add(line);
            root.add("publicLog", history);
            if(withLog) {
                // Four-seat screens receive the resolved board, plus public attack endpoints for the trail.
                for(MatchEvent event:match.events.reversed()){
                    if(event.type()!=MatchEvent.Type.ATTACK || event.target()==null || event.target().isNone())continue;
                    JsonObject path=new JsonObject();
                    path.addProperty("from",event.side());path.addProperty("source",event.mid());
                    path.addProperty("to",event.target().side());
                    path.addProperty("target",event.target().hero()?Match.HERO:event.target().minionId());
                    root.add("publicAttack",path);
                    break;
                }
            }
        }
        // The battle log: plain lines (with the card they're about, for an icon) and turn headers.
        if (withLog) {
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
        }

        JsonArray events = new JsonArray();
        for (int i = Math.max(0, firstEvent); i < match.events.size(); i++) {
            events.add(event(match.events.get(i), viewer));
        }
        root.add("events", events);

        JsonArray players = new JsonArray();
        for (int side = 0; side < match.players.length; side++) {
            final int boardSide = side;
            MatchPlayer player = match.player(side);
            JsonObject p = new JsonObject();
            p.addProperty("name", player.name);
            p.addProperty("cls", player.heroClass.name());
            p.addProperty("ai", player.isAi());
            if(player.trialsFoe!=null)p.add("trialFoe",TrialsOpponentRule.view(player));
            p.addProperty("uuid", player.playerId == null ? "" : player.playerId.toString());
            p.addProperty("sleeve", player.sleeve == null ? "" : player.sleeve.toString());
            p.addProperty("deckName", player.deckName);
            p.addProperty("health", player.health);
            p.addProperty("maxHealth", player.maxHealth);
            if (player.badOmenSummon != null) p.addProperty("badOmen", player.badOmenSummon.toString());
            p.addProperty("frozen", player.frozen || player.skippedAttackFromFreeze);
            p.addProperty("xp", player.xp);
            p.addProperty("maxXp", player.maxXp);
            p.addProperty("deck", player.deck.size());
            p.addProperty("handCount", player.hand.size());
            p.addProperty("mulliganDone", match.mulliganDone[side]);
            if (side == viewer) {
                if (player.trialsCharms != null && !player.trialsCharms.isEmpty()) {
                    JsonArray charms = new JsonArray();
                    for (int i = 0; i < player.trialsCharms.size(); i++) {
                        TrialCharmSpec charm = TrialCharmSpec.find(player.trialsCharms.get(i));
                        if (charm == null) continue;
                        JsonObject slot = charm.view();
                        slot.addProperty("spent", player.trialsCharmTurn == match.turn
                                && (player.spentTrialsCharms & (1 << i)) != 0);
                        charms.add(slot);
                    }
                    p.add("trialsCharms", charms);
                }
                JsonArray hand = new JsonArray();
                for (int i = 0; i < player.hand.size(); i++) {
                    CardRef card = player.hand.get(i);
                    JsonObject c = new JsonObject();
                    c.addProperty("id", card.id().toString());
                    c.addProperty("ench", card.enchanted());
                writeTraining(c, card);
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
                writeTraining(m, minion.card);
                m.addProperty("atk", match.attackOf(minion));
                m.addProperty("hp", minion.health);
                m.addProperty("maxHp", minion.maxHealth);
                m.addProperty("ready", match.yourMove(side) && minion.canAttack() && match.attackOf(minion) > 0
                        && anyAttackTarget(match, boardSide, minion.id));
                m.addProperty("block", minion.has(Keyword.BLOCK));
                m.addProperty("faceReady", !minion.summonedThisTurn);
                m.addProperty("shield", minion.has(Keyword.SHIELD));
                m.addProperty("ranged", minion.has(Keyword.RANGED));
                m.addProperty("ignoreShield", minion.has(Keyword.IGNORE_SHIELD));
                m.addProperty("sprint", minion.has(Keyword.SPRINT));
                m.addProperty("appliedBenefit", minion.hasAppliedBenefit());
                m.addProperty("attackDebuff", minion.attackDebuff);
                m.addProperty("frozen", minion.frozen || minion.skippedAttackFromFreeze);
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
                JsonArray enchantments = new JsonArray();
                for (Tool.Enchantment applied : player.tool.enchantments) {
                    JsonObject e = new JsonObject();
                    e.addProperty("id", applied.card().id().toString());
                    e.addProperty("ench", applied.card().enchanted());
                    e.addProperty("atk", applied.attack());
                    e.addProperty("uses", applied.durability());
                    enchantments.add(e);
                }
                tool.add("enchantments", enchantments);
                p.add("tool", tool);
            }
            p.addProperty("heroAttack", match.heroAttackOf(side));
            p.addProperty("heroReady", match.heroCanAttack(side));
            JsonObject power = new JsonObject();
            power.addProperty("name", HeroPowers.of(player).name());
            power.addProperty("usable", match.whyCantUsePower(side) == null);
            power.addProperty("used", player.powerUsed);
            p.add("power", power);
            players.add(p);
        }
        root.add("players", players);
        writeChoice(root, match, viewer);
        return root;
    }

    /** Private offers are included only in the chooser's view, including at four-seat tables. */
    public static void writeChoice(JsonObject root, Match match, int viewer) {
        root.remove("choice");
        // A card someone must pick from a hand. Only the chooser is sent the cards to pick from.
        Match.HandChoice choice = match.pendingChoice();
        if (choice != null && !match.over) {
            JsonObject c = new JsonObject();
            c.addProperty("action", choice.action().name());
            c.addProperty("owner", choice.owner());
            c.addProperty("victim", choice.victim());
            c.addProperty("chooser", choice.chooser());
            c.addProperty("count", choice.count());
            c.addProperty("ticks", match.choiceTicksRemaining);
            c.addProperty("source", choice.source() == null ? "" : choice.source().id().toString());
            if (viewer == choice.chooser()) {
                JsonArray cards = new JsonArray();
                for (CardRef card : match.choiceCards(choice)) {
                    JsonObject entry = new JsonObject();
                    entry.addProperty("id", card.id().toString());
                    entry.addProperty("ench", card.enchanted());
                    writeTraining(entry, card);
                    cards.add(entry);
                }
                c.add("cards", cards);
            }
            root.add("choice", c);
        }
    }

    private static boolean anyAttackTarget(Match match, int side, int attacker) {
        for (int defender = 0; defender < match.players.length; defender++) {
            if (!match.inPlay(defender) || !match.isEnemy(side, defender)) continue;
            if (match.whyCantAttack(side, attacker, Target.hero(defender)) == null) return true;
            for (var mob : match.player(defender).board)
                if (match.whyCantAttack(side, attacker, Target.mob(defender, mob.id)) == null) return true;
        }
        return false;
    }

    /** Public tabletop projection. Neither hand, deck order, logs nor private draw events leave the server. */
    public static String publicBoard(Match match, boolean paused) {
        return publicBoardJson(match, paused).toString();
    }

    // Built from a viewer-less view (no hand, log or events), since none of those fields are copied.
    private static final java.util.List<String> PUBLIC_FIELDS = java.util.List.of("name","cls","sleeve","health","maxHealth",
            "badOmen","frozen","xp","maxXp","deck","handCount","board","tool","power","heroAttack");
    public static JsonObject publicBoardJson(Match match, boolean paused) {
        JsonObject full = view(match, -1, match.events.size(), false);
        JsonObject result = new JsonObject();
        result.add("boardDesign", full.get("boardDesign"));
        result.addProperty("turn",match.turn);result.addProperty("current",match.current);
        result.addProperty("paused",paused);result.addProperty("over",match.over);
        result.add("clockTicks",full.get("clockTicks"));
        JsonArray sides=new JsonArray();
        for(var element:full.getAsJsonArray("players")) {
            var source=element.getAsJsonObject();var side=new JsonObject();
            for(String field:PUBLIC_FIELDS)
                if(source.has(field)) side.add(field,source.get(field));
            sides.add(side);
        }
        result.add("players",sides);return result;
    }

    static JsonArray spectatorEvents(Match match,int first,int grants) {
        JsonArray events=new JsonArray();
        for(int i=Math.max(0,first);i<match.events.size();i++){
            MatchEvent event=match.events.get(i);JsonObject e=event(event,-1);
            if(event.type()==MatchEvent.Type.DRAW||event.type()==MatchEvent.Type.GIVE||event.type()==MatchEvent.Type.BURN){
                boolean allowed=(grants&(1<<event.side()))!=0;
                if (allowed) writeTraining(e, event.card()); else e.remove("training");
                e.addProperty("c",allowed&&event.card()!=null?event.card().id().toString():"");
                e.addProperty("e",allowed&&event.card()!=null&&event.card().enchanted());
            }
            events.add(e);
        }
        return events;
    }

    private static void writeTraining(JsonObject json, CardRef card) {
        if (card != null && card.training() != TrialTraining.NONE) json.addProperty("training", card.training().name());
    }

    private static JsonObject event(MatchEvent event, int viewer) {
        JsonObject e = new JsonObject();
        e.addProperty("t", event.type().name());
        e.addProperty("s", event.side());
        e.addProperty("m", event.mid());
        // You only see the cards the other player draws once they play them. Hand effects show the
        // moved card only to the two players involved (a discard is public).
        boolean hidden = switch (event.type()) {
            case DRAW -> event.side() != viewer;
            case HAND_REVEAL, HAND_STEAL -> viewer != event.side() && (event.target() == null || viewer != event.target().side());
            case HAND_SHUFFLE -> viewer != event.side() && viewer != event.extra();
            default -> false;
        };
        e.addProperty("c", event.card() == null || hidden ? "" : event.card().id().toString());
        e.addProperty("e", event.card() != null && !hidden && event.card().enchanted());
        if (!hidden) writeTraining(e, event.card());
        e.addProperty("a", event.amount());
        e.addProperty("x", event.extra());
        Target target = event.target();
        boolean aimed = target != null && !target.isNone();
        e.addProperty("ts", aimed ? target.side() : -1);
        e.addProperty("th", aimed && target.hero());
        e.addProperty("tm", aimed ? target.minionId() : -1);
        Target source = event.source();
        e.addProperty("os", source == null || source.isNone() ? -1 : source.side());
        e.addProperty("om", source == null || source.isNone() || source.hero() ? Match.HERO : source.minionId());
        return e;
    }

    private MatchView() {
    }

    static {
        // Make sure the card list is loaded before any view is built.
        Cards.collectible();
    }
}
