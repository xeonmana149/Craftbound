package craftbound.client.match;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import craftbound.card.CardClass;
import craftbound.card.CardDefinition;
import craftbound.card.Cards;
import craftbound.game.HeroPowers;
import craftbound.game.TrialTraining;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.resources.Identifier;

// What the match screen shows. It starts as a copy of what the server sent; while the screen animates each
// event (a summon, a hit, a death) it changes this copy step by step, then lines it up with the server's again.
final class MatchState {
    static final class Mob {
        int mid;
        Identifier id;
        boolean enchanted;
        TrialTraining training = TrialTraining.NONE;
        int attack;
        int health;
        int maxHealth;
        boolean ready;
        boolean faceReady;
        boolean shield;
        boolean ignoreShield;
        boolean ranged;
        boolean sprint;
        int attackDebuff;
        boolean appliedBenefit;
        boolean frozen;
        boolean withered;
        boolean totem;
        boolean invulnerable;
        boolean block;
        boolean litTnt, detonated;

        CardDefinition card() {
            return Cards.get(this.id);
        }

        Mob copy() {
            Mob mob = new Mob();
            mob.mid = this.mid;
            mob.id = this.id;
            mob.enchanted = this.enchanted;
            mob.training = this.training;
            mob.attack = this.attack;
            mob.health = this.health;
            mob.maxHealth = this.maxHealth;
            mob.ready = this.ready;
            mob.faceReady = this.faceReady;
            mob.shield = this.shield;
            mob.ignoreShield = this.ignoreShield;
            mob.ranged = this.ranged;
            mob.sprint = this.sprint;
            mob.appliedBenefit = this.appliedBenefit;
            mob.attackDebuff = this.attackDebuff;
            mob.frozen = this.frozen;
            mob.withered = this.withered;
            mob.totem = this.totem;
            mob.invulnerable = this.invulnerable;
            mob.block = this.block;
            mob.litTnt = this.litTnt;
            mob.detonated = this.detonated;
            return mob;
        }
    }

    // A card in your hand. uid stays the same while the card is in your hand, so its animation can follow it.
    static final class HandCard {
        long uid;
        Identifier id;
        boolean enchanted;
        TrialTraining training = TrialTraining.NONE;
        boolean playable;
        String target = "NONE";

        HandCard copy() {
            HandCard card = new HandCard();
            card.uid = this.uid;
            card.id = this.id;
            card.enchanted = this.enchanted;
            card.training = this.training;
            card.playable = this.playable;
            card.target = this.target;
            return card;
        }
    }

    static final class Tool {
        final List<craftbound.game.Tool.Enchantment> enchantments = new ArrayList<>();
        Identifier id;
        boolean enchanted;
        int attack;
        int uses;
        boolean ranged;

        Tool copy() {
            Tool tool = new Tool();
            tool.id = this.id;
            tool.enchanted = this.enchanted;
            tool.attack = this.attack;
            tool.uses = this.uses;
            tool.ranged = this.ranged;
            tool.enchantments.addAll(this.enchantments);
            return tool;
        }
    }

    record TrialFoe(String name, String abilityName, String abilityText, boolean boss, int horn) {}

    record CharmSlot(TrialsScreen.Charm charm, boolean spent) {}

    static final class Side {
        String name = "";
        CardClass cardClass = CardClass.NEUTRAL;
        boolean ai;
        UUID uuid;
        Identifier badOmen;
        Identifier sleeve;
        // The name the player gave their deck, or "" for its default ("Farmer Deck").
        String deckName = "";
        int health;
        int maxHealth = craftbound.game.Match.START_HEALTH;
        int xp;
        int maxXp;
        int deck;
        int handCount;
        boolean mulliganDone;
        TrialFoe trialFoe;
        final List<CharmSlot> charms = new ArrayList<>();
        // Your hand (empty for the other player).
        final List<HandCard> hand = new ArrayList<>();
        // The other player's hand: one id per face-down card, for its animation.
        final List<Long> backs = new ArrayList<>();
        final List<Mob> board = new ArrayList<>();
        Tool tool;
        int heroAttack;
        boolean heroReady;
        boolean frozen;
        HeroPowers.Power power = HeroPowers.Power.NONE;
        boolean powerUsable;
        boolean powerUsed;

        Mob mob(int mid) {
            for (Mob mob : this.board) {
                if (mob.mid == mid) {
                    return mob;
                }
            }
            return null;
        }

