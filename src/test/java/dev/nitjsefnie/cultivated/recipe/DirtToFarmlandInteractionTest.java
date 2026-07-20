package dev.nitjsefnie.cultivated.recipe;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JsonOps;
import dev.nitjsefnie.cultivated.CultivatedTestBootstrap;
import dev.nitjsefnie.cultivated.cache.RecipeLookupCache;
import dev.nitjsefnie.cultivated.ingredient.CultivatedIngredient;
import dev.nitjsefnie.cultivated.registry.ModComponents;
import dev.nitjsefnie.cultivated.util.LazyItemStack;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Registry;
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
 * R2a regression guards for the {@code dirt_to_farmland} hoe pot-interaction, covering the exact
 * diagnosis that made it not fire in-game and the two data-shape suspects it was checked against:
 * <ul>
 *   <li>{@link #soilItemArrayMatchesDirtAndGrass()} — the {@code soil_item} JSON array parses to a
 *       {@link CultivatedIngredient} that matches both {@code dirt} and {@code grass_block};</li>
 *   <li>{@link #newSoilResolvesWithSoilOverride()} — the {@code new_soil} deferred stack, with item
 *       components bound, resolves to a non-empty stack carrying the {@code cultivated:soil} override
 *       whose growth modifier is {@code 0.10};</li>
 *   <li>{@link #firstMatchingConfirmsEachSharedHeldRecipe()} — the ACTUAL root cause: two hoe
 *       interactions sharing {@code #minecraft:hoes} must not shadow each other. A single-result
 *       {@code lookup} returns the first cheap-match (which fails its soil constraint on a dirt pot),
 *       while {@code firstMatching} confirms each candidate and returns the one that truly matches.</li>
 * </ul>
 */
class DirtToFarmlandInteractionTest {
	private static HolderLookup.Provider provider;

	@BeforeAll
	static void boot() throws Exception {
		CultivatedTestBootstrap.bootstrap();
		registerModComponents();
		provider = VanillaRegistries.createLookup();
		// Bind item data components (needed so ItemStack.CODEC can materialise new_soil), then the hoe tag.
		BuiltInRegistries.DATA_COMPONENT_INITIALIZERS.build(provider).forEach(DataComponentInitializers.PendingComponents::apply);
		final HashMap<TagKey<Item>, List<Holder<Item>>> tags = new HashMap<>();
		tags.put(TagKey.create(Registries.ITEM, Identifier.parse("minecraft:hoes")), List.of(
			Items.WOODEN_HOE.builtInRegistryHolder(), Items.STONE_HOE.builtInRegistryHolder(),
			Items.IRON_HOE.builtInRegistryHolder(), Items.GOLDEN_HOE.builtInRegistryHolder(),
			Items.DIAMOND_HOE.builtInRegistryHolder(), Items.NETHERITE_HOE.builtInRegistryHolder()));
		BuiltInRegistries.ITEM.prepareTagReload(new TagLoader.LoadResult<>(Registries.ITEM, tags)).apply();
	}

	/**
	 * Register the mod's data components into the (post-bootstrap, frozen) built-in registry — in-game
	 * these register during mod init BEFORE the freeze; a bootstrap-only test freezes first, so briefly
	 * unfreeze, register (idempotent), then re-freeze to bind the new holders. Without this the
	 * {@code cultivated:soil} override on new_soil would not decode.
	 */
	private static void registerModComponents() throws Exception {
		if (BuiltInRegistries.DATA_COMPONENT_TYPE.getKey(ModComponents.SOIL_OVERRIDE) != null) {
			return; // already registered in this JVM
		}
		final Registry<?> registry = BuiltInRegistries.DATA_COMPONENT_TYPE;
		final Field frozen = Class.forName("net.minecraft.core.MappedRegistry").getDeclaredField("frozen");
		frozen.setAccessible(true);
		frozen.setBoolean(registry, false);
		ModComponents.register();
		try {
			registry.freeze(); // binds the new holders (the post-freeze tag-state check then throws — harmless)
		} catch (final IllegalStateException expectedTagState) {
			// freeze() re-binds byValue holders (what we need) before its "tags already present" guard fires.
		}
	}

	private static DynamicOps<JsonElement> ops() {
		return RegistryOps.create(JsonOps.INSTANCE, provider);
	}

	private static JsonElement readShipped(final String path) throws Exception {
		try (InputStream in = DirtToFarmlandInteractionTest.class.getResourceAsStream(path)) {
			assertNotNull(in, "shipped resource must be on the test classpath: " + path);
			return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8));
		}
	}

	@Test
	void soilItemArrayMatchesDirtAndGrass() {
		final JsonElement json = JsonParser.parseString("[\"minecraft:dirt\", \"minecraft:grass_block\"]");
		final DataResult<CultivatedIngredient> result = CultivatedIngredient.CODEC.parse(ops(), json);
		assertTrue(result.error().isEmpty(), "soil_item array should parse: " + result.error().map(Object::toString).orElse(""));
		final CultivatedIngredient ingredient = result.result().orElseThrow();
		assertTrue(ingredient.test(new ItemStack(Items.DIRT)), "must match dirt");
		assertTrue(ingredient.test(new ItemStack(Items.GRASS_BLOCK)), "must match grass_block");
		assertFalse(ingredient.test(new ItemStack(Items.STONE)), "must not match an unrelated item");
	}

	@Test
	void newSoilResolvesWithSoilOverride() throws Exception {
		final JsonElement newSoil = readShipped("/data/cultivated/recipe/pot_interaction/dirt_to_farmland.json")
			.getAsJsonObject().get("new_soil");
		final DataResult<LazyItemStack> parsed = LazyItemStack.CODEC.parse(ops(), newSoil);
		assertTrue(parsed.error().isEmpty(), "new_soil should lazy-parse: " + parsed.error().map(Object::toString).orElse(""));
		final ItemStack resolved = parsed.result().orElseThrow().get();
		assertFalse(resolved.isEmpty(), "new_soil.get() must resolve to a non-empty stack, never EMPTY");
		assertSame(Items.DIRT, resolved.getItem(), "new_soil resolves to a dirt stack");
		final SoilRecipe override = resolved.get(ModComponents.SOIL_OVERRIDE);
		assertNotNull(override, "resolved stack must carry the cultivated:soil override component");
		assertTrue(Math.abs(override.growthModifier() - 0.10f) < 1.0e-6f,
			"soil override growth_modifier must be 0.10, was " + override.growthModifier());
	}

	@Test
	void firstMatchingConfirmsEachSharedHeldRecipe() throws Exception {
		final PotInteractionRecipe coarse = parseInteraction("coarse_dirt_to_dirt");
		final PotInteractionRecipe dirtToFarmland = parseInteraction("dirt_to_farmland");
		// coarse indexed FIRST so a single-result lookup would return it (and its coarse_dirt soil check
		// would fail on a dirt pot, shadowing dirt_to_farmland).
		final RecipeLookupCache<PotInteractionRecipe> cache = RecipeLookupCache.build(List.of(coarse, dirtToFarmland));

		final ItemStack hoe = new ItemStack(Items.DIAMOND_HOE);
		final SimplePotContext dirtPot = new SimplePotContext(
			new ItemStack(Items.DIRT), ItemStack.EMPTY, ItemStack.EMPTY, hoe, null, true, 0);

		assertSame(coarse, cache.lookup(hoe, dirtPot),
			"single-result lookup returns the first cheap-matching hoe recipe (the shadowing one)");
		final PotInteractionRecipe resolved = cache.firstMatching(hoe, dirtPot, null);
		assertSame(dirtToFarmland, resolved,
			"firstMatching must skip the non-matching coarse_dirt recipe and return dirt_to_farmland");
		assertNotSame(coarse, resolved, "the shadowing recipe must not be chosen for a dirt pot");
	}

	private static PotInteractionRecipe parseInteraction(final String name) throws Exception {
		final JsonElement json = readShipped("/data/cultivated/recipe/pot_interaction/" + name + ".json");
		final DataResult<PotInteractionRecipe> result = PotInteractionRecipe.CODEC.codec().parse(ops(), json);
		assertTrue(result.error().isEmpty(), name + " should parse: " + result.error().map(Object::toString).orElse(""));
		return result.result().orElseThrow();
	}
}
