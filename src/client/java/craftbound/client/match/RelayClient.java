package craftbound.client.match;

import com.google.gson.JsonParser;
import craftbound.network.RelayViewPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

public final class RelayClient {
    public static void initialize() {
        ClientPlayNetworking.registerGlobalReceiver(RelayViewPayload.TYPE,(payload,ctx)->{
            var mc=ctx.client();var current=mc.gui.screen();

            var data=JsonParser.parseString(payload.json()).getAsJsonObject();
            if(data.has("closed")) { if(current instanceof RelayScreen screen && screen.same(data))mc.gui.setScreen(null);return; }
            if(data.has("joinError")) { if(current instanceof RelaySetupScreen setup)setup.notice(data.get("joinError").getAsString());return; }
            if(current instanceof RelayScreen screen && screen.same(data))screen.update(data);
            else if(payload.open())mc.gui.setScreen(new RelayScreen(data));
        });
    }
    private RelayClient(){}
}