package dev.nitjsefnie.cultivated.recipe;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import dev.nitjsefnie.cultivated.CultivatedTestBootstrap;
import dev.nitjsefnie.cultivated.condition.LoadCondition;
import dev.nitjsefnie.cultivated.data.display.Display;
import dev.nitjsefnie.cultivated.data.display.RenderOptions;
import java.util.List;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * C1 (guarded absent-block files skip cleanly at parse) and I3 (double_block_half auto display).
 */
class BlockDerivedRecipesTest {
	private static final String ABSENT = "zzznonexistent:crop";

	@BeforeAll
	static void boot() {
		CultivatedTestBootstrap.bootstrap();
	}

	private static DataResult<CropRecipe> decodeCrop(final String json) {
		return BlockDerivedRecipes.CROP_CODEC.codec().parse(JsonOps.INSTANCE, JsonParser.parseString(json));
	}

	// ---- C1 ----

	@Test
	void guardedAbsentBlock_parsesCleanlyAndIsDropped() {
		final DataResult<CropRecipe> result = decodeCrop(
			"{\"block\":\"" + ABSENT + "\",\"cultivated:load_conditions\":"
			+ "[{\"type\":\"cultivated:block_exists\",\"values\":[\"" + ABSENT + "\"]}]}");
		assertTrue(result.result().isPresent(), "a guarded absent-block file must parse with no error: " + result.error());

		final CropRecipe recipe = result.result().get();
		assertEquals(1, recipe.conditions().size());
		// RecipeLookupCache.build filters on testAll -> a failing guard means the recipe is never an
		// active pot recipe. (build() itself iterates every item's default instance, which needs a
		// full registry freeze beyond Bootstrap, so the drop decision is asserted directly here.)
		assertFalse(LoadCondition.testAll(recipe.conditions()), "the failing guard must mark the recipe for drop");
	}

	@Test
	void legacyGuardedAbsentBlock_parsesCleanlyAndIsDropped() {
		final DataResult<CropRecipe> result = decodeCrop(
			"{\"block\":\"" + ABSENT + "\",\"bookshelf:load_conditions\":"
			+ "[{\"type\":\"cultivated:block_exists\",\"values\":[\"" + ABSENT + "\"]}]}");
		assertTrue(result.result().isPresent(), "legacy bookshelf guard must also gate pre-parse: " + result.error());
		assertEquals(1, result.result().get().conditions().size(), "legacy bookshelf:load_conditions must be read");
		assertFalse(LoadCondition.testAll(result.result().get().conditions()));
	}

	@Test
	void unguardedAbsentBlock_isAGenuineError() {
		final DataResult<CropRecipe> result = decodeCrop("{\"block\":\"" + ABSENT + "\"}");
		assertTrue(result.error().isPresent(), "an absent block with no gating condition must still error");
	}

	// ---- I3 ----

	@Test
	void doubleBlockHalfBlock_derivesTwoStackedDisplays() {
		final List<Display> displays = BlockDerivedRecipes.autoDisplay(Blocks.SUNFLOWER, RenderOptions.DEFAULT);
		assertEquals(2, displays.size(), "a double_block_half plant must yield two stacked displays");
		final Display.Simple lower = assertInstanceOf(Display.Simple.class, displays.get(0));
		final Display.Simple upper = assertInstanceOf(Display.Simple.class, displays.get(1));
		assertEquals(DoubleBlockHalf.LOWER, lower.blockState().getValue(BlockStateProperties.DOUBLE_BLOCK_HALF),
			"lower half must render first");
		assertEquals(DoubleBlockHalf.UPPER, upper.blockState().getValue(BlockStateProperties.DOUBLE_BLOCK_HALF),
			"upper half must render second");
	}

	@Test
	void plainCropBlock_derivesSingleAgingDisplay() {
		final List<Display> displays = BlockDerivedRecipes.autoDisplay(Blocks.WHEAT, RenderOptions.DEFAULT);
		assertEquals(1, displays.size());
		assertInstanceOf(Display.Aging.class, displays.get(0));
	}
}
