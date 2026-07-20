package dev.nitjsefnie.cultivated.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * Pure tick-rate-normalisation math (SPEC §G #4 / §B.3). The per-tick step is
 * {@code NORMAL_TICK_RATE / currentRate}, so the accumulator advances more per game-tick when the
 * world runs slow and less when it runs fast — keeping REAL-TIME growth stable under {@code /tick
 * rate}, which is the explicit §B.3 goal ("behavior is stable under /tick rate changes"). No game
 * runtime required.
 */
class TickAccumulatorTest {

	private static final float EPS = 1.0e-6f;

	// ---- step() scales inversely with the passed rate (the core §G #4 relation) ----

	@Test
	void step_isNormalRateOverCurrentRate() {
		assertEquals(1.0f, TickAccumulator.step(20.0f), EPS, "at the vanilla 20 t/s baseline, one step per tick");
		assertEquals(2.0f, TickAccumulator.step(10.0f), EPS, "half tick rate -> each game-tick advances ~2x");
		assertEquals(0.5f, TickAccumulator.step(40.0f), EPS, "double tick rate -> each game-tick advances ~0.5x");
		assertEquals(4.0f, TickAccumulator.step(5.0f), EPS, "quarter tick rate -> ~4x per game-tick");
	}

	@Test
	void step_dependsOnTheArgument_notAConstant() {
		// Guards the R3c regression: growth must read the LIVE rate, never a hardcoded 20. Different
		// rates in => different steps out.
		assertEquals(false, TickAccumulator.step(10.0f) == TickAccumulator.step(40.0f));
		assertEquals(false, TickAccumulator.step(20.0f) == TickAccumulator.step(10.0f));
	}

	@Test
	void step_clampsNonPositiveRateToNormal() {
		assertEquals(1.0f, TickAccumulator.step(0.0f), EPS, "a zero rate falls back to the normal step");
		assertEquals(1.0f, TickAccumulator.step(-5.0f), EPS, "a negative rate falls back to the normal step");
	}

	// ---- the §B.3 stability property: real-time advance is constant across tick rates ----

	@Test
	void realTimeAdvanceIsStableAcrossTickRates() {
		// Over one real second a rate-R world ticks R times, each advancing step(R); the product
		// R * step(R) = NORMAL_TICK_RATE is constant, so wall-clock growth does not change with
		// /tick rate. (This is exactly why a playtester sees "no effect" on growth speed: real-time
		// stability is the designed §B.3 behavior, not a bug.)
		for (final float rate : new float[] {5.0f, 10.0f, 20.0f, 40.0f, 60.0f}) {
			final TickAccumulator acc = new TickAccumulator();
			final int ticksPerSecond = Math.round(rate);
			for (int i = 0; i < ticksPerSecond; i++) {
				acc.tickUp(rate);
			}
			assertEquals(TickAccumulator.NORMAL_TICK_RATE, acc.get(), 1.0e-3f,
				"one real second of growth stays ~" + TickAccumulator.NORMAL_TICK_RATE + " at tick rate " + rate);
		}
	}

	@Test
	void tickUpThenTickDownReturnsToStart() {
		final TickAccumulator acc = new TickAccumulator(100.0f);
		acc.tickUp(13.0f);
		acc.tickDown(13.0f);
		assertEquals(100.0f, acc.get(), EPS, "tickUp and tickDown at the same rate cancel exactly");
	}

	@Test
	void tickDown_advancesRetreatByTheSameScaledStep() {
		final TickAccumulator acc = new TickAccumulator(10.0f);
		acc.tickDown(10.0f); // step(10) = 2
		assertEquals(8.0f, acc.get(), EPS, "half-rate cooldown retreats ~2 per tick");
	}
}
