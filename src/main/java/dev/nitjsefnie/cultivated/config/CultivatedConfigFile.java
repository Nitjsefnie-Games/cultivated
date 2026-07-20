package dev.nitjsefnie.cultivated.config;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.google.gson.stream.JsonReader;
import dev.nitjsefnie.cultivated.Cultivated;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.DoubleSupplier;
import java.util.function.IntSupplier;
import java.util.function.Supplier;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Phase F (§F.1) — a minimal, in-mod (no external annotation library) loader that backs
 * {@link CultivatedConfig} with a commented JSON file at the Fabric config directory.
 *
 * <p>The file is a JSON object annotated with {@code //} comment lines describing each field. On
 * load, present keys overwrite the matching {@code CultivatedConfig} static field; any key that is
 * absent, wrong-typed, or otherwise unparseable is skipped so that field keeps its default — a
 * malformed or partial file therefore degrades per-field instead of crashing. When the file does
 * not exist, {@link #loadOrCreate()} writes the current defaults (with comments) so behaviour with
 * no file on disk is identical to the built-in defaults.
 */
public final class CultivatedConfigFile {
	/** File name under {@link FabricLoader#getConfigDir()}. */
	public static final String FILE_NAME = "cultivated.json";

	private static final Gson GSON = new Gson();

	private CultivatedConfigFile() {
	}

	/**
	 * Resolve the config file, loading it if present or writing the defaults if absent. Must run
	 * early in {@code onInitialize()}, before any config value is first read. Never throws: I/O and
	 * parse failures are logged and leave the in-memory defaults untouched.
	 */
	public static void loadOrCreate() {
		final Path path = FabricLoader.getInstance().getConfigDir().resolve(FILE_NAME);
		try {
			if (Files.exists(path)) {
				load(path);
			} else {
				save(path);
			}
		} catch (final IOException e) {
			Cultivated.LOGGER.warn("Could not access Cultivated config at {}; using defaults", path, e);
		}
	}

	/**
	 * Read {@code path} and apply every recognised, well-formed field onto {@link CultivatedConfig}.
	 * A whole-file parse failure leaves all defaults in place; a single bad field leaves only that
	 * field at its default.
	 */
	public static void load(final Path path) throws IOException {
		final String text = Files.readString(path, StandardCharsets.UTF_8);
		final JsonObject root;
		try {
			// Lenient reading tolerates the // comments we emit as well as trailing commas.
			final JsonReader reader = new JsonReader(new StringReader(text));
			reader.setLenient(true);
			final JsonElement parsed = JsonParser.parseReader(reader);
			if (!parsed.isJsonObject()) {
				Cultivated.LOGGER.warn("Cultivated config at {} is not a JSON object; using defaults", path);
				return;
			}
			root = parsed.getAsJsonObject();
		} catch (final RuntimeException e) {
			Cultivated.LOGGER.warn("Cultivated config at {} is malformed; using defaults", path, e);
			return;
		}

		for (final ConfigField field : FIELDS) {
			if (!root.has(field.key())) {
				continue;
			}
			try {
				field.decode().accept(root.get(field.key()));
			} catch (final RuntimeException e) {
				Cultivated.LOGGER.warn("Cultivated config: bad value for '{}', keeping default", field.key(), e);
			}
		}
	}

	/** Write the current {@link CultivatedConfig} values to {@code path} as commented JSON. */
	public static void save(final Path path) throws IOException {
		final Path parent = path.getParent();
		if (parent != null) {
			Files.createDirectories(parent);
		}
		Files.writeString(path, render(), StandardCharsets.UTF_8);
	}

	/** Build the commented-JSON document for the current config values. */
	static String render() {
		final StringBuilder sb = new StringBuilder();
		sb.append("{\n");
		for (int i = 0; i < FIELDS.size(); i++) {
			final ConfigField field = FIELDS.get(i);
			if (field.section() != null) {
				sb.append("\n  // ==== ").append(field.section()).append(" ====\n");
			}
			for (final String commentLine : field.comment().split("\n", -1)) {
				sb.append("  // ").append(commentLine).append('\n');
			}
			sb.append("  \"").append(field.key()).append("\": ").append(GSON.toJson(field.encode().get()));
			sb.append(i == FIELDS.size() - 1 ? "\n" : ",\n");
		}
		sb.append("}\n");
		return sb.toString();
	}

	// --- Field registry ---------------------------------------------------------------------------

	/**
	 * One config field: how to serialize its current value to a JSON element and how to apply a
	 * parsed element back onto {@link CultivatedConfig}. {@code section}, when non-null, emits a
	 * header comment before this field on write.
	 */
	private record ConfigField(String section, String comment, String key,
			Supplier<JsonElement> encode, Consumer<JsonElement> decode) {
	}

	private static ConfigField withSection(final String section, final ConfigField field) {
		return new ConfigField(section, field.comment(), field.key(), field.encode(), field.decode());
	}

	private static ConfigField bool(final String key, final String comment,
			final BooleanSupplier getter, final Consumer<Boolean> setter) {
		return new ConfigField(null, comment, key,
			() -> new JsonPrimitive(getter.getAsBoolean()),
			e -> setter.accept(e.getAsBoolean()));
	}

	private static ConfigField integer(final String key, final String comment,
			final IntSupplier getter, final Consumer<Integer> setter) {
		return new ConfigField(null, comment, key,
			() -> new JsonPrimitive(getter.getAsInt()),
			e -> setter.accept(e.getAsInt()));
	}

	private static ConfigField floating(final String key, final String comment,
			final FloatSupplier getter, final Consumer<Float> setter) {
		return new ConfigField(null, comment, key,
			() -> new JsonPrimitive(getter.getAsFloat()),
			e -> setter.accept(e.getAsFloat()));
	}

	private static ConfigField doubling(final String key, final String comment,
			final DoubleSupplier getter, final Consumer<Double> setter) {
		return new ConfigField(null, comment, key,
			() -> new JsonPrimitive(getter.getAsDouble()),
			e -> setter.accept(e.getAsDouble()));
	}

	/**
	 * An ItemStack field carried as raw JSON. The stack is not decoded here — see
	 * {@link CultivatedConfig#defaultHarvestStack()} for why decoding is deferred — so the loader
	 * simply round-trips the JSON element the user wrote (or the default).
	 */
	private static ConfigField rawStack(final String key, final String comment,
			final Supplier<JsonElement> getter, final Consumer<JsonElement> setter) {
		return new ConfigField(null, comment, key, getter, setter);
	}

	@FunctionalInterface
	private interface FloatSupplier {
		float getAsFloat();
	}

	private static ConfigField tierGate(final String tier, final String type,
			final BooleanSupplier getter, final Consumer<Boolean> setter) {
		final String key = "can_craft_" + tier + "_" + type + "_pots";
		return bool(key, "Allow crafting the " + tier + " " + type + " pot.", getter, setter);
	}

	private static final List<ConfigField> FIELDS = List.of(
		withSection("Gameplay",
			floating("global_growth_modifier", "Global multiplier on growth speed for every pot.",
				() -> CultivatedConfig.globalGrowthModifier, v -> CultivatedConfig.globalGrowthModifier = v)),
		bool("damage_harvest_tool", "Whether harvesting a crop with a tool damages that tool.",
			() -> CultivatedConfig.damageHarvestTool, v -> CultivatedConfig.damageHarvestTool = v),
		floating("efficiency_growth_modifier", "Growth speed added per level of a pot-growth enchantment.",
			() -> CultivatedConfig.efficiencyGrowthModifier, v -> CultivatedConfig.efficiencyGrowthModifier = v),
		rawStack("default_harvest_stack", "Fallback item stack a pot yields when a crop defines no drops.\nJSON ItemStack, e.g. { \"id\": \"minecraft:wheat\", \"count\": 1 }; empty ({}) means none.",
			CultivatedConfig::defaultHarvestStackJson, CultivatedConfig::setDefaultHarvestStackJson),

		withSection("Crafting gates",
			bool("can_craft_basic_pots", "Allow crafting basic botany pots.",
				() -> CultivatedConfig.canCraftBasicPots, v -> CultivatedConfig.canCraftBasicPots = v)),
		bool("can_craft_hopper_pots", "Allow crafting hopper botany pots.",
			() -> CultivatedConfig.canCraftHopperPots, v -> CultivatedConfig.canCraftHopperPots = v),
		bool("can_wax_pots", "Allow waxing botany pots.",
			() -> CultivatedConfig.canWaxPots, v -> CultivatedConfig.canWaxPots = v),

		withSection("Tier modifiers (additive: speed into the growth divisor, output into total yield)",
			integer("elite_speed", "Elite tier growth-speed bonus.",
				() -> CultivatedConfig.eliteSpeed, v -> CultivatedConfig.eliteSpeed = v)),
		integer("elite_output", "Elite tier yield bonus.",
			() -> CultivatedConfig.eliteOutput, v -> CultivatedConfig.eliteOutput = v),
		integer("ultra_speed", "Ultra tier growth-speed bonus.",
			() -> CultivatedConfig.ultraSpeed, v -> CultivatedConfig.ultraSpeed = v),
		integer("ultra_output", "Ultra tier yield bonus.",
			() -> CultivatedConfig.ultraOutput, v -> CultivatedConfig.ultraOutput = v),
		integer("mega_speed", "Mega tier growth-speed bonus.",
			() -> CultivatedConfig.megaSpeed, v -> CultivatedConfig.megaSpeed = v),
		integer("mega_output", "Mega tier yield bonus.",
			() -> CultivatedConfig.megaOutput, v -> CultivatedConfig.megaOutput = v),

		withSection("Per-tier crafting gates",
			tierGate("elite", "basic", () -> CultivatedConfig.canCraftEliteBasicPots, v -> CultivatedConfig.canCraftEliteBasicPots = v)),
		tierGate("elite", "hopper", () -> CultivatedConfig.canCraftEliteHopperPots, v -> CultivatedConfig.canCraftEliteHopperPots = v),
		tierGate("elite", "waxed", () -> CultivatedConfig.canCraftEliteWaxedPots, v -> CultivatedConfig.canCraftEliteWaxedPots = v),
		tierGate("ultra", "basic", () -> CultivatedConfig.canCraftUltraBasicPots, v -> CultivatedConfig.canCraftUltraBasicPots = v),
		tierGate("ultra", "hopper", () -> CultivatedConfig.canCraftUltraHopperPots, v -> CultivatedConfig.canCraftUltraHopperPots = v),
		tierGate("ultra", "waxed", () -> CultivatedConfig.canCraftUltraWaxedPots, v -> CultivatedConfig.canCraftUltraWaxedPots = v),
		tierGate("mega", "basic", () -> CultivatedConfig.canCraftMegaBasicPots, v -> CultivatedConfig.canCraftMegaBasicPots = v),
		tierGate("mega", "hopper", () -> CultivatedConfig.canCraftMegaHopperPots, v -> CultivatedConfig.canCraftMegaHopperPots = v),
		tierGate("mega", "waxed", () -> CultivatedConfig.canCraftMegaWaxedPots, v -> CultivatedConfig.canCraftMegaWaxedPots = v),

		withSection("Visuals",
			doubling("pot_view_distance", "Maximum distance (blocks) at which pot contents render.",
				() -> CultivatedConfig.potViewDistance, v -> CultivatedConfig.potViewDistance = v)),
		bool("use_growth_animation", "Animate crops as they grow instead of snapping between stages.",
			() -> CultivatedConfig.useGrowthAnimation, v -> CultivatedConfig.useGrowthAnimation = v),
		bool("render_soil", "Render the soil block inside pots.",
			() -> CultivatedConfig.renderSoil, v -> CultivatedConfig.renderSoil = v),
		bool("render_crop", "Render the growing crop inside pots.",
			() -> CultivatedConfig.renderCrop, v -> CultivatedConfig.renderCrop = v)
	);
}
