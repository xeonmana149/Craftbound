package craftbound.item;

import craftbound.binder.BinderContents;
import craftbound.card.CardDefinition;
import craftbound.card.Cards;
import craftbound.component.ModComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

// One binder holds the whole collection, with a numbered pocket for every card.
// File cards by clicking them onto the binder in your inventory, like a bundle. Right-click it to open the pages.
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
        file(binder, other);
        player.playSound(SoundEvents.BUNDLE_INSERT, 0.8F, 1.0F);
        return true;
    }

    // Holding the binder on the cursor and clicking it onto a card files that card.
    @Override
    public boolean overrideStackedOnOther(ItemStack binder, Slot slot, ClickAction action, Player player) {
        if (action != ClickAction.PRIMARY || !canFile(slot.getItem())) {
            return false;
        }
        int count = slot.getItem().getCount();
        file(binder, slot.safeTake(count, count, player));
        player.playSound(SoundEvents.BUNDLE_INSERT, 0.8F, 1.0F);
        return true;
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

    // Moves the whole card stack into the binder.
    public static void file(ItemStack binder, ItemStack cards) {
        Identifier id = cards.get(ModComponents.CARD_ID);
        boolean enchanted = cards.has(ModComponents.ENCHANTED);
        BinderContents contents = binder.getOrDefault(ModComponents.BINDER_CONTENTS, BinderContents.EMPTY);
        binder.set(ModComponents.BINDER_CONTENTS, contents.add(id, enchanted, cards.getCount()));
        cards.setCount(0);
    }
}
