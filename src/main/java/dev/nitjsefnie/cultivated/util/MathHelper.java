package dev.nitjsefnie.cultivated.util;

import java.text.DecimalFormat;
import net.minecraft.util.RandomSource;

/**
 * Phase G #15 — small maths helpers: percent-chance rolls, inclusive-range random ints, and
 * decimal formatting for tooltips.
 */
public final class MathHelper {
	private static final DecimalFormat DECIMAL = new DecimalFormat("#.##");

	private MathHelper() {
	}

	/** True with probability {@code chance} (clamped to 0..1). */
	public static boolean rollChance(final RandomSource random, final float chance) {
		if (chance <= 0.0f) {
			return false;
		}
		if (chance >= 1.0f) {
			return true;
		}
		return random.nextFloat() < chance;
	}

	/** Uniform random int in {@code [min, max]} inclusive. */
	public static int nextIntInclusive(final RandomSource random, final int min, final int max) {
		if (max <= min) {
			return min;
		}
		return random.nextIntBetweenInclusive(min, max);
	}

	public static String formatDecimal(final double value) {
		return DECIMAL.format(value);
	}

	/** Split a fractional yield into whole rolls plus a probabilistic bonus roll (see §A.8). */
	public static int rollCount(final RandomSource random, final double totalYield) {
		if (totalYield <= 0.0) {
			return 0;
		}
		int rolls = (int)Math.floor(totalYield);
		final double frac = totalYield - rolls;
		if (frac > 0.0 && random.nextFloat() < frac) {
			rolls++;
		}
		return rolls;
	}
}
