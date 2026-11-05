package craftbound.game;

import com.google.gson.*;
import craftbound.block.CardTableBlock;
import craftbound.board.BoardCollection;
import craftbound.block.ModBlocks;
import craftbound.network.*;
import java.util.*;
import net.fabricmc.fabric.api.event.lifecycle.v1.*;
import net.fabricmc.fabric.api.networking.v1.*;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public final class RelayTables {
    private record Location(ResourceKey<Level> level, BlockPos pos) {}
    private record Entry(int seat, int slot, String deckId, String name) { int team(){return seat&1;} }
    private static final class Table {
        final String session=UUID.randomUUID().toString();
        final LinkedHashMap<UUID,Entry> seats=new LinkedHashMap<>();
        final Set<UUID> watchers=new HashSet<>();
        final Map<UUID,craftbound.entity.CardSeatEntity> chairs=new HashMap<>();
        final Map<UUID,Integer> absent=new HashMap<>();
        final Location location;
        final SeatRevisions inputs=new SeatRevisions();
        int revision;
        float yaw;
        RelayMatch match;
        MultiplayerSeats furniture;
        Table(Location location) { this.location=location; }
    }
    /** Opposing actions cannot invalidate a player's click unless a hand index or turn changed. */
    static final class SeatRevisions {
        final int[] versions=new int[4];
        private final List<List<CardRef>> hands=new ArrayList<>(List.of(List.of(),List.of(),List.of(),List.of()));
        private int turn=-1;
        void observe(RelayMatch m,int actor) {
            for(int s=0;s<4;s++) {
                List<CardRef> hand=m.player(s).hand;
                boolean handChanged=!hand.equals(hands.get(s));
                if(s==actor || turn!=m.turn || handChanged) versions[s]++;
                if(handChanged) hands.set(s,List.copyOf(hand));
            }
            turn=m.turn;
        }
    }
    private static final Map<Location,Table> tables=new HashMap<>();
    private static final Map<UUID,Table> members=new HashMap<>();

    static void initialize() {
        PayloadTypeRegistry.serverboundPlay().register(RelayJoinPayload.TYPE,RelayJoinPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(RelayInputPayload.TYPE,RelayInputPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(RelayViewPayload.TYPE,RelayViewPayload.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(RelayJoinPayload.TYPE,(packet,ctx)->join(ctx.player(),packet));
        ServerPlayNetworking.registerGlobalReceiver(RelayInputPayload.TYPE,(packet,ctx)->input(ctx.player(),packet));
        ServerTickEvents.END_SERVER_TICK.register(RelayTables::tick);
        ServerLifecycleEvents.SERVER_STOPPING.register(server->{ for(var t:List.copyOf(tables.values()))release(t); members.clear(); tables.clear(); });
    }
    static boolean busy(ServerPlayer p) { return members.containsKey(p.getUUID()); }
    static boolean hasGames() { return !tables.isEmpty(); }
    static boolean reserved(ServerLevel level, BlockPos pos) { return tables.containsKey(new Location(level.dimension(),pos)); }
    static String waiting(ServerLevel level, BlockPos pos) {
        Table t=tables.get(new Location(level.dimension(),pos));
        return t!=null && t.match==null ? "Team Duos · Ember "+count(t,0)+"/2 · Tide "+count(t,1)+"/2" : "";
    }
    /** A waiting lobby reserves individual chairs, not every empty chair around the table. */
    public static boolean canSitWhileWaiting(ServerLevel level,BlockPos table,BlockPos chair) {
        Table t=tables.get(new Location(level.dimension(),table));
        if(t==null || t.match!=null)return false;
        var state=level.getBlockState(table);
        if(!(state.getBlock() instanceof craftbound.block.MultiplayerTableBlock))return false;
        var plans=craftbound.block.MultiplayerTableBlock.chairs(table,state.getValue(craftbound.block.MultiplayerTableBlock.FACING),
                craftbound.block.MultiplayerTableBlock.DUOS);
        for(int seat=0;seat<plans.size();seat++)if(plans.get(seat).pos().equals(chair)) {
            int selected=seat;
            return t.seats.values().stream().noneMatch(entry->entry.seat()==selected);
        }
        return false;
    }
    static RelayMatch matchAt(ServerLevel level,BlockPos pos) {
        Table t=tables.get(new Location(level.dimension(),pos)); return t==null?null:t.match;
    }
    private static long count(Table t,int team) { return t.seats.values().stream().filter(e->e.team()==team).count(); }
    private static boolean nearby(ServerPlayer p,Location location,int radius) {
        return p!=null && p.level().dimension().equals(location.level()) && p.distanceToSqr(Vec3.atCenterOf(location.pos()))<=radius*radius;
    }
    static boolean open(ServerPlayer p,BlockPos pos) {
        Table t=members.get(p.getUUID());
        if(t==null) {
            t=tables.get(new Location(p.level().dimension(),pos));
            if(t==null || t.match==null || MatchManager.inMatch(p)) return false;
            t.watchers.add(p.getUUID());
        }
        if(t.furniture!=null)t.furniture.reopen(p);
        send(t,p,true,""); return true;
    }
    record EntitySeat(BlockPos pos,Vec3 center) {
        static EntitySeat of(craftbound.entity.CardSeatEntity seat){return new EntitySeat(seat.blockPosition(),seat.position());}
    }
    /** Sitting wins over proximity. An approach selects a SIDE; a full side never changes the team. */
    static int chooseChair(List<EntitySeat> chairs,Vec3 position,BlockPos chosen,Set<Integer> reserved,Set<Integer> occupied) {
        if(chairs.size()!=4)return -1;
        if(chosen!=null)for(int i=0;i<4;i++)if(chairs.get(i).pos().equals(chosen))
            return reserved.contains(i)||occupied.contains(i)?-1:i;
        var near=chairs.get(0).center().add(chairs.get(2).center()).scale(.5);
        var far=chairs.get(1).center().add(chairs.get(3).center()).scale(.5);
        int side=position.distanceToSqr(near)<=position.distanceToSqr(far)?0:1;
        int best=-1;double distance=Double.MAX_VALUE;
        for(int i=side;i<4;i+=2)if(!reserved.contains(i)&&!occupied.contains(i)) {
            double d=position.distanceToSqr(chairs.get(i).center());
            if(d<distance){best=i;distance=d;}
        }
        return best;
    }
    private static String validate(ServerPlayer p,Entry e) {
        String issue=MatchManager.whyCantPlay(p,e.slot(),e.deckId());
        if(issue!=null) return Component.translatable(issue,MatchManager.PRACTICE_MIN_DECK).getString();
        var deck=MatchManager.deckIn(p,e.slot(),e.deckId());
        return RelayMatch.deckProblem(deck.deckClass(),deck.cards());
    }
    private static void reject(ServerPlayer p,String issue) {
        JsonObject msg=new JsonObject();
        msg.addProperty("joinError",issue);
        ServerPlayNetworking.send(p,new RelayViewPayload(msg.toString(),false));
    }
    static void join(ServerPlayer p,RelayJoinPayload msg) {
        ServerLevel level=(ServerLevel)p.level();
        Location loc=new Location(level.dimension(),CardTableBlock.anchor(level,msg.table()).immutable());
        if(!p.isAlive() || p.isSpectator() || !nearby(p,loc,6) || !level.getBlockState(loc.pos).is(ModBlocks.TEAM_TABLE)) { reject(p,"Ready at a Team Duos table."); return; }
        if(MatchManager.inMatch(p) || TableLobby.waiting(p,loc.pos)) { reject(p,"Finish or leave your current table queue first."); return; }
        Table t=tables.get(loc);
        if(t==null && MatchManager.tableInUse(level,loc.pos) || t!=null && t.match!=null) { reject(p,"This table already has a game."); return; }
        var chairs=TableSeats.ensure(level,loc.pos,net.minecraft.core.Direction.NORTH);
        if(!TableSeats.canJoinFromVehicle(p,loc.pos) || chairs.size()!=4) {
            reject(p,"The four chairs need clear space and solid ground.");return;
        }
        Set<Integer> reserved=new HashSet<>(), occupied=new HashSet<>();
        if(t!=null)for(var entry:t.seats.values())reserved.add(entry.seat());
        for(int i=0;i<4;i++)if(chairs.get(i).getPassengers().stream().anyMatch(rider->rider!=p))occupied.add(i);
        BlockPos chosen=p.getVehicle() instanceof craftbound.entity.CardSeatEntity chair?chair.blockPosition():null;
        int seat=chooseChair(chairs.stream().map(EntitySeat::of).toList(),p.position(),chosen,reserved,occupied);
        if(seat<0) {reject(p,"Both chairs on this side are occupied or reserved. Stand on the opposite side to join that team.");return;}
        Entry pick=new Entry(seat,msg.slot(),msg.deckId(),p.getName().getString());
        String issue=validate(p,pick);
        if(issue!=null) { reject(p,issue); return; }
        var origin=p.position();float originYaw=p.getYRot();var previousChair=p.getVehicle();
        if(!TableSeats.mount(p,chairs.get(seat))) {
            TableSeats.move(p,origin,originYaw);
            if(previousChair instanceof craftbound.entity.CardSeatEntity old && !old.isRemoved())TableSeats.mount(p,old);
            reject(p,"Unable to sit. Leave your other vehicle and try again.");return;
        }
        if(t==null) { t=new Table(loc); tables.put(loc,t); }
        t.chairs.put(p.getUUID(),chairs.get(seat));
        t.seats.put(p.getUUID(),pick); members.put(p.getUUID(),t);
        if(t.seats.size()==4) {
            MatchPlayer[][] partners=new MatchPlayer[2][2]; int[] counts=new int[2];
            for(var member:List.copyOf(t.seats.entrySet())) {
                UUID id=member.getKey(); Entry e=member.getValue();
                ServerPlayer other=level.getServer().getPlayerList().getPlayer(id);
                if(!nearby(other,loc,8) || validate(other,e)!=null) {
                    remove(t,id);
                    if(other!=null) { close(t,other); other.sendSystemMessage(Component.literal("Your team seat was released. Check your deck and ready again.")); }
                    continue;
                }
                var deck=MatchManager.deckIn(other,e.slot(),e.deckId());
                var pilot=new MatchPlayer(e.name(),id,deck.deckClass(),deck.sleeve(),deck.cards());
                pilot.deckName=deck.name(); partners[e.team()][e.seat()/2]=pilot; counts[e.team()]++;
            }
            if(counts[0]==2 && counts[1]==2) {
                var candidate=new RelayMatch(partners,p.getRandom());
                var players=new ArrayList<ServerPlayer>();
                for(int s=0;s<4;s++)players.add(level.getServer().getPlayerList().getPlayer(candidate.player(s).playerId));
                var furniture=MultiplayerSeats.start(level,loc.pos,players);
                if(furniture==null) {
                    remove(t,p.getUUID()); reject(p,"Keep the four chairs clear, and leave any other vehicle before joining.");
                } else {
                    t.match=candidate;t.furniture=furniture;t.yaw=furniture.yaw();
                    var selected=partners[p.getRandom().nextInt(2)][p.getRandom().nextInt(2)].playerId;
                    t.match.boardDesign=BoardCollection.selected(level.getServer().getPlayerList().getPlayer(selected));
                }
            }
        }
        changed(level.getServer(),t,true);
    }
    static void input(ServerPlayer p,RelayInputPayload msg) {
        UUID id=p.getUUID(); Table t=members.get(id);
        if(t==null || !t.session.equals(msg.session())) return;
        var server=p.level().getServer();
        if(t.match==null) {
            if(msg.action()!=RelayMatch.LEAVE_QUEUE) return;
            remove(t,id); close(t,p);
            changed(server,t,false); return;
        }
        int seat=t.match.sideOf(id);
        if(seat<0)return;
        if(msg.revision()!=t.inputs.versions[seat]) { send(t,p,false,"Your hand or turn changed. Choose your move again."); return; }
        if(msg.targetSide() < -1 || msg.targetSide() > 3 || msg.targetId()<0) return;
        Target target=msg.targetSide()<0 ? Target.NONE : msg.targetId()==0 ? Target.hero(msg.targetSide()) : Target.mob(msg.targetSide(),msg.targetId());
        String reason=t.match.act(id,msg.action(),msg.index(),msg.rival(),target);
        if(reason!=null) { send(t,p,false,reason); return; }
        changed(server,t,false,seat);
    }
    private static void remove(Table t,UUID id) {
        var chair=t.chairs.remove(id);
        if(chair!=null)for(var rider:List.copyOf(chair.getPassengers()))if(rider.getUUID().equals(id))TableSeats.release(rider,chair);
        t.seats.remove(id); members.remove(id,t);
    }
    private static void close(Table t,ServerPlayer p) {
        JsonObject message=new JsonObject();
        message.addProperty("session",t.session); message.addProperty("closed",true);
        ServerPlayNetworking.send(p,new RelayViewPayload(message.toString(),false));
    }
    private static void release(Table t) {
        if(t.furniture!=null)t.furniture.close();
        tables.remove(t.location,t);
        for(UUID id:List.copyOf(t.seats.keySet())) remove(t,id);
    }
    private static void changed(MinecraftServer server,Table t,boolean open) {
        changed(server,t,open,-1);
    }
    private static void changed(MinecraftServer server,Table t,boolean open,int actor) {
        t.revision++;
        if(t.match!=null)t.inputs.observe(t.match,actor);
        Set<UUID> viewers=new HashSet<>(t.watchers);
        for(UUID id:t.seats.keySet()) if(members.get(id)==t) viewers.add(id);
        for(UUID id:viewers) {
            var p=server.getPlayerList().getPlayer(id);
            if(p!=null) send(t,p,open,"");
        }
        if(t.match!=null) {
            t.match.events.clear();
            var level=server.getLevel(t.location.level);
            var be=level==null?null:CardTableBlock.data(level,t.location.pos);
            if(be!=null)be.board(t.match.over?"":publicBoard(t.match),t.yaw);
        }
        if(t.seats.isEmpty() || t.match!=null && t.match.over) release(t);
    }
    private static void tick(MinecraftServer server) {
        for(Table t:List.copyOf(tables.values())) {
            if(server.getTickCount()%20==0) {
                var loc=t.location; var level=server.getLevel(loc.level);
                if(level==null || !level.hasChunkAt(loc.pos) || !level.getBlockState(loc.pos).is(ModBlocks.TEAM_TABLE)) {
                    Set<UUID> viewers=new HashSet<>(t.watchers); viewers.addAll(t.seats.keySet());
                    for(UUID id:viewers) { var p=server.getPlayerList().getPlayer(id); if(p!=null && (members.get(id)==t || t.watchers.contains(id))) {close(t,p);p.sendSystemMessage(Component.literal("The Team Duos table was removed."));} }
                    release(t); continue;
                }
                t.watchers.removeIf(id->{
                    var p=server.getPlayerList().getPlayer(id);
                    if(nearby(p,loc,32)) return false;
                    if(p!=null) close(t,p);
                    return true;
                });
                boolean dirty=false;
                for(UUID id:List.copyOf(t.seats.keySet())) {
                    var online=server.getPlayerList().getPlayer(id);
                    if(t.match==null) {
                        if(!nearby(online,loc,8)) {remove(t,id); if(online!=null)close(t,online); dirty=true;}
                    } else if(!t.match.over) {
                        if(online!=null) t.absent.remove(id);
                        else if(t.absent.merge(id,1,Integer::sum)>=90) {t.match.disconnected(id);dirty=true;}
                    }
                }
                if(dirty) {changed(server,t,false);continue;}
            }
            if(t.match!=null && !t.match.over && t.match.pendingChoice()!=null) {
                if(--t.match.choiceTicksRemaining<=0){t.match.resolveChoiceRandomly();changed(server,t,false);}
                continue;
            }
            if(t.match!=null && t.match.tickTurnClock()) {
                changed(server,t,false);
            }
            if(t.match!=null && !t.match.over && server.getTickCount()%20==0) {
                var audience=new HashSet<>(t.watchers);audience.addAll(t.seats.keySet());
                var clock=new TurnClockPayload(t.match.turn,t.match.current,t.match.turnTicksRemaining,false);
                for(UUID id:audience){var online=server.getPlayerList().getPlayer(id);if(online!=null)ServerPlayNetworking.send(online,clock);}
            }
        }
    }
    private static void send(Table t,ServerPlayer p,boolean open,String notice) {
        JsonObject data;
        if(t.match!=null) data=view(t.match,p.getUUID());
        else {
            data=new JsonObject();var names=new JsonArray();
            for(Entry e:t.seats.values()){var item=new JsonObject();item.addProperty("team",e.team());item.addProperty("seat",e.seat());item.addProperty("name",e.name());names.add(item);}
            data.add("waiting",names);
        }
        int seat=t.match==null?-1:t.match.sideOf(p.getUUID());
        data.addProperty("session",t.session);
        data.addProperty("stateRevision",t.revision);
        data.addProperty("revision",seat<0?t.revision:t.inputs.versions[seat]);
        if(!notice.isEmpty()) data.addProperty("notice",notice);
        ServerPlayNetworking.send(p,new RelayViewPayload(data.toString(),open));
    }
    static JsonObject view(RelayMatch m,UUID viewer) {
        // Match logs and effects can mention a hidden drawn/given card. Start with public
        // board data, then attach a hand only for the actual owner of that seat.
        JsonObject out=JsonParser.parseString(MatchView.build(m,-1,m.events.size())).getAsJsonObject();
        out.remove("log");out.remove("events");
        int side=viewer==null?-1:m.sideOf(viewer);
        MatchView.writeChoice(out,m,side);
        boolean mine=m.yourMove(side);
        out.addProperty("you",side);out.addProperty("team",side<0?-1:RelayMatch.teamOf(side));
        out.addProperty("activeTeam",m.current);out.addProperty("myTurn",mine);
        out.addProperty("ready",side>=0 && m.ready[side]);
        out.addProperty("partnerReady",side>=0 && m.ready[RelayMatch.partnerSeat(side)]);
        out.addProperty("winnerTeam",m.winner);out.addProperty("resultReason",m.resultReason);
        out.addProperty("lastMove",m.lastMove);
        for(int s=0;s<4;s++) {
            var info=out.getAsJsonArray("players").get(s).getAsJsonObject();
            info.addProperty("team",RelayMatch.teamOf(s));info.addProperty("partnerSeat",RelayMatch.partnerSeat(s));
            info.addProperty("ready",m.ready[s]);info.addProperty("me",s==side);info.addProperty("focus",m.focus[s]);
        }
        var hand=new JsonArray(); var moves=new JsonObject();
        if(side>=0) {
            var pilot=m.player(side);
            for(int i=0;i<pilot.hand.size();i++) {
                var ref=pilot.hand.get(i);var card=new JsonObject();
                card.addProperty("id",ref.id().toString());card.addProperty("ench",ref.enchanted());hand.add(card);
                if(mine) {
                    int previous=m.focus[side];
                    JsonArray rivals=new JsonArray();
                    String issue=null;
                    try {
                        for(int opponent=1-RelayMatch.teamOf(side);opponent<4;opponent+=2) {
                            m.focus[side]=opponent;
                            issue=m.whyCantPlay(side,i);
                            if(issue==null)rivals.add(opponent);
                        }
                    } finally {m.focus[side]=previous;}
                    card.add("rivals",rivals);
                    if(rivals.isEmpty())card.addProperty("reason",issue);
                    else moves.add("play"+i,keys(m.validTargets(side,CardEffects.targetKind(ref.id()),ref.id())));
                }
            }
        }
        if(mine) {
            var all=m.validTargets(side,Target.Kind.ANY_CHARACTER);
            if(m.whyCantUsePower(side)==null) moves.add("power",keys(all.stream().filter(t->HeroPowers.validTarget(m,side,t)).toList()));
            List<Integer> attackers=new ArrayList<>();attackers.add(Match.HERO);
            for(var mob:m.player(side).board) attackers.add(mob.id);
            for(int id:attackers) moves.add("attack"+id,keys(all.stream().filter(t->m.whyCantAttack(side,id,t)==null).toList()));
        }
        out.add("hand",hand); out.add("moves",moves); return out;
    }
    static String publicBoard(RelayMatch m) {
        JsonObject board=JsonParser.parseString(MatchView.publicBoard(m,false)).getAsJsonObject();
        board.addProperty("teamDuos",true);
        for(int s=0;s<4;s++)board.getAsJsonArray("players").get(s).getAsJsonObject().addProperty("ready",m.ready[s]);
        return board.toString();
    }
    private static JsonArray keys(List<Target> targets) {
        var out=new JsonArray();
        for(var t:targets) out.add(t.side()+":"+(t.hero()?0:t.minionId()));
        return out;
    }
    private RelayTables(){}
}