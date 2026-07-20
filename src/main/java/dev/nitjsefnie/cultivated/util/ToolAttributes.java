package dev.nitjsefnie.cultivated.util;

import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemAttributeModifiers;

/**
 * Phase B — sum the additive item-attribute modifiers a harvest tool carries for a given attribute
 * (the {@code cultivated:growth} and {@code cultivated:yield} attributes feed the growth §A.7 and
 * yield §A.8 formulas). Reads the stack's {@link ItemAttributeModifiers} data component directly, so
 * it works for a bare stack outside an equipped-entity context.
 */
public final class ToolAttributes {
	private ToolAttributes() {
	}

	/** Sum of the {@code amount}s of every modifier on {@code stack} for {@code attribute}; 0 if none. */
	public static double sum(final ItemStack stack, final Holder<Attribute> attribute) {
		if (stack == null || stack.isEmpty()) {
			return 0.0;
		}
		final ItemAttributeModifiers modifiers = stack.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY);
		double total = 0.0;
		for (final ItemAttributeModifiers.Entry entry : modifiers.modifiers()) {
			if (entry.attribute().equals(attribute)) {
				total += entry.modifier().amount();
			}
		}
		return total;
	}
}
