package dev.nitjsefnie.cultivated.block;

import org.jspecify.annotations.Nullable;

/**
 * Playtest PF4c — the pure upgrade rule for the in-place Basic→Hopper pot conversion (kept free of any
 * Minecraft world access so it is unit-testable). The {@code hopper_upgrade} item converts a
 * {@link PotType#BASIC} pot to the {@link PotType#HOPPER} pot of the <em>same</em> material and tier;
 * hopper and waxed pots are rejected (no-op, item not consumed).
 *
 * <p>Mirrors {@link TierUpgrade} but swaps the pot-<em>type</em> segment of the block id instead of the
 * tier prefix: a Basic pot id is {@code [<tier>_]<material>_botany_pot}, and the matching Hopper id is
 * {@code [<tier>_]<material>_hopper_botany_pot} — so only the {@code _hopper} infix is inserted, leaving
 * the tier prefix and material intact.
 */
public final class HopperUpgrade {
	private static final String BASIC_SUFFIX = "_botany_pot";
	private static final String HOPPER_SUFFIX = "_hopper_botany_pot";

	private HopperUpgrade() {
	}

	/** Only a {@link PotType#BASIC} pot may be converted to Hopper; every other type is rejected. */
	public static boolean canUpgrade(final @Nullable PotType current) {
		return current == PotType.BASIC;
	}

	/**
	 * The registry path of the Hopper pot for a Basic pot's {@code currentPath}, preserving the tier
	 * prefix and material, or {@code null} when {@code current} is not {@link PotType#BASIC} (or the id
	 * does not have the expected Basic-pot shape). A Basic pot id ends in {@code _botany_pot} without a
	 * {@code _hopper}/{@code _waxed} pot-type infix, so only that suffix is rewritten to {@code _hopper_botany_pot}.
	 */
	public static @Nullable String hopperBlockPath(final String currentPath, final @Nullable PotType current) {
		if (!canUpgrade(current) || !currentPath.endsWith(BASIC_SUFFIX)) {
			return null;
		}
		final String withoutSuffix = currentPath.substring(0, currentPath.length() - BASIC_SUFFIX.length());
		return withoutSuffix + HOPPER_SUFFIX;
	}
}
