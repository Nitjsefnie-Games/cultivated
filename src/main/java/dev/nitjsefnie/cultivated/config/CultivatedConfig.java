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

	// Tier modifiers (§D). Both are ADDITIVE — speed into the growth divisor (§A.7), output into
	// totalYield (§A.8) — not direct multipliers. Read live by the Tier enum.
	public static int eliteSpeed = 2;
	public static int eliteOutput = 2;
	public static int ultraSpeed = 6;
	public static int ultraOutput = 3;
	public static int megaSpeed = 10;
	public static int megaOutput = 4;

	// Per-tier crafting gates (§D / §A.9), one per tier × pot type, resolved by name like the base
	// gates above. Names follow Tier#craftGate: can_craft_<tier>_{basic,hopper,waxed}_pots.
	public static boolean canCraftEliteBasicPots = true;
	public static boolean canCraftEliteHopperPots = true;
	public static boolean canCraftEliteWaxedPots = true;
	public static boolean canCraftUltraBasicPots = true;
	public static boolean canCraftUltraHopperPots = true;
	public static boolean canCraftUltraWaxedPots = true;
	public static boolean canCraftMegaBasicPots = true;
	public static boolean canCraftMegaHopperPots = true;
	public static boolean canCraftMegaWaxedPots = true;

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

	private static final Map<String, BoolGetter> BOOLEAN_PROPERTIES = Map.ofEntries(
		Map.entry("can_craft_basic_pots", (BoolGetter) () -> canCraftBasicPots),
		Map.entry("can_craft_hopper_pots", (BoolGetter) () -> canCraftHopperPots),
		Map.entry("can_wax_pots", (BoolGetter) () -> canWaxPots),
		Map.entry("damage_harvest_tool", (BoolGetter) () -> damageHarvestTool),
		Map.entry("can_craft_elite_basic_pots", (BoolGetter) () -> canCraftEliteBasicPots),
		Map.entry("can_craft_elite_hopper_pots", (BoolGetter) () -> canCraftEliteHopperPots),
		Map.entry("can_craft_elite_waxed_pots", (BoolGetter) () -> canCraftEliteWaxedPots),
		Map.entry("can_craft_ultra_basic_pots", (BoolGetter) () -> canCraftUltraBasicPots),
		Map.entry("can_craft_ultra_hopper_pots", (BoolGetter) () -> canCraftUltraHopperPots),
		Map.entry("can_craft_ultra_waxed_pots", (BoolGetter) () -> canCraftUltraWaxedPots),
		Map.entry("can_craft_mega_basic_pots", (BoolGetter) () -> canCraftMegaBasicPots),
		Map.entry("can_craft_mega_hopper_pots", (BoolGetter) () -> canCraftMegaHopperPots),
		Map.entry("can_craft_mega_waxed_pots", (BoolGetter) () -> canCraftMegaWaxedPots)
	);
}
