package craftbound.client.block;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import craftbound.block.CardDisplayBlock;
import craftbound.block.CardDisplayBlockEntity;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.phys.Vec3;

// Draws the card on a display stand: upright, leaning back a little, facing the way the stand was placed.
// For now it shows the card's item picture; once cards have their own art, that's what will show here.
public class CardDisplayRenderer implements BlockEntityRenderer<CardDisplayBlockEntity, CardDisplayRenderer.State> {
    public static class State extends BlockEntityRenderState {
        public final ItemStackRenderState card = new ItemStackRenderState();
        public Direction facing = Direction.NORTH;
    }

    private final ItemModelResolver itemModelResolver;

    public CardDisplayRenderer(BlockEntityRendererProvider.Context context) {
        this.itemModelResolver = context.itemModelResolver();
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(CardDisplayBlockEntity display, State state, float partialTick, Vec3 cameraPosition,
                                   ModelFeatureRenderer.CrumblingOverlay breakProgress) {
        BlockEntityRenderer.super.extractRenderState(display, state, partialTick, cameraPosition, breakProgress);
        state.facing = display.getBlockState().getValue(CardDisplayBlock.FACING);
        this.itemModelResolver.updateForTopItem(state.card, display.getCard(), ItemDisplayContext.FIXED,
                display.getLevel(), null, (int) display.getBlockPos().asLong());
    }

    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        if (state.card.isEmpty()) {
            return;
        }
        poseStack.pushPose();
        poseStack.translate(0.5F, 0.5F, 0.5F);
        poseStack.rotateDegrees(Axis.YP, -state.facing.toYRot() + 180.0F);
        poseStack.rotateDegrees(Axis.XP, -15.0F);
        poseStack.scale(0.8F, 0.8F, 0.8F);
        state.card.submit(poseStack, collector, state.lightCoords, OverlayTexture.NO_OVERLAY, 0);
        poseStack.popPose();
    }
}
