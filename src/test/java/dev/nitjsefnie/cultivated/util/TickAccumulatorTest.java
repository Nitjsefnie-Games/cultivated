package dev.nitjsefnie.cultivated.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * Game-tick tracking math (SPEC §G #4 / §B.3). The accumulator advances by a FIXED {@link
 * TickAccumulator#TICK_STEP} per game tick, independent of the world's current tick rate, so more
 * game ticks per second (a higher {@code /tick rate}) grows crops faster and fewer grows them
 * slower — user decision 2026-07-20, superseding the original real-time-stable normalisation. No
 * game runtime required.
 */
class TickAccumulatorTest {

	private static final float EPS = 1.0e-6f;

	// ---- each game tick advances a constant amount, regardless of tick rate ----

	@Test
	void tickStepIsOnePerGameTick() {
		assertEquals(1.0f, TickAccumulator.TICK_STEP, EPS, "one tick of progress per game tick");
	}

	@Test
	void tickUpAdvancesByTheConstantStep() {
		final TickAccumulator acc = new TickAccumulator();
		acc.tickUp();
		assertEquals(TickAccumulator.TICK_STEP, acc.get(), EPS, "one game tick advances by TICK_STEP");
		acc.tickUp();
		acc.tickUp();
		assertEquals(3.0f * TickAccumulator.TICK_STEP, acc.get(), EPS, "three game ticks advance by 3 * TICK_STEP");
	}

	@Test
	void tickDownRetreatsByTheConstantStep() {
		final TickAccumulator acc = new TickAccumulator(10.0f);
		acc.tickDown();
		assertEquals(9.0f, acc.get(), EPS, "one game tick retreats by TICK_STEP");
	}

	@Test
	void tickUpThenTickDownReturnsToStart() {
		final TickAccumulator acc = new TickAccumulator(100.0f);
		acc.tickUp();
		acc.tickDown();
		assertEquals(100.0f, acc.get(), EPS, "a tickUp and a tickDown cancel exactly");
	}

	// ---- the NEW property: growth per game tick is constant, so /tick rate scales growth speed ----

	@Test
	void gameTickAdvanceIsIndependentOfTickRate() {
		// The accumulator has no notion of tick rate: N game ticks always advance it by N * TICK_STEP.
		// A world running at a higher /tick rate therefore ticks the pot MORE times per real second and
		// grows the crop proportionally faster (and slower at a lower rate) — the reversed §B.3 goal.
		final int reachedAfter = 200; // required accumulator units (constant, rate-independent)

		final TickAccumulator acc = new TickAccumulator();
		for (int tick = 0; tick < reachedAfter; tick++) {
			acc.tickUp();
		}
		assertEquals(reachedAfter, acc.get(), EPS,
			"maturity is reached after a fixed number of game ticks, independent of /tick rate");
	}

	@Test
	void higherTickRateReachesMaturityInLessRealTime() {
		// Same fixed number of game ticks to mature; a rate-100 world ticks 5x as often as a rate-20
		// world, so it covers those game ticks in 1/5 the real time (~5x faster growth). Model the two
		// worlds' per-second progress via how many ticks each fires in one real second.
		final int required = 100;

		final TickAccumulator fast = new TickAccumulator(); // 100 t/s: 100 ticks in one real second
		for (int i = 0; i < 100; i++) {
			fast.tickUp();
		}
		final TickAccumulator normal = new TickAccumulator(); // 20 t/s: 20 ticks in one real second
		for (int i = 0; i < 20; i++) {
			normal.tickUp();
		}

		assertEquals((float) required, fast.get(), EPS, "the fast world reaches required (matures) within one real second");
		assertEquals(20.0f, normal.get(), EPS, "the normal world has advanced only 20 in the same real second");
		// -> the higher tick rate matured the crop 5x sooner in wall-clock time.
	}
}
