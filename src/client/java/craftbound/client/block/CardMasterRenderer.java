package craftbound.client.block;

import craftbound.entity.CardSeatEntity;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.npc.VillagerModel;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.WanderingTraderRenderer;
import net.minecraft.client.renderer.entity.state.VillagerRenderState;
import net.minecraft.world.entity.npc.wanderingtrader.WanderingTrader;

/** Vanilla traders do not bend their legs when mounted; only our Card Master needs this pose. */
public final class CardMasterRenderer extends WanderingTraderRenderer {
    public static class State extends VillagerRenderState { public boolean seated; }
    public static final class SeatedModel extends VillagerModel {
        public SeatedModel(ModelPart root) { super(root); }
        public static LayerDefinition createLayer() {
            var mesh=VillagerModel.createBodyModel();
            var root=mesh.getRoot();
            // Keep vanilla standing legs/robe intact; the seated version has actual knee joints.
            for(String side:new String[]{"right","left"}) {
                float x=side.equals("right")?-2:2;
                var thigh=root.addOrReplaceChild(side+"_seated_leg",CubeListBuilder.create().texOffs(0,22)
                        .addBox(-2,0,-2,4,6,4),PartPose.offset(x,12,0));
                thigh.addOrReplaceChild("shin",CubeListBuilder.create().texOffs(0,28)
                        .addBox(-2,0,-2,4,6,4),PartPose.offset(0,6,0));
            }
            root.getChild("body").addOrReplaceChild("seated_jacket",CubeListBuilder.create().texOffs(0,38)
                    .addBox(-4,0,-3,8,12,6,new CubeDeformation(0.5F))
                    .texOffs(0,50).addBox(-4,12,-8,8,2,8,new CubeDeformation(0.25F)),PartPose.ZERO);
            return LayerDefinition.create(mesh,64,64);
        }
        @Override public void setupAnim(VillagerRenderState state) {
            super.setupAnim(state);
            boolean seated=state instanceof State master && master.seated;
            root().getChild("body").getChild("jacket").visible=!seated;
            root().getChild("body").getChild("seated_jacket").visible=seated;
            for(String side:new String[]{"right","left"}) {
                root().getChild(side+"_leg").visible=!seated;
                var thigh=root().getChild(side+"_seated_leg");
                thigh.visible=seated;
                if(seated) {
                    thigh.xRot=-(float)Math.PI/2;
                    thigh.getChild("shin").xRot=(float)Math.PI/2;
                }
            }
        }
    }
    public CardMasterRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.model=new SeatedModel(SeatedModel.createLayer().bakeRoot());
    }
    @Override public State createRenderState() { return new State(); }
    @Override public void extractRenderState(WanderingTrader entity,VillagerRenderState state,float partialTick) {
        super.extractRenderState(entity,state,partialTick);
        ((State)state).seated=entity.getVehicle() instanceof CardSeatEntity;
    }
}
