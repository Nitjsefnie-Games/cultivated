package dev.nitjsefnie.cultivated.block;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
 * R3d — hopper auto-harvest overflow is DISCARDED, not dropped into the world. Exercises the pure
 * {@link PotMechanics#fillStorage} merge (the extractable half of {@code insertIntoStorage}): it fits
 * what the storage buffer can take and REPORTS the overflow count, which the hopper path then voids
 * (it no longer calls {@code Block.popResource}, so nothing lands in the world). Needs the vanilla
 * registries to construct {@link ItemStack}s.
 */
class StorageBufferFillTest {

	private static final int CONTAINER_MAX = 64;

	@BeforeAll
	static void boot() {
		CultivatedTestBootstrap.bootstrap();
		// Bind item data components so ItemStack#getMaxStackSize (a MAX_STACK_SIZE component read) resolves.
		final HolderLookup.Provider provider = VanillaRegistries.createLookup();
		BuiltInRegistries.DATA_COMPONENT_INITIALIZERS.build(provider).forEach(DataComponentInitializers.PendingComponents::apply);
	}

	private static NonNullList<ItemStack> emptyBuffer() {
		return NonNullList.withSize(PotMechanics.SIZE, ItemStack.EMPTY);
	}

	@Test
	void emptyBuffer_takesEverythingNoOverflow() {
		final NonNullList<ItemStack> items = emptyBuffer();
		final int overflow = PotMechanics.fillStorage(items, new ItemStack(Items.WHEAT, 40), CONTAINER_MAX);
		assertEquals(0, overflow, "an empty buffer swallows the whole drop");
		assertEquals(40, items.get(PotMechanics.FIRST_STORAGE).getCount(), "all 40 land in the first storage slot");
	}

	@Test
	void partialBuffer_keepsWhatFitsAndReportsRestAsOverflow() {
		final NonNullList<ItemStack> items = emptyBuffer();
		// Fill 11 of the 12 storage slots completely, leave the last with room for exactly 10.
		for (int slot = PotMechanics.FIRST_STORAGE; slot < PotMechanics.LAST_STORAGE; slot++) {
			items.set(slot, new ItemStack(Items.WHEAT, CONTAINER_MAX));
		}
		items.set(PotMechanics.LAST_STORAGE, new ItemStack(Items.WHEAT, 54)); // room for 10

		final int overflow = PotMechanics.fillStorage(items, new ItemStack(Items.WHEAT, 30), CONTAINER_MAX);

		assertEquals(20, overflow, "only 10 of the 30 fit; the other 20 are overflow (to be discarded)");
		assertEquals(CONTAINER_MAX, items.get(PotMechanics.LAST_STORAGE).getCount(), "the last slot tops up to full");
	}

	@Test
	void completelyFullBuffer_reportsEntireDropAsOverflow() {
		final NonNullList<ItemStack> items = emptyBuffer();
		for (int slot = PotMechanics.FIRST_STORAGE; slot <= PotMechanics.LAST_STORAGE; slot++) {
			items.set(slot, new ItemStack(Items.WHEAT, CONTAINER_MAX));
		}
		final int overflow = PotMechanics.fillStorage(items, new ItemStack(Items.WHEAT, 12), CONTAINER_MAX);
		assertEquals(12, overflow, "a full buffer accepts nothing; the whole drop overflows (all voided)");
	}

	@Test
	void differentItemDoesNotMergeIntoAnExistingStack() {
		final NonNullList<ItemStack> items = emptyBuffer();
		// One free slot; the rest full of a DIFFERENT item so carrots can only use the single empty slot.
		for (int slot = PotMechanics.FIRST_STORAGE; slot < PotMechanics.LAST_STORAGE; slot++) {
			items.set(slot, new ItemStack(Items.WHEAT, CONTAINER_MAX));
		}
		final int overflow = PotMechanics.fillStorage(items, new ItemStack(Items.CARROT, 70), CONTAINER_MAX);
		assertEquals(6, overflow, "one empty slot fits 64 carrots; the remaining 6 overflow");
		assertEquals(Items.CARROT, items.get(PotMechanics.LAST_STORAGE).getItem(), "carrots take the one empty slot");
		assertEquals(CONTAINER_MAX, items.get(PotMechanics.LAST_STORAGE).getCount(), "that slot fills to 64");
	}

	@Test
	void overflowAgreesWithStorageBufferHasRoom() {
		// A buffer that storageBufferHasRoom() calls full must accept nothing (whole drop overflows),
		// keeping the R2d pause decision consistent with what a harvest could actually deposit.
		final NonNullList<ItemStack> items = emptyBuffer();
		final int[] free = new int[PotMechanics.STORAGE_COUNT];
		for (int slot = PotMechanics.FIRST_STORAGE; slot <= PotMechanics.LAST_STORAGE; slot++) {
			items.set(slot, new ItemStack(Items.WHEAT, CONTAINER_MAX));
			free[slot - PotMechanics.FIRST_STORAGE] = 0;
		}
		assertTrue(!PotMechanics.storageBufferHasRoom(free), "buffer reports no room");
		assertEquals(8, PotMechanics.fillStorage(items, new ItemStack(Items.WHEAT, 8), CONTAINER_MAX),
			"and fillStorage deposits nothing, so the pause and the fill agree");
	}
}
