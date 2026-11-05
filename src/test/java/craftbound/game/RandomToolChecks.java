package craftbound.game;

import craftbound.card.*;
import com.google.gson.*;
import java.util.*;
import static craftbound.game.WorkshopChecks.*;

final class RandomToolChecks {
    static void run() {
        var original=CardCatalog.current();var root=JsonParser.parseString(original.json()).getAsJsonObject();
        var anvil=SurvivingKillChecks.unit(root,"test_anvil",0,4,true);
        var upgrade=SurvivingKillChecks.effect("END_TURN","SELF",1);upgrade.addProperty("action","UPGRADE_TOOL");upgrade.addProperty("attack",2);upgrade.addProperty("health",0);
        anvil.getAsJsonArray("effects").add(upgrade);
        var repair=SurvivingKillChecks.unit(root,"test_repair",0,4,true);var fix=upgrade.deepCopy();fix.addProperty("attack",0);fix.addProperty("health",3);repair.getAsJsonArray("effects").add(fix);
        var onPlay=SurvivingKillChecks.unit(root,"test_anvil_spawn",0,4,true);var spawn=upgrade.deepCopy();spawn.addProperty("trigger","PLAY");onPlay.getAsJsonArray("effects").add(spawn);
        for(String aim:List.of("RANDOM_ENEMY","RANDOM_FRIENDLY","RANDOM_MOB","RANDOM_BLOCK","RANDOM_HERO")) {
            var c=AuraAndClassCardChecks.copy(root,"arrow","test_"+aim.toLowerCase(Locale.ROOT));
            var e=SurvivingKillChecks.effect("PLAY",aim,1);
            if(aim.equals("RANDOM_MOB"))e.addProperty("boardKind","MOB");
            if(aim.equals("RANDOM_BLOCK"))e.addProperty("boardKind","BLOCK");
            c.getAsJsonArray("effects").add(e);
        }
        var buff=AuraAndClassCardChecks.copy(root,"arrow","test_random_buff");var buffEffect=upgrade.deepCopy();buffEffect.addProperty("trigger","PLAY");buffEffect.addProperty("action","BUFF");buffEffect.addProperty("target","RANDOM_FRIENDLY");buffEffect.addProperty("boardKind","MOB");buff.getAsJsonArray("effects").add(buffEffect);
        try {
            CardCatalog.install(CardCatalog.parse(root.toString()));
            ok(CardCatalog.description(CardCatalog.entry(id("test_anvil"))).equals("At the end of your turn: Give your equipped tool +2 Attack."),"Anvil omits zero Durability");
            ok(CardCatalog.description(CardCatalog.entry(id("test_repair"))).equals("At the end of your turn: Give your equipped tool +3 Durability."),"Repair omits zero Attack");
            var m=match();var a=mob(m,0,"test_anvil");m.equip(0,new CardRef(id("iron_sword"),false));int attack=m.player(0).tool.attack,uses=m.player(0).tool.uses;
            CardEffects.run(m,0,a.card,a,CardCatalog.Trigger.END_TURN,Target.NONE);
            ok(m.player(0).tool.attack==attack+2&&m.player(0).tool.uses==uses,"Block adds only tool Attack");
            ok(m.player(0).tool.enchantments.isEmpty()&&m.player(0).tool.triggerEnchantments.isEmpty(),"Tool improvement does not add an Enchantment");
            ok(m.events.stream().anyMatch(e->e.type()==MatchEvent.Type.TOOL_UPGRADE&&e.amount()==attack+2&&e.extra()==uses),"Tool stat update reaches client");
            a=mob(m,0,"test_repair");CardEffects.run(m,0,a.card,a,CardCatalog.Trigger.END_TURN,Target.NONE);
            ok(m.player(0).tool.attack==attack+2&&m.player(0).tool.uses==uses+3,"Block adds only tool Durability");
            m.player(0).tool=null;play(m,"test_anvil_spawn");ok(m.player(0).board.stream().anyMatch(b->b.card.id().equals(id("test_anvil_spawn"))),"Block can be played without tool");
            CardEffects.run(m,0,a.card,a,CardCatalog.Trigger.END_TURN,Target.NONE);ok(m.player(0).tool==null,"Absent tool causes no upgrade/crash");
            for(String aim:List.of("RANDOM_ENEMY","RANDOM_FRIENDLY","RANDOM_MOB","RANDOM_BLOCK","RANDOM_HERO")) {
                m=match();mob(m,0,"zombie");mob(m,0,"furnace");mob(m,1,"zombie");mob(m,1,"furnace");
                var ref=new CardRef(id("test_"+aim.toLowerCase(Locale.ROOT)),false);var effect=CardCatalog.entry(ref.id()).effects().getFirst();
                var candidates=CardEffects.possibleTargets(m,0,null,effect,Target.NONE);
                int expected=aim.equals("RANDOM_ENEMY")||aim.equals("RANDOM_FRIENDLY")?3:2;
                ok(candidates.size()==expected,"Exact eligible pool for "+aim);
                for(var target:candidates){
                    if(aim.equals("RANDOM_ENEMY"))ok(target.side()==1,"Enemy side only");
                    if(aim.equals("RANDOM_FRIENDLY"))ok(target.side()==0,"Friendly side only");
                    if(aim.equals("RANDOM_HERO"))ok(target.hero(),"Hero pool only");
                    if(aim.equals("RANDOM_MOB")||aim.equals("RANDOM_BLOCK"))ok(!target.hero()&&m.player(target.side()).minion(target.minionId()).has(Keyword.BLOCK)==aim.equals("RANDOM_BLOCK"),"Correct board kind");
                }
                var observed=new HashSet<Target>();
                for(int n=0;n<150;n++){
                    for(var p:m.players){p.health=30;for(var b:p.board)b.health=b.maxHealth;}
                    m.events.clear();CardEffects.run(m,0,ref,null,CardCatalog.Trigger.PLAY,Target.NONE);
                    var hits=m.events.stream().filter(e->e.type()==MatchEvent.Type.DAMAGE).toList();
                    ok(hits.size()==1,"Random effect picks exactly one target");var e=hits.getFirst();var hit=e.mid()==Match.HERO?Target.hero(e.side()):Target.mob(e.side(),e.mid());
                    ok(candidates.contains(hit),"Chosen target belongs to eligible pool");observed.add(hit);
                }
                ok(observed.size()==expected,"Random pool can reach every eligible target");
            }
            m=match();var ref=new CardRef(id("test_random_mob"),false);CardEffects.run(m,0,ref,null,CardCatalog.Trigger.PLAY,Target.NONE);
            ok(m.events.isEmpty(),"Empty random pool has no effect");
            var ally=mob(m,0,"zombie");mob(m,0,"furnace");mob(m,1,"zombie");
            ref=new CardRef(id("test_random_buff"),false);var e=CardCatalog.entry(ref.id()).effects().getFirst();
            ok(CardEffects.possibleTargets(m,0,null,e,Target.NONE).equals(List.of(Target.mob(0,ally.id))),"Random friendly buff excludes heroes, blocks and enemies");
            var source=new EffectPreview.Source(ref.id(),0,-1,false,0);
            ok(EffectPreview.forTarget(source,EffectPreview.Subject.hero(0,30))==null,"Hero not highlighted for random mob buff");
            var subject=new EffectPreview.Subject(EffectPreview.Kind.BOARD,ally.card.id(),0,ally.id,ally.health,ally.maxHealth,false,false,false,false);
            ok(EffectPreview.forTarget(source,subject).possible(),"Random beneficiary preview marked possible");
        } finally {CardCatalog.install(original);}
    }
}