        int indexOf(int mid) {
            for (int i = 0; i < this.board.size(); i++) {
                if (this.board.get(i).mid == mid) {
                    return i;
                }
            }
            return -1;
        }

        Side copy() {
            Side side = new Side();
            side.name = this.name;
            side.cardClass = this.cardClass;
            side.ai = this.ai;
            side.uuid = this.uuid;
            side.sleeve = this.sleeve;
            side.deckName = this.deckName;
            side.health = this.health;
            side.maxHealth = this.maxHealth;
            side.xp = this.xp;
            side.maxXp = this.maxXp;
            side.deck = this.deck;
            side.handCount = this.handCount;
            side.mulliganDone = this.mulliganDone;
            side.trialFoe = this.trialFoe;
            side.charms.addAll(this.charms);
            this.hand.forEach(card -> side.hand.add(card.copy()));
            side.backs.addAll(this.backs);
            this.board.forEach(mob -> side.board.add(mob.copy()));
            side.tool = this.tool == null ? null : this.tool.copy();
            side.heroAttack = this.heroAttack;
            side.heroReady = this.heroReady;
            side.badOmen = this.badOmen;
            side.frozen = this.frozen;
            side.power = this.power;
            side.powerUsable = this.powerUsable;
            side.powerUsed = this.powerUsed;
            return side;
        }
    }

    record LogLine(String text, Identifier card, int round, String turnOf) {
    }

    // A card someone must pick from a hand (Discard/Steal/Shuffle from hand). cards is only sent to the
    // chooser, as the victim's hand; it's empty for everyone else.
    record Choice(String action, int owner, int victim, int chooser, int count, int ticks, Identifier source, List<HandCard> cards) {
    }

    // One step to animate. mid is Match.HERO (0) for a hero; targetSide is -1 when nothing was aimed at.
    record Event(String type, int side, int mid, Identifier card, boolean enchanted, int amount, int extra,
                 int targetSide, boolean targetHero, int targetMid, int originSide, int originMid, TrialTraining training) {
        Event(String type, int side, int mid, Identifier card, boolean enchanted, int amount, int extra,
              int targetSide, boolean targetHero, int targetMid) {
            this(type, side, mid, card, enchanted, amount, extra, targetSide, targetHero, targetMid, -1, 0, TrialTraining.NONE);
        }
        boolean aimed() {
            return this.targetSide >= 0;
        }
    }

    static TrialTraining readTraining(JsonObject object) {
        return object.has("training") ? TrialTraining.fromName(object.get("training").getAsString()) : TrialTraining.NONE;
    }

    int you;
    craftbound.board.BoardDesign boardDesign = craftbound.board.BoardDesign.CLASSIC;
    int turn;
    int current;
    int winner;
    int powerCost = 2;
    boolean playing;
    boolean myTurn;
    boolean over;
    final List<LogLine> log = new ArrayList<>();
    final Side[] sides;
    MatchState() { this(2); }
    private MatchState(int seats) {
        sides = new Side[seats];
        for (int i = 0; i < seats; i++) sides[i] = new Side();
    }
    Choice choice;

    Side me() {
        return this.sides[this.you];
    }

    Side them() {
        return this.sides[1 - this.you];
    }

    MatchState copy() {
        MatchState state = new MatchState();
        state.you = this.you;
        state.boardDesign = this.boardDesign;
        state.turn = this.turn;
        state.current = this.current;
        state.winner = this.winner;
        state.powerCost = this.powerCost;
        state.playing = this.playing;
        state.myTurn = this.myTurn;
        state.over = this.over;
        state.choice = this.choice;
        state.log.addAll(this.log);
        for (int i = 0; i < 2; i++) {
            state.sides[i] = this.sides[i].copy();
        }
        return state;
    }

