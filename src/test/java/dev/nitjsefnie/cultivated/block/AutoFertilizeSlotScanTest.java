package dev.nitjsefnie.cultivated.block;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.nitjsefnie.cultivated.CultivatedTestBootstrap;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponentInitializers;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Hopper auto-fertilize slot scan (§B.5) — the pure half of
 * {@code BotanyPotBlockEntity#autoFertilizeFromInputs}: {@link PotMechanics#nextNonEmptyFertilizerSlot}
 * walks the fertilizer input region (15..26) returning the first non-empty slot at or after a start
 * slot, or -1 when none remains, so the BE can skip past stacks that match no fertilizer recipe.
 * Needs the vanilla registries to construct non-empty {@link ItemStack}s.
 */
class AutoFertilizeSlotScanTest {

	@BeforeAll
	static void boot() {
		CultivatedTestBootstrap.bootstrap();
		// Bind item data components so constructing ItemStacks resolves their component map.
		final HolderLookup.Provider provider = VanillaRegistries.createLookup();
		BuiltInRegistries.DATA_COMPONENT_INITIALIZERS.build(provider).forEach(DataComponentInitializers.PendingComponents::apply);
	}

	private static NonNullList<ItemStack> emptyContainer() {
		return NonNullList.withSize(PotMechanics.SIZE, ItemStack.EMPTY);
	}

	@Test
	void emptyRegion_findsNothing() {
		assertEquals(-1, PotMechanics.nextNonEmptyFertilizerSlot(emptyContainer(), PotMechanics.FERTILIZER_INPUT_FIRST));
	}

	@Test
	void findsFirstNonEmptyInputSlot() {
		final NonNullList<ItemStack> items = emptyContainer();
		items.set(PotMechanics.FERTILIZER_INPUT_FIRST, new ItemStack(Items.BONE_MEAL, 3));
		assertEquals(PotMechanics.FERTILIZER_INPUT_FIRST,
			PotMechanics.nextNonEmptyFertilizerSlot(items, PotMechanics.FERTILIZER_INPUT_FIRST));
	}

	@Test
	void skipsEmptySlots() {
		final NonNullList<ItemStack> items = emptyContainer();
		items.set(20, new ItemStack(Items.BONE_MEAL, 1));
		assertEquals(20, PotMechanics.nextNonEmptyFertilizerSlot(items, PotMechanics.FERTILIZER_INPUT_FIRST));
	}

	@Test
	void ignoresItemsOutsideTheInputRegion() {
		final NonNullList<ItemStack> items = emptyContainer();
		// Soil/seed/tool and storage slots are never fertilizer candidates, even when occupied.
		items.set(PotMechanics.TOOL, new ItemStack(Items.WOODEN_HOE, 1));
		items.set(PotMechanics.FIRST_STORAGE, new ItemStack(Items.WHEAT, 10));
		items.set(PotMechanics.LAST_STORAGE, new ItemStack(Items.WHEAT, 10));
		assertEquals(-1, PotMechanics.nextNonEmptyFertilizerSlot(items, PotMechanics.FERTILIZER_INPUT_FIRST));
	}

	@Test
	void fromSlotResumesTheScan() {
		// The BE's skip loop: after slot 15 matched no recipe, resume at 16 and find the next candidate.
		final NonNullList<ItemStack> items = emptyContainer();
		items.set(PotMechanics.FERTILIZER_INPUT_FIRST, new ItemStack(Items.STICK, 1)); // a non-fertilizer stray
		items.set(PotMechanics.FERTILIZER_INPUT_LAST, new ItemStack(Items.BONE_MEAL, 2));
		final int first = PotMechanics.nextNonEmptyFertilizerSlot(items, PotMechanics.FERTILIZER_INPUT_FIRST);
		assertEquals(PotMechanics.FERTILIZER_INPUT_FIRST, first);
		assertEquals(PotMechanics.FERTILIZER_INPUT_LAST, PotMechanics.nextNonEmptyFertilizerSlot(items, first + 1));
		assertEquals(-1, PotMechanics.nextNonEmptyFertilizerSlot(items, PotMechanics.FERTILIZER_INPUT_LAST + 1));
	}
}
