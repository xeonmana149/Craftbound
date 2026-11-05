package craftbound.block;

import craftbound.vendor.VendorTrading;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.phys.BlockHitResult;

public final class CardVendorBlock extends Block implements EntityBlock {
    public static final EnumProperty<Direction> FACING=BlockStateProperties.HORIZONTAL_FACING;
    public static final EnumProperty<DoubleBlockHalf> HALF=BlockStateProperties.DOUBLE_BLOCK_HALF;
    public CardVendorBlock(Properties properties){
        super(properties);registerDefaultState(stateDefinition.any().setValue(FACING,Direction.NORTH).setValue(HALF,DoubleBlockHalf.LOWER));
    }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> builder){builder.add(FACING,HALF);}
    @Override public BlockState getStateForPlacement(BlockPlaceContext context){
        BlockPos pos=context.getClickedPos();Level level=context.getLevel();
        if(pos.getY()>=level.getMaxY()||!level.getBlockState(pos.above()).canBeReplaced(context))return null;
        return defaultBlockState().setValue(FACING,context.getHorizontalDirection().getOpposite());
    }
    @Override public void setPlacedBy(Level level,BlockPos pos,BlockState state,LivingEntity placer,ItemStack stack){
        level.setBlock(pos.above(),state.setValue(HALF,DoubleBlockHalf.UPPER),3);
    }
    @Override protected BlockState updateShape(BlockState state,LevelReader level,ScheduledTickAccess ticks,BlockPos pos,
                                               Direction direction,BlockPos neighbourPos,BlockState neighbour,RandomSource random){
        Direction other=state.getValue(HALF)==DoubleBlockHalf.LOWER?Direction.UP:Direction.DOWN;
        if(direction==other && (!neighbour.is(this)||neighbour.getValue(HALF)==state.getValue(HALF)))
            return Blocks.AIR.defaultBlockState();
        return super.updateShape(state,level,ticks,pos,direction,neighbourPos,neighbour,random);
    }
    @Override public BlockState playerWillDestroy(Level level,BlockPos pos,BlockState state,Player player){
        if(!level.isClientSide()&&player.preventsBlockDrops()&&state.getValue(HALF)==DoubleBlockHalf.UPPER){
            BlockPos lower=pos.below();
            if(level.getBlockState(lower).is(this))level.setBlock(lower,Blocks.AIR.defaultBlockState(),35);
        }
        return super.playerWillDestroy(level,pos,state,player);
    }
    @Override public BlockEntity newBlockEntity(BlockPos pos,BlockState state){
        return state.getValue(HALF)==DoubleBlockHalf.LOWER?new CardVendorBlockEntity(pos,state):null;
    }
    @Override protected InteractionResult useWithoutItem(BlockState state,Level level,BlockPos pos,Player player,BlockHitResult hit){
        if(player instanceof ServerPlayer server)VendorTrading.send(server,state.getValue(HALF)==DoubleBlockHalf.UPPER?pos.below():pos,true);
        return InteractionResult.SUCCESS;
    }
    @Override protected BlockState rotate(BlockState state,Rotation rotation){return state.setValue(FACING,rotation.rotate(state.getValue(FACING)));}
    @Override protected BlockState mirror(BlockState state,Mirror mirror){return state.rotate(mirror.getRotation(state.getValue(FACING)));}
}
