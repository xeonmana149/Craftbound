package craftbound.mastery;

import craftbound.Craftbound;
import craftbound.card.*;
import craftbound.component.ModComponents;
import craftbound.deck.DeckBuilding;
import craftbound.item.CardItem;
import craftbound.item.ModItems;
import net.fabricmc.fabric.api.attachment.v1.*;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Prediction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.resources.Identifier;

public final class ClassMastery {
    public static final AttachmentType<MasteryProgress> PROGRESS = AttachmentRegistry.create(
            Craftbound.id("class_mastery"), b -> b.initializer(() -> MasteryProgress.EMPTY)
                    .persistent(MasteryProgress.CODEC).copyOnDeath()
                    .syncWith(MasteryProgress.STREAM_CODEC, AttachmentSyncPredicate.targetOnly()));
    public static void initialize() {}
    public static MasteryProgress get(Player player) {
        return ((AttachmentTarget) player).getAttachedOrElse(PROGRESS, MasteryProgress.EMPTY);
    }
    public static void award(ServerPlayer player, CardClass cls, boolean won) { award(player, cls, won, false); }

    public static void award(ServerPlayer player, CardClass cls, boolean won, boolean practice) {
        if (cls == CardClass.NEUTRAL) return;
        var before = get(player);
        var after = before.earn(cls, MasteryProgress.matchPoints(won, practice));
        int gained = after.points(cls) - before.points(cls);
        if (gained == 0) return;
        ((AttachmentTarget) player).setAttached(PROGRESS, after);
        player.sendSystemMessage(Component.literal("+" + gained + " ").append(cls.displayName())
                .append(" Mastery — Level " + after.level(cls)).withStyle(ChatFormatting.GOLD));
        if (before.level(cls) < after.level(cls)) {
            for (var board : craftbound.board.BoardDesign.values()) {
                if (!board.unlockedBy(before) && board.unlockedBy(after))
                    player.sendSystemMessage(Component.literal("Board unlocked: " + board.title + ". Equip it in your binder.").withStyle(ChatFormatting.GOLD));
            }
        }
        for (int level = before.level(cls) + 1; level <= after.level(cls); level++) {
            if (level <= 10 && level % 2 == 0) rewardCommon(player, cls);
        }
    }

    // Stable collector order; top up a carried Common playset instead of adding duplicate copies.
    private static void rewardCommon(ServerPlayer player, CardClass cls) {
        var inventory = player.getInventory();
        for (Identifier id : Cards.collectible()) {
            var card = Cards.get(id);
            if (card.cardClass() != cls || card.rarity() != CardRarity.COMMON) continue;
            int owned = DeckBuilding.owned(inventory, id, false) + DeckBuilding.owned(inventory, id, true);
            for (int i = 0; i < inventory.getContainerSize(); i++) {
                var stack = inventory.getItem(i);
                if (stack.is(ModItems.CARD) && id.equals(stack.get(ModComponents.CARD_ID))) owned += stack.getCount();
            }
            if (owned >= 2) continue;
            var reward = CardItem.of(id);
            reward.setCount(2 - owned);
            inventory.placeItemBackInInventory(reward, Prediction.SERVER_ONLY);
            player.sendSystemMessage(Component.literal("Mastery reward: ").append(Cards.name(id))
                    .append(" ×" + (2 - owned)).withStyle(ChatFormatting.GREEN));
            return;
        }
        player.sendSystemMessage(Component.literal("You already carry every available Common playset for this class.")
                .withStyle(ChatFormatting.GRAY));
    }
    private ClassMastery() {}
}
