package dev.nitjsefnie.cultivated.config;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.nitjsefnie.cultivated.CultivatedTestBootstrap;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Phase F (§F.1) — the commented-JSON config loader: write-defaults round-trips exactly, comments
 * are emitted and are tolerated on read, and a partial or malformed file falls back to defaults
 * per-field without throwing. The {@code default_harvest_stack} ItemStack is carried as raw JSON
 * (decoded lazily at use time, since item components are not bound this early), so its round-trip is
 * verified at the JSON level here; the decode-to-EMPTY default is also checked.
 */
class CultivatedConfigFileTest {

	@TempDir
	Path tempDir;

	private Snapshot original;

	@BeforeAll
	static void boot() {
		// ItemStack.OPTIONAL_CODEC (used by CultivatedConfig.defaultHarvestStack()) resolves ids
		// against BuiltInRegistries.
		CultivatedTestBootstrap.bootstrap();
	}

	@BeforeEach
	void capture() {
		original = Snapshot.capture();
	}

	@AfterEach
	void restore() {
		original.restore();
	}

	@Test
	void writeDefaultsThenReadBackEqualsDefaults() throws IOException {
		final Path file = tempDir.resolve("cultivated.json");
		CultivatedConfigFile.save(file);

		// Corrupt every in-memory value, then prove load() restores the written defaults.
		mutateEverything();
		CultivatedConfigFile.load(file);

		original.assertMatchesConfig();
	}

	@Test
	void savedFileEmitsComments() throws IOException {
		final Path file = tempDir.resolve("cultivated.json");
		CultivatedConfigFile.save(file);

		final String text = Files.readString(file, StandardCharsets.UTF_8);
		assertTrue(text.contains("//"), "file should contain comment lines");
		assertTrue(text.contains("\"global_growth_modifier\""), "file should contain a scalar key");
		assertTrue(text.contains("\"default_harvest_stack\""), "file should contain the item-stack key");
	}

	@Test
	void commentedFileIsReadable() throws IOException {
		final Path file = tempDir.resolve("cultivated.json");
		Files.writeString(file, """
			{
			  // a leading comment
			  "global_growth_modifier": 2.5, // trailing comment
			  // another
			  "pot_view_distance": 96.0
			}
			""", StandardCharsets.UTF_8);

		CultivatedConfig.globalGrowthModifier = 1.0f;
		CultivatedConfig.potViewDistance = 48.0;
		CultivatedConfigFile.load(file);

		assertEquals(2.5f, CultivatedConfig.globalGrowthModifier, 1.0e-6f);
		assertEquals(96.0, CultivatedConfig.potViewDistance, 1.0e-6);
	}

	@Test
	void partialFileKeepsDefaultsForMissingKeys() throws IOException {
		final Path file = tempDir.resolve("cultivated.json");
		Files.writeString(file, "{ \"can_wax_pots\": false }", StandardCharsets.UTF_8);

		CultivatedConfig.canWaxPots = true;
		CultivatedConfig.canCraftBasicPots = true;
		CultivatedConfig.megaSpeed = 10;
		CultivatedConfigFile.load(file);

		assertFalse(CultivatedConfig.canWaxPots, "present key applied");
		assertTrue(CultivatedConfig.canCraftBasicPots, "absent key keeps default");
		assertEquals(10, CultivatedConfig.megaSpeed, "absent key keeps default");
	}

	@Test
	void malformedFieldFallsBackPerField() throws IOException {
		final Path file = tempDir.resolve("cultivated.json");
		// global_growth_modifier is a string (bad), pot_view_distance is a valid number (good).
		Files.writeString(file, """
			{
			  "global_growth_modifier": "not-a-number",
			  "elite_speed": { "nested": true },
			  "pot_view_distance": 123.0
			}
			""", StandardCharsets.UTF_8);

		CultivatedConfig.globalGrowthModifier = 1.0f;
		CultivatedConfig.eliteSpeed = 2;
		CultivatedConfig.potViewDistance = 48.0;

		assertDoesNotThrow(() -> CultivatedConfigFile.load(file));

		assertEquals(1.0f, CultivatedConfig.globalGrowthModifier, 1.0e-6f, "bad field kept default");
		assertEquals(2, CultivatedConfig.eliteSpeed, "bad field kept default");
		assertEquals(123.0, CultivatedConfig.potViewDistance, 1.0e-6, "good field applied");
	}

	@Test
	void wholeFileMalformedKeepsAllDefaults() throws IOException {
		final Path file = tempDir.resolve("cultivated.json");
		Files.writeString(file, "this is not json at all {{{", StandardCharsets.UTF_8);

		CultivatedConfig.globalGrowthModifier = 3.0f;
		assertDoesNotThrow(() -> CultivatedConfigFile.load(file));
		assertEquals(3.0f, CultivatedConfig.globalGrowthModifier, 1.0e-6f, "nothing applied from garbage file");
	}

