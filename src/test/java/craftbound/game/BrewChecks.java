package craftbound.game;

import craftbound.card.*;
import com.google.gson.*;
import static craftbound.game.WorkshopChecks.*;

// Brewing Stand: at the end of your turn, upgrade a potion in hand with Potency, Duration or Lingering.
final class BrewChecks {
    private static boolean parses(JsonObject root) {
        try { CardCatalog.parse(root.toString()); return true; } catch (IllegalArgumentException ex) { return false; }
    }
    private static void playFrom(Match m, int side, CardRef ref, Target target) {
        m.current = side;
        m.player(side).xp = 10;
        m.player(side).hand.add(ref);
        m.playCard(side, m.player(side).hand.size() - 1, target);
    }

    static void run() {
        var original = CardCatalog.current();
        var root = JsonParser.parseString(original.json()).getAsJsonObject();
        var cards = root.getAsJsonArray("cards");
        var harm = newEffect("PLAY", "DAMAGE", "CHOSEN_ENEMY");
        harm.addProperty("amount", 2);
        fixture(cards, "bone_meal", "test_harming", harm).addProperty("potion", true);
        var heal = newEffect("PLAY", "HEAL", "CHOSEN_FRIENDLY");
        heal.addProperty("amount", 2);
        fixture(cards, "bone_meal", "test_healing", heal).addProperty("potion", true);
        var swift = newEffect("PLAY", "GRANT_KEYWORD", "CHOSEN_FRIENDLY");
        swift.addProperty("keyword", "SPRINT");
        swift.addProperty("boardKind", "MOB");
        fixture(cards, "bone_meal", "test_swiftness", swift).addProperty("potion", true);
        var insight = newEffect("PLAY", "DRAW", "SELF");
        insight.addProperty("amount", 1);
        fixture(cards, "bone_meal", "test_insight", insight).addProperty("potion", true);
        var plain = newEffect("PLAY", "DAMAGE", "CHOSEN_ENEMY");
        plain.addProperty("amount", 2);
        fixture(cards, "bone_meal", "test_not_potion", plain);
        var stand = SurvivingKillChecks.unit(root, "test_stand", 0, 4, true);
        var standEffects = new JsonArray();
        standEffects.add(newEffect("END_TURN", "BREW_POTION", "SELF"));
        stand.add("effects", standEffects);
        stand.addProperty("autoText", true);
        SurvivingKillChecks.unit(root, "test_dummy", 0, 30, false);
        var cauldron = SurvivingKillChecks.unit(root, "test_cauldron", 0, 4, true);
        var pick = newEffect("END_TURN", "PICK_ONE", "SELF");
        var pool = new JsonArray();
        pool.add("craftbound:test_harming");
        pool.add("craftbound:test_healing");
        pick.add("pool", pool);
        var cauldronEffects = new JsonArray();
        cauldronEffects.add(pick);
        cauldron.add("effects", cauldronEffects);
        cauldron.addProperty("autoText", true);
        try {
            CardCatalog.install(CardCatalog.parse(root.toString()));
            var harming = CardCatalog.entry(id("test_harming"));
            ok(harming.potion() && !CardCatalog.entry(id("test_not_potion")).potion(), "Potion flag parses");
            ok(CardCatalog.description(CardCatalog.entry(id("test_stand"))).contains("Brew a potion in your hand"), "Brewing Stand text");
            ok(Brew.options(harming, Brew.NONE).size() == 3, "A fresh damage potion can take all three upgrades");
            ok(Brew.options(CardCatalog.entry(id("test_not_potion")), Brew.NONE).isEmpty(), "Only potions can be brewed");
            var swiftOptions = Brew.options(CardCatalog.entry(id("test_swiftness")), Brew.NONE);
            ok(swiftOptions.equals(java.util.List.of(Brew.Upgrade.LINGERING)), "Swiftness only offers Lingering (no numbers, Sprint doesn't stack): " + swiftOptions);
            ok(Brew.options(CardCatalog.entry(id("test_swiftness")), new Brew(false, false, true)).isEmpty(), "A Lingering Swiftness can't be brewed further");
            var insightOptions = Brew.options(CardCatalog.entry(id("test_insight")), Brew.NONE);
            ok(insightOptions.equals(java.util.List.of(Brew.Upgrade.POTENCY, Brew.Upgrade.DURATION)), "A potion with no target doesn't offer Lingering: " + insightOptions);
            var all = new Brew(true, true, true);
            ok(Brew.options(harming, all).isEmpty(), "Each upgrade is added once");
            ok(Brew.fromBits(all.bits()).equals(all) && Brew.fromBits(0) == Brew.NONE, "Brew bits round trip");
            String text = new Brew(true, false, false).describe(harming);
            ok(text.startsWith("Brewed: Potency.") && text.contains("Deal 3 damage"), "Potency text: " + text);
            ok(new Brew(false, true, false).describe(harming).endsWith("It happens again at the start of your next turn."), "Duration text");
            ok(new Brew(false, false, true).describe(harming).contains("enemy mobs"), "Lingering text: " + new Brew(false, false, true).describe(harming));

            var potion = new CardRef(id("test_harming"), false);
            // Potency: 2 damage becomes 3.
            var m = match();
            var dummy = mob(m, 1, "test_dummy");
            playFrom(m, 0, potion.brewed(new Brew(true, false, false)), Target.mob(1, dummy.id));
            ok(dummy.health == 27, "Potency adds 1 damage: " + dummy.health);
            // Lingering: no target needed, every enemy mob is hit.
            m = match();
            var a = mob(m, 1, "test_dummy");
            var b = mob(m, 1, "test_dummy");
            var lingering = potion.brewed(new Brew(false, false, true));
            ok(CardEffects.targetKind(lingering) == Target.Kind.NONE && CardEffects.targetKind(potion) != Target.Kind.NONE, "Lingering needs no target");
            playFrom(m, 0, lingering, Target.NONE);
            ok(a.health == 28 && b.health == 28, "Lingering hits every enemy mob: " + a.health + "/" + b.health);
            // Duration: happens again at the start of your next turn, on the same target.
            m = match();
            dummy = mob(m, 1, "test_dummy");
            playFrom(m, 0, potion.brewed(new Brew(false, true, false)), Target.mob(1, dummy.id));
            ok(dummy.health == 28 && m.player(0).brewing.size() == 1, "Duration potion hits once when played");
            m.beginTurnFor(0, false);
            ok(dummy.health == 26 && m.player(0).brewing.isEmpty(), "Duration repeats once at the next turn start: " + dummy.health);

            // Potions with lasting effects mark the mobs they changed, with their brew; instant ones don't.
            m = match();
            var friend = mob(m, 0, "test_dummy");
            var other = mob(m, 0, "test_dummy");
            playFrom(m, 0, new CardRef(id("test_swiftness"), false).brewed(new Brew(false, false, true)), Target.NONE);
            ok(friend.activePotions().size() == 1 && other.activePotions().size() == 1
                    && friend.activePotions().getFirst().id().equals("craftbound:test_swiftness") && friend.activePotions().getFirst().brew() == 4,
                    "A Lingering Swiftness marks every mob it reached, with its brew");
            friend.health = 25;
            int marks = friend.activePotions().size();
            playFrom(m, 0, new CardRef(id("test_healing"), false), Target.mob(0, friend.id));
            ok(friend.activePotions().size() == marks, "An instant potion (Healing) leaves no mark");
            m.clearAppliedEffects(friend);
            ok(friend.activePotions().isEmpty(), "Cleanse removes potion marks");

            // A human picks the potion, then the upgrade. The coin toss decides seats.
            m = match();
            int me = m.player(0).isAi() ? 1 : 0, ai = 1 - me;
            var standRef = new CardRef(id("test_stand"), false);
            m.player(me).hand.add(potion);
            m.player(me).hand.add(new CardRef(id("test_not_potion"), false));
            m.brewPotion(me, standRef);
            var choice = m.pendingChoice();
            ok(choice != null && choice.count() == 2 && m.choiceCards(choice).size() == 3, "One potion skips straight to the upgrade pick");
            int lingerIndex = -1;
            for (int i = 0; i < m.choiceCards(choice).size(); i++) if (m.choiceCards(choice).get(i).brew().lingering()) lingerIndex = i;
            ok(m.chooseFromHand(me, lingerIndex), "Upgrade pick accepted");
            ok(m.player(me).hand.getFirst().brew().lingering() && m.pendingChoice() == null, "Picked upgrade is on the hand card");
            ok(m.events.stream().anyMatch(e -> e.type() == MatchEvent.Type.BREW), "Brewing sends a BREW event");
            m.player(me).hand.add(new CardRef(id("test_healing"), false));
            m.brewPotion(me, standRef);
            choice = m.pendingChoice();
            ok(choice != null && choice.count() == 1 && m.choiceCards(choice).size() == 2, "Two potions: pick the potion first");
            m.chooseFromHand(me, 1);
            choice = m.pendingChoice();
            ok(choice != null && choice.count() == 2 && choice.source().id().equals(id("test_healing")), "Then pick that potion's upgrade");
            m.resolveChoiceRandomly();
            ok(!m.player(me).hand.get(2).brew().none(), "Time-out brews at random");
            // The computer opponent brews at random straight away.
            m.player(ai).hand.add(potion);
            m.brewPotion(ai, standRef);
            ok(!m.player(ai).hand.getFirst().brew().none() && m.pendingChoice() == null, "AI brews without a pick");

            // The Brewing Stand waits for the Cauldron's pick, even when the stand is placed first.
            m = match();
            me = m.player(0).isAi() ? 1 : 0; ai = 1 - me;
            m.player(me).hand.clear();
            mob(m, me, "test_stand");
            mob(m, me, "test_cauldron");
            CardEffects.onEndTurn(m, me);
            choice = m.pendingChoice();
            ok(choice != null && choice.pickOne(), "The Cauldron pick comes first");
            m.chooseFromHand(me, 0);
            choice = m.pendingChoice();
            ok(choice != null && choice.brewing() && choice.count() == 2 && choice.source().id().equals(id("test_harming")),
                    "Then the Brewing Stand offers upgrades for the picked potion");
            m.chooseFromHand(me, 0);
            ok(!m.player(me).hand.getLast().brew().none() && m.pendingChoice() == null, "The picked potion is brewed");
            m.player(ai).hand.clear();
            mob(m, ai, "test_stand");
            mob(m, ai, "test_cauldron");
            CardEffects.onEndTurn(m, ai);
            ok(m.player(ai).hand.size() == 1 && !m.player(ai).hand.getFirst().brew().none(), "The AI brews its Cauldron potion too");

            var bad = root.deepCopy();
            var badPotion = SurvivingKillChecks.unit(bad, "test_bad_potion", 1, 1, false);
            badPotion.addProperty("potion", true);
            ok(!parses(bad), "Only Actions can be potions");
        } finally { CardCatalog.install(original); }
    }
}
