package craftbound.client.block;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import craftbound.block.CardDisplayBlock;
import craftbound.block.CardDisplayBlockEntity;
import craftbound.client.card.CardRenderer;
import craftbound.client.card.WorldCardFace;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

/** The inspection card, placed on the easel; no separate world-card layout. */
public class CardDisplayRenderer implements BlockEntityRenderer<CardDisplayBlockEntity, CardDisplayRenderer.State> {
    public static class State extends BlockEntityRenderState {
        public WorldCardFace face;
        public Direction facing=Direction.NORTH;
    }
    private final ItemModelResolver resolver;
    public CardDisplayRenderer(BlockEntityRendererProvider.Context context) { resolver=context.itemModelResolver(); }
    @Override public State createRenderState() { return new State(); }
    @Override public void extractRenderState(CardDisplayBlockEntity display,State state,float partialTick,
                                              Vec3 cameraPosition,ModelFeatureRenderer.CrumblingOverlay breakProgress) {
        BlockEntityRenderer.super.extractRenderState(display,state,partialTick,cameraPosition,breakProgress);
        state.facing=display.getBlockState().getValue(CardDisplayBlock.FACING);
        state.face=display.getCard().isEmpty()?null:WorldCardFace.extract(Minecraft.getInstance().font,
                display.getCard(),resolver,display.getLevel());
    }
    @Override public void submit(State state,PoseStack pose,SubmitNodeCollector collector,CameraRenderState camera) {
        if(state.face==null) return;
        pose.pushPose();
        pose.translate(0.5F,0.125F,0.5F);
        pose.rotateDegrees(Axis.YP,-state.facing.toYRot());
        pose.translate(0,0,0.20F);
        pose.rotateDegrees(Axis.XP,-22.5F);
        pose.scale(0.0048F,-0.0048F,0.0048F);
        pose.translate(-CardRenderer.WIDTH/2F,-CardRenderer.HEIGHT,0);
        state.face.submit(pose,collector,state.lightCoords);
        pose.popPose();
    }
}
