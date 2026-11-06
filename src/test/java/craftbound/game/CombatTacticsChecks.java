package craftbound.game;

import craftbound.card.Keyword;
import static craftbound.game.WorkshopChecks.*;

final class CombatTacticsChecks {
    private static Match duel(int health) {
        Match m=match();
        m.player(0).health=health;
        m.player(0).xp=0;
        return m;
    }
    private static Minion unit(Match m,int side,int attack,int health) {
        Minion mob=mob(m,side,"villager");
        mob.attack=attack;
        mob.maxHealth=mob.health=health;
        mob.ready=true;
        mob.summonedThisTurn=false;
        return mob;
    }

    static void run() {
        Match m=duel(5);
        Minion mine=unit(m,0,2,2), threat=unit(m,1,5,2);
        // It attacked last turn. It still threatens five when its owner's turn refreshes.
        threat.ready=false;
        int face=m.player(1).health;
        ok(MatchAI.step(m,0) && mine.isDead() && threat.isDead() && m.player(1).health==face,"Sacrifice to stop exact board lethal");
        m=duel(6);
        unit(m,0,2,1);unit(m,0,3,1);
        threat=unit(m,1,6,5);threat.summonedThisTurn=true;threat.ready=false;
        MatchAI.step(m,0);
        ok(threat.health<5 && !threat.isDead() && m.player(1).health==face,"Start a combined kill against a fresh mob that will be ready next turn");
        MatchAI.step(m,0);
        ok(threat.isDead() && m.player(1).health==face,"Finish the combined trade before hitting face");

        m=duel(7);
        unit(m,0,3,2);unit(m,0,3,2);
        Minion giant=unit(m,1,6,6), small=unit(m,1,4,3), second=unit(m,1,4,3);
        MatchAI.step(m,0);MatchAI.step(m,0);
        ok(!giant.isDead() && small.isDead() && second.isDead(),"Two separate kills escape lethal when combining into the largest mob would lose");

        m=duel(3);
        unit(m,0,2,3);unit(m,0,3,3);threat=unit(m,1,7,2);m.player(1).health=5;
        MatchAI.step(m,0);MatchAI.step(m,0);
        ok(m.over && m.winner==0 && !threat.isDead(),"Take combined face lethal instead of trading into a threat");

        m=duel(5);mine=unit(m,0,2,2);threat=unit(m,1,6,2);threat.frozen=true;
        MatchAI.step(m,0);
        ok(m.player(1).health==face-2 && !mine.isDead(),"A frozen attacker does not force a defensive trade");
        m=duel(5);mine=unit(m,0,2,2);threat=unit(m,1,6,1);threat.withered=true;
        MatchAI.step(m,0);
        ok(m.player(1).health==face-2 && !mine.isDead(),"A mob that will die to Wither is not counted as face damage");

        m=duel(5);unit(m,0,2,2);threat=unit(m,1,3,2);
        m.equip(1,new CardRef(id("iron_sword"),false));
        MatchAI.step(m,0);
        ok(threat.isDead() && m.player(1).health==face,"An equipped enemy tool contributes to projected lethal");
        m=duel(5);unit(m,0,2,2);threat=unit(m,1,3,2);
        m.equip(1,new CardRef(id("iron_sword"),false));m.player(1).frozen=true;
        MatchAI.step(m,0);
        ok(threat.isDead() && m.player(1).health==face,"A favorable trade can still be correct without weapon lethal");

        m=duel(5);Minion guard=unit(m,0,0,2);guard.grantedKeywords.add(Keyword.SHIELD);
        unit(m,0,6,1);threat=unit(m,1,6,2);
        MatchAI.step(m,0);
        ok(!threat.isDead() && m.player(1).health==face-6,"A lone attacker must spend its swing on Shield");
        m=duel(5);guard=unit(m,0,0,2);guard.grantedKeywords.add(Keyword.SHIELD);
        unit(m,0,2,1);threat=unit(m,1,6,2);threat.grantedKeywords.add(Keyword.IGNORE_SHIELD);
        MatchAI.step(m,0);
        ok(threat.isDead(),"Ignore Shield can still threaten lethal through the front line");
        m=duel(5);guard=unit(m,0,0,2);guard.grantedKeywords.add(Keyword.SHIELD);
        unit(m,0,2,1);unit(m,1,2,4);threat=unit(m,1,5,2);
        MatchAI.step(m,0);
        ok(threat.isDead(),"Enemy can break Shield first and send its remaining attacker face");

        m=duel(5);unit(m,0,2,1);unit(m,0,2,1);Minion armor=mob(m,1,"piglin_brute");
        armor.health=3;armor.attack=5;
        MatchAI.step(m,0);
        ok(armor.health==3 && m.player(1).health==face-2,"Do not spend two attackers on a kill that per-hit armor prevents");
        m=duel(4);m.equip(0,new CardRef(id("iron_sword"),false));threat=unit(m,1,7,2);
        MatchAI.step(m,0);
        ok(!m.over && m.player(0).health==4,"Do not trade the hero into fatal retaliation");

        m=duel(30);
        Minion champion=unit(m,0,6,5), recruit=unit(m,0,2,1);
        threat=unit(m,1,5,2);
        MatchAI.step(m,0);
        ok(threat.isDead() && recruit.isDead() && champion.ready,"Use the cheap body to trade up before lethal threatens");

        m=duel(30);
        recruit=unit(m,0,2,2);champion=unit(m,0,6,2);champion.ready=false;
        threat=unit(m,1,2,2);
        MatchAI.step(m,0);
        ok(recruit.isDead() && threat.isDead() && !champion.isDead(),"Take an even trade to protect a more valuable exhausted ally");

        m=duel(30);
        unit(m,0,2,1);unit(m,0,2,1);threat=unit(m,1,8,4);
        MatchAI.step(m,0);MatchAI.step(m,0);
        ok(threat.isDead() && m.player(1).health==face,"Combine attackers to remove a big threat while at full health");

        m=duel(30);m.player(1).health=7;
        mine=unit(m,0,5,3);unit(m,1,3,1);
        MatchAI.step(m,0);
        ok(!mine.isDead() && m.player(1).health==2,"Keep pressure instead of sacrificing a finisher to a small mob");

        m=duel(30);unit(m,0,3,4);Minion engine=mob(m,1,"dispenser");
        MatchAI.step(m,0);
        ok(engine.isDead(),"Remove a reachable repeat-damage block while there is no frontline");

        m=duel(30);m.player(0).xp=2;
        m.player(0).hand.add(new CardRef(id("furnace"),false));
        m.player(0).hand.add(new CardRef(id("arrow"),false));
        giant=unit(m,1,8,7);threat=unit(m,1,5,2);
        MatchAI.step(m,0);
        ok(threat.isDead() && giant.health==7 && m.player(0).xp==1,"Spend cheap removal to finish a threat instead of buying a block or chipping the largest mob");

        m=duel(30);m.player(0).xp=2;
        m.player(0).hand.add(new CardRef(id("furnace"),false));
        m.player(0).hand.add(new CardRef(id("villager"),false));
        unit(m,1,2,3);
        MatchAI.step(m,0);
        ok(m.player(0).board.getFirst().card.id().equals(id("villager")),"Develop a frontline before a support block when behind on board");
    }
    private CombatTacticsChecks() {}
}
