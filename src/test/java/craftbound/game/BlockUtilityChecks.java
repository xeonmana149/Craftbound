package craftbound.game;

import com.google.gson.*;
import craftbound.card.*;
import craftbound.network.MatchActionPayload;
import net.minecraft.util.RandomSource;
import java.util.*;
import static craftbound.game.WorkshopChecks.*;

/** Real catalogue authoring, target resolution, persistent card copies and private deck selection. */
final class BlockUtilityChecks {
    static JsonObject effect(String trigger, String action, String target, String kind, int amount) {
        var e=SurvivingKillChecks.effect(trigger,target,amount);
        e.addProperty("action",action);e.addProperty("boardKind",kind);return e;
    }
    static Match human() {
        var m=match();
        for(int s=0;s<2;s++)m.players[s]=new MatchPlayer("Player "+s,UUID.randomUUID(),CardClass.FISHERMAN,null,List.of());
        m.player(0).xp=10;return m;
    }
    static CardRef ref(String name) {return new CardRef(id(name),false);}
    static boolean parses(JsonObject root) {
        try {CardCatalog.parse(root.toString());return true;}catch(IllegalArgumentException e){return false;}
    }
    static void run() {
        var original=CardCatalog.current();
        var root=JsonParser.parseString(original.json()).getAsJsonObject();
        var arch=SurvivingKillChecks.unit(root,"test_arch",0,3,true);
        arch.addProperty("passive","AURA_STATS");arch.addProperty("passiveAmount",0);
        arch.addProperty("auraHealth",3);arch.addProperty("auraKind","BLOCK");arch.addProperty("auraType","");
        var both=SurvivingKillChecks.unit(root,"test_both_aura",0,3,true);
        both.addProperty("passive","AURA_STATS");both.addProperty("passiveAmount",0);
        both.addProperty("auraHealth",1);both.addProperty("auraKind","BOTH");
        var target=SurvivingKillChecks.unit(root,"test_target_block",0,3,true);
        target.getAsJsonArray("effects").add(effect("DROP","DRAW","SELF","BOTH",1));
        SurvivingKillChecks.unit(root,"test_second_block",0,2,true);
        SurvivingKillChecks.unit(root,"test_third_block",0,4,true);
        var token=SurvivingKillChecks.unit(root,"test_copy_token",0,2,true);
        token.addProperty("token",true);
        var press=AuraAndClassCardChecks.copy(root,"bone_meal","test_press");
        press.getAsJsonArray("effects").add(effect("PLAY","RETURN_AND_COPY","CHOSEN_FRIENDLY","BLOCK",1));
        var chest=SurvivingKillChecks.unit(root,"test_blueprint",0,4,true);
        chest.getAsJsonArray("effects").add(effect("PLAY","CHOOSE_FROM_DECK","SELF","BLOCK",3));
        chest.getAsJsonArray("effects").add(effect("DROP","DRAW","SELF","BOTH",1));
        try {
            CardCatalog.install(CardCatalog.parse(root.toString()));
            ok(CardCatalog.description(CardCatalog.entry(id("test_arch"))).equals("Your other blocks have +3 Health."),"Block aura wording");
            ok(CardCatalog.description(CardCatalog.entry(id("test_blueprint"))).contains("Choose one of up to 3 different blocks from your deck and draw it"),"Deck choice wording");
            ok(CardEffects.targetKind(id("test_blueprint"))==Target.Kind.NONE,"Deck selection does not ask for board target");

            aura();
            copying();
            deck();
            multiplayer();

            arch.addProperty("passiveAmount",1);ok(!parses(root),"Block aura rejects Attack");arch.addProperty("passiveAmount",0);
            arch.addProperty("auraKind","INVALID");ok(!parses(root),"Unknown aura kind rejected");arch.addProperty("auraKind","BLOCK");
            var e=press.getAsJsonArray("effects").get(0).getAsJsonObject();
            e.addProperty("amount",11);ok(!parses(root),"Extra copy limit");e.addProperty("amount",1);
            var d=chest.getAsJsonArray("effects").get(0).getAsJsonObject();
            d.addProperty("amount",4);ok(!parses(root),"Deck offer limit");d.addProperty("amount",3);
            d.addProperty("target","ENEMY_MOBS");ok(!parses(root),"Deck search cannot target opponent");d.addProperty("target","SELF");
            d.addProperty("boardKind","MOB");d.addProperty("type","UNDEAD");
            ok(parses(root),"Deck search supports optional mob-type filters");
            CardCatalog.install(CardCatalog.parse(root.toString()));
            var filtered=human();filtered.player(0).deck.addAll(List.of(ref("zombie"),ref("cow"),ref("test_target_block")));
            play(filtered,"test_blueprint",Target.NONE);
            ok(filtered.choiceCards(filtered.pendingChoice()).equals(List.of(ref("zombie"))),"Deck kind and subtype filters both apply");
            d.addProperty("boardKind","BLOCK");d.addProperty("type","");
            chest.getAsJsonArray("effects").add(effect("PLAY","DRAW","SELF","BOTH",3));
            CardCatalog.install(CardCatalog.parse(root.toString()));
            var stale=human();stale.player(0).deck.add(ref("test_target_block"));
            play(stale,"test_blueprint",Target.NONE);
            ok(stale.pendingChoice()==null,"Subsequent effects consuming every offer cannot leave an empty picker");
        } finally {CardCatalog.install(original);}
    }
    static void aura() {
        var m=human();var b=mob(m,0,"test_target_block");var unit=mob(m,0,"zombie");var enemy=mob(m,1,"test_target_block");
        int unitHp=unit.health;
        var a=mob(m,0,"test_arch");
        ok(b.health==6&&b.maxHealth==6&&a.health==3,"Other blocks receive +3, source excludes itself");
        ok(unit.health==unitHp&&enemy.health==3&&m.attackOf(b)==0,"Aura excludes mobs and enemies, blocks still cannot attack");
        var second=mob(m,0,"test_arch");
        ok(b.maxHealth==9&&a.maxHealth==6&&second.maxHealth==6,"Block auras stack across distinct sources");
        int events=m.events.size();m.refreshAuras();m.refreshAuras();
        ok(events==m.events.size()&&b.health==9,"Aura recomputation is idempotent");
        var subject=new EffectPreview.Subject(EffectPreview.Kind.BOARD,b.card.id(),0,b.id,b.health,b.maxHealth,false,false,false,false);
        ok(EffectPreview.forTarget(new EffectPreview.Source(a.card.id(),0,a.id,true,0),subject)!=null,"Block aura preview highlights blocks");
        m.damage(b,2,null);
        m=MatchSnapshot.restore(MatchSnapshot.capture(m));b=m.player(0).board.get(0);a=m.player(0).board.get(2);
        ok(b.health==7&&b.maxHealth==9,"Block aura persists across save without healing");
        m.bounce(a);
        ok(b.maxHealth==6&&b.health==6,"Returning one source removes only its aura");
        var remaining=m.player(0).board.stream().filter(x->x.card.id().equals(id("test_arch"))).findFirst().orElseThrow();
        m.damage(remaining,99,null);m.resolveDeaths();
        ok(b.maxHealth==3&&b.health==3,"Source death removes Health bonus");
        m=human();b=mob(m,0,"test_target_block");unit=mob(m,0,"zombie");unitHp=unit.health;
        a=mob(m,0,"test_both_aura");
        ok(b.maxHealth==4&&unit.maxHealth==unitHp+1&&a.maxHealth==3,"Both-kind Health aura");
    }
    static void copying() {
        var m=human();var original=new CardRef(id("test_target_block"),true,TrialTraining.TEMPER);
        var b=m.summon(0,original);m.buff(b,0,4);m.damage(b,2,null);b.totem=true;b.frozen=true;
        m.events.clear();play(m,"test_press",Target.mob(0,b.id));
        ok(m.player(0).board.isEmpty()&&m.player(0).hand.equals(List.of(original,original)),"Returns original and creates exactly one matching copy");
        ok(m.events.stream().noneMatch(e->e.type()==MatchEvent.Type.DEATH||e.type()==MatchEvent.Type.DRAW),"Return does not trigger Drop or Totem");
        var order=m.events.stream().map(MatchEvent::type).filter(t->t==MatchEvent.Type.BOUNCE||t==MatchEvent.Type.GIVE).toList();
        ok(order.equals(List.of(MatchEvent.Type.BOUNCE,MatchEvent.Type.GIVE,MatchEvent.Type.GIVE)),"Return animates before sequential copies");
        var replay=m.summon(0,m.player(0).hand.removeFirst());
        ok(replay.health==5&&replay.maxHealth==5&&!replay.frozen&&!replay.totem,"Copy retains training, resets combat buffs and statuses");

        m=human();b=mob(m,0,"test_target_block");var other=mob(m,0,"test_target_block");
        play(m,"test_press",Target.mob(0,b.id));ok(m.player(0).board.equals(List.of(other)),"Exact selected board instance returns");
        m=human();b=mob(m,1,"test_target_block");var wrong=mob(m,0,"zombie");m.player(0).hand.add(ref("test_press"));int xp=m.player(0).xp;
        m.playCard(0,0,Target.mob(1,b.id));m.playCard(0,0,Target.mob(0,wrong.id));
        ok(m.player(0).xp==xp&&m.player(0).hand.size()==1,"Wrong side or wrong card kind rejected without payment");

        m=human();b=mob(m,0,"test_target_block");
        for(int i=0;i<9;i++)m.player(0).hand.add(ref("zombie"));
        play(m,"test_press",Target.mob(0,b.id));
        ok(m.player(0).hand.size()==10&&hand(m,"test_target_block")==1,"Only available hand slots filled");
        ok(m.events.stream().filter(e->e.type()==MatchEvent.Type.BURN).count()==1,"Extra copy burns with full hand");
        m=human();b=mob(m,0,"test_copy_token");play(m,"test_press",Target.mob(0,b.id));
        ok(m.player(0).board.isEmpty()&&hand(m,"test_copy_token")==1,"Token return rule and explicit new copy are separate");
    }
    static void setupDeck(Match m,int side) {
        m.player(side).deck.clear();
        m.player(side).deck.addAll(List.of(ref("zombie"),ref("test_target_block"),
            new CardRef(id("test_target_block"),true,TrialTraining.TEMPER),
            ref("test_second_block"),ref("test_third_block"),ref("bone_meal")));
    }
    static void deck() {
        var m=human();setupDeck(m,0);var before=List.copyOf(m.player(0).deck);
        play(m,"test_blueprint",Target.NONE);
        var c=m.pendingChoice();var offers=m.choiceCards(c);
        ok(c!=null&&offers.size()==3&&offers.stream().map(CardRef::id).distinct().count()==3,"Three distinct block IDs, no duplicate normal/enchanted slots");
        ok(m.player(0).deck.equals(before)&&m.player(0).hand.isEmpty(),"Offering does not draw, shuffle or generate");
        ok(!m.yourMove(0)&&!m.chooseFromHand(1,0)&&!m.chooseFromHand(0,3)&&!m.chooseFromHand(-1,0),"Choice blocks moves and validates chooser/index");
        ok(MatchView.buildJson(m,0,0).getAsJsonObject("choice").getAsJsonArray("cards").size()==3,"Chooser sees all offers");
        for(int viewer:new int[]{1,-1})ok(!MatchView.buildJson(m,viewer,0).getAsJsonObject("choice").has("cards"),"Offers private to owner");
        var selected=offers.get(1);var expected=new ArrayList<>(before);expected.remove(selected);
        m.events.clear();ok(m.chooseFromHand(0,1),"Select offered card");
        ok(m.player(0).hand.equals(List.of(selected))&&m.player(0).deck.equals(expected),"Selected exact copy drawn, unchosen deck order preserved");
        ok(m.events.stream().anyMatch(e->e.type()==MatchEvent.Type.DRAW)&&m.events.stream().noneMatch(e->e.type()==MatchEvent.Type.GIVE),"Selection uses DRAW, not GIVE");
        var source=m.player(0).board.getFirst();int draws=m.player(0).hand.size();m.damage(source,99,null);m.resolveDeaths();
        ok(m.player(0).hand.size()==draws+1,"Blueprint Drop separately draws one");

        m=human();var trained=new CardRef(id("test_target_block"),true,TrialTraining.TEMPER);
        m.player(0).deck.add(trained);play(m,"test_blueprint",Target.NONE);
        ok(m.choiceCards(m.pendingChoice()).equals(List.of(trained)),"One remaining match offers one choice");
        m.chooseFromHand(0,0);ok(m.player(0).hand.equals(List.of(trained)),"Draw retains Enchanted and training");
        m=human();m.player(0).deck.add(ref("zombie"));play(m,"test_blueprint",Target.NONE);
        ok(m.pendingChoice()==null&&m.player(0).hunger==0&&m.player(0).deck.size()==1,"No eligible blocks is harmless");
        m=human();play(m,"test_blueprint",Target.NONE);ok(m.pendingChoice()==null&&m.player(0).hunger==0,"Empty deck search causes no Hunger");

        m=human();setupDeck(m,0);for(int i=0;i<10;i++)m.player(0).hand.add(ref("zombie"));
        play(m,"test_blueprint",Target.NONE);m.events.clear();m.resolveChoiceRandomly();
        ok(m.pendingChoice()==null&&m.player(0).deck.size()==5&&m.player(0).hand.size()==10,"Timeout resolves even with full hand");
        ok(m.events.stream().anyMatch(e->e.type()==MatchEvent.Type.BURN),"Chosen draw burns using normal hand limit");
        m=human();setupDeck(m,1);m.players[1]=new MatchPlayer("AI",null,CardClass.FISHERMAN,null,m.player(1).deck);
        var f=CardCatalog.entry(id("test_blueprint")).effects().getFirst();m.chooseFromDeck(1,f,ref("test_blueprint"));
        ok(m.pendingChoice()==null&&m.player(1).hand.size()==1&&m.player(1).deck.size()==5,"AI selects a real deck card without waiting");
        m=human();setupDeck(m,0);m.chooseFromDeck(0,f,ref("test_blueprint"));m.chooseFromDeck(0,f,ref("test_blueprint"));
        m.resolveChoiceRandomly();ok(m.pendingChoice()!=null,"Multiple deck choices queue");
        m.resolveChoiceRandomly();ok(m.pendingChoice()==null&&m.player(0).hand.size()==2,"Queued choices settle from remaining copies");
    }
    static MatchPlayer pilot(String name) {
        var cards=Cards.collectible().stream().filter(id->DeckRules.allowed(id,CardClass.FIGHTER))
                .flatMap(id->java.util.stream.Stream.generate(()->new CardRef(id,false))
                        .limit(DeckRules.maxCopies(Cards.get(id).rarity()))).limit(30).toList();
        return new MatchPlayer(name,UUID.randomUUID(),CardClass.FIGHTER,null,cards);
    }
    static void multiplayer() {
        var f=CardCatalog.entry(id("test_blueprint")).effects().getFirst();
        var team=new RelayMatch(new MatchPlayer[][]{{pilot("A"),pilot("C")},{pilot("B"),pilot("D")}},RandomSource.create(145));
        int side=team.current;setupDeck(team,side);team.chooseFromDeck(side,f,ref("test_blueprint"));
        int time=team.turnTicksRemaining;team.tickTurnClock();team.endTurn(side);
        ok(time==team.turnTicksRemaining&&!team.ready[side],"Team timer/readiness wait for deck choice");
        var view=RelayTables.view(team,team.player(side).playerId);
        ok(view.getAsJsonObject("choice").has("cards"),"Team chooser receives private offers");
        ok(!RelayTables.view(team,team.player(side^2).playerId).getAsJsonObject("choice").has("cards"),"Teammate cannot see deck offers");
        ok(team.act(team.player(side).playerId,MatchActionPayload.CHOOSE_CARD,0,side^1,Target.NONE)==null,"Team input resolves deck choice");

        var seats=new ArrayList<MatchPlayer>();var leaders=new HashMap<UUID,net.minecraft.resources.Identifier>();
        var deck=Cards.collectible().stream().filter(id->DeckRules.allowed(id,CardClass.FIGHTER)).limit(CommanderMatch.DECK_SIZE).map(id->new CardRef(id,false)).toList();
        var leader=deck.stream().filter(c->CommanderMatch.eligible(c.id())).findFirst().orElseThrow().id();
        for(int i=0;i<4;i++){
            var p=new MatchPlayer("Seat "+i,UUID.randomUUID(),CardClass.FIGHTER,null,deck);
            seats.add(p);leaders.put(p.playerId,leader);
        }
        var commander=new CommanderMatch(seats,leaders,RandomSource.create(146));side=commander.current;
        setupDeck(commander,side);commander.chooseFromDeck(side,f,ref("test_blueprint"));
        ok(CommanderTables.view(commander,side).getAsJsonObject("choice").has("cards"),"Commander chooser receives offers");
        ok(!CommanderTables.view(commander,(side+1)%4).getAsJsonObject("choice").has("cards"),"Commander opponent cannot see offers");
        ok(CommanderTables.act(commander,side,CommanderTables.CHOOSE_CARD,0,Target.NONE)==null&&commander.pendingChoice()==null,"Commander input distinct from casting command unit");
    }
}
