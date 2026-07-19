package dev.nitjsefnie.cultivated.recipe;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

/**
 * A minimal immutable {@link PotContext} with no world backing — used for cache indexing (a bare
 * soil/seed/held item) and for tests. The Phase-B live and display contexts are separate.
 */
public record SimplePotContext(
	ItemStack soil, ItemStack seed, ItemStack tool, ItemStack held, @Nullable Level level, boolean serverSide, int requiredGrowthTicks
) implements PotContext {
	public static SimplePotContext empty() {
		return new SimplePotContext(ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY, null, false, 0);
	}

	public static SimplePotContext ofSoil(final ItemStack soil) {
		return new SimplePotContext(soil, ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY, null, false, 0);
	}

	public static SimplePotContext ofSeed(final ItemStack seed) {
		return new SimplePotContext(ItemStack.EMPTY, seed, ItemStack.EMPTY, ItemStack.EMPTY, null, false, 0);
	}

	public static SimplePotContext ofHeld(final ItemStack held) {
		return new SimplePotContext(ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY, held, null, false, 0);
	}

	@Override
	public ItemStack getSoilStack() {
		return this.soil;
	}

	@Override
	public ItemStack getSeedStack() {
		return this.seed;
	}

	@Override
	public ItemStack getToolStack() {
		return this.tool;
	}

	@Override
	public ItemStack getHeldItem() {
		return this.held;
	}

	@Override
	@Nullable
	public Level getLevel() {
		return this.level;
	}

	@Override
	public boolean isServerSide() {
		return this.serverSide;
	}

	@Override
	public int getRequiredGrowthTicks() {
		return this.requiredGrowthTicks;
	}
}
