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
    private final Screen parent;
    private final InspectAnimation opening = new InspectAnimation();

    public SleevePreviewScreen(ItemStack sleeve) {
        super(sleeve.getHoverName());
        Identifier id = sleeve.get(ModComponents.SLEEVE_ID);
        this.design = Sleeves.get(id == null ? Sleeves.CLASSIC : id);
        this.parent=null;
    }
    public SleevePreviewScreen(Identifier sleeve,Screen parent) {
        super(Sleeves.name(sleeve));this.design=Sleeves.get(sleeve);this.parent=parent;
    }
    @Override public void onClose(){minecraft.gui.setScreen(parent);}

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        g.fill(0,0,width,height,0xE018211D);
        super.extractRenderState(g, mouseX, mouseY, partialTick);
        float scale=Math.min(2F,Math.min((height-72F)/CardRenderer.HEIGHT,(width-40F)/CardRenderer.WIDTH))*opening.scale();
        int x = Math.round((this.width - CardRenderer.WIDTH * scale) / 2);
        int y = Math.round((this.height - CardRenderer.HEIGHT * scale) / 2 + 6);
        g.centeredText(this.font, this.title, this.width / 2, y - 14, 0xFFFFFFFF);
        CardRenderer.drawBack(g, this.design, x, y, scale);
        g.centeredText(font,"Click or Esc to return",width/2,height-18,0xFFCEC6B5);
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
