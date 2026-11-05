package craftbound.client.mixin;

import craftbound.client.block.TableBoardTextures;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public abstract class TableResourceReloadMixin {
    @Inject(method="onResourceManagerReload",at=@At("HEAD"))
    private void craftbound$reloadTables(net.minecraft.server.packs.resources.ResourceManager resources, CallbackInfo ci) {
        craftbound.client.card.CardFrames.reload(resources);
        TableBoardTextures.clear();
    }
}
