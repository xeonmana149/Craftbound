package craftbound.block;

import craftbound.Craftbound;
import craftbound.item.ModItems;
import java.util.function.Function;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;

public class ModBlocks {
    public static final Block CARD_TABLE = register("card_table", CardTableBlock::new,
            BlockBehaviour.Properties.of().strength(2.0F).sound(SoundType.WOOD).noOcclusion());
    public static final Block CARD_DISPLAY = register("card_display", CardDisplayBlock::new,
            BlockBehaviour.Properties.of().strength(1.0F).sound(SoundType.WOOD).noOcclusion());

    public static final BlockEntityType<CardDisplayBlockEntity> CARD_DISPLAY_ENTITY = Registry.register(
            BuiltInRegistries.BLOCK_ENTITY_TYPE, Craftbound.id("card_display"),
            FabricBlockEntityTypeBuilder.create(CardDisplayBlockEntity::new, CARD_DISPLAY).build());

    private static final ResourceKey<EntityType<?>> SEAT_KEY = ResourceKey.create(Registries.ENTITY_TYPE, Craftbound.id("seat"));
    // Seats must be allowed to save with the world: in 26.3 the server won't let anyone ride an entity that can't.
    // Empty seats remove themselves on their next tick, so none are left lying around.
    public static final EntityType<SeatEntity> SEAT = Registry.register(BuiltInRegistries.ENTITY_TYPE, SEAT_KEY,
            EntityType.Builder.<SeatEntity>of(SeatEntity::new, MobCategory.MISC)
                    .sized(0.1F, 0.1F)
                    .noSummon()
                    .clientTrackingRange(10)
                    .build(SEAT_KEY));

    // Registers a block and the item you hold to place it.
    private static Block register(String name, Function<BlockBehaviour.Properties, Block> factory, BlockBehaviour.Properties properties) {
        ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, Craftbound.id(name));
        Block block = factory.apply(properties.setId(key));
        Registry.register(BuiltInRegistries.BLOCK, key, block);
        ModItems.register(name, itemProperties -> new BlockItem(block, itemProperties), new Item.Properties().useBlockDescriptionPrefix());
        return block;
    }

    public static void initialize() {
    }
}
