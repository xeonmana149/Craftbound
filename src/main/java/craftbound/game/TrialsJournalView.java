package craftbound.game;

import com.google.gson.*;
import craftbound.card.Cards;
import java.util.*;
import net.minecraft.resources.Identifier;

final class TrialsJournalView {
    static JsonObject build(TrialsRun r,String notice) {
        JsonObject v=new JsonObject();
        var room=r.room();
        boolean empty=(r.phase==TrialsRun.Phase.CLASS || r.phase==TrialsRun.Phase.DRAFT) && r.choices.isEmpty();
        v.addProperty("runId",r.id); v.addProperty("revision",r.revision); v.addProperty("phase",r.checkpointDimension!=null?"PAUSED":empty?"UNAVAILABLE":r.phase.name());
        v.addProperty("heroClass",r.heroClass.name()); v.add("choices",TrialsRun.JSON.toJsonTree(r.choices));v.add("deck",TrialsRun.JSON.toJsonTree(r.deck));
        v.addProperty("draftSize",r.draftGoal());v.addProperty("wins",r.wins);v.addProperty("lives",r.lives);v.addProperty("encounters",r.act().battles);
        v.addProperty("health",r.health);v.addProperty("maxHealth",r.maxHealth());v.addProperty("depth",room==null?0:room.row()+1);
        v.addProperty("roomCount",r.act().floors);v.addProperty("actName",r.act().name);v.addProperty("activeNode",room==null?"":room.id());
        v.add("training",TrialsRun.JSON.toJsonTree(r.training));v.addProperty("deckMinimum",r.minimum());v.addProperty("deckLimit",r.maximum());
        v.addProperty("marks",r.marks);v.addProperty("charmPrice",r.config("charmPrice",3));v.addProperty("cardPrice",r.config("cardPrice",2));
        v.add("acquiredBoons",TrialsRun.JSON.toJsonTree(TrialBoons.visible(r)));
        v.add("charms",TrialsRun.JSON.toJsonTree(r.charms));v.addProperty("pendingCharm",r.pendingCharm==null?"":r.pendingCharm);
        v.addProperty("tradeOut",r.selected>=0 && r.selected<r.deck.size()?r.deck.get(r.selected):"");
        v.addProperty("bonusHealth",r.bonusHealth);v.addProperty("charmSlots",r.config("charmSlots",2));
        v.addProperty("heart",r.bonusHealth/4);v.addProperty("pack",r.pack);v.addProperty("torch",r.torch);
        v.addProperty("message",empty?"Activate a collectible Neutral or class card in the card catalogue to draft here.":notice);
        var charms=new JsonArray(); for(var c:TrialsContent.charms()){
            var charm=new JsonObject();charm.addProperty("id",c.id());charm.addProperty("title",c.title());charm.addProperty("description",c.description());
            charm.addProperty("iconItem",c.iconItem());charms.add(charm);
        }
        v.add("charmDefinitions",charms);
        var map=new JsonArray();var next=r.act().next(r.visited);
        for(var node:r.act().rooms) {
            var n=new JsonObject();n.addProperty("id",node.id());n.addProperty("row",node.row());n.addProperty("lane",node.lane());n.addProperty("kind",node.kind().name());
            boolean revealed=node.kind()!=TrialsAct.Kind.EVENT || r.visited.contains(node.id());
            String title=revealed?TrialsText.currency(node.title()):"Unknown encounter";
            String description=revealed?TrialsText.currency(node.description()):"Enter this room to discover what awaits.";
            n.addProperty("revealed",revealed);
            n.addProperty("mapX",node.mapX());n.addProperty("mapY",node.mapY());
            if(node.combat()){
                var foe=r.encounter(node);
                if(List.of("Challenger","Elite challenger","Chamber guardian").contains(title))title=foe.name();
                var f=new JsonObject();f.addProperty("name",foe.name());f.addProperty("heroClass",foe.hero().name());
                f.addProperty("health",foe.health());f.addProperty("bonusXp",foe.bonusXp());f.addProperty("extraCards",foe.extraCards());
                f.addProperty("bounty",foe.bounty());f.addProperty("elite",foe.elite());f.addProperty("boss",foe.boss());f.addProperty("style",foe.style());
                f.addProperty("abilityName",foe.abilityName());f.addProperty("abilityText",foe.abilityText());f.addProperty("deckSize",foe.deckSize());
                n.add("foe",f);
            }
            n.addProperty("title",title);n.addProperty("description",description);n.add("next",TrialsRun.JSON.toJsonTree(node.next()));
            n.addProperty("status",node.equals(room)?"CURRENT":r.visited.contains(node.id())?"DONE":r.phase==TrialsRun.Phase.MAP && next.contains(node.id())?"OPEN":"LOCKED");
            map.add(n);
        }
        v.add("map",map);
        var eligible=new JsonArray();var menu=new JsonArray();
        for(int i=0;i<r.choices.size();i++){
            String key=r.choices.get(i);var option=new JsonObject();
            boolean enabled=r.enabled(i);if(enabled)eligible.add(i);
            option.addProperty("key",key);option.addProperty("price",r.price(key));option.addProperty("enabled",enabled);
            JsonObject authored=r.phase==TrialsRun.Phase.SHOP?r.item(key):TrialsRules.find(r.options(r.phase),key);
            String title=key,desc="",icon="";
            if(authored!=null){title=TrialsText.currency(TrialsRules.s(authored,"title",key));desc=TrialsText.currency(TrialsRules.s(authored,"description",""));icon=TrialsRules.s(authored,"iconItem","");}
            else if(r.phase==TrialsRun.Phase.TRAIN_TYPE){
                var type=TrialTraining.valueOf(key);title=type.label();desc=type.description(Identifier.parse(r.deck.get(r.selected)));
            }else if(r.phase==TrialsRun.Phase.SHOP_CHARMS || r.phase==TrialsRun.Phase.ELITE_PRIZE || r.phase==TrialsRun.Phase.REPLACE) {
                var charm=TrialCharmSpec.find(key);if(charm!=null){title=charm.title();desc=charm.description();icon=charm.iconItem();}
            }else if(key.contains(":")){
                var id=Identifier.tryParse(key);if(id!=null && Cards.get(id)!=null){title=Cards.name(id).getString();desc=Cards.text(id).getString();}
            }
            option.addProperty("title",title);option.addProperty("description",desc);option.addProperty("iconItem",icon);menu.add(option);
        }
        v.add("menuItems",menu);v.add("eligibleIndices",eligible);
        return v;
    }
    private TrialsJournalView() {}
}
