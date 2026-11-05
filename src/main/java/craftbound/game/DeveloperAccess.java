package craftbound.game;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import craftbound.Craftbound;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.level.ServerPlayer;

/** The server grants developer UI access by account UUID, never by creative mode or OP status. */
public final class DeveloperAccess {
    public static final UUID OWNER = UUID.fromString("4269df07-431c-4b6e-a5d4-d1b143c91872");
    private static Set<UUID> allowed = Set.of();

    public static void initialize() {
        ServerLifecycleEvents.SERVER_STARTING.register(server -> {
            Path file=FabricLoader.getInstance().getConfigDir().resolve("craftbound/developers.json");
            try { allowed=load(file); }
            catch (IOException | RuntimeException ex) {
                allowed=Set.of();
                Craftbound.LOGGER.error("Cannot read developer allowlist {}; developer tools disabled.",file,ex);
            }
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> allowed=Set.of());
    }

    static Set<UUID> load(Path file) throws IOException {
        if(Files.notExists(file)) {
            Files.createDirectories(file.getParent());
            JsonObject defaults=new JsonObject();
            defaults.addProperty("note","Developer layout previews. ZeElectron is the project owner. Use account UUIDs; restart the server after editing.");
            JsonArray ids=new JsonArray();ids.add(OWNER.toString());defaults.add("allowedUuids",ids);
            Files.writeString(file,new GsonBuilder().setPrettyPrinting().create().toJson(defaults)+"\n",StandardOpenOption.CREATE_NEW);
        }
        JsonObject root=JsonParser.parseString(Files.readString(file)).getAsJsonObject();
        JsonArray ids=root.getAsJsonArray("allowedUuids");
        if(ids==null)throw new IllegalArgumentException("Missing allowedUuids");
        Set<UUID> result=new HashSet<>();
        for(var value:ids) {
            String text=value.getAsString();
            UUID uuid=UUID.fromString(text);
            if(!uuid.toString().equalsIgnoreCase(text))throw new IllegalArgumentException("Use complete account UUIDs");
            result.add(uuid);
        }
        return Set.copyOf(result);
    }

    public static boolean allowed(ServerPlayer player) {
        var server=player.level().getServer();
        boolean developmentHost=FabricLoader.getInstance().isDevelopmentEnvironment()
                && server!=null && server.isSingleplayer()
                && server.getSingleplayerProfile()!=null
                && server.getSingleplayerProfile().id().equals(player.getUUID());
        return permits(player.getUUID(),developmentHost,allowed);
    }

    static boolean permits(UUID account,boolean developmentHost,Set<UUID> ids) {
        return developmentHost || ids.contains(account);
    }

    private DeveloperAccess(){}
}
