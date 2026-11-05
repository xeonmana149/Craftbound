package craftbound.block;

import java.util.*;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/** The private paused game belongs to this table. Only the public board is synced. */
public final class CardTableBlockEntity extends BlockEntity {
    private String savedMatch = "";
    private String publicBoard = "";
    private float boardYaw;
    private UUID waitingMaster;
    private final Map<UUID,Long> ready = new HashMap<>();
    public CardTableBlockEntity(BlockPos pos, BlockState state) { super(ModBlocks.CARD_TABLE_ENTITY,pos,state); }
    public String savedMatch() { return savedMatch; }
    public boolean paused() { return !savedMatch.isEmpty(); }
    public String publicBoard() { return publicBoard; }
    public float boardYaw() { return boardYaw; }
    public UUID waitingMaster() { return waitingMaster; }
    public void waitingMaster(UUID id) { waitingMaster=id;setChanged(); }
    public void saveMatch(String json) { savedMatch=json; ready.clear(); setChanged(); }
    public void ready(UUID player,long time) { ready.put(player,time+1200); }
    public void cancelReady(UUID player) { ready.remove(player); }
    public boolean isReady(UUID player,long time) { return ready.getOrDefault(player,0L)>time; }
    public void board(String json,float yaw) {
        if (json.equals(publicBoard) && yaw==boardYaw) return;
        publicBoard=json;boardYaw=yaw;setChanged();
        if(level!=null) level.sendBlockUpdated(worldPosition,getBlockState(),getBlockState(),3);
    }
    @Override protected void loadAdditional(ValueInput in) {
        super.loadAdditional(in);
        boolean previouslyVisible=!publicBoard.isEmpty();
        savedMatch=in.getStringOr("match","");
        publicBoard=in.getStringOr("board","");
        boardYaw=in.getFloatOr("yaw",0);
        waitingMaster=in.read("waitingMaster",net.minecraft.core.UUIDUtil.CODEC).orElse(null);
        ready.clear();
        // An old table can acquire its block entity after its client section was compiled.
        // A same-state vanilla block update alone does not rebuild that section's BE list.
        if(level!=null && level.isClientSide() && previouslyVisible!=!publicBoard.isEmpty())
            level.sendBlockUpdated(worldPosition,getBlockState(),getBlockState(),3);
    }
    @Override protected void saveAdditional(ValueOutput out) {
        super.saveAdditional(out);
        out.putString("match",savedMatch);
        // Running matches do not survive a shutdown; never leave a phantom active board.
        out.putString("board",paused()?publicBoard:"");
        out.putFloat("yaw",boardYaw);
        if(waitingMaster!=null) out.store("waitingMaster",net.minecraft.core.UUIDUtil.CODEC,waitingMaster);
    }
    @Override public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return publicTag(publicBoard,boardYaw);
    }
    public static CompoundTag publicTag(String board,float yaw) {
        CompoundTag tag=new CompoundTag();tag.putString("board",board);tag.putFloat("yaw",yaw);return tag;
    }
    @Override public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
