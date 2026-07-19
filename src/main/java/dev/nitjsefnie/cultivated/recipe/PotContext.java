package dev.nitjsefnie.cultivated.recipe;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.level.Level;
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
