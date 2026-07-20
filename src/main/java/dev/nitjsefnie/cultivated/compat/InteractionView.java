package dev.nitjsefnie.cultivated.compat;

import java.util.List;
import net.minecraft.world.item.ItemStack;

/**
 * A viewer-agnostic snapshot of one {@code cultivated:pot_interaction} recipe for recipe browsers: the
 * held item(s) that trigger it, the soil/seed the pot must already contain (empty when unconstrained),
 * and the soil/seed the pot ends up holding afterwards. Carries only plain {@link ItemStack} lists and
 * primitives so it is shared by both the JEI and REI plugins.
 */
public record InteractionView(
	List<ItemStack> held,
	List<ItemStack> requiredSoil,
	List<ItemStack> requiredSeed,
	List<ItemStack> resultSoil,
	List<ItemStack> resultSeed,
	boolean consumesHeld,
	boolean damagesHeld
) {
}
