package craftbound.client.match;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

/** Preview snapshots must be discarded when the server replaces the card catalogue. */
public interface LocalLayoutPreview {
    Screen layoutPreviewParent();

    static void catalogueChanged(Minecraft client) {
        if (client.gui.screen() instanceof LocalLayoutPreview preview && preview.layoutPreviewParent()!=null)
            client.gui.setScreen(preview.layoutPreviewParent());
    }
}
