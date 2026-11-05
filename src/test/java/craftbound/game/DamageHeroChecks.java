package craftbound.game;

import craftbound.card.*;
import com.google.gson.*;
import static craftbound.game.WorkshopChecks.*;

final class DamageHeroChecks {
    static void run() {
        var original=CardCatalog.current();var root=JsonParser.parseString(original.json()).getAsJsonObject();
        var snow=SurvivingKillChecks.unit(root,"test_snow_golem",1,4,false);
        snow.getAsJsonArray("keywords").add("RANGED");
        var freeze=SurvivingKillChecks.effect("DAMAGE_DEALT","CHOSEN_CHARACTER",1);
        freeze.addProperty("action","FREEZE");freeze.addProperty("boardKind","MOB");snow.getAsJsonArray("effects").add(freeze);
        var legacy=SurvivingKillChecks.unit(root,"test_legacy_freezer",1,4,false);
        var old=freeze.deepCopy();old.addProperty("target","CHOSEN_ANY");legacy.getAsJsonArray("effects").add(old);
        SurvivingKillChecks.unit(root,"test_freeze_target",1,4,false);
        SurvivingKillChecks.unit(root,"test_freeze_block",0,4,true);
        var tool=AuraAndClassCardChecks.copy(root,"iron_sword","test_freeze_tool");tool.getAsJsonArray("effects").add(freeze.deepCopy());
        try {
            CardCatalog.install(CardCatalog.parse(root.toString()));
            ok(CardCatalog.description(CardCatalog.entry(id("test_snow_golem"))).contains("After this deals damage: Freeze the damaged mob or hero."),"Snow Golem precise generated wording");
            ok(CardEffects.targetKind(id("test_snow_golem"))==Target.Kind.NONE,"Damage trigger does not demand a target when playing Snow Golem");
            var m=match();var a=SurvivingKillChecks.ready(m,0,"test_snow_golem");m.attack(0,a.id,Target.hero(1));
            ok(m.player(1).health==Match.START_HEALTH-1&&m.player(1).frozen&&!m.player(0).frozen,"Snow Golem damages and freezes only enemy hero");
            ok(m.events.stream().anyMatch(e->e.type()==MatchEvent.Type.STATUS&&e.mid()==Match.HERO&&e.side()==1&&e.amount()==MatchEvent.STATUS_FROZEN),"Hero Freeze uses normal visible status event");
            a.ready=true;m.attack(0,a.id,Target.hero(1));
            ok(m.events.stream().filter(e->e.type()==MatchEvent.Type.STATUS&&e.mid()==Match.HERO&&e.amount()==MatchEvent.STATUS_FROZEN).count()==1,"Already frozen hero does not repeat Freeze effects");
            m.player(1).temporaryHeroAttack=2;m.endTurn(0);ok(!m.heroCanAttack(1)&&m.player(1).skippedAttackFromFreeze,"Freeze blocks hero's next attack opportunity");
            m=match();a=SurvivingKillChecks.ready(m,0,"test_snow_golem");var victim=mob(m,1,"test_freeze_target");m.attack(0,a.id,Target.mob(1,victim.id));
            ok(victim.health==3&&victim.frozen&&a.health==3,"Ranged hit freezes living mob and takes retaliation");
            m=match();a=SurvivingKillChecks.ready(m,0,"test_snow_golem");victim=mob(m,1,"test_freeze_target");victim.invulnerable=true;m.attack(0,a.id,Target.mob(1,victim.id));
            ok(!victim.frozen&&victim.health==4,"Prevented damage does not Freeze");
            m=match();a=SurvivingKillChecks.ready(m,0,"test_snow_golem");victim=mob(m,1,"test_freeze_block");m.attack(0,a.id,Target.mob(1,victim.id));
            ok(victim.health==3&&!victim.frozen,"Mobs-only board filter excludes blocks while retaining heroes");
            m=match();a=SurvivingKillChecks.ready(m,0,"test_legacy_freezer");m.attack(0,a.id,Target.hero(1));
            ok(!m.player(1).frozen,"Legacy board-only target stays board-only");
            m=match();m.equip(0,new CardRef(id("test_freeze_tool"),false));m.attack(0,Match.HERO,Target.hero(1));
            ok(m.player(1).frozen,"Equipped tool damage trigger can Freeze hero too");
            freeze.addProperty("action","WITHER");boolean rejected=false;
            try{CardCatalog.parse(root.toString());}catch(IllegalArgumentException ex){rejected=true;}
            ok(rejected,"Wither cannot use explicit mob-or-hero targeting");
        } finally { CardCatalog.install(original); }
    }
}
