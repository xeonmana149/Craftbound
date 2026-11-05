package craftbound.game;
import craftbound.block.*;
import craftbound.network.*;
import com.google.gson.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import net.minecraft.world.level.Level;
import net.fabricmc.fabric.api.networking.v1.*;
import net.fabricmc.fabric.api.event.lifecycle.v1.*;

/** Public-by-default subscription. Hand permission is scoped to one viewer, owner and live match. */
public final class TableSpectating {
 private static final Map<UUID,Watch> WATCHES=new HashMap<>();
 private static final Map<UUID,Request> REQUESTS=new HashMap<>(); // one pending prompt per owner
 private static final class Watch {
  final ResourceKey<Level> level;final BlockPos table;Match match;int grants;int eventCursor;String last="";
  final long[] nextRequest={0,0};
  Watch(ServerPlayer p,BlockPos table,Match match){level=p.level().dimension();this.table=table.immutable();this.match=match;eventCursor=match==null?0:match.events.size();}
 }
 record Request(String id,UUID viewer,UUID owner,Match match,int side,long expires) {
  boolean canAnswer(UUID actor,long now,Match active){return owner.equals(actor)&&now<expires&&match==active;}
 }
 public static void initialize(){
  PayloadTypeRegistry.clientboundPlay().register(SpectatorStatePayload.TYPE,SpectatorStatePayload.CODEC);
  PayloadTypeRegistry.clientboundPlay().register(HandConsentPayload.TYPE,HandConsentPayload.CODEC);
  PayloadTypeRegistry.serverboundPlay().register(SpectatorActionPayload.TYPE,SpectatorActionPayload.CODEC);
  ServerPlayNetworking.registerGlobalReceiver(SpectatorActionPayload.TYPE,(p,c)->action(c.player(),p));
  ServerTickEvents.END_SERVER_TICK.register(TableSpectating::tick);
  ServerPlayConnectionEvents.DISCONNECT.register((c,s)->stop(s,c.player.getUUID()));
  ServerLifecycleEvents.SERVER_STOPPING.register(s->{WATCHES.clear();REQUESTS.clear();});
 }
 public static boolean open(ServerPlayer player,BlockPos table){
  var level=(ServerLevel)player.level();var data=CardTableBlock.data(level,table);
  if(data==null||data.publicBoard().isEmpty()||MatchManager.inMatch(player))return false;
  var match=MatchManager.matchAt(level,table);
  if(data.paused())try{
   if(MatchSnapshot.participant(JsonParser.parseString(data.savedMatch()).getAsJsonObject(),player.getUUID()))return false;
  }catch(RuntimeException ignored){return false;}
  stop(level.getServer(),player.getUUID());
  var watch=new Watch(player,table,match);WATCHES.put(player.getUUID(),watch);
  send(player,watch,data,true,"");return true;
 }
 private static void stop(MinecraftServer server,UUID viewer){
  var old=WATCHES.remove(viewer);
  REQUESTS.values().removeIf(r->r.viewer.equals(viewer));
  if(old!=null&&old.match!=null)notifyOwners(server,old.match);
 }
 public static void revokeMatch(MinecraftServer server,Match match){
  REQUESTS.values().removeIf(r->r.match==match);
  for(var w:WATCHES.values())if(w.match==match){w.grants=0;w.last="";}
  notifyOwners(server,match);
 }
 private static void tick(MinecraftServer server){
  long now=server.getTickCount();
  var expired=REQUESTS.values().stream().filter(r->now>=r.expires).toList();
  for(var r:expired){REQUESTS.remove(r.owner);notifyOwner(server,r.owner);}
  if(now%5!=0)return;
  for(var entry:new ArrayList<>(WATCHES.entrySet())){
   var p=server.getPlayerList().getPlayer(entry.getKey());var w=entry.getValue();
   if(p==null||p.level().dimension()!=w.level||p.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(w.table))>32*32||MatchManager.inMatch(p)){
    if(p!=null)ServerPlayNetworking.send(p,new SpectatorStatePayload(w.table,"",false,"Spectating ended."));
    stop(server,entry.getKey());continue;
   }
   var level=(ServerLevel)p.level();var be=level.getBlockEntity(w.table);
   if(!(be instanceof CardTableBlockEntity data)||data.publicBoard().isEmpty()){
    ServerPlayNetworking.send(p,new SpectatorStatePayload(w.table,"",false,"The match has ended."));stop(server,entry.getKey());continue;
   }
   var active=MatchManager.matchAt(level,w.table);
   if(active!=w.match){if(w.match!=null)revokeMatch(server,w.match);w.grants=0;w.match=active;w.eventCursor=active==null?0:active.events.size();w.last="";}
   send(p,w,data,false,"");
  }
 }
 static JsonObject view(String publicBoard,Match match,int grants){
  var board=JsonParser.parseString(publicBoard).getAsJsonObject();
  boolean paused=board.has("paused")&&board.get("paused").getAsBoolean();
  var root=match==null?board:MatchView.buildJson(match,-1,match.events.size());
  root.addProperty("spectator",true);
  root.addProperty("paused",paused);
  var players=root.getAsJsonArray("players");
  for(int side=0;side<2;side++){
   var p=players.get(side).getAsJsonObject();p.remove("hand");
   boolean visible=match!=null&&!match.over&&(grants&(1<<side))!=0;
   p.addProperty("handVisible",visible);p.addProperty("canRequestHand",match!=null&&!match.player(side).isAi()&&!visible);
   if(visible){var hand=new JsonArray();for(var card:match.player(side).hand){
    var c=new JsonObject();c.addProperty("id",card.id().toString());c.addProperty("ench",card.enchanted());c.addProperty("playable",false);c.addProperty("target","NONE");hand.add(c);
   }p.add("hand",hand);}
  }
  return root;
 }
 private static void send(ServerPlayer player,Watch watch,CardTableBlockEntity data,boolean open,String notice){
  var root=view(data.publicBoard(),watch.match,watch.grants);
  if(watch.match!=null){
   root.add("events",MatchView.spectatorEvents(watch.match,watch.eventCursor,watch.grants));
   watch.eventCursor=watch.match.events.size();
  }
  for(int side=0;side<2;side++){
   final int s=side;
   root.getAsJsonArray("players").get(side).getAsJsonObject().addProperty("handPending",REQUESTS.values().stream().anyMatch(r->r.viewer.equals(player.getUUID())&&r.match==watch.match&&r.side==s));
  }
  String json=root.toString();if(!open&&notice.isEmpty()&&json.equals(watch.last))return;
  watch.last=json;ServerPlayNetworking.send(player,new SpectatorStatePayload(watch.table,json,open,notice));
 }
 private static void action(ServerPlayer player,SpectatorActionPayload p){
  var server=player.level().getServer();long now=server.getTickCount();
  if(p.action()==SpectatorActionPayload.REVOKE){
   for(var w:WATCHES.values())if(w.match!=null){int side=w.match.sideOf(player.getUUID());if(side>=0){w.grants&=~(1<<side);w.last="";}}
   REQUESTS.remove(player.getUUID());notifyOwner(server,player.getUUID());return;
  }
  if(p.action()==SpectatorActionPayload.ACCEPT||p.action()==SpectatorActionPayload.DENY){
   var r=REQUESTS.get(player.getUUID());if(r==null||!r.id.equals(p.request()))return;
   var w=WATCHES.get(r.viewer);
   if(w==null||!r.canAnswer(player.getUUID(),now,w.match)||MatchManager.matchAt((ServerLevel)player.level(),w.table)!=r.match)return;
   REQUESTS.remove(player.getUUID());
   if(p.action()==SpectatorActionPayload.ACCEPT)w.grants|=1<<r.side;
   w.last="";notifyOwner(server,player.getUUID());
   var viewer=server.getPlayerList().getPlayer(r.viewer);
   if(viewer!=null&&viewer.level().getBlockEntity(w.table) instanceof CardTableBlockEntity data)
    send(viewer,w,data,false,p.action()==SpectatorActionPayload.ACCEPT?"Hand request accepted.":"Hand request declined.");
   return;
  }
  var w=WATCHES.get(player.getUUID());if(w==null||!w.table.equals(p.table()))return;
  if(p.action()==SpectatorActionPayload.CLOSE){stop(server,player.getUUID());return;}
  if(p.action()!=SpectatorActionPayload.REQUEST||p.side()<0||p.side()>1||w.match==null||w.match.over)return;
  int side=p.side();var owner=w.match.player(side).playerId;
  if(owner==null||now<w.nextRequest[side]||REQUESTS.containsKey(owner)||(w.grants&(1<<side))!=0
    ||player.level().dimension()!=w.level||player.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(w.table))>32*32
    ||MatchManager.matchAt((ServerLevel)player.level(),w.table)!=w.match)return;
  w.nextRequest[side]=now+600;
  REQUESTS.put(owner,new Request(UUID.randomUUID().toString(),player.getUUID(),owner,w.match,side,now+600));
  w.last="";notifyOwner(server,owner);
 }
 private static void notifyOwners(MinecraftServer server,Match match){for(var p:match.players)if(p.playerId!=null)notifyOwner(server,p.playerId);}
 private static void notifyOwner(MinecraftServer server,UUID owner){
  var player=server.getPlayerList().getPlayer(owner);if(player==null)return;
  var r=REQUESTS.get(owner);int count=0;
  for(var w:WATCHES.values())if(w.match!=null){int side=w.match.sideOf(owner);if(side>=0&&(w.grants&(1<<side))!=0)count++;}
  var viewer=r==null?null:server.getPlayerList().getPlayer(r.viewer);
  ServerPlayNetworking.send(player,new HandConsentPayload(r==null?"":r.id,viewer==null?"":viewer.getName().getString(),count));
 }
 private TableSpectating(){}
}
