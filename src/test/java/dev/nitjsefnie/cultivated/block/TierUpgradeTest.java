package dev.nitjsefnie.cultivated.block;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Pure §D upgrade state machine + next-tier block-id composition. No game runtime required. */
class TierUpgradeTest {

	// ---- strict next-only state machine ----

	@Test
	void canUpgrade_allowsExactNextTier() {
		assertTrue(TierUpgrade.canUpgrade(Tier.BASE, Tier.ELITE));
		assertTrue(TierUpgrade.canUpgrade(Tier.ELITE, Tier.ULTRA));
		assertTrue(TierUpgrade.canUpgrade(Tier.ULTRA, Tier.MEGA));
	}

	@Test
	void canUpgrade_rejectsSkippingTiers() {
		assertFalse(TierUpgrade.canUpgrade(Tier.BASE, Tier.ULTRA));
		assertFalse(TierUpgrade.canUpgrade(Tier.BASE, Tier.MEGA));
		assertFalse(TierUpgrade.canUpgrade(Tier.ELITE, Tier.MEGA));
	}

	@Test
	void canUpgrade_rejectsSameOrLowerTier() {
		assertFalse(TierUpgrade.canUpgrade(Tier.ELITE, Tier.ELITE));
		assertFalse(TierUpgrade.canUpgrade(Tier.ULTRA, Tier.ELITE));
		assertFalse(TierUpgrade.canUpgrade(Tier.MEGA, Tier.ELITE));
	}

	@Test
	void canUpgrade_rejectsAtMaxTier() {
		// MEGA has no next tier; nothing can upgrade it.
		assertFalse(TierUpgrade.canUpgrade(Tier.MEGA, Tier.MEGA));
		assertFalse(TierUpgrade.canUpgrade(Tier.MEGA, null));
	}

	@Test
	void canUpgrade_rejectsNullTarget() {
		assertFalse(TierUpgrade.canUpgrade(Tier.BASE, null));
	}

	// ---- next-tier block-id composition ----

	@Test
	void nextTierBlockPath_baseToEliteAddsPrefix() {
		assertEquals("elite_terracotta_botany_pot",
			TierUpgrade.nextTierBlockPath("terracotta_botany_pot", Tier.BASE, Tier.ELITE));
	}

	@Test
	void nextTierBlockPath_eliteToUltraSwapsPrefixKeepingMaterialAndType() {
		assertEquals("ultra_terracotta_hopper_botany_pot",
			TierUpgrade.nextTierBlockPath("elite_terracotta_hopper_botany_pot", Tier.ELITE, Tier.ULTRA));
	}

	@Test
	void nextTierBlockPath_ultraToMegaWaxed() {
		assertEquals("mega_red_concrete_waxed_botany_pot",
			TierUpgrade.nextTierBlockPath("ultra_red_concrete_waxed_botany_pot", Tier.ULTRA, Tier.MEGA));
	}

	@Test
	void nextTierBlockPath_outOfOrderIsNull() {
		assertNull(TierUpgrade.nextTierBlockPath("terracotta_botany_pot", Tier.BASE, Tier.MEGA));
		assertNull(TierUpgrade.nextTierBlockPath("elite_terracotta_botany_pot", Tier.ELITE, Tier.MEGA));
	}

	@Test
	void nextTierBlockPath_atMaxIsNull() {
		assertNull(TierUpgrade.nextTierBlockPath("mega_terracotta_botany_pot", Tier.MEGA, null));
	}
}