    static MatchState parse(JsonObject root, List<Event> events) {
        MatchState state = new MatchState();
        state.you = root.get("you").getAsInt();
        if (root.has("boardDesign")) state.boardDesign = craftbound.board.BoardDesign.fromId(root.get("boardDesign").getAsString());
        state.turn = root.get("turn").getAsInt();
        state.current = root.get("current").getAsInt();
        state.playing = root.get("phase").getAsString().equals("PLAYING");
        state.myTurn = root.get("myTurn").getAsBoolean();
        state.over = root.get("over").getAsBoolean();
        state.winner = root.get("winner").getAsInt();
        state.powerCost = root.get("powerCost").getAsInt();
        for (JsonElement element : root.getAsJsonArray("log")) {
            JsonObject line = element.getAsJsonObject();
            state.log.add(new LogLine(line.get("text").getAsString(), card(line.get("card").getAsString()),
                    line.get("round").getAsInt(), line.get("turnOf").getAsString()));
        }
        for (JsonElement element : root.getAsJsonArray("events")) {
            JsonObject e = element.getAsJsonObject();
            events.add(new Event(e.get("t").getAsString(), e.get("s").getAsInt(), e.get("m").getAsInt(),
                    card(e.get("c").getAsString()), e.get("e").getAsBoolean(), e.get("a").getAsInt(), e.get("x").getAsInt(),
                    e.get("ts").getAsInt(), e.get("th").getAsBoolean(), e.get("tm").getAsInt(),
                    e.has("os") ? e.get("os").getAsInt() : -1, e.has("om") ? e.get("om").getAsInt() : 0, readTraining(e)));
        }
        JsonArray players = root.getAsJsonArray("players");
        for (int i = 0; i < 2; i++) {
            JsonObject p = players.get(i).getAsJsonObject();
            Side side = state.sides[i];
            side.name = p.get("name").getAsString();
            if (p.has("trialFoe")) {
                var foe = p.getAsJsonObject("trialFoe");
                side.trialFoe = new TrialFoe(foe.get("name").getAsString(),foe.get("abilityName").getAsString(),
                        foe.get("abilityText").getAsString(),foe.get("boss").getAsBoolean(),
                        foe.has("turnsUntilHorn") ? foe.get("turnsUntilHorn").getAsInt() : -1);
            }
            side.cardClass = cardClass(p.get("cls").getAsString());
            side.ai = p.get("ai").getAsBoolean();
            String uuid = p.get("uuid").getAsString();
            side.uuid = uuid.isEmpty() ? null : UUID.fromString(uuid);
            side.sleeve = card(p.get("sleeve").getAsString());
            side.deckName = p.get("deckName").getAsString();
            side.health = p.get("health").getAsInt();
            side.heroAttack = p.has("heroAttack") ? p.get("heroAttack").getAsInt() : 0;
            side.maxHealth = p.has("maxHealth") ? p.get("maxHealth").getAsInt() : craftbound.game.Match.START_HEALTH;
            side.xp = p.get("xp").getAsInt();
            side.maxXp = p.get("maxXp").getAsInt();
            side.deck = p.get("deck").getAsInt();
            side.handCount = p.get("handCount").getAsInt();
            side.mulliganDone = p.get("mulliganDone").getAsBoolean();
            if (p.has("trialsCharms")) for (var raw : p.getAsJsonArray("trialsCharms")) {
                var slot = raw.getAsJsonObject();
                side.charms.add(new CharmSlot(new TrialsScreen.Charm(slot.get("id").getAsString(), slot.get("title").getAsString(),
                            slot.get("description").getAsString(), slot.get("iconItem").getAsString()), slot.get("spent").getAsBoolean()));
            }
            if (p.has("hand")) {
                for (JsonElement element : p.getAsJsonArray("hand")) {
                    JsonObject c = element.getAsJsonObject();
                    HandCard card = new HandCard();
                    card.id = Identifier.parse(c.get("id").getAsString());
                    card.enchanted = c.get("ench").getAsBoolean();
                    card.training = readTraining(c);
                    card.playable = c.get("playable").getAsBoolean();
                    card.target = c.get("target").getAsString();
                    side.hand.add(card);
                }
            }
            for (JsonElement element : p.getAsJsonArray("board")) {
                JsonObject m = element.getAsJsonObject();
                side.board.add(readMob(m));
            }
            if (p.has("tool")) {
                JsonObject t = p.getAsJsonObject("tool");
                side.tool = readTool(t);
            }
            side.heroReady = p.get("heroReady").getAsBoolean();
            side.badOmen = p.has("badOmen") && !p.get("badOmen").isJsonNull() ? card(p.get("badOmen").getAsString()) : null;
            side.frozen = p.has("frozen") && p.get("frozen").getAsBoolean();
            JsonObject power = p.getAsJsonObject("power");
            side.power = HeroPowers.Power.valueOf(power.get("name").getAsString());
            side.powerUsable = power.get("usable").getAsBoolean();
            side.powerUsed = power.get("used").getAsBoolean();
        }
        readChoice(root, state);
        return state;
    }

