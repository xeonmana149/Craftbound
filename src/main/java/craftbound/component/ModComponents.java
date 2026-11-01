package craftbound.component;

import craftbound.Craftbound;
import craftbound.card.CardDefinition;
import craftbound.card.Cards;
import net.fabricmc.fabric.api.item.v1.ItemComponentTooltipProviderRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
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
    }
}
