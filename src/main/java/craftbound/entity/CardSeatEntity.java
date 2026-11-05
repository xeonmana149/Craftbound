package craftbound.entity;

import craftbound.block.ModBlocks;
import craftbound.game.MatchManager;
import net.minecraft.core.BlockPos;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

/** A persistent table chair. Its type must be serializable to accept riders in 26.3. */
public final class CardSeatEntity extends Entity {
    private BlockPos table;
    private boolean releaseRestoredRiders;
    public CardSeatEntity(EntityType<? extends CardSeatEntity> type, Level level) {
        super(type,level);
        setNoGravity(true);
    }
    public void table(BlockPos pos) { table=pos.immutable(); }
    public boolean belongsTo(BlockPos pos) { return pos.equals(table); }
    @Override protected void defineSynchedData(SynchedEntityData.Builder builder) {}
    @Override protected void readAdditionalSaveData(ValueInput input) {
        table=input.read("Table",BlockPos.CODEC).orElse(null);
        releaseRestoredRiders=true;
    }
    @Override protected void addAdditionalSaveData(ValueOutput output) {
        if(table!=null) output.store("Table",BlockPos.CODEC,table);
    }
    @Override public boolean hurtServer(ServerLevel level,DamageSource source,float amount) { return false; }
    @Override public boolean isPushable() { return false; }
    @Override public boolean isPickable() { return !isRemoved(); }
    @Override public InteractionResult interact(Player player,InteractionHand hand,Vec3 location) {
        if(player.isSpectator() || player.isShiftKeyDown()) return InteractionResult.PASS;
        if(isRemoved() || !player.isAlive() || player.isPassenger() || !getPassengers().isEmpty())
            return InteractionResult.FAIL;
        if(level().isClientSide()) return InteractionResult.SUCCESS;
        if(!(player instanceof ServerPlayer server) || table==null || !level().hasChunkAt(table)
                || !craftbound.block.CardTableBlock.isTable(level().getBlockState(table))
                || MatchManager.inMatch(server) || (MatchManager.tableInUse((ServerLevel)level(),table)
                && !craftbound.game.RelayTables.canSitWhileWaiting((ServerLevel)level(),table,blockPosition())))
            return InteractionResult.FAIL;
        if(!player.startRiding(this,true,false)) return InteractionResult.FAIL;
        player.setYRot(getYRot());player.setYHeadRot(getYRot());player.setYBodyRot(getYRot());
        positionRider(player);
        return InteractionResult.SUCCESS;
    }
    @Override protected boolean canAddPassenger(Entity passenger) { return getPassengers().isEmpty(); }
    @Override protected Vec3 getPassengerAttachmentPoint(Entity passenger,EntityDimensions dimensions,float scale) {
        // Align model hips with the 9/16-block seat, accounting for the rider's own attachment.
        double hipHeight=passenger instanceof CardMasterEntity ? 0.75*0.9375 : 0.75;
        return new Vec3(0,0.5625-hipHeight+passenger.getVehicleAttachmentPoint(this).y,0);
    }
    @Override public Vec3 getDismountLocationForPassenger(LivingEntity passenger) {
        // Same block, at floor level. The chair has no collision.
        return position();
    }
    @Override public void tick() {
        super.tick();
        setDeltaMovement(Vec3.ZERO);
        // Furniture is saved, but matches are not. Do not keep restored NPCs/players trapped in a chair.
        if(!level().isClientSide() && releaseRestoredRiders) {
            ejectPassengers();
            releaseRestoredRiders=false;
        }
        // Empty chairs remain between games. Never load a neighbouring chunk just to check a table.
        if(!level().isClientSide() && (table==null || (level().hasChunkAt(table)
                && !craftbound.block.CardTableBlock.isTable(level().getBlockState(table))))) {
            ejectPassengers();
            discard();
        }
    }
}
