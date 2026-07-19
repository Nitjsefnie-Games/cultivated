package dev.nitjsefnie.cultivated.config;

import java.util.Map;

/**
 * Phase A/F — minimal gameplay config holder. The full annotation-driven, commented-JSON loader
 * is Phase F (§F.1); for the Phase-A data engine this exposes the default values that the growth
 * (§A.7) and yield (§A.8) formulas and the {@code cultivated:config} load condition (§A.9) read.
 *
 * <p>Fields are mutable statics so a later Phase-F loader can overwrite them from disk.
 */
public final class CultivatedConfig {
	// Gameplay (§F.1)
	public static float globalGrowthModifier = 1.0f;
	public static boolean damageHarvestTool = true;
	public static float efficiencyGrowthModifier = 0.05f;

	// Recipe/crafting gates, addressed by name via the cultivated:config load condition (§A.9).
	public static boolean canCraftBasicPots = true;
	public static boolean canCraftHopperPots = true;
	public static boolean canWaxPots = true;

	// Visuals (§C.7)
	public static double potViewDistance = 48.0;
	public static boolean useGrowthAnimation = true;
	public static boolean renderSoil = true;
	public static boolean renderCrop = true;

	private CultivatedConfig() {
	}

	/**
	 * Look up a named boolean config property (used by {@code cultivated:config}). Unknown
	 * property names resolve to {@code false} so a mis-typed gate never silently enables content.
	 */
	public static boolean booleanProperty(final String name) {
		return BOOLEAN_PROPERTIES.getOrDefault(name, () -> false).getAsBoolean();
	}

	@FunctionalInterface
	private interface BoolGetter {
		boolean getAsBoolean();
	}

	private static final Map<String, BoolGetter> BOOLEAN_PROPERTIES = Map.of(
		"can_craft_basic_pots", () -> canCraftBasicPots,
		"can_craft_hopper_pots", () -> canCraftHopperPots,
		"can_wax_pots", () -> canWaxPots,
		"damage_harvest_tool", () -> damageHarvestTool
	);
}
