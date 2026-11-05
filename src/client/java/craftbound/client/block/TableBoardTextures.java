package craftbound.client.block;

import com.google.gson.JsonObject;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.ProjectionType;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.textures.FilterMode;
import craftbound.Craftbound;
import craftbound.client.card.CardCanvas;
import craftbound.client.card.CardRenderEpoch;
import craftbound.client.match.TableBoardFace;
import craftbound.client.mixin.TableGuiRendererAccess;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.render.GuiRenderer;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.resources.Identifier;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import java.util.*;

/** Vanilla's GUI item atlas resolves 3D art first; the world sees a single flat textured quad. */
public final class TableBoardTextures {
    private static final int MAX_SURFACES = 16;
    private static final Map<String,Surface> SURFACES=new LinkedHashMap<>(16, .75F, true);
    private static final Set<Surface> RETIRED = new HashSet<>();
    private static Engine engine;
    private static long nextId;
    private static final class Surface extends AbstractTexture {
        final Identifier id=Craftbound.id("table_board/"+(nextId++));
        final TextureTarget target=new TextureTarget("Craftbound table",1024,1024,GpuFormat.RGBA8_UNORM,GpuFormat.D32_FLOAT);
        String json="";JsonObject board;long updated,lastUsed,epoch=-1;boolean animated;
        Surface(){texture=target.getColorTexture();textureView=target.getColorTextureView();
            sampler=RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST);
            Minecraft.getInstance().getTextureManager().register(id,this);}
        @Override public void close(){target.destroyBuffers();texture=null;textureView=null;}
    }
    private static final class Engine implements AutoCloseable {
        final GuiRenderState state=new GuiRenderState();
        final RenderBuffers buffers=new RenderBuffers(1);
        final FeatureRenderDispatcher features;
        final GuiRenderer gui;
        final Projection projection=new Projection();
        final ProjectionMatrixBuffer projectionBuffer=new ProjectionMatrixBuffer("Craftbound table GUI");
        final net.minecraft.client.renderer.fog.FogRenderer fog=new net.minecraft.client.renderer.fog.FogRenderer();
        Engine(){
            var mc=Minecraft.getInstance();
            features=new FeatureRenderDispatcher(buffers,mc.getModelManager(),mc.getAtlasManager(),mc.font,mc.gameRenderer.gameRenderState());
            gui=new GuiRenderer(state,features,List.of());
            projection.setupOrtho(1000,11000,TableBoardFace.WIDTH,TableBoardFace.HEIGHT,true);
        }
        void paint(Surface surface,JsonObject board,long now){
            var mc=Minecraft.getInstance();
            var oldProjection=RenderSystem.getProjectionMatrixBuffer();var oldType=RenderSystem.getProjectionType();
            var oldFog=RenderSystem.getShaderFog();var oldLights=RenderSystem.getShaderLights();
            // GUI item atlases must not inherit the world's camera rotation (top-down makes flat items edge-on).
            RenderSystem.getModelViewStack().pushMatrix();RenderSystem.getModelViewStack().identity();
            RenderSystem.setShaderFog(fog.getBuffer(net.minecraft.client.renderer.fog.FogRenderer.FogMode.NONE));
            try {
                state.reset();
                var graphics=new GuiGraphicsExtractor(mc,state,-1,-1);
                var canvas = new AnimationCanvas(new CardCanvas.Gui(graphics));
                TableBoardFace.draw(canvas,mc.font,board,now);
                surface.animated = canvas.animated || hasAnimatedEffects(board);
                var access=(TableGuiRendererAccess)gui;
                access.craftbound$prepare();access.craftbound$vertices().upload();
                RenderSystem.resizeAllAutoStorageIndexBuffers();
                RenderSystem.getDevice().createCommandEncoder().clearColorAndDepthTextures(
                    surface.target.getColorTexture(),new Vector4f(0,0,0,1),surface.target.getDepthTexture(),0);
                RenderSystem.setProjectionMatrix(projectionBuffer.getBuffer(projection),ProjectionType.ORTHOGRAPHIC);
                var transform=RenderSystem.getDynamicUniforms().writeTransform(new Matrix4f().translation(0,0,-11000));
                access.craftbound$draw(()->"Craftbound tabletop",surface.target,transform,0,access.craftbound$draws().size());
            } finally {
                var access=(TableGuiRendererAccess)gui;
                access.craftbound$vertices().endFrame();access.craftbound$draws().clear();access.craftbound$clearUnusedItems();
                gui.endFrame();buffers.endFrame();state.reset();
                RenderSystem.setProjectionMatrix(oldProjection,oldType);
                RenderSystem.setShaderFog(oldFog);RenderSystem.setShaderLights(oldLights);
                RenderSystem.getModelViewStack().popMatrix();
            }
        }
        public void close(){gui.close();features.close();buffers.close();projectionBuffer.close();fog.close();}
    }
    /** Resolves once per paint, rather than guessing which resource-pack item models animate. */
    private static final class AnimationCanvas implements CardCanvas {
        private final CardCanvas delegate;
        boolean animated;
        AnimationCanvas(CardCanvas delegate) { this.delegate = delegate; }
        public org.joml.Matrix3x2fStack pose() { return delegate.pose(); }
        public void fill(int x0,int y0,int x1,int y1,int color) { delegate.fill(x0,y0,x1,y1,color); }
        public void fillGradient(int x0,int y0,int x1,int y1,int top,int bottom) { delegate.fillGradient(x0,y0,x1,y1,top,bottom); }
        public void text(net.minecraft.client.gui.Font font,net.minecraft.util.FormattedCharSequence text,int x,int y,int color,boolean shadow) {
            delegate.text(font,text,x,y,color,shadow);
        }
        public void item(net.minecraft.world.item.ItemStack stack,int x,int y) {
            delegate.item(stack,x,y);
            if (animated || stack.isEmpty()) return;
            var mc = Minecraft.getInstance();
            var item = new net.minecraft.client.renderer.item.ItemStackRenderState();
            mc.getItemModelResolver().updateForTopItem(item,stack,net.minecraft.world.item.ItemDisplayContext.GUI,mc.level,null,0);
            var model = stack.get(net.minecraft.core.component.DataComponents.ITEM_MODEL);
            // Range/select/special models can depend on time, even if their selected sprite is static.
            animated = item.isAnimated() || model == null
                    || !(mc.getModelManager().getItemModel(model) instanceof net.minecraft.client.renderer.item.CuboidItemModelWrapper);
        }
        public void frameRegion(Identifier texture,int u,int v,int sw,int sh,int x,int y,int w,int h) {
            delegate.frameRegion(texture,u,v,sw,sh,x,y,w,h);
        }
        public void shimmer(int x,int y,int w,int h,float diagonal,int halfWidth,int alpha) {
            animated = true;
            delegate.shimmer(x,y,w,h,diagonal,halfWidth,alpha);
        }
    }

    static boolean hasAnimatedEffects(com.google.gson.JsonElement value) {
        if (value.isJsonArray()) {
            for (var entry : value.getAsJsonArray()) if (hasAnimatedEffects(entry)) return true;
        } else if (value.isJsonObject()) {
            var object = value.getAsJsonObject();
            for (String flag : List.of("ench", "frozen", "withered", "invulnerable", "totem")) {
                if (object.has(flag) && object.get(flag).isJsonPrimitive() && object.get(flag).getAsBoolean()) return true;
            }
            if (object.has("clockTicks") && (!object.has("paused") || !object.get("paused").getAsBoolean())) {
                int ticks = object.get("clockTicks").getAsInt();
                if (ticks >= 0 && ticks <= 400) return true;
            }
            for (var entry : object.entrySet()) if (hasAnimatedEffects(entry.getValue())) return true;
        }
        return false;
    }

    static boolean needsPaint(boolean changed, boolean animated, long elapsed) {
        return changed || (animated && elapsed >= 200);
    }

    private static void releaseAfterFrame(Surface surface) {
        // texture() runs during world rendering; the next fenced-task pass is before the next
        // frame. Keep the texture valid for quads already queued in the current frame.
        RETIRED.add(surface);
        RenderSystem.queueFencedTask(() -> {
            if (RETIRED.remove(surface)) Minecraft.getInstance().getTextureManager().release(surface.id);
        });
    }

    static boolean hasTextureCapacity(int cached, int retired) {
        return cached + retired < MAX_SURFACES;
    }

    /** May return null under GPU-cache pressure; the caller keeps the normal tabletop visible. */
    public static Identifier texture(String key,String json,long now){
        if(engine==null)engine=new Engine();
        var iterator=SURFACES.entrySet().iterator();
        while(iterator.hasNext()){
            var entry=iterator.next();
            if(now-entry.getValue().lastUsed>5000){releaseAfterFrame(entry.getValue());iterator.remove();}
        }
        Surface surface = SURFACES.get(key);
        if (surface == null) {
            if (SURFACES.size() >= MAX_SURFACES) {
                var oldest = SURFACES.entrySet().iterator();
                var evicted = oldest.next().getValue();
                // Never evict a hot table: a 17th visible table must not cause every cached table
                // to allocate and repaint again next frame. Overload leaves the normal felt visible.
                if (now - evicted.lastUsed < 1000) return null;
                oldest.remove();
                releaseAfterFrame(evicted);
            }
            // Retired textures may still be referenced by this frame's queued quads. Count them
            // against the same memory budget; a slot is available after the next fence pass.
            if (!hasTextureCapacity(SURFACES.size(), RETIRED.size())) return null;
            surface = new Surface();
            SURFACES.put(key, surface);
        }
        surface.lastUsed=now;
        long epoch = CardRenderEpoch.current(Minecraft.getInstance().font);
        boolean changed=!json.equals(surface.json);
        if (changed) {
            surface.board=com.google.gson.JsonParser.parseString(json).getAsJsonObject();
            surface.json=json;
        }
        if(needsPaint(changed || surface.epoch != epoch,surface.animated,now-surface.updated)){
            engine.paint(surface,surface.board,now);
            surface.updated=now;
            surface.epoch=epoch;
        }
        return surface.id;
    }
    public static void clear(){
        for(var surface:SURFACES.values())Minecraft.getInstance().getTextureManager().release(surface.id);
        SURFACES.clear();
        for(var surface:RETIRED)Minecraft.getInstance().getTextureManager().release(surface.id);
        RETIRED.clear();
        if(engine!=null){engine.close();engine=null;}
    }
    private TableBoardTextures(){}
}
