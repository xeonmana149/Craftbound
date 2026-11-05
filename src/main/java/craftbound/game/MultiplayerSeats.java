package craftbound.game;

import craftbound.block.*;
import craftbound.entity.CardSeatEntity;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/** Four match seat IDs bound to the furniture, without owning or deleting that furniture. */
final class MultiplayerSeats {
    private final ServerLevel level;
    private final BlockPos table;
    private final List<UUID> owners;
    private final List<CardSeatEntity> seats;
    private MultiplayerSeats(ServerLevel level,BlockPos table,List<ServerPlayer> players,List<CardSeatEntity> seats) {
        this.level=level;this.table=table.immutable();this.owners=players.stream().map(Entity::getUUID).toList();this.seats=List.copyOf(seats);
    }
    static MultiplayerSeats start(ServerLevel level,BlockPos table,List<ServerPlayer> players) {
        if(players.size()!=4 || players.stream().anyMatch(p->p==null || p.level()!=level || !p.isAlive() || !TableSeats.canJoinFromVehicle(p,table))
                || players.stream().map(Entity::getUUID).distinct().count()!=4) return null;
        var chairs=TableSeats.ensure(level,table,Direction.NORTH);
        if(chairs.size()!=4 || chairs.stream().anyMatch(s->s.getPassengers().stream().anyMatch(p->!players.contains(p))))return null;
        boolean alreadySeated=true;
        for(int i=0;i<4;i++)alreadySeated &= players.get(i).getVehicle()==chairs.get(i);
        if(alreadySeated)return new MultiplayerSeats(level,table,players,chairs);
        var origins=players.stream().map(Entity::position).toList();
        var yaws=players.stream().map(Entity::getYRot).toList();
        var originalSeats=players.stream().map(Entity::getVehicle).toList();
        // Commander shuffles seats, while Team Duos preserves its lobby assignments. Bind all
        // participants to the final match order atomically instead of blocking a chair swap.
        players.forEach(Entity::stopRiding);
        for(int i=0;i<4;i++) if(!TableSeats.mount(players.get(i),chairs.get(i))) {
            players.forEach(Entity::stopRiding);
            for(int j=0;j<4;j++) {
                TableSeats.move(players.get(j),origins.get(j),yaws.get(j));
                if(originalSeats.get(j) instanceof CardSeatEntity old && !old.isRemoved())TableSeats.mount(players.get(j),old);
            }
            return null;
        }
        return new MultiplayerSeats(level,table,players,chairs);
    }
    float yaw() { return seats.getFirst().getYRot(); }
    void reopen(ServerPlayer player) {
        int seat=owners.indexOf(player.getUUID()); if(seat<0 || player.level()!=level || player.distanceToSqr(Vec3.atCenterOf(table))>64)return;
        var chair=seats.get(seat);
        if(!chair.isRemoved() && chair.getPassengers().isEmpty() && TableSeats.canJoinFromVehicle(player,table)
                && TableSeats.clearSpace(level,chair.blockPosition(),table)) TableSeats.mount(player,chair);
    }
    void close() {
        for(int i=0;i<4;i++) for(var rider:List.copyOf(seats.get(i).getPassengers()))
            if(rider.getUUID().equals(owners.get(i))) TableSeats.release(rider,seats.get(i));
    }
    static void explain(ServerPlayer player) {
        player.sendOverlayMessage(net.minecraft.network.chat.Component.literal("Keep all four chairs clear, with solid ground and two blocks of headroom. Only the four participants may occupy them."));
    }
}
