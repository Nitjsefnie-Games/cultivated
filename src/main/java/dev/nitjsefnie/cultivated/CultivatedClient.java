package dev.nitjsefnie.cultivated;

import dev.nitjsefnie.cultivated.block.BotanyPotBlockEntity;
import dev.nitjsefnie.cultivated.cache.PotRecipeCaches;
import dev.nitjsefnie.cultivated.client.BasicPotScreen;
import dev.nitjsefnie.cultivated.client.HopperPotScreen;
import dev.nitjsefnie.cultivated.client.render.BotanyPotBlockEntityRenderer;
import dev.nitjsefnie.cultivated.registry.ModBlockEntities;
import dev.nitjsefnie.cultivated.registry.ModMenus;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.recipe.v1.sync.ClientRecipeSynchronizedEvent;
import net.fabricmc.fabric.api.client.rendering.v1.BlockEntityRendererRegistry;
import net.minecraft.client.gui.screens.MenuScreens;

/**
 * Phase B (B1 carry-over S2) / Phase C — client-side wiring. Rebuilds the client recipe lookup caches
 * whenever the client receives synced recipes (so client-side pots resolve their crop/soil the same
 * way the server does), binds the pot menu screens (§B.7), and registers the pot block-entity renderer
 * (§C) that draws the soil/crop displays in-world. MC 26.2 no longer ships a full client {@code
 * RecipeManager}; Fabric's {@link ClientRecipeSynchronizedEvent} delivers the opted-in recipes (see
 * {@code ModRecipes#syncToClients}) as {@code SynchronizedRecipes}, which
 * {@link PotRecipeCaches#rebuildClient} consumes.
 *
 * <p>The pots render their block model on the cutout layer via {@code "render_type":
 * "minecraft:cutout"} declared per block model (Task B6 asset work — Fabric 26.2 has no code-side
 * block render-layer map); the block-entity renderer here adds the dynamic soil/crop on top.
 */
public final class CultivatedClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		ClientRecipeSynchronizedEvent.EVENT.register((minecraft, syncedRecipes) ->
			PotRecipeCaches.rebuildClient(syncedRecipes));

		// Bind the pot menu types to their screens (§B.7). The menu types are Fabric ExtendedMenuTypes,
		// but screen registration still goes through vanilla MenuScreens#register.
		MenuScreens.register(ModMenus.BASIC_POT, BasicPotScreen::new);
		MenuScreens.register(ModMenus.HOPPER_POT, HopperPotScreen::new);

		// Draw the soil/crop displays in the planter (§C), for the base pot BE type and each tier BE type
		// (§D) — all share the one renderer. The BE types were registered during common init (Task B2/D2),
		// which runs before client init.
		for (final BlockEntityType<BotanyPotBlockEntity> type : ModBlockEntities.TYPES.values()) {
			BlockEntityRendererRegistry.register(type, BotanyPotBlockEntityRenderer::new);
		}

		Cultivated.LOGGER.info("Cultivated client recipe-sync, screen and renderer wiring initialised");
	}
}
