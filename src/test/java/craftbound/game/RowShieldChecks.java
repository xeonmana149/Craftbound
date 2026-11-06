package craftbound.game;

import craftbound.card.Keyword;
import static craftbound.game.WorkshopChecks.*;

// Row-limited Shield: a Shield block guards the hero and other blocks, not the front row,
// and still sits behind the front row itself.
final class RowShieldChecks {
    static void run() {
        var m = match();
        var front = mob(m, 1, "zombie");
        var wall = mob(m, 1, "furnace");
        var other = mob(m, 1, "furnace");
        m.grantKeyword(wall, Keyword.SHIELD);
        var melee = mob(m, 0, "zombie");
        var archer = mob(m, 0, "skeleton");
        melee.ready = archer.ready = true;
        melee.summonedThisTurn = archer.summonedThisTurn = false;
        ok(m.whyCantAttack(0, melee.id, Target.mob(1, front.id)) == null, "A Shield block doesn't guard front-row mobs");
        ok(m.whyCantAttack(0, melee.id, Target.mob(1, wall.id)) != null, "Melee still can't reach a Shield block behind the front row");
        ok(m.whyCantAttack(0, archer.id, Target.mob(1, wall.id)) == null, "Ranged can hit a Shield block behind the front row");
        ok(m.whyCantAttack(0, archer.id, Target.mob(1, other.id)) != null, "A Shield block guards the other blocks");
        ok(m.whyCantAttack(0, archer.id, Target.hero(1)) != null, "A Shield block guards the hero");
        m.grantKeyword(archer, Keyword.IGNORE_SHIELD);
        ok(m.whyCantAttack(0, archer.id, Target.hero(1)) == null, "Ignore Shield gets past a Shield block");

        m.remove(front);
        ok(m.whyCantAttack(0, melee.id, Target.mob(1, wall.id)) == null, "With the front row cleared, melee can hit the Shield block");
        ok(m.whyCantAttack(0, melee.id, Target.hero(1)) != null, "The hero stays guarded until the Shield block falls");

        // A Shield mob still guards everything, including a Shield block.
        m = match();
        var guard = mob(m, 1, "zombie");
        m.grantKeyword(guard, Keyword.SHIELD);
        wall = mob(m, 1, "furnace");
        m.grantKeyword(wall, Keyword.SHIELD);
        archer = mob(m, 0, "skeleton");
        archer.ready = true;
        archer.summonedThisTurn = false;
        ok(m.whyCantAttack(0, archer.id, Target.mob(1, wall.id)) != null, "A Shield mob must go before a Shield block");
        ok(m.whyCantAttack(0, archer.id, Target.mob(1, guard.id)) == null, "The Shield mob is a legal target");
    }
}
