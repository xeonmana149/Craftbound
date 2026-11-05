package craftbound.game;

import com.google.gson.JsonParser;
import craftbound.card.*;
import java.util.*;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;

final class TrialsChecks {
    static void run() {
        var full=CardCatalog.current();
        try {
            TrialsMapChecks.run();
            TrialsRelicChecks.run();
            mapAndCampaign();
            services();
            authoredContent();
            smallCatalogues(full);
            charmTriggers();
            charmVisibility();
        } finally {CardCatalog.install(full);}
        System.out.println("PASS: Trials connected act, 10-pick draft, card rewards, elite perks, shop stock, copy training, event transactions, lives, carry-over Health, boss clear and legacy journal migration.");
    }
    private static TrialsRun copy(TrialsRun r) {return TrialsRun.JSON.fromJson(TrialsRun.JSON.toJson(r),TrialsRun.class);}
    private static void pick(TrialsRun r,String key) {check(r.pick(r.choices.indexOf(key)),"pick " + key + " on " + r.phase);}
    private static TrialsRun draft(long seed) {
        var r=TrialsRun.create(seed);
        check(r.choices.size()==3,"three playable classes");r.pick(0);
        for(int i=0;i<10;i++) {
            check(r.choices.size()==3 && new HashSet<>(r.choices).size()==3,"distinct borrowed card offers");
            var restored=copy(r);
            check(restored.choices.equals(r.choices) && !restored.reconcile(),"resume exactly the draft on screen");
            int choice=0;
            for(int j=0;j<r.choices.size();j++)if(Cards.get(Identifier.parse(r.choices.get(j))).isMob()){choice=j;break;}
            check(r.pick(choice),"accept draft");
        }
        check(r.phase==TrialsRun.Phase.MAP && r.deck.size()==10,"ten picks enter map");
        return r;
    }
    private static void win(TrialsRun r,int hp){
        r.startBattle("minecraft:overworld",31);String key=r.battleKey();
        check(r.finish(key,true,hp) && !r.finish(key,true,hp),"fight pays once");
    }
    private static void mapAndCampaign(){
        for(int seed=0;seed<30;seed++){
            var r=TrialsRun.create(seed);
            Set<String> accessible=new HashSet<>(r.act().next(r.visited));
            for(var room:r.act().rooms){
                check(accessible.contains(room.id()),"reachable "+room.id());
                if(room.kind()!=TrialsAct.Kind.BOSS)check(!room.next().isEmpty(),"no dead ends");
                accessible.addAll(room.next());
            }
        }
        var r=draft(664);UUID user=UUID.randomUUID();int battles=0;
        while(!r.terminal()){
            check(r.phase==TrialsRun.Phase.MAP && !r.pick(99),"only choose a connected room");
            int before=r.visited.size();r.pick(0);
            check(r.visited.size()==before+1,"one committed move");
            if(!r.room().combat()){check(r.leaveChoice(),"skip service");continue;}
            battles++; var m=r.createBattle("Miner",user,RandomSource.create(55+battles));
            var me=m.player(m.sideOf(user));
            check(me.health==r.health && me.deck.size()+me.hand.size()==r.deck.size(),"exact carried vitality/deck");
            r.startBattle("minecraft:overworld",1);String key=r.battleKey();m.trialsBattle=key;
            var restored=MatchSnapshot.restore(MatchSnapshot.capture(m));check(key.equals(restored.trialsBattle),"battle checkpoint");
            MatchAI.mulligan(m,0);MatchAI.mulligan(m,1);
            for(int n=0;n<1200&&!m.over;n++)MatchAI.step(m,m.current);
            check(m.over,"AI completes live fight at floor "+r.room().row()+" turn "+m.turn);
            check(r.finish(key,true,17) && r.health==17 && !r.finish(key,true,17),"resolve combat, remember HP");
            if(r.phase==TrialsRun.Phase.ELITE_PRIZE)check(r.leaveChoice(),"optional elite charm");
            if(r.phase==TrialsRun.Phase.REWARD) {
                int size=r.deck.size();
                if(battles%2==0)check(r.pick(0)&&r.deck.size()==size+1,"add card after fight");
                else check(r.leaveChoice()&&r.deck.size()==size,"skip card");
            }
        }
        check(r.phase==TrialsRun.Phase.VICTORY && battles>=4 && battles<=7 && r.visited.size()>=9 && r.visited.size()<=13 && r.room().row()==12,"short or long route ends at the act boss");
        var lost=draft(1);lost.pick(0);
        for(int life=0;life<2;life++){lost.startBattle("minecraft:overworld",9);lost.finish(lost.battleKey(),false,0);}
        check(lost.phase==TrialsRun.Phase.DEFEAT && lost.lives==0,"two losses end a run");
        var legacy=draft(2);String key=legacy.id;
        legacy.version=0;legacy.rules=null;legacy.phase=TrialsRun.Phase.READY;legacy.wins=2;legacy.revision=213;legacy.checkpointDimension="minecraft:overworld";
        check(legacy.migrate() && legacy.deck.size()==10 && legacy.wins==2 && legacy.room().combat() && legacy.battleKey().equals(key+"/213"),
                "old active save keeps choices and checkpoint");
    }
    private static TrialsRun atStop(long seed,TrialsAct.Kind kind,String event){
        for(int n=0;n<35;n++){
            var r=draft(seed+n);
            var path=pathTo(r.act(),r.choices,kind,event);
            if(path==null)continue;
            for(int i=0;i<path.size();i++){
                pick(r,path.get(i));
                if(i==path.size()-1)return r;
                if(r.room().combat())win(r,18);
                while(r.phase!=TrialsRun.Phase.MAP)check(r.leaveChoice(),"skip intermediate room or prize");
            }
        }
        throw new AssertionError("cannot find a path to "+kind+" "+event);
    }
    private static List<String> pathTo(TrialsAct act,List<String> exits,TrialsAct.Kind kind,String event){
        for(String id:exits){
            var node=act.byId.get(id);
            if(node.kind()==kind && (event==null || event.equals(node.event())))return List.of(id);
            var tail=pathTo(act,node.next(),kind,event);
            if(tail!=null){var path=new ArrayList<String>();path.add(id);path.addAll(tail);return path;}
        }
        return null;
    }
    private static void services(){
        var r=atStop(91,TrialsAct.Kind.SHOP,null);int purse=r.marks;
        pick(r,"BUY_CARDS");check(r.leaveChoice()&&r.marks==purse,"leave shelf without a charge");
        pick(r,"BUY_CARDS");r.pick(0);check(r.marks==purse-2 && r.deck.size()==11 && !r.choices.contains("BUY_CARDS"),"buy one, mark shelf sold");
        pick(r,"TRAIN");int selected=0;while(!r.enabled(selected))selected++;
        r.pick(selected);var unchanged=copy(r);unchanged.leaveChoice();unchanged.leaveChoice();
        check(unchanged.phase==TrialsRun.Phase.SHOP && unchanged.marks==purse-2,"cancel training leaves wallet");
        pick(r,"TEMPER");check(r.training.get(selected)==TrialTraining.TEMPER && r.marks==purse-5 && !r.choices.contains("TRAIN"),"one copy trained and paid");
        var camp=atStop(17,TrialsAct.Kind.REST,null);pick(camp,"HEAL");check(camp.health==26,"camp restores 8");
        var archive=atStop(50,TrialsAct.Kind.EVENT,"archive");archive.training.set(0,TrialTraining.TEMPER);
        pick(archive,"DUPLICATE");String id=archive.deck.getFirst();archive.pick(0);
        check(archive.deck.size()==11 && archive.deck.getLast().equals(id)&&archive.training.getLast()==TrialTraining.TEMPER,"copy includes training");
        var forge=atStop(76,TrialsAct.Kind.EVENT,"forge");pick(forge,"REMOVE");forge.pick(1);check(forge.deck.size()==9 && forge.training.size()==9,"remove selected copy");
        var collector=atStop(30,TrialsAct.Kind.EVENT,"collector");pick(collector,"TRADE");collector.pick(0);var offers=List.copyOf(collector.choices);
        collector.leaveChoice();collector.pick(0);check(offers.equals(collector.choices),"no trade reroll");
        String replacement=offers.getFirst();collector.pick(0);check(collector.deck.getFirst().equals(replacement)&&collector.deck.size()==10,"trade changes chosen slot");
    }
    private static void authoredContent(){
        var run=atStop(33,TrialsAct.Kind.EVENT,"archive");
        var opt=TrialsRules.list(TrialsRules.find(TrialsRules.list(run.rules,"events"),"archive"),"options").getFirst();
        opt.addProperty("costMarks",2);
        var steps=opt.getAsJsonArray("operations");
        var copy=steps.remove(0);var heal=new com.google.gson.JsonObject();heal.addProperty("type","HEAL");heal.addProperty("amount",5);steps.add(heal);steps.add(copy);
        int cash=run.marks;pick(run,"DUPLICATE");check(run.health==18 && run.marks==cash,"defer compound option until copy picked");
        var saved=copy(run);saved.pick(2);
        check(saved.health==23&&saved.marks==cash-2&&saved.deck.size()==11,"compound option commits once after restore");
        var invalid=run.rules.deepCopy();invalid.getAsJsonObject("act").getAsJsonArray("floors").get(0).getAsJsonObject().getAsJsonArray("edges")
              .get(0).getAsJsonArray().add(99);
        var issues=new ArrayList<String>();TrialsRun.validateContent(invalid,issues);check(!issues.isEmpty(),"reject invalid map exits");
    }
    private static void smallCatalogues(CardCatalog.State full){
        var r=draft(87);var json=JsonParser.parseString(full.json()).getAsJsonObject();
        for(var card:json.getAsJsonArray("cards")){var c=card.getAsJsonObject();if(!c.get("token").getAsBoolean()&&!c.get("id").getAsString().equals("craftbound:zombie"))c.addProperty("deleted",true);}
        CardCatalog.install(CardCatalog.parse(json.toString()));
        check(r.reconcile() && r.phase==TrialsRun.Phase.DRAFT && r.choices.equals(List.of("craftbound:zombie")),"redraft deleted cards");
        while(r.phase==TrialsRun.Phase.DRAFT)r.pick(0);
        r.pick(0);var m=r.createBattle("Test",UUID.randomUUID(),RandomSource.create(1));check(m.players.length==2,"one-card pool battle");
        for(var card:json.getAsJsonArray("cards"))if(card.getAsJsonObject().get("id").getAsString().equals("craftbound:zombie"))card.getAsJsonObject().addProperty("deleted",true);
        CardCatalog.install(CardCatalog.parse(json.toString()));var empty=TrialsRun.create(1);
        check(empty.choices.isEmpty()&&empty.view("").get("phase").getAsString().equals("UNAVAILABLE"),"empty catalogue explained");
    }
    private static void charmVisibility() {
        var match = WorkshopChecks.match();
        match.player(0).trialsCharms = List.of("FANG");
        var enemyView = MatchView.build(match, 1, 0);
        var tabletop = MatchView.publicBoard(match, false);
        check(!enemyView.contains("\"trialsCharms\"") && !tabletop.contains("\"trialsCharms\"")
                && !TableSpectating.view(tabletop, match, 3).toString().contains("\"trialsCharms\""),
                "run loadout is private, including hand-sharing spectators");
        match.draw(0);
        check(!shownSpent(match), "drawing a mob does not spend Fang");
        match.playCard(0, 0, Target.NONE);
        check(shownSpent(match), "playing the drawn mob dims Fang");
        match.turn += 2;
        match.giveCard(0, new CardRef(Cards.ZOMBIE, false));
        check(!shownSpent(match), "new turn readies Fang before the next play");
        match.playCard(0, 0, Target.NONE);
        check(shownSpent(match), "playing a generated mob dims Fang");
    }

