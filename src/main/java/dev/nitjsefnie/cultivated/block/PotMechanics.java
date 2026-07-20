package dev.nitjsefnie.cultivated.block;

import net.minecraft.core.Direction;

/**
 * Phase B — the pure, world-independent pot arithmetic and slot rules, factored out of
 * {@link BotanyPotBlockEntity} so they can be unit-tested without a game runtime. Covers the
 * 15-slot layout (§B.3), the comparator scaling math (§B.5), the fertilizer growth clamp (§A.5),
 * and the automation-face rules (§B.3).
 */
public final class PotMechanics {
	/** Soil input slot. */
	public static final int SOIL = 0;
	/** Seed input slot. */
	public static final int SEED = 1;
	/** Harvest-tool slot. */
	public static final int TOOL = 2;
	/** First storage/output slot. */
	public static final int FIRST_STORAGE = 3;
	/** Last storage/output slot (inclusive). */
	public static final int LAST_STORAGE = 14;
	/** Number of storage/output slots. */
	public static final int STORAGE_COUNT = 12;
	/** Total container size. */
	public static final int SIZE = 15;

	/** Analog comparator output for a fully mature pot. */
	public static final int MATURE_SIGNAL = 15;

	private static final int[] NO_SLOTS = new int[0];
	private static final int[] STORAGE_SLOTS = buildStorageSlots();

	private PotMechanics() {
	}

	private static int[] buildStorageSlots() {
		final int[] slots = new int[STORAGE_COUNT];
		for (int i = 0; i < STORAGE_COUNT; i++) {
			slots[i] = FIRST_STORAGE + i;
		}
		return slots;
	}

	/** True if {@code slot} is one of the 12 storage/output slots (3..14). */
	public static boolean isStorageSlot(final int slot) {
		return slot >= FIRST_STORAGE && slot <= LAST_STORAGE;
	}

	/** True if {@code slot} is an input slot (soil/seed/tool, i.e. {@code <= TOOL}). */
	public static boolean isInputSlot(final int slot) {
		return slot >= 0 && slot <= TOOL;
	}

    /**
     * Comparator output while a crop is still growing: {@code ceil(14 * growthTime / required)},
     * clamped to {@code [0, 14]} (15 is reserved for a mature pot). Returns 0 for a non-positive
     * requirement.
     */
	public static int comparatorWhileGrowing(final float growthTime, final int required) {
		if (required <= 0 || growthTime <= 0.0f) {
			return 0;
		}
		final int value = (int)Math.ceil(14.0 * growthTime / required);
		return Math.max(0, Math.min(14, value));
	}

	/**
	 * Apply a fertilizer's growth (§A.5), clamped so the resulting growth can never exceed
	 * {@code required - 20}. Returns the new {@code growth_time}; a no-op (returns {@code current}
	 * unchanged) when the crop needs {@code <= 20} ticks total or is already within 20 of done.
	 */
	public static float clampFertilizedGrowth(final float current, final int addedTicks, final int required) {
		final float cap = required - 20.0f;
		if (cap <= 0.0f || current >= cap) {
			return current;
		}
		return Math.min(current + addedTicks, cap);
	}

	/** True if the fertilizer clamp would actually advance growth (i.e. it is not a no-op). */
	public static boolean canFertilize(final float current, final int required) {
		final float cap = required - 20.0f;
		return cap > 0.0f && current < cap;
	}

	/**
	 * The slots an automation (hopper/pipe) may reach through {@code face}. Only hopper pots expose
	 * anything, and only the storage slots through the DOWN face (§B.3); every other case is empty.
	 */
	public static int[] automationSlotsForFace(final boolean hopper, final Direction face) {
		return hopper && face == Direction.DOWN ? STORAGE_SLOTS.clone() : NO_SLOTS;
	}

	/** Automation extraction is allowed only from a hopper pot's storage slots via the DOWN face. */
	public static boolean canAutomationTake(final boolean hopper, final int slot, final Direction face) {
		return hopper && face == Direction.DOWN && isStorageSlot(slot);
	}

	/** Automation may never insert into a pot through any face (§B.3). */
	public static boolean canAutomationPlace() {
		return false;
	}
}
