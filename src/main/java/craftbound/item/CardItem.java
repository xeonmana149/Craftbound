package craftbound.item;

import craftbound.card.CardDefinition;
import craftbound.card.Cards;
import craftbound.component.ModComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Unit;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public class CardItem extends Item {
    public CardItem(Properties properties) {
        super(properties);
    }

    // Makes a Card stack that is a specific card, e.g. CardItem.of(Cards.ZOMBIE).
    public static ItemStack of(Identifier id) {
        return of(id, false);
    }

    public static ItemStack of(Identifier id, boolean enchanted) {
        ItemStack stack = new ItemStack(ModItems.CARD);
        stack.set(ModComponents.CARD_ID, id);
        if (enchanted) {
            stack.set(ModComponents.ENCHANTED, Unit.INSTANCE);
        }
        return stack;
    }

    @Override
    public Component getName(ItemStack stack) {
        Identifier id = stack.get(ModComponents.CARD_ID);
        CardDefinition card = id == null ? null : Cards.get(id);
        if (card == null) {
            return super.getName(stack);
        }
        Component name = Cards.name(id);
        if (stack.has(ModComponents.ENCHANTED)) {
            name = Component.translatable("card.craftbound.enchanted", name);
        }
        return name.copy().withStyle(card.rarity().color());
    }

    // Enchanted cards get the enchantment glint.
    @Override
    public boolean isFoil(ItemStack stack) {
        return stack.has(ModComponents.ENCHANTED) || super.isFoil(stack);
    }
}
