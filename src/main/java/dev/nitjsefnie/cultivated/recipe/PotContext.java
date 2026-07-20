package dev.nitjsefnie.cultivated.recipe;

import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * Phase B §B.4 — the input a pot recipe matches against. This is the minimal seam the Phase-A
 * data engine needs; the full live context (world writes, loot params, mcfunctions) and the
 * client display context are Phase B. Slots follow the pot layout: {@code SOIL=0, SEED=1,
 * TOOL=2}.
 */
public interface PotContext extends RecipeInput {
	int SOIL = 0;
	int SEED = 1;
	int TOOL = 2;

	ItemStack getSoilStack();

	ItemStack getSeedStack();

	ItemStack getToolStack();

	/** The item currently in the interacting player's hand (empty if none / not interacting). */
	ItemStack getHeldItem();

	/** The level, or {@code null} for a client display context with no world. */
	@Nullable
	Level getLevel();

	/** True on the logical server (where world writes and loot rolls are permitted). */
	boolean isServerSide();

	/** The crop's required growth ticks under the pot's current modifiers (§A.7), or 0 if unknown. */
	int getRequiredGrowthTicks();

	/** The pot's world position, or {@code null} for a world-less context (cache indexing / display). */
	@Nullable
	default BlockPos getPotPos() {
		return null;
	}

	/** The pot's block state, or {@code null} for a world-less context. */
	@Nullable
	default BlockState getPotState() {
		return null;
	}

	/** The pot's currently resolved crop (override or recipe), or {@code null} if none / unknown. */
	@Nullable
	default CropRecipe getCrop() {
		return null;
	}

	/** The pot's currently resolved soil (override or recipe), or {@code null} if none / unknown. */
	@Nullable
	default SoilRecipe getSoil() {
		return null;
	}

	/**
	 * The tool used when rolling harvest loot (the pot's tool slot, or an empty stack if absent).
	 * Feeds silk-touch/fortune-style loot conditions (§A.6).
	 */
	default ItemStack getHarvestTool() {
		return this.getToolStack();
	}

	/**
	 * Roll a block state's own loot table with the pot's live loot params (§A.6). Returns an empty
	 * list for a world-less context; live contexts roll against the server loot registry.
	 */
	default List<ItemStack> rollBlockDrops(final BlockState dropState, final RandomSource random) {
		return List.of();
	}

	/** Roll an explicit loot table id with the pot's live loot params (§A.6). */
	default List<ItemStack> rollLootTable(final Identifier tableId, final RandomSource random) {
		return List.of();
	}

	/** Roll a living entity's death loot table, derived from NBT (§A.6). */
	default List<ItemStack> rollEntityDrops(final CompoundTag entity, final Optional<Identifier> damageSource, final RandomSource random) {
		return List.of();
	}

	/** Run a crop's optional mcfunction (§B.3), server-side, positioned at the pot. No-op otherwise. */
	default void runFunction(final Identifier functionId) {
	}

	@Override
	default ItemStack getItem(final int index) {
		return switch (index) {
			case SOIL -> this.getSoilStack();
			case SEED -> this.getSeedStack();
			case TOOL -> this.getToolStack();
			default -> ItemStack.EMPTY;
		};
	}

	@Override
	default int size() {
		return 3;
	}
}
