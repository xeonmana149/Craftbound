package craftbound.game;

import craftbound.card.*;
import com.google.gson.*;
import static craftbound.game.WorkshopChecks.*;

// Reveal / Discard / Steal / Shuffle from the opponent's hand: rules text, picks by you, the opponent or at random,
// the queued pick blocking the turn, the timeout, full hands and what each viewer is told.
final class HandEffectChecks {
    private static JsonObject spell(JsonObject root, String name, String action, String chooser, int amount) {
        var c = SurvivingKillChecks.unit(root, name, 1, 1, false);
        var e = SurvivingKillChecks.effect("PLAY", "ENEMY_HERO", amount);
        e.addProperty("action", action);
        if (chooser != null) e.addProperty("chooser", chooser);
        c.getAsJsonArray("effects").add(e);
        return c;
    }

    private static void fill(Match m, int side, String... names) {
        for (String n : names) m.player(side).hand.add(new CardRef(id(n), false));
    }

    // WorkshopChecks.match() flips a coin for seats; here seat 0 is always a human and seat 1 is human or the Card Master.
    private static Match table(boolean humanOpponent) {
        var m = match();
        var deck = java.util.Collections.nCopies(30, new CardRef(Cards.ZOMBIE, false));
        m.players[0] = new MatchPlayer("A", java.util.UUID.randomUUID(), CardClass.FISHERMAN, id("classic"), deck);
        m.players[1] = new MatchPlayer("B", humanOpponent ? java.util.UUID.randomUUID() : null, CardClass.FISHERMAN, id("classic"), deck);
        m.player(0).xp = 10;
        return m;
    }

    private static boolean parses(JsonObject root) {
        try { CardCatalog.parse(root.toString()); return true; } catch (IllegalArgumentException ex) { return false; }
    }

