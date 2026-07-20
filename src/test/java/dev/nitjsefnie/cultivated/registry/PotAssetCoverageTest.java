package dev.nitjsefnie.cultivated.registry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.nitjsefnie.cultivated.block.PotMaterials;
import dev.nitjsefnie.cultivated.block.PotType;
import dev.nitjsefnie.cultivated.block.Tier;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

/**
 * Task B6 — headless completeness check for the pot client assets. {@code ./gradlew build} does not
 * validate client asset JSON, so this test asserts (from the classpath copy of
 * {@code src/main/resources}) that every registered pot variant ({@link PotMaterials#ALL} ×
 * {@link PotType}) has a blockstate, a block model, and an item model, that each of those references a
 * model file that actually exists, that a lang entry is present, and that the shared templates and GUI
 * textures Task B3 referenced are shipped. It does NOT verify in-game rendering (deferred).
 */
class PotAssetCoverageTest {
	private static final String ASSETS = "/assets/cultivated/";
	private static final String LOOT = "/data/cultivated/loot_table/blocks/";
	private static final int EXPECTED_VARIANTS = 61 * 3;
	/** Phase D §D — elite/ultra/mega × 61 materials × 3 pot types. */
	private static final int EXPECTED_TIERED_VARIANTS = 3 * 61 * 3;

	/** The tiers that prefix an id ({@link Tier#BASE} carries no prefix and is covered separately). */
	private static final Tier[] TIERS = {Tier.ELITE, Tier.ULTRA, Tier.MEGA};

	/** The registry id of a pot variant for {@code material}/{@code type} under {@code tier}. */
	private static String variantName(final Tier tier, final String material, final PotType type) {
		final String base = PotMaterials.potBlockName(material, type);
		return tier.isBase() ? base : tier.idPrefix() + "_" + base;
	}

	private static boolean exists(final String resource) {
		try (InputStream in = PotAssetCoverageTest.class.getResourceAsStream(resource)) {
			return in != null;
		} catch (final Exception e) {
			return false;
		}
	}

	private static JsonObject readJson(final String resource) {
		try (InputStream in = PotAssetCoverageTest.class.getResourceAsStream(resource)) {
			assertNotNull(in, "missing asset: " + resource);
			return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
		} catch (final Exception e) {
			throw new AssertionError("could not read " + resource, e);
		}
	}

	/** Turn a {@code cultivated:block/foo} model id into its classpath resource path. */
	private static String modelResource(final String modelId) {
		assertTrue(modelId.startsWith("cultivated:"), "unexpected namespace in model id: " + modelId);
		return ASSETS + "models/" + modelId.substring("cultivated:".length()) + ".json";
	}

	@Test
	void everyVariantHasBlockstateModelAndItem() {
		int checked = 0;
		final JsonObject lang = readJson(ASSETS + "lang/en_us.json");
		for (final String material : PotMaterials.ALL) {
			for (final PotType type : PotType.values()) {
				final String name = PotMaterials.potBlockName(material, type);

				// Blockstate present, single default variant, referenced model exists.
				final JsonObject blockstate = readJson(ASSETS + "blockstates/" + name + ".json");
				final JsonObject variants = blockstate.getAsJsonObject("variants");
				assertTrue(variants.has(""), "blockstate " + name + " lacks a default \"\" variant");
				final String bsModel = variants.getAsJsonObject("").get("model").getAsString();
				assertTrue(exists(modelResource(bsModel)),
					"blockstate " + name + " references missing model " + bsModel);

				// Item definition present, referenced model exists.
				final JsonObject item = readJson(ASSETS + "items/" + name + ".json");
				final String itemModel = item.getAsJsonObject("model").get("model").getAsString();
				assertTrue(exists(modelResource(itemModel)),
					"item " + name + " references missing model " + itemModel);

				// The variant's own block model file exists.
				assertTrue(exists(ASSETS + "models/block/" + name + ".json"),
					"missing block model for " + name);

				// Lang entry present (item uses the block description prefix -> block.cultivated.<name>).
				assertTrue(lang.has("block.cultivated." + name),
					"missing lang entry block.cultivated." + name);

				checked++;
			}
		}
		assertEquals(EXPECTED_VARIANTS, checked);
	}

	@Test
	void basicAndHopperModelsDeclareCutoutAndParentTemplates() {
		for (final String material : PotMaterials.ALL) {
			final JsonObject basic = readJson(ASSETS + "models/block/" + material + "_botany_pot.json");
			assertEquals("minecraft:cutout", basic.get("render_type").getAsString(),
				material + " basic model missing cutout render_type");
			assertEquals("cultivated:block/template/pot", basic.get("parent").getAsString());

			final JsonObject hopper = readJson(ASSETS + "models/block/" + material + "_hopper_botany_pot.json");
			assertEquals("minecraft:cutout", hopper.get("render_type").getAsString(),
				material + " hopper model missing cutout render_type");
			assertEquals("cultivated:block/template/hopper_pot", hopper.get("parent").getAsString());

			// Waxed reuses the basic model by parenting it, and still declares cutout explicitly.
			final JsonObject waxed = readJson(ASSETS + "models/block/" + material + "_waxed_botany_pot.json");
			assertEquals("cultivated:block/" + material + "_botany_pot", waxed.get("parent").getAsString());
			assertEquals("minecraft:cutout", waxed.get("render_type").getAsString(),
				material + " waxed model missing cutout render_type");
		}
	}

