package dev.nitjsefnie.cultivated.block;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * Tracks every distinct item a hopper pot has auto-harvested, in discovery order, with a per-entry
 * {@code suppressed} flag (server-side core; no UI/sync — those are later chunks). Identity is the
 * item type plus all data components EXCEPT durability ({@link DataComponents#DAMAGE}): a
 * full-durability and a half-durability roll of the same item+enchants are ONE entry, while
 * different enchants, a different custom name or a different item are distinct entries. An item is
 * always produced the first time it is seen; afterwards it is produced only while unsuppressed.
 */
public final class GeneratedItems {
	/** NBT key under which the ordered list is persisted inside the pot's block-entity data. */
	public static final String SAVE_KEY = "GeneratedItems";

	/** A tracked item: a normalized (count 1, durability-free) representative stack + its flag. */
	public static final class Entry {
		private final ItemStack representative;
		private boolean suppressed;

		private Entry(final ItemStack representative, final boolean suppressed) {
			this.representative = representative;
			this.suppressed = suppressed;
		}

		/** The normalized representative stack (count 1, no {@code damage} component). */
		public ItemStack representative() {
			return this.representative;
		}

		/** Whether this item is currently dropped (suppressed) instead of stored. */
		public boolean suppressed() {
			return this.suppressed;
		}
	}

	private static final Codec<Entry> ENTRY_CODEC = RecordCodecBuilder.create(instance -> instance.group(
		ItemStack.OPTIONAL_CODEC.fieldOf("Item").forGetter(Entry::representative),
		Codec.BOOL.optionalFieldOf("Suppressed", false).forGetter(Entry::suppressed)
	).apply(instance, Entry::new));

	private final List<Entry> entries = new ArrayList<>();

	/**
	 * The identity form of a rolled stack: count normalized to 1 and the durability
	 * ({@link DataComponents#DAMAGE}) component removed, so wear level never splits an entry.
	 */
	public static ItemStack normalize(final ItemStack stack) {
		final ItemStack normalized = stack.copyWithCount(1);
		normalized.remove(DataComponents.DAMAGE);
		return normalized;
	}

	/**
	 * Track one rolled stack and decide whether it should be kept (stored). Empty stacks are skipped
	 * ({@code false}). A never-before-seen identity is appended (unsuppressed) and kept; a known
	 * identity is kept iff its entry is not suppressed. The list mutates only on discovery.
	 */
	public boolean filterAndTrack(final ItemStack rolled) {
		if (rolled.isEmpty()) {
			return false;
		}
		final ItemStack identity = normalize(rolled);
		for (final Entry entry : this.entries) {
			if (ItemStack.isSameItemSameComponents(entry.representative, identity)) {
				return !entry.suppressed;
			}
		}
		this.entries.add(new Entry(identity, false));
		return true;
	}

	/** Flip the suppression of entry {@code index}; returns the new suppressed state. */
	public boolean toggle(final int index) {
		final Entry entry = this.entries.get(index);
		entry.suppressed = !entry.suppressed;
		return entry.suppressed;
	}

	/** Set the suppression of entry {@code index} explicitly. */
	public void setSuppressed(final int index, final boolean suppressed) {
		this.entries.get(index).suppressed = suppressed;
	}

	/** Forget everything (soil/seed change, pot reset). */
	public void clear() {
		this.entries.clear();
	}

	/** Unmodifiable view of the tracked entries in discovery order (for later UI/sync chunks). */
	public List<Entry> view() {
		return Collections.unmodifiableList(this.entries);
	}

	public boolean isEmpty() {
		return this.entries.isEmpty();
	}

	/** Persist the ordered list of {representative stack, suppressed} under {@link #SAVE_KEY}. */
	public void save(final ValueOutput output) {
		final ValueOutput.TypedOutputList<Entry> list = output.list(SAVE_KEY, ENTRY_CODEC);
		for (final Entry entry : this.entries) {
			list.add(entry);
		}
	}

	/** Restore the list from {@link #SAVE_KEY}, preserving order and flags; clears on absence. */
	public void load(final ValueInput input) {
		this.entries.clear();
		for (final Entry entry : input.listOrEmpty(SAVE_KEY, ENTRY_CODEC)) {
			if (!entry.representative.isEmpty()) {
				this.entries.add(entry);
			}
		}
	}
}
