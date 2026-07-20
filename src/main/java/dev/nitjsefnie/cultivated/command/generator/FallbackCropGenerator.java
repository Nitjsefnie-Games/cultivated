package dev.nitjsefnie.cultivated.command.generator;

import com.google.gson.JsonObject;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;

/**
 * Phase F.2 — the general-case crop generator: emits a {@code block_derived_crop} for any block,
 * with a default grow time and the soil chosen by {@link SoilAssignment}. This is the last generator
 * consulted so a more specific add-on generator can take precedence.
 */
public final class FallbackCropGenerator implements CropGenerator {
	/** Default grow time (ticks) for a suggested crop, tuned per-crop by hand afterwards. */
	public static final int DEFAULT_GROW_TIME = 1200;

	@Override
	public boolean supports(final Block block, final Identifier blockId) {
		return true;
	}

	@Override
	public JsonObject generate(final Block block, final Identifier blockId) {
		final JsonObject json = new JsonObject();
		json.addProperty("type", "cultivated:block_derived_crop");
		json.addProperty("block", blockId.toString());
		json.addProperty("grow_time", DEFAULT_GROW_TIME);
		SoilAssignment.forBlock(block, blockId).soilValue()
			.ifPresent(soil -> json.addProperty("soil", soil));
		return json;
	}
}
