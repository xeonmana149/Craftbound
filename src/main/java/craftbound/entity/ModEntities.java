package craftbound.entity;

import craftbound.Craftbound;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.Attributes;

public class ModEntities {
    private static final ResourceKey<EntityType<?>> CARD_MASTER_KEY = ResourceKey.create(Registries.ENTITY_TYPE, Craftbound.id("card_master"));
    // A Card Master waiting at a paused table survives chunk unloads and world reloads.
    public static final EntityType<CardMasterEntity> CARD_MASTER = Registry.register(BuiltInRegistries.ENTITY_TYPE, CARD_MASTER_KEY,
            EntityType.Builder.<CardMasterEntity>of(CardMasterEntity::new, MobCategory.MISC)
                    .sized(0.6F, 1.95F)
                    .noSummon()
                    .clientTrackingRange(10)
                    .build(CARD_MASTER_KEY));

    private static final ResourceKey<EntityType<?>> CARD_SEAT_KEY = ResourceKey.create(Registries.ENTITY_TYPE, Craftbound.id("card_seat"));
    public static final EntityType<CardSeatEntity> CARD_SEAT = Registry.register(BuiltInRegistries.ENTITY_TYPE, CARD_SEAT_KEY,
            EntityType.Builder.<CardSeatEntity>of(CardSeatEntity::new, MobCategory.MISC)
                    .sized(0.75F, 1.25F).noSummon().clientTrackingRange(10).build(CARD_SEAT_KEY));

    public static void initialize() {
        FabricDefaultAttributeRegistry.register(CARD_MASTER, Mob.createMobAttributes().add(Attributes.MOVEMENT_SPEED, 0.5));
    }
}
