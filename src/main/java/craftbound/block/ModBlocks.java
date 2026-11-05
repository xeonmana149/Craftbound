package craftbound.block;

import craftbound.Craftbound;
import craftbound.item.ModItems;
import java.util.function.Function;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;

public class ModBlocks {
    public static final Block CARD_TABLE = register("card_table", CardTableBlock::new,
            BlockBehaviour.Properties.of().strength(2.0F).sound(SoundType.WOOD).noOcclusion()
                    .pushReaction(net.minecraft.world.level.material.PushReaction.IMMOVEABLE));
    public static final Block TEAM_TABLE = register("team_table", p -> new MultiplayerTableBlock(p,MultiplayerTableBlock.DUOS),
            BlockBehaviour.Properties.of().strength(2.5F).sound(SoundType.WOOD).noOcclusion()
                    .pushReaction(net.minecraft.world.level.material.PushReaction.IMMOVEABLE));
    public static final Block COMMANDER_TABLE = register("commander_table", p -> new MultiplayerTableBlock(p,MultiplayerTableBlock.COMMANDER),
            BlockBehaviour.Properties.of().strength(3.0F).sound(SoundType.WOOD).noOcclusion()
                    .pushReaction(net.minecraft.world.level.material.PushReaction.IMMOVEABLE));
    public static final Block CARD_DISPLAY = register("card_display", CardDisplayBlock::new,
            BlockBehaviour.Properties.of().strength(1.0F).sound(SoundType.WOOD).noOcclusion());

    public static final Block CARD_VENDOR = register("card_vendor", CardVendorBlock::new,
            BlockBehaviour.Properties.of().strength(3.5F).sound(SoundType.METAL)
                    .pushReaction(net.minecraft.world.level.material.PushReaction.IMMOVEABLE)
                    .lightLevel(state -> state.getValue(CardVendorBlock.HALF)==net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER?7:0));
    public static final BlockEntityType<CardVendorBlockEntity> CARD_VENDOR_ENTITY = Registry.register(
            BuiltInRegistries.BLOCK_ENTITY_TYPE, Craftbound.id("card_vendor"),
            FabricBlockEntityTypeBuilder.create(CardVendorBlockEntity::new, CARD_VENDOR).build());

    public static final BlockEntityType<CardDisplayBlockEntity> CARD_DISPLAY_ENTITY = Registry.register(
            BuiltInRegistries.BLOCK_ENTITY_TYPE, Craftbound.id("card_display"),
            FabricBlockEntityTypeBuilder.create(CardDisplayBlockEntity::new, CARD_DISPLAY).build());

    public static final BlockEntityType<CardTableBlockEntity> CARD_TABLE_ENTITY = Registry.register(
            BuiltInRegistries.BLOCK_ENTITY_TYPE, Craftbound.id("card_table"),
            FabricBlockEntityTypeBuilder.create(CardTableBlockEntity::new, CARD_TABLE, TEAM_TABLE, COMMANDER_TABLE).build());

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
