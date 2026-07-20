package dev.nitjsefnie.cultivated.command.generator;

import com.google.gson.JsonObject;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;

/**
 * Phase F.2 — a pluggable generator that turns a block into a suggested soil recipe JSON (the
 * {@code missing soils generate} output). The core registers a {@link TaggedSoilGenerator} per
 * built-in fluid/special soil (water / lava / snow) plus a {@link FallbackSoilGenerator} for the
 * general block-derived case; add-ons may register their own.
 */
public interface SoilGenerator {
	/** True if this generator produces a soil recipe for {@code block}. */
	boolean supports(Block block, Identifier blockId);

	/** Build the suggested soil recipe JSON for {@code block}. */
	JsonObject generate(Block block, Identifier blockId);
}
