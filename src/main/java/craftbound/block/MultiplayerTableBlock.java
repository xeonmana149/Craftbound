package craftbound.block;

import craftbound.game.TableSeats;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.server.level.*;
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
import net.minecraft.world.phys.*;
import net.minecraft.world.phys.shapes.*;

/** One inventory item, a complete reserved tabletop footprint, and four persistent chairs. */
public final class MultiplayerTableBlock extends CardTableBlock {
    public static final int DUOS = 1, COMMANDER = 2;
    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final IntegerProperty COLUMN = IntegerProperty.create("column", 0, 2);
    public static final IntegerProperty ROW = IntegerProperty.create("row", 0, 2);
    private final int mode;
    private record Removal(Level level, BlockPos anchor) {}
    private static final ThreadLocal<Set<Removal>> REMOVING = ThreadLocal.withInitial(HashSet::new);

    public MultiplayerTableBlock(Properties properties, int mode) {
        super(properties);
        this.mode = mode;
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(COLUMN, 1).setValue(ROW, 1));
    }
    public int mode() { return mode; }
    public int depth() { return mode == DUOS ? 1 : 3; }
    public static boolean center(BlockState state) { return state.getValue(COLUMN) == 1 && state.getValue(ROW) == 1; }
    public static BlockPos offset(BlockPos anchor, Direction facing, int x, int z) {
        return anchor.relative(facing.getClockWise(), x).relative(facing.getOpposite(), z);
    }
    public static BlockPos anchor(BlockPos pos, BlockState state) {
        return offset(pos, state.getValue(FACING), 1-state.getValue(COLUMN), 1-state.getValue(ROW));
    }
    public static List<BlockPos> footprint(BlockPos anchor, Direction facing, int mode) {
        var result = new ArrayList<BlockPos>();
        int radius = mode == DUOS ? 0 : 1;
        for (int z=-radius; z<=radius; z++) for (int x=-1; x<=1; x++) result.add(offset(anchor,facing,x,z));
        return List.copyOf(result);
    }
    public record Chair(BlockPos pos, Direction facing) {}
    /** Match seat IDs: Duos allies 0+2 / 1+3; Commander seat order goes around the table. */
    public static List<Chair> chairs(BlockPos anchor, Direction facing, int mode) {
        if (mode == DUOS) return List.of(
            new Chair(offset(anchor,facing,-1,1),facing),
            new Chair(offset(anchor,facing,-1,-1),facing.getOpposite()),
            new Chair(offset(anchor,facing,1,1),facing),
            new Chair(offset(anchor,facing,1,-1),facing.getOpposite()));
        return List.of(
            new Chair(offset(anchor,facing,0,2),facing),
            new Chair(offset(anchor,facing,2,0),facing.getCounterClockWise()),
            new Chair(offset(anchor,facing,0,-2),facing.getOpposite()),
            new Chair(offset(anchor,facing,-2,0),facing.getClockWise()));
    }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> b) { b.add(FACING,COLUMN,ROW); }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return center(state) ? new CardTableBlockEntity(pos,state) : null;
    }
    @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        var level=context.getLevel(); var pos=context.getClickedPos();
        Direction facing=context.getHorizontalDirection().getOpposite();
        for (BlockPos part : footprint(pos,facing,mode)) {
            if (!level.getWorldBorder().isWithinBounds(part) || !level.hasChunkAt(part)
                    || !level.getBlockState(part).canBeReplaced(context) || !level.getFluidState(part).isEmpty()
                    || !level.getBlockState(part.below()).isFaceSturdy(level,part.below(),Direction.UP)) return null;
            if (!level.getEntitiesOfClass(craftbound.entity.CardSeatEntity.class,new AABB(part)).isEmpty()
                    || !level.getEntitiesOfClass(LivingEntity.class,new AABB(part),e->e.isAlive()&&!e.isSpectator()).isEmpty()) return null;
        }
        for (Chair chair : chairs(pos,facing,mode))
            if (!TableSeats.clearSpace(level,chair.pos(),pos)) return null;
        return defaultBlockState().setValue(FACING,facing);
    }
    @Override public void setPlacedBy(Level level,BlockPos pos,BlockState state,LivingEntity placer,ItemStack stack) {
        var facing=state.getValue(FACING); int radius=depth()/2;
        for (int z=-radius;z<=radius;z++) for (int x=-1;x<=1;x++) {
            if (x==0 && z==0) continue;
            level.setBlock(offset(pos,facing,x,z),state.setValue(COLUMN,x+1).setValue(ROW,z+1),3);
        }
        if (level instanceof ServerLevel server) TableSeats.ensure(server,pos,facing);
    }
    public boolean complete(Level level,BlockPos anchor,BlockState state) {
        if(!state.is(this) || !center(state))return false;
        Direction facing=state.getValue(FACING); int radius=depth()/2;
        for (int z=-radius;z<=radius;z++) for (int x=-1;x<=1;x++) {
            var other=level.getBlockState(offset(anchor,facing,x,z));
            if (!other.is(this) || other.getValue(FACING)!=facing || other.getValue(COLUMN)!=x+1 || other.getValue(ROW)!=z+1) return false;
        }
        return true;
    }
    @Override protected void onPlace(BlockState state,Level level,BlockPos pos,BlockState before,boolean moved) {
        if (!level.isClientSide() && center(state)) level.scheduleTick(pos,this,1);
    }
    @Override protected void tick(BlockState state,ServerLevel level,BlockPos pos,RandomSource random) {
        if (!center(state)) return;
        // Do not destroy or duplicate furniture while another footprint chunk is still loading.
        for (var part:footprint(pos,state.getValue(FACING),mode)) if(!level.hasChunkAt(part)) {level.scheduleTick(pos,this,40);return;}
        if (!complete(level,pos,state)) {level.destroyBlock(pos,true);return;}
        TableSeats.ensure(level,pos,state.getValue(FACING));
        level.scheduleTick(pos,this,40);
    }
    @Override protected void affectNeighborsAfterRemoval(BlockState state,ServerLevel level,BlockPos pos,boolean moved) {
        if (level.getBlockState(pos).is(this)) return;
        BlockPos anchor=anchor(pos,state); var key=new Removal(level,anchor);
        if (!REMOVING.get().add(key)) return;
        try {
            TableSeats.remove(level,anchor);
            // Only the piece the player/explosion broke drops an item. Siblings are cleared without drops.
            for (var part:footprint(anchor,state.getValue(FACING),mode)) {
                var remaining=level.getBlockState(part);
                if (remaining.is(this) && anchor(part,remaining).equals(anchor)) level.setBlock(part,Blocks.AIR.defaultBlockState(),3);
            }
        } finally { REMOVING.get().remove(key); }
    }
    @Override protected VoxelShape getShape(BlockState state,BlockGetter level,BlockPos pos,CollisionContext context) {
        var shape=Block.box(0,12,0,16,16,16);
        int x=state.getValue(COLUMN)-1,z=state.getValue(ROW)-1;
        int radius=depth()/2;
        // Four legs at the actual outside corners; inner sections leave open knee room.
        for(int lx:new int[]{1,13}) for(int lz:new int[]{1,13}) {
            boolean edgeX=x==(lx==1?-1:1);
            boolean edgeZ=radius==0 || z==(lz==1?-radius:radius);
            if(!edgeX || !edgeZ)continue;
            Direction facing=state.getValue(FACING);
            int wx=lx,wz=lz;
            for(int n=0;n<rotation(facing);n++){int previous=wx;wx=14-wz;wz=previous;}
            shape=Shapes.or(shape,Block.box(wx,0,wz,wx+2,12,wz+2));
        }
        return shape;
    }
    public static int rotation(Direction facing) { return switch(facing){case EAST->1;case SOUTH->2;case WEST->3;default->0;}; }
    @Override protected InteractionResult useWithoutItem(BlockState state,Level level,BlockPos pos,Player player,BlockHitResult hit) {
        if (player instanceof ServerPlayer server) {
            BlockPos root=anchor(pos,state);
            if (!complete(level,root,level.getBlockState(root))) return InteractionResult.FAIL;
            TableSeats.ensure((ServerLevel)level,root,state.getValue(FACING));
            craftbound.game.TableLobby.open(server,root);
        }
        return InteractionResult.SUCCESS;
    }
    @Override protected BlockState rotate(BlockState state,Rotation rotation) { return state.setValue(FACING,rotation.rotate(state.getValue(FACING))); }
    @Override protected BlockState mirror(BlockState state,Mirror mirror) {
        // Mirroring a whole structure reverses local coordinates as well as the facing.
        Direction facing=state.getValue(FACING); var result=state.rotate(mirror.getRotation(facing));
        if(mirror!=Mirror.NONE) result=result.setValue(COLUMN,2-state.getValue(COLUMN));
        return result;
    }
}
