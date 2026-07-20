package dev.nitjsefnie.cultivated;

import dev.nitjsefnie.cultivated.cache.PotRecipeCaches;
import dev.nitjsefnie.cultivated.command.CultivatedCommands;
import dev.nitjsefnie.cultivated.config.CultivatedConfigFile;
import dev.nitjsefnie.cultivated.registry.CultivatedRegistries;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Cultivated implements ModInitializer {
	public static final String MOD_ID = "cultivated";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	/** A resource id in the mod's own namespace. */
	public static Identifier id(final String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}

	@Override
	public void onInitialize() {
		// Load config first so registration and load conditions see the on-disk values, and write
		// the commented defaults file if none exists yet.
		CultivatedConfigFile.loadOrCreate();

		CultivatedRegistries.register();

		// Rebuild the server-side recipe lookup caches whenever the server starts or datapacks
		// reload, so the data engine's per-item indexes stay in sync with loaded recipe JSON.
		ServerLifecycleEvents.SERVER_STARTED.register(server ->
			PotRecipeCaches.rebuildServer(server.getRecipeManager(), server.registryAccess()));
		ServerLifecycleEvents.END_DATA_PACK_RELOAD.register((server, resources, success) ->
			PotRecipeCaches.rebuildServer(server.getRecipeManager(), server.registryAccess()));

		// F.2 — the /cultivated debug command tree (owner-gated datapack QA tooling).
		CultivatedCommands.register();

		LOGGER.info("Cultivated data engine initialised");
	}
}
