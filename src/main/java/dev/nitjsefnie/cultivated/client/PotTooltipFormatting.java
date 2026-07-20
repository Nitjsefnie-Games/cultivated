package dev.nitjsefnie.cultivated.client;

import dev.nitjsefnie.cultivated.util.MathHelper;
import dev.nitjsefnie.cultivated.util.TickAccumulator;

/**
 * Phase B §B.8 — the pure, MC-free arithmetic and string formatting behind the pot GUI tooltips,
 * factored out of {@link PotTooltip} so it can be unit-tested without a game runtime. Covers the
 * yield-breakdown decomposition (base/soil/pot/tool, each already scaled by {@code yield_scale} the
 * same way {@link dev.nitjsefnie.cultivated.data.formula.YieldFormula} does), the tick-rate-aware
 * grow-time formatting (growth tracks game ticks directly, so the crop matures after exactly
 * {@code requiredGrowthTicks} game ticks and wall-clock time is {@code requiredGrowthTicks / tickRate}),
 * the +/− buff/nerf sign decision, and the wrong-soil / wrong-pot predicates (§B.8).
 */
public final class PotTooltipFormatting {
	private PotTooltipFormatting() {
	}

	/** Whether a modifier value helps ({@link #BUFF}), hurts ({@link #NERF}) or does neither. */
	public enum Trend {
		BUFF,
		NERF,
		NEUTRAL
	}

	/** A positive value is a buff, a negative value a nerf, zero is neutral. */
	public static Trend trend(final double value) {
		if (value > 0.0) {
			return Trend.BUFF;
		}
		if (value < 0.0) {
			return Trend.NERF;
		}
		return Trend.NEUTRAL;
	}

	/**
	 * Format a modifier fraction as a signed percentage — {@code "+50%"} for a buff, {@code "−25%"}
	 * (unicode minus) for a nerf, {@code "0%"} for neutral. Uses the shared {@link MathHelper}
	 * decimal format so tooltip numbers match every other displayed value.
	 */
	public static String signedPercent(final double fraction) {
		final String magnitude = MathHelper.formatDecimal(Math.abs(fraction) * 100.0) + "%";
		return switch (trend(fraction)) {
			case BUFF -> "+" + magnitude;
			case NERF -> "−" + magnitude;
			case NEUTRAL -> magnitude;
		};
	}

	/** Format a fraction as a plain (unsigned) percentage, e.g. {@code 1.0 -> "100%"}. */
	public static String percent(final double fraction) {
		return MathHelper.formatDecimal(fraction * 100.0) + "%";
	}

	/**
	 * The yield decomposition (§A.8): {@code base = crop.yield}; each external source contributes
	 * {@code yield_scale * modifier}; {@code total} equals
	 * {@link dev.nitjsefnie.cultivated.data.formula.YieldFormula#totalYield}.
	 */
	public record YieldBreakdown(double base, double soil, double pot, double tool, double total) {
	}

	/** Decompose the total yield into its base/soil/pot/tool source contributions (§A.8). */
	public static YieldBreakdown yieldBreakdown(
		final double cropYield,
		final double yieldScale,
		final double potYieldModifier,
		final double soilYieldModifier,
		final double toolYieldAttribute
	) {
		final double soil = yieldScale * soilYieldModifier;
		final double pot = yieldScale * potYieldModifier;
		final double tool = yieldScale * toolYieldAttribute;
		return new YieldBreakdown(cropYield, soil, pot, tool, cropYield + soil + pot + tool);
	}

	/**
	 * The number of game ticks the crop will take to mature. Growth now advances one tick of progress
	 * per game tick, so the crop matures after exactly {@code requiredGrowthTicks} game ticks — the tick
	 * count is a constant and does NOT scale with the world's tick rate (the {@code tickRate} argument is
	 * accepted for call-site symmetry with {@link #effectiveSeconds} but deliberately does not scale the
	 * result; that rate-scaling was the pre-tick-tracking bug).
	 */
	public static int effectiveGameTicks(final int requiredGrowthTicks, final float tickRate) {
		return Math.max(0, requiredGrowthTicks);
	}

	/**
	 * The real-world seconds the crop will take to mature at the world's current tick rate. Because
	 * growth tracks game ticks directly, the crop matures after exactly {@code requiredGrowthTicks} game
	 * ticks, so wall-clock time is {@code requiredGrowthTicks / tickRate} — it shrinks as the tick rate
	 * rises (e.g. {@code required=1200} is 60s at 20 t/s but 12s at 100 t/s). A non-positive rate is
	 * treated as the {@link TickAccumulator#NORMAL_TICK_RATE 20 t/s} baseline.
	 */
	public static double effectiveSeconds(final int requiredGrowthTicks, final float tickRate) {
		final float rate = tickRate <= 0.0f ? TickAccumulator.NORMAL_TICK_RATE : tickRate;
		return requiredGrowthTicks / (double) rate;
	}

	/**
	 * Human-readable duration for a required-ticks count at the world's current tick rate, e.g.
	 * {@code "1m 15s"} or {@code "4.5s"}.
	 */
	public static String formatDuration(final int requiredGrowthTicks, final float tickRate) {
		final double totalSeconds = effectiveSeconds(requiredGrowthTicks, tickRate);
		if (totalSeconds >= 60.0) {
			int minutes = (int) (totalSeconds / 60.0);
			int seconds = (int) Math.round(totalSeconds - minutes * 60.0);
			if (seconds == 60) {
				minutes++;
				seconds = 0;
			}
			return seconds == 0 ? minutes + "m" : minutes + "m " + seconds + "s";
		}
		return MathHelper.formatDecimal(totalSeconds) + "s";
	}

	/** A red "wrong soil" warning is shown when the pot's current soil is not accepted by the crop. */
	public static boolean wrongSoil(final boolean cropAcceptsCurrentSoil) {
		return !cropAcceptsCurrentSoil;
	}

	/** A red "wrong pot" warning is shown when a {@code pot_predicate} is present and excludes this pot. */
	public static boolean wrongPot(final boolean potPredicatePresent, final boolean potPredicateMatches) {
		return potPredicatePresent && !potPredicateMatches;
	}
}
