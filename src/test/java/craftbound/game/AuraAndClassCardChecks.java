package craftbound.game;

import craftbound.card.*;
import com.google.gson.*;
import java.util.*;
import static craftbound.game.WorkshopChecks.*;

/** Continuous stats must survive board changes and pause/resume without becoming permanent buffs. */
final class AuraAndClassCardChecks {
    static JsonObject copy(JsonObject root, String base, String name) {
        var card = root.getAsJsonArray("cards").asList().stream().map(JsonElement::getAsJsonObject)
                .filter(c -> c.get("id").getAsString().equals("craftbound:" + base)).findFirst().orElseThrow().deepCopy();
        card.addProperty("id", "craftbound:" + name);
        card.addProperty("autoText", true);card.addProperty("text", "");
        card.addProperty("passive", "NONE");card.add("effects", new JsonArray());
        root.getAsJsonArray("cards").add(card);
        return card;
    }
    static void run() {
        var original = CardCatalog.current();
        var root = JsonParser.parseString(craftbound.test.TestCatalogue.full().json()).getAsJsonObject();
        var aura = copy(root,"zombie","test_stat_aura");
        aura.addProperty("passive","AURA_STATS");aura.addProperty("passiveAmount",1);
        aura.addProperty("auraHealth",2);aura.addProperty("auraType","UNDEAD");
        var healthAura = copy(root,"zombie","test_health_aura");
        healthAura.addProperty("passive","AURA_STATS");healthAura.addProperty("passiveAmount",0);healthAura.addProperty("auraHealth",1);
        var chest = copy(root,"furnace","test_ender_chest");
        chest.addProperty("class","NEUTRAL");
        chest.add("effects",JsonParser.parseString("[{\"trigger\":\"END_TURN\",\"action\":\"GIVE_RANDOM_CLASS\",\"target\":\"SELF\",\"boardKind\":\"BOTH\",\"type\":\"\",\"amount\":1,\"attack\":0,\"health\":0,\"card\":\"\",\"excludeSelf\":false}]"));
        try {
            CardCatalog.install(CardCatalog.parse(root.toString()));
            ok(CardCatalog.description(CardCatalog.entry(id("test_stat_aura"))).equals("Your other Undead mobs have +1 Attack and +2 Health."),"Aura wording");
            ok(CardCatalog.description(CardCatalog.entry(id("test_health_aura"))).equals("Your other mobs have +1 Health."),"Health-only wording");
            ok(CardCatalog.description(CardCatalog.entry(id("test_ender_chest"))).contains("Add a random card from your class to your hand"),"Class card wording");
            var m=match();var ally=mob(m,0,"zombie");var enemy=mob(m,1,"zombie");var block=mob(m,0,"furnace");var otherType=mob(m,0,"cow");
            int hp=ally.health, atk=ally.attack, blockHp=block.health, otherHp=otherType.health;
            var source=mob(m,0,"test_stat_aura");
            ok(ally.health==hp+2&&ally.maxHealth==hp+2&&m.attackOf(ally)==atk+1,"Aura affects existing ally");
            ok(source.health==source.definition().health()&&enemy.health==hp&&block.health==blockHp&&otherType.health==otherHp,"No self, enemy, Block or wrong type aura");
            var late=mob(m,0,"zombie");ok(late.health==hp+2,"Aura affects later summons");
            int events=m.events.size();m.refreshAuras();m.refreshAuras();ok(m.events.size()==events&&ally.health==hp+2,"Refresh is idempotent");
            var subject=new EffectPreview.Subject(EffectPreview.Kind.BOARD,ally.card.id(),0,ally.id,ally.health,ally.maxHealth,false,false,false,false);
            ok(EffectPreview.forTarget(new EffectPreview.Source(source.card.id(),0,source.id,true,0),subject)!=null,"Aura highlights beneficiaries");
            m.damage(ally,3,null);int damaged=ally.health;
            m.bounce(source);ok(ally.health==damaged&&ally.maxHealth==hp&&m.attackOf(ally)==atk&&late.health==hp,"Aura removal caps Health without damaging injured ally");
            m.heal(Target.mob(0,ally.id),99,false);ok(ally.health==hp,"Healing uses reduced maximum");
            source=mob(m,0,"test_stat_aura");m.buff(ally,2,3);m.remove(source);
            ok(ally.maxHealth==hp+3&&m.attackOf(ally)==atk+2,"Permanent buff survives aura removal");

            m=match();ally=mob(m,0,"zombie");source=mob(m,0,"test_stat_aura");var second=mob(m,0,"test_stat_aura");
            ok(ally.health==hp+4&&source.auraHealth==2&&second.auraHealth==2,"Stacked sources buff each other but not themselves");
            m.damage(ally,1,null);m=MatchSnapshot.restore(MatchSnapshot.capture(m));
            ally=m.player(0).board.get(0);source=m.player(0).board.get(1);second=m.player(0).board.get(2);
            ok(ally.health==hp+3&&ally.maxHealth==hp+4,"Pause restore does not heal or duplicate aura");
            m.damage(source,100,null);m.resolveDeaths();ok(ally.maxHealth==hp+2&&second.auraHealth==0,"Source death removes one layer");
            m.transform(second,id("cow"));ok(ally.maxHealth==hp&&m.attackOf(ally)==atk,"Transforming source removes aura");
            source=mob(m,0,"test_stat_aura");m.transform(ally,id("cow"));
            ok(m.player(0).minion(ally.id).auraHealth==0,"Transformed recipient loses type eligibility");
            var dying=mob(m,0,"zombie");m.damage(dying,100,null);mob(m,0,"test_health_aura");
            ok(dying.isDead(),"New aura never resurrects a dead mob");m.resolveDeaths();

            m=match();ally=mob(m,0,"zombie");ally.attack=0;ally.ready=true;ally.summonedThisTurn=false;source=mob(m,0,"test_stat_aura");
            ok(m.whyCantAttack(0,ally.id,Target.hero(1))==null,"Zero-base Attack mob can attack using aura");
            m.remove(source);ok(m.whyCantAttack(0,ally.id,Target.hero(1))!=null,"Losing last Attack disables attack");
            source=mob(m,0,"test_health_aura");ok(ally.auraHealth==1,"Health-only aura works on all mob types");
            m.events.clear();m.remove(source);
            int finalHealth=ally.health, finalMax=ally.maxHealth;
            ok(m.events.stream().anyMatch(e->e.type()==MatchEvent.Type.AURA_HEALTH&&e.amount()==finalHealth&&e.extra()==finalMax),"Client gets absolute Health stats");
            ok(m.events.stream().noneMatch(e->e.type()==MatchEvent.Type.HEAL||e.type()==MatchEvent.Type.DAMAGE),"Aura changes are not healing or damage");

            m=HeroPowerChecks.match(CardClass.FIGHTER);source=mob(m,0,"test_ender_chest");
            int deckSize=m.player(0).deck.size();m.endTurn(0);
            ok(m.player(0).hand.size()==1&&m.player(1).hand.size()==1,"Chest generates on owner's end turn; opponent only draws");
            for(int n=0;n<150;n++) {
                m.player(0).hand.clear();CardEffects.run(m,0,source.card,source,CardCatalog.Trigger.END_TURN,Target.NONE);
                var generated=m.player(0).hand.getFirst();var entry=CardCatalog.entry(generated.id());
                ok(entry.definition().cardClass()==CardClass.FIGHTER&&!entry.definition().token()&&!entry.deleted()&&!generated.enchanted(),"Only normal active collectible class cards generated");
            }
            ok(m.player(0).deck.size()==deckSize,"Generation does not draw from deck");
            while(m.player(0).hand.size()<Match.MAX_HAND)m.player(0).hand.add(new CardRef(id("zombie"),false));
            m.events.clear();CardEffects.run(m,0,source.card,source,CardCatalog.Trigger.END_TURN,Target.NONE);
            ok(m.player(0).hand.size()==10&&m.events.stream().anyMatch(e->e.type()==MatchEvent.Type.BURN),"Full hand burns generated card");
            m=HeroPowerChecks.match(CardClass.REDSTONER);source=mob(m,0,"test_ender_chest");
            CardEffects.run(m,0,source.card,source,CardCatalog.Trigger.END_TURN,Target.NONE);
            ok(m.player(0).hand.isEmpty(),"Empty class pool does nothing without Neutral fallback");
        } finally { CardCatalog.install(original); }
    }
}
