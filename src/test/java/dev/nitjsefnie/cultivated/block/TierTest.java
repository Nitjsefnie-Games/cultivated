package dev.nitjsefnie.cultivated.block;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.nitjsefnie.cultivated.client.PotTooltipFormatting;
import dev.nitjsefnie.cultivated.client.PotTooltipFormatting.YieldBreakdown;
import dev.nitjsefnie.cultivated.config.CultivatedConfig;
import dev.nitjsefnie.cultivated.data.formula.GrowthFormula;
import dev.nitjsefnie.cultivated.data.formula.YieldFormula;
import net.minecraft.util.RandomSource;
import org.junit.jupiter.api.Test;

/**
 * Phase D §D — the tier model: upgrade-chain ordering, config-backed additive speed/output, and how
 * those values flow into the growth divisor (§A.7) and totalYield (§A.8). Pure logic — no game
 * runtime; reads {@link CultivatedConfig} defaults.
 */
class TierTest {

	// ---- upgrade chain: base -> elite -> ultra -> mega -> (top) ----

	@Test
	void upgradeChainIsOrdered() {
		assertEquals(Tier.ELITE, Tier.BASE.next());
		assertEquals(Tier.ULTRA, Tier.ELITE.next());
		assertEquals(Tier.MEGA, Tier.ULTRA.next());
		assertNull(Tier.MEGA.next(), "mega is the top of the chain");
	}

	@Test
	void baseIsTheOnlyBaseTier() {
		assertTrue(Tier.BASE.isBase());
		assertFalse(Tier.ELITE.isBase());
		assertFalse(Tier.ULTRA.isBase());
		assertFalse(Tier.MEGA.isBase());
	}

	// ---- config-backed additive speed/output (defaults elite 2/2, ultra 6/3, mega 10/4) ----

	@Test
	void tierSpeedAndOutputMatchConfigDefaults() {
		assertEquals(0, Tier.BASE.speed());
		assertEquals(0, Tier.BASE.output());
		assertEquals(2, Tier.ELITE.speed());
		assertEquals(2, Tier.ELITE.output());
		assertEquals(6, Tier.ULTRA.speed());
		assertEquals(3, Tier.ULTRA.output());
		assertEquals(10, Tier.MEGA.speed());
		assertEquals(4, Tier.MEGA.output());
	}

	@Test
	void tierSpeedTracksConfig() {
		final int original = CultivatedConfig.eliteSpeed;
		try {
			CultivatedConfig.eliteSpeed = 5;
			assertEquals(5, Tier.ELITE.speed(), "tier reads config live");
		} finally {
			CultivatedConfig.eliteSpeed = original;
		}
	}

	// ---- id prefix (base has none; tiers are their lowercase name) ----

	@Test
	void idPrefix() {
		assertEquals("", Tier.BASE.idPrefix());
		assertEquals("elite", Tier.ELITE.idPrefix());
		assertEquals("ultra", Tier.ULTRA.idPrefix());
		assertEquals("mega", Tier.MEGA.idPrefix());
	}

	// ---- config gate names (base keeps the Phase B names; tiers use the can_craft_<tier>_<type> grid) ----

	@Test
	void craftGateNames() {
		assertEquals("can_craft_basic_pots", Tier.BASE.craftGate(PotType.BASIC));
		assertEquals("can_craft_hopper_pots", Tier.BASE.craftGate(PotType.HOPPER));
		assertEquals("can_wax_pots", Tier.BASE.craftGate(PotType.WAXED));

		assertEquals("can_craft_elite_basic_pots", Tier.ELITE.craftGate(PotType.BASIC));
		assertEquals("can_craft_ultra_hopper_pots", Tier.ULTRA.craftGate(PotType.HOPPER));
		assertEquals("can_craft_mega_waxed_pots", Tier.MEGA.craftGate(PotType.WAXED));
	}

	// ---- additive semantics: speed feeds the divisor, output feeds totalYield (§A.7/§A.8) ----

	@Test
	void speedAddsToDivisorSoEliteIsAboutThreeTimesFaster() {
		final int growTime = 300;
		// Base pot: divisor = global(1) + 0 + 0 + 0 = 1 -> full grow time.
		final int baseTicks = GrowthFormula.requiredGrowthTicks(growTime, 0.0, 0.0, Tier.BASE.speed());
		assertEquals(300, baseTicks);
		// Elite pot: divisor = 1 + elite speed(2) = 3 -> ~1/3 the ticks (≈ 3× base speed).
		final int eliteTicks = GrowthFormula.requiredGrowthTicks(growTime, 0.0, 0.0, Tier.ELITE.speed());
		assertEquals(100, eliteTicks);
		// Mega pot: divisor = 1 + mega speed(10) = 11.
		final int megaTicks = GrowthFormula.requiredGrowthTicks(growTime, 0.0, 0.0, Tier.MEGA.speed());
		assertEquals(300 / 11, megaTicks);
	}

	@Test
	void outputAddsToTotalYield() {
		final double cropYield = 1.0;
		final double yieldScale = 1.0;
		// Elite output(2) added additively (scaled by yield_scale): 1 + 1*(2+0+0) = 3.
		final double eliteTotal = YieldFormula.totalYield(cropYield, yieldScale, Tier.ELITE.output(), 0.0, 0.0);
		assertEquals(3.0, eliteTotal, 1.0e-9);
		// Base contributes nothing.
		final double baseTotal = YieldFormula.totalYield(cropYield, yieldScale, Tier.BASE.output(), 0.0, 0.0);
		assertEquals(1.0, baseTotal, 1.0e-9);
	}

	@Test
	void yieldRollsScaleWithTierOutput() {
		// A deterministic random: totalYield 3.0 -> exactly 3 guaranteed rolls (no fractional part).
		final RandomSource random = RandomSource.create(0L);
		final double total = YieldFormula.totalYield(1.0, 1.0, Tier.ELITE.output(), 0.0, 0.0);
		assertEquals(3, YieldFormula.rolls(random, total));
	}

	// ---- tooltip pot% line: the tier output surfaces as a non-zero "Pot" breakdown for tiered pots ----

	@Test
	void tierOutputSurfacesInYieldBreakdownPotLine() {
		final YieldBreakdown base = PotTooltipFormatting.yieldBreakdown(1.0, 1.0, Tier.BASE.output(), 0.0, 0.0);
		assertEquals(0.0, base.pot(), 1.0e-9);
		final YieldBreakdown mega = PotTooltipFormatting.yieldBreakdown(1.0, 1.0, Tier.MEGA.output(), 0.0, 0.0);
		assertEquals(4.0, mega.pot(), 1.0e-9);
	}
}
