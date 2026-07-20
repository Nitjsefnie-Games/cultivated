package dev.nitjsefnie.cultivated.menu;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.nitjsefnie.cultivated.block.PotMechanics;
import org.junit.jupiter.api.Test;

/**
 * Pure {@code quickMoveStack} routing decision (§B.7). No game runtime required. Verifies the
 * tool → soil → seed → inventory precedence and each gating condition.
 */
class PotMenuRoutingTest {

	@Test
	void harvestToolGoesToToolSlotWhenEmptyAndSlotExists() {
		assertEquals(PotMechanics.TOOL, PotMenuRouting.routeFromInventory(true, true, true, false, false));
	}

	@Test
	void harvestToolFallsThroughWhenToolSlotOccupied() {
		// tool slot not empty -> tool branch skipped; nothing else resolves -> inventory
		assertEquals(PotMenuRouting.ROUTE_INVENTORY, PotMenuRouting.routeFromInventory(true, true, false, false, false));
	}

	@Test
	void harvestToolIgnoredWhenMenuHasNoToolSlot() {
		// basic pot has no tool slot; a harvest tool that is neither soil nor crop -> inventory
		assertEquals(PotMenuRouting.ROUTE_INVENTORY, PotMenuRouting.routeFromInventory(false, true, true, false, false));
	}

	@Test
	void soilGoesToSoilSlot() {
		assertEquals(PotMechanics.SOIL, PotMenuRouting.routeFromInventory(true, false, true, true, false));
	}

	@Test
	void cropGoesToSeedSlot() {
		assertEquals(PotMechanics.SEED, PotMenuRouting.routeFromInventory(true, false, true, false, true));
	}

	@Test
	void toolTakesPrecedenceOverSoilAndCrop() {
		assertEquals(PotMechanics.TOOL, PotMenuRouting.routeFromInventory(true, true, true, true, true));
	}

	@Test
	void soilTakesPrecedenceOverCrop() {
		assertEquals(PotMechanics.SOIL, PotMenuRouting.routeFromInventory(true, false, true, true, true));
	}

	@Test
	void unrelatedItemGoesToInventory() {
		assertEquals(PotMenuRouting.ROUTE_INVENTORY, PotMenuRouting.routeFromInventory(true, false, true, false, false));
	}

	@Test
	void basicPotSoilStillRoutesToSoil() {
		// hasToolSlot=false but the item resolves to a soil
		assertEquals(PotMechanics.SOIL, PotMenuRouting.routeFromInventory(false, false, true, true, false));
	}
}
