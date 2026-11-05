package craftbound.client.mixin;
import java.util.List;
import java.util.function.Supplier;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import net.minecraft.client.gui.render.GuiRenderer;
import net.minecraft.client.renderer.StagedVertexBuffer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.*;

/** Reuse vanilla GUI composition, but draw into a table texture instead of the window. */
@Mixin(GuiRenderer.class)
public interface TableGuiRendererAccess {
    @Invoker("prepare") void craftbound$prepare();
    @Accessor("vertexBuffer") StagedVertexBuffer craftbound$vertices();
    @Accessor("draws") List<?> craftbound$draws();
    @Invoker("executeDrawRange") void craftbound$draw(Supplier<String> label,RenderTarget target,GpuBufferSlice transform,int first,int end);
    @Invoker("clearUnusedOversizedItemRenderers") void craftbound$clearUnusedItems();
}
