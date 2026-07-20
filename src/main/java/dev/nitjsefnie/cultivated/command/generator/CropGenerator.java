package dev.nitjsefnie.cultivated.command.generator;

import com.google.gson.JsonObject;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;

/**
 * Phase F.2 — a pluggable generator that turns a growable block into a suggested
 * {@code block_derived_crop} recipe JSON (the {@code missing seeds generate} output). Add-ons can
 * register their own to customise grow time / soil / drops for their blocks; the core registers a
 * {@link FallbackCropGenerator} that handles the general case.
 */
public interface CropGenerator {
	/** True if this generator produces a crop recipe for {@code block}. */
	boolean supports(Block block, Identifier blockId);

	/** Build the suggested {@code block_derived_crop} JSON for {@code block}. */
	JsonObject generate(Block block, Identifier blockId);
}
