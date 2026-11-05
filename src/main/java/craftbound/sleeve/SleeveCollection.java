package craftbound.sleeve;

import craftbound.Craftbound;
import craftbound.component.ModComponents;
import craftbound.item.ModItems;
import craftbound.item.SleeveItem;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentTarget;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Prediction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

// A player's permanent card-back collection: saved with the player, retained on death, and synced to its owner.
public final class SleeveCollection {
    public static final AttachmentType<SleeveUnlocks> UNLOCKED = AttachmentRegistry.create(
            Craftbound.id("unlocked_sleeves"), builder -> builder
                    .initializer(() -> SleeveUnlocks.EMPTY)
                    .persistent(SleeveUnlocks.CODEC)
                    .copyOnDeath()
                    .syncWith(SleeveUnlocks.STREAM_CODEC,
                            AttachmentSyncPredicate.targetOnly()));

    public static void initialize() {
        // Preserve designs already fitted to deck cases under the old physical-sleeve system.
        ServerPlayConnectionEvents.JOIN.register((listener, sender, server) -> unlockEquipped(listener.player));
    }

    public static boolean owns(Player player, Identifier sleeve) {
        return ((AttachmentTarget) player).getAttachedOrElse(UNLOCKED, SleeveUnlocks.EMPTY).owns(sleeve);
    }

    public static boolean unlock(ServerPlayer player, Identifier sleeve) {
        if (!Sleeves.ids().contains(sleeve) || owns(player, sleeve)) return false;
        AttachmentTarget target = (AttachmentTarget) player;
        target.setAttached(UNLOCKED, target.getAttachedOrElse(UNLOCKED, SleeveUnlocks.EMPTY).with(sleeve));
        return true;
    }

    public static void unlockEquipped(ServerPlayer player) {
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.is(ModItems.BINDER)) {
                for (var entry : craftbound.deck.BinderDecks.get(stack).entries()) unlock(player, entry.sleeve());
            }
        }
    }

    public static void awardVictory(ServerPlayer player) {
        unlockEquipped(player);
        SleeveUnlocks unlocked = ((AttachmentTarget) player).getAttachedOrElse(UNLOCKED, SleeveUnlocks.EMPTY);
        Set<Identifier> owned = new HashSet<>();
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            Identifier sleeve = stack.get(ModComponents.SLEEVE_ID);
            if (stack.is(ModItems.SLEEVE) && sleeve != null) owned.add(sleeve);
        }
        List<Identifier> options = unlocked.rewardOptions(owned);
        if (options.isEmpty()) {
            player.sendSystemMessage(Component.translatable("sleeve_ui.craftbound.all_collected").withStyle(ChatFormatting.GOLD));
            return;
        }
        Identifier reward = options.get(player.getRandom().nextInt(options.size()));
        // The standard inventory helper drops it at their feet if the inventory is full.
        player.getInventory().placeItemBackInInventory(SleeveItem.of(reward), Prediction.SERVER_ONLY);
        player.sendSystemMessage(Component.translatable("sleeve_ui.craftbound.reward", Sleeves.name(reward)).withStyle(ChatFormatting.GOLD));
    }

    private SleeveCollection() {
    }
}
