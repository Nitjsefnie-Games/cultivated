package dev.nitjsefnie.cultivated.recipe;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import com.google.gson.JsonParser;
import dev.nitjsefnie.cultivated.CultivatedTestBootstrap;
import dev.nitjsefnie.cultivated.data.display.Display;
import dev.nitjsefnie.cultivated.data.drop.DropProvider;
import dev.nitjsefnie.cultivated.ingredient.CultivatedIngredient;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentInitializers;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * The generic growable-mob mechanism. Proves the "any spawn egg" ingredient matches spawn eggs (and
 * nothing else) and that a spawn-egg seed derives an {@code entity} display + equipment-aware
 * {@code entity} death-loot drop for THAT egg's entity type — with no hardcoded entity id, so it holds
 * for vanilla and modded eggs alike (all are {@code SpawnEggItem} carrying an {@code ENTITY_DATA} type).
 */
class SpawnEggCropRecipeTest {
	@BeforeAll
	static void boot() {
		CultivatedTestBootstrap.bootstrap();
		// Bind item data components so real ItemStacks (and SpawnEggItem.getType's ENTITY_DATA) resolve;
		// they are otherwise unbound in a bootstrap-only run (see DirtToFarmlandInteractionTest).
		final HolderLookup.Provider provider = VanillaRegistries.createLookup();
		BuiltInRegistries.DATA_COMPONENT_INITIALIZERS.build(provider).forEach(DataComponentInitializers.PendingComponents::apply);
	}

	@Test
	void spawnEggIngredientMatchesAnySpawnEggAndNothingElse() {
		final CultivatedIngredient ingredient = CultivatedIngredient.SpawnEgg.INSTANCE;
		assertTrue(ingredient.test(new ItemStack(Items.ZOMBIE_SPAWN_EGG)), "zombie spawn egg should match");
		assertTrue(ingredient.test(new ItemStack(Items.CREEPER_SPAWN_EGG)), "creeper spawn egg should match");
		assertFalse(ingredient.test(new ItemStack(Items.DIRT)), "dirt is not a spawn egg");
		assertFalse(ingredient.test(new ItemStack(Items.EGG)), "a chicken egg is not a spawn egg");
		assertFalse(ingredient.test(ItemStack.EMPTY), "empty is not a spawn egg");
	}

	@Test
	void spawnEggIngredientParsesThroughItsTypeId() {
		final DataResult<CultivatedIngredient> result = CultivatedIngredient.CODEC.parse(
			JsonOps.INSTANCE, JsonParser.parseString("{\"type\":\"cultivated:spawn_egg\"}")
		);
		assertTrue(result.error().isEmpty(), () -> "spawn_egg ingredient should parse: " + result.error());
		assertInstanceOf(CultivatedIngredient.SpawnEgg.class, result.result().orElseThrow());
	}

	@Test
	void zombieSpawnEggResolvesToZombieEntityDisplayAndDrop() {
		final SpawnEggCropRecipe recipe = sampleRecipe();
		final CropRecipe resolved = recipe.resolveFor(new ItemStack(Items.ZOMBIE_SPAWN_EGG));
		assertNotNull(resolved, "a zombie spawn egg must resolve to a crop");

		assertEquals(1, resolved.displays().size(), "exactly one entity display");
		final Display.Entity display = assertInstanceOf(Display.Entity.class, resolved.displays().getFirst());
		assertEquals("minecraft:zombie", display.entity().getStringOr("id", ""), "display entity is the egg's type");

		assertEquals(2, resolved.drops().size(), "the entity death-loot drop plus the rare spawn-egg drop");
		final DropProvider.EntityDrop drop = assertInstanceOf(DropProvider.EntityDrop.class, resolved.drops().getFirst());
		assertEquals("minecraft:zombie", drop.entity().getStringOr("id", ""), "drop entity is the egg's type");
		assertTrue(drop.finalizeSpawn(), "the derived drop must finalize the mob so equipment can roll");
	}

	@Test
	void harvestHasRareChanceToDropTheSpawnEggBack() {
		final SpawnEggCropRecipe recipe = sampleRecipe();
		final CropRecipe resolved = recipe.resolveFor(new ItemStack(Items.ZOMBIE_SPAWN_EGG));
		assertNotNull(resolved, "a zombie spawn egg must resolve to a crop");

		// The second drop is a 0.1%-chance items drop yielding the egg itself, on top of the death loot.
		final DropProvider.Items eggDrop = assertInstanceOf(DropProvider.Items.class, resolved.drops().get(1));
		assertEquals(1, eggDrop.items().size(), "exactly one bonus egg entry");
		final DropProvider.Items.Entry entry = eggDrop.items().getFirst();
		assertEquals(0.001f, entry.chance(), "a 0.1% drop chance");
		assertEquals(Items.ZOMBIE_SPAWN_EGG, entry.result().get().getItem(), "the bonus drop is the egg itself");
	}

	@Test
	void resolvesGenericallyForAnyEggWithoutHardcodingIds() {
		final SpawnEggCropRecipe recipe = sampleRecipe();
		// A different egg derives a different entity type through the same code path — no per-mob branch.
		final CropRecipe resolved = recipe.resolveFor(new ItemStack(Items.CREEPER_SPAWN_EGG));
		assertNotNull(resolved);
		final Display.Entity display = assertInstanceOf(Display.Entity.class, resolved.displays().getFirst());
		assertEquals("minecraft:creeper", display.entity().getStringOr("id", ""));
	}

	@Test
	void nonSpawnEggResolvesToNull() {
		assertNull(sampleRecipe().resolveFor(new ItemStack(Items.DIRT)), "dirt has no entity type to derive");
	}

	private static SpawnEggCropRecipe sampleRecipe() {
		return new SpawnEggCropRecipe(
			Optional.empty(), 6000, 0, true, 0.0f,
			dev.nitjsefnie.cultivated.data.display.Display.Entity.DEFAULT_SCALE,
			Optional.empty(), Optional.empty(), 1.0f, 1.0f, List.of()
		);
	}
}
