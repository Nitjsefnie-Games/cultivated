package dev.nitjsefnie.cultivated.menu;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JsonOps;
import dev.nitjsefnie.cultivated.CultivatedTestBootstrap;
import dev.nitjsefnie.cultivated.cache.RecipeLookupCache;
import dev.nitjsefnie.cultivated.recipe.CropRecipe;
import dev.nitjsefnie.cultivated.recipe.SimplePotContext;
import dev.nitjsefnie.cultivated.recipe.SoilRecipe;
import dev.nitjsefnie.cultivated.recipe.SpawnEggCropRecipe;
import java.util.HashMap;
import java.util.List;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentInitializers;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;
import net.minecraft.tags.TagKey;
import net.minecraft.tags.TagLoader;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Seed/soil slot acceptance (the pot-menu {@code mayPlace} + shift-click routing predicates). Guards
 * the regression where a spawn egg was rejected by the seed slot: {@link AbstractPotMenu#cropResolves}
 * must recognise a spawn egg as a plantable crop through the generic growable-mob mechanism (the SAME
 * spawn-egg cache + {@code resolveFor} the block entity grows it by), not just the normal crop cache.
 * Also confirms {@code minecraft:spawner} still resolves as a soil (its soil slot is not gated out).
 */
class SeedSlotAcceptanceTest {
	private static HolderLookup.Provider provider;

	@BeforeAll
	static void boot() {
		CultivatedTestBootstrap.bootstrap();
		provider = VanillaRegistries.createLookup();
		// Bind item data components so every item's default instance (scanned by RecipeLookupCache.build)
		// and SpawnEggItem.getType's ENTITY_DATA resolve — otherwise unbound in a bootstrap-only run.
		BuiltInRegistries.DATA_COMPONENT_INITIALIZERS.build(provider)
			.forEach(DataComponentInitializers.PendingComponents::apply);
		// Bind the spawner soil item tag the shipped soil recipe indexes on.
		final HashMap<TagKey<Item>, List<Holder<Item>>> tags = new HashMap<>();
		tags.put(TagKey.create(Registries.ITEM, Identifier.parse("cultivated:soil/spawner")),
			List.of(Items.SPAWNER.builtInRegistryHolder()));
		BuiltInRegistries.ITEM.prepareTagReload(new TagLoader.LoadResult<>(Registries.ITEM, tags)).apply();
	}

	private static DynamicOps<JsonElement> ops() {
		return RegistryOps.create(JsonOps.INSTANCE, provider);
	}

	/** The shipped generic growable-mob recipe (indexed under every spawn egg). */
	private static SpawnEggCropRecipe spawnEggRecipe() {
		return SpawnEggCropRecipe.EXPLICIT_CODEC.codec()
			.parse(ops(), JsonParser.parseString(
				"{\"type\":\"cultivated:spawn_egg_crop\",\"soil\":\"#cultivated:soil/spawner\",\"grow_time\":6000}"))
			.result().orElseThrow();
	}

	/** The shipped {@code minecraft:spawner} soil recipe. */
	private static SoilRecipe spawnerSoilRecipe() {
		return SoilRecipe.EXPLICIT_CODEC.codec()
			.parse(ops(), JsonParser.parseString(
				"{\"type\":\"cultivated:soil\",\"input\":\"#cultivated:soil/spawner\","
				+ "\"display\":{\"type\":\"cultivated:simple\",\"block_state\":{\"Name\":\"minecraft:spawner\"}}}"))
			.result().orElseThrow();
	}

	@Test
	void spawnEggResolvesAsCropSoTheSeedSlotAcceptsIt() {
		final RecipeLookupCache<CropRecipe> crops = RecipeLookupCache.build(List.of());
		final RecipeLookupCache<SpawnEggCropRecipe> eggs = RecipeLookupCache.build(List.of(spawnEggRecipe()));

		assertTrue(AbstractPotMenu.cropResolves(crops, eggs, new ItemStack(Items.ZOMBIE_SPAWN_EGG)),
			"a spawn egg must resolve as a crop (placeable in the seed slot) via the generic mechanism");
		assertTrue(AbstractPotMenu.cropResolves(crops, eggs, new ItemStack(Items.CREEPER_SPAWN_EGG)),
			"any spawn egg resolves — the mechanism is not special-cased to one mob");
		assertFalse(AbstractPotMenu.cropResolves(crops, eggs, new ItemStack(Items.DIRT)),
			"a non-egg, non-crop item must not resolve as a crop");
	}

	@Test
	void spawnEggResolvesToAConcreteCropWithTheRecipeGrowTime() {
		// The client tooltip (Task B5) resolves the hovered seed through the SAME helper the menu/BE use,
		// so a spawn egg must yield a concrete CropRecipe (grow_time 6000) whose grow-time/yield lines the
		// tooltip can then render — not null (which would skip the crop tooltip branch, the reported bug).
		final RecipeLookupCache<CropRecipe> crops = RecipeLookupCache.build(List.of());
		final RecipeLookupCache<SpawnEggCropRecipe> eggs = RecipeLookupCache.build(List.of(spawnEggRecipe()));

		final CropRecipe resolved = AbstractPotMenu.resolveCrop(crops, eggs, new ItemStack(Items.ZOMBIE_SPAWN_EGG));
		assertNotNull(resolved, "a spawn egg must resolve to a concrete crop so its tooltip rates show");
		assertEquals(6000, resolved.growTime(), "the derived crop carries the recipe's grow_time");

		assertNull(AbstractPotMenu.resolveCrop(crops, eggs, new ItemStack(Items.DIRT)),
			"a non-egg, non-crop item resolves to no crop");
	}

	@Test
	void spawnerResolvesAsSoil() {
		final RecipeLookupCache<SoilRecipe> soils = RecipeLookupCache.build(List.of(spawnerSoilRecipe()));
		final ItemStack spawner = new ItemStack(Items.SPAWNER);

		assertNotNull(soils.lookup(spawner, SimplePotContext.ofSoil(spawner)),
			"minecraft:spawner must resolve as a soil so the soil slot accepts it");
	}
}
