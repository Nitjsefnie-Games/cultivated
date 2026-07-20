package dev.nitjsefnie.cultivated.command.generator;

import com.google.gson.JsonObject;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;

/**
 * Phase F.2 — the general-case soil generator: emits a plain {@code block_derived_soil} for any
 * block with a small default growth modifier, to be tuned per-soil by hand afterwards. Consulted
 * last so the built-in {@link TaggedSoilGenerator}s (water/lava/snow) win for their tagged items.
 */
public final class FallbackSoilGenerator implements SoilGenerator {
	/** Default growth modifier for a suggested soil. */
	public static final double DEFAULT_GROWTH_MODIFIER = 0.1;

	@Override
	public boolean supports(final Block block, final Identifier blockId) {
		return true;
	}

	@Override
	public JsonObject generate(final Block block, final Identifier blockId) {
		final JsonObject json = new JsonObject();
		json.addProperty("type", "cultivated:block_derived_soil");
		json.addProperty("block", blockId.toString());
		json.addProperty("growth_modifier", DEFAULT_GROWTH_MODIFIER);
		return json;
	}
}
