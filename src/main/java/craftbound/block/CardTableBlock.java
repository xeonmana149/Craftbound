package craftbound.block;

import craftbound.game.TableLobby;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

// A table for card games. Right-click it to open its lobby: pick a deck and play the Card Master or another player.
// You play standing at the table; the Card Master comes over and stands across from you.
public class CardTableBlock extends Block {
    private static final VoxelShape SHAPE = Shapes.or(
            Block.box(0, 12, 0, 16, 16, 16),
            Block.box(1, 0, 1, 3, 12, 3),
            Block.box(13, 0, 1, 15, 12, 3),
            Block.box(1, 0, 13, 3, 12, 15),
            Block.box(13, 0, 13, 15, 12, 15));

    public CardTableBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer) {
            TableLobby.open(serverPlayer, pos);
        }
        return InteractionResult.SUCCESS;
    }
}
