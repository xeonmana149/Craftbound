package craftbound.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.Containers;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

// Holds the one card shown on a display stand, saves it with the world and sends it to players so they can see it.
public class CardDisplayBlockEntity extends BlockEntity {
    private ItemStack card = ItemStack.EMPTY;

    public CardDisplayBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlocks.CARD_DISPLAY_ENTITY, pos, state);
    }

    public ItemStack getCard() {
        return this.card;
    }

    public void setCard(ItemStack card) {
        this.card = card;
        setChanged();
        if (this.level != null) {
            // Tell nearby players' games to redraw the stand.
            this.level.sendBlockUpdated(this.worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        this.card = input.read("card", ItemStack.CODEC).orElse(ItemStack.EMPTY);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        if (!this.card.isEmpty()) {
            output.store("card", ItemStack.CODEC, this.card);
        }
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveWithoutMetadata(registries);
    }

    // Breaking the stand drops the card.
    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        if (this.level != null && !this.card.isEmpty()) {
            Containers.dropItemStack(this.level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, this.card);
        }
    }
}
