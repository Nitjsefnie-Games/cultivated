package dev.nitjsefnie.cultivated.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Pure {@code perftest} layout math — count → cubic NxNxN grid of unit offsets. No MC bootstrap
 * needed; {@link PerfTestGrid} is world-free.
 */
class PerfTestGridTest {

	@Test
	void clampCount_boundsToOneAndMax() {
		assertEquals(1, PerfTestGrid.clampCount(0), "below 1 clamps up to 1");
		assertEquals(1, PerfTestGrid.clampCount(-100), "negatives clamp up to 1");
		assertEquals(256, PerfTestGrid.clampCount(256), "in-range value passes through");
		assertEquals(PerfTestGrid.MAX_COUNT, PerfTestGrid.clampCount(PerfTestGrid.MAX_COUNT + 1), "above cap clamps down");
	}

	@Test
	void perfectCube_isNxNxN() {
		final PerfTestGrid.Layout layout = PerfTestGrid.layout(27, 64);
		assertEquals(3, layout.nx());
		assertEquals(3, layout.nz());
		assertEquals(3, layout.ny());
		assertEquals(27, layout.count());
	}

	@Test
	void verticalLayersCappedByMaxLayers_widensHorizontally() {
		// 64 units would be a 4x4x4 cube, but only 2 vertical layers fit — footprint widens to hold all 64.
		final PerfTestGrid.Layout layout = PerfTestGrid.layout(64, 2);
		assertEquals(2, layout.ny(), "vertical layers capped at maxLayers");
		assertTrue((long) layout.nx() * layout.nz() * layout.ny() >= 64, "footprint still holds every unit");
		assertEquals(64, layout.offsets().size(), "every requested unit is placed");
	}

	@Test
	void offsets_areCountManyDistinctInBounds() {
		final int count = 256;
		final PerfTestGrid.Layout layout = PerfTestGrid.layout(count, 64);
		final List<int[]> offsets = layout.offsets();
		assertEquals(count, offsets.size(), "one offset per requested unit");

		final Set<Long> seen = new HashSet<>();
		for (final int[] offset : offsets) {
			final int dx = offset[0];
			final int layer = offset[1];
			final int dz = offset[2];
			assertTrue(dx >= 0 && dx < layout.nx(), "dx in [0,nx)");
			assertTrue(dz >= 0 && dz < layout.nz(), "dz in [0,nz)");
			assertTrue(layer >= 0 && layer < layout.ny(), "layer in [0,ny)");
			final long key = ((long) layer << 40) | ((long) dz << 20) | dx;
			assertTrue(seen.add(key), "no two units share a cell");
		}
	}

	@Test
	void clampedCount_isReflectedInLayout() {
		final PerfTestGrid.Layout layout = PerfTestGrid.layout(PerfTestGrid.MAX_COUNT + 500, 64);
		assertEquals(PerfTestGrid.MAX_COUNT, layout.count(), "count clamped to MAX_COUNT");
		assertEquals(PerfTestGrid.MAX_COUNT, layout.offsets().size());
	}
}
