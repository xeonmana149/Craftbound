package craftbound.game;

import craftbound.card.*;
import com.google.gson.*;
import static craftbound.game.WorkshopChecks.*;

final class SurvivingKillChecks {
    static JsonObject effect(String trigger, String target, int amount) {
        return JsonParser.parseString("{\"trigger\":\""+trigger+"\",\"action\":\"DAMAGE\",\"target\":\""+target+"\",\"boardKind\":\"BOTH\",\"type\":\"\",\"amount\":"+amount+",\"attack\":0,\"health\":0,\"card\":\"\",\"excludeSelf\":false}").getAsJsonObject();
    }
    static JsonObject unit(JsonObject root,String name,int attack,int health,boolean block) {
        var c=AuraAndClassCardChecks.copy(root,"zombie",name);
        c.addProperty("attack",attack);c.addProperty("health",health);
        c.add("keywords",JsonParser.parseString(block?"[\"BLOCK\"]":"[]"));return c;
    }
    static Minion ready(Match m,int side,String name) {
        var c=mob(m,side,name);c.ready=true;c.summonedThisTurn=false;return c;
    }
    static void run() {
        var original=CardCatalog.current();var root=JsonParser.parseString(original.json()).getAsJsonObject();
        var ravager=unit(root,"test_ravager",7,7,false);
        ravager.getAsJsonArray("effects").add(effect("AFTER_SURVIVING_KILL","ENEMY_HERO",2));
        unit(root,"test_small",1,2,false);unit(root,"test_large",1,8,false);unit(root,"test_lethal",7,2,false);
        unit(root,"test_wall",0,2,true);
        var thorns=unit(root,"test_thorns",0,2,true);thorns.getAsJsonArray("effects").add(effect("ATTACKED","CHOSEN_ANY",7));
        var blast=unit(root,"test_death_blast",0,2,true);blast.getAsJsonArray("effects").add(effect("DROP","ENEMY_MOBS",7));
        var indirect=unit(root,"test_indirect",7,7,false);
        indirect.getAsJsonArray("effects").add(effect("DAMAGE_DEALT","CHOSEN_ANY",9));
        indirect.getAsJsonArray("effects").add(effect("AFTER_SURVIVING_KILL","ENEMY_HERO",2));
        try {
            CardCatalog.install(CardCatalog.parse(root.toString()));
            String wording="After this attacks and destroys a mob or block, if it survives: Deal 2 damage to the enemy hero.";
            ok(CardCatalog.description(CardCatalog.entry(id("test_ravager"))).equals(wording),"Surviving kill generated text");
            ok(Cards.styledText(wording).getSiblings().stream().anyMatch(c->c.getString().contains("if it survives")&&c.getStyle().isBold()),"Surviving kill timing is bold");
            for(String victim:new String[]{"test_small","test_wall","test_large","test_lethal","test_thorns","test_death_blast"}) {
                var m=match();var a=ready(m,0,"test_ravager");var d=mob(m,1,victim);
                m.attack(0,a.id,Target.mob(1,d.id));
                boolean qualifies=victim.equals("test_small")||victim.equals("test_wall");
                ok(m.player(1).health==Match.START_HEALTH-(qualifies?2:0),"Surviving kill against "+victim);
                if(qualifies) {
                    int death=-1,payoff=-1;
                    for(int i=0;i<m.events.size();i++){var e=m.events.get(i);if(e.type()==MatchEvent.Type.DEATH&&e.mid()==d.id)death=i;if(e.type()==MatchEvent.Type.DAMAGE&&e.side()==1&&e.mid()==Match.HERO)payoff=i;}
                    ok(payoff>death,"Payoff waits for death resolution");
                }
            }
            var m=match();var a=ready(m,0,"test_ravager");m.attack(0,a.id,Target.hero(1));
            ok(m.player(1).health==Match.START_HEALTH-7,"Hero hit does not trigger payoff");
            m=match();a=mob(m,1,"test_ravager");var d=ready(m,0,"test_small");m.attack(0,d.id,Target.mob(1,a.id));
            ok(m.player(0).health==Match.START_HEALTH,"Defensive retaliation kill does not trigger payoff");
            m=match();a=ready(m,0,"test_ravager");d=mob(m,1,"test_small");d.invulnerable=true;m.attack(0,a.id,Target.mob(1,d.id));
            ok(m.player(1).health==Match.START_HEALTH,"Invulnerable target does not count as destroyed");
            m=match();a=ready(m,0,"test_ravager");a.totem=true;d=mob(m,1,"test_lethal");m.attack(0,a.id,Target.mob(1,d.id));
            ok(m.player(0).board.size()==1&&m.player(0).board.getFirst()!=a&&m.player(1).health==Match.START_HEALTH,"Resurrection is not survival");
            m=match();a=ready(m,0,"test_ravager");d=mob(m,1,"test_small");d.totem=true;m.attack(0,a.id,Target.mob(1,d.id));
            ok(m.player(1).board.size()==1&&m.player(1).health==Match.START_HEALTH-2,"Destroyed target reviving does not undo the original kill");
            m=match();a=ready(m,0,"test_indirect");d=mob(m,1,"test_large");m.attack(0,a.id,Target.mob(1,d.id));
            ok(d.isDead()&&m.player(1).health==Match.START_HEALTH,"Triggered ability kill is not an attack kill");
            m=match();a=ready(m,0,"test_ravager");a.grantedKeywords.add(Keyword.RANGED);d=mob(m,1,"test_lethal");m.attack(0,a.id,Target.mob(1,d.id));
            ok(a.isDead()&&m.player(1).health==Match.START_HEALTH,"Ranged attacker killed by retaliation cannot trigger surviving kill");
            ravager.getAsJsonArray("keywords").add("BLOCK");ravager.addProperty("attack",0);
            boolean rejected=false;try{CardCatalog.parse(root.toString());}catch(IllegalArgumentException ex){rejected=true;}
            ok(rejected,"Block cannot author attack-survival trigger");
        } finally { CardCatalog.install(original); }
    }
}
