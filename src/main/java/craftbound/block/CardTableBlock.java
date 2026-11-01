package craftbound.block;

import craftbound.game.MatchManager;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

// A table for card games. Right-click it to sit down on the side you're standing on, facing the table.
// Right-click it again while sitting to play a practice match against the Card Master.
public class CardTableBlock extends Block {
    private static final VoxelShape SHAPE = Shapes.or(
            Block.box(0, 12, 0, 16, 16, 16),
            Block.box(1, 0, 1, 3, 12, 3),
            Block.box(13, 0, 1, 15, 12, 3),
            Block.box(1, 0, 13, 3, 12, 15),
            Block.box(13, 0, 13, 15, 12, 15));
    // How high above the floor of the seat block the invisible seat sits.
    private static final double SEAT_HEIGHT = 0.45;

    public CardTableBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        // Already sitting at a table: right-clicking it starts a practice match against the Card Master.
        if (player.getVehicle() instanceof SeatEntity) {
            if (player instanceof ServerPlayer serverPlayer) {
                MatchManager.startPractice(serverPlayer);
            }
            return InteractionResult.SUCCESS;
        }
        if (player.isPassenger()) {
            return InteractionResult.PASS;
        }
        // Sit on the side of the table you're standing on.
        Direction side = Direction.getApproximateNearest(player.getX() - (pos.getX() + 0.5), 0, player.getZ() - (pos.getZ() + 0.5));
        if (side.getAxis().isVertical()) {
            side = player.getDirection().getOpposite();
        }
        BlockPos seatPos = pos.relative(side);
        boolean blocked = !level.getBlockState(seatPos).getCollisionShape(level, seatPos).isEmpty()
                || !level.getBlockState(seatPos.above()).getCollisionShape(level, seatPos.above()).isEmpty();
        List<SeatEntity> taken = level.getEntitiesOfClass(SeatEntity.class, new AABB(seatPos));
        if (blocked || !taken.isEmpty()) {
            return InteractionResult.FAIL;
        }
        SeatEntity seat = ModBlocks.SEAT.create(level, EntitySpawnReason.TRIGGERED);
        if (seat == null) {
            return InteractionResult.FAIL;
        }
        // Face the table.
        float yaw = side.getOpposite().toYRot();
        seat.snapTo(seatPos.getX() + 0.5, seatPos.getY() + SEAT_HEIGHT, seatPos.getZ() + 0.5, yaw, 0.0F);
        level.addFreshEntity(seat);
        player.startRiding(seat);
        player.setYRot(yaw);
        player.setYHeadRot(yaw);
        if (player instanceof ServerPlayer serverPlayer) {
            serverPlayer.sendOverlayMessage(Component.translatable("match_ui.craftbound.sit_hint"));
        }
        return InteractionResult.SUCCESS;
    }
}
