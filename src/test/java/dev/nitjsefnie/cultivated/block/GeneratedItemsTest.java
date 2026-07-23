package dev.nitjsefnie.cultivated.block;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.nitjsefnie.cultivated.CultivatedTestBootstrap;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentInitializers;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * The hopper-pot generated-item tracker (chunk 1 core): identity (same item+components ignoring
 * durability), discovery-order tracking, per-item suppression, clearing and NBT round-trip.
 * Bootstraps the registries like {@code SpawnEggCropRecipeTest} so real component-carrying stacks work.
 */
class GeneratedItemsTest {
	private static HolderLookup.Provider registries;

	@BeforeAll
	static void boot() {
		CultivatedTestBootstrap.bootstrap();
		registries = VanillaRegistries.createLookup();
		BuiltInRegistries.DATA_COMPONENT_INITIALIZERS.build(registries).forEach(DataComponentInitializers.PendingComponents::apply);
	}

	private static Holder<Enchantment> enchantment(final ResourceKey<Enchantment> key) {
		return registries.lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(key);
	}

	private static ItemStack enchantedSword(final ResourceKey<Enchantment> key, final int level) {
		final ItemStack stack = new ItemStack(Items.DIAMOND_SWORD);
		stack.enchant(enchantment(key), level);
		return stack;
	}

	// ---- identity ----

	@Test
	void sameItemAndEnchantsDifferentDamageIsOneEntry() {
		final GeneratedItems generated = new GeneratedItems();
		final ItemStack fresh = enchantedSword(Enchantments.SHARPNESS, 2);
		final ItemStack worn = fresh.copy();
		worn.setDamageValue(123);

		assertTrue(generated.filterAndTrack(fresh));
		assertTrue(generated.filterAndTrack(worn), "same item+enchants at different durability is the same entry");
		assertEquals(1, generated.view().size(), "durability must not split the entry");
	}

	@Test
	void differentEnchantsAreDistinctEntries() {
		final GeneratedItems generated = new GeneratedItems();
		assertTrue(generated.filterAndTrack(enchantedSword(Enchantments.SHARPNESS, 2)));
		assertTrue(generated.filterAndTrack(enchantedSword(Enchantments.UNBREAKING, 2)));
		assertEquals(2, generated.view().size());
	}

	@Test
	void differentItemsAreDistinctEntries() {
		final GeneratedItems generated = new GeneratedItems();
		assertTrue(generated.filterAndTrack(new ItemStack(Items.WHEAT)));
		assertTrue(generated.filterAndTrack(new ItemStack(Items.WHEAT_SEEDS)));
		assertEquals(2, generated.view().size());
	}

	@Test
	void customNamedAndPlainAreDistinctEntries() {
		final GeneratedItems generated = new GeneratedItems();
		final ItemStack named = new ItemStack(Items.WHEAT);
		named.set(DataComponents.CUSTOM_NAME, Component.literal("Fancy Wheat"));
		assertTrue(generated.filterAndTrack(new ItemStack(Items.WHEAT)));
		assertTrue(generated.filterAndTrack(named));
		assertEquals(2, generated.view().size());
	}

	@Test
	void discoveryOrderIsPreserved() {
		final GeneratedItems generated = new GeneratedItems();
		generated.filterAndTrack(new ItemStack(Items.WHEAT));
		generated.filterAndTrack(new ItemStack(Items.WHEAT_SEEDS));
		generated.filterAndTrack(new ItemStack(Items.APPLE));
		assertEquals(Items.WHEAT, generated.view().get(0).representative().getItem());
		assertEquals(Items.WHEAT_SEEDS, generated.view().get(1).representative().getItem());
		assertEquals(Items.APPLE, generated.view().get(2).representative().getItem());
	}

	@Test
	void normalizeStripsCountAndDurability() {
		final ItemStack rolled = new ItemStack(Items.DIAMOND_SWORD, 1);
		rolled.setDamageValue(50);
		final ItemStack normalized = GeneratedItems.normalize(rolled);
		assertEquals(1, normalized.getCount());
		assertFalse(normalized.has(DataComponents.DAMAGE), "the damage component must be removed");
		assertEquals(50, rolled.getDamageValue(), "normalization must copy, not mutate the roll");
	}

	// ---- filtering / suppression ----

	@Test
	void firstSightIsKeptAndRecorded() {
		final GeneratedItems generated = new GeneratedItems();
		assertTrue(generated.filterAndTrack(new ItemStack(Items.WHEAT)));
		assertEquals(1, generated.view().size());
		assertFalse(generated.view().getFirst().suppressed(), "a new item starts unsuppressed");
	}

