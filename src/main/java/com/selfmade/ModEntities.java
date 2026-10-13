package com.selfmade;

import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

/** Every entity this mod adds. */
public final class ModEntities {
	public static final ResourceKey<EntityType<?>> EIDOLON_KEY =
			ResourceKey.create(Registries.ENTITY_TYPE, Identifier.fromNamespaceAndPath(SelfMade.MOD_ID, "eidolon"));

	public static final EntityType<EidolonEntity> EIDOLON = Registry.register(
			BuiltInRegistries.ENTITY_TYPE, EIDOLON_KEY,
			EntityType.Builder.<EidolonEntity>of(EidolonEntity::new, MobCategory.MISC)
					.sized(0.7f, 1.9f)
					.clientTrackingRange(8)
					.updateInterval(2)
					.build(EIDOLON_KEY));

	public static final ResourceKey<EntityType<?>> SHADE_KEY =
			ResourceKey.create(Registries.ENTITY_TYPE, Identifier.fromNamespaceAndPath(SelfMade.MOD_ID, "shade"));

	/** Echoes, the Death-Eidolon, The Other You, statues and name-tags. Never saved to the world. */
	public static final EntityType<ShadeEntity> SHADE = Registry.register(
			BuiltInRegistries.ENTITY_TYPE, SHADE_KEY,
			EntityType.Builder.<ShadeEntity>of(ShadeEntity::new, MobCategory.MISC)
					.sized(0.7f, 1.9f)
					.fireImmune()
					.noSave()
					.clientTrackingRange(8)
					.updateInterval(2)
					.build(SHADE_KEY));

	private ModEntities() {
	}

	/** Called once from the mod initializer; touching this class is what registers the entity above. */
	public static void initialize() {
		FabricDefaultAttributeRegistry.register(EIDOLON, EidolonEntity.createAttributes());
		FabricDefaultAttributeRegistry.register(SHADE, ShadeEntity.createAttributes());
	}
}