	@Test
	void defaultHarvestStackRawJsonRoundTrips() throws IOException {
		final Path file = tempDir.resolve("cultivated.json");
		final JsonElement wheat = JsonParser.parseString("{\"id\":\"minecraft:wheat\",\"count\":7}");
		CultivatedConfig.setDefaultHarvestStackJson(wheat);
		CultivatedConfigFile.save(file);

		// The item id must survive to the file verbatim.
		assertTrue(Files.readString(file, StandardCharsets.UTF_8).contains("minecraft:wheat"));

		CultivatedConfig.setDefaultHarvestStackJson(new JsonObject());
		CultivatedConfigFile.load(file);
		assertEquals(wheat, CultivatedConfig.defaultHarvestStackJson(), "raw JSON round-trips");
	}

	@Test
	void defaultHarvestStackDefaultsToEmptyStack() {
		CultivatedConfig.setDefaultHarvestStackJson(new JsonObject());
		assertTrue(CultivatedConfig.defaultHarvestStack().isEmpty(), "empty ({}) decodes to the empty stack");
	}

	private static void mutateEverything() {
		CultivatedConfig.globalGrowthModifier = -99.0f;
		CultivatedConfig.damageHarvestTool = false;
		CultivatedConfig.efficiencyGrowthModifier = -99.0f;
		CultivatedConfig.setDefaultHarvestStackJson(JsonParser.parseString("{\"id\":\"minecraft:diamond\",\"count\":5}"));
		CultivatedConfig.canCraftBasicPots = false;
		CultivatedConfig.canCraftHopperPots = false;
		CultivatedConfig.canWaxPots = false;
		CultivatedConfig.eliteSpeed = -1;
		CultivatedConfig.eliteOutput = -1;
		CultivatedConfig.ultraSpeed = -1;
		CultivatedConfig.ultraOutput = -1;
		CultivatedConfig.megaSpeed = -1;
		CultivatedConfig.megaOutput = -1;
		CultivatedConfig.canCraftEliteBasicPots = false;
		CultivatedConfig.canCraftEliteHopperPots = false;
		CultivatedConfig.canCraftEliteWaxedPots = false;
		CultivatedConfig.canCraftUltraBasicPots = false;
		CultivatedConfig.canCraftUltraHopperPots = false;
		CultivatedConfig.canCraftUltraWaxedPots = false;
		CultivatedConfig.canCraftMegaBasicPots = false;
		CultivatedConfig.canCraftMegaHopperPots = false;
		CultivatedConfig.canCraftMegaWaxedPots = false;
		CultivatedConfig.potViewDistance = -1.0;
		CultivatedConfig.useGrowthAnimation = false;
		CultivatedConfig.renderSoil = false;
		CultivatedConfig.renderCrop = false;
	}

