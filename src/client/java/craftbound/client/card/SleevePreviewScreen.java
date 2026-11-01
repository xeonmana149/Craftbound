package craftbound.client.card;

import craftbound.component.ModComponents;
import craftbound.sleeve.Sleeves;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

// Right-clicking a sleeve shows the card back it gives, full size.
public class SleevePreviewScreen extends Screen {
    private final Sleeves.Design design;

    public SleevePreviewScreen(ItemStack sleeve) {
        super(sleeve.getHoverName());
        Identifier id = sleeve.get(ModComponents.SLEEVE_ID);
        this.design = Sleeves.get(id == null ? Sleeves.CLASSIC : id);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(g, mouseX, mouseY, partialTick);
        int scale = CardRenderer.HEIGHT * 2 + 40 <= this.height ? 2 : 1;
        int x = (this.width - CardRenderer.WIDTH * scale) / 2;
        int y = (this.height - CardRenderer.HEIGHT * scale) / 2 + 6;
        g.centeredText(this.font, this.title, this.width / 2, y - 14, 0xFFFFFFFF);
        CardRenderer.drawBack(g, this.design, x, y, scale);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        this.onClose();
        return true;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
