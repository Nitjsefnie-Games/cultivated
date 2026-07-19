package dev.nitjsefnie.cultivated.cache;

import dev.nitjsefnie.cultivated.Cultivated;
import dev.nitjsefnie.cultivated.recipe.CropRecipe;
import dev.nitjsefnie.cultivated.recipe.FertilizerRecipe;
import dev.nitjsefnie.cultivated.recipe.PotInteractionRecipe;
import dev.nitjsefnie.cultivated.recipe.SoilRecipe;
import dev.nitjsefnie.cultivated.registry.ModRecipes;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.RegistryAccess;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;

/**
 * Phase A §A.2 — the sided lookup caches for the four pot recipe kinds, rebuilt whenever recipes
 * reload. This is the server-side owner (the reload listener target); the client-side caches
 * (from network-synced recipes) are wired in Phase B/C. Kept side-separated per Phase G #3.
 */
public final class PotRecipeCaches {
	private static volatile RecipeLookupCache<SoilRecipe> serverSoil = RecipeLookupCache.empty();
	private static volatile RecipeLookupCache<CropRecipe> serverCrop = RecipeLookupCache.empty();
	private static volatile RecipeLookupCache<FertilizerRecipe> serverFertilizer = RecipeLookupCache.empty();
	private static volatile RecipeLookupCache<PotInteractionRecipe> serverInteraction = RecipeLookupCache.empty();

	private PotRecipeCaches() {
	}

	public static RecipeLookupCache<SoilRecipe> soils() {
		return serverSoil;
	}

	public static RecipeLookupCache<CropRecipe> crops() {
		return serverCrop;
	}

	public static RecipeLookupCache<FertilizerRecipe> fertilizers() {
		return serverFertilizer;
	}

	public static RecipeLookupCache<PotInteractionRecipe> interactions() {
		return serverInteraction;
	}

	/** Rebuild all server-side caches from the loaded recipe manager. */
	public static void rebuildServer(final RecipeManager manager, final RegistryAccess registries) {
		serverSoil = RecipeLookupCache.build(collect(manager, ModRecipes.SOIL_TYPE));
		serverCrop = RecipeLookupCache.build(collect(manager, ModRecipes.CROP_TYPE));
		serverFertilizer = RecipeLookupCache.build(collect(manager, ModRecipes.FERTILIZER_TYPE));
		serverInteraction = RecipeLookupCache.build(collect(manager, ModRecipes.POT_INTERACTION_TYPE));
		Cultivated.LOGGER.info(
			"Cultivated caches rebuilt: {} soils, {} crops, {} fertilizers, {} interactions",
			serverSoil.values().size(), serverCrop.values().size(), serverFertilizer.values().size(), serverInteraction.values().size()
		);
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
