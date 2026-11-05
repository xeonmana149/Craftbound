package craftbound.client.card;

import com.mojang.blaze3d.vertex.PoseStack;
import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import craftbound.card.Cards;
import craftbound.component.ModComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.joml.Matrix3x2f;
import org.joml.Matrix3x2fStack;
import org.joml.Matrix4f;

/** Extracts the inspection layout into immutable commands; never reads the block entity during submission. */
public final class WorldCardFace implements CardCanvas {
    @FunctionalInterface private interface Draw {
        void submit(PoseStack pose, SubmitNodeCollector collector, int light);
    }
    private record Command(Matrix3x2f transform, Draw draw, ItemStack item, int itemX, int itemY) {}
    private static final Map<Identifier, List<Command>> TEMPLATES = new LinkedHashMap<>(32, .75F, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<Identifier, List<Command>> eldest) { return size() > 128; }
    };
    private static long templateEpoch = -1, templateBuilds;
    static long templateBuilds() { return templateBuilds; }
    static int cachedTemplates() { return TEMPLATES.size(); }
    private boolean recordingTemplate;
    private final Matrix3x2fStack transform = new Matrix3x2fStack(16);
    private final List<Command> commands = new ArrayList<>();
    private final ItemModelResolver resolver;
    private final Level level;
    public WorldCardFace(ItemModelResolver resolver, Level level) { this.resolver=resolver; this.level=level; }
    public static WorldCardFace extract(Font font, ItemStack card, ItemModelResolver resolver, Level level) {
        var face = new WorldCardFace(resolver, level);
        Identifier id = card.get(ModComponents.CARD_ID);
        if (id == null || Cards.get(id) == null) return face;
        long epoch = CardRenderEpoch.current(font);
        if (epoch != templateEpoch) { TEMPLATES.clear(); templateEpoch = epoch; }
        List<Command> template = TEMPLATES.get(id);
        if (template == null) {
            var layout = new WorldCardFace(null, null);
            layout.recordingTemplate = true;
            CardRenderer.drawFace(layout, font, id, false, 0, 0, 1, -1, -1);
            template = List.copyOf(layout.commands);
            TEMPLATES.put(id, template);
            templateBuilds++;
        }
        for (var command : template) {
            // Item states are extracted afresh, so clocks, custom model predicates and animated
            // sprites remain live. Never mutate a render state queued by an earlier frame.
            if (command.item() == null) face.commands.add(command);
            else face.commands.add(new Command(command.transform(),
                    face.itemDraw(command.item(), command.itemX(), command.itemY()), null, 0, 0));
        }
        if (card.has(ModComponents.ENCHANTED)) CardRenderer.drawEnchantedShimmer(face);
        return face;
    }
    public Matrix3x2fStack pose() { return transform; }
    private void add(Draw draw) { commands.add(new Command(new Matrix3x2f(transform), draw, null, 0, 0)); }
    public void fill(int x0,int y0,int x1,int y1,int color) {
        add((pose,collector,light)->collector.submitTextBackground(pose,x0,y0,x1,y1,color,Font.DisplayMode.NORMAL,light));
    }
    public void text(Font font,FormattedCharSequence text,int x,int y,int color,boolean shadow) {
        add((pose,collector,light)->collector.submitText(pose,x,y,text,shadow,Font.DisplayMode.NORMAL,light,color,0,0));
    }
    public void item(ItemStack stack,int x,int y) {
        if (recordingTemplate) {
            commands.add(new Command(new Matrix3x2f(transform), null, stack.copy(), x, y));
        } else add(itemDraw(stack, x, y));
    }
    private Draw itemDraw(ItemStack stack, int x, int y) {
        var item = new ItemStackRenderState();
        resolver.updateForTopItem(item,stack,ItemDisplayContext.GUI,level,null,0);
        return (pose,collector,light)->{
            pose.pushPose();
            pose.translate(x+8,y+8,0);
            // Same 16x16 GUI model and rotation, flattened into the printed card plane.
            pose.scale(16,-16,0.001F);
            item.submit(pose,collector,light,OverlayTexture.NO_OVERLAY,0);
            pose.popPose();
        };
    }
    public void frameRegion(net.minecraft.resources.Identifier texture, int u, int v, int sw, int sh,
                            int x, int y, int width, int height) {
        float left = u / (float) CardRenderer.WIDTH, right = (u + sw) / (float) CardRenderer.WIDTH;
        float top = v / (float) CardRenderer.HEIGHT, bottom = (v + sh) / (float) CardRenderer.HEIGHT;
        add((pose, collector, light) -> collector.submitCustomGeometry(pose, RenderTypes.entityCutout(texture), (p, buffer) -> {
            // The stand flips the card's Y axis. This winding faces the front of the easel.
            frameVertex(buffer,p,x,y+height,left,bottom,light);
            frameVertex(buffer,p,x+width,y+height,right,bottom,light);
            frameVertex(buffer,p,x+width,y,right,top,light);
            frameVertex(buffer,p,x,y,left,top,light);
        }));
    }
    private static void frameVertex(com.mojang.blaze3d.vertex.VertexConsumer buffer, PoseStack.Pose pose,
                                    int x, int y, float u, float v, int light) {
        buffer.addVertex(pose.pose(),x,y,0).setColor(0xFFFFFFFF).setUv(u,v)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose,0,0,1);
    }
    public void shimmer(int x,int y,int width,int height,float diagonal,int halfWidth,int alpha) {
        var vertices=ShimmerMesh.build(width,height,diagonal,halfWidth,alpha);
        if(vertices.isEmpty()) return;
        add((pose,collector,light)->{
            // A translucent overlay must follow every face pass, including text and translucent
            // item art. Submission order within order(0) does not guarantee render order.
            // Keep a small physical gap too: glyphs and flattened item models have their own Z.
            pose.pushPose();
            pose.translate(0,0,0.5F);
            collector.order(1).submitCustomGeometry(pose,RenderTypes.debugQuads(),(p,buffer)->{
                for(var v:vertices) buffer.addVertex(p.pose(),x+v.x(),y+v.y(),0)
                        .setColor((v.alpha()<<24)|0xB070FF);
            });
            pose.popPose();
        });
    }
    public void submit(PoseStack pose,SubmitNodeCollector collector,int light) {
        float depth=0;
        for(var command:commands) {
            var m=command.transform();
            pose.pushPose();
            pose.translate(0,0,depth);
            pose.mulPose(new Matrix4f().m00(m.m00()).m01(m.m01()).m10(m.m10()).m11(m.m11())
                    .m30(m.m20()).m31(m.m21()));
            command.draw().submit(pose,collector,light);
            pose.popPose();
            depth+=0.01F;
        }
    }
}
