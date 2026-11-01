package craftbound.item;

import craftbound.component.ModComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

// Holds a deck of real cards, built from the binders in your inventory. Right-click it to build the deck.
// Click a sleeve onto it in your inventory to change your card backs; the old sleeve comes back to your cursor.
public class DeckCaseItem extends Item {
    public DeckCaseItem(Properties properties) {
        super(properties);
    }

    @Override
    public boolean overrideOtherStackedOnMe(ItemStack deckCase, ItemStack other, Slot slot, ClickAction action,
                                            Player player, SlotAccess carried) {
        if (action != ClickAction.PRIMARY || !other.is(ModItems.SLEEVE) || !other.has(ModComponents.SLEEVE_ID)) {
            return false;
        }
        Identifier oldSleeve = deckCase.get(ModComponents.SLEEVE_ID);
        deckCase.set(ModComponents.SLEEVE_ID, other.get(ModComponents.SLEEVE_ID));
        if (oldSleeve != null) {
            carried.set(SleeveItem.of(oldSleeve));
        } else {
            other.shrink(1);
        }
        player.playSound(SoundEvents.BUNDLE_INSERT, 0.8F, 1.2F);
        return true;
    }
}
