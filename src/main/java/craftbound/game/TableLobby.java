package craftbound.game;

import craftbound.block.ModBlocks;
import craftbound.entity.CardMasterEntity;
import craftbound.network.TableActionPayload;
import craftbound.network.TableLobbyPayload;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

// Getting a game going at a card table, standing up:
// 1. Right-click the table: the lobby opens on your screen. Pick a Deck Case and an opponent, then Ready.
// 2. Card Master: one appears nearby and walks to the far side of the table. The match starts when it gets there.
//    Another player: you wait at the table until someone else readies up there for a player too.
// Walking away from the table, or right-clicking it again, stops the wait.
public class TableLobby {
    // How close you have to be to the table to press Ready, and how far you can wander while waiting.
    private static final double READY_RANGE = 6.0;
    private static final double WAIT_RANGE = 10.0;

    private record Waiting(ResourceKey<Level> level, BlockPos table, int slot, int mode, CardMasterEntity cardMaster) {
    }

    private static final Map<UUID, Waiting> WAITING = new HashMap<>();
    private static int ticks;

    static void initialize() {
        PayloadTypeRegistry.clientboundPlay().register(TableLobbyPayload.TYPE, TableLobbyPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(TableActionPayload.TYPE, TableActionPayload.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(TableActionPayload.TYPE, (payload, context) -> handle(context.player(), payload));
        ServerTickEvents.END_SERVER_TICK.register(TableLobby::tick);
        ServerPlayConnectionEvents.DISCONNECT.register((listener, server) -> stopWaiting(listener.player, false));
    }

    // Right-clicking a card table.
    public static void open(ServerPlayer player, BlockPos table) {
        if (MatchManager.inMatch(player)) {
            MatchManager.resend(player);
            return;
        }
        if (WAITING.containsKey(player.getUUID())) {
            stopWaiting(player, true);
        }
        ServerPlayNetworking.send(player, new TableLobbyPayload(table, waitingAt(player, table), "", false));
    }

    // The name of someone else waiting at this table for a player to join, or "".
    private static String waitingAt(ServerPlayer viewer, BlockPos table) {
        MinecraftServer server = viewer.level().getServer();
        for (Map.Entry<UUID, Waiting> entry : WAITING.entrySet()) {
            Waiting waiting = entry.getValue();
            if (waiting.mode() == TableActionPayload.PLAYER && waiting.table().equals(table)
                    && waiting.level().equals(viewer.level().dimension()) && !entry.getKey().equals(viewer.getUUID())) {
                ServerPlayer other = server.getPlayerList().getPlayer(entry.getKey());
                if (other != null) {
                    return other.getName().getString();
                }
            }
        }
        return "";
    }

    private static void handle(ServerPlayer player, TableActionPayload payload) {
        if (payload.action() == TableActionPayload.LEAVE) {
            stopWaiting(player, true);
            return;
        }
        if (MatchManager.inMatch(player) || WAITING.containsKey(player.getUUID())) {
            return;
        }
        BlockPos table = payload.table();
        if (!player.level().getBlockState(table).is(ModBlocks.CARD_TABLE)
                || player.distanceToSqr(table.getX() + 0.5, table.getY() + 0.5, table.getZ() + 0.5) > READY_RANGE * READY_RANGE) {
            error(player, table, "match_ui.craftbound.too_far");
            return;
        }
        String problem = MatchManager.whyCantPlay(player, payload.slot());
        if (problem != null) {
            error(player, table, problem);
            return;
        }
        if (payload.action() == TableActionPayload.PLAYER) {
            // Someone already waiting here? Then that's the match.
            for (Map.Entry<UUID, Waiting> entry : new ArrayList<>(WAITING.entrySet())) {
                Waiting waiting = entry.getValue();
                if (waiting.mode() != TableActionPayload.PLAYER || !waiting.table().equals(table)
                        || !waiting.level().equals(player.level().dimension())) {
                    continue;
                }
                ServerPlayer other = player.level().getServer().getPlayerList().getPlayer(entry.getKey());
                if (other == null || MatchManager.inMatch(other)) {
                    continue;
                }
                WAITING.remove(entry.getKey());
                String otherProblem = MatchManager.whyCantPlay(other, waiting.slot());
                if (otherProblem != null) {
                    other.sendOverlayMessage(Component.translatable(otherProblem, MatchManager.PRACTICE_MIN_DECK));
                    continue;
                }
                close(player, table);
                MatchManager.startVersus(other, MatchManager.deckIn(other, waiting.slot()), player, MatchManager.deckIn(player, payload.slot()));
                return;
            }
            WAITING.put(player.getUUID(), new Waiting(player.level().dimension(), table, payload.slot(), TableActionPayload.PLAYER, null));
            close(player, table);
            player.sendOverlayMessage(Component.translatable("match_ui.craftbound.waiting_player"));
        } else {
            CardMasterEntity cardMaster = CardMasterEntity.comeTo((ServerLevel) player.level(), table, player);
            WAITING.put(player.getUUID(), new Waiting(player.level().dimension(), table, payload.slot(), TableActionPayload.CARD_MASTER, cardMaster));
            close(player, table);
            player.sendOverlayMessage(Component.translatable("match_ui.craftbound.waiting_card_master"));
        }
    }

    private static void tick(MinecraftServer server) {
        ticks++;
        for (Map.Entry<UUID, Waiting> entry : new ArrayList<>(WAITING.entrySet())) {
            Waiting waiting = entry.getValue();
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (player == null) {
                WAITING.remove(entry.getKey());
                if (waiting.cardMaster() != null) {
                    waiting.cardMaster().leave(false);
                }
                continue;
            }
            BlockPos table = waiting.table();
            boolean wandered = !player.level().dimension().equals(waiting.level())
                    || player.distanceToSqr(table.getX() + 0.5, table.getY() + 0.5, table.getZ() + 0.5) > WAIT_RANGE * WAIT_RANGE;
            if (wandered) {
                stopWaiting(player, false);
                continue;
            }
            if (waiting.mode() == TableActionPayload.CARD_MASTER) {
                CardMasterEntity cardMaster = waiting.cardMaster();
                if (cardMaster == null || cardMaster.isRemoved() || cardMaster.hasArrived()) {
                    WAITING.remove(entry.getKey());
                    String problem = MatchManager.whyCantPlay(player, waiting.slot());
                    if (problem != null) {
                        player.sendOverlayMessage(Component.translatable(problem, MatchManager.PRACTICE_MIN_DECK));
                        if (cardMaster != null) {
                            cardMaster.leave(false);
                        }
                        continue;
                    }
                    MatchManager.startVsCardMaster(player, MatchManager.deckIn(player, waiting.slot()),
                            cardMaster == null || cardMaster.isRemoved() ? null : cardMaster);
                } else if (ticks % 40 == 0) {
                    player.sendOverlayMessage(Component.translatable("match_ui.craftbound.waiting_card_master"));
                }
            } else if (ticks % 40 == 0) {
                player.sendOverlayMessage(Component.translatable("match_ui.craftbound.waiting_player"));
            }
        }
    }

    private static void stopWaiting(ServerPlayer player, boolean quiet) {
        Waiting waiting = WAITING.remove(player.getUUID());
        if (waiting == null) {
            return;
        }
        if (waiting.cardMaster() != null && !waiting.cardMaster().isRemoved()) {
            waiting.cardMaster().leave(false);
        }
        if (!quiet) {
            player.sendOverlayMessage(Component.translatable("match_ui.craftbound.stopped_waiting"));
        }
    }

    private static void error(ServerPlayer player, BlockPos table, String key) {
        ServerPlayNetworking.send(player, new TableLobbyPayload(table, waitingAt(player, table), key, false));
    }

    private static void close(ServerPlayer player, BlockPos table) {
        ServerPlayNetworking.send(player, new TableLobbyPayload(table, "", "", true));
    }

    private TableLobby() {
    }
}