    static Mob readMob(JsonObject m) {
        Mob mob = new Mob();
        mob.mid = m.get("mid").getAsInt();
        mob.id = Identifier.parse(m.get("id").getAsString());
        mob.enchanted = m.get("ench").getAsBoolean();
        mob.training = readTraining(m);
        mob.attack = m.get("atk").getAsInt();
        mob.health = m.get("hp").getAsInt();
        mob.maxHealth = m.get("maxHp").getAsInt();
        mob.ready = m.get("ready").getAsBoolean();
        mob.faceReady = m.has("faceReady") && m.get("faceReady").getAsBoolean();
        mob.shield = m.get("shield").getAsBoolean();
        mob.ignoreShield = m.has("ignoreShield") && m.get("ignoreShield").getAsBoolean();
        mob.ranged = m.has("ranged") ? m.get("ranged").getAsBoolean() : mob.card() != null && mob.card().keywords().contains(craftbound.card.Keyword.RANGED);
        mob.sprint = m.has("sprint") ? m.get("sprint").getAsBoolean() : mob.card() != null && mob.card().keywords().contains(craftbound.card.Keyword.SPRINT);
        mob.appliedBenefit = m.has("appliedBenefit") && m.get("appliedBenefit").getAsBoolean();
        mob.attackDebuff = m.has("attackDebuff") ? m.get("attackDebuff").getAsInt() : 0;
        mob.frozen = m.get("frozen").getAsBoolean();
        mob.withered = m.get("withered").getAsBoolean();
        mob.totem = m.get("totem").getAsBoolean();
        mob.invulnerable = m.get("invulnerable").getAsBoolean();
        mob.litTnt = craftbound.card.CardCatalog.hasTntFuse(mob.id);
        mob.block = m.get("block").getAsBoolean();
        return mob;
    }

    static Tool readTool(JsonObject t) {
        Tool tool = new Tool();
        tool.id = Identifier.parse(t.get("id").getAsString());
        tool.enchanted = t.get("ench").getAsBoolean();
        tool.attack = t.get("atk").getAsInt();
        tool.uses = t.get("uses").getAsInt();
        tool.ranged = t.get("ranged").getAsBoolean();
        if (t.has("enchantments")) for (var entry : t.getAsJsonArray("enchantments")) {
            JsonObject e = entry.getAsJsonObject();
            tool.enchantments.add(new craftbound.game.Tool.Enchantment(
                    new craftbound.game.CardRef(Identifier.parse(e.get("id").getAsString()), e.get("ench").getAsBoolean()),
                    e.get("atk").getAsInt(), e.get("uses").getAsInt()));
        }
        return tool;
    }

    /** Lightweight overlay state; does not duplicate the multiplayer board parser. */
    static MatchState choiceView(JsonObject root) {
        int seats = root.has("players") ? root.getAsJsonArray("players").size() : 2;
        MatchState state = new MatchState(seats);
        state.you = root.has("you") ? root.get("you").getAsInt() : -1;
        state.over = root.has("over") && root.get("over").getAsBoolean();
        for (int i = 0; i < seats && root.has("players"); i++)
            state.sides[i].name = root.getAsJsonArray("players").get(i).getAsJsonObject().get("name").getAsString();
        readChoice(root, state);
        return state;
    }

    private static void readChoice(JsonObject root, MatchState state) {
        if (root.has("choice")) {
            JsonObject c = root.getAsJsonObject("choice");
            List<HandCard> cards = new ArrayList<>();
            if (c.has("cards")) for (JsonElement element : c.getAsJsonArray("cards")) {
                JsonObject entry = element.getAsJsonObject();
                HandCard card = new HandCard();
                card.id = Identifier.parse(entry.get("id").getAsString());
                card.enchanted = entry.get("ench").getAsBoolean();
                card.training = readTraining(entry);
                cards.add(card);
            }
            state.choice = new Choice(c.get("action").getAsString(), c.get("owner").getAsInt(), c.get("victim").getAsInt(),
                    c.get("chooser").getAsInt(), c.get("count").getAsInt(), c.get("ticks").getAsInt(),
                    card(c.get("source").getAsString()), List.copyOf(cards));
        }
    }

    private static Identifier card(String id) {
        return id.isEmpty() ? null : Identifier.parse(id);
    }

    private static CardClass cardClass(String name) {
        try {
            return CardClass.valueOf(name);
        } catch (IllegalArgumentException e) {
            return CardClass.NEUTRAL;
        }
    }
}
