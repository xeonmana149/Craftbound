package craftbound.game;

import com.google.gson.*;
import craftbound.Craftbound;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

/** Server-owned Trials rules. Validate the entire book before publishing any part of it. */
public final class TrialsContent {
    public static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final List<String> SECTIONS = List.of("config","act","events","rest","boons","shop","opponents","charms","rewards");
    private record Book(JsonObject root,List<TrialsOpponents.Profile> opponents,List<TrialCharmSpec> charms,List<TrialsRewards.Reward> rewards) {}
    private static volatile Book current;
    private static Book book() {
        var book = current;
        if (book != null) return book;
        synchronized (TrialsContent.class) {
            if (current == null) current = parse(defaults());
            return current;
        }
    }
    public static JsonElement section(String name) { return book().root.get(name); }
    public static List<TrialsOpponents.Profile> opponents() { return book().opponents; }
    public static List<TrialCharmSpec> charms() { return book().charms; }
    public static List<TrialsRewards.Reward> rewards() { return book().rewards; }
    static JsonObject defaults() {
        var root = new JsonObject();
        root.addProperty("version",1);
        for (String key : SECTIONS) {
            String file = key.equals("opponents") ? "/craftbound/trials-opponents.json" : "/craftbound/trials/"+key+".json";
            try (var stream = TrialsContent.class.getResourceAsStream(file)) {
                if (stream == null) throw new IllegalStateException("Missing " + file);
                root.add(key,JsonParser.parseReader(new InputStreamReader(stream,StandardCharsets.UTF_8)));
            } catch (IOException e) { throw new UncheckedIOException(e); }
        }
        return root;
    }
    private static Book parse(JsonObject root) {
        if (!root.has("version") || root.get("version").getAsInt()!=1) throw new IllegalArgumentException("Unsupported Trials version");
        for (String key:SECTIONS) if (!root.has(key)) throw new IllegalArgumentException("Missing Trials section: " + key);
        List<String> problems = new ArrayList<>();
        TrialsRun.validateContent(root,problems);
        if (!problems.isEmpty()) throw new IllegalArgumentException(String.join("; ",problems));
        var opponents = TrialsOpponents.parse(root.getAsJsonArray("opponents"));
        var charms = TrialCharmSpec.parse(root.getAsJsonArray("charms"));
        var rewards = TrialsRewards.parse(root.getAsJsonArray("rewards"));
        return new Book(root,opponents,charms,rewards);
    }
    private static Path path() { return FabricLoader.getInstance().getConfigDir().resolve("craftbound/trials.json"); }
    static void loadLocal() throws IOException {
        var path = path();
        if (!Files.exists(path)) {
            Files.createDirectories(path.getParent());
            Files.writeString(path,GSON.toJson(defaults())+"\n",StandardOpenOption.CREATE_NEW);
        }
        if (Files.size(path)>1_000_000) throw new IOException("trials.json exceeds 1 MB");
        var root = JsonParser.parseString(Files.readString(path)).getAsJsonObject();
        boolean upgraded = upgradeDefaultAct(root);
        upgraded |= TrialsText.migrateCurrency(root);
        var validated = parse(root);
        if (upgraded) {
            // Preserve the original settings before replacing only the untouched bundled route.
            var backup = path.resolveSibling("trials.pre-branching-v2-backup.json");
            var staged = Files.createTempFile(path.getParent(), "trials-route-", ".json");
            try {
                if (!Files.exists(backup)) Files.copy(path, backup);
                Files.writeString(staged, GSON.toJson(root) + "\n");
                try { Files.move(staged, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
                catch (AtomicMoveNotSupportedException e) { Files.move(staged, path, StandardCopyOption.REPLACE_EXISTING); }
                Craftbound.LOGGER.info("Updated Trials settings. Saved expeditions keep their original paths.");
            } finally { Files.deleteIfExists(staged); }
        }
        current = validated;
    }
    /** Upgrade only the exact old bundled act; authored routes and saved run snapshots stay intact. */
    static boolean upgradeDefaultAct(JsonObject root) {
        var legacy = new JsonObject();
        legacy.addProperty("name", "The Copper Depths");
        var floors = new JsonArray();
        for (int row = 0; row < 13; row++) {
            var floor = new JsonObject();
            String[] kinds = row == 12 ? new String[]{"BOSS"}
                : row % 2 == 1 ? new String[]{"SHOP", "REST", "EVENT"}
                : row == 0 ? new String[]{"FIGHT", "FIGHT", "FIGHT"}
                : new String[]{"FIGHT", "FIGHT", "ELITE"};
            var entries = new JsonArray();
            for (var kind : kinds) entries.add(kind);
            floor.add("kinds", entries);
            floor.addProperty("shuffle", row != 12);
            floor.add("edges", JsonParser.parseString(row == 12 ? "[[]]" : row == 11
                ? "[[0],[0],[0]]" : "[[0,1],[0,1,2],[1,2]]"));
            floors.add(floor);
        }
        legacy.add("floors", floors);
        boolean matches = legacy.equals(root.get("act"));
        legacy.addProperty("topology", TrialsAct.BRANCHING);
        if (!matches && !legacy.equals(root.get("act"))) return false;
        root.add("act", defaults().getAsJsonObject("act").deepCopy());
        return true;
    }

    public static void initialize() {
        ServerLifecycleEvents.SERVER_STARTING.register(server -> {
            current = parse(defaults());
            try { loadLocal(); }
            catch (Exception e) { Craftbound.LOGGER.error("Trials config rejected. Using bundled Trials rules.",e); }
        });
        CommandRegistrationCallback.EVENT.register((dispatcher,registry,environment) ->
            dispatcher.register(Commands.literal("craftbound").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.literal("trials").then(Commands.literal("reload").executes(ctx -> {
                    if (MatchManager.hasActiveMatches()) { ctx.getSource().sendFailure(Component.literal("Finish or pause matches before reloading Trials.")); return 0; }
                    try { loadLocal(); ctx.getSource().sendSuccess(()->Component.literal("Loaded trials.json. New expeditions use the updated route."),true);return 1; }
                    catch(Exception e) { ctx.getSource().sendFailure(Component.literal("Trials unchanged: "+e.getMessage()));return 0; }
                })))));
    }
    private TrialsContent() {}
}
