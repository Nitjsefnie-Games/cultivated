package dev.nitjsefnie.cultivated.block;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;

/** Pure pot arithmetic and slot rules (§A.5, §B.3, §B.5). No game runtime required. */
class PotMechanicsTest {

	// ---- comparator scaling: ceil(14 * growth / required), capped at 14 while growing ----

	@Test
	void comparator_zeroGrowthIsZero() {
		assertEquals(0, PotMechanics.comparatorWhileGrowing(0.0f, 100));
	}

	@Test
	void comparator_roundsUp() {
		// 14 * 1 / 100 = 0.14 -> ceil -> 1
		assertEquals(1, PotMechanics.comparatorWhileGrowing(1.0f, 100));
		// 14 * 50 / 100 = 7.0 -> 7
		assertEquals(7, PotMechanics.comparatorWhileGrowing(50.0f, 100));
		// 14 * 51 / 100 = 7.14 -> ceil -> 8
		assertEquals(8, PotMechanics.comparatorWhileGrowing(51.0f, 100));
	}

	@Test
	void comparator_capsAt14WhileGrowing() {
		// at required, 14 * 100 / 100 = 14 (15 reserved for mature)
		assertEquals(14, PotMechanics.comparatorWhileGrowing(100.0f, 100));
		// beyond required still capped at 14
		assertEquals(14, PotMechanics.comparatorWhileGrowing(200.0f, 100));
	}

	@Test
	void comparator_nonPositiveRequiredIsZero() {
		assertEquals(0, PotMechanics.comparatorWhileGrowing(50.0f, 0));
		assertEquals(0, PotMechanics.comparatorWhileGrowing(50.0f, -5));
	}

	// ---- fertilizer clamp: never exceed required - 20 ----

	@Test
	void fertilizer_clampsToRequiredMinus20() {
		// required 100 -> cap 80; adding 500 from 0 lands exactly on cap
		assertEquals(80.0f, PotMechanics.clampFertilizedGrowth(0.0f, 500, 100));
	}

	@Test
	void fertilizer_addsWhenUnderCap() {
		// required 100 -> cap 80; 0 + 30 = 30
		assertEquals(30.0f, PotMechanics.clampFertilizedGrowth(0.0f, 30, 100));
	}

	@Test
	void fertilizer_noOpWhenNeeds20OrLess() {
		// required 20 -> cap 0 -> no-op
		assertEquals(5.0f, PotMechanics.clampFertilizedGrowth(5.0f, 999, 20));
		assertEquals(5.0f, PotMechanics.clampFertilizedGrowth(5.0f, 999, 10));
		assertFalse(PotMechanics.canFertilize(0.0f, 20));
	}

	@Test
	void fertilizer_noOpWhenAlreadyWithin20OfDone() {
		// required 90 -> cap 70; already at 75 -> no-op
		assertEquals(75.0f, PotMechanics.clampFertilizedGrowth(75.0f, 999, 90));
		assertFalse(PotMechanics.canFertilize(75.0f, 90));
		assertTrue(PotMechanics.canFertilize(50.0f, 90));
	}

	// ---- slot classification ----

	@Test
	void slotClassification() {
		assertTrue(PotMechanics.isInputSlot(PotMechanics.SOIL));
		assertTrue(PotMechanics.isInputSlot(PotMechanics.SEED));
		assertTrue(PotMechanics.isInputSlot(PotMechanics.TOOL));
		assertFalse(PotMechanics.isInputSlot(PotMechanics.FIRST_STORAGE));

		assertFalse(PotMechanics.isStorageSlot(PotMechanics.TOOL));
		assertTrue(PotMechanics.isStorageSlot(PotMechanics.FIRST_STORAGE));
		assertTrue(PotMechanics.isStorageSlot(PotMechanics.LAST_STORAGE));
		assertFalse(PotMechanics.isStorageSlot(PotMechanics.SIZE));
	}

	// ---- per-slot max stack size: soil/seed cap at 1, everything else keeps the default (PF2b) ----

	@Test
	void maxStackSize_soilAndSeedCapAtOne() {
		assertEquals(1, PotMechanics.maxStackSizeForSlot(PotMechanics.SOIL, 64));
		assertEquals(1, PotMechanics.maxStackSizeForSlot(PotMechanics.SEED, 64));
		// Independent of how large the container default is.
		assertEquals(1, PotMechanics.maxStackSizeForSlot(PotMechanics.SOIL, 99));
		assertEquals(1, PotMechanics.maxStackSizeForSlot(PotMechanics.SEED, Integer.MAX_VALUE));
	}

