package dev.nitjsefnie.cultivated.command;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.nitjsefnie.cultivated.CultivatedTestBootstrap;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Pure {@code missing seeds} scan predicate (§F.2): which blocks a botany pot could host as a crop,
 * independent of any recipe. Needs the vanilla block registry bootstrapped for the {@code instanceof}
 * family checks.
 */
class CropCandidatesTest {
	@BeforeAll
	static void bootstrap() {
		CultivatedTestBootstrap.bootstrap();
	}

	@Test
	void crops_flowers_mushrooms_cactus_lily_coral_areCandidates() {
		assertTrue(CropCandidates.couldBeCrop(Blocks.WHEAT, false), "wheat (CropBlock)");
		assertTrue(CropCandidates.couldBeCrop(Blocks.POPPY, false), "poppy (FlowerBlock)");
		assertTrue(CropCandidates.couldBeCrop(Blocks.RED_MUSHROOM, false), "red mushroom");
		assertTrue(CropCandidates.couldBeCrop(Blocks.CACTUS, false), "cactus");
		assertTrue(CropCandidates.couldBeCrop(Blocks.LILY_PAD, false), "lily pad");
		assertTrue(CropCandidates.couldBeCrop(Blocks.TUBE_CORAL, false), "coral plant");
	}

	@Test
	void nonGrowableBlockIsNotACandidate() {
		assertFalse(CropCandidates.couldBeCrop(Blocks.STONE, false), "stone is not a crop");
	}

	@Test
	void saplingsGatedByIncludeSaplingsFlag() {
		assertFalse(CropCandidates.couldBeCrop(Blocks.OAK_SAPLING, false), "sapling excluded by default");
		assertTrue(CropCandidates.couldBeCrop(Blocks.OAK_SAPLING, true), "sapling included when requested");
	}
}
