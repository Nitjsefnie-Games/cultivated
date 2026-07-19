package dev.nitjsefnie.cultivated.data.formula;

import dev.nitjsefnie.cultivated.config.CultivatedConfig;
import dev.nitjsefnie.cultivated.registry.ModTags;
import dev.nitjsefnie.cultivated.util.EnchantmentLevelHelper;
import net.minecraft.world.item.ItemStack;

/**
 * Phase A §A.7 — growth-time formula. Higher divisor → fewer required ticks → faster growth.
 *
 * <pre>
 * divisor = global_growth_modifier + soil.growth_modifier + tool_efficiency_modifier + pot.growth_modifier
 * requiredGrowthTicks = floor(crop.grow_time / divisor)
 * </pre>
 */
public final class GrowthFormula {
	private GrowthFormula() {
	}

	/**
	 * Growth added by the harvest tool: {@code highestEnchantLevel(increase_pot_growth) *
	 * efficiency_growth_modifier + attributeValue(cultivated:growth)} (§A.7). The item-attribute
	 * sum is supplied by the caller (Phase B reads it from the live tool stack).
	 */
	public static double toolEfficiencyModifier(final ItemStack tool, final double growthAttributeSum) {
		if (tool == null || tool.isEmpty()) {
			return growthAttributeSum;
		}
		final int level = EnchantmentLevelHelper.highestLevel(tool, ModTags.INCREASE_POT_GROWTH);
		return level * CultivatedConfig.efficiencyGrowthModifier + growthAttributeSum;
	}

	/** The full growth divisor (§A.7). */
	public static double divisor(final double soilGrowthModifier, final double toolEfficiencyModifier, final double potGrowthModifier) {
		return CultivatedConfig.globalGrowthModifier + soilGrowthModifier + toolEfficiencyModifier + potGrowthModifier;
	}

	/** Required ticks to mature: {@code floor(grow_time / divisor)}, minimum 1. */
	public static int requiredGrowthTicks(final int cropGrowTime, final double divisor) {
		if (divisor <= 0.0) {
			return cropGrowTime;
		}
		return Math.max(1, (int)Math.floor(cropGrowTime / divisor));
	}

	/** Convenience: compute required ticks from the raw modifier inputs. */
	public static int requiredGrowthTicks(
		final int cropGrowTime, final double soilGrowthModifier, final double toolEfficiencyModifier, final double potGrowthModifier
	) {
		return requiredGrowthTicks(cropGrowTime, divisor(soilGrowthModifier, toolEfficiencyModifier, potGrowthModifier));
	}
}
