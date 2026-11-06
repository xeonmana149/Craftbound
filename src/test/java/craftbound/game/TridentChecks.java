package craftbound.game;

import craftbound.card.*;
import com.google.gson.*;
import static craftbound.game.WorkshopChecks.*;

// Trident: a Throw tool sticks in the mob it hits and comes back when that mob leaves the board.
// Loyalty pulls it back at the start of your turn; Channeling strikes the mob and its neighbours.
final class TridentChecks {
    private static boolean parses(JsonObject root) {
        try { CardCatalog.parse(root.toString()); return true; } catch (IllegalArgumentException ex) { return false; }
    }
    private static void enchant(Match m, int side, String name) {
        m.current = side;
        m.player(side).xp = 10;
        m.player(side).hand.add(new CardRef(id(name), false));
        m.playCard(side, m.player(side).hand.size() - 1, Target.NONE);
    }

    private static void enchantment(JsonObject root, String name, String keyword) {
        var card = AuraAndClassCardChecks.copy(root, "iron_sword", name);
        card.addProperty("kind", "ENCHANTMENT");
        card.addProperty("class", "FIGHTER");
        card.addProperty("attack", 0);
        card.addProperty("health", 0);
        card.add("keywords", JsonParser.parseString("[\"" + keyword + "\"]"));
    }

    static void run() {
        var original = CardCatalog.current();
        var root = JsonParser.parseString(original.json()).getAsJsonObject();
        var trident = AuraAndClassCardChecks.copy(root, "iron_sword", "test_trident");
        trident.addProperty("attack", 3);
        trident.addProperty("health", 3);
        trident.add("keywords", JsonParser.parseString("[\"THROW\"]"));
        AuraAndClassCardChecks.copy(root, "iron_sword", "test_sword");
        enchantment(root, "test_loyalty", "LOYALTY");
        enchantment(root, "test_channeling", "CHANNELING");
        SurvivingKillChecks.unit(root, "test_target", 4, 10, false);
        SurvivingKillChecks.unit(root, "test_frail", 4, 2, false);
        try {
            CardCatalog.install(CardCatalog.parse(root.toString()));
            var spear = new CardRef(id("test_trident"), false);

            // Thrown: no hit back, it sticks, and the hero is unarmed.
            var m = match();
            m.equip(0, spear);
            var target = mob(m, 1, "test_target");
            int heroHealth = m.player(0).health;
            ok(m.whyCantAttack(0, Match.HERO, Target.hero(1)) != null, "A Trident can't be thrown at a hero");
            m.attack(0, Match.HERO, Target.mob(1, target.id));
            ok(target.health == 7 && m.player(0).health == heroHealth, "A throw deals the tool's Attack and nothing hits back");
            ok(m.player(0).tool == null && target.impaled != null && target.impaled.uses == 2, "The Trident sticks in the mob, minus 1 Durability");
            ok(m.events.stream().anyMatch(e -> e.type() == MatchEvent.Type.IMPALE && e.amount() == 1), "Sticking sends IMPALE");
            m.beginTurnFor(0, false);
            ok(target.impaled != null && m.player(0).tool == null, "Without Loyalty it stays in the mob");
            target.health = 0;
            m.resolveDeaths();
            ok(m.player(0).tool != null && m.player(0).tool.card.id().equals(id("test_trident")) && m.player(0).tool.uses == 2,
                    "When the mob dies the Trident returns to the hero");

            // A mob killed by the throw doesn't keep it.
            m = match();
            m.equip(0, spear);
            var frail = mob(m, 1, "test_frail");
            m.attack(0, Match.HERO, Target.mob(1, frail.id));
            ok(m.player(0).tool != null && !m.player(1).board.contains(frail), "Killing the mob keeps the Trident in hand");

            // Holding another tool: it comes back to hand instead. Bounce also frees it.
            m = match();
            m.equip(0, spear);
            target = mob(m, 1, "test_target");
            m.attack(0, Match.HERO, Target.mob(1, target.id));
            m.equip(0, new CardRef(id("test_sword"), false));
            int handBefore = m.player(0).hand.size();
            m.bounce(target);
            ok(m.player(0).tool.card.id().equals(id("test_sword")) && m.player(0).hand.size() == handBefore + 1
                    && m.player(0).hand.getLast().id().equals(id("test_trident")), "Returned while armed: the Trident goes to hand");

            // Loyalty: back at the start of your next turn.
            m = match();
            m.equip(0, spear);
            enchant(m, 0, "test_loyalty");
            ok(m.player(0).tool != null && m.player(0).tool.enchantments.size() == 1, "Loyalty attaches to the Trident");
            target = mob(m, 1, "test_target");
            m.attack(0, Match.HERO, Target.mob(1, target.id));
            ok(m.player(0).tool == null, "Thrown");
            m.beginTurnFor(0, false);
            ok(m.player(0).tool != null && target.impaled == null && m.player(0).tool.enchantments.size() == 1, "Loyalty brings it back, still enchanted");

            // Channeling: the stuck mob and its left and right neighbours take 2.
            m = match();
            m.equip(0, spear);
            enchant(m, 0, "test_channeling");
            var left = mob(m, 1, "test_target");
            var middle = mob(m, 1, "test_target");
            var right = mob(m, 1, "test_target");
            var far = mob(m, 1, "test_target");
            m.attack(0, Match.HERO, Target.mob(1, middle.id));
            m.beginTurnFor(0, false);
            ok(left.health == 8 && middle.health == 5 && right.health == 8 && far.health == 10,
                    "Channeling hits the stuck mob and both neighbours: " + left.health + "/" + middle.health + "/" + right.health + "/" + far.health);
            ok(m.events.stream().anyMatch(e -> e.type() == MatchEvent.Type.CHANNEL), "Channeling sends CHANNEL");
            ok(middle.impaled != null, "Channeling alone doesn't bring the Trident back");

            var bad = root.deepCopy();
            SurvivingKillChecks.unit(bad, "test_bad_throw", 1, 1, false).add("keywords", JsonParser.parseString("[\"THROW\"]"));
            ok(!parses(bad), "Throw is tool-only");
            bad = root.deepCopy();
            AuraAndClassCardChecks.copy(bad, "iron_sword", "test_bad_loyal").add("keywords", JsonParser.parseString("[\"LOYALTY\"]"));
            ok(!parses(bad), "Loyalty is enchantment-only");
        } finally { CardCatalog.install(original); }
    }
}
