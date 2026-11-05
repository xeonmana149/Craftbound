package craftbound.game;

import com.google.gson.*;
import craftbound.card.*;
import java.util.*;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import static craftbound.game.WorkshopChecks.*;

final class TrialsOpponentChecks {
    static void run() throws Exception {
        JsonArray bundled;
        try(var input=TrialsOpponentChecks.class.getResourceAsStream("/craftbound/trials-opponents.json")){
            bundled=JsonParser.parseString(new String(Objects.requireNonNull(input).readAllBytes(),java.nio.charset.StandardCharsets.UTF_8)).getAsJsonArray();
        }
        var all=TrialsOpponents.parse(bundled);
        var invalid=bundled.deepCopy();invalid.get(0).getAsJsonObject().addProperty("health",0);
        boolean refused=false;
        try{TrialsOpponents.parse(invalid);}catch(IllegalArgumentException e){refused=e.getMessage().contains("opponents[0]")&&e.getMessage().contains("health");}
        ok(refused,"Opponent editor errors identify row and invalid health");
        for(var f:all) {
            int stage=f.tier()==TrialsOpponents.Tier.BOSS?12:f.tier()==TrialsOpponents.Tier.ELITE?6:0;
            var foe=TrialsRoute.forNode(42,stage,1,f.tier().name(),CardClass.FIGHTER,f.id());
            ok(foe.profile().id().equals(f.id()),"Authored room selects configured opponent");
            ok(foe.health()==(foe.boss()?30:foe.elite()?21:10),"Health scales from a 10-HP scout to a 30-HP final boss");
            List<CardRef> deck=TrialsRoute.deck(foe,33);
            ok(deck.size()==foe.deckSize() && deck.equals(TrialsRoute.deck(foe,33)),"Opponent has deterministic exact deck size");
            ok(deck.stream().allMatch(c->DeckRules.allowed(c.id(),foe.hero())),"Opponent deck contains only playable existing class and Neutral cards");
            if(f.id().equals("pillager_lord")) {
                ok(deck.stream().anyMatch(c->c.id().equals(id("ravager"))) && deck.stream().anyMatch(c->c.id().equals(id("pillager"))),"Raid boss brings available authored Illager package");
            }
            var human=new MatchPlayer("Challenger",UUID.randomUUID(),CardClass.FIGHTER,null,
                Collections.nCopies(10,new CardRef(Cards.ZOMBIE,false)));
            var opponent=new MatchPlayer(foe.name(),null,foe.hero(),null,deck);
            TrialsRoute.prepare(foe,opponent);
            var m=new Match(human,opponent,RandomSource.create(321));
            if(foe.boss()){
                var opener=f.anchors().stream().map(Identifier::parse).filter(id->deck.stream().anyMatch(c->c.id().equals(id)))
                    .filter(id->Cards.get(id).cost()<=3).findFirst();
                ok(opener.isEmpty() || opponent.hand.stream().anyMatch(c->c.id().equals(opener.get())),"Boss starts with an existing signature in its hand");
                ok(opponent.hand.size()+opponent.deck.size()==foe.deckSize(),"Signature opener preserves real card count");
            }
            m.mulligan(0,0);m.mulligan(1,0);
            int steps=0;
            while(!m.over && steps++<1500)MatchAI.step(m,m.current);
            ok(m.over,"Both sides can finish in real combat against "+f.id());
        }
        var boss=TrialsRoute.forNode(90,12,0,"BOSS",CardClass.TAMER,"gone_from_workshop");
        ok(boss.boss() && !boss.profile().id().equals("gone_from_workshop"),"Removed forced opponent falls back to valid tier");
        var horn=all.stream().filter(f->f.id().equals("pillager_lord")).findFirst().orElseThrow();
        var m=match();m.turn=7;m.player(1).trialsFoe=horn;m.current=1;
        var fighter=mob(m,1,"zombie");var block=mob(m,1,"furnace");int base=fighter.attack;
        TrialsOpponentRule.start(m,1);TrialsOpponentRule.start(m,1);
        var restored=MatchSnapshot.restore(MatchSnapshot.capture(m));var active=restored.player(1);
        ok(active.trialsFoe.id().equals(horn.id()) && active.trialsFoeTurns==2,"Paused boss carries rule and exact horn phase");
        TrialsOpponentRule.start(restored,1);
        ok(active.minion(fighter.id).attack==base+2 && active.minion(block.id).attack==0,"Third-turn horn buffs only existing mobs");
        restored.endTurn(1);ok(active.minion(fighter.id).attack==base,"Raid Horn expires at end of turn");
        var blast=all.stream().filter(f->f.id().equals("creeper_king")).findFirst().orElseThrow();
        m=match();m.turn=6;m.player(1).trialsFoe=blast;
        var creep=mob(m,1,"creeper");var defender=mob(m,0,"ravager");var wall=mob(m,0,"furnace");
        int hp=defender.health,wallHp=wall.health;
        TrialsOpponentRule.onDeath(m,1,creep);TrialsOpponentRule.onDeath(m,1,creep);
        ok(defender.health==hp-2 && wall.health==wallHp,"Creeper King only blasts enemy mobs once per turn");
        // Wire an actual configured child into a profile and test the death hook, including its row limit.
        var props=bundled.deepCopy();
        for(var item:props){
            var o=item.getAsJsonObject();
            if(o.get("id").getAsString().equals("elder_slime")){o.add("tribes",new JsonArray());o.getAsJsonObject("ability").addProperty("card","craftbound:vex");}
        }
        var split=TrialsOpponents.parse(props).stream().filter(f->f.id().equals("elder_slime")).findFirst().orElseThrow();
        m=match();m.turn=5;m.player(1).trialsFoe=split;var brood=mob(m,1,"zombie");
        for(int i=0;i<6;i++)mob(m,1,"villager");
        m.damage(brood,100,null);m.resolveDeaths();
        ok(m.player(1).board.size()==7 && m.player(1).board.stream().anyMatch(u->u.card.id().equals(id("vex"))),"Configured death summons only existing cards and respects row capacity");
        var child=m.player(1).board.stream().filter(u->u.card.id().equals(id("vex"))).findFirst().orElseThrow();
        m.turn++;m.damage(child,100,null);m.resolveDeaths();
        ok(m.player(1).board.size()==6,"A child token cannot seed infinite boss splits");
    }
    private TrialsOpponentChecks(){}
}