package craftbound.game;

import craftbound.card.*;
import com.google.gson.*;
import static craftbound.game.WorkshopChecks.*;

// Pick one: choose one of 2-3 listed cards (e.g. a Cauldron's potion tokens) and add it to your hand.
final class PickOneChecks {
    private static boolean parses(JsonObject root) {
        try { CardCatalog.parse(root.toString()); return true; } catch (IllegalArgumentException ex) { return false; }
    }
    private static JsonObject pick(String trigger, String... pool) {
        var e = newEffect(trigger, "PICK_ONE", "SELF");
        var list = new JsonArray();
        for (String p : pool) list.add("craftbound:" + p);
        e.add("pool", list);
        return e;
    }

    static void run() {
        var original = CardCatalog.current();
        var root = JsonParser.parseString(original.json()).getAsJsonObject();
        var cards = root.getAsJsonArray("cards");
        var cauldron = SurvivingKillChecks.unit(root, "test_cauldron", 0, 4, true);
        var effects = new JsonArray();
        effects.add(pick("END_TURN", "zombie", "wolf_pup", "bone_meal"));
        cauldron.add("effects", effects);
        cauldron.addProperty("autoText", true);
        fixture(cards, "bone_meal", "test_pick_action", pick("PLAY", "zombie", "wolf_pup"));
        try {
            CardCatalog.install(CardCatalog.parse(root.toString()));
            String text = CardCatalog.description(CardCatalog.entry(id("test_cauldron")));
            ok(text.contains("Pick one of [") && text.contains("] to add to your hand"), "Pick one rules text: " + text);

            // A human chooses: the offers wait, and the picked card goes to hand. The coin toss decides seats.
            var m = match();
            int me = m.player(0).isAi() ? 1 : 0, ai = 1 - me;
            m.current = me;
            mob(m, me, "test_cauldron");
            m.endTurn(me);
            var choice = m.pendingChoice();
            ok(choice != null && choice.action() == CardCatalog.Action.PICK_ONE && choice.chooser() == me, "End of turn offers a pick");
            ok(m.choiceCards(choice).size() == 3, "All three options are offered");
            var wanted = m.choiceCards(choice).get(1).id();
            int before = m.player(me).hand.size();
            ok(!m.chooseFromHand(ai, 0), "Only the owner can pick");
            ok(m.chooseFromHand(me, 1), "The owner picks");
            ok(m.player(me).hand.size() == before + 1 && m.player(me).hand.getLast().id().equals(wanted) && m.pendingChoice() == null, "The picked card is added to hand");

            // Time running out picks one at random.
            m = match();
            me = m.player(0).isAi() ? 1 : 0;
            m.current = me;
            m.player(me).xp = 10;
            m.player(me).hand.add(new CardRef(id("test_pick_action"), false));
            m.playCard(me, m.player(me).hand.size() - 1, Target.NONE);
            ok(m.pendingChoice() != null && m.choiceCards(m.pendingChoice()).size() == 2, "An action can offer two cards");
            before = m.player(me).hand.size();
            m.resolveChoiceRandomly();
            ok(m.player(me).hand.size() == before + 1 && m.pendingChoice() == null, "Timeout adds a random option");

            // The computer opponent picks straight away.
            m = match();
            ai = m.player(0).isAi() ? 0 : 1;
            m.current = ai;
            mob(m, ai, "test_cauldron");
            before = m.player(ai).hand.size();
            m.endTurn(ai);
            ok(m.pendingChoice() == null && m.player(ai).hand.size() >= before + 1, "The AI picks one");

            var bad = root.deepCopy();
            fixture(bad.getAsJsonArray("cards"), "bone_meal", "test_bad_pick", pick("PLAY", "zombie"));
            ok(!parses(bad), "Pick one needs at least two options");
            bad = root.deepCopy();
            fixture(bad.getAsJsonArray("cards"), "bone_meal", "test_bad_pick", pick("PLAY", "zombie", "wolf_pup", "bone_meal", "wheat"));
            ok(!parses(bad), "Pick one offers at most three options");
        } finally { CardCatalog.install(original); }
    }
}
