package dev.nitjsefnie.cultivated.compat;

import java.util.List;
import net.minecraft.world.item.ItemStack;

/**
 * A viewer-agnostic snapshot of one growing recipe for recipe browsers (JEI/REI): the seed(s) that
 * plant it, the soil(s) the crop accepts, the possible harvest drops, and its base grow time in game
 * ticks. Deliberately carries only plain {@link ItemStack} lists and primitives so it can be shared by
 * both the JEI and REI plugins without either viewer's API leaking into this package.
 *
 * <p>{@code anySpawnEgg} marks the single generic "growable mob" crop, whose seed slot cycles through
 * every spawn egg and whose per-mob drops cannot be enumerated statically (so {@link #drops()} is empty
 * for it — the category renders a note instead).
 */
public record CropView(
	List<ItemStack> seeds,
	List<ItemStack> soils,
	List<ItemStack> drops,
	int growTicks,
	boolean anySpawnEgg
) {
}
