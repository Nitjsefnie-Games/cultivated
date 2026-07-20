package dev.nitjsefnie.cultivated.command.generator;

import com.google.gson.JsonObject;
import dev.nitjsefnie.cultivated.registry.ModTags;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;

/**
 * Phase F.2 — the pluggable generator registry. Holds the ordered crop/soil generator lists the
 * {@code generate} debug commands consult (first {@code supports} match wins), so add-ons can prepend
 * their own generators ahead of the built-in fallbacks. The built-ins are the {@link TaggedSoilGenerator}s
 * for the fluid/special soils plus the {@link FallbackCropGenerator}/{@link FallbackSoilGenerator}
 * general cases, always registered last.
 */
public final class Generators {
	private static final List<CropGenerator> CROP_GENERATORS = new ArrayList<>();
	private static final List<SoilGenerator> SOIL_GENERATORS = new ArrayList<>();
	private static final CropGenerator FALLBACK_CROP = new FallbackCropGenerator();
	private static final SoilGenerator FALLBACK_SOIL = new FallbackSoilGenerator();

	static {
		// Built-in tagged fluid/special soils win for their tagged members, matched before the fallback.
		SOIL_GENERATORS.add(new TaggedSoilGenerator(ModTags.SOIL_WATER, "minecraft:water", true, 0.0, 0));
		SOIL_GENERATORS.add(new TaggedSoilGenerator(ModTags.SOIL_LAVA, "minecraft:lava", true, 0.2, 15));
		SOIL_GENERATORS.add(new TaggedSoilGenerator(ModTags.SOIL_SNOW, "minecraft:snow_block", false, 0.0, 0));
	}

	private Generators() {
	}

	/** Register a crop generator ahead of the built-in fallback (add-on hook). */
	public static void registerCrop(final CropGenerator generator) {
		CROP_GENERATORS.add(generator);
	}

	/** Register a soil generator ahead of the built-in fallback (add-on hook). */
	public static void registerSoil(final SoilGenerator generator) {
		SOIL_GENERATORS.add(generator);
	}

	/** The first registered crop generator that supports {@code block}, else the fallback. */
	public static JsonObject generateCrop(final Block block, final Identifier blockId) {
		for (final CropGenerator generator : CROP_GENERATORS) {
			if (generator.supports(block, blockId)) {
				return generator.generate(block, blockId);
			}
		}
		return FALLBACK_CROP.generate(block, blockId);
	}

	/** The first registered soil generator that supports {@code block}, else the fallback. */
	public static JsonObject generateSoil(final Block block, final Identifier blockId) {
		for (final SoilGenerator generator : SOIL_GENERATORS) {
			if (generator.supports(block, blockId)) {
				return generator.generate(block, blockId);
			}
		}
		return FALLBACK_SOIL.generate(block, blockId);
	}

	/** Expose whether any registered (non-fallback) soil generator claims {@code block}, for reporting. */
	public static Optional<SoilGenerator> soilGeneratorFor(final Block block, final Identifier blockId) {
		for (final SoilGenerator generator : SOIL_GENERATORS) {
			if (generator.supports(block, blockId)) {
				return Optional.of(generator);
			}
		}
		return Optional.empty();
	}
}
