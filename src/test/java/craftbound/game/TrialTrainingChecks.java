package craftbound.game;

import com.google.gson.*;
import craftbound.card.*;
import craftbound.sleeve.*;
import java.util.*;
import static craftbound.game.WorkshopChecks.*;

final class TrialTrainingChecks {
    static void run() {
        Match m=match();var def=Cards.get(Cards.ZOMBIE);
        var p=m.player(0);
        CardRef trained=new CardRef(Cards.ZOMBIE,true,TrialTraining.TEMPER);
        var standard=new CardRef(Cards.ZOMBIE,true);
        p.hand.add(standard);p.hand.add(trained);p.xp=10;
        m.playCard(0,1,Target.NONE);
        var first=p.board.getFirst();
        ok(first.card.equals(trained)&&first.attack==def.attack()+1&&first.maxHealth==def.health()+1,"Only selected copy receives printed training");
        m.buff(first,4,3);first.withered=true;m.clearAppliedEffects(first);
        ok(!first.withered&&first.attack==def.attack()+1&&first.maxHealth==def.health()+1,"Cleanse removes applied effects but retains base training");
        m.bounce(first);
        ok(p.hand.equals(List.of(standard,trained)),"Bounce returns exact trained and enchanted copy");
        m.playCard(0,1,Target.NONE);m.playCard(0,0,Target.NONE);
        var replay=p.board.getFirst();var plain=p.board.getLast();
        ok(replay.attack==def.attack()+1 && plain.attack==def.attack() && plain.card.enchanted() && plain.maxHealth==def.health(),"The ordinary enchanted copy never inherits its sibling's training");
        replay.totem=true;m.damage(replay,200,null);m.resolveDeaths();
        var revived=p.board.stream().filter(u->u.card.equals(trained)).findFirst().orElseThrow();
        ok(revived.id!=replay.id&&revived.attack==def.attack()+1&&revived.maxHealth==def.health()+1,"Totem resurrection reapplies one trained copy exactly once");
        var resident=m.summon(1,new CardRef(Cards.ZOMBIE,false,TrialTraining.SHIELD));
        m.clearAppliedEffects(resident);ok(resident.has(Keyword.SHIELD),"Permanent Shield training survives Cleanse");
        var trainedSprint=m.summon(0,new CardRef(Cards.ZOMBIE,false,TrialTraining.SPRINT));
        ok(trainedSprint.canAttack() && m.whyCantAttack(0,trainedSprint.id,Target.hero(1))!=null,"Trained Sprint follows ordinary first-turn restrictions");
        var wall=m.summon(1,new CardRef(Cards.COBBLESTONE_WALL,false,TrialTraining.TEMPER));
        ok(wall.attack==0&&wall.health==Cards.get(wall.card.id()).health()+2,"Tempering a Block adds two Health");
        m.equip(0,new CardRef(Cards.CRAFTED_SWORD,true,TrialTraining.TEMPER));
        ok(p.tool.attack==Cards.get(Cards.CRAFTED_SWORD).attack()+1&&p.tool.uses==Cards.get(Cards.CRAFTED_SWORD).health(),"Tempering a Tool affects only Attack");
        p.hand.clear();p.deck.addFirst(trained);m.events.clear();m.draw(0);
        JsonObject owner=JsonParser.parseString(MatchView.build(m,0,0)).getAsJsonObject(),opponent=JsonParser.parseString(MatchView.build(m,1,0)).getAsJsonObject();
        var privateCard=owner.getAsJsonArray("players").get(0).getAsJsonObject().getAsJsonArray("hand").get(0).getAsJsonObject();
        ok(privateCard.get("training").getAsString().equals("TEMPER") && privateCard.get("ench").getAsBoolean(),"Trained hand identity and cosmetic are independent");
        var opponentDraw=opponent.getAsJsonArray("events").get(0).getAsJsonObject();
        ok(!opponent.getAsJsonArray("players").get(0).getAsJsonObject().has("hand") && !opponentDraw.has("training") && opponentDraw.get("c").getAsString().isEmpty(),"Hidden draw reveals neither training nor card identity to opponents");
        var noGrant=MatchView.spectatorEvents(m,0,0).get(0).getAsJsonObject();var grant=MatchView.spectatorEvents(m,0,1).get(0).getAsJsonObject();
        ok(!noGrant.has("training") && noGrant.get("c").getAsString().isEmpty() && grant.get("training").getAsString().equals("TEMPER"),"Spectator hand consent gates both art and training");
        var restored=MatchSnapshot.restore(MatchSnapshot.capture(m));
        ok(restored.player(0).hand.getFirst().equals(trained) && restored.player(0).tool.card.training()==TrialTraining.TEMPER,"Trained hand and equipped tool survive pause");
        var old=MatchSnapshot.capture(match());removeTraining(old);
        var legacy=MatchSnapshot.restore(old);
        ok(legacy.player(0).deck.stream().allMatch(c->c.training()==TrialTraining.NONE) && !legacy.player(0).deck.isEmpty(),"Pre-training saves load as ordinary cards");
        var rewards=SleeveUnlocks.EMPTY.rewardOptions(Set.of());
        ok(rewards.size()>0 && rewards.stream().noneMatch(Sleeves::isTrial),"Ordinary Card Master rewards exclude trial-only backs");
    }
    private static void removeTraining(JsonElement element){
        if(element.isJsonArray()){for(var item:element.getAsJsonArray())removeTraining(item);}
        if(element.isJsonObject()){var obj=element.getAsJsonObject();obj.remove("training");for(var pair:obj.entrySet())removeTraining(pair.getValue());}
    }
    private TrialTrainingChecks(){}
}