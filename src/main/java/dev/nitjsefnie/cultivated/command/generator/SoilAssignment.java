package dev.nitjsefnie.cultivated.command.generator;

import java.util.Optional;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.BaseCoralPlantTypeBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CactusBlock;
import net.minecraft.world.level.block.LilyPadBlock;
import net.minecraft.world.level.block.MushroomBlock;

/**
 * Phase F.2 — the pure soil-assignment decision a crop generator applies when deriving a
 * {@code block_derived_crop}. Maps a growable block to the soil category it should grow on:
 * coral / lily → water, mushrooms → the mushroom soil tag, cactus → sand, sculk growables → sculk,
 * everything else → dirt (the default when a crop recipe omits its {@code soil} field). The
 * assignment carries the JSON {@code soil} value ({@code null} for the dirt default so the generator
 * omits the field entirely), keeping this decision free of any world state for unit testing.
 */
public enum SoilAssignment {
	/** Default — omit the {@code soil} field; crops fall back to the {@code cultivated:soil/dirt} tag. */
	DIRT(null),
	WATER("#cultivated:soil/water"),
	MUSHROOM("#cultivated:soil/mushroom"),
	SAND("#cultivated:soil/sand"),
	SCULK("#cultivated:soil/sculk");

	private final String soilValue;

	SoilAssignment(final String soilValue) {
		this.soilValue = soilValue;
	}

	/** The JSON {@code soil} ingredient value, or empty for {@link #DIRT} (field omitted). */
	public Optional<String> soilValue() {
		return Optional.ofNullable(this.soilValue);
	}

	/** Decide the soil category for {@code block} (id used for the sculk name heuristic). */
	public static SoilAssignment forBlock(final Block block, final Identifier blockId) {
		if (block instanceof LilyPadBlock || block instanceof BaseCoralPlantTypeBlock) {
			return WATER;
		}
		if (block instanceof MushroomBlock) {
			return MUSHROOM;
		}
		if (block instanceof CactusBlock) {
			return SAND;
		}
		if (blockId != null && blockId.getPath().contains("sculk")) {
			return SCULK;
		}
		return DIRT;
	}
}
