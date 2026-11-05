package craftbound.game;

import com.google.gson.*;
import craftbound.block.ModBlocks;
import craftbound.block.CardTableBlock;
import craftbound.card.*;
import craftbound.network.*;
import java.util.*;
import net.fabricmc.fabric.api.event.lifecycle.v1.*;
import net.fabricmc.fabric.api.networking.v1.*;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** Four real players ready at one table. All moves and private views are generated on the server. */
public final class CommanderTables {
    public static final int CAST=10, LEAVE=11, CHOOSE_CARD=12;
    private record Location(ResourceKey<Level> dimension, BlockPos pos) {}
    private record Pick(int slot,String deck,Identifier leader) {}
    private static final class Pod {
        final String id=UUID.randomUUID().toString();
        final LinkedHashMap<UUID,Pick> ready=new LinkedHashMap<>();
        final Set<UUID> watchers=new HashSet<>();
        final int[] absent=new int[4];
        CommanderMatch match;
        MultiplayerSeats furniture;
        final Location location;
        float yaw;
        Pod(Location location){this.location=location;}
        int revision;
    }
    private static final Map<Location,Pod> pods=new HashMap<>();
    private static final Map<UUID,Pod> members=new HashMap<>();
    static boolean busy(ServerPlayer player) {return members.containsKey(player.getUUID());}
    static boolean reserved(ServerLevel l,BlockPos p) {return pods.containsKey(new Location(l.dimension(),p));}
    static boolean hasGames() {return !pods.isEmpty();}
    static void initialize() {
        PayloadTypeRegistry.serverboundPlay().register(CommanderJoinPayload.TYPE,CommanderJoinPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(CommanderInputPayload.TYPE,CommanderInputPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(CommanderViewPayload.TYPE,CommanderViewPayload.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(CommanderJoinPayload.TYPE,(p,c)->join(c.player(),p));
        ServerPlayNetworking.registerGlobalReceiver(CommanderInputPayload.TYPE,(p,c)->input(c.player(),p));
        ServerTickEvents.END_SERVER_TICK.register(CommanderTables::tick);
        ServerLifecycleEvents.SERVER_STOPPING.register(server->{for(var pod:pods.values())if(pod.furniture!=null)pod.furniture.close();pods.clear();members.clear();});
    }
    private static boolean nearby(ServerPlayer p, Location k, int range) {
        return p!=null && p.level().dimension().equals(k.dimension) && p.distanceToSqr(Vec3.atCenterOf(k.pos))<=range*range;
    }
    static boolean open(ServerPlayer p,BlockPos pos) {
        Pod pod=members.get(p.getUUID());
        if(pod==null) {
            pod=pods.get(new Location(p.level().dimension(),pos));
            if(pod==null || pod.match==null || MatchManager.inMatch(p)) return false;
            pod.watchers.add(p.getUUID());
        }
        if(pod.furniture!=null)pod.furniture.reopen(p);
        send(p,pod,true);return true;
    }
    static String waiting(ServerLevel level,BlockPos pos) {
        Pod pod=pods.get(new Location(level.dimension(),pos));
        return pod!=null && pod.match==null?"Commander: "+pod.ready.size()+"/4":"";
    }
    private static String validate(ServerPlayer p, Pick pick) {
        String issue=MatchManager.whyCantPlay(p,pick.slot,pick.deck);
        if(issue!=null) return Component.translatable(issue,MatchManager.PRACTICE_MIN_DECK).getString();
        var choice=MatchManager.deckIn(p,pick.slot,pick.deck);
        return CommanderMatch.problem(choice.deckClass(),choice.cards(),pick.leader);
    }
    static CommanderMatch matchAt(ServerLevel level,BlockPos pos){var pod=pods.get(new Location(level.dimension(),pos));return pod==null?null:pod.match;}
    static void join(ServerPlayer p,CommanderJoinPayload msg) {
        ServerLevel l=(ServerLevel)p.level(); Location k=new Location(l.dimension(),CardTableBlock.anchor(l,msg.table()).immutable());
        if(!nearby(p,k,6) || !l.getBlockState(k.pos).is(ModBlocks.COMMANDER_TABLE) || MatchManager.inMatch(p) || TableLobby.waiting(p,k.pos))return;
        Pod pod=pods.get(k);
        if(pod==null && MatchManager.tableInUse(l,k.pos) || pod!=null && pod.match!=null)return;
        if(!TableSeats.canJoinFromVehicle(p,k.pos) || TableSeats.ensure(l,k.pos,net.minecraft.core.Direction.NORTH).size()!=4) {
            ServerPlayNetworking.send(p,new TableLobbyPayload(k.pos,"","The four chairs need clear space and solid ground.",false,DeveloperAccess.allowed(p),2));return;
        }
        Identifier id=Identifier.tryParse(msg.leader()); if(id==null) return;
        Pick pick=new Pick(msg.slot(),msg.deckId(),id);
        String problem=validate(p,pick);
        if(problem!=null) {ServerPlayNetworking.send(p,new TableLobbyPayload(k.pos,"",problem,false,DeveloperAccess.allowed(p),2));return;}
        if(pod==null){pod=new Pod(k);pods.put(k,pod);}
        pod.ready.put(p.getUUID(),pick);members.put(p.getUUID(),pod);
        if(pod.ready.size()==CommanderMatch.SEATS) {
            List<MatchPlayer> entrants=new ArrayList<>(); Map<UUID,Identifier> picks=new HashMap<>();
            for(var entry:new ArrayList<>(pod.ready.entrySet())) {
                var other=l.getServer().getPlayerList().getPlayer(entry.getKey()); var chosen=entry.getValue();
                if(!nearby(other,k,8) || validate(other,chosen)!=null) {
                    removeReady(pod,entry.getKey());
                    if(other!=null){sendClose(other); other.sendSystemMessage(Component.literal("Commander: recheck your deck and ready up at the table again."));}continue;
                }
                var deck=MatchManager.deckIn(other,chosen.slot,chosen.deck);
                MatchPlayer seat=new MatchPlayer(other.getName().getString(),other.getUUID(),deck.deckClass(),deck.sleeve(),deck.cards());
                seat.deckName=deck.name(); entrants.add(seat);picks.put(other.getUUID(),chosen.leader);
            }
            if(entrants.size()==4) {
                var candidate=new CommanderMatch(entrants,picks,p.getRandom());
                var players=new ArrayList<ServerPlayer>();
                for(int s=0;s<4;s++)players.add(l.getServer().getPlayerList().getPlayer(candidate.player(s).playerId));
                var furniture=MultiplayerSeats.start(l,k.pos,players);
                if(furniture==null) {
                    removeReady(pod,p.getUUID());
                    ServerPlayNetworking.send(p,new TableLobbyPayload(k.pos,"","Keep all four chairs clear before joining.",false,DeveloperAccess.allowed(p),2));
                } else {
                    pod.match=candidate;pod.furniture=furniture;pod.yaw=furniture.yaw();
                    candidate.boardDesign=craftbound.board.BoardCollection.selected(players.get(p.getRandom().nextInt(4)));
                }
            }
        }
        pod.revision++;broadcast(l.getServer(),pod,true);
    }
    private static void removeReady(Pod pod,UUID id){pod.ready.remove(id);members.remove(id,pod);}
    private static void sendClose(ServerPlayer p){ServerPlayNetworking.send(p,new CommanderViewPayload("",false));}
    private static void input(ServerPlayer p,CommanderInputPayload msg) {
        Pod pod=members.get(p.getUUID()); if(pod==null || !pod.id.equals(msg.session()))return;
        MinecraftServer server=p.level().getServer();
        if(pod.match==null) {
            if(msg.action()!=LEAVE)return;
            removeReady(pod,p.getUUID());sendClose(p);pod.revision++;broadcast(server,pod,false);
            if(pod.ready.isEmpty())release(pod);return;
        }
        if(msg.revision()!=pod.revision)return;
        var m=pod.match; int s=m.sideOf(p.getUUID());
        if(!m.inPlay(s)||m.over)return;
        if(msg.action()==CHOOSE_CARD) { if(!m.chooseFromHand(s,msg.index()))return; }
        else if(msg.action()==MatchActionPayload.CONCEDE) m.concede(s);
        else {
            if(m.current!=s || !m.inPlay(msg.rival()) || msg.rival()==s) return;
            int a=msg.action(),i=msg.index(); Target t=msg.targetSide()==-1?Target.NONE:msg.targetId()==0?Target.hero(msg.targetSide()):Target.mob(msg.targetSide(),msg.targetId());
            if(!t.isNone() && (!m.inPlay(t.side()) || !t.hero() && m.player(t.side()).minion(t.minionId())==null))return;
            int previous=m.focus[s];m.focus(s,msg.rival());
            String invalid=act(m,s,a,i,t);
            if(invalid!=null){m.focus[s]=previous;send(p,pod,false,invalid);return;}
        }
        changed(server,pod);
    }
    static String act(CommanderMatch m,int s,int a,int i,Target t) {
        if(a==CHOOSE_CARD)return m.chooseFromHand(s,i)?null:"That choice is no longer available.";
        if(!m.inPlay(s)||m.current!=s||m.over)return "Wait for your turn.";
        switch(a) {
            case MatchActionPayload.PLAY,CAST -> {
                String issue=a==CAST?m.whyCantCommand(s):m.whyCantPlay(s,i);if(issue!=null)return issue;
                CardRef ref=a==CAST?m.leaders[s]:m.player(s).hand.get(i);
                var kind=CardEffects.targetKind(ref.id());var choices=m.validTargets(s,kind,ref.id());
                if(!t.isNone() && !choices.contains(t) || t.isNone() && !choices.isEmpty())return "Choose a valid target.";
                if(a==CAST)m.cast(s,t);else m.playCard(s,i,t);
            }
            case MatchActionPayload.ATTACK -> {String issue=m.whyCantAttack(s,i,t);if(issue!=null)return issue;m.attack(s,i,t);}
            case MatchActionPayload.POWER -> {String issue=m.whyCantUsePower(s);if(issue!=null)return issue;if(!HeroPowers.validTarget(m,s,t))return "Choose a valid target.";m.usePower(s,t);}
            case MatchActionPayload.END_TURN -> m.endTurn(s);
            default -> {return "Unknown action.";}
        }
        return null;
    }
    private static void changed(MinecraftServer server,Pod pod) {
        pod.revision++;broadcast(server,pod,false);
        // Commander views use resolved state, not the duel animation queue.
        pod.match.events.clear();
        if(pod.match.over) release(pod);
    }
    private static void release(Pod pod){if(pod.furniture!=null)pod.furniture.close();for(UUID id:pod.ready.keySet())members.remove(id,pod);pods.values().remove(pod);}
    private static void tick(MinecraftServer server) {
        for(var entry:new ArrayList<>(pods.entrySet())) {
            Pod pod=entry.getValue();Location k=entry.getKey();var l=server.getLevel(k.dimension);
            if(server.getTickCount()%20==0) {
                if(l==null || !l.hasChunkAt(k.pos) || !l.getBlockState(k.pos).is(ModBlocks.COMMANDER_TABLE)) {
                    for(UUID id:audience(pod)){var p=server.getPlayerList().getPlayer(id);if(p!=null){sendClose(p);p.sendSystemMessage(Component.literal("The Commander table is no longer available."));}}
                    release(pod);continue;
                }
                pod.watchers.removeIf(id->!nearby(server.getPlayerList().getPlayer(id),k,32));
                if(pod.match==null) {
                    boolean dirty=false;for(UUID id:new ArrayList<>(pod.ready.keySet())) {
                        var p=server.getPlayerList().getPlayer(id);
                        if(!nearby(p,k,8)){removeReady(pod,id);if(p!=null)sendClose(p);dirty=true;}
                    }
                    if(pod.ready.isEmpty()){release(pod);continue;}
                    if(dirty){pod.revision++;broadcast(server,pod,false);}
                }else {
                    var m=pod.match;boolean dirty=false;
                    for(int s=0;s<4&&!m.over;s++)if(m.inPlay(s)){
                        var online=server.getPlayerList().getPlayer(m.player(s).playerId);
                        if(online!=null)pod.absent[s]=0;
                        else if(++pod.absent[s]>=90){m.concede(s);dirty=true;}
                    }
                    if(dirty){changed(server,pod);continue;}
                }
            }
            if(pod.match!=null && !pod.match.over && pod.match.pendingChoice()!=null) {
                if(--pod.match.choiceTicksRemaining<=0){pod.match.resolveChoiceRandomly();changed(server,pod);}
                continue;
            }
            if(pod.match!=null && pod.match.tickTurnClock())changed(server,pod);
            if(pod.match!=null && !pod.match.over && server.getTickCount()%20==0) {
                var clock=new TurnClockPayload(pod.match.turn,pod.match.current,pod.match.turnTicksRemaining,false);
                for(UUID id:audience(pod)){var online=server.getPlayerList().getPlayer(id);if(online!=null)ServerPlayNetworking.send(online,clock);}
            }
        }
    }
    private static Set<UUID> audience(Pod p){Set<UUID> ids=new HashSet<>(p.ready.keySet());ids.addAll(p.watchers);return ids;}
    private static void broadcast(MinecraftServer server,Pod pod,boolean open) {
        if(pod.match!=null) {
            var level=server.getLevel(pod.location.dimension());
            var data=level==null?null:CardTableBlock.data(level,pod.location.pos());
            if(data!=null)data.board(pod.match.over?"":publicBoard(pod.match),pod.yaw);
        }
        for(UUID id:audience(pod)){var p=server.getPlayerList().getPlayer(id);if(p!=null)send(p,pod,open);}
    }
    private static void send(ServerPlayer p,Pod pod,boolean open) {send(p,pod,open,null);}
    private static void send(ServerPlayer p,Pod pod,boolean open,String notice) {
        JsonObject out;
        if(pod.match==null){
            out=new JsonObject();JsonArray names=new JsonArray();
            var list=p.level().getServer().getPlayerList();
            for(UUID id:pod.ready.keySet()){var other=list.getPlayer(id);if(other!=null)names.add(other.getName().getString());}
            out.add("waiting",names);
        }else out=view(pod.match,pod.match.sideOf(p.getUUID()));
        if(notice!=null)out.addProperty("notice",notice);
        out.addProperty("session",pod.id);out.addProperty("revision",pod.revision);
        ServerPlayNetworking.send(p,new CommanderViewPayload(out.toString(),open));
    }
    static String publicBoard(CommanderMatch match) {
        var board=JsonParser.parseString(MatchView.publicBoard(match,false)).getAsJsonObject();
        board.addProperty("commander",true);
        for(int s=0;s<4;s++)board.getAsJsonArray("players").get(s).getAsJsonObject().addProperty("alive",match.inPlay(s));
        return board.toString();
    }
    public static JsonObject view(CommanderMatch m,int viewer) {
        JsonObject out=JsonParser.parseString(MatchView.build(m,viewer,m.events.size())).getAsJsonObject();
        out.remove("log"); out.remove("events"); // This screen uses public resolved state, never historic hand events.
        for(int s=0;s<4;s++) {
            var seat=out.getAsJsonArray("players").get(s).getAsJsonObject();
            seat.addProperty("alive",m.inPlay(s));seat.addProperty("leader",m.leaders[s].id().toString());
            seat.addProperty("leaderEnch",m.leaders[s].enchanted());seat.addProperty("commandUnit",m.unit[s]);
            seat.addProperty("commandCost",m.commandCost(s));seat.addProperty("deployed",m.player(s).minion(m.unit[s])!=null);
            var hits=new JsonArray();for(int damage:m.strikes[s])hits.add(damage);seat.add("strikes",hits);
        }
        // Precompute legal targets for the acting player only. The server checks them again on receipt.
        if(viewer>=0 && m.current==viewer && !m.over) {
            var moves=new JsonObject();
            var own=out.getAsJsonArray("players").get(viewer).getAsJsonObject();
            var hand=own.getAsJsonArray("hand");
            for(int i=0;i<m.player(viewer).hand.size();i++) {
                var ref=m.player(viewer).hand.get(i);
                var rivals=availableRivals(m,viewer,i,false);
                var card=hand.get(i).getAsJsonObject();
                card.add("rivals",rivals);card.addProperty("playable",!rivals.isEmpty());
                if(!rivals.isEmpty())addCardTargets(m,viewer,moves,"play"+i,ref);
            }
            var commandRivals=availableRivals(m,viewer,0,true);
            own.add("rivals",commandRivals);
            if(!commandRivals.isEmpty())addCardTargets(m,viewer,moves,"cast",m.leaders[viewer]);
            if(m.whyCantUsePower(viewer)==null){
                var power=new JsonArray();
                for(var t:m.validTargets(viewer,Target.Kind.ANY_CHARACTER)) if(HeroPowers.validTarget(m,viewer,t))power.add(key(t));
                moves.add("power",power);
            }
            var attackers=new ArrayList<Integer>();attackers.add(Match.HERO);
            for(var mob:m.player(viewer).board)attackers.add(mob.id);
            for(int id:attackers){
                var targets=new JsonArray();
                for(var t:m.validTargets(viewer,Target.Kind.ANY_CHARACTER))if(m.whyCantAttack(viewer,id,t)==null)targets.add(key(t));
                moves.add("attack"+id,targets);
            }
            out.add("moves",moves);
        }
        return out;
    }
    // Focus affects singular enemy effects. Report every legal rival without changing the match.
    private static JsonArray availableRivals(CommanderMatch m,int side,int handIndex,boolean commander){
        int previous=m.focus[side];var rivals=new JsonArray();
        try {
            for(int other=0;other<CommanderMatch.SEATS;other++)if(other!=side&&m.inPlay(other)){
                m.focus[side]=other;
                if((commander?m.whyCantCommand(side):m.whyCantPlay(side,handIndex))==null)rivals.add(other);
            }
        } finally {m.focus[side]=previous;}
        return rivals;
    }
    private static String key(Target t){return t.side()+":"+(t.hero()?0:t.minionId());}
    private static void addCardTargets(CommanderMatch m,int viewer,JsonObject moves,String key,CardRef card){
        var list=new JsonArray();for(var t:m.validTargets(viewer,CardEffects.targetKind(card.id()),card.id()))list.add(key(t));moves.add(key,list);
    }
    private CommanderTables(){}
}
