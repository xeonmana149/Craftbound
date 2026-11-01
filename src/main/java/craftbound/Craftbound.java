package craftbound;

import craftbound.block.ModBlocks;
import craftbound.component.ModComponents;
import craftbound.entity.ModEntities;
import craftbound.game.MatchManager;
import craftbound.item.ModCreativeTabs;
import craftbound.item.ModItems;
import craftbound.item.ModLoot;
import craftbound.network.ModNetworking;
import net.fabricmc.api.ModInitializer;


import net.minecraft.resources.Identifier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Craftbound implements ModInitializer {
	public static final String MOD_ID = "craftbound";

	// This logger is used to write text to the console and the log file.
	// It is considered best practice to use your mod id as the logger's name.
	// That way, it's clear which mod wrote info, warnings, and errors.
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		// This code runs as soon as Minecraft is in a mod-load-ready state.
		// However, some things (like resources) may still be uninitialized.
		// Proceed with mild caution.

		LOGGER.info("Hello Fabric world!");
		ModComponents.initialize();
		ModItems.initialize();
		ModBlocks.initialize();
		ModEntities.initialize();
		ModCreativeTabs.initialize();
		ModNetworking.initialize();
		ModLoot.initialize();
		MatchManager.initialize();
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}
