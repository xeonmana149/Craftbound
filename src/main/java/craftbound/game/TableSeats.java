package craftbound.game;

import craftbound.block.ModBlocks;
import craftbound.block.MultiplayerTableBlock;
import craftbound.entity.CardSeatEntity;
import craftbound.entity.ModEntities;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/** Persistent table furniture with temporary match riders. */
public final class TableSeats {
    public record Plan(BlockPos first,BlockPos second,float firstYaw,float secondYaw) {}
    private final ServerLevel level;
    private final BlockPos table;
    private final Entity first,second;
    private final CardSeatEntity firstSeat,secondSeat;
    private TableSeats(ServerLevel level,BlockPos table,Entity first,Entity second,
                       CardSeatEntity firstSeat,CardSeatEntity secondSeat) {
        this.level=level;this.table=table.immutable();this.first=first;this.second=second;
        this.firstSeat=firstSeat;this.secondSeat=secondSeat;
    }
    public static Plan plan(ServerLevel level,BlockPos table,Entity player) {
        Direction near=Direction.getApproximateNearest(player.getX()-table.getX()-0.5,0,player.getZ()-table.getZ()-0.5);
        if(near.getAxis().isVertical()) near=player.getDirection().getOpposite();
        var pair=ensure(level,table,near);
        if(pair.size()!=2)return null;
        var a=pair.get(0);var b=pair.get(1);
        BlockPos chosen=player.getVehicle() instanceof CardSeatEntity chair && chair.belongsTo(table)?chair.blockPosition():null;
        if(preferSecondChair(a.position(),b.position(),player.position(),chosen,null)){var other=a;a=b;b=other;}
        return new Plan(a.blockPosition(),b.blockPosition(),a.getYRot(),b.getYRot());
    }
    /** Existing sitters take precedence; an approaching opponent keeps its assigned chair. */
    static boolean preferSecondChair(Vec3 a,Vec3 b,Vec3 player,BlockPos chosen,BlockPos opponent) {
        if(chosen!=null)return chosen.equals(BlockPos.containing(b));
        if(opponent!=null && opponent.equals(BlockPos.containing(a)))return true;
        if(opponent!=null && opponent.equals(BlockPos.containing(b)))return false;
        return player.distanceToSqr(b)<player.distanceToSqr(a);
    }
    static Plan plan(BlockPos table,Direction near,java.util.function.Predicate<BlockPos> clear) {
        for(Direction side:List.of(near,near.getClockWise(),near.getCounterClockWise(),near.getOpposite())) {
            Plan plan=opposite(table,side);
            if(clear.test(plan.first()) && clear.test(plan.second())) return plan;
        }
        return null;
    }
    static Plan opposite(BlockPos table,Direction side) {
        return new Plan(table.relative(side),table.relative(side.getOpposite()),
                side.getOpposite().toYRot(),side.toYRot());
    }
    public static boolean clearSpace(net.minecraft.world.level.Level level,BlockPos pos,BlockPos table) {
        return level.getWorldBorder().isWithinBounds(pos) && level.hasChunkAt(pos)
                && level.getFluidState(pos).isEmpty() && level.getFluidState(pos.above()).isEmpty()
                && level.getBlockState(pos).getCollisionShape(level,pos).isEmpty()
                && level.getBlockState(pos.above()).getCollisionShape(level,pos.above()).isEmpty()
                && level.getBlockState(pos.below()).isFaceSturdy(level,pos.below(),Direction.UP)
                && level.getEntitiesOfClass(CardSeatEntity.class,new net.minecraft.world.phys.AABB(pos),
                        seat->!seat.belongsTo(table)).isEmpty();
    }
    private static List<CardSeatEntity> chairs(ServerLevel level,BlockPos table) {
        return level.getEntitiesOfClass(CardSeatEntity.class,new net.minecraft.world.phys.AABB(table).inflate(3),
                seat->seat.belongsTo(table));
    }
    static boolean oppositePositions(BlockPos table,BlockPos a,BlockPos b) {
        return a.getY()==table.getY() && b.getY()==table.getY() && a.distManhattan(table)==1
                && b.distManhattan(table)==1 && a.getX()+b.getX()==2*table.getX()
                && a.getZ()+b.getZ()==2*table.getZ();
    }
    /** Called on placement and by the table's maintenance tick; never replaces occupied furniture. */
    public static List<CardSeatEntity> ensure(ServerLevel level,BlockPos table,Direction preferred) {
        var state=level.getBlockState(table);
        if(state.getBlock() instanceof MultiplayerTableBlock block) return ensureGroup(level,table,state,block);
        if(!state.is(ModBlocks.CARD_TABLE)) return List.of();
        // Entity saves at chunk borders may not have loaded yet. Do not create a duplicate pair.
        for(int x:new int[]{-1,1}) for(int z:new int[]{-1,1})
            if(!level.hasChunkAt(table.offset(x,0,z))
                    || !level.areEntitiesLoaded(net.minecraft.world.level.ChunkPos.pack(table.offset(x,0,z))))
                return List.of();
        var existing=chairs(level,table);
        if(existing.size()==2 && oppositePositions(table,existing.get(0).blockPosition(),existing.get(1).blockPosition())
                && existing.stream().allMatch(seat->clearSpace(level,seat.blockPosition(),table))) return existing;
        if(existing.stream().anyMatch(seat->!seat.getPassengers().isEmpty())) return List.of();
        existing.forEach(Entity::discard);
        Plan plan=plan(table,preferred,pos->clearSpace(level,pos,table));
        if(plan==null) return List.of();
        var a=ModEntities.CARD_SEAT.create(level,EntitySpawnReason.TRIGGERED);
        var b=ModEntities.CARD_SEAT.create(level,EntitySpawnReason.TRIGGERED);
        if(a==null || b==null) return List.of();
        a.table(table);b.table(table);
        a.snapTo(plan.first().getX()+0.5,plan.first().getY(),plan.first().getZ()+0.5,plan.firstYaw(),0);
        b.snapTo(plan.second().getX()+0.5,plan.second().getY(),plan.second().getZ()+0.5,plan.secondYaw(),0);
        if(!level.addFreshEntity(a) || !level.addFreshEntity(b)) {a.discard();b.discard();return List.of();}
        return List.of(a,b);
    }
    private static List<CardSeatEntity> ensureGroup(ServerLevel level,BlockPos table,
            net.minecraft.world.level.block.state.BlockState state,MultiplayerTableBlock block) {
        if(!MultiplayerTableBlock.center(state) || !block.complete(level,table,state)) return List.of();
        var plans=MultiplayerTableBlock.chairs(table,state.getValue(MultiplayerTableBlock.FACING),block.mode());
        for(var plan:plans)
            if(!level.hasChunkAt(plan.pos()) || !level.areEntitiesLoaded(net.minecraft.world.level.ChunkPos.pack(plan.pos())))
                return List.of();
        var existing=chairs(level,table);
        var ordered=new java.util.ArrayList<CardSeatEntity>();
        for(var plan:plans) {
            var found=existing.stream().filter(s->s.blockPosition().equals(plan.pos())).toList();
            if(found.size()!=1 || !clearSpace(level,plan.pos(),table)) {ordered.clear();break;}
            ordered.add(found.getFirst());
        }
        if(ordered.size()==4 && existing.size()==4) return List.copyOf(ordered);
        if(existing.stream().anyMatch(Entity::isVehicle)) return List.of();
        existing.forEach(Entity::discard);
        ordered.clear();
        if(plans.stream().anyMatch(p->!clearSpace(level,p.pos(),table))) return List.of();
        for(var plan:plans) {
            var seat=ModEntities.CARD_SEAT.create(level,EntitySpawnReason.TRIGGERED);
            if(seat==null) {ordered.forEach(Entity::discard);return List.of();}
            seat.table(table);
            seat.snapTo(plan.pos().getX()+.5,plan.pos().getY(),plan.pos().getZ()+.5,plan.facing().toYRot(),0);
            if(!level.addFreshEntity(seat)) {seat.discard();ordered.forEach(Entity::discard);return List.of();}
            ordered.add(seat);
        }
        return List.copyOf(ordered);
    }
    public static void remove(ServerLevel level,BlockPos table) {
        for(var seat:chairs(level,table)){seat.ejectPassengers();seat.discard();}
    }
    public static boolean canJoinFromVehicle(Entity rider,BlockPos table) {
        return !rider.isPassenger() || rider.getVehicle() instanceof CardSeatEntity seat && seat.belongsTo(table);
    }
    public static TableSeats start(ServerLevel level,BlockPos table,ServerPlayer first,LivingEntity second) {
        if(second==null || !first.isAlive() || !second.isAlive() || first.level()!=second.level()
                || !canJoinFromVehicle(first,table) || !canJoinFromVehicle(second,table)
                || !level.getBlockState(table).is(ModBlocks.CARD_TABLE)) return null;
        var pair=ensure(level,table,first.getDirection().getOpposite());
        if(pair.size()!=2 || pair.stream().anyMatch(seat->seat.getPassengers().stream()
                .anyMatch(rider->rider!=first && rider!=second))) return null;
        var a=pair.get(0);var b=pair.get(1);
        // Respect either participant's chosen chair; never move an existing sitter to the other side.
        BlockPos chosen=first.getVehicle() instanceof CardSeatEntity chair?chair.blockPosition():null;
        BlockPos opponent=second.getVehicle() instanceof CardSeatEntity chair?chair.blockPosition()
                :second instanceof craftbound.entity.CardMasterEntity master?master.approachSeat():null;
        boolean swap=preferSecondChair(a.position(),b.position(),first.position(),chosen,opponent);
        if(swap){var other=a;a=b;b=other;}
        var seats=new TableSeats(level,table,first,second,a,b);
        // Both chairs exist before either player is moved. Roll back on any mount failure.
        Vec3 firstOrigin=first.position(),secondOrigin=second.position();
        float firstYaw=first.getYRot(),secondYaw=second.getYRot();
        boolean firstAlreadySeated=first.getVehicle()==a,secondAlreadySeated=second.getVehicle()==b;
        if(!mount(first,a) || !mount(second,b)) {
            if(!firstAlreadySeated){release(first,a);move(first,firstOrigin,firstYaw);}
            if(!secondAlreadySeated){release(second,b);move(second,secondOrigin,secondYaw);}
            return null;
        }
        return seats;
    }
    static boolean mount(Entity rider,CardSeatEntity seat) {
        if(rider.getVehicle()==seat) return true;
        move(rider,seat.position(),seat.getYRot());
        rider.setYHeadRot(seat.getYRot());rider.setYBodyRot(seat.getYRot());
        if(!rider.startRiding(seat,true,false)) return false;
        seat.positionRider(rider);
        return true;
    }
    static void move(Entity entity,Vec3 pos,float yaw) {
        if(entity instanceof ServerPlayer player)
            player.teleportTo((ServerLevel)player.level(),pos.x,pos.y,pos.z,Set.of(),yaw,0,true);
        else entity.snapTo(pos.x,pos.y,pos.z,yaw,0);
    }
    public boolean at(ServerLevel otherLevel,BlockPos otherTable) { return level==otherLevel && table.equals(otherTable); }
    public craftbound.block.CardTableBlockEntity tableData() { return craftbound.block.CardTableBlock.data(level,table); }
    public float boardYaw(java.util.UUID firstPlayer) {
        return first.getUUID().equals(firstPlayer)?firstSeat.getYRot():secondSeat.getYRot();
    }
    public boolean valid() {
        return !firstSeat.isRemoved() && !secondSeat.isRemoved()
                && first.getVehicle()==firstSeat && second.getVehicle()==secondSeat
                && first.isAlive() && second.isAlive() && first.level()==level && second.level()==level
                && level.getBlockState(table).is(ModBlocks.CARD_TABLE);
    }
    public void close() {
        release(first,firstSeat);release(second,secondSeat);
    }
    static void release(Entity rider,CardSeatEntity seat) {
        if(rider.getVehicle()==seat) {
            rider.stopRiding();
            if(rider.isAlive() && rider.level()==seat.level()) move(rider,seat.position(),rider.getYRot());
        }
    }
    public static void explain(ServerPlayer player) {
        player.sendOverlayMessage(Component.literal("Leave two opposite sides of the table clear, with solid ground and two blocks of headroom."));
    }
}
