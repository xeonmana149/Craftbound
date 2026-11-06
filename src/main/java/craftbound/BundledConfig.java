package craftbound;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Config files the mod writes from its bundled defaults (cards.json, trials.json). A marker beside the file
 * remembers the version written. If the file still matches that version (the owner never edited it) and a newer
 * jar bundles different content, the file is replaced, with a backup. Edited or older unmarked files are left alone.
 */
public final class BundledConfig {
    private BundledConfig() {}

    public static void refresh(Path path, String bundled) throws IOException {
        Path marker = path.resolveSibling(path.getFileName() + ".bundled");
        String bundledHash = hash(bundled);
        if (!Files.exists(path)) {
            Files.createDirectories(path.getParent());
            Files.writeString(path, bundled, StandardCharsets.UTF_8);
            Files.writeString(marker, bundledHash, StandardCharsets.UTF_8);
            return;
        }
        if (!Files.exists(marker)) return;
        String written = Files.readString(marker, StandardCharsets.UTF_8).trim();
        if (written.equals(bundledHash)) return;
        if (!hash(Files.readString(path, StandardCharsets.UTF_8)).equals(written)) return;
        Files.copy(path, path.resolveSibling(path.getFileName() + ".previous"), StandardCopyOption.REPLACE_EXISTING);
        Files.writeString(path, bundled, StandardCharsets.UTF_8);
        Files.writeString(marker, bundledHash, StandardCharsets.UTF_8);
        Craftbound.LOGGER.info("Updated {} to the version bundled with this Craftbound jar (old copy saved as .previous).", path.getFileName());
    }

    static String hash(String text) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
