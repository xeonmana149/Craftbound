package craftbound.client.mixin;

import craftbound.client.card.CardDragMotion;
import craftbound.item.ModItems;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Covers carried cards in inventory, chests and every other vanilla container screen.
@Mixin(AbstractContainerScreen.class)
public class CardCursorMotionMixin {
    @Unique private final CardDragMotion craftbound$cardMotion = new CardDragMotion();
    @Unique private boolean craftbound$movingCard;

    @Inject(method = "extractFloatingItem", at = @At("HEAD"))
    private void craftbound$beginCardMotion(GuiGraphicsExtractor g, ItemStack stack, int x, int y, String count, CallbackInfo ci) {
        this.craftbound$movingCard = stack.is(ModItems.CARD);
        if (this.craftbound$movingCard) {
            this.craftbound$cardMotion.begin(g, x + 8, y + 8, 16);
            g.pose().translate(-x - 8, -y - 8);
        }
    }

    @Inject(method = "extractFloatingItem", at = @At("RETURN"))
    private void craftbound$endCardMotion(GuiGraphicsExtractor g, ItemStack stack, int x, int y, String count, CallbackInfo ci) {
        if (this.craftbound$movingCard) {
            g.pose().popMatrix();
            this.craftbound$movingCard = false;
        }
    }
}
