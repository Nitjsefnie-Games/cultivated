package dev.nitjsefnie.cultivated.recipe;

import dev.nitjsefnie.cultivated.condition.LoadCondition;
import dev.nitjsefnie.cultivated.ingredient.CultivatedIngredient;
import dev.nitjsefnie.cultivated.registry.ModRecipes;
import java.util.List;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.PlacementInfo;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeBookCategory;
import net.minecraft.world.level.Level;

/**
 * Phase A §A.1 — common base for the four pot recipe kinds. Each is a "special" recipe that
 * produces no crafting-grid result and matches a {@link PotContext} instead of a grid. Two
 * matching tests are exposed:
 * <ul>
 *   <li>{@link #couldMatch(ItemStack)} — a cheap, intrinsic test on a single item (used for cache
 *       indexing and lookup);</li>
 *   <li>{@link #matches(PotContext, Level)} — the full contextual test.</li>
 * </ul>
 */
public interface BotanyRecipe extends Recipe<PotContext> {
	/** The primary ingredient this kind indexes on (soil for soils, seed for crops, held item for
	 * fertilizer/interaction). */
	CultivatedIngredient primaryIngredient();

	/** Load conditions that gate this recipe (§A.9); empty means always active. */
	List<LoadCondition> conditions();

	/**
	 * Cheap intrinsic test on a single item — ingredient membership only, no world/NBT/components.
	 * Used to build and query the lookup cache.
	 */
	default boolean couldMatch(final ItemStack candidate) {
		return this.primaryIngredient().test(candidate);
	}

	/** True if this recipe's match depends only on intrinsic item identity/tags (§A.2). */
	default boolean isCacheable() {
		return true;
	}

	// ---- Vanilla Recipe boilerplate: special, grid-less, not placeable ----

	@Override
	default ItemStack assemble(final PotContext input) {
		return ItemStack.EMPTY;
	}

	@Override
	default boolean isSpecial() {
		return true;
	}

	@Override
	default boolean showNotification() {
		return false;
	}

	@Override
	default String group() {
		return "";
	}

	@Override
	default PlacementInfo placementInfo() {
		return PlacementInfo.NOT_PLACEABLE;
	}

	@Override
	default RecipeBookCategory recipeBookCategory() {
		return ModRecipes.POT_CATEGORY;
	}
}