    private static boolean shownSpent(Match match) {
        var view = JsonParser.parseString(MatchView.build(match, 0, 0)).getAsJsonObject();
        return view.getAsJsonArray("players").get(0).getAsJsonObject().getAsJsonArray("trialsCharms")
                .get(0).getAsJsonObject().get("spent").getAsBoolean();
    }

    private static void charmTriggers() {
        var m = WorkshopChecks.match();
        var player = m.player(0);
        player.trialsCharms = List.of("FANG", "RATION");
        player.health = 22;
        WorkshopChecks.play(m, "zombie");
        check(player.board.getFirst().attack == Cards.get(Cards.ZOMBIE).attack()+1 && player.health == 24,
                "two compatible charms activate together");
        WorkshopChecks.play(m, "zombie");
        check(player.board.get(1).attack == Cards.get(Cards.ZOMBIE).attack() && player.health == 24, "one charge each per own turn");
        var restored = MatchSnapshot.restore(MatchSnapshot.capture(m));
        WorkshopChecks.play(restored, "zombie");
        check(restored.player(0).health == 24, "pause preserves spent charges");
        restored.turn += 2;
        WorkshopChecks.play(restored, "zombie");
        check(restored.player(0).health == 26, "next turn restores charges");
        m = WorkshopChecks.match(); player=m.player(0); player.trialsCharms=List.of("MORTAR", "FLASK");
        WorkshopChecks.play(m, "cobblestone_wall");
        check(player.board.getFirst().maxHealth == Cards.get(Cards.COBBLESTONE_WALL).health()+2, "Mortar health");
        int enemyHealth=m.player(1).health;
        WorkshopChecks.play(m, "experience_bottle");
        check(m.player(1).health == enemyHealth - 1, "Flask damage");
        m = WorkshopChecks.match(); player=m.player(0); player.trialsCharms=List.of("WHETSTONE", "LANTERN");
        WorkshopChecks.play(m, "diamond_sword");
        check(player.tool.attack==Cards.get(Cards.DIAMOND_SWORD).attack()+1 && player.hand.size()==1, "tool upgrade and draw");
    }

    private static void check(boolean value, String reason) {
        if (!value) throw new AssertionError("Trials: " + reason);
    }
}
