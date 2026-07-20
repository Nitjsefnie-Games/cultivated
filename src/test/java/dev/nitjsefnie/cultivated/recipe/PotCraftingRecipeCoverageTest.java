package dev.nitjsefnie.cultivated.recipe;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.nitjsefnie.cultivated.block.PotMaterials;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Ships-with-the-jar completeness check for the base-pot crafting recipes ({@code ./gradlew build}
 * does not validate datapack JSON). For every {@link PotMaterials#ALL material} it asserts the four
 * generated recipe files exist under {@code data/cultivated/recipe/pot/}, are the expected vanilla
 * {@code crafting_shaped}/{@code crafting_shapeless} type, carry the correct ingredients / result id,
 * and are gated by the exact {@code cultivated:config} property registered in {@code CultivatedConfig}.
 *
 * <p>Only BASE pots are craftable; tiered pots come from the upgrade items, so no tiered recipes are
 * expected (and none are asserted here).
 */
class PotCraftingRecipeCoverageTest {
	private static final String RECIPES = "/data/cultivated/recipe/pot/";

	private static final String GATE_BASIC = "can_craft_basic_pots";
	private static final String GATE_HOPPER = "can_craft_hopper_pots";
	private static final String GATE_WAXED = "can_wax_pots";

	private static JsonObject readJson(final String resource) {
		try (InputStream in = PotCraftingRecipeCoverageTest.class.getResourceAsStream(resource)) {
			assertNotNull(in, "missing recipe: " + resource);
			return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
		} catch (final Exception e) {
			throw new AssertionError("could not read " + resource, e);
		}
	}

	private static String type(final JsonObject recipe) {
		return recipe.get("type").getAsString();
	}

	private static String resultId(final JsonObject recipe) {
		return recipe.getAsJsonObject("result").get("id").getAsString();
	}

	private static String gate(final JsonObject recipe) {
		final JsonArray conditions = recipe.getAsJsonArray("fabric:load_conditions");
		assertEquals(1, conditions.size(), "expected exactly one load condition");
		final JsonObject condition = conditions.get(0).getAsJsonObject();
		assertEquals("cultivated:config", condition.get("condition").getAsString());
		return condition.get("property").getAsString();
	}

	private static List<String> patternRows(final JsonObject recipe) {
		final JsonArray pattern = recipe.getAsJsonArray("pattern");
		final List<String> rows = new ArrayList<>(pattern.size());
		for (final JsonElement row : pattern) {
			rows.add(row.getAsString());
		}
		return rows;
	}

	private static List<String> ingredients(final JsonObject recipe) {
		final JsonArray array = recipe.getAsJsonArray("ingredients");
		final List<String> items = new ArrayList<>(array.size());
		for (final JsonElement element : array) {
			items.add(element.getAsString());
		}
		return items;
	}

	@Test
	void everyBaseMaterialHasTheFourExpectedRecipes() {
		int checked = 0;
		for (final String material : PotMaterials.ALL) {
			final String matItem = "minecraft:" + material;
			final String basicId = "cultivated:" + material + "_botany_pot";
			final String hopperId = "cultivated:" + material + "_hopper_botany_pot";
			final String waxedId = "cultivated:" + material + "_waxed_botany_pot";

			// Basic: shaped material ring around a flower pot.
			final JsonObject basic = readJson(RECIPES + material + "_botany_pot.json");
			assertEquals("minecraft:crafting_shaped", type(basic), material + " basic wrong type");
			assertEquals(List.of("M M", "MPM", " M "), patternRows(basic), material + " basic wrong pattern");
			assertEquals(matItem, basic.getAsJsonObject("key").get("M").getAsString(), material + " basic M");
			assertEquals("minecraft:flower_pot", basic.getAsJsonObject("key").get("P").getAsString(),
				material + " basic P");
			assertEquals(basicId, resultId(basic), material + " basic result");
			assertEquals(GATE_BASIC, gate(basic), material + " basic gate");

			// Hopper (a): shapeless hopper + basic pot.
			final JsonObject hopper = readJson(RECIPES + material + "_hopper_botany_pot.json");
			assertEquals("minecraft:crafting_shapeless", type(hopper), material + " hopper wrong type");
			assertEquals(List.of("minecraft:hopper", basicId), ingredients(hopper),
				material + " hopper shapeless ingredients");
			assertEquals(hopperId, resultId(hopper), material + " hopper result");
			assertEquals(GATE_HOPPER, gate(hopper), material + " hopper gate");

			// Hopper (b): "quick" shaped variant with a hopper on top.
			final JsonObject hopperQuick = readJson(RECIPES + material + "_hopper_botany_pot_quick.json");
			assertEquals("minecraft:crafting_shaped", type(hopperQuick), material + " hopper quick wrong type");
			assertEquals(List.of("MHM", "MPM", " M "), patternRows(hopperQuick),
				material + " hopper quick wrong pattern");
			assertEquals(matItem, hopperQuick.getAsJsonObject("key").get("M").getAsString(),
				material + " hopper quick M");
			assertEquals("minecraft:hopper", hopperQuick.getAsJsonObject("key").get("H").getAsString(),
				material + " hopper quick H");
			assertEquals("minecraft:flower_pot", hopperQuick.getAsJsonObject("key").get("P").getAsString(),
				material + " hopper quick P");
			assertEquals(hopperId, resultId(hopperQuick), material + " hopper quick result");
			assertEquals(GATE_HOPPER, gate(hopperQuick), material + " hopper quick gate");

			// Waxed: shapeless honeycomb + basic pot.
			final JsonObject waxed = readJson(RECIPES + material + "_waxed_botany_pot.json");
			assertEquals("minecraft:crafting_shapeless", type(waxed), material + " waxed wrong type");
			assertEquals(List.of("minecraft:honeycomb", basicId), ingredients(waxed),
				material + " waxed shapeless ingredients");
			assertEquals(waxedId, resultId(waxed), material + " waxed result");
			assertEquals(GATE_WAXED, gate(waxed), material + " waxed gate");

			checked++;
		}
		assertEquals(61, checked, "expected all 61 base materials covered");
		assertEquals(61, PotMaterials.ALL.size(), "PotMaterials.ALL should list 61 materials");
	}
}
