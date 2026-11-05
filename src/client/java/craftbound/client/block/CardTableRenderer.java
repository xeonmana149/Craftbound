package craftbound.client.block;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import craftbound.block.CardTableBlockEntity;
import craftbound.client.match.TableBoardFace;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.blockentity.*;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.world.phys.Vec3;

/** One composited surface: no world-space stacking of text, item models, or effect triangles. */
public final class CardTableRenderer implements BlockEntityRenderer<CardTableBlockEntity,CardTableRenderer.State> {
    public static final class State extends BlockEntityRenderState {String json="",key;float yaw,width=1,depth=1;}
    public CardTableRenderer(BlockEntityRendererProvider.Context context){}
    public State createRenderState(){return new State();}
    public void extractRenderState(CardTableBlockEntity table,State state,float tick,Vec3 camera,ModelFeatureRenderer.CrumblingOverlay cracks){
        BlockEntityRenderer.super.extractRenderState(table,state,tick,camera,cracks);
        state.json="";state.yaw=table.boardYaw();state.width=1;state.depth=1;
        if(table.getBlockState().getBlock() instanceof craftbound.block.MultiplayerTableBlock block) {
            state.width=3;state.depth=block.depth();
        }
        if(camera.distanceToSqr(Vec3.atCenterOf(table.getBlockPos()))>24*24)return;
        state.json=table.publicBoard();state.key=table.getLevel().dimension().identifier()+"/"+table.getBlockPos().asLong();
    }
    public void submit(State state,PoseStack pose,SubmitNodeCollector collector,CameraRenderState camera){
        if(state.json.isEmpty())return;
        var texture=TableBoardTextures.texture(state.key,state.json,System.nanoTime()/1_000_000);
        if (texture == null) return;
        pose.pushPose();transform(pose,state.yaw,state.width,state.depth);
        collector.submitCustomGeometry(pose,RenderTypes.entityCutout(texture),(p,buffer)->{
            for(float[] v:new float[][]{{0,TableBoardFace.HEIGHT,0,1},{TableBoardFace.WIDTH,TableBoardFace.HEIGHT,1,1},
                    {TableBoardFace.WIDTH,0,1,0},{0,0,0,0}})
                // Render-target textures have the opposite vertical origin to uploaded item textures.
                buffer.addVertex(p.pose(),v[0],v[1],0).setColor(0xFFFFFFFF).setUv(v[2],1-v[3])
                    .setOverlay(OverlayTexture.NO_OVERLAY).setLight(state.lightCoords).setNormal(p,0,0,1);
        });
        pose.popPose();
    }
    public static void transform(PoseStack pose,float yaw){transform(pose,yaw,1,1);}
    public static void transform(PoseStack pose,float yaw,float width,float depth){
        pose.translate(.5,1.004,.5);pose.rotateDegrees(Axis.YP,180-yaw);pose.rotateDegrees(Axis.XP,-90);
        pose.scale((width-.03F)/TableBoardFace.WIDTH,-(depth-.03F)/TableBoardFace.HEIGHT,.004F);
        pose.translate(-TableBoardFace.WIDTH/2F,-TableBoardFace.HEIGHT/2F,0);
    }
}
