package dev.nitjsefnie.cultivated;

import dev.nitjsefnie.cultivated.cache.PotRecipeCaches;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.recipe.v1.sync.ClientRecipeSynchronizedEvent;
import net.minecraft.core.RegistryAccess;

/**
 * Phase B (B1 carry-over S2) — client-side wiring. Rebuilds the client recipe lookup caches whenever
 * the client receives synced recipes, so client-side pots resolve their crop/soil the same way the
 * server does. MC 26.2 no longer ships a full client {@code RecipeManager}; Fabric's
 * {@link ClientRecipeSynchronizedEvent} delivers the opted-in recipes (see
 * {@code ModRecipes#syncToClients}) as {@code SynchronizedRecipes}, which
 * {@link PotRecipeCaches#rebuildClient} consumes.
 *
 * <p>TODO(B6): the pots must render on the cutout layer. Fabric 26.2 has no code-side block
 * render-layer map (the old {@code BlockRenderLayerMap} was removed); the cutout layer is instead
 * declared per block model via {@code "render_type": "minecraft:cutout"} in the block model JSON,
 * which is asset work owned by Task B6.
 */
public final class CultivatedClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		ClientRecipeSynchronizedEvent.EVENT.register((minecraft, syncedRecipes) -> {
			final RegistryAccess registries = minecraft.getConnection() != null
				? minecraft.getConnection().registryAccess()
				: RegistryAccess.EMPTY;
			PotRecipeCaches.rebuildClient(syncedRecipes, registries);
		});
		Cultivated.LOGGER.info("Cultivated client recipe-sync wiring initialised");
	}
}
