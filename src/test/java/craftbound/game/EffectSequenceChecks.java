package craftbound.game;

import craftbound.card.*;
import com.google.gson.*;

final class EffectSequenceChecks {
    static void ok(boolean value, String message) { WorkshopChecks.ok(value, message); }
    static int event(Match m, MatchEvent.Type type) {
        for (int i=0;i<m.events.size();i++) if(m.events.get(i).type()==type) return i;
        return -1;
    }
    static JsonObject copy(JsonObject root,String source,String id,String effects) {
        var c=root.getAsJsonArray("cards").asList().stream().map(JsonElement::getAsJsonObject)
                .filter(x->x.get("id").getAsString().equals("craftbound:"+source)).findFirst().orElseThrow().deepCopy();
        c.addProperty("id","craftbound:"+id);c.addProperty("autoText",true);
        c.add("effects",JsonParser.parseString(effects));root.getAsJsonArray("cards").add(c);return c;
    }
    static void run() {
        var original=CardCatalog.current();
        try {
            var root=JsonParser.parseString(original.json()).getAsJsonObject();
            String damage="{\"trigger\":\"ON_KILL\",\"action\":\"DAMAGE\",\"target\":\"RANDOM_ENEMY\",\"boardKind\":\"BOTH\",\"type\":\"\",\"amount\":1,\"attack\":0,\"health\":0,\"card\":\"\",\"excludeSelf\":false}";
            var sword=copy(root,"crafted_sword","sequence_sword","["+damage+"]");sword.addProperty("attack",2);
            String freeze="{\"trigger\":\"AFTER_ATTACK\",\"action\":\"FREEZE\",\"target\":\"EVENT\",\"boardKind\":\"BOTH\",\"type\":\"\",\"amount\":1,\"attack\":0,\"health\":0,\"card\":\"\",\"excludeSelf\":false}";
            var snow=copy(root,"skeleton","sequence_snow","["+freeze+"]");snow.addProperty("attack",1);
            String area=damage.replace("ON_KILL","PLAY").replace("RANDOM_ENEMY","ENEMY_MOBS");
            copy(root,"arrow","sequence_area","["+area+","+area+"]");
            CardCatalog.install(CardCatalog.parse(root.toString()));
            var m=WorkshopChecks.match();WorkshopChecks.play(m,"sequence_sword");
            var victim=WorkshopChecks.mob(m,1,"wolf_pup");m.events.clear();
            m.attack(0,Match.HERO,Target.mob(1,victim.id));
            int death=event(m,MatchEvent.Type.DEATH),shot=event(m,MatchEvent.Type.SHOOT);
            ok(event(m,MatchEvent.Type.ATTACK)<event(m,MatchEvent.Type.DAMAGE)&&death>event(m,MatchEvent.Type.DAMAGE),"Attack damage precedes casualty removal");
            ok(shot>death&&m.events.get(shot+1).type()==MatchEvent.Type.DAMAGE,"Kill bonus arrow follows death and precedes bonus damage");
            ok(m.events.get(shot).target().hero()&&m.player(1).health==29,"Kill bonus chooses a living target");
            ok(m.player(0).tool==null&&event(m,MatchEvent.Type.BREAK)>shot,"Last durability triggers kill bonus before tool breaks");
            m=WorkshopChecks.match();WorkshopChecks.play(m,"sequence_sword");victim=WorkshopChecks.mob(m,1,"ravager");m.events.clear();
            m.attack(0,Match.HERO,Target.mob(1,victim.id));ok(event(m,MatchEvent.Type.SHOOT)<0,"No bonus shot without a kill");
            for(boolean hero:new boolean[]{false,true}) {
                m=WorkshopChecks.match();var golem=WorkshopChecks.mob(m,0,"sequence_snow");golem.ready=true;golem.summonedThisTurn=false;
                var friendly=WorkshopChecks.mob(m,0,"zombie");victim=WorkshopChecks.mob(m,1,"ravager");m.events.clear();
                m.attack(0,golem.id,hero?Target.hero(1):Target.mob(1,victim.id));
                ok(hero?m.player(1).frozen:victim.frozen,"After attack freezes that mob or hero");
                ok(!friendly.frozen&&!golem.frozen,"Snow Golem does not freeze friendlies");
                ok(event(m,MatchEvent.Type.STATUS)>event(m,MatchEvent.Type.DAMAGE),"Freeze is a separate beat after attack damage");
            }
            ok(CardCatalog.description(CardCatalog.entry(WorkshopChecks.id("sequence_snow"))).contains("Freeze its target"),"After-attack target wording is explicit");
            m=WorkshopChecks.match();WorkshopChecks.mob(m,1,"ravager");WorkshopChecks.mob(m,1,"ravager");m.events.clear();
            WorkshopChecks.play(m,"sequence_area");
            var types=m.events.stream().map(MatchEvent::type).filter(t->t==MatchEvent.Type.EFFECT||t==MatchEvent.Type.DAMAGE).toList();
            ok(types.equals(java.util.List.of(MatchEvent.Type.EFFECT,MatchEvent.Type.DAMAGE,MatchEvent.Type.DAMAGE,MatchEvent.Type.EFFECT,MatchEvent.Type.DAMAGE,MatchEvent.Type.DAMAGE)),"Separate effects have boundaries; targets within an area effect stay together");
        } finally { CardCatalog.install(original); }
    }
}
