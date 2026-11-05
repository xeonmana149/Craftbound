package craftbound.test;

import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;

/** Keep automated visual checks bounded; these settings apply only to the isolated test client. */
public final class RenderTestSettings {
    public static void apply(ClientGameTestContext context) {
        context.runOnClient(mc -> {
            mc.options.enableVsync().set(true);
            mc.options.framerateLimit().set(60);
        });
    }
    private RenderTestSettings() {}
}
