package craftbound.game;

import craftbound.card.*;
import craftbound.network.MatchActionPayload;
import java.util.*;
import net.minecraft.util.RandomSource;
import static craftbound.game.WorkshopChecks.*;

final class RelayChecks {
    private static MatchPlayer captain(String name,CardClass cls) {
        List<CardRef> deck=Cards.collectible().stream().filter(id->DeckRules.allowed(id,cls)).limit(15).map(id->new CardRef(id,false)).toList();
        return new MatchPlayer(name,UUID.randomUUID(),cls,null,deck);
    }
    static RelayMatch table() {
        return new RelayMatch(new MatchPlayer[][]{{captain("A",CardClass.FIGHTER),captain("C",CardClass.CHEF)},
            {captain("B",CardClass.FARMER),captain("D",CardClass.TAMER)}},RandomSource.create(7493));
    }
    static void run() {
        var m=table();int side=m.current,ally=RelayMatch.partnerSeat(side),foe=1-side,otherFoe=RelayMatch.partnerSeat(foe);
        var me=m.player(side);var partner=m.player(ally);
        ok(m.turn==1 && m.players.length==4,"Duos has four distinct hero seats and one shared turn number");
        for(int s=0;s<4;s++){
            ok(m.player(s).health==30 && m.player(s).hand.size()==4 && m.player(s).deck.size()==11,"Equal four private opening hands");
            ok(m.player(s).xp==(m.isTurnSeat(s)?1:0),"Both starting captains gain 1 personal XP but skip first draw");
            ok(m.yourMove(s)==m.isTurnSeat(s),"Both active teammates may act");
        }
        var first=mob(m,side,"zombie");var guard=mob(m,foe,"turtle");
        first.ready=true;first.summonedThisTurn=false;
        ok(m.act(partner.playerId,MatchActionPayload.ATTACK,first.id,Target.hero(otherFoe))!=null,"Cannot order an ally's unit");
        ok(m.whyCantAttack(side,first.id,Target.hero(foe))!=null && m.whyCantAttack(side,first.id,Target.hero(otherFoe))==null,"Shield protects its owner's front, not the other opponent");
        int dmg=m.attackOf(first);
        ok(m.act(me.playerId,MatchActionPayload.ATTACK,first.id,Target.hero(otherFoe))==null && m.player(foe).health==30-dmg && m.player(otherFoe).health==30-dmg,"Hitting either hero changes one shared team HP pool");
        m.healHero(foe,1);ok(m.player(otherFoe).health==31-dmg,"Healing either captain updates both");
        ok(m.player(ally).board.isEmpty() && m.player(side).minion(first.id)==first,"Board ownership stays separate");
        m.equip(side,new CardRef(Cards.CRAFTED_SWORD,false));ok(m.player(ally).tool==null,"Tools stay personal");
        var stamp=new RelayTables.SeatRevisions();stamp.observe(m,-1);int[] original=stamp.versions.clone();
        stamp.observe(m,side);ok(stamp.versions[side]>original[side] && stamp.versions[ally]==original[ally],"Acting bumps own input revision without invalidating an ally");
        m.player(ally).hand.removeFirst();stamp.observe(m,side);ok(stamp.versions[ally]>original[ally],"A changed hand invalidates that teammate's stale index");
        m.endTurn(side);ok(!m.yourMove(side) && m.yourMove(ally) && m.turn==1,"Ready locks only one's own moves");
        m.endTurn(side);ok(m.yourMove(side) && m.yourMove(ally),"A player can unready while their partner plays");
        int handSize=m.player(foe).hand.size();
        m.endTurn(side);m.endTurn(ally);
        ok(m.turn==2 && m.current==foe && m.player(foe).hand.size()==handSize+1 && m.player(otherFoe).hand.size()==handSize+1,"Both opposing players refresh and draw once after joint readiness");
        ok(m.player(foe).maxXp==1 && m.player(otherFoe).maxXp==1 && !m.ready[side] && !m.ready[ally],"Both team members refresh, ready votes clear");
        var any=UUID.randomUUID();
        for(int s=0;s<4;s++){
            var viewer=m.player(s);var v=RelayTables.view(m,viewer.playerId);
            var privateHand=v.getAsJsonArray("hand");ok(privateHand.size()==viewer.hand.size(),"Private hand matches viewer seat");
            for(int i=0;i<privateHand.size();i++)ok(privateHand.get(i).getAsJsonObject().get("id").getAsString().equals(viewer.hand.get(i).id().toString()),"Correct private card identity");
            for(var row:v.getAsJsonArray("players"))ok(!row.getAsJsonObject().has("hand"),"No other hand travels in seat data");
        }
        int publicEntries=m.publicBattleLog.size();
        m.event(MatchEvent.Type.DRAW,side,0,new CardRef(id("warden"),false),0,0,Target.NONE);
        m.event(MatchEvent.Type.GIVE,side,0,new CardRef(id("warden"),false),0,0,Target.NONE);
        m.event(MatchEvent.Type.BURN,side,0,new CardRef(id("warden"),false),0,0,Target.NONE);
        ok(m.publicBattleLog.size()==publicEntries,"Private hand events never enter the public multiplayer history");
        m.event(MatchEvent.Type.PLAY,side,0,new CardRef(id("zombie"),false),0,0,Target.NONE);
        ok(m.publicBattleLog.size()==publicEntries+1,"Public plays enter multiplayer history");
        var spectator=RelayTables.view(m,any);
        ok(spectator.getAsJsonArray("publicLog").size()==m.publicBattleLog.size(),"Spectators receive only the filtered public history");
        m.events.clear();
        m.event(MatchEvent.Type.GIVE,side,0,new CardRef(id("warden"),false),0,0,Target.NONE);
        ok(!RelayTables.view(m,any).has("publicAttack"),"Private card events cannot produce a public attack trail");
        ok(!RelayTables.view(m,any).has("publicCast"),"A private event must never be replayed as a public cast");
        var castCard = new CardRef(id("arrow"), false);
        m.event(MatchEvent.Type.PLAY,side,Match.HERO,castCard,2,0,Target.mob(otherFoe,12));
        var cast=RelayTables.view(m,any).getAsJsonObject("publicCast");
        ok(cast.get("card").getAsString().equals(castCard.id().toString()) && cast.get("from").getAsInt()==side
                && cast.get("to").getAsInt()==otherFoe && cast.get("target").getAsInt()==12,
                "A spectator sees the played card and its original target even after resolution");
        m.event(MatchEvent.Type.ATTACK,side,17,new CardRef(id("zombie"),false),2,0,Target.hero(otherFoe));
        var trail=RelayTables.view(m,any).getAsJsonObject("publicAttack");
        ok(trail.size()==4&&trail.get("from").getAsInt()==side&&trail.get("source").getAsInt()==17
                &&trail.get("to").getAsInt()==otherFoe&&trail.get("target").getAsInt()==Match.HERO,
                "Public attack carries only the source and destination, including a hero across lanes");
        ok(spectator.getAsJsonArray("hand").isEmpty()&&spectator.getAsJsonObject("moves").isEmpty()&&!spectator.has("log"),"Spectators have only public board data");
        m.endTurn(foe);m.turnTicksRemaining=1;
        ok(m.tickTurnClock() && m.current==side && m.turn==3 && me.xp==2 && partner.xp==2,"Timeout advances once for both players");
        ok(me.hand.size()==5 && partner.hand.size()==4,"Active team's second turn draws for both");
        m.damageHero(otherFoe,100);m.checkWinner();ok(m.over&&m.winner==side && m.resultReason.contains("Health"),"Lethal ends the game for both teammates");
        var focusGame=table();int owner=focusGame.current, rival=1-owner, equipped=RelayMatch.partnerSeat(rival);
        focusGame.equip(equipped,new CardRef(Cards.CRAFTED_SWORD,false));
        focusGame.player(owner).hand.add(new CardRef(id("lava_bucket"),false));focusGame.player(owner).xp=10;
        int bucket=focusGame.player(owner).hand.size()-1;
        var legalRivals=RelayTables.view(focusGame,focusGame.player(owner).playerId).getAsJsonArray("hand")
            .get(bucket).getAsJsonObject().getAsJsonArray("rivals");
        ok(legalRivals.size()==1 && legalRivals.get(0).getAsInt()==equipped && focusGame.focus[owner]==rival,
            "Lava Bucket offers the armed rival before player focus changes; rendering never commits a focus");
        ok(focusGame.act(focusGame.player(owner).playerId,MatchActionPayload.PLAY,bucket,equipped,Target.NONE)==null &&
            focusGame.player(equipped).tool==null,"Only the chosen rival's tool is destroyed");
        var empty=table();int loser=empty.current^2;empty.player(loser).deck.clear();
        empty.draw(loser);ok(empty.over&&empty.winner==1-RelayMatch.teamOf(loser)&&empty.resultReason.contains("empty deck"),"An empty draw by either player loses the team immediately");
        var quit=table();quit.concede(3);ok(quit.over&&quit.winner==0&&quit.resultReason.contains("conceded"),"Concession ends the whole team's match");
        var offline=table();offline.disconnected(offline.player(2).playerId);ok(offline.over&&offline.winner==1&&offline.resultReason.contains("reconnect"),"Offline seat forfeits the team after reconnect grace expires");
    }
    private RelayChecks(){}
}