package dev.nitjsefnie.cultivated.menu;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.nitjsefnie.cultivated.block.PotMechanics;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Pure slot-index / coordinate rules for the pot menus (§B.8). No game runtime required. */
class PotMenuLayoutTest {

	@Test
	void outputGridMapsRowMajorToStorageSlots() {
		assertEquals(PotMechanics.FIRST_STORAGE, PotMenuLayout.outputContainerSlot(0, 0)); // 3
		assertEquals(6, PotMenuLayout.outputContainerSlot(0, 3));
		assertEquals(7, PotMenuLayout.outputContainerSlot(1, 0));
		assertEquals(PotMechanics.LAST_STORAGE, PotMenuLayout.outputContainerSlot(2, 3)); // 14
	}

	@Test
	void outputGridCoversAllTwelveStorageSlotsExactlyOnce() {
		final Set<Integer> seen = new HashSet<>();
		for (int row = 0; row < PotMenuLayout.OUTPUT_ROWS; row++) {
			for (int column = 0; column < PotMenuLayout.OUTPUT_COLUMNS; column++) {
				seen.add(PotMenuLayout.outputContainerSlot(row, column));
			}
		}
		assertEquals(PotMechanics.STORAGE_COUNT, seen.size());
		for (int slot = PotMechanics.FIRST_STORAGE; slot <= PotMechanics.LAST_STORAGE; slot++) {
			assertTrue(seen.contains(slot), "missing storage slot " + slot);
		}
	}

	@Test
	void outputCoordinatesStepBy18FromOrigin() {
		assertEquals(80, PotMenuLayout.outputX(0));
		assertEquals(98, PotMenuLayout.outputX(1));
		assertEquals(134, PotMenuLayout.outputX(3));
		assertEquals(17, PotMenuLayout.outputY(0));
		assertEquals(35, PotMenuLayout.outputY(1));
		assertEquals(53, PotMenuLayout.outputY(2));
	}

	@Test
	void basicInputCoordinatesMatchSpec() {
		assertEquals(80, PotMenuLayout.BASIC_SOIL_X);
		assertEquals(48, PotMenuLayout.BASIC_SOIL_Y);
		assertEquals(80, PotMenuLayout.BASIC_SEED_X);
		assertEquals(22, PotMenuLayout.BASIC_SEED_Y);
	}

	@Test
	void hopperInputCoordinatesMatchSpec() {
		assertEquals(44, PotMenuLayout.HOPPER_SOIL_X);
		assertEquals(48, PotMenuLayout.HOPPER_SOIL_Y);
		assertEquals(44, PotMenuLayout.HOPPER_SEED_X);
		assertEquals(22, PotMenuLayout.HOPPER_SEED_Y);
		assertEquals(18, PotMenuLayout.HOPPER_TOOL_X);
		assertEquals(35, PotMenuLayout.HOPPER_TOOL_Y);
	}
}
