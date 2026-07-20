package dev.nitjsefnie.cultivated.recipe;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonElement;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.MapCodec;
import dev.nitjsefnie.cultivated.CultivatedTestBootstrap;
import java.util.HashMap;
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
import java.io.Reader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeSet;
import net.minecraft.world.item.crafting.Recipe;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * The real content gate ({@code ./gradlew build} does NOT validate datapack JSON): loads EVERY
 * shipped {@code data/cultivated/recipe/**} JSON through the actual Phase A codecs
 * (soil, crop, block_derived_soil/crop, fertilizer, pot_interaction) and asserts zero parse
 * errors, and that every {@code loot_table} referenced by a drop provider resolves to a shipped file.
 */
class ShippedRecipesParseTest {
	/** Built once: a fully-populated lookup provider (built-in + vanilla dynamic registries, tags bound). */
	private static HolderLookup.Provider provider;

	@BeforeAll
	static void boot() {
		CultivatedTestBootstrap.bootstrap();
		provider = VanillaRegistries.createLookup();
		bindItemComponents();
		bindReferencedItemTags();
	}

	/**
	 * {@code Bootstrap.bootStrap()} freezes the registries but does not bind item data components —
	 * that happens on datapack load. {@code ItemStack.CODEC} (used by drop results and pot-interaction
	 * {@code new_soil}) rejects items whose components are unbound, so bind them here exactly as a
	 * server reload does ({@link net.minecraft.server.ReloadableServerResources}), using the vanilla
	 * lookup provider (which supplies the {@code damage_type} tags the fire-resistant items need).
	 */
	private static void bindItemComponents() {
		BuiltInRegistries.DATA_COMPONENT_INITIALIZERS.build(provider).forEach(DataComponentInitializers.PendingComponents::apply);
	}

	/**
	 * Bind the item tags the shipped recipes reference as ingredients (the {@code #cultivated:soil/*}
	 * soil tags plus {@code #minecraft:hoes}) — tags are otherwise unbound in a bootstrap-only test, so
	 * a {@code #tag} ingredient would fail to decode. The soil-tag contents are read from the shipped
	 * tag files so the test stays in sync with them.
	 */
	private static void bindReferencedItemTags() {
		final HashMap<TagKey<Item>, List<Holder<Item>>> tags = new HashMap<>();
		for (final String soil : List.of("dirt", "sand", "water", "mushroom")) {
			tags.put(itemTag("cultivated:soil/" + soil), soilTagItems(soil));
		}
		tags.put(itemTag("minecraft:hoes"), itemsFor(
			"minecraft:wooden_hoe", "minecraft:stone_hoe", "minecraft:iron_hoe",
			"minecraft:golden_hoe", "minecraft:diamond_hoe", "minecraft:netherite_hoe"));
		BuiltInRegistries.ITEM.prepareTagReload(new TagLoader.LoadResult<>(Registries.ITEM, tags)).apply();
	}

	private static TagKey<Item> itemTag(final String id) {
		return TagKey.create(Registries.ITEM, Identifier.parse(id));
	}

	private static List<Holder<Item>> itemsFor(final String... ids) {
		final List<Holder<Item>> holders = new ArrayList<>(ids.length);
		for (final String id : ids) {
			BuiltInRegistries.ITEM.getOptional(Identifier.parse(id))
				.ifPresent(item -> holders.add(item.builtInRegistryHolder()));
		}
		return holders;
	}

	/** Item ids from a shipped {@code tags/item/soil/<name>.json} file. */
	private static List<Holder<Item>> soilTagItems(final String name) {
		try {
			final Path tagFile = dataRoot().resolve("tags/item/soil/" + name + ".json");
			final JsonObject json = readJson(tagFile);
			final JsonArray values = json.getAsJsonArray("values");
			final List<String> ids = new ArrayList<>(values.size());
			for (final JsonElement value : values) {
				ids.add(value.getAsString());
			}
			return itemsFor(ids.toArray(new String[0]));
		} catch (final Exception failure) {
			throw new AssertionError("could not read soil tag " + name, failure);
		}
	}

	/**
	 * A registry-aware ops over the bootstrapped built-in registries. Vanilla {@code Ingredient} (and
	 * {@code ItemStack}/{@code BlockState}) codecs resolve items/blocks through holder lookups, so they
	 * require a {@link RegistryOps} — plain {@code JsonOps} would silently fail those fields.
	 */
	private static DynamicOps<JsonElement> registryOps() {
		return RegistryOps.create(JsonOps.INSTANCE, provider);
	}

	/**
	 * Maps each {@code cultivated:*} recipe {@code type} to the exact map-codec that decodes it. Built
	 * lazily (not as a static field) so it is created only after {@link #boot()} has bootstrapped the
	 * vanilla registries the codecs and their ingredients depend on.
	 */
	private static Map<String, MapCodec<? extends Recipe<?>>> codecsByType() {
		return Map.of(
			"cultivated:crop", CropRecipe.EXPLICIT_CODEC,
			"cultivated:block_derived_crop", BlockDerivedRecipes.CROP_CODEC,
			"cultivated:soil", SoilRecipe.EXPLICIT_CODEC,
			"cultivated:block_derived_soil", BlockDerivedRecipes.SOIL_CODEC,
			"cultivated:fertilizer", FertilizerRecipe.CODEC,
			"cultivated:pot_interaction", PotInteractionRecipe.CODEC
		);
	}

	/** The processed-resources {@code data/cultivated} root (build/resources/main on the classpath). */
	private static Path dataRoot() throws Exception {
		final URL url = ShippedRecipesParseTest.class.getResource("/data/cultivated");
		assertNotNull(url, "data/cultivated must be on the test classpath");
		return Path.of(url.toURI());
	}

	private static JsonObject readJson(final Path path) throws Exception {
		try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
			return JsonParser.parseReader(reader).getAsJsonObject();
		}
	}

	private static List<Path> recipeFiles() throws Exception {
		final Path recipeDir = dataRoot().resolve("recipe");
		assertTrue(Files.isDirectory(recipeDir), "recipe directory must exist: " + recipeDir);
		try (var stream = Files.walk(recipeDir)) {
			return stream
				.filter(Files::isRegularFile)
				.filter(p -> p.getFileName().toString().endsWith(".json"))
				.sorted()
				.toList();
		}
	}

	@Test
	void everyCultivatedRecipeParsesThroughItsCodec() throws Exception {
		final List<Path> files = recipeFiles();
		assertFalse(files.isEmpty(), "no recipe JSON files were found to validate");

		final Map<String, MapCodec<? extends Recipe<?>>> codecs = codecsByType();
		final DynamicOps<JsonElement> ops = registryOps();
		int parsed = 0;
		final List<String> failures = new ArrayList<>();
		for (final Path file : files) {
			final JsonObject json = readJson(file);
			assertTrue(json.has("type"), file + " has no \"type\" field");
			final String type = json.get("type").getAsString();
			if (!type.startsWith("cultivated:")) {
				continue; // e.g. the minecraft:crafting_shaped pot-upgrade recipes
			}
			final MapCodec<? extends Recipe<?>> codec = codecs.get(type);
			if (codec == null) {
				failures.add(file + " uses unknown cultivated recipe type: " + type);
				continue;
			}
			try {
				final DataResult<? extends Recipe<?>> result = codec.codec().parse(ops, json);
				if (result.error().isPresent()) {
					failures.add(file + " -> " + result.error().get().message());
					continue;
				}
			} catch (final RuntimeException thrown) {
				failures.add(file + " threw " + thrown);
				continue;
			}
			parsed++;
		}

		assertTrue(failures.isEmpty(), "shipped recipes failed to parse:\n" + String.join("\n", failures));
		assertTrue(parsed >= 50, "expected the curated content set (>=50 cultivated recipes); parsed " + parsed);
	}

	@Test
	void everyReferencedCultivatedLootTableExists() throws Exception {
		final Path root = dataRoot();
		final List<String> missing = new ArrayList<>();
		final TreeSet<String> checked = new TreeSet<>();

		for (final Path file : recipeFiles()) {
			final JsonObject json = readJson(file);
			final TreeSet<String> refs = new TreeSet<>();
			collectLootTableRefs(json, refs);
			for (final String ref : refs) {
				final int colon = ref.indexOf(':');
				final String namespace = colon < 0 ? "minecraft" : ref.substring(0, colon);
				if (!"cultivated".equals(namespace)) {
					continue; // only our own datapack files are shippable/checkable here
				}
				final String path = colon < 0 ? ref : ref.substring(colon + 1);
				final Path tableFile = root.resolve("loot_table").resolve(path + ".json");
				checked.add(ref);
				if (!Files.isRegularFile(tableFile)) {
					missing.add(file + " references missing loot table " + ref + " (expected " + tableFile + ")");
				}
			}
		}

		assertTrue(missing.isEmpty(), "referenced loot tables are missing:\n" + String.join("\n", missing));
		assertFalse(checked.isEmpty(), "expected at least one cultivated loot-table reference (trees)");
	}

	/** Recursively collect every {@code table_id}/{@code extra_drops} string value in the tree. */
	private static void collectLootTableRefs(final JsonElement element, final TreeSet<String> out) {
		if (element.isJsonObject()) {
			final JsonObject object = element.getAsJsonObject();
			for (final Entry<String, JsonElement> entry : object.entrySet()) {
				final String key = entry.getKey();
				final JsonElement value = entry.getValue();
				if (("table_id".equals(key) || "extra_drops".equals(key)) && value.isJsonPrimitive()) {
					out.add(value.getAsString());
				} else {
					collectLootTableRefs(value, out);
				}
			}
		} else if (element.isJsonArray()) {
			final JsonArray array = element.getAsJsonArray();
			for (final JsonElement child : array) {
				collectLootTableRefs(child, out);
			}
		}
	}
}
