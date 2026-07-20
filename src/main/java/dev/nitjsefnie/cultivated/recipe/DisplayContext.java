package dev.nitjsefnie.cultivated.recipe;

import java.util.List;
import java.util.Optional;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * Phase B §B.4 — a client-only, immutable {@link PotContext} backed by a simulated inventory: the
 * display-simulation context that asks "what would this pot do?" without touching a live world. It
 * carries the resolved crop/soil and the required growth ticks for display, but performs no world
 * writes: loot rolls and mcfunctions throw, because a display context has no server to run them on.
 *
 * <p>Reserved for the Phase C JEI / recipe-viewer and GUI display simulation (§C, §F.4). The Phase C
 * block-entity renderer reads the live block entity directly rather than through this context; this
 * type is the world-less context those later display surfaces resolve crop/soil against.
 */
public record DisplayContext(
	ItemStack soil,
	ItemStack seed,
	ItemStack tool,
	ItemStack held,
	@Nullable Level level,
	@Nullable CropRecipe crop,
	@Nullable SoilRecipe soilRecipe,
	int requiredGrowthTicks
) implements PotContext {
	/** A display context for an empty pot with no world. */
	public static DisplayContext empty() {
		return new DisplayContext(ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY, null, null, null, 0);
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
		return false;
	}

	@Override
	public int getRequiredGrowthTicks() {
		return this.requiredGrowthTicks;
	}

	@Override
	@Nullable
	public CropRecipe getCrop() {
		return this.crop;
	}

	@Override
	@Nullable
	public SoilRecipe getSoil() {
		return this.soilRecipe;
	}

	@Override
	public List<ItemStack> rollBlockDrops(final BlockState dropState, final RandomSource random) {
		throw new UnsupportedOperationException("DisplayContext cannot roll loot");
	}

	@Override
	public List<ItemStack> rollLootTable(final Identifier tableId, final RandomSource random) {
		throw new UnsupportedOperationException("DisplayContext cannot roll loot");
	}

	@Override
	public List<ItemStack> rollEntityDrops(
		final CompoundTag entity, final Optional<Identifier> damageSource, final boolean finalizeSpawn, final RandomSource random
	) {
		throw new UnsupportedOperationException("DisplayContext cannot roll loot");
	}

	@Override
	public void runFunction(final Identifier functionId) {
		throw new UnsupportedOperationException("DisplayContext cannot run functions");
	}
}
