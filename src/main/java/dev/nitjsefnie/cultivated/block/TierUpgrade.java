package dev.nitjsefnie.cultivated.block;

import org.jspecify.annotations.Nullable;

/**
 * Phase D §D — the pure upgrade state machine for the in-place tier conversion (kept free of any
 * Minecraft world access so it is unit-testable). An upgrade item carries a {@code target} tier; a
 * pot may only be converted when {@code target} is exactly the pot's current tier's
 * {@link Tier#next()} — the chain is strict base→elite→ultra→mega, so skipping a tier (base→ultra),
 * a same-tier re-apply, or upgrading a {@code mega} pot are all rejected (no-op, item not consumed).
 *
 * <p>{@link #nextTierBlockPath} composes the resulting block's registry path purely from the current
 * block's registry path by swapping the tier id-prefix ({@link Tier#idPrefix()}): a base block has
 * no prefix ({@code terracotta_botany_pot}), a tiered block is {@code <tier>_<material>_...}.
 */
public final class TierUpgrade {
	private TierUpgrade() {
	}

	/** Strict next-only: an upgrade to {@code target} is allowed iff {@code target == current.next()}. */
	public static boolean canUpgrade(final Tier current, final @Nullable Tier target) {
		return target != null && target == current.next();
	}

	/**
	 * The registry path of the pot block one tier up from {@code currentPath}, or {@code null} when
	 * {@code target} is not the strict next tier of {@code current}. The material + pot-type portion of
	 * the id is preserved; only the tier prefix changes.
	 */
	public static @Nullable String nextTierBlockPath(final String currentPath, final Tier current, final @Nullable Tier target) {
		if (!canUpgrade(current, target)) {
			return null;
		}
		final String baseName = current.isBase()
			? currentPath
			: currentPath.substring(current.idPrefix().length() + 1);
		return target.idPrefix() + "_" + baseName;
	}
}
