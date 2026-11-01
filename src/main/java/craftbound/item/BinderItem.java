package craftbound.item;

import craftbound.ModAdvancements;
import craftbound.binder.BinderContents;
import craftbound.binder.Disenchanting;
import craftbound.card.CardDefinition;
import craftbound.card.Cards;
import craftbound.component.ModComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

// One binder holds the whole collection, with a numbered pocket for every card.
// File cards by clicking them onto the binder in your inventory, like a bundle. Right-click it to open the pages.
// It keeps 2 normal and 2 Enchanted copies of each card (1 of each for Legendaries); extra copies are
// disenchanted into experience as they're filed.
public class BinderItem extends Item {
    public BinderItem(Properties properties) {
        super(properties);
    }

    // Holding cards on the cursor and clicking them onto the binder files them.
    @Override
    public boolean overrideOtherStackedOnMe(ItemStack binder, ItemStack other, Slot slot, ClickAction action,
                                            Player player, SlotAccess carried) {
        if (action != ClickAction.PRIMARY || !canFile(other)) {
            return false;
        }
        Disenchanting.Result result = file(binder, other);
        player.playSound(SoundEvents.BUNDLE_INSERT, 0.8F, 1.0F);
        afterFiling(player, binder, result.disenchanted(), result.xp());
        return true;
    }

    // Holding the binder on the cursor and clicking it onto a card files that card.
    @Override
    public boolean overrideStackedOnOther(ItemStack binder, Slot slot, ClickAction action, Player player) {
        if (action != ClickAction.PRIMARY || !canFile(slot.getItem())) {
            return false;
        }
        int count = slot.getItem().getCount();
        Disenchanting.Result result = file(binder, slot.safeTake(count, count, player));
        player.playSound(SoundEvents.BUNDLE_INSERT, 0.8F, 1.0F);
        afterFiling(player, binder, result.disenchanted(), result.xp());
        return true;
    }

    // On the server: hand out experience for disenchanted extras, say so above the hotbar, and check
    // the collection advancements. Filing runs on both sides; only the server does this part.
    public static void afterFiling(Player player, ItemStack binder, int disenchanted, int xp) {
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return;
        }
        if (disenchanted > 0) {
            serverPlayer.giveExperiencePoints(xp);
            serverPlayer.sendOverlayMessage(Component.translatable("binder_ui.craftbound.auto_disenchanted", disenchanted, xp));
            serverPlayer.level().playSound(null, serverPlayer.getX(), serverPlayer.getY(), serverPlayer.getZ(),
                    SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.6F, 1.0F);
        }
        ModAdvancements.binderChanged(serverPlayer, binder.getOrDefault(ModComponents.BINDER_CONTENTS, BinderContents.EMPTY));
    }

    // Only real collectible cards go in a binder: not blank cards, and not tokens.
    public static boolean canFile(ItemStack stack) {
        if (!stack.is(ModItems.CARD)) {
            return false;
        }
        Identifier id = stack.get(ModComponents.CARD_ID);
        CardDefinition card = id == null ? null : Cards.get(id);
        return card != null && !card.token();
    }

    // Moves the whole card stack into the binder. Copies over the limit are disenchanted.
    public static Disenchanting.Result file(ItemStack binder, ItemStack cards) {
        Identifier id = cards.get(ModComponents.CARD_ID);
        boolean enchanted = cards.has(ModComponents.ENCHANTED);
        BinderContents contents = binder.getOrDefault(ModComponents.BINDER_CONTENTS, BinderContents.EMPTY);
        Disenchanting.Result result = Disenchanting.file(contents, id, enchanted, cards.getCount());
        binder.set(ModComponents.BINDER_CONTENTS, result.contents());
        cards.setCount(0);
        return result;
    }
}
