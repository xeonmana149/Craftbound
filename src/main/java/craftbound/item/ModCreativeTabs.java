package craftbound.item;

import craftbound.Craftbound;
import craftbound.card.Cards;
import craftbound.sleeve.Sleeves;
import net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTab;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;

public class ModCreativeTabs {
    public static final ResourceKey<CreativeModeTab> CRAFTBOUND_KEY = ResourceKey.create(
            Registries.CREATIVE_MODE_TAB, Craftbound.id("craftbound")
    );

    public static final CreativeModeTab CRAFTBOUND = FabricCreativeModeTab.builder()
            .icon(() -> new ItemStack(ModItems.CARD))
            .title(Component.translatable("itemGroup.craftbound"))
            .displayItems((params, output) -> {
                output.accept(ModItems.BOOSTER_PACK);
                output.accept(ModItems.BINDER);
                output.accept(ModItems.DECK_CASE);
                for (Identifier sleeve : Sleeves.ids()) {
                    output.accept(SleeveItem.of(sleeve));
                }
                output.accept(ModItems.CARD);
                for (Identifier id : Cards.ids()) {
                    if (Cards.get(id).token()) {
                        continue;
                    }
                    output.accept(CardItem.of(id));
                    output.accept(CardItem.of(id, true));
                }
            })
            .build();

    public static void initialize() {
        Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, CRAFTBOUND_KEY, CRAFTBOUND);
    }
}
