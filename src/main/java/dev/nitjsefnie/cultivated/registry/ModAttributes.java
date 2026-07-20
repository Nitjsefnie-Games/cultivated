package dev.nitjsefnie.cultivated.registry;

import dev.nitjsefnie.cultivated.Cultivated;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.RangedAttribute;

/**
 * Phase A appendix — item attributes {@code cultivated:growth} and {@code cultivated:yield} that a
 * harvest tool may carry modifiers for, feeding the growth (§A.7) and yield (§A.8) formulas.
 */
public final class ModAttributes {
	public static Holder<Attribute> GROWTH;
	public static Holder<Attribute> YIELD;

	private ModAttributes() {
	}

	private static Holder<Attribute> register(final String name, final Attribute attribute) {
		return Registry.registerForHolder(BuiltInRegistries.ATTRIBUTE, Cultivated.id(name), attribute);
	}

	/**
	 * Registers the {@code cultivated:growth} and {@code cultivated:yield} attributes and binds the
	 * {@link #GROWTH}/{@link #YIELD} holders the growth (§A.7) and yield (§A.8) formulas resolve. Called
	 * first in {@code CultivatedRegistries} so the holders are non-null before any formula runs.
	 */
	public static void register() {
		GROWTH = register("growth", new RangedAttribute("attribute.name.cultivated.growth", 0.0, -1024.0, 1024.0));
		YIELD = register("yield", new RangedAttribute("attribute.name.cultivated.yield", 0.0, -1024.0, 1024.0));
	}
}
