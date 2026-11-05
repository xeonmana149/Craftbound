package craftbound.game;
import java.util.*;
import com.google.gson.*;
import craftbound.card.*;
import craftbound.sleeve.Sleeves;
import net.minecraft.util.RandomSource;

final class ClockSpectatorChecks {
 static void run(){
  var deck=Collections.nCopies(30,new CardRef(Cards.ZOMBIE,false));
  var a=new MatchPlayer("A",UUID.randomUUID(),CardClass.FIGHTER,Sleeves.CLASSIC,deck);
  var b=new MatchPlayer("B",UUID.randomUUID(),CardClass.FARMER,Sleeves.CLASSIC,deck);
  var m=new Match(a,b,RandomSource.create(13));
  for(int i=0;i<1600;i++)WorkshopChecks.ok(!m.tickTurnClock(),"Mulligan has no turn deadline");
  m.mulligan(0,0);m.mulligan(1,0);int turn=m.turn,current=m.current;
  for(int i=1;i<Match.TURN_TICKS;i++)WorkshopChecks.ok(!m.tickTurnClock(),"Clock cannot end a turn early");
  WorkshopChecks.ok(m.tickTurnClock()&&m.turn==turn+1&&m.current==1-current&&m.turnTicksRemaining==Match.TURN_TICKS,"Expiry runs normal end-turn and resets once");
  m.turnTicksRemaining=97;m.pauseRequestedTurn[0]=m.turn;
  var restored=MatchSnapshot.restore(MatchSnapshot.capture(m));
  WorkshopChecks.ok(restored.turnTicksRemaining==97&&restored.pauseRequestedTurn[0]==m.turn,"Snapshot preserves time and request quota until resume succeeds");
  for(int cycle=0;cycle<3;cycle++) {
   restored.pauseRequestedTurn[0]=restored.turn;restored.pauseRequestedTurn[1]=restored.turn;
   restored=MatchSnapshot.restore(MatchSnapshot.capture(restored));
   int savedTurn=restored.turn,savedSide=restored.current;
   restored.resumeFromPause();
   WorkshopChecks.ok(restored.pauseRequestedTurn[0]==-1&&restored.pauseRequestedTurn[1]==-1,"Both players can request another pause after resuming");
   WorkshopChecks.ok(restored.turnTicksRemaining==97&&restored.turn==savedTurn&&restored.current==savedSide,"Repeated resume never grants time or advances the turn");
  }
  m.endTurn(m.current);WorkshopChecks.ok(m.turnTicksRemaining==Match.TURN_TICKS,"Manual end turn resets clock");
  m.player(0).hand.clear();m.player(0).hand.add(new CardRef(Cards.ENDER_DRAGON,true));
  m.player(1).hand.clear();m.player(1).hand.add(new CardRef(Cards.WARDEN,false));
  String pub=MatchView.publicBoard(m,false);
  for(int mask=0;mask<4;mask++){
   var view=TableSpectating.view(pub,m,mask).getAsJsonArray("players");
   for(int side=0;side<2;side++)WorkshopChecks.ok(view.get(side).getAsJsonObject().has("hand")==((mask&(1<<side))!=0),"Each owner's consent is independent");
  }
  WorkshopChecks.ok(!TableSpectating.view(pub,m,0).toString().contains("ender_dragon"),"Revocation removes private hand data");
  WorkshopChecks.ok(!TableSpectating.view(pub,null,3).toString().contains("ender_dragon"),"Paused match never retains old hand grants");
  m.events.clear();
  for(var kind:List.of(MatchEvent.Type.DRAW,MatchEvent.Type.GIVE,MatchEvent.Type.BURN))
   m.events.add(new MatchEvent(kind,0,Match.HERO,new CardRef(Cards.ENDER_DRAGON,true),0,0,null));
  WorkshopChecks.ok(!MatchView.spectatorEvents(m,0,0).toString().contains("ender_dragon"),"Spectator event replay must not bypass hidden hand permissions");
  WorkshopChecks.ok(MatchView.spectatorEvents(m,0,1).toString().contains("ender_dragon"),"A grant allows that owner's hand animation identities");
  WorkshopChecks.ok(!MatchView.spectatorEvents(m,0,2).toString().contains("ender_dragon"),"The other owner's consent does not expose this hand");
  var drop=WorkshopChecks.match();var zombie=WorkshopChecks.mob(drop,0,"zombie");drop.events.clear();drop.damage(zombie,99,null);drop.resolveDeaths();
  var gift=drop.events.stream().filter(e->e.type()==MatchEvent.Type.GIVE).findFirst().orElseThrow();
  WorkshopChecks.ok(gift.source().equals(Target.mob(0,zombie.id)),"A Drop retains its source after the source leaves the board");
  drop.giveCard(0,new CardRef(Cards.ZOMBIE,false));
  WorkshopChecks.ok(drop.events.getLast().source().isNone(),"Effect origin context cannot leak into later unrelated events");
  var owner=m.player(0).playerId;var request=new TableSpectating.Request("r",UUID.randomUUID(),owner,m,0,100);
  WorkshopChecks.ok(request.canAnswer(owner,99,m)&&!request.canAnswer(UUID.randomUUID(),99,m)
   &&!request.canAnswer(owner,100,m)&&!request.canAnswer(owner,99,restored),"Only correct owner of current match answers unexpired requests");
  var ai=new Match(new MatchPlayer("AI",null,CardClass.FARMER,Sleeves.CLASSIC,deck),a,RandomSource.create(14));
  ai.phase=Match.Phase.PLAYING;ai.turnTicksRemaining=1;
  for(int i=0;i<2000;i++)WorkshopChecks.ok(!ai.tickTurnClock()&&ai.turnTicksRemaining==1,"Card Master matches stay untimed");
 }
}
