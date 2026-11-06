package craftbound.card;

import craftbound.Craftbound;
import craftbound.binder.BinderContents;
import craftbound.component.ModComponents;
import craftbound.deck.DeckContents;
import craftbound.game.MatchManager;
import craftbound.item.CardItem;
import craftbound.item.ModItems;
import craftbound.network.CardCatalogPayload;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.io.IOException;
import java.util.*;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.*;
import net.minecraft.commands.Commands;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.util.Prediction;

public final class CardCatalogService {
    public static Path path() { return FabricLoader.getInstance().getConfigDir().resolve("craftbound/cards.json"); }

    public static void loadLocal() throws IOException {
        Path path = path();
        // Writes the bundled cards if missing, and updates an untouched copy when a newer jar bundles new cards.
        craftbound.BundledConfig.refresh(path, new com.google.gson.GsonBuilder().setPrettyPrinting().create()
                .toJson(com.google.gson.JsonParser.parseString(CardCatalog.builtin().json())));
        if (Files.size(path) > 1_000_000) throw new IOException("cards.json is too large");
        var next = CardCatalog.parse(Files.readString(path, StandardCharsets.UTF_8));
        CardCatalog.validateReplacement(CardCatalog.builtin(), next);
        CardCatalog.install(next);
    }

    public static void initialize() {
        try { loadLocal(); } catch (Exception e) { Craftbound.LOGGER.error("Card catalogue rejected; bundled defaults remain active", e); }
        PayloadTypeRegistry.clientboundPlay().register(CardCatalogPayload.TYPE, CardCatalogPayload.CODEC);
        ServerLifecycleEvents.SERVER_STARTING.register(server -> {
            CardCatalog.install(CardCatalog.builtin());
            try { loadLocal(); } catch (Exception e) { Craftbound.LOGGER.error("Card catalogue rejected; using bundled defaults", e); }
        });
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> sync(handler.player));
        CommandRegistrationCallback.EVENT.register((dispatcher, registry, environment) ->
            dispatcher.register(Commands.literal("craftbound")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.literal("cards")
                    .then(Commands.literal("reload").executes(ctx -> reload(ctx.getSource())))
                    .then(Commands.literal("path").executes(ctx -> {
                        ctx.getSource().sendSuccess(() -> Component.literal(path().toAbsolutePath().toString()), false); return 1;
                    }))
                    .then(Commands.literal("give").then(Commands.argument("id", StringArgumentType.word())
                        .suggests((ctx, builder) -> {
                            Cards.ids().stream().map(Object::toString).filter(s -> s.startsWith(builder.getRemaining())).forEach(builder::suggest);
                            return builder.buildFuture();
                        }).executes(ctx -> {
                            Identifier id = Identifier.tryParse(StringArgumentType.getString(ctx, "id"));
                            if (id == null || Cards.get(id) == null) { ctx.getSource().sendFailure(Component.literal("Unknown card ID")); return 0; }
                            ctx.getSource().getPlayerOrException().getInventory().placeItemBackInInventory(CardItem.of(id, false), Prediction.SERVER_ONLY);
                            return 1;
                        })))
                    .then(Commands.literal("testdeck").then(Commands.argument("class", StringArgumentType.word())
                        .suggests((ctx, builder) -> {
                            for (CardClass c : CardClass.values()) if (c != CardClass.NEUTRAL && c.name().toLowerCase(Locale.ROOT).startsWith(builder.getRemaining())) builder.suggest(c.name().toLowerCase(Locale.ROOT));
                            return builder.buildFuture();
                        }).executes(ctx -> {
                            CardClass kind;
                            try { kind = CardClass.valueOf(StringArgumentType.getString(ctx, "class").toUpperCase(Locale.ROOT)); }
                            catch (IllegalArgumentException e) { ctx.getSource().sendFailure(Component.literal("Unknown class")); return 0; }
                            if (kind == CardClass.NEUTRAL) { ctx.getSource().sendFailure(Component.literal("Choose a playable class")); return 0; }
                            return testDeck(ctx.getSource().getPlayerOrException(), kind);
                        }))))));
    }
    private static void sync(ServerPlayer player) { ServerPlayNetworking.send(player, new CardCatalogPayload(CardCatalog.current().json())); }

    private static int reload(CommandSourceStack source) {
        if (MatchManager.hasActiveMatches()) { source.sendFailure(Component.literal("Finish all Craftbound matches before reloading cards.")); return 0; }
        try {
            Path path = path();
            if (Files.size(path) > 1_000_000) throw new IOException("cards.json is too large");
            var next = CardCatalog.parse(Files.readString(path, StandardCharsets.UTF_8));
            CardCatalog.validateReplacement(CardCatalog.current(), next);
            CardCatalog.validateReplacement(CardCatalog.builtin(), next);
            CardCatalog.install(next);
            for (ServerPlayer player : source.getServer().getPlayerList().getPlayers()) sync(player);
            source.sendSuccess(() -> Component.literal("Loaded " + Cards.collectible().size() + " collectible cards. New matches use these rules."), true);
            return 1;
        } catch (Exception e) {
            source.sendFailure(Component.literal("Cards unchanged: " + e.getMessage()));
            return 0;
        }
    }

    private static int testDeck(ServerPlayer player, CardClass kind) {
        List<Identifier> pool = new ArrayList<>(Cards.collectible().stream().filter(id -> DeckRules.allowed(id, kind)).toList());
        pool.sort(Comparator.<Identifier>comparingInt(id -> Cards.get(id).cardClass() == kind ? 0 : 1)
                .thenComparingInt(id -> Cards.get(id).cost()));
        BinderContents contents = BinderContents.EMPTY;
        for (Identifier id : pool) {
            int n = Math.min(30 - contents.total(), DeckRules.maxCopies(Cards.get(id).rarity()));
            if (n > 0) contents = contents.add(id, false, n);
        }
        if (contents.total() != 30) { player.sendSystemMessage(Component.literal("This class does not have enough legal cards for a 30-card deck.")); return 0; }
        ItemStack binder = new ItemStack(ModItems.BINDER);
        binder.set(ModComponents.BINDER_CONTENTS, contents);
        var entry = new craftbound.deck.SavedDecks.Entry(java.util.UUID.randomUUID().toString(), "",
                new DeckContents(kind, contents, true), craftbound.sleeve.Sleeves.CLASSIC);
        binder.set(ModComponents.SAVED_DECKS, craftbound.deck.SavedDecks.EMPTY.put(entry));
        player.getInventory().placeItemBackInInventory(binder, Prediction.SERVER_ONLY);
        player.sendSystemMessage(Component.literal("Added a test binder containing its saved 30-card deck. Keep the binder in your inventory."));
        return 1;
    }
    private CardCatalogService() {}
}
