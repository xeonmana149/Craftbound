package craftbound.client.match;

import craftbound.network.ResumeMatchPayload;
import craftbound.network.TableActionPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.network.chat.Component;

/** A world prompt: declining leaves the game saved and the player outside the match. */
public final class MatchResumeScreen extends ConfirmScreen {
    public MatchResumeScreen(ResumeMatchPayload offer) {
        super(accepted -> {
            Minecraft.getInstance().gui.setScreen(null);
            if(accepted) ClientPlayNetworking.send(new TableActionPayload(offer.table(),TableActionPayload.RESUME,0));
        }, Component.literal("Resume this match?"),
                Component.literal(offer.multiplayer()
                        ? "Your game is saved at this table. Both players must confirm within 60 seconds to resume."
                        : "Your game with the Card Master is saved at this table. Sit down and continue?"),
                Component.literal("Resume match"), Component.literal("Not now"));
    }
    @Override public boolean isPauseScreen() { return false; }
    @Override public void onClose() { Minecraft.getInstance().gui.setScreen(null); }
}
