package dev.nitjsefnie.cultivated.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Phase D §D / §A.9 — config defaults and named-property resolution, including the per-tier
 * crafting gates the {@code cultivated:config} load condition reads. Pure logic, no game runtime.
 */
class CultivatedConfigTest {

	@Test
	void tierModifierDefaults() {
		assertEquals(2, CultivatedConfig.eliteSpeed);
		assertEquals(2, CultivatedConfig.eliteOutput);
		assertEquals(6, CultivatedConfig.ultraSpeed);
		assertEquals(3, CultivatedConfig.ultraOutput);
		assertEquals(10, CultivatedConfig.megaSpeed);
		assertEquals(4, CultivatedConfig.megaOutput);
	}

	@Test
	void baseGateNamesStillResolve() {
		assertTrue(CultivatedConfig.booleanProperty("can_craft_basic_pots"));
		assertTrue(CultivatedConfig.booleanProperty("can_craft_hopper_pots"));
		assertTrue(CultivatedConfig.booleanProperty("can_wax_pots"));
		assertTrue(CultivatedConfig.booleanProperty("damage_harvest_tool"));
	}

	@Test
	void perTierGateNamesResolve() {
		for (final String tier : new String[] {"elite", "ultra", "mega"}) {
			for (final String type : new String[] {"basic", "hopper", "waxed"}) {
				final String name = "can_craft_" + tier + "_" + type + "_pots";
				assertTrue(CultivatedConfig.booleanProperty(name), name + " should resolve to its default (true)");
			}
		}
	}

	@Test
	void perTierGateTracksField() {
		final boolean original = CultivatedConfig.canCraftEliteHopperPots;
		try {
			CultivatedConfig.canCraftEliteHopperPots = false;
			assertFalse(CultivatedConfig.booleanProperty("can_craft_elite_hopper_pots"));
		} finally {
			CultivatedConfig.canCraftEliteHopperPots = original;
		}
	}

	@Test
	void unknownGateIsFalse() {
		assertFalse(CultivatedConfig.booleanProperty("can_craft_legendary_basic_pots"));
		assertFalse(CultivatedConfig.booleanProperty("nonsense"));
	}
}
