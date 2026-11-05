package craftbound.game;

import craftbound.card.*;
import com.google.gson.*;
import java.util.List;

final class IgnoreShieldChecks {
    static void ok(boolean value,String message){WorkshopChecks.ok(value,message);}
    static void run(){
        var old=CardCatalog.current();
        try {
            var root=JsonParser.parseString(old.json()).getAsJsonObject();
            var nativeMob=EffectSequenceChecks.copy(root,"zombie","bypass_mob","[]");
            var keywords=new JsonArray();keywords.add("IGNORE_SHIELD");nativeMob.add("keywords",keywords);
            var nativeTool=EffectSequenceChecks.copy(root,"crafted_sword","bypass_tool","[]");nativeTool.add("keywords",keywords.deepCopy());
            EffectSequenceChecks.copy(root,"arrow","grant_bypass","[{\"trigger\":\"PLAY\",\"action\":\"GRANT_KEYWORD\",\"target\":\"CHOSEN_FRIENDLY\",\"boardKind\":\"MOB\",\"type\":\"\",\"amount\":1,\"attack\":0,\"health\":0,\"card\":\"\",\"excludeSelf\":false,\"keyword\":\"IGNORE_SHIELD\"}]");
            CardCatalog.install(CardCatalog.parse(root.toString()));
            var m=WorkshopChecks.match();var attacker=WorkshopChecks.mob(m,0,"zombie");
            attacker.ready=true;attacker.summonedThisTurn=false;
            var wall=WorkshopChecks.mob(m,1,"turtle");var other=WorkshopChecks.mob(m,1,"zombie");
            ok(m.whyCantAttack(0,attacker.id,Target.hero(1))!=null,"Ordinary mob cannot bypass Shield for face");
            ok(m.whyCantAttack(0,attacker.id,Target.mob(1,other.id))!=null,"Ordinary mob cannot bypass Shield for another mob");
            WorkshopChecks.play(m,"grant_bypass",Target.mob(0,attacker.id));
            ok(attacker.has(Keyword.IGNORE_SHIELD),"Authored grant applies Ignore Shield");
            ok(m.whyCantAttack(0,attacker.id,Target.hero(1))==null&&m.whyCantAttack(0,attacker.id,Target.mob(1,other.id))==null,"Granted bypass unlocks face and non-Shield targets");
            ok(m.whyCantAttack(0,attacker.id,Target.mob(1,wall.id))==null,"Shield card remains an optional target");
            ok(wall.has(Keyword.SHIELD),"Bypass does not remove Shield from defenders");
            ok(m.events.stream().anyMatch(e->e.type()==MatchEvent.Type.STATUS&&e.amount()==MatchEvent.STATUS_IGNORE_SHIELD),"Grant sends its own live status event");
            var view=JsonParser.parseString(MatchView.build(m,0,0)).getAsJsonObject().getAsJsonArray("players").get(0).getAsJsonObject().getAsJsonArray("board").get(0).getAsJsonObject();
            ok(view.get("ignoreShield").getAsBoolean(),"Client view exposes granted keyword");
            int mid=attacker.id;m=MatchSnapshot.restore(MatchSnapshot.capture(m));attacker=m.player(0).minion(mid);
            ok(attacker.has(Keyword.IGNORE_SHIELD)&&m.whyCantAttack(0,mid,Target.hero(1))==null,"Ignore Shield survives pause and resume");
            attacker.frozen=true;ok(m.whyCantAttack(0,mid,Target.hero(1))!=null,"Frozen still blocks attacking");attacker.frozen=false;
            attacker.summonedThisTurn=true;m.grantKeyword(attacker,Keyword.SPRINT);
            ok(m.whyCantAttack(0,mid,Target.hero(1))!=null&&m.whyCantAttack(0,mid,Target.mob(1,other.id))==null,"Sprint still cannot hit face on arrival, but may bypass to another mob");
            attacker.summonedThisTurn=false;other=m.player(1).minion(other.id);other.invulnerable=true;int hp=other.health;
            m.attack(0,mid,Target.mob(1,other.id));ok(other.health==hp,"Bypass does not bypass invulnerability");
            ok(m.whyCantAttack(0,mid,Target.hero(1))!=null,"Attack remains spent");
            m=WorkshopChecks.match();attacker=WorkshopChecks.mob(m,0,"bypass_mob");attacker.ready=true;attacker.summonedThisTurn=false;WorkshopChecks.mob(m,1,"turtle");
            ok(m.whyCantAttack(0,attacker.id,Target.hero(1))==null,"Printed keyword works without a grant");
            m.player(0).xp=0;m.player(0).powerUsed=true;m.player(1).health=2;
            MatchAI.step(m,0);ok(m.over&&m.winner==0,"AI chooses lethal through Shield with Ignore Shield");
            m=WorkshopChecks.match();WorkshopChecks.mob(m,1,"turtle");WorkshopChecks.play(m,"bypass_tool");
            ok(m.whyCantAttack(0,Match.HERO,Target.hero(1))==null,"Tool grants bypass to hero attacks");
            WorkshopChecks.play(m,"crafted_sword");ok(m.whyCantAttack(0,Match.HERO,Target.hero(1))!=null,"Replacing tool removes bypass");
            var text=Cards.styledText("Ignore Shield.");
            ok(text.getSiblings().stream().anyMatch(c->Keyword.fromInsertion(c.getStyle().getInsertion())==Keyword.IGNORE_SHIELD&&c.getStyle().isBold()),"Multiword keyword is bold and tagged for hover explanation");
            ok(CardCatalog.description(CardCatalog.entry(WorkshopChecks.id("grant_bypass"))).contains("Ignore Shield"),"Generated effect text names keyword");
        } finally {CardCatalog.install(old);}
    }
}
