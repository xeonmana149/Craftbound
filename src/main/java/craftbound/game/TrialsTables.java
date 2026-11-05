package craftbound.game;

import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import craftbound.Craftbound;
import craftbound.block.CardTableBlockEntity;
import craftbound.block.ModBlocks;
import craftbound.entity.CardMasterEntity;
import craftbound.item.ModItems;
import craftbound.network.TrialsActionPayload;
import craftbound.network.TrialsStatePayload;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.*;
import net.fabricmc.fabric.api.networking.v1.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Prediction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/** One private journal per player, saved with their world data. Borrowed cards never enter the binder. */
public final class TrialsTables {
    private static final AttachmentType<String> JOURNAL = AttachmentRegistry.create(Craftbound.id("trials_journal"),
            builder -> builder.persistent(Codec.STRING).copyOnDeath());

    public static void initialize() {
        PayloadTypeRegistry.serverboundPlay().register(TrialsActionPayload.TYPE, TrialsActionPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(TrialsStatePayload.TYPE, TrialsStatePayload.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(TrialsActionPayload.TYPE, (action, context) -> handle(context.player(), action));
    }

    private static TrialsRun load(ServerPlayer player) {
        String saved = ((AttachmentTarget)player).getAttached(JOURNAL);
        if (saved == null) return null;
        var run=TrialsRun.JSON.fromJson(saved, TrialsRun.class);
        if(run.migrate())save(player,run);
        return run;
    }

    private static void save(ServerPlayer player, TrialsRun run) {
        ((AttachmentTarget)player).setAttached(JOURNAL, TrialsRun.JSON.toJson(run));
    }

    static boolean ownsCheckpoint(ServerPlayer player, String key) {
        var run = load(player);
        return run != null && run.checkpointDimension != null && run.battleKey().equals(key);
    }

    private static void send(ServerPlayer player, BlockPos table, TrialsRun run, String message, boolean result) {
        var view = run.view(message);
        if (result) view.addProperty("afterBattle", true);
        ServerPlayNetworking.send(player, new TrialsStatePayload(table, view.toString()));
    }

    private static void notice(ServerPlayer player, String message) {
        player.sendOverlayMessage(Component.literal(message));
    }

    private static void handle(ServerPlayer player, TrialsActionPayload action) {
        ServerLevel level = (ServerLevel)player.level();
        BlockPos table = action.table();
        if (!player.isAlive() || player.distanceToSqr(Vec3.atCenterOf(table)) > 36
                || !level.getBlockState(table).is(ModBlocks.CARD_TABLE)) {
            notice(player, "Stand beside a card table to open your Trials journal.");
            return;
        }
        if (MatchManager.inMatch(player)) return;
        boolean open = action.action().equals("open");
        TrialsRun run = load(player);
        if (run == null) {
            if (!open) return;
            run = TrialsRun.create(player.getRandom().nextLong());
            save(player, run);
        }

        if (run.checkpointDimension != null) {
            if (hasSavedEncounter(level.getServer(), run)) {
                var pos = BlockPos.of(run.checkpointPos);
                send(player, table, run, "Resume at " + pos.toShortString() + " in " + run.checkpointDimension + ".", false);
                return;
            }
            // An active fight interrupted before it could checkpoint (or a broken paused table) can be retried.
            run.finish(run.battleKey(), null);
            save(player, run);
        }
        if (run.reconcile()) save(player, run);
        if (open || !run.id.equals(action.runId()) || run.revision != action.revision()) {
            send(player, table, run, "", false);
            return;
        }

        switch (action.action()) {
            case "pick" -> {
                if (run.pick(action.index())) save(player, run);
            }
            case "leave" -> {
                if (run.leaveChoice()) save(player, run);
            }
            case "new" -> {
                if (run.terminal()) {
                    run = TrialsRun.create(player.getRandom().nextLong());
                    save(player, run);
                }
            }
            case "retire" -> {
                if (run.retire()) save(player, run);
            }
            case "battle" -> {
                if (run.phase == TrialsRun.Phase.READY) {
                    if (TableLobby.waiting(player, table) || MatchManager.tableInUse(level, table)) {
                        send(player, table, run, "This table is busy. Choose a free table to begin the encounter.", false);
                        return;
                    }
                    if (!TableSeats.canJoinFromVehicle(player, table) || TableSeats.plan(level, table, player) == null) {
                        send(player, table, run, "Leave space for both chairs and stand beside the table.", false);
                        return;
                    }
                    Match match = run.createBattle(player.getName().getString(), player.getUUID(), player.getRandom());
                    var master = CardMasterEntity.comeTo(level, table, player);
                    if (master != null) master.setCustomName(Component.literal(run.encounter().name()));
                    run.startBattle(level.dimension().identifier().toString(), table.asLong());
                    match.trialsBattle = run.battleKey();
                    if (master != null && MatchManager.startAiMatch(player, table, master, match)) {
                        save(player, run);
                        return;
                    }
                    run.finish(match.trialsBattle, null);
                    save(player, run);
                    send(player, table, run, "The challenger needs a free chair across from you.", false);
                    return;
                }
            }
            default -> { return; }
        }
        send(player, table, run, "", false);
    }

    private static boolean hasSavedEncounter(MinecraftServer server, TrialsRun run) {
        Identifier dimension = Identifier.tryParse(run.checkpointDimension);
        if (dimension == null) return false;
        var level = server.getLevel(ResourceKey.create(Registries.DIMENSION, dimension));
        if (level == null) return false;
        var pos = BlockPos.of(run.checkpointPos);
        // Visiting a different dimension must not load the old table's chunk.
        if (!level.hasChunkAt(pos)) return true;
        if (!(level.getBlockEntity(pos) instanceof CardTableBlockEntity data) || !data.paused()) return false;
        var snapshot = JsonParser.parseString(data.savedMatch()).getAsJsonObject();
        return snapshot.has("trialsBattle") && run.battleKey().equals(snapshot.get("trialsBattle").getAsString());
    }

    static void finish(MinecraftServer server, Match match) {
        MatchPlayer human = match.player(0).isAi() ? match.player(1) : match.player(0);
        UUID owner = human.playerId;
        var player = server.getPlayerList().getPlayer(owner);
        if (player == null) return;
        var run = load(player);
        if (run == null) return;
        BlockPos table = BlockPos.of(run.checkpointPos);
        Boolean won = match.winner < 0 ? null : Boolean.valueOf(match.winner == match.sideOf(owner));
        if (!run.finish(match.trialsBattle, won, human.health)) return;
        save(player, run);
        String message;
        if (run.phase == TrialsRun.Phase.VICTORY) {
            int packs = run.config("bossPacks",2);
            player.getInventory().placeItemBackInInventory(new ItemStack(ModItems.BOOSTER_PACK, packs), Prediction.SERVER_ONLY);
            TrialsRewards.afterClear(player,run.heroClass);
            message = run.act().name+" cleared! You earned "+packs+" Foundation Booster Pack"+(packs==1?"":"s")+".";
        } else if (Boolean.TRUE.equals(won)) {
            message = "Encounter cleared. Choose your reward or continue along the trail.";
        } else if (run.phase == TrialsRun.Phase.DEFEAT) {
            message = "Your expedition has ended. A fresh draft awaits.";
        } else if (won == null) {
            message = "The encounter was interrupted. You can retry with both your deck and remaining lives.";
        } else {
            message = "You recovered your Health. "+run.lives+" "+(run.lives==1?"life remains":"lives remain")+" for the rematch.";
        }
        player.sendSystemMessage(Component.literal(message));
        send(player, table, run, message, true);
    }

    private TrialsTables() {}
}
