package dev.nitjsefnie.cultivated.command.generator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.nitjsefnie.cultivated.CultivatedTestBootstrap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Pure crop soil-assignment decision (§F.2): coral/lily → water, mushroom → mushroom tag, cactus →
 * sand, sculk → sculk, everything else → dirt (soil field omitted).
 */
class SoilAssignmentTest {
	@BeforeAll
	static void bootstrap() {
		CultivatedTestBootstrap.bootstrap();
	}

	private static SoilAssignment forBlock(final Block block) {
		return SoilAssignment.forBlock(block, BuiltInRegistries.BLOCK.getKey(block));
	}

	@Test
	void coralAndLilyAssignWater() {
		assertEquals(SoilAssignment.WATER, forBlock(Blocks.TUBE_CORAL));
		assertEquals(SoilAssignment.WATER, forBlock(Blocks.LILY_PAD));
	}

	@Test
	void mushroomAssignsMushroomTag() {
		assertEquals(SoilAssignment.MUSHROOM, forBlock(Blocks.RED_MUSHROOM));
	}

	@Test
	void cactusAssignsSand() {
		assertEquals(SoilAssignment.SAND, forBlock(Blocks.CACTUS));
	}

	@Test
	void sculkAssignsSculkByName() {
		assertEquals(SoilAssignment.SCULK, forBlock(Blocks.SCULK_VEIN));
	}

	@Test
	void fallbackIsDirtWithNoSoilField() {
		final SoilAssignment wheat = forBlock(Blocks.WHEAT);
		assertEquals(SoilAssignment.DIRT, wheat);
		assertTrue(wheat.soilValue().isEmpty(), "dirt default omits the soil JSON field");
	}

	@Test
	void nonDirtAssignmentsCarryATagReference() {
		assertEquals("#cultivated:soil/water", forBlock(Blocks.LILY_PAD).soilValue().orElseThrow());
		assertEquals("#cultivated:soil/sand", forBlock(Blocks.CACTUS).soilValue().orElseThrow());
	}

	@Test
	void nullIdDoesNotThrowForSculkHeuristic() {
		// The sculk check tolerates a null id (falls through to dirt) rather than NPE-ing.
		final Identifier noId = null;
		assertEquals(SoilAssignment.DIRT, SoilAssignment.forBlock(Blocks.WHEAT, noId));
	}
}
