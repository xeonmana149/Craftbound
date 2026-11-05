package craftbound.block;

import craftbound.game.TableLobby;
import craftbound.game.TableSeats;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
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
// Two persistent chairs flank the table; matches automatically seat their participants.
public class CardTableBlock extends Block implements net.minecraft.world.level.block.EntityBlock {
    @Override public net.minecraft.world.level.block.entity.BlockEntity newBlockEntity(BlockPos pos,BlockState state) {
        return new CardTableBlockEntity(pos,state);
    }
    public static CardTableBlockEntity data(ServerLevel level,BlockPos pos) {
        pos=anchor(level,pos);
        if(!isTable(level.getBlockState(pos))) return null;
        if(level.getBlockEntity(pos) instanceof CardTableBlockEntity table) return table;
        // Upgrade tables placed before they held game data.
        var table=new CardTableBlockEntity(pos,level.getBlockState(pos));level.setBlockEntity(table);return table;
    }
    public static boolean isTable(BlockState state) {
        return state.getBlock() instanceof CardTableBlock && (!(state.getBlock() instanceof MultiplayerTableBlock) || MultiplayerTableBlock.center(state));
    }
    public static BlockPos anchor(Level level,BlockPos pos) {
        var state=level.getBlockState(pos);
        return state.getBlock() instanceof MultiplayerTableBlock ? MultiplayerTableBlock.anchor(pos,state) : pos;
    }
    public static int mode(Level level,BlockPos pos) {
        var state=level.getBlockState(anchor(level,pos));
        return state.getBlock() instanceof MultiplayerTableBlock table ? table.mode() : 0;
    }
    public static boolean accepts(Level level,BlockPos pos,int requested) {
        var state=level.getBlockState(pos);
        return isTable(state) && (!(state.getBlock() instanceof MultiplayerTableBlock table) || table.mode()==requested);
    }
    private static final VoxelShape SHAPE = Shapes.or(
            Block.box(0, 12, 0, 16, 16, 16),
            Block.box(1, 0, 1, 3, 12, 3),
            Block.box(13, 0, 1, 15, 12, 3),
            Block.box(1, 0, 13, 3, 12, 15),
            Block.box(13, 0, 13, 15, 12, 15));

    public CardTableBlock(Properties properties) {
        super(properties);
    }

    @Override protected void onPlace(BlockState state,Level level,BlockPos pos,BlockState oldState,boolean moved) {
        super.onPlace(state,level,pos,oldState,moved);
        if(!level.isClientSide()) level.scheduleTick(pos,this,1);
    }
    @Override public void setPlacedBy(Level level,BlockPos pos,BlockState state,LivingEntity placer,ItemStack stack) {
        super.setPlacedBy(level,pos,state,placer,stack);
        if(level instanceof ServerLevel server)
            TableSeats.ensure(server,pos,placer==null?Direction.NORTH:placer.getDirection().getOpposite());
    }
    @Override protected void tick(BlockState state,ServerLevel level,BlockPos pos,RandomSource random) {
        craftbound.game.MatchManager.maintainPaused(level,data(level,pos));
        TableSeats.ensure(level,pos,Direction.NORTH);
        level.scheduleTick(pos,this,40);
    }
    @Override protected void affectNeighborsAfterRemoval(BlockState state,ServerLevel level,BlockPos pos,boolean moved) {
        super.affectNeighborsAfterRemoval(state,level,pos,moved);
        if(!level.getBlockState(pos).is(this)) TableSeats.remove(level,pos);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer) {
            // Also upgrade tables placed before persistent chairs were introduced.
            TableSeats.ensure((ServerLevel)level,pos,player.getDirection().getOpposite());
            level.scheduleTick(pos,this,40);
            TableLobby.open(serverPlayer, pos);
        }
        return InteractionResult.SUCCESS;
    }
}
