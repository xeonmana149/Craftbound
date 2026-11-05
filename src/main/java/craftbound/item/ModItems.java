package craftbound.item;

import craftbound.Craftbound;
import craftbound.binder.BinderContents;
import craftbound.component.ModComponents;
import craftbound.deck.DeckContents;
import java.util.function.Function;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;

public class ModItems {
    public static final Item CARD = register("card", CardItem::new, new Item.Properties());
    // Packs stack to 16 and can take Fortune at an enchanting table or anvil, which rolls rarer cards.
    public static final Item BOOSTER_PACK = register("booster_pack", BoosterPackItem::new, new Item.Properties()
            .stacksTo(16)
            .enchantable(10));
    public static final Item BINDER = register("binder", BinderItem::new, new Item.Properties()
            .stacksTo(1)
            .component(ModComponents.BINDER_CONTENTS, BinderContents.EMPTY));
    public static final Item SLEEVE = register("sleeve", SleeveItem::new, new Item.Properties().stacksTo(1));
    public static Item register(String name, Function<Item.Properties, Item> factory, Item.Properties properties) {
        ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, Craftbound.id(name));
        Item item = factory.apply(properties.setId(key));
        Registry.register(BuiltInRegistries.ITEM, key, item);
        return item;
    }

    public static void initialize() {
    }
}
