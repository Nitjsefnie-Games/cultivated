package dev.nitjsefnie.cultivated.data.formula;

import dev.nitjsefnie.cultivated.util.MathHelper;
import net.minecraft.util.RandomSource;

/**
 * Phase A §A.8 — yield formula (number of drop rolls).
 *
 * <pre>
 * totalYield = crop.yield
 *            + yield_scale * pot.yield_modifier
 *            + yield_scale * soil.yield_modifier
 *            + yield_scale * attributeValue(cultivated:yield, tool)
 * rolls: guaranteed = floor(totalYield); then +1 roll with probability = frac(totalYield); min 0
 * </pre>
 */
public final class YieldFormula {
	private YieldFormula() {
	}

	/** The total (fractional) yield (§A.8). External modifiers are scaled by {@code yieldScale}. */
	public static double totalYield(
		final double cropYield,
		final double yieldScale,
		final double potYieldModifier,
		final double soilYieldModifier,
		final double toolYieldAttribute
	) {
		return cropYield + yieldScale * (potYieldModifier + soilYieldModifier + toolYieldAttribute);
	}

	/** Number of drop rolls: {@code floor(totalYield)} plus a fractional bonus roll (§A.8). */
	public static int rolls(final RandomSource random, final double totalYield) {
		return MathHelper.rollCount(random, totalYield);
	}
}
