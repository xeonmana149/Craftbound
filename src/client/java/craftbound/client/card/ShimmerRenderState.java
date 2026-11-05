package craftbound.client.card;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.state.gui.GuiElementRenderState;
import org.joml.Matrix3x2f;
import org.joml.Matrix3x2fc;

// One GUI submission per shimmering card instead of hundreds of individual pixel tiles.
record ShimmerRenderState(Matrix3x2fc pose, int x, int y, List<ShimmerMesh.Vertex> vertices,
                          ScreenRectangle scissorArea, ScreenRectangle bounds) implements GuiElementRenderState {
    static void submit(GuiGraphicsExtractor g, int x, int y, int width, int height,
                       float diagonal, int halfWidth, int maxAlpha) {
        if (width <= 0 || height <= 0) return;
        Matrix3x2f pose = new Matrix3x2f(g.pose());
        ScreenRectangle scissor = g.scissorStack.peek();
        ScreenRectangle bounds = new ScreenRectangle(x, y, width, height).transformMaxBounds(pose);
        if (scissor != null) bounds = bounds.intersection(scissor);
        if (bounds == null || bounds.width() <= 0 || bounds.height() <= 0) return;
        List<ShimmerMesh.Vertex> vertices = ShimmerMesh.build(width, height, diagonal, halfWidth, maxAlpha);
        if (!vertices.isEmpty()) {
            g.guiRenderState.addGuiElement(new ShimmerRenderState(pose, x, y, vertices, scissor, bounds));
        }
    }

    @Override public RenderPipeline pipeline() { return RenderPipelines.GUI; }
    @Override public TextureSetup textureSetup() { return TextureSetup.noTexture(); }

    @Override public void buildVertices(VertexConsumer consumer) {
        for (ShimmerMesh.Vertex vertex : vertices) {
            consumer.addVertexWith2DPose(pose, x + vertex.x(), y + vertex.y())
                    .setColor((vertex.alpha() << 24) | 0xB070FF);
        }
    }
}
