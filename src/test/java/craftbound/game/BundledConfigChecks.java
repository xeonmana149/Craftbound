package craftbound.game;

import craftbound.BundledConfig;
import java.nio.file.Files;
import static craftbound.game.WorkshopChecks.ok;

// Config files written from the jar update themselves when a newer jar bundles new content,
// unless their owner edited them.
final class BundledConfigChecks {
    static void run() {
        try {
            var dir = Files.createTempDirectory("craftbound-bundled");
            var file = dir.resolve("cards.json");
            BundledConfig.refresh(file, "v1");
            ok(Files.readString(file).equals("v1") && Files.exists(dir.resolve("cards.json.bundled")), "A missing file is written with a marker");
            BundledConfig.refresh(file, "v2");
            ok(Files.readString(file).equals("v2") && Files.readString(dir.resolve("cards.json.previous")).equals("v1"),
                    "An untouched file updates to the new bundle, keeping a backup");
            Files.writeString(file, "edited");
            BundledConfig.refresh(file, "v3");
            ok(Files.readString(file).equals("edited"), "An edited file is never replaced");
            var legacy = dir.resolve("trials.json");
            Files.writeString(legacy, "old");
            BundledConfig.refresh(legacy, "new");
            ok(Files.readString(legacy).equals("old"), "Files from before markers existed are left alone");
        } catch (java.io.IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }
}
