package craftbound.component;

import craftbound.Craftbound;
import craftbound.binder.BinderContents;
import craftbound.card.CardDefinition;
import craftbound.card.Cards;
import net.fabricmc.fabric.api.item.v1.ItemComponentTooltipProviderRegistry;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Unit;

public class ModComponents {
    public static final DataComponentType<Identifier> CARD_ID = Registry.register(
            BuiltInRegistries.DATA_COMPONENT_TYPE,
            Craftbound.id("card_id"),
            DataComponentType.<Identifier>builder()
                    .persistent(Identifier.CODEC)
                    .networkSynchronized(Identifier.STREAM_CODEC)
                    .build()
    );

    // Marks a card as Enchanted. It holds no value: a card either has it or doesn't.
    public static final DataComponentType<Unit> ENCHANTED = Registry.register(
            BuiltInRegistries.DATA_COMPONENT_TYPE,
            Craftbound.id("enchanted"),
            DataComponentType.<Unit>builder()
                    .persistent(Unit.CODEC)
                    .networkSynchronized(StreamCodec.unit(Unit.INSTANCE))
                    .build()
    );

    // The cards filed in a binder.
    public static final DataComponentType<BinderContents> BINDER_CONTENTS = Registry.register(
            BuiltInRegistries.DATA_COMPONENT_TYPE,
            Craftbound.id("binder_contents"),
            DataComponentType.<BinderContents>builder()
                    .persistent(BinderContents.CODEC)
                    .networkSynchronized(BinderContents.STREAM_CODEC)
                    .build()
    );

    // Which card back design a sleeve has.
    public static final DataComponentType<Identifier> SLEEVE_ID = Registry.register(
            BuiltInRegistries.DATA_COMPONENT_TYPE,
            Craftbound.id("sleeve_id"),
            DataComponentType.<Identifier>builder()
                    .persistent(Identifier.CODEC)
                    .networkSynchronized(Identifier.STREAM_CODEC)
                    .build()
    );

    public static void initialize() {
        // Unknown IDs (e.g. a typo in /give) get an empty tooltip instead of crashing.
        ItemComponentTooltipProviderRegistry.addFirst(CARD_ID, id -> {
            CardDefinition card = Cards.get(id);
            return (context, tooltip, flag, components) -> {
                if (card != null) {
                    card.appendTooltip(id, tooltip);
                }
            };
        });

        ItemComponentTooltipProviderRegistry.addFirst(BINDER_CONTENTS, contents -> (context, tooltip, flag, components) -> {
            tooltip.accept(Component.translatable("binder_ui.craftbound.collected",
                    contents.collected(Cards.collectible()), Cards.collectible().size()).withStyle(ChatFormatting.GOLD));
            tooltip.accept(Component.translatable("binder_ui.craftbound.collected_enchanted",
                    contents.collectedEnchanted(Cards.collectible()), Cards.collectible().size()).withStyle(ChatFormatting.LIGHT_PURPLE));
            tooltip.accept(Component.translatable("binder_ui.craftbound.holds", contents.total()).withStyle(ChatFormatting.GRAY));
            tooltip.accept(Component.translatable("binder_ui.craftbound.how_to").withStyle(ChatFormatting.DARK_GRAY));
        });

        ItemComponentTooltipProviderRegistry.addFirst(SLEEVE_ID, id -> (context, tooltip, flag, components) ->
                tooltip.accept(Component.translatable("sleeve_ui.craftbound.desc").withStyle(ChatFormatting.GRAY)));
    }
}
