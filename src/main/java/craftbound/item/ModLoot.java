package craftbound.item;

import java.util.Map;
import net.fabricmc.fabric.api.loot.v3.LootTableEvents;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.predicates.LootItemRandomChanceCondition;

// Booster packs in naturally generated chests. The number is the chance that a chest has a pack.
public class ModLoot {
    private static final Map<ResourceKey<LootTable>, Float> PACK_CHANCES = Map.ofEntries(
            Map.entry(BuiltInLootTables.SIMPLE_DUNGEON, 0.35F),
            Map.entry(BuiltInLootTables.ABANDONED_MINESHAFT, 0.25F),
            Map.entry(BuiltInLootTables.VILLAGE_PLAINS_HOUSE, 0.2F),
            Map.entry(BuiltInLootTables.VILLAGE_DESERT_HOUSE, 0.2F),
            Map.entry(BuiltInLootTables.VILLAGE_TAIGA_HOUSE, 0.2F),
            Map.entry(BuiltInLootTables.VILLAGE_SNOWY_HOUSE, 0.2F),
            Map.entry(BuiltInLootTables.VILLAGE_SAVANNA_HOUSE, 0.2F),
            Map.entry(BuiltInLootTables.VILLAGE_CARTOGRAPHER, 0.4F),
            Map.entry(BuiltInLootTables.DESERT_PYRAMID, 0.3F),
            Map.entry(BuiltInLootTables.STRONGHOLD_LIBRARY, 0.5F),
            Map.entry(BuiltInLootTables.WOODLAND_MANSION, 0.5F),
            Map.entry(BuiltInLootTables.PILLAGER_OUTPOST, 0.3F),
            Map.entry(BuiltInLootTables.BURIED_TREASURE, 0.4F),
            Map.entry(BuiltInLootTables.SHIPWRECK_TREASURE, 0.3F),
            Map.entry(BuiltInLootTables.NETHER_BRIDGE, 0.25F),
            Map.entry(BuiltInLootTables.BASTION_TREASURE, 0.4F),
            Map.entry(BuiltInLootTables.END_CITY_TREASURE, 0.4F)
    );

    public static void initialize() {
        LootTableEvents.MODIFY.register((key, tableBuilder, source, registries) -> {
            Float chance = PACK_CHANCES.get(key);
            // Only the game's own tables, so a datapack that replaces a chest keeps its own loot.
            if (chance == null || !source.isBuiltin()) {
                return;
            }
            tableBuilder.withPool(LootPool.lootPool()
                    .add(LootItem.lootTableItem(ModItems.BOOSTER_PACK))
                    .when(LootItemRandomChanceCondition.randomChance(chance)));
        });
    }
}
