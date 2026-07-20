package dev.nitjsefnie.cultivated.registry;

import dev.nitjsefnie.cultivated.Cultivated;
import dev.nitjsefnie.cultivated.recipe.BlockDerivedRecipes;
import dev.nitjsefnie.cultivated.recipe.CropRecipe;
import dev.nitjsefnie.cultivated.recipe.FertilizerRecipe;
import dev.nitjsefnie.cultivated.recipe.PotInteractionRecipe;
import dev.nitjsefnie.cultivated.recipe.SoilRecipe;
import dev.nitjsefnie.cultivated.util.CodecHelper;
import net.fabricmc.fabric.api.recipe.v1.sync.RecipeSynchronization;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.crafting.RecipeBookCategory;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;

/**
 * Phase A appendix — the four pot recipe types and six serializers. The {@code block_derived_*}
 * serializers decode to the same resolved {@link SoilRecipe}/{@link CropRecipe} classes, which
 * always report their explicit serializer (so they re-encode in explicit form).
 */
public final class ModRecipes {
	/** A book category is required by the vanilla Recipe interface but never surfaced (our recipes
	 * declare no display entries), so it needs no registration. */
	public static final RecipeBookCategory POT_CATEGORY = new RecipeBookCategory();

	public static final RecipeType<SoilRecipe> SOIL_TYPE = type("soil");
	public static final RecipeType<CropRecipe> CROP_TYPE = type("crop");
	public static final RecipeType<FertilizerRecipe> FERTILIZER_TYPE = type("fertilizer");
	public static final RecipeType<PotInteractionRecipe> POT_INTERACTION_TYPE = type("pot_interaction");

	public static final RecipeSerializer<SoilRecipe> SOIL_SERIALIZER = CodecHelper.recipeSerializer(SoilRecipe.EXPLICIT_CODEC);
	public static final RecipeSerializer<SoilRecipe> BLOCK_DERIVED_SOIL_SERIALIZER = CodecHelper.recipeSerializer(BlockDerivedRecipes.SOIL_CODEC);
	public static final RecipeSerializer<CropRecipe> CROP_SERIALIZER = CodecHelper.recipeSerializer(CropRecipe.EXPLICIT_CODEC);
	public static final RecipeSerializer<CropRecipe> BLOCK_DERIVED_CROP_SERIALIZER = CodecHelper.recipeSerializer(BlockDerivedRecipes.CROP_CODEC);
	public static final RecipeSerializer<FertilizerRecipe> FERTILIZER_SERIALIZER = CodecHelper.recipeSerializer(FertilizerRecipe.CODEC);
	public static final RecipeSerializer<PotInteractionRecipe> POT_INTERACTION_SERIALIZER = CodecHelper.recipeSerializer(PotInteractionRecipe.CODEC);

	private ModRecipes() {
	}

	private static <T extends net.minecraft.world.item.crafting.Recipe<?>> RecipeType<T> type(final String name) {
		final String id = name;
		return new RecipeType<T>() {
			@Override
			public String toString() {
				return Cultivated.MOD_ID + ":" + id;
			}
		};
	}

	public static void register() {
		registerType("soil", SOIL_TYPE);
		registerType("crop", CROP_TYPE);
		registerType("fertilizer", FERTILIZER_TYPE);
		registerType("pot_interaction", POT_INTERACTION_TYPE);

		registerSerializer("soil", SOIL_SERIALIZER);
		registerSerializer("block_derived_soil", BLOCK_DERIVED_SOIL_SERIALIZER);
		registerSerializer("crop", CROP_SERIALIZER);
		registerSerializer("block_derived_crop", BLOCK_DERIVED_CROP_SERIALIZER);
		registerSerializer("fertilizer", FERTILIZER_SERIALIZER);
		registerSerializer("pot_interaction", POT_INTERACTION_SERIALIZER);

		// Opt these recipe serializers into network sync so the client receives the full recipe
		// objects (MC 26.2 no longer syncs recipes by default). The client cache (S2) is rebuilt
		// from them via ClientRecipeSynchronizedEvent (see CultivatedClient).
		syncToClients();
	}

	private static void syncToClients() {
		RecipeSynchronization.synchronizeRecipeSerializer(SOIL_SERIALIZER);
		RecipeSynchronization.synchronizeRecipeSerializer(BLOCK_DERIVED_SOIL_SERIALIZER);
		RecipeSynchronization.synchronizeRecipeSerializer(CROP_SERIALIZER);
		RecipeSynchronization.synchronizeRecipeSerializer(BLOCK_DERIVED_CROP_SERIALIZER);
		RecipeSynchronization.synchronizeRecipeSerializer(FERTILIZER_SERIALIZER);
		RecipeSynchronization.synchronizeRecipeSerializer(POT_INTERACTION_SERIALIZER);
	}

	private static void registerType(final String name, final RecipeType<?> type) {
		Registry.register(BuiltInRegistries.RECIPE_TYPE, id(name), type);
	}

	private static void registerSerializer(final String name, final RecipeSerializer<?> serializer) {
		Registry.register(BuiltInRegistries.RECIPE_SERIALIZER, id(name), serializer);
	}

	private static Identifier id(final String name) {
		return Cultivated.id(name);
	}
}
