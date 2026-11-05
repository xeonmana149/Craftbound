package craftbound.game;

import craftbound.card.*;
import craftbound.network.MatchActionPayload;
import java.util.*;
import net.minecraft.util.RandomSource;
import static craftbound.game.WorkshopChecks.*;

final class CommanderChecks {
    static void run(){
        RelayChecks.run();
        var cards=Cards.collectible().stream().filter(c->DeckRules.allowed(c,CardClass.FIGHTER)).limit(20).map(c->new CardRef(c,false)).toList();
        var lead=cards.stream().filter(c->CommanderMatch.eligible(c.id())).findFirst().orElseThrow();
        for(var rarity:CardRarity.values())for(var candidate:Cards.collectible()) {
            var definition=Cards.get(candidate);
            if(definition.rarity()==rarity && definition.isMob() && !definition.keywords().contains(Keyword.BLOCK))
                ok(CommanderMatch.eligible(candidate),"All collectible mobs can command regardless of rarity");
        }
        var dup=new ArrayList<>(cards);dup.set(19,new CardRef(cards.getFirst().id(),true));
        ok(CommanderMatch.problem(CardClass.FIGHTER,dup,lead.id())!=null,"Commander rejects mixed-finish duplicate");
        ok(CommanderMatch.problem(CardClass.FIGHTER,cards.subList(0,19),lead.id())!=null,"Commander rejects undersized deck");
        ok(CommanderMatch.problem(CardClass.FIGHTER,cards,Cards.WHEAT)!=null,"System tokens cannot command");
        List<MatchPlayer> seats=new ArrayList<>();var picks=new HashMap<UUID,net.minecraft.resources.Identifier>();
        for(int i=0;i<4;i++){var id=UUID.randomUUID();seats.add(new MatchPlayer("Seat "+i,id,CardClass.FIGHTER,null,cards));picks.put(id,lead.id());}
        availability(cards,lead);
        var m=new CommanderMatch(seats,picks,RandomSource.create(17));
        ok(m.turnTicksRemaining==120*20 && m.turnLimitTicks()==120*20 && m.turnWarningTicks()==20*20,
            "Commander starts with two minutes and a twenty-second warning");
        var clockView=CommanderTables.view(m,0);
        ok(clockView.get("clockLimitTicks").getAsInt()==2400 && clockView.get("clockWarningTicks").getAsInt()==400,
            "Commander clients receive the mode-specific timer policy");
        for(int i=0;i<4;i++) {
            var p=m.player(i);ok(p.health==40 && p.hand.size()==5 && p.deck.size()==14 && !p.hand.contains(lead)&&!p.deck.contains(lead),"Private command zone and equal deal");
            var v=CommanderTables.view(m,i).getAsJsonArray("players");
            for(int j=0;j<4;j++)ok(v.get(j).getAsJsonObject().has("hand")==(i==j),"Only the viewer receives card identities");
        }
        for(var s:CommanderTables.view(m,-1).getAsJsonArray("players"))ok(!s.getAsJsonObject().has("hand"),"Spectator cannot see any hand");
        for(int i=0;i<4;i++){ok(m.current==i,"Clockwise turn order");m.endTurn(i);}
        ok(m.current==0 && m.player(0).maxXp==2,"Turn returns to starting seat");
        m.player(0).xp=10;m.cast(0,Target.NONE);var leader=m.player(0).minion(m.unit[0]);
        ok(leader!=null && m.whyCantCommand(0)!=null,"Cannot deploy two commanders");
        m.bounce(leader);ok(!m.player(0).hand.contains(lead)&&m.commandCost(0)==Math.min(10,Cards.get(lead.id()).cost()+2),"Bounce returns commander to command with tax");
        m.player(0).xp=10;m.cast(0,Target.NONE);leader=m.player(0).minion(m.unit[0]);
        leader.totem=true;int old=leader.id;leader.health=0;m.resolveDeaths();leader=m.player(0).minion(m.unit[0]);
        ok(leader!=null&&leader.id!=old,"Totem revival retains command identity");
        m.player(1).board.clear();m.player(2).board.clear();m.player(3).board.clear();
        var guard=mob(m,1,"zombie");m.grantKeyword(guard,Keyword.SHIELD);
        leader.ready=true;leader.summonedThisTurn=false;leader.attack=21;
        ok(m.whyCantAttack(0,leader.id,Target.hero(1))!=null && m.whyCantAttack(0,leader.id,Target.hero(3))==null,"Shield protects its own seat only");
        var blocker=mob(m,2,"cobblestone_wall");var front=mob(m,2,"zombie");
        ok(m.whyCantAttack(0,leader.id,Target.mob(2,blocker.id))!=null,"Third seat front protects its back row");
        ok(CommanderTables.act(m,1,MatchActionPayload.END_TURN,0,Target.NONE)!=null,"Cannot act out of turn");
        m.attack(0,leader.id,Target.hero(3));
        ok(!m.over&&!m.inPlay(3)&&m.player(3).health==19&&m.strikes[0][3]==21,"21 commander damage eliminates exactly that opponent");
        ok(!m.validTargets(0,Target.Kind.ANY_CHARACTER).contains(Target.hero(3)),"Eliminated seats cannot be healed or targeted");
        var warden=new CardRef(id("warden"),false);
        var rival1=mob(m,1,"ravager");var rival2=mob(m,2,"ravager");
        m.player(0).hand.add(warden);m.player(0).xp=10;m.playCard(0,m.player(0).hand.size()-1,Target.NONE);
        ok(rival1.health<7&&rival2.health<7,"Enemy area effect reaches multiple rivals");
        m.concede(0);ok(m.current==1&&!m.over,"Active player forfeit passes to next survivor");
        m.concede(2);ok(m.over&&m.winner==1,"Last living seat wins");
        var clockSeats=new ArrayList<MatchPlayer>();var clockPicks=new HashMap<UUID,net.minecraft.resources.Identifier>();
        for(int i=0;i<4;i++){var who=UUID.randomUUID();clockSeats.add(new MatchPlayer("Clock "+i,who,CardClass.FIGHTER,null,cards));clockPicks.put(who,lead.id());}
        var timed=new CommanderMatch(clockSeats,clockPicks,RandomSource.create(44));
        int initialTurn=timed.turn;
        for(int tick=1;tick<2400;tick++)ok(!timed.tickTurnClock(),"Commander may not time out early");
        ok(timed.current==0 && timed.turn==initialTurn,"Commander receives the full two minutes");
        ok(timed.tickTurnClock() && timed.current==1 && timed.turn==initialTurn+1 && timed.turnTicksRemaining==2400,
            "Commander timeout advances exactly one seat and refreshes the next full turn");
        timed.endTurn(1);
        ok(timed.current==2 && timed.turnTicksRemaining==2400,"Manual Commander end turn also refreshes two minutes");
    }
    private static void availability(List<CardRef> cards,CardRef lead){
        var seats=new ArrayList<MatchPlayer>();var picks=new HashMap<UUID,net.minecraft.resources.Identifier>();
        for(int i=0;i<4;i++){var id=UUID.randomUUID();seats.add(new MatchPlayer("Availability "+i,id,CardClass.FIGHTER,null,cards));picks.put(id,lead.id());}
        var m=new CommanderMatch(seats,picks,RandomSource.create(67));
        for(var player:m.players){player.board.clear();player.hand.clear();player.tool=null;}
        var own=m.player(0);own.xp=10;
        own.hand.add(new CardRef(id("lava_bucket"),false));
        own.hand.add(new CardRef(id("enderman"),false));
        own.hand.add(new CardRef(id("sharpness"),false));
        m.equip(2,new CardRef(id("iron_sword"),false));m.focus(0,1);
        var view=CommanderTables.view(m,0);var moves=view.getAsJsonObject("moves");
        var hand=view.getAsJsonArray("players").get(0).getAsJsonObject().getAsJsonArray("hand");
        var rivals=hand.get(0).getAsJsonObject().getAsJsonArray("rivals");
        ok(rivals.size()==1&&rivals.get(0).getAsInt()==2&&moves.has("play0"),"Tool removal offers the equipped rival even while another rival is focused");
        ok(hand.get(0).getAsJsonObject().get("playable").getAsBoolean()&&m.focus[0]==1,"Rival availability keeps focus unchanged and advertises an available alternate rival");
        ok(!moves.has("play2")&&hand.get(2).getAsJsonObject().getAsJsonArray("rivals").isEmpty(),"Enchantment has no move without an owned tool");
        ok(moves.has("play1")&&moves.getAsJsonArray("play1").isEmpty(),"Optional Spawn targeting still allows a mob on an empty board");
        ok(!CommanderTables.view(m,1).has("moves"),"Nonacting player receives no action options");
        own.xp=0;view=CommanderTables.view(m,0);moves=view.getAsJsonObject("moves");
        ok(!moves.has("play1")&&!moves.has("cast")&&!moves.has("power"),"Unpayable cards, commander and power have no published move");
        own.xp=10;
        ok(CommanderTables.act(m,0,MatchActionPayload.PLAY,1,Target.NONE)==null&&own.board.size()==1,"Targeted mob can be played without a target when no legal target exists");
        own.board.clear();own.xp=10;m.leaders[0]=new CardRef(id("enderman"),false);
        view=CommanderTables.view(m,0);
        ok(view.getAsJsonObject("moves").has("cast")&&view.getAsJsonObject("moves").getAsJsonArray("cast").isEmpty(),"Targeted commander has an untargeted summon when there are no targets");
        ok(CommanderTables.act(m,0,CommanderTables.CAST,0,Target.NONE)==null&&own.board.size()==1,"Commander summon without an optional target resolves");
        own.board.clear();own.xp=10;own.hand.add(new CardRef(id("enderman"),false));
        for(int i=0;i<7;i++)mob(m,0,"zombie");
        view=CommanderTables.view(m,0);moves=view.getAsJsonObject("moves");
        ok(!moves.has("play"+(own.hand.size()-1))&&!moves.has("cast"),"Full mob row blocks hand and command-zone summons even with legal targets");
        m.concede(2);view=CommanderTables.view(m,0);
        hand=view.getAsJsonArray("players").get(0).getAsJsonObject().getAsJsonArray("hand");
        ok(hand.get(0).getAsJsonObject().getAsJsonArray("rivals").isEmpty(),"Eliminated rival cannot remain an available tool-removal focus");
    }
}
