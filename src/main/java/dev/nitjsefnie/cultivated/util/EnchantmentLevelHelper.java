package dev.nitjsefnie.cultivated.util;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.minecraft.core.Holder;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.ItemEnchantments;

/**
 * Phase G #9 — read the highest / first enchantment level among the enchantments belonging to a
 * given enchantment {@link TagKey} on a stack. Used for the growth-boost enchantment (§A.7) and
 * the harvest-damage-negation enchantment (§B.5).
 */
public final class EnchantmentLevelHelper {
	private EnchantmentLevelHelper() {
	}

	/** Highest level among enchantments on the stack that are members of {@code tag}; 0 if none. */
	public static int highestLevel(final ItemStack stack, final TagKey<Enchantment> tag) {
		if (stack.isEmpty()) {
			return 0;
		}
		final ItemEnchantments enchantments = EnchantmentHelper.getEnchantmentsForCrafting(stack);
		int best = 0;
		for (final Object2IntMap.Entry<Holder<Enchantment>> entry : enchantments.entrySet()) {
			if (entry.getKey().is(tag)) {
				best = Math.max(best, entry.getIntValue());
			}
		}
		return best;
	}

	/** True if the stack carries any enchantment in {@code tag}. */
	public static boolean hasAny(final ItemStack stack, final TagKey<Enchantment> tag) {
		return highestLevel(stack, tag) > 0;
	}
}
