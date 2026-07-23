package dev.nitjsefnie.cultivated.block;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

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
 * Hopper-pot right-click fertilizer deposit — the pure {@link PotMechanics#depositFertilizer} merge
 * into the fertilizer input region (slots 15..26). The block entity places the returned count and
 * shrinks the player's held stack (unless creative); the auto-fertilize tick then consumes the
 * deposited items. Needs the vanilla registries to construct {@link ItemStack}s.
 */
class FertilizerDepositTest {

	private static final int CONTAINER_MAX = 64;

	@BeforeAll
	static void boot() {
		CultivatedTestBootstrap.bootstrap();
		// Bind item data components so ItemStack#getMaxStackSize (a MAX_STACK_SIZE component read) resolves.
		final HolderLookup.Provider provider = VanillaRegistries.createLookup();
		BuiltInRegistries.DATA_COMPONENT_INITIALIZERS.build(provider).forEach(DataComponentInitializers.PendingComponents::apply);
	}

	private static NonNullList<ItemStack> emptyPot() {
		return NonNullList.withSize(PotMechanics.SIZE, ItemStack.EMPTY);
	}

	private static void assertOutsideRegionUntouched(final NonNullList<ItemStack> items) {
		for (int slot = 0; slot < PotMechanics.SIZE; slot++) {
			if (!PotMechanics.isFertilizerInputSlot(slot)) {
				assertEquals(ItemStack.EMPTY, items.get(slot), "slot " + slot + " is outside 15..26 and must stay empty");
			}
		}
	}

	@Test
	void emptyRegion_fillsFromFirstSlot() {
		final NonNullList<ItemStack> items = emptyPot();
		final ItemStack held = new ItemStack(Items.BONE_MEAL, 40);
		final int placed = PotMechanics.depositFertilizer(items, held, CONTAINER_MAX);
		assertEquals(40, placed, "an empty region takes the whole stack");
		assertEquals(40, items.get(PotMechanics.FERTILIZER_INPUT_FIRST).getCount(), "all 40 land in slot 15");
		assertEquals(40, held.getCount(), "the held stack itself is NOT mutated — the caller shrinks");
	}

	@Test
	void matchingPartialSlot_isToppedUpFirst() {
		final NonNullList<ItemStack> items = emptyPot();
		items.set(PotMechanics.FERTILIZER_INPUT_FIRST + 3, new ItemStack(Items.BONE_MEAL, 60));
		final int placed = PotMechanics.depositFertilizer(items, new ItemStack(Items.BONE_MEAL, 10), CONTAINER_MAX);
		assertEquals(10, placed, "4 top up the partial stack, the other 6 need an empty slot");
		assertEquals(CONTAINER_MAX, items.get(PotMechanics.FERTILIZER_INPUT_FIRST + 3).getCount(), "the partial slot fills to 64");
		assertEquals(6, items.get(PotMechanics.FERTILIZER_INPUT_FIRST).getCount(), "the remainder takes the first empty slot");
	}

	@Test
	void respectsItemMaxStackSize() {
		final NonNullList<ItemStack> items = emptyPot();
		// Eggs stack to 16: 20 held eggs split into a full slot of 16 plus 4 in the next slot.
		final int placed = PotMechanics.depositFertilizer(items, new ItemStack(Items.EGG, 20), CONTAINER_MAX);
		assertEquals(20, placed);
		assertEquals(16, items.get(PotMechanics.FERTILIZER_INPUT_FIRST).getCount(), "first slot caps at the item's max of 16");
		assertEquals(4, items.get(PotMechanics.FERTILIZER_INPUT_FIRST + 1).getCount(), "the rest spills into the next slot");
	}

	@Test
	void fullRegion_placesNothing() {
		final NonNullList<ItemStack> items = emptyPot();
		for (int slot = PotMechanics.FERTILIZER_INPUT_FIRST; slot <= PotMechanics.FERTILIZER_INPUT_LAST; slot++) {
			items.set(slot, new ItemStack(Items.BONE_MEAL, CONTAINER_MAX));
		}
		final ItemStack held = new ItemStack(Items.BONE_MEAL, 12);
		assertEquals(0, PotMechanics.depositFertilizer(items, held, CONTAINER_MAX), "a full region accepts nothing");
		assertEquals(12, held.getCount(), "the held stack stays whole for the caller");
	}

	@Test
	void differentItem_onlyUsesEmptySlots() {
		final NonNullList<ItemStack> items = emptyPot();
		items.set(PotMechanics.FERTILIZER_INPUT_FIRST, new ItemStack(Items.BONE, CONTAINER_MAX));
		final int placed = PotMechanics.depositFertilizer(items, new ItemStack(Items.BONE_MEAL, 30), CONTAINER_MAX);
		assertEquals(30, placed);
		assertEquals(Items.BONE, items.get(PotMechanics.FERTILIZER_INPUT_FIRST).getItem(), "a non-matching stack is not merged into");
		assertEquals(30, items.get(PotMechanics.FERTILIZER_INPUT_FIRST + 1).getCount(), "the deposit skips to the first empty slot");
	}

	@Test
	void neverTouchesSlotsOutsideTheFertilizerRegion() {
		final NonNullList<ItemStack> items = emptyPot();
		assertEquals(64, PotMechanics.depositFertilizer(items, new ItemStack(Items.BONE_MEAL, 64), CONTAINER_MAX));
		assertOutsideRegionUntouched(items);
	}

	@Test
	void emptyHeldStack_placesNothing() {
		final NonNullList<ItemStack> items = emptyPot();
		assertEquals(0, PotMechanics.depositFertilizer(items, ItemStack.EMPTY, CONTAINER_MAX));
		assertSame(ItemStack.EMPTY, items.get(PotMechanics.FERTILIZER_INPUT_FIRST), "nothing is written for an empty held stack");
	}
}
