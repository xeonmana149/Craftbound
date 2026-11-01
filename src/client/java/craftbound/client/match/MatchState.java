package craftbound.client.match;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import craftbound.card.CardClass;
import craftbound.card.CardDefinition;
import craftbound.card.Cards;
import craftbound.game.HeroPowers;
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
        int attack;
        int health;
        int maxHealth;
        boolean ready;
        boolean shield;
        boolean frozen;
        boolean withered;
        boolean totem;
        boolean invulnerable;
        boolean block;

        CardDefinition card() {
            return Cards.get(this.id);
        }

        Mob copy() {
            Mob mob = new Mob();
            mob.mid = this.mid;
            mob.id = this.id;
            mob.enchanted = this.enchanted;
            mob.attack = this.attack;
            mob.health = this.health;
            mob.maxHealth = this.maxHealth;
            mob.ready = this.ready;
            mob.shield = this.shield;
            mob.frozen = this.frozen;
            mob.withered = this.withered;
            mob.totem = this.totem;
            mob.invulnerable = this.invulnerable;
            mob.block = this.block;
            return mob;
        }
    }

    // A card in your hand. uid stays the same while the card is in your hand, so its animation can follow it.
    static final class HandCard {
        long uid;
        Identifier id;
        boolean enchanted;
        boolean playable;
        String target = "NONE";

        HandCard copy() {
            HandCard card = new HandCard();
            card.uid = this.uid;
            card.id = this.id;
            card.enchanted = this.enchanted;
            card.playable = this.playable;
            card.target = this.target;
            return card;
        }
    }

    static final class Tool {
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
            return tool;
        }
    }

    static final class Side {
        String name = "";
        CardClass cardClass = CardClass.NEUTRAL;
        boolean ai;
        UUID uuid;
        Identifier sleeve;
        // The name the player gave their deck, or "" for its default ("Farmer Deck").
        String deckName = "";
        int health;
        int xp;
        int maxXp;
        int deck;
        int handCount;
        boolean mulliganDone;
        // Your hand (empty for the other player).
        final List<HandCard> hand = new ArrayList<>();
        // The other player's hand: one id per face-down card, for its animation.
        final List<Long> backs = new ArrayList<>();
        final List<Mob> board = new ArrayList<>();
        Tool tool;
        boolean heroReady;
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
            side.xp = this.xp;
            side.maxXp = this.maxXp;
            side.deck = this.deck;
            side.handCount = this.handCount;
            side.mulliganDone = this.mulliganDone;
            this.hand.forEach(card -> side.hand.add(card.copy()));
            side.backs.addAll(this.backs);
            this.board.forEach(mob -> side.board.add(mob.copy()));
            side.tool = this.tool == null ? null : this.tool.copy();
            side.heroReady = this.heroReady;
            side.power = this.power;
            side.powerUsable = this.powerUsable;
            side.powerUsed = this.powerUsed;
            return side;
        }
    }

    record LogLine(String text, Identifier card, int round, String turnOf) {
    }

    // One step to animate. mid is Match.HERO (0) for a hero; targetSide is -1 when nothing was aimed at.
    record Event(String type, int side, int mid, Identifier card, boolean enchanted, int amount, int extra,
                 int targetSide, boolean targetHero, int targetMid) {
        boolean aimed() {
            return this.targetSide >= 0;
        }
    }

    int you;
    int turn;
    int current;
    int winner;
    int powerCost = 2;
    boolean playing;
    boolean myTurn;
    boolean over;
    final List<LogLine> log = new ArrayList<>();
    final Side[] sides = {new Side(), new Side()};

    Side me() {
        return this.sides[this.you];
    }

    Side them() {
        return this.sides[1 - this.you];
    }

    MatchState copy() {
        MatchState state = new MatchState();
        state.you = this.you;
        state.turn = this.turn;
        state.current = this.current;
        state.winner = this.winner;
        state.powerCost = this.powerCost;
        state.playing = this.playing;
        state.myTurn = this.myTurn;
        state.over = this.over;
        state.log.addAll(this.log);
        for (int i = 0; i < 2; i++) {
            state.sides[i] = this.sides[i].copy();
        }
        return state;
    }

    static MatchState parse(JsonObject root, List<Event> events) {
        MatchState state = new MatchState();
        state.you = root.get("you").getAsInt();
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
                    e.get("ts").getAsInt(), e.get("th").getAsBoolean(), e.get("tm").getAsInt()));
        }
        JsonArray players = root.getAsJsonArray("players");
        for (int i = 0; i < 2; i++) {
            JsonObject p = players.get(i).getAsJsonObject();
            Side side = state.sides[i];
            side.name = p.get("name").getAsString();
            side.cardClass = cardClass(p.get("cls").getAsString());
            side.ai = p.get("ai").getAsBoolean();
            String uuid = p.get("uuid").getAsString();
            side.uuid = uuid.isEmpty() ? null : UUID.fromString(uuid);
            side.sleeve = card(p.get("sleeve").getAsString());
            side.deckName = p.get("deckName").getAsString();
            side.health = p.get("health").getAsInt();
            side.xp = p.get("xp").getAsInt();
            side.maxXp = p.get("maxXp").getAsInt();
            side.deck = p.get("deck").getAsInt();
            side.handCount = p.get("handCount").getAsInt();
            side.mulliganDone = p.get("mulliganDone").getAsBoolean();
            if (p.has("hand")) {
                for (JsonElement element : p.getAsJsonArray("hand")) {
                    JsonObject c = element.getAsJsonObject();
                    HandCard card = new HandCard();
                    card.id = Identifier.parse(c.get("id").getAsString());
                    card.enchanted = c.get("ench").getAsBoolean();
                    card.playable = c.get("playable").getAsBoolean();
                    card.target = c.get("target").getAsString();
                    side.hand.add(card);
                }
            }
            for (JsonElement element : p.getAsJsonArray("board")) {
                JsonObject m = element.getAsJsonObject();
                Mob mob = new Mob();
                mob.mid = m.get("mid").getAsInt();
                mob.id = Identifier.parse(m.get("id").getAsString());
                mob.enchanted = m.get("ench").getAsBoolean();
                mob.attack = m.get("atk").getAsInt();
                mob.health = m.get("hp").getAsInt();
                mob.maxHealth = m.get("maxHp").getAsInt();
                mob.ready = m.get("ready").getAsBoolean();
                mob.shield = m.get("shield").getAsBoolean();
                mob.frozen = m.get("frozen").getAsBoolean();
                mob.withered = m.get("withered").getAsBoolean();
                mob.totem = m.get("totem").getAsBoolean();
                mob.invulnerable = m.get("invulnerable").getAsBoolean();
                mob.block = m.get("block").getAsBoolean();
                side.board.add(mob);
            }
            if (p.has("tool")) {
                JsonObject t = p.getAsJsonObject("tool");
                Tool tool = new Tool();
                tool.id = Identifier.parse(t.get("id").getAsString());
                tool.enchanted = t.get("ench").getAsBoolean();
                tool.attack = t.get("atk").getAsInt();
                tool.uses = t.get("uses").getAsInt();
                tool.ranged = t.get("ranged").getAsBoolean();
                side.tool = tool;
            }
            side.heroReady = p.get("heroReady").getAsBoolean();
            JsonObject power = p.getAsJsonObject("power");
            side.power = HeroPowers.Power.valueOf(power.get("name").getAsString());
            side.powerUsable = power.get("usable").getAsBoolean();
            side.powerUsed = power.get("used").getAsBoolean();
        }
        return state;
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
