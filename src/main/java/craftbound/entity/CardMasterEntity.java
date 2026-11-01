package craftbound.entity;

import craftbound.game.MatchManager;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
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
// puff once the game is over. It can't be hurt or traded with, and it isn't saved with the world.
public class CardMasterEntity extends WanderingTrader {
    // Gives up walking and steps straight to the table after this many ticks (15 seconds).
    private static final int MAX_WALK_TICKS = 300;

    private BlockPos seat;
    private float seatYaw;
    private int walkTicks;
    private boolean arrived;
    // Counts down to disappearing once the game is over (-1 while it's still needed).
    private int leaveTicks = -1;

    public CardMasterEntity(EntityType<? extends CardMasterEntity> type, Level level) {
        super(type, level);
        this.setPersistenceRequired();
        this.setCustomName(Component.translatable("entity.craftbound.card_master"));
        this.setCustomNameVisible(true);
    }

    // No wandering, trading or potion drinking: it just looks at the nearest player.
    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(1, new LookAtPlayerGoal(this, Player.class, 10.0F, 1.0F));
    }

    // Spawns a Card Master a few blocks away and sends it to the side of the table across from the player.
    public static CardMasterEntity comeTo(ServerLevel level, BlockPos table, ServerPlayer player) {
        Direction playerSide = Direction.getApproximateNearest(player.getX() - (table.getX() + 0.5), 0, player.getZ() - (table.getZ() + 0.5));
        if (playerSide.getAxis().isVertical()) {
            playerSide = player.getDirection().getOpposite();
        }
        // Straight across from the player if there's room, otherwise beside the table.
        Direction seatSide = playerSide.getOpposite();
        BlockPos seat = table.relative(seatSide);
        for (Direction side : List.of(playerSide.getOpposite(), playerSide.getClockWise(), playerSide.getCounterClockWise())) {
            if (canStand(level, table.relative(side))) {
                seatSide = side;
                seat = table.relative(side);
                break;
            }
        }
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

    // The game is over: it says something, then disappears.
    public void leave(boolean won) {
        if (this.leaveTicks < 0) {
            this.leaveTicks = 50;
            this.playSound(won ? SoundEvents.WANDERING_TRADER_YES : SoundEvents.WANDERING_TRADER_NO, 1.0F, 1.0F);
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
        if (this.leaveTicks >= 0) {
            if (this.leaveTicks-- == 0) {
                vanish(level);
            }
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
        this.playSound(SoundEvents.WANDERING_TRADER_YES, 1.0F, 1.0F);
    }

    private void vanish(ServerLevel level) {
        level.sendParticles(ParticleTypes.POOF, this.getX(), this.getY() + 1.0, this.getZ(), 12, 0.3, 0.5, 0.3, 0.02);
        this.discard();
    }

    // Right-clicking it brings back the match screen instead of opening trades.
    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (player instanceof ServerPlayer serverPlayer) {
            MatchManager.resend(serverPlayer);
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
