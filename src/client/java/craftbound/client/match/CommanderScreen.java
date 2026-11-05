package craftbound.client.match;

import com.google.gson.JsonObject;
import net.minecraft.client.gui.screens.Screen;

/** One fixed home field and one selectable rival field, with all four players in the match. */
public final class CommanderScreen extends MultiplayerBoardScreen {
    public CommanderScreen(JsonObject state){super(state,false);}
    public CommanderScreen layoutPreview(Screen parent){previewReturn=parent;return this;}
}