    static void run() {
        var original = CardCatalog.current();
        var root = JsonParser.parseString(original.json()).getAsJsonObject();
        spell(root, "test_peek", "REVEAL_HAND", null, 1);
        spell(root, "test_discard_you", "DISCARD_FROM_HAND", "YOU", 1);
        spell(root, "test_discard_them", "DISCARD_FROM_HAND", "OPPONENT", 2);
        spell(root, "test_steal_random", "STEAL_FROM_HAND", "RANDOM", 1);
        spell(root, "test_steal_you", "STEAL_FROM_HAND", "YOU", 2);
        spell(root, "test_shuffle_random", "SHUFFLE_FROM_HAND", "RANDOM", 1);
        try {
            CardCatalog.install(CardCatalog.parse(root.toString()));
            String text = CardCatalog.description(CardCatalog.entry(id("test_discard_you")));
            ok(text.contains("Look at your opponent's hand and discard a card from it"), "Discard (you choose) wording: " + text);
            text = CardCatalog.description(CardCatalog.entry(id("test_discard_them")));
            ok(text.contains("Your opponent discards 2 cards of their choice"), "Discard (opponent chooses) wording: " + text);
            text = CardCatalog.description(CardCatalog.entry(id("test_steal_random")));
            ok(text.contains("Steal a random card from your opponent's hand"), "Steal (random) wording: " + text);
            ok(CardEffects.targetKind(id("test_steal_you")) == Target.Kind.NONE, "Hand effects need no target click");

            // Reveal: one event per card, shown only to the two players involved.
            var m = table(false);
            fill(m, 1, "zombie", "skeleton");
            play(m, "test_peek");
            ok(m.events.stream().filter(e -> e.type() == MatchEvent.Type.HAND_REVEAL).count() == 2, "Reveal sends each card");
            var spectator = MatchView.buildJson(m, -1, 0).getAsJsonArray("events").asList().stream()
                    .map(JsonElement::getAsJsonObject).filter(e -> e.get("t").getAsString().equals("HAND_REVEAL")).toList();
            ok(!spectator.isEmpty() && spectator.stream().allMatch(e -> e.get("c").getAsString().isEmpty()), "Spectators don't see revealed cards");
            var mine = MatchView.buildJson(m, 0, 0).getAsJsonArray("events").asList().stream()
                    .map(JsonElement::getAsJsonObject).filter(e -> e.get("t").getAsString().equals("HAND_REVEAL")).toList();
            ok(mine.stream().noneMatch(e -> e.get("c").getAsString().isEmpty()), "The looking player sees the revealed cards");
            ok(m.player(1).hand.size() == 2, "Reveal moves nothing");

            // Random steal: resolves at once, card moves to the thief.
            m = table(false);
            fill(m, 1, "skeleton");
            play(m, "test_steal_random");
            ok(m.player(1).hand.isEmpty() && hand(m, "skeleton") == 1 && m.pendingChoice() == null, "Random steal moves the card at once");

            // Steal into a full hand: the card is lost.
            m = table(false);
            fill(m, 1, "skeleton");
            // play() adds the spell to hand first, so after casting it the hand is exactly full.
            for (int i = 0; i < Match.MAX_HAND; i++) fill(m, 0, "zombie");
            play(m, "test_steal_random");
            ok(m.player(1).hand.isEmpty() && m.player(0).hand.size() == Match.MAX_HAND && hand(m, "skeleton") == 0, "Steal into a full hand loses the card");

            // Random shuffle: the card goes into the victim's deck.
            m = table(false);
            fill(m, 1, "skeleton");
            int deck = m.player(1).deck.size();
            play(m, "test_shuffle_random");
            ok(m.player(1).hand.isEmpty() && m.player(1).deck.size() == deck + 1, "Shuffle puts the card into their deck");

            // The opponent chooses, and the opponent is the Card Master: resolves at once.
            m = table(false);
            fill(m, 1, "zombie", "skeleton", "zombie");
            play(m, "test_discard_them");
            ok(m.player(1).hand.size() == 1 && m.pendingChoice() == null, "AI opponent discards its picks at once");

            // The opponent chooses and is a person: they get the picker.
            m = table(true);
            fill(m, 1, "zombie", "skeleton", "zombie");
            play(m, "test_discard_them");
            ok(m.pendingChoice() != null && m.pendingChoice().chooser() == 1 && !m.chooseFromHand(0, 0), "Human opponent picks their own discards");
            ok(m.chooseFromHand(1, 0) && m.chooseFromHand(1, 0) && m.player(1).hand.size() == 1 && m.pendingChoice() == null, "Both discards made by the opponent");

            // You choose (a human): the pick is queued and blocks the turn until made.
            m = table(false);
            fill(m, 1, "zombie", "skeleton", "zombie");
            play(m, "test_discard_you");
            ok(m.pendingChoice() != null && m.pendingChoice().chooser() == 0 && m.player(1).hand.size() == 3, "Human pick waits for a choice");
            ok(!m.yourMove(0), "No other moves while a pick is open");
            m.endTurn(0);
            ok(m.current == 0, "Ending the turn waits for the pick");
            var view = MatchView.buildJson(m, 0, 0).getAsJsonObject("choice");
            ok(view != null && view.getAsJsonArray("cards").size() == 3, "The chooser is sent the hand to pick from");
            view = MatchView.buildJson(m, 1, 0).getAsJsonObject("choice");
            ok(view != null && !view.has("cards"), "The other player is told a pick is happening, not the cards");
            ok(!m.chooseFromHand(1, 0), "Only the chooser can pick");
            ok(!m.chooseFromHand(0, 7), "Out-of-range pick is refused");
            ok(m.chooseFromHand(0, 1), "Chooser picks the second card");
            ok(m.pendingChoice() == null && m.player(1).hand.size() == 2 && m.player(1).hand.stream().noneMatch(c -> c.id().equals(id("skeleton"))),
                    "The picked card is discarded");
            ok(m.events.stream().anyMatch(e -> e.type() == MatchEvent.Type.HAND_DISCARD), "Discard event sent");
            ok(m.yourMove(0), "The turn carries on after the pick");

            // Two picks: the second one stays queued; a timeout picks at random.
            m = table(false);
            fill(m, 1, "zombie", "skeleton", "zombie");
            play(m, "test_steal_you");
            ok(m.pendingChoice() != null && m.pendingChoice().count() == 2, "Two picks owed");
            m.chooseFromHand(0, 0);
            ok(m.pendingChoice() != null && m.pendingChoice().count() == 1, "Second pick still owed");
            m.resolveChoiceRandomly();
            ok(m.pendingChoice() == null && m.player(1).hand.size() == 1 && m.player(0).hand.size() == 2, "Timeout steals a random card");

            // An empty hand: nothing to pick, nothing queued.
            m = table(false);
            play(m, "test_discard_you");
            ok(m.pendingChoice() == null && m.yourMove(0), "No pick from an empty hand");

            // Validation.
            var bad = root.deepCopy();
            spell(bad, "test_bad", "STEAL_FROM_HAND", "YOU", 1).getAsJsonArray("effects").get(0).getAsJsonObject().addProperty("target", "ENEMY_MOBS");
            ok(!parses(bad), "Hand effects must aim at an enemy hero");
            bad = root.deepCopy();
            spell(bad, "test_bad", "REVEAL_HAND", "YOU", 1);
            ok(!parses(bad), "Reveal has no chooser");
            bad = root.deepCopy();
            spell(bad, "test_bad", "DISCARD_FROM_HAND", "YOU", 11);
            ok(!parses(bad), "At most 10 cards");
            bad = root.deepCopy();
            spell(bad, "test_bad", "DISCARD_FROM_HAND", "SOMEONE", 1);
            ok(!parses(bad), "Unknown chooser rejected");
        } finally { CardCatalog.install(original); }
    }
}
