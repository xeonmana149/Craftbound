package craftbound.item;

import craftbound.component.ModComponents;
import craftbound.sleeve.Sleeves;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public class SleeveItem extends Item {
    public SleeveItem(Properties properties) {
        super(properties);
    }

    public static ItemStack of(Identifier sleeve) {
        ItemStack stack = new ItemStack(ModItems.SLEEVE);
        stack.set(ModComponents.SLEEVE_ID, sleeve);
        return stack;
    }

    // "Emerald Sleeve" instead of just "Sleeve".
    @Override
    public Component getName(ItemStack stack) {
        Identifier id = stack.get(ModComponents.SLEEVE_ID);
        return id == null ? super.getName(stack) : Sleeves.name(id);
    }
}
