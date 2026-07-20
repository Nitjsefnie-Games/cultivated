package dev.nitjsefnie.cultivated.command;

import net.minecraft.world.level.block.BaseCoralPlantTypeBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.CactusBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.FlowerBlock;
import net.minecraft.world.level.block.GrowingPlantBlock;
import net.minecraft.world.level.block.LilyPadBlock;
import net.minecraft.world.level.block.MushroomBlock;
import net.minecraft.world.level.block.SaplingBlock;
import net.minecraft.world.level.block.StemBlock;
import net.minecraft.world.level.block.BonemealableBlock;

/**
 * Phase F.2 — the pure "could this block be a pot crop?" predicate driving the {@code missing seeds}
 * scan. A block is a crop <em>candidate</em> if it is one of the growable families the datapack
 * curates (crop / growing / bonemealable / sapling / bush / coral / flower / lily / cactus / stem),
 * independent of whether a recipe already exists — the command layer filters out already-cached
 * seeds separately via {@code PotRecipeCaches}. Kept free of any world/registry state so it is unit
 * testable against the bootstrapped vanilla block set.
 */
public final class CropCandidates {
	private CropCandidates() {
	}

	/**
	 * True if {@code block} belongs to a growable family a botany pot could host. Saplings are gated
	 * behind {@code includeSaplings} because the tree crops typically ship with bespoke loot
	 * tables rather than auto-derived, so the default scan omits them.
	 */
	public static boolean couldBeCrop(final Block block, final boolean includeSaplings) {
		if (block instanceof SaplingBlock) {
			return includeSaplings;
		}
		return block instanceof CropBlock
			|| block instanceof StemBlock
			|| block instanceof BushBlock
			|| block instanceof FlowerBlock
			|| block instanceof MushroomBlock
			|| block instanceof CactusBlock
			|| block instanceof LilyPadBlock
			|| block instanceof GrowingPlantBlock
			|| block instanceof BaseCoralPlantTypeBlock
			|| block instanceof BonemealableBlock;
	}
}
