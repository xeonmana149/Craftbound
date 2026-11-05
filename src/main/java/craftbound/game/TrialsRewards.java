package craftbound.game;

import com.google.gson.*;
import com.mojang.serialization.Codec;
import craftbound.Craftbound;
import craftbound.board.BoardDesign;
import craftbound.card.CardClass;
import craftbound.sleeve.*;
import java.util.*;
import net.fabricmc.fabric.api.attachment.v1.*;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

public final class TrialsRewards {
    // Stable entries keep earlier clears and awarded boards even after a Workshop edit.
    private static final class Attachments {
        private static final AttachmentType<List<String>> AWARDS = AttachmentRegistry.create(Craftbound.id("trial_awards"),
            b -> b.initializer(List::of).persistent(Codec.STRING.listOf()).copyOnDeath()
                    .syncWith(ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), AttachmentSyncPredicate.targetOnly()));
    }
    public static void initialize() { java.util.Objects.requireNonNull(Attachments.AWARDS); }
    public record Reward(CardClass heroClass, String cardBack, String board) {}
    static List<Reward> parse(JsonArray entries) {
        var used = EnumSet.noneOf(CardClass.class);
        List<Reward> result = new ArrayList<>();
        for (var entry : entries) {
            Reward r = TrialsContent.GSON.fromJson(entry, Reward.class);
            if (r == null || r.heroClass == null || r.heroClass == CardClass.NEUTRAL || !used.add(r.heroClass))
                throw new IllegalArgumentException("Each first-clear reward needs a different playable class");
            if (r.board == null || !r.board.isEmpty() && !BoardDesign.fromId(r.board).id().equals(r.board))
                throw new IllegalArgumentException("Unknown reward board for " + r.heroClass);
            if (r.cardBack == null || !r.cardBack.isEmpty() && !Sleeves.ids().contains(Identifier.tryParse(r.cardBack)))
                throw new IllegalArgumentException("Unknown reward card back for " + r.heroClass);
            result.add(r);
        }
        return List.copyOf(result);
    }
    private static List<String> awards(Player p) {
        return ((AttachmentTarget)p).getAttachedOrElse(Attachments.AWARDS, List.of());
    }
    public static boolean cleared(Player p, CardClass cls) { return p != null && awards(p).contains("clear/" + cls.name()); }
    public static boolean ownsBoard(Player p, BoardDesign board) { return awards(p).contains("board/" + board.id()); }
    public static void afterClear(ServerPlayer player, CardClass cls) {
        if (cls == CardClass.NEUTRAL || cleared(player,cls)) return;
        List<String> updated = new ArrayList<>(awards(player));
        updated.add("clear/" + cls.name());
        var message = Component.literal("First Trials clear as ").append(cls.displayName()).append("!");
        for (Reward r : TrialsContent.rewards()) if (r.heroClass == cls) {
            if (!r.cardBack.isEmpty()) {
                var back = Identifier.parse(r.cardBack);
                if (SleeveCollection.unlock(player, back)) message.append(" Card back: ").append(Sleeves.name(back)).append(".");
            }
            if (!r.board.isEmpty() && !updated.contains("board/" + r.board)) {
                updated.add("board/" + r.board);
                message.append(" Board: " + BoardDesign.fromId(r.board).title + ".");
            }
        }
        ((AttachmentTarget)player).setAttached(Attachments.AWARDS,List.copyOf(updated));
        player.sendSystemMessage(message);
    }
    private TrialsRewards() {}
}
