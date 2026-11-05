package craftbound.board;

import com.mojang.serialization.Codec;
import craftbound.Craftbound;
import craftbound.mastery.ClassMastery;
import net.fabricmc.fabric.api.attachment.v1.*;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.world.entity.player.Player;
import net.minecraft.server.level.ServerPlayer;

public final class BoardCollection {
    private static final AttachmentType<String> SELECTED = AttachmentRegistry.create(
            Craftbound.id("selected_board"), b -> b.initializer(BoardDesign.CLASSIC::id)
                    .persistent(Codec.STRING).copyOnDeath()
                    .syncWith(ByteBufCodecs.STRING_UTF8, AttachmentSyncPredicate.targetOnly()));
    public static void initialize() {}

    public static boolean owns(Player player, BoardDesign design) {
        return design.unlockedBy(ClassMastery.get(player)) || craftbound.game.TrialsRewards.ownsBoard(player, design);
    }

    public static BoardDesign selected(Player player) {
        return BoardDesign.fromId(((AttachmentTarget) player).getAttachedOrElse(SELECTED, BoardDesign.CLASSIC.id()));
    }

    public static void select(ServerPlayer player, String id) {
        BoardDesign design = BoardDesign.fromId(id);
        if (design.id().equals(id) && owns(player, design))
            ((AttachmentTarget) player).setAttached(SELECTED, design.id());
    }

    private BoardCollection() {}
}
