package craftbound.game;

import craftbound.card.*;
import craftbound.network.MatchActionPayload;
import java.util.*;
import net.minecraft.util.RandomSource;
import static craftbound.game.WorkshopChecks.*;

final class CommanderChecks {
    static void run(){
        RelayChecks.run();
        var cards=Cards.collectible().stream().filter(c->DeckRules.allowed(c,CardClass.FIGHTER)).limit(CommanderMatch.DECK_SIZE).map(c->new CardRef(c,false)).toList();
        var lead=cards.stream().filter(c->CommanderMatch.eligible(c.id())).findFirst().orElseThrow();
        for(var rarity:CardRarity.values())for(var candidate:Cards.collectible()) {
            var definition=Cards.get(candidate);
            if(definition.rarity()==rarity && definition.isMob() && !definition.keywords().contains(Keyword.BLOCK))
                ok(CommanderMatch.eligible(candidate)==(rarity==CardRarity.LEGENDARY),"Only collectible legendary mobs may command");
        }
        var dup=new ArrayList<>(cards);dup.set(CommanderMatch.DECK_SIZE-1,new CardRef(cards.getFirst().id(),true));
        ok(CommanderMatch.problem(CardClass.FIGHTER,dup,lead.id())!=null,"Commander rejects mixed-finish duplicate");
        ok(CommanderMatch.problem(CardClass.FIGHTER,cards.subList(0,CommanderMatch.DECK_SIZE-1),lead.id())!=null,"Commander rejects undersized deck");
        ok(CommanderMatch.problem(CardClass.FIGHTER,cards,Cards.WHEAT)!=null,"System tokens cannot command");
        List<MatchPlayer> seats=new ArrayList<>();var picks=new HashMap<UUID,net.minecraft.resources.Identifier>();
        for(int i=0;i<4;i++){var id=UUID.randomUUID();seats.add(new MatchPlayer("Seat "+i,id,CardClass.FIGHTER,null,cards));picks.put(id,lead.id());}
        availability(cards,lead);
        historyChecks(cards,lead);
        savedFormats(cards,lead);
        var m=new CommanderMatch(seats,picks,RandomSource.create(17));
        ok(m.turnTicksRemaining==120*20 && m.turnLimitTicks()==120*20 && m.turnWarningTicks()==20*20,
            "Commander starts with two minutes and a twenty-second warning");
        var clockView=CommanderTables.view(m,0);
        ok(clockView.get("clockLimitTicks").getAsInt()==2400 && clockView.get("clockWarningTicks").getAsInt()==400,
            "Commander clients receive the mode-specific timer policy");
        for(int i=0;i<4;i++) {
            var p=m.player(i);ok(p.health==40 && p.hand.size()==5 && p.deck.size()==CommanderMatch.DECK_SIZE-6 && !p.hand.contains(lead)&&!p.deck.contains(lead),"Private command zone and equal deal");
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
    private static void savedFormats(List<CardRef> cards,CardRef lead) {
        var cls=CardClass.FIGHTER;
        var key=craftbound.deck.BinderDecks.creation(craftbound.deck.DeckFormat.COMMANDER,cls,lead.id());
        var draft=craftbound.deck.BinderDecks.create(key,true,(id,foil)->foil);
        ok(draft!=null && draft.leads(lead.id()) && draft.size()==1 && draft.cards().count(lead.id(),true)==1,"Pick and store an owned leader before adding cards");
        ok(craftbound.deck.BinderDecks.create(key,false,(id,foil)->foil)==null,"Leader must be owned in the selected finish");
        var basic=cards.stream().filter(c->!CommanderMatch.eligible(c.id())).findFirst().orElseThrow().id();
        ok(craftbound.deck.BinderDecks.create(craftbound.deck.BinderDecks.creation(craftbound.deck.DeckFormat.COMMANDER,cls,basic),false,(id,f)->true)==null,"Nonlegendary card cannot lead a saved deck");
        var legacy=com.mojang.serialization.JsonOps.INSTANCE;
        var deck=craftbound.deck.DeckContents.CODEC.parse(legacy,new com.google.gson.JsonObject()).getOrThrow();
        ok(deck.format()==craftbound.deck.DeckFormat.DUEL && deck.commander().isEmpty(),"Older lists migrate to 1v1 without changing their cards");
        var duel=craftbound.deck.BinderDecks.create("FIGHTER",false,(id,f)->true).add(basic,false,2);
        var team=craftbound.deck.BinderDecks.create("DUOS|FIGHTER|",false,(id,f)->true);
        ok(team.format()==craftbound.deck.DeckFormat.DUOS && duel.format()==craftbound.deck.DeckFormat.DUEL,"Duel and Duos drafts keep separate formats");
        var copied=craftbound.deck.BinderDecks.copyCards(draft,duel,(id,f)->2);
        ok(copied.leads(lead.id()) && copied.copies(basic)==1 && duel.copies(basic)==2,"Copying a legacy list respects singleton and leaves the source intact");
        ok(copied.clearCards().equals(draft),"Clearing keeps the legendary leader and its finish");
        for (CardRef card:cards) if(!draft.leads(card.id())) draft=draft.add(card.id(),false,1);
        ok(draft.size()==40 && craftbound.deck.DeckBuilding.whyCantPlay(draft,(id,f)->1)==null,"40 including the leader is a playable Commander list");
        ok(craftbound.deck.DeckBuilding.whyNot(draft,basic)!=null && craftbound.deck.DeckBuilding.whyCantPlay(copied,(id,f)->1)!=null,"Full decks reject additions and incomplete decks cannot queue");
        var other=cards.stream().filter(c->CommanderMatch.eligible(c.id()) && !c.id().equals(lead.id())).findFirst().orElseThrow();
        var swapped=draft.withCommander(other.id(),true);
        ok(swapped.leads(other.id()) && swapped.copies(lead.id())==0 && swapped.copies(other.id())==1 && swapped.size()==39,"Switching to an already-listed leader consumes only one slot");
        var encoded=craftbound.deck.DeckContents.CODEC.encodeStart(legacy,draft).getOrThrow();
        ok(craftbound.deck.DeckContents.CODEC.parse(legacy,encoded).getOrThrow().equals(draft),"Saved format and commander survive disk reload");
        var buf=io.netty.buffer.Unpooled.buffer();
        try {
            craftbound.deck.DeckContents.STREAM_CODEC.encode(buf,draft);
            ok(craftbound.deck.DeckContents.STREAM_CODEC.decode(buf).equals(draft),"Deck sync preserves commander and format");
        } finally {buf.release();}
    }

    private static void historyChecks(List<CardRef> cards, CardRef lead) {
        var entrants = new ArrayList<MatchPlayer>();
        var picks = new HashMap<UUID,net.minecraft.resources.Identifier>();
        for (int s=0;s<4;s++) {
            UUID id = UUID.randomUUID(); picks.put(id,lead.id());
            entrants.add(new MatchPlayer("Player "+s,id,CardClass.FIGHTER,null,cards));
        }
        var m = new CommanderMatch(entrants,picks,RandomSource.create(16));
        var h = m.history;
        ok(h.round()==1 && h.slice(0,1).getFirst().kind()==CommanderHistory.Kind.TURN,"History opens before start-of-turn events");
        var secret = new CardRef(id("totem_of_undying"),false);
        int offset = h.size();
        for (var kind : List.of(MatchEvent.Type.DRAW,MatchEvent.Type.GIVE,MatchEvent.Type.BURN,
                MatchEvent.Type.HAND_REVEAL,MatchEvent.Type.HAND_STEAL,MatchEvent.Type.HAND_SHUFFLE))
            m.event(kind,0,Match.HERO,secret,0,0,Target.hero(1));
        var entries = h.slice(offset,h.size());
        ok(entries.size()==6 && entries.stream().allMatch(e -> e.kind()==CommanderHistory.Kind.DETAIL
                && !e.text().contains(Match.name(secret))),"Public journal redacts private hand identities");

        var mob = mob(m,2,"zombie");
        offset = h.size();
        m.event(MatchEvent.Type.ATTACK,1,Match.HERO,null,1,0,Target.mob(2,mob.id));
        m.damage(mob,1,null);
        entries = h.slice(offset,h.size());
        String victim = m.player(2).name+"’s "+Match.name(mob.card);
        ok(entries.getFirst().text().contains(m.player(1).name+"’s hero attacked "+victim)
                && entries.get(1).text().contains(victim+" took 1 damage ("+mob.health+" Health)"),
                "Attack and applied damage name both public combatants and remaining Health");

        m.endTurn(0); m.endTurn(1); m.endTurn(2);
        ok(h.round()==1 && m.current==3,"Four clockwise turns belong to round one");
        m.concede(0); m.endTurn(3);
        ok(h.round()==2 && m.current==1 && m.turn==5,"Round advances when skipped opening seat is eliminated");
        m.concede(2); m.endTurn(1);
        ok(h.round()==2 && m.current==3,"Skipping a middle seat does not advance the round");
        m.endTurn(3);
        ok(h.round()==3 && m.current==1 && m.turn==7,"Two survivors get one turn each per round");
        offset=h.size();
        for(int i=0;i<80;i++) m.event(MatchEvent.Type.DRAW,1,Match.HERO,secret,0,0,null);
        var payload = new craftbound.network.CommanderHistoryPayload("session",offset,
                h.slice(offset,offset+craftbound.network.CommanderHistoryPayload.PAGE_SIZE));
        var buf = new net.minecraft.network.RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(),net.minecraft.core.RegistryAccess.EMPTY);
        try {
            craftbound.network.CommanderHistoryPayload.CODEC.encode(buf,payload);
            ok(payload.equals(craftbound.network.CommanderHistoryPayload.CODEC.decode(buf)),"Public journal packet retains positions and entry kinds");
        } finally { buf.release(); }
        ok(h.slice(0,1).getFirst().round()==1 && h.slice(offset+64,h.size()).size()==16,"Earlier rounds survive multiple network batches");
        m.concede(3);
        ok(h.slice(h.size()-1,h.size()).getFirst().text().contains("wins Commander"),"Final result remains in the journal");
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
