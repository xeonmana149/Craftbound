package craftbound.client.match;

import craftbound.network.MatchActionPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.network.chat.Component;

/** Server-authoritative consent; closing/declining never pauses the other player's game. */
public final class MatchPauseScreen extends ConfirmScreen {
    public final MatchScreen match;
    public MatchPauseScreen(MatchScreen match) {
        super(accepted -> {
            Minecraft.getInstance().gui.setScreen(match);
            ClientPlayNetworking.send(new MatchActionPayload(accepted?MatchActionPayload.ACCEPT_PAUSE:MatchActionPayload.DECLINE_PAUSE,0,false,-1,-1));
        },Component.literal("Pause this match?"),
                Component.literal("Your opponent wants to pause. Save the game at this table and both walk away? Return here together to resume."),
                Component.literal("Pause game"),Component.literal("Keep playing"));
        this.match=match;
    }
    @Override public boolean isPauseScreen() { return false; }
    @Override public void onClose() {
        Minecraft.getInstance().gui.setScreen(match);
        ClientPlayNetworking.send(new MatchActionPayload(MatchActionPayload.DECLINE_PAUSE,0,false,-1,-1));
    }
}
