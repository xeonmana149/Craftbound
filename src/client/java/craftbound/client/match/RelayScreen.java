package craftbound.client.match;

import com.google.gson.JsonObject;
import net.minecraft.client.gui.screens.Screen;

/** Both allied battlefields and both enemy battlefields remain visible on one mat. */
public final class RelayScreen extends MultiplayerBoardScreen {
    public RelayScreen(JsonObject state){super(state,true);}
    public RelayScreen layoutPreview(Screen parent){previewReturn=parent;return this;}
}
