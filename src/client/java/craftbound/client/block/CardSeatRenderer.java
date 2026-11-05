package craftbound.client.block;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import craftbound.entity.CardSeatEntity;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;

/** Oak chair with four legs, a broad seat and a slatted back. Local +Z faces the table. */
public final class CardSeatRenderer extends EntityRenderer<CardSeatEntity,CardSeatRenderer.State> {
    public static class State extends EntityRenderState { public float yaw; }
    private static final Identifier WOOD=Identifier.withDefaultNamespace("textures/block/oak_planks.png");
    private final Model<State> model;
    public CardSeatRenderer(EntityRendererProvider.Context context) {
        super(context);
        MeshDefinition mesh=new MeshDefinition();
        var cubes=CubeListBuilder.create().texOffs(0,0)
                .addBox(-6,-9,-6,12,2,12)
                .addBox(-6,-7,-6,2,7,2).addBox(4,-7,-6,2,7,2)
                .addBox(-6,-7,4,2,7,2).addBox(4,-7,4,2,7,2)
                .addBox(-6,-20,-6,2,11,2).addBox(4,-20,-6,2,11,2)
                .addBox(-4,-20,-6,8,3,2).addBox(-4,-15,-6,8,2,2);
        mesh.getRoot().addOrReplaceChild("chair",cubes,PartPose.ZERO);
        model=new Model<State>(LayerDefinition.create(mesh,16,16).bakeRoot(),RenderTypes::entityCutout) {};
        shadowRadius=0.35F;
    }
    @Override public State createRenderState() { return new State(); }
    @Override public void extractRenderState(CardSeatEntity seat,State state,float partialTick) {
        super.extractRenderState(seat,state,partialTick);
        state.yaw=seat.getYRot();
    }
    @Override public void submit(State state,PoseStack pose,SubmitNodeCollector collector,CameraRenderState camera) {
        super.submit(state,pose,collector,camera);
        pose.pushPose();
        pose.rotateDegrees(Axis.YP,-state.yaw);
        pose.scale(1,-1,1);
        collector.submitModel(model,state,pose,model.renderType(WOOD),state.lightCoords,OverlayTexture.NO_OVERLAY,0);
        pose.popPose();
    }
}
