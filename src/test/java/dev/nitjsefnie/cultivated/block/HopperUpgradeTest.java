package dev.nitjsefnie.cultivated.block;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Pure PF4c Basic→Hopper conversion rule + hopper block-id composition. No game runtime required. */
class HopperUpgradeTest {

	// ---- strict "only basic upgrades" rule ----

	@Test
	void canUpgrade_allowsOnlyBasic() {
		assertTrue(HopperUpgrade.canUpgrade(PotType.BASIC));
	}

	@Test
	void canUpgrade_rejectsHopperAndWaxed() {
		assertFalse(HopperUpgrade.canUpgrade(PotType.HOPPER));
		assertFalse(HopperUpgrade.canUpgrade(PotType.WAXED));
	}

	@Test
	void canUpgrade_rejectsNull() {
		assertFalse(HopperUpgrade.canUpgrade(null));
	}

	// ---- hopper block-id composition (pot-type segment swap, tier + material preserved) ----

	@Test
	void hopperBlockPath_baseBasicInsertsHopperInfix() {
		assertEquals("terracotta_hopper_botany_pot",
			HopperUpgrade.hopperBlockPath("terracotta_botany_pot", PotType.BASIC));
	}

	@Test
	void hopperBlockPath_keepsMaterial() {
		assertEquals("red_concrete_hopper_botany_pot",
			HopperUpgrade.hopperBlockPath("red_concrete_botany_pot", PotType.BASIC));
	}

	@Test
	void hopperBlockPath_keepsTierPrefix() {
		assertEquals("elite_terracotta_hopper_botany_pot",
			HopperUpgrade.hopperBlockPath("elite_terracotta_botany_pot", PotType.BASIC));
		assertEquals("mega_quartz_bricks_hopper_botany_pot",
			HopperUpgrade.hopperBlockPath("mega_quartz_bricks_botany_pot", PotType.BASIC));
	}

	@Test
	void hopperBlockPath_nonBasicIsNull() {
		// A HOPPER pot id already carries the _hopper infix; a WAXED one the _waxed infix. Neither is a
		// valid conversion source, so both resolve to null regardless of the path shape.
		assertNull(HopperUpgrade.hopperBlockPath("terracotta_hopper_botany_pot", PotType.HOPPER));
		assertNull(HopperUpgrade.hopperBlockPath("terracotta_waxed_botany_pot", PotType.WAXED));
	}
}