	@Test
	void suppressedIdentityIsDroppedAndNotReAdded() {
		final GeneratedItems generated = new GeneratedItems();
		generated.filterAndTrack(new ItemStack(Items.WHEAT));
		generated.setSuppressed(0, true);

		assertFalse(generated.filterAndTrack(new ItemStack(Items.WHEAT)), "a suppressed item is dropped");
		assertEquals(1, generated.view().size(), "a dropped roll must not add a new entry");
		assertFalse(generated.filterAndTrack(new ItemStack(Items.WHEAT)), "suppression persists across rolls");
	}

	@Test
	void activeKnownItemIsKept() {
		final GeneratedItems generated = new GeneratedItems();
		generated.filterAndTrack(new ItemStack(Items.WHEAT));
		assertTrue(generated.filterAndTrack(new ItemStack(Items.WHEAT)));
		assertEquals(1, generated.view().size());
	}

	@Test
	void suppressionIsPerEntry() {
		final GeneratedItems generated = new GeneratedItems();
		generated.filterAndTrack(new ItemStack(Items.WHEAT));
		generated.filterAndTrack(new ItemStack(Items.WHEAT_SEEDS));
		generated.setSuppressed(0, true);
		assertFalse(generated.filterAndTrack(new ItemStack(Items.WHEAT)));
		assertTrue(generated.filterAndTrack(new ItemStack(Items.WHEAT_SEEDS)), "suppressing one entry leaves others active");
	}

	@Test
	void toggleFlipsAndReturnsNewState() {
		final GeneratedItems generated = new GeneratedItems();
		generated.filterAndTrack(new ItemStack(Items.WHEAT));
		assertTrue(generated.toggle(0));
		assertTrue(generated.view().getFirst().suppressed());
		assertFalse(generated.toggle(0));
		assertFalse(generated.view().getFirst().suppressed());
	}

	@Test
	void invalidIndexThrows() {
		final GeneratedItems generated = new GeneratedItems();
		assertThrows(IndexOutOfBoundsException.class, () -> generated.toggle(0));
		assertThrows(IndexOutOfBoundsException.class, () -> generated.setSuppressed(0, true));
	}

	@Test
	void emptyStackIsSkipped() {
		final GeneratedItems generated = new GeneratedItems();
		assertFalse(generated.filterAndTrack(ItemStack.EMPTY));
		assertTrue(generated.isEmpty());
	}

	@Test
	void viewIsUnmodifiable() {
		final GeneratedItems generated = new GeneratedItems();
		generated.filterAndTrack(new ItemStack(Items.WHEAT));
		assertThrows(UnsupportedOperationException.class, () -> generated.view().clear());
	}

	// ---- clear / persistence ----

	@Test
	void clearEmptiesTheList() {
		final GeneratedItems generated = new GeneratedItems();
		generated.filterAndTrack(new ItemStack(Items.WHEAT));
		generated.filterAndTrack(new ItemStack(Items.APPLE));
		generated.clear();
		assertTrue(generated.isEmpty());
		assertTrue(generated.view().isEmpty());
	}

	@Test
	void saveLoadRoundTripsOrderFlagsAndStacks() {
		final GeneratedItems original = new GeneratedItems();
		original.filterAndTrack(enchantedSword(Enchantments.SHARPNESS, 3));
		original.filterAndTrack(new ItemStack(Items.WHEAT));
		original.filterAndTrack(new ItemStack(Items.APPLE));
		original.setSuppressed(1, true);

		final TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, registries);
		original.save(output);

		final GeneratedItems restored = new GeneratedItems();
		restored.load(TagValueInput.create(ProblemReporter.DISCARDING, registries, output.buildResult()));

		assertEquals(3, restored.view().size(), "all entries round-trip");
		assertEquals(Items.DIAMOND_SWORD, restored.view().get(0).representative().getItem(), "order is preserved");
		assertEquals(Items.WHEAT, restored.view().get(1).representative().getItem());
		assertEquals(Items.APPLE, restored.view().get(2).representative().getItem());
		assertFalse(restored.view().get(0).suppressed());
		assertTrue(restored.view().get(1).suppressed(), "the suppressed flag round-trips");
		assertFalse(restored.view().get(2).suppressed());
		assertTrue(
			ItemStack.isSameItemSameComponents(original.view().getFirst().representative(), restored.view().getFirst().representative()),
			"the representative stack round-trips with its components"
		);
		// The restored tracker must behave identically: suppressed wheat is dropped, the rest kept.
		assertTrue(restored.filterAndTrack(enchantedSword(Enchantments.SHARPNESS, 3)));
		assertFalse(restored.filterAndTrack(new ItemStack(Items.WHEAT)));
		assertEquals(3, restored.view().size());
	}

	@Test
	void loadWithNoSavedDataClearsToEmpty() {
		final GeneratedItems generated = new GeneratedItems();
		generated.filterAndTrack(new ItemStack(Items.WHEAT));
		generated.load(TagValueInput.create(ProblemReporter.DISCARDING, registries, new CompoundTag()));
		assertTrue(generated.isEmpty(), "old saves without the key load as an empty tracker");
	}
}
