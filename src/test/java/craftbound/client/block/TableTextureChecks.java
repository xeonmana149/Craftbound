package craftbound.client.block;

import com.google.gson.JsonParser;

public final class TableTextureChecks {
    public static void run() {
        if (TableBoardTextures.needsPaint(false, false, 60_000)) throw new AssertionError("Static table repainted");
        if (!TableBoardTextures.needsPaint(true, false, 0)) throw new AssertionError("Changed table not repainted");
        if (TableBoardTextures.needsPaint(false, true, 199) || !TableBoardTextures.needsPaint(false, true, 200))
            throw new AssertionError("Animated table cadence changed");
        for (String flag : java.util.List.of("ench", "frozen", "withered", "invulnerable", "totem")) {
            var board = JsonParser.parseString("{\"players\":[{\"board\":[{\"" + flag + "\":true}]}]}");
            if (!TableBoardTextures.hasAnimatedEffects(board)) throw new AssertionError("Frozen animation: " + flag);
        }
        if (TableBoardTextures.hasAnimatedEffects(JsonParser.parseString("{\"paused\":true,\"clockTicks\":200}"))
                || !TableBoardTextures.hasAnimatedEffects(JsonParser.parseString("{\"clockTicks\":200}"))
                || TableBoardTextures.hasAnimatedEffects(JsonParser.parseString("{\"clockTicks\":1400}")))
            throw new AssertionError("Timer animation refresh mismatch");
        if (!TableBoardTextures.hasTextureCapacity(15, 0) || TableBoardTextures.hasTextureCapacity(16, 0)
                || TableBoardTextures.hasTextureCapacity(12, 4) || TableBoardTextures.hasTextureCapacity(0, 16))
            throw new AssertionError("Pending textures escaped GPU memory budget");
        System.out.println("PASS: static tabletop reuse, animated refresh and active-plus-retired texture budget.");
    }
    private TableTextureChecks() {}
}