	@Test
	void maxStackSize_otherSlotsKeepDefault() {
		assertEquals(64, PotMechanics.maxStackSizeForSlot(PotMechanics.TOOL, 64));
		assertEquals(64, PotMechanics.maxStackSizeForSlot(PotMechanics.FIRST_STORAGE, 64));
		assertEquals(16, PotMechanics.maxStackSizeForSlot(PotMechanics.LAST_STORAGE, 16));
		assertEquals(1, PotMechanics.maxStackSizeForSlot(PotMechanics.TOOL, 1));
	}

	@Test
	void maxStackSize_neverExceedsDefault() {
		// A degenerate default below 1 is clamped, not raised, for soil/seed.
		assertEquals(0, PotMechanics.maxStackSizeForSlot(PotMechanics.SOIL, 0));
	}

	// ---- automation faces: extract only from hopper storage via DOWN; never insert ----

	@Test
	void automationFaces_hopperDownExposesStorage() {
		final int[] slots = PotMechanics.automationSlotsForFace(true, Direction.DOWN);
		assertEquals(PotMechanics.STORAGE_COUNT, slots.length);
		assertArrayEquals(new int[] {3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14}, slots);
	}

	@Test
	void automationFaces_hopperNonDownExposesNothing() {
		assertEquals(0, PotMechanics.automationSlotsForFace(true, Direction.UP).length);
		assertEquals(0, PotMechanics.automationSlotsForFace(true, Direction.NORTH).length);
	}

	@Test
	void automationFaces_basicPotExposesNothing() {
		assertEquals(0, PotMechanics.automationSlotsForFace(false, Direction.DOWN).length);
	}

	@Test
	void automationTake_onlyHopperStorageViaDown() {
		assertTrue(PotMechanics.canAutomationTake(true, PotMechanics.FIRST_STORAGE, Direction.DOWN));
		assertTrue(PotMechanics.canAutomationTake(true, PotMechanics.LAST_STORAGE, Direction.DOWN));
		assertFalse(PotMechanics.canAutomationTake(true, PotMechanics.SOIL, Direction.DOWN));
		assertFalse(PotMechanics.canAutomationTake(true, PotMechanics.FIRST_STORAGE, Direction.UP));
		assertFalse(PotMechanics.canAutomationTake(false, PotMechanics.FIRST_STORAGE, Direction.DOWN));
	}

	@Test
	void automationPlace_neverAllowed() {
		assertFalse(PotMechanics.canAutomationPlace());
	}

	// ---- R2d: storage-buffer room decision (a full hopper pot pauses its growth cycle) ----

	@Test
	void bufferRoom_emptyBufferHasRoom() {
		// A completely empty buffer (every slot free) obviously has room.
		final int[] free = new int[PotMechanics.STORAGE_COUNT];
		java.util.Arrays.fill(free, 64);
		assertTrue(PotMechanics.storageBufferHasRoom(free));
	}

	@Test
	void bufferRoom_completelyFullHasNoRoom() {
		// Every slot a full stack (free capacity 0) -> no room -> hopper pot pauses.
		final int[] free = new int[PotMechanics.STORAGE_COUNT];
		java.util.Arrays.fill(free, 0);
		assertFalse(PotMechanics.storageBufferHasRoom(free));
	}

	@Test
	void bufferRoom_oneNonFullStackHasRoom() {
		// 11 full stacks and a single mergeable (non-full) stack -> still has room.
		final int[] free = new int[PotMechanics.STORAGE_COUNT];
		java.util.Arrays.fill(free, 0);
		free[5] = 1;
		assertTrue(PotMechanics.storageBufferHasRoom(free));
	}

	@Test
	void bufferRoom_oneEmptySlotHasRoom() {
		// 11 full stacks and one empty slot (positive free capacity) -> has room.
		final int[] free = new int[PotMechanics.STORAGE_COUNT];
		java.util.Arrays.fill(free, 0);
		free[PotMechanics.STORAGE_COUNT - 1] = 64;
		assertTrue(PotMechanics.storageBufferHasRoom(free));
	}

	@Test
	void bufferRoom_emptyArrayHasNoRoom() {
		// Degenerate: no slots at all -> no room.
		assertFalse(PotMechanics.storageBufferHasRoom(new int[0]));
	}
}
