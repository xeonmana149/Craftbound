package craftbound.entity;

import craftbound.game.MatchManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.npc.wanderingtrader.WanderingTrader;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

// The Card Master: a travelling card player who comes over when you ask for a game at a card table.
// It appears a few blocks away, walks to the side of the table across from you, plays, then disappears in a
// puff once the game is over. A paused opponent stays by its table and is saved with the world.
public class CardMasterEntity extends WanderingTrader {
    // Gives up walking and steps straight to the table after this many ticks (15 seconds).
    private static final int MAX_WALK_TICKS = 300;

    private BlockPos seat;
    private float seatYaw;
    private int walkTicks;
    private boolean arrived;
    private BlockPos waitingTable;
    // Counts down to disappearing once the game is over (-1 while it's still needed).
    private int leaveTicks = -1;

    public CardMasterEntity(EntityType<? extends CardMasterEntity> type, Level level) {
        super(type, level);
        this.setPersistenceRequired();
        this.setCustomName(Component.translatable("entity.craftbound.card_master"));
        this.setCustomNameVisible(true);
        // MatchScreen plays the hurt cue when this opponent's hero actually loses health.
        // Keep the world entity quiet: no idle chatter, greetings or departure noises over the game.
        this.setSilent(true);
    }

    // No wandering, trading or potion drinking: it just looks at the nearest player.
    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(1, new LookAtPlayerGoal(this, Player.class, 10.0F, 1.0F));
    }

    // Spawns a Card Master a few blocks away and sends it to the side of the table across from the player.
    public static CardMasterEntity comeTo(ServerLevel level, BlockPos table, ServerPlayer player) {
        var seating=craftbound.game.TableSeats.plan(level,table,player);
        if(seating==null) return null;
        BlockPos seat=seating.second();
        Direction seatSide=Direction.getApproximateNearest(seat.getX()-table.getX(),0,seat.getZ()-table.getZ());
        // It starts further out on the same side, so it walks up to its seat.
        BlockPos start = seat;
        for (int distance = 7; distance >= 2; distance--) {
            BlockPos ground = findGround(level, seat.relative(seatSide, distance));
            if (ground != null) {
                start = ground;
                break;
            }
        }
        CardMasterEntity cardMaster = ModEntities.CARD_MASTER.create(level, EntitySpawnReason.TRIGGERED);
        if (cardMaster == null) {
            return null;
        }
        cardMaster.seat = seat;
        cardMaster.seatYaw = seatSide.getOpposite().toYRot();
        cardMaster.snapTo(start.getX() + 0.5, start.getY(), start.getZ() + 0.5, cardMaster.seatYaw, 0.0F);
        level.addFreshEntity(cardMaster);
        level.sendParticles(ParticleTypes.POOF, cardMaster.getX(), cardMaster.getY() + 1.0, cardMaster.getZ(), 12, 0.3, 0.5, 0.3, 0.02);
        return cardMaster;
    }

    // Room for a mob to stand: two blocks of air on top of something solid.
    private static boolean canStand(ServerLevel level, BlockPos pos) {
        return level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()
                && level.getBlockState(pos.above()).getCollisionShape(level, pos.above()).isEmpty()
                && !level.getBlockState(pos.below()).getCollisionShape(level, pos.below()).isEmpty();
    }

    // The nearest spot to stand on within 3 blocks up or down, or null.
    private static BlockPos findGround(ServerLevel level, BlockPos around) {
        for (int dy = 0; dy <= 3; dy++) {
            if (canStand(level, around.above(dy))) return around.above(dy);
            if (canStand(level, around.below(dy))) return around.below(dy);
        }
        return null;
    }

    public boolean hasArrived() {
        return this.arrived;
    }

    public BlockPos approachSeat() { return this.seat; }

    public void waitAt(BlockPos table) {
        waitingTable=table.immutable();leaveTicks=-1;arrived=true;
        seat=blockPosition();seatYaw=getYRot();
        getNavigation().stop();
    }
    public boolean waitingAt(BlockPos table) { return table.equals(waitingTable) && !isRemoved(); }
    public void resumeMatch() { waitingTable=null;leaveTicks=-1; }

    /** Repair a paused save made before opponents were persistent. */
    public static CardMasterEntity restoreWaiting(ServerLevel level,BlockPos table,float boardYaw) {
        BlockPos spot=findGround(level,table.relative(Direction.fromYRot(boardYaw)));
        if(spot==null)return null;
        var master=ModEntities.CARD_MASTER.create(level,EntitySpawnReason.TRIGGERED);
        if(master==null)return null;
        float yaw=Direction.fromYRot(boardYaw).getOpposite().toYRot();
        master.snapTo(spot.getX()+.5,spot.getY(),spot.getZ()+.5,yaw,0);
        master.waitAt(table);
        return level.addFreshEntity(master)?master:null;
    }

    @Override protected void addAdditionalSaveData(net.minecraft.world.level.storage.ValueOutput out) {
        super.addAdditionalSaveData(out);
        if(waitingTable!=null && seat!=null) {
            out.store("WaitingTable",BlockPos.CODEC,waitingTable);
            out.store("CardSeat",BlockPos.CODEC,seat);
            out.putFloat("CardSeatYaw",seatYaw);
        }
    }
    @Override protected void readAdditionalSaveData(net.minecraft.world.level.storage.ValueInput in) {
        super.readAdditionalSaveData(in);
        waitingTable=in.read("WaitingTable",BlockPos.CODEC).orElse(null);
        seat=in.read("CardSeat",BlockPos.CODEC).orElse(null);
        seatYaw=in.getFloatOr("CardSeatYaw",0);
        arrived=waitingTable!=null;leaveTicks=-1;
    }

    // The game is over: disappear after a short pause.
    public void leave(boolean won) {
        if (this.leaveTicks < 0) {
            this.leaveTicks = 50;
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (!(this.level() instanceof ServerLevel level)) {
            return;
        }
        // One left over from an old game (or spawned some other way) has nothing to do.
        if (this.seat == null) {
            vanish(level);
            return;
        }
        if(waitingTable!=null) {
            // Do not load a neighbouring chunk just to check the table.
            if(!level.hasChunkAt(waitingTable)) return;
            if(!(level.getBlockEntity(waitingTable) instanceof craftbound.block.CardTableBlockEntity data) || !data.paused()) {
                waitingTable=null;leave(false);
            } else {
                getNavigation().stop();
                return;
            }
        }
        if (this.leaveTicks >= 0) {
            if (this.leaveTicks-- == 0) {
                vanish(level);
            }
            return;
        }
        if (getVehicle() instanceof CardSeatEntity chair) {
            getNavigation().stop();
            setYRot(chair.getYRot());setYHeadRot(chair.getYRot());setYBodyRot(chair.getYRot());
            setXRot(0);
            return;
        }
        if (this.arrived) {
            return;
        }
        this.walkTicks++;
        double dx = this.seat.getX() + 0.5 - this.getX();
        double dz = this.seat.getZ() + 0.5 - this.getZ();
        double distance = dx * dx + dz * dz;
        if (distance < 0.25 || (this.getNavigation().isDone() && distance < 2.5) || this.walkTicks > MAX_WALK_TICKS) {
            arrive();
        } else if (this.walkTicks % 20 == 1 || this.getNavigation().isDone()) {
            this.getNavigation().moveTo(this.seat.getX() + 0.5, this.seat.getY(), this.seat.getZ() + 0.5, 0.6);
        }
    }

    private void arrive() {
        this.getNavigation().stop();
        this.snapTo(this.seat.getX() + 0.5, this.seat.getY(), this.seat.getZ() + 0.5, this.seatYaw, 0.0F);
        this.setYHeadRot(this.seatYaw);
        this.setYBodyRot(this.seatYaw);
        this.arrived = true;
    }

    private void vanish(ServerLevel level) {
        level.sendParticles(ParticleTypes.POOF, this.getX(), this.getY() + 1.0, this.getZ(), 12, 0.3, 0.5, 0.3, 0.02);
        this.discard();
    }

    // Right-clicking it brings back the match screen instead of opening trades.
    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (player instanceof ServerPlayer serverPlayer) {
            if(waitingTable!=null) MatchManager.openPaused(serverPlayer,waitingTable);
            else MatchManager.resend(serverPlayer);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    @Override
    protected void updateTrades(ServerLevel level) {
    }
}
