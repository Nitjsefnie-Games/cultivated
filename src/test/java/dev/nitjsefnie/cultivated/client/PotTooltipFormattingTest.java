package dev.nitjsefnie.cultivated.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.nitjsefnie.cultivated.client.PotTooltipFormatting.Trend;
import dev.nitjsefnie.cultivated.client.PotTooltipFormatting.YieldBreakdown;
import org.junit.jupiter.api.Test;

/**
 * Pure formatting/decision logic behind the pot GUI tooltips (§B.8). No game runtime required:
 * the yield-breakdown decomposition, the tick-rate-aware duration formatting, the +/− buff/nerf
 * sign decision, and the wrong-soil / wrong-pot predicates.
 */
class PotTooltipFormattingTest {

	private static final double EPS = 1.0e-6;

	// ---- trend (buff / nerf / neutral) ----

	@Test
	void trendIsBuffForPositiveNerfForNegativeNeutralForZero() {
		assertEquals(Trend.BUFF, PotTooltipFormatting.trend(0.25));
		assertEquals(Trend.NERF, PotTooltipFormatting.trend(-0.25));
		assertEquals(Trend.NEUTRAL, PotTooltipFormatting.trend(0.0));
	}

	@Test
	void signedPercentPrefixesPlusForBuffAndUnicodeMinusForNerf() {
		assertEquals("+50%", PotTooltipFormatting.signedPercent(0.5));
		assertEquals("−25%", PotTooltipFormatting.signedPercent(-0.25));
		assertEquals("0%", PotTooltipFormatting.signedPercent(0.0));
	}

	@Test
	void signedPercentUsesSharedDecimalFormat() {
		// #.## drops trailing zeros and keeps up to two decimals.
		assertEquals("+12.5%", PotTooltipFormatting.signedPercent(0.125));
		assertEquals("+100%", PotTooltipFormatting.signedPercent(1.0));
	}

	@Test
	void percentIsUnsigned() {
		assertEquals("100%", PotTooltipFormatting.percent(1.0));
		assertEquals("175%", PotTooltipFormatting.percent(1.75));
		assertEquals("0%", PotTooltipFormatting.percent(0.0));
	}

	// ---- yield breakdown (base / soil / pot / tool, each × yield_scale) ----

	@Test
	void yieldBreakdownScalesExternalSourcesAndSumsToTotal() {
		// crop yield 1.0, scale 2.0, pot 0.5, soil 0.25, tool 0.1
		final YieldBreakdown b = PotTooltipFormatting.yieldBreakdown(1.0, 2.0, 0.5, 0.25, 0.1);
		assertEquals(1.0, b.base(), EPS);
		assertEquals(0.5, b.soil(), EPS); // 2.0 * 0.25
		assertEquals(1.0, b.pot(), EPS);  // 2.0 * 0.5
		assertEquals(0.2, b.tool(), EPS); // 2.0 * 0.1
		assertEquals(1.0 + 0.5 + 1.0 + 0.2, b.total(), EPS);
	}

	@Test
	void yieldBreakdownTotalMatchesYieldFormula() {
		final double cropYield = 1.0;
		final double scale = 1.0;
		final double pot = 0.0;
		final double soil = 0.5;
		final double tool = 0.25;
		final YieldBreakdown b = PotTooltipFormatting.yieldBreakdown(cropYield, scale, pot, soil, tool);
		final double engineTotal = dev.nitjsefnie.cultivated.data.formula.YieldFormula.totalYield(cropYield, scale, pot, soil, tool);
		assertEquals(engineTotal, b.total(), EPS);
	}

	@Test
	void yieldBreakdownWithZeroScaleLeavesOnlyBase() {
		final YieldBreakdown b = PotTooltipFormatting.yieldBreakdown(1.0, 0.0, 5.0, 5.0, 5.0);
		assertEquals(1.0, b.base(), EPS);
		assertEquals(0.0, b.soil(), EPS);
		assertEquals(0.0, b.pot(), EPS);
		assertEquals(0.0, b.tool(), EPS);
		assertEquals(1.0, b.total(), EPS);
	}

	// ---- tick-rate-aware grow time ----

	@Test
	void effectiveGameTicksEqualsRequiredAtNormalRate() {
		assertEquals(1500, PotTooltipFormatting.effectiveGameTicks(1500, 20.0f));
	}

	@Test
	void effectiveGameTicksScalesWithTickRate() {
		// Half rate -> half as many (longer) game ticks; double rate -> twice as many.
		assertEquals(750, PotTooltipFormatting.effectiveGameTicks(1500, 10.0f));
		assertEquals(3000, PotTooltipFormatting.effectiveGameTicks(1500, 40.0f));
	}

	@Test
	void effectiveGameTicksTreatsNonPositiveRateAsNormal() {
		assertEquals(1500, PotTooltipFormatting.effectiveGameTicks(1500, 0.0f));
	}

	@Test
	void effectiveSecondsIsRateIndependentByNormalisation() {
		assertEquals(75.0, PotTooltipFormatting.effectiveSeconds(1500), EPS);
		assertEquals(5.0, PotTooltipFormatting.effectiveSeconds(100), EPS);
	}

	@Test
	void formatDurationRendersMinutesAndSeconds() {
		assertEquals("1m", PotTooltipFormatting.formatDuration(1200));       // 60s
		assertEquals("1m 15s", PotTooltipFormatting.formatDuration(1500));   // 75s
		assertEquals("2m 30s", PotTooltipFormatting.formatDuration(3000));   // 150s
	}

	@Test
	void formatDurationRendersSubMinuteSeconds() {
		assertEquals("5s", PotTooltipFormatting.formatDuration(100));   // 5s
		assertEquals("4.5s", PotTooltipFormatting.formatDuration(90));  // 4.5s
	}

	// ---- wrong-soil / wrong-pot predicates ----

	@Test
	void wrongSoilIsTrueOnlyWhenCurrentSoilNotAccepted() {
		assertTrue(PotTooltipFormatting.wrongSoil(false));
		assertFalse(PotTooltipFormatting.wrongSoil(true));
	}

	@Test
	void wrongPotIsTrueOnlyWhenPredicatePresentAndDoesNotMatch() {
		assertTrue(PotTooltipFormatting.wrongPot(true, false));
		assertFalse(PotTooltipFormatting.wrongPot(true, true));
		assertFalse(PotTooltipFormatting.wrongPot(false, false));
		assertFalse(PotTooltipFormatting.wrongPot(false, true));
	}
}