	@Test
	void sharedTemplatesAndGuiTexturesArePresent() {
		assertTrue(exists(ASSETS + "models/block/template/pot.json"), "missing pot template");
		assertTrue(exists(ASSETS + "models/block/template/hopper_pot.json"), "missing hopper_pot template");
		assertTrue(exists(ASSETS + "textures/gui/container/basic_pot.png"), "missing basic_pot background");
		assertTrue(exists(ASSETS + "textures/gui/container/hopper_pot.png"), "missing hopper_pot background");
		assertTrue(exists(ASSETS + "textures/gui/sprites/container/slot/soil.png"), "missing soil slot sprite");
		assertTrue(exists(ASSETS + "textures/gui/sprites/container/slot/seed.png"), "missing seed slot sprite");
		assertTrue(exists(ASSETS + "textures/gui/sprites/container/slot/hoe.png"), "missing hoe slot sprite");
	}

	@Test
	void creativeTabLangEntryPresent() {
		final JsonObject lang = readJson(ASSETS + "lang/en_us.json");
		assertTrue(lang.has("itemGroup.cultivated.botany_pots"), "missing creative tab lang entry");
	}

	// ---- Phase D §D: tiered pot coverage (elite/ultra/mega × 61 materials × 3 pot types = 549) ----

	@Test
	void everyTieredVariantHasBlockstateModelAndItem() {
		int checked = 0;
		final JsonObject lang = readJson(ASSETS + "lang/en_us.json");
		for (final Tier tier : TIERS) {
			for (final String material : PotMaterials.ALL) {
				for (final PotType type : PotType.values()) {
					final String name = variantName(tier, material, type);

					final JsonObject blockstate = readJson(ASSETS + "blockstates/" + name + ".json");
					final JsonObject variants = blockstate.getAsJsonObject("variants");
					assertTrue(variants.has(""), "blockstate " + name + " lacks a default \"\" variant");
					final String bsModel = variants.getAsJsonObject("").get("model").getAsString();
					assertTrue(exists(modelResource(bsModel)),
						"blockstate " + name + " references missing model " + bsModel);

					final JsonObject item = readJson(ASSETS + "items/" + name + ".json");
					final String itemModel = item.getAsJsonObject("model").get("model").getAsString();
					assertTrue(exists(modelResource(itemModel)),
						"item " + name + " references missing model " + itemModel);

					assertTrue(exists(ASSETS + "models/block/" + name + ".json"),
						"missing block model for " + name);

					assertTrue(lang.has("block.cultivated." + name),
						"missing lang entry block.cultivated." + name);

					checked++;
				}
			}
		}
		assertEquals(EXPECTED_TIERED_VARIANTS, checked);
	}

	@Test
	void tieredModelsDeclareCutoutAndParentTemplates() {
		for (final Tier tier : TIERS) {
			for (final String material : PotMaterials.ALL) {
				final String basicName = variantName(tier, material, PotType.BASIC);
				final JsonObject basic = readJson(ASSETS + "models/block/" + basicName + ".json");
				assertEquals("minecraft:cutout", basic.get("render_type").getAsString(),
					basicName + " basic model missing cutout render_type");
				assertEquals("cultivated:block/template/pot", basic.get("parent").getAsString());

				final String hopperName = variantName(tier, material, PotType.HOPPER);
				final JsonObject hopper = readJson(ASSETS + "models/block/" + hopperName + ".json");
				assertEquals("minecraft:cutout", hopper.get("render_type").getAsString(),
					hopperName + " hopper model missing cutout render_type");
				assertEquals("cultivated:block/template/hopper_pot", hopper.get("parent").getAsString());

				// Waxed reuses the tier's basic model by parenting it, and still declares cutout explicitly.
				final String waxedName = variantName(tier, material, PotType.WAXED);
				final JsonObject waxed = readJson(ASSETS + "models/block/" + waxedName + ".json");
				assertEquals("cultivated:block/" + basicName, waxed.get("parent").getAsString());
				assertEquals("minecraft:cutout", waxed.get("render_type").getAsString(),
					waxedName + " waxed model missing cutout render_type");
			}
		}
	}

	@Test
	void everyVariantHasSelfDropLootTable() {
		int checked = 0;
		final Tier[] all = {Tier.BASE, Tier.ELITE, Tier.ULTRA, Tier.MEGA};
		for (final Tier tier : all) {
			for (final String material : PotMaterials.ALL) {
				for (final PotType type : PotType.values()) {
					final String name = variantName(tier, material, type);
					final JsonObject loot = readJson(LOOT + name + ".json");
					assertEquals("minecraft:block", loot.get("type").getAsString(),
						name + " loot table is not a block table");
					final String dropId = loot.getAsJsonArray("pools").get(0).getAsJsonObject()
						.getAsJsonArray("entries").get(0).getAsJsonObject().get("name").getAsString();
					assertEquals("cultivated:" + name, dropId, name + " loot table does not self-drop");
					checked++;
				}
			}
		}
		assertEquals(EXPECTED_VARIANTS + EXPECTED_TIERED_VARIANTS, checked);
	}
}
