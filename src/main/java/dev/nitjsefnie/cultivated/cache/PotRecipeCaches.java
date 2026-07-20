package dev.nitjsefnie.cultivated.cache;

import dev.nitjsefnie.cultivated.Cultivated;
import dev.nitjsefnie.cultivated.recipe.CropRecipe;
import dev.nitjsefnie.cultivated.recipe.FertilizerRecipe;
import dev.nitjsefnie.cultivated.recipe.PotInteractionRecipe;
import dev.nitjsefnie.cultivated.recipe.SoilRecipe;
import dev.nitjsefnie.cultivated.registry.ModRecipes;
import dev.nitjsefnie.cultivated.util.SidedReloadableCache;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.RegistryAccess;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;

/**
 * Phase A §A.2 / Phase B S2 — the lookup caches for the four pot recipe kinds, kept side-separated
 * (Phase G #3) so a single-player JVM's logical client and server never share indexes. Each side's
 * cache is a {@link SidedReloadableCache} rebuilt from that side's {@link RecipeManager}: the server
 * side on {@code SERVER_STARTED}/{@code END_DATA_PACK_RELOAD}, the client side whenever recipes are
 * network-synced (the client-init task calls {@link #rebuildClient}). Resolution callers pass their
 * logical side so a pot on the client renders from client-synced recipes while the server harvests
 * from server recipes.
 */
public final class PotRecipeCaches {
	private static volatile RecipeManager clientManager;
	private static volatile RecipeManager serverManager;

	private static final SidedReloadableCache<RecipeLookupCache<SoilRecipe>> SOILS = SidedReloadableCache.of(
		() -> build(clientManager, ModRecipes.SOIL_TYPE), () -> build(serverManager, ModRecipes.SOIL_TYPE)
	);
	private static final SidedReloadableCache<RecipeLookupCache<CropRecipe>> CROPS = SidedReloadableCache.of(
		() -> build(clientManager, ModRecipes.CROP_TYPE), () -> build(serverManager, ModRecipes.CROP_TYPE)
	);
	private static final SidedReloadableCache<RecipeLookupCache<FertilizerRecipe>> FERTILIZERS = SidedReloadableCache.of(
		() -> build(clientManager, ModRecipes.FERTILIZER_TYPE), () -> build(serverManager, ModRecipes.FERTILIZER_TYPE)
	);
	private static final SidedReloadableCache<RecipeLookupCache<PotInteractionRecipe>> INTERACTIONS = SidedReloadableCache.of(
		() -> build(clientManager, ModRecipes.POT_INTERACTION_TYPE), () -> build(serverManager, ModRecipes.POT_INTERACTION_TYPE)
	);

	private PotRecipeCaches() {
	}

	// ---- server-side accessors (default; kept for existing callers) ----

	public static RecipeLookupCache<SoilRecipe> soils() {
		return soils(false);
	}

	public static RecipeLookupCache<CropRecipe> crops() {
		return crops(false);
	}

	public static RecipeLookupCache<FertilizerRecipe> fertilizers() {
		return fertilizers(false);
	}

	public static RecipeLookupCache<PotInteractionRecipe> interactions() {
		return interactions(false);
	}

	// ---- sided accessors (Phase B) ----

	public static RecipeLookupCache<SoilRecipe> soils(final boolean clientSide) {
		return SOILS.get(clientSide);
	}

	public static RecipeLookupCache<CropRecipe> crops(final boolean clientSide) {
		return CROPS.get(clientSide);
	}

	public static RecipeLookupCache<FertilizerRecipe> fertilizers(final boolean clientSide) {
		return FERTILIZERS.get(clientSide);
	}

	public static RecipeLookupCache<PotInteractionRecipe> interactions(final boolean clientSide) {
		return INTERACTIONS.get(clientSide);
	}

	/** Rebuild the server-side caches from the loaded recipe manager (lazy — invalidate now, build on next get). */
	public static void rebuildServer(final RecipeManager manager, final RegistryAccess registries) {
		serverManager = manager;
		SOILS.invalidate(false);
		CROPS.invalidate(false);
		FERTILIZERS.invalidate(false);
		INTERACTIONS.invalidate(false);
		Cultivated.LOGGER.info("Cultivated server recipe caches invalidated; will rebuild on next lookup");
	}

	/**
	 * Rebuild the client-side caches from the client's network-synced recipe manager. Invoked by the
	 * client-init task on recipe sync; harmless if never called (client lookups then see empty caches).
	 */
	public static void rebuildClient(final RecipeManager manager, final RegistryAccess registries) {
		clientManager = manager;
		SOILS.invalidate(true);
		CROPS.invalidate(true);
		FERTILIZERS.invalidate(true);
		INTERACTIONS.invalidate(true);
		Cultivated.LOGGER.info("Cultivated client recipe caches invalidated; will rebuild on next lookup");
	}

	private static <R extends dev.nitjsefnie.cultivated.recipe.BotanyRecipe> RecipeLookupCache<R> build(
		final RecipeManager manager, final RecipeType<R> type
	) {
		if (manager == null) {
			return RecipeLookupCache.empty();
		}
		return RecipeLookupCache.build(collect(manager, type));
	}

	private static <T extends Recipe<?>> List<T> collect(final RecipeManager manager, final RecipeType<T> type) {
		final List<T> result = new ArrayList<>();
		for (final RecipeHolder<?> holder : manager.getRecipes()) {
			if (holder.value().getType() == type) {
				@SuppressWarnings("unchecked")
				final T recipe = (T)holder.value();
				result.add(recipe);
			}
		}
		return result;
	}
}
