package dev.nitjsefnie.cultivated.menu;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.nitjsefnie.cultivated.block.PotMechanics;
import org.junit.jupiter.api.Test;

/**
 * Pure {@code quickMoveStack} routing decision (§B.7). No game runtime required. Verifies the
 * tool → fertilizer → soil → seed → inventory precedence and each gating condition. The 4th argument
 * is "resolves to a fertilizer AND the menu wires the fertilizer slots" (only the hopper menu does).
 */
class PotMenuRoutingTest {

	@Test
	void harvestToolGoesToToolSlotWhenEmptyAndSlotExists() {
		assertEquals(PotMechanics.TOOL, PotMenuRouting.routeFromInventory(true, true, true, false, false, false));
	}

	@Test
	void harvestToolFallsThroughWhenToolSlotOccupied() {
		// tool slot not empty -> tool branch skipped; nothing else resolves -> inventory
		assertEquals(PotMenuRouting.ROUTE_INVENTORY, PotMenuRouting.routeFromInventory(true, true, false, false, false, false));
	}

	@Test
	void harvestToolIgnoredWhenMenuHasNoToolSlot() {
		// basic pot has no tool slot; a harvest tool that is neither soil nor crop -> inventory
		assertEquals(PotMenuRouting.ROUTE_INVENTORY, PotMenuRouting.routeFromInventory(false, true, true, false, false, false));
	}

	@Test
	void soilGoesToSoilSlot() {
		assertEquals(PotMechanics.SOIL, PotMenuRouting.routeFromInventory(true, false, true, false, true, false));
	}

	@Test
	void cropGoesToSeedSlot() {
		assertEquals(PotMechanics.SEED, PotMenuRouting.routeFromInventory(true, false, true, false, false, true));
	}

	@Test
	void toolTakesPrecedenceOverSoilAndCrop() {
		assertEquals(PotMechanics.TOOL, PotMenuRouting.routeFromInventory(true, true, true, false, true, true));
	}

	@Test
	void soilTakesPrecedenceOverCrop() {
		assertEquals(PotMechanics.SOIL, PotMenuRouting.routeFromInventory(true, false, true, false, true, true));
	}

	@Test
	void unrelatedItemGoesToInventory() {
		assertEquals(PotMenuRouting.ROUTE_INVENTORY, PotMenuRouting.routeFromInventory(true, false, true, false, false, false));
	}

	@Test
	void basicPotSoilStillRoutesToSoil() {
		// hasToolSlot=false but the item resolves to a soil
		assertEquals(PotMechanics.SOIL, PotMenuRouting.routeFromInventory(false, false, true, false, true, false));
	}

	@Test
	void fertilizerGoesToFertilizerRegion() {
		assertEquals(PotMechanics.FERTILIZER_INPUT_FIRST,
			PotMenuRouting.routeFromInventory(true, false, true, true, false, false));
	}

	@Test
	void fertilizerTakesPrecedenceOverSoilAndCrop() {
		// a bone/bone_block that also resolved as soil or crop must still land in the fertilizer slots
		assertEquals(PotMechanics.FERTILIZER_INPUT_FIRST,
			PotMenuRouting.routeFromInventory(true, false, true, true, true, true));
	}

	@Test
	void toolTakesPrecedenceOverFertilizer() {
		assertEquals(PotMechanics.TOOL, PotMenuRouting.routeFromInventory(true, true, true, true, false, false));
	}

	@Test
	void fertilizerIgnoredWhenMenuHasNoFertilizerSlots() {
		// basic pot wires no fertilizer slots, so its menu passes resolvesFertilizer=false
		assertEquals(PotMenuRouting.ROUTE_INVENTORY,
			PotMenuRouting.routeFromInventory(false, false, true, false, false, false));
	}

	@Test
	void nonFertilizerStillFallsThroughToInventory() {
		// a fertilizer-capable menu, but the stack is not a fertilizer: existing routing unchanged
		assertEquals(PotMenuRouting.ROUTE_INVENTORY,
			PotMenuRouting.routeFromInventory(true, false, true, false, false, false));
	}
}