	/** Immutable snapshot of every {@link CultivatedConfig} field, for save-round-trip and restore. */
	private record Snapshot(
			float globalGrowthModifier, boolean damageHarvestTool, float efficiencyGrowthModifier,
			JsonElement defaultHarvestStackJson,
			boolean canCraftBasicPots, boolean canCraftHopperPots, boolean canWaxPots,
			int eliteSpeed, int eliteOutput, int ultraSpeed, int ultraOutput, int megaSpeed, int megaOutput,
			boolean canCraftEliteBasicPots, boolean canCraftEliteHopperPots, boolean canCraftEliteWaxedPots,
			boolean canCraftUltraBasicPots, boolean canCraftUltraHopperPots, boolean canCraftUltraWaxedPots,
			boolean canCraftMegaBasicPots, boolean canCraftMegaHopperPots, boolean canCraftMegaWaxedPots,
			double potViewDistance, boolean useGrowthAnimation, boolean renderSoil, boolean renderCrop) {

		static Snapshot capture() {
			return new Snapshot(
				CultivatedConfig.globalGrowthModifier, CultivatedConfig.damageHarvestTool, CultivatedConfig.efficiencyGrowthModifier,
				CultivatedConfig.defaultHarvestStackJson(),
				CultivatedConfig.canCraftBasicPots, CultivatedConfig.canCraftHopperPots, CultivatedConfig.canWaxPots,
				CultivatedConfig.eliteSpeed, CultivatedConfig.eliteOutput, CultivatedConfig.ultraSpeed,
				CultivatedConfig.ultraOutput, CultivatedConfig.megaSpeed, CultivatedConfig.megaOutput,
				CultivatedConfig.canCraftEliteBasicPots, CultivatedConfig.canCraftEliteHopperPots, CultivatedConfig.canCraftEliteWaxedPots,
				CultivatedConfig.canCraftUltraBasicPots, CultivatedConfig.canCraftUltraHopperPots, CultivatedConfig.canCraftUltraWaxedPots,
				CultivatedConfig.canCraftMegaBasicPots, CultivatedConfig.canCraftMegaHopperPots, CultivatedConfig.canCraftMegaWaxedPots,
				CultivatedConfig.potViewDistance, CultivatedConfig.useGrowthAnimation, CultivatedConfig.renderSoil, CultivatedConfig.renderCrop);
		}

		void restore() {
			CultivatedConfig.globalGrowthModifier = globalGrowthModifier;
			CultivatedConfig.damageHarvestTool = damageHarvestTool;
			CultivatedConfig.efficiencyGrowthModifier = efficiencyGrowthModifier;
			CultivatedConfig.setDefaultHarvestStackJson(defaultHarvestStackJson);
			CultivatedConfig.canCraftBasicPots = canCraftBasicPots;
			CultivatedConfig.canCraftHopperPots = canCraftHopperPots;
			CultivatedConfig.canWaxPots = canWaxPots;
			CultivatedConfig.eliteSpeed = eliteSpeed;
			CultivatedConfig.eliteOutput = eliteOutput;
			CultivatedConfig.ultraSpeed = ultraSpeed;
			CultivatedConfig.ultraOutput = ultraOutput;
			CultivatedConfig.megaSpeed = megaSpeed;
			CultivatedConfig.megaOutput = megaOutput;
			CultivatedConfig.canCraftEliteBasicPots = canCraftEliteBasicPots;
			CultivatedConfig.canCraftEliteHopperPots = canCraftEliteHopperPots;
			CultivatedConfig.canCraftEliteWaxedPots = canCraftEliteWaxedPots;
			CultivatedConfig.canCraftUltraBasicPots = canCraftUltraBasicPots;
			CultivatedConfig.canCraftUltraHopperPots = canCraftUltraHopperPots;
			CultivatedConfig.canCraftUltraWaxedPots = canCraftUltraWaxedPots;
			CultivatedConfig.canCraftMegaBasicPots = canCraftMegaBasicPots;
			CultivatedConfig.canCraftMegaHopperPots = canCraftMegaHopperPots;
			CultivatedConfig.canCraftMegaWaxedPots = canCraftMegaWaxedPots;
			CultivatedConfig.potViewDistance = potViewDistance;
			CultivatedConfig.useGrowthAnimation = useGrowthAnimation;
			CultivatedConfig.renderSoil = renderSoil;
			CultivatedConfig.renderCrop = renderCrop;
		}

		void assertMatchesConfig() {
			assertEquals(globalGrowthModifier, CultivatedConfig.globalGrowthModifier, 1.0e-6f);
			assertEquals(damageHarvestTool, CultivatedConfig.damageHarvestTool);
			assertEquals(efficiencyGrowthModifier, CultivatedConfig.efficiencyGrowthModifier, 1.0e-6f);
			assertEquals(defaultHarvestStackJson, CultivatedConfig.defaultHarvestStackJson(), "default_harvest_stack JSON round-trips");
			assertEquals(canCraftBasicPots, CultivatedConfig.canCraftBasicPots);
			assertEquals(canCraftHopperPots, CultivatedConfig.canCraftHopperPots);
			assertEquals(canWaxPots, CultivatedConfig.canWaxPots);
			assertEquals(eliteSpeed, CultivatedConfig.eliteSpeed);
			assertEquals(eliteOutput, CultivatedConfig.eliteOutput);
			assertEquals(ultraSpeed, CultivatedConfig.ultraSpeed);
			assertEquals(ultraOutput, CultivatedConfig.ultraOutput);
			assertEquals(megaSpeed, CultivatedConfig.megaSpeed);
			assertEquals(megaOutput, CultivatedConfig.megaOutput);
			assertEquals(canCraftEliteBasicPots, CultivatedConfig.canCraftEliteBasicPots);
			assertEquals(canCraftEliteHopperPots, CultivatedConfig.canCraftEliteHopperPots);
			assertEquals(canCraftEliteWaxedPots, CultivatedConfig.canCraftEliteWaxedPots);
			assertEquals(canCraftUltraBasicPots, CultivatedConfig.canCraftUltraBasicPots);
			assertEquals(canCraftUltraHopperPots, CultivatedConfig.canCraftUltraHopperPots);
			assertEquals(canCraftUltraWaxedPots, CultivatedConfig.canCraftUltraWaxedPots);
			assertEquals(canCraftMegaBasicPots, CultivatedConfig.canCraftMegaBasicPots);
			assertEquals(canCraftMegaHopperPots, CultivatedConfig.canCraftMegaHopperPots);
			assertEquals(canCraftMegaWaxedPots, CultivatedConfig.canCraftMegaWaxedPots);
			assertEquals(potViewDistance, CultivatedConfig.potViewDistance, 1.0e-9);
			assertEquals(useGrowthAnimation, CultivatedConfig.useGrowthAnimation);
			assertEquals(renderSoil, CultivatedConfig.renderSoil);
			assertEquals(renderCrop, CultivatedConfig.renderCrop);
		}
	}
}
