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
	 * Per-slot maximum stack size (§B.3): the SOIL and SEED slots hold at most one item so a single
	 * pot only ever grows one crop at a time (a full stack of seeds would otherwise sit inertly); every
	 * other slot (tool, storage) uses {@code defaultMax}. Pure so it can gate both the menu slot and the
	 * block-entity container clamp identically.
	 */
	public static int maxStackSizeForSlot(final int slot, final int defaultMax) {
		return slot == SOIL || slot == SEED ? Math.min(1, defaultMax) : defaultMax;
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

	/**
	 * True if the storage buffer (the 12 output slots, 3..14) can accept at least one more item —
	 * i.e. at least one slot is empty or holds a non-full stack (R2d). {@code freeCapacity[i]} is the
	 * number of additional items storage slot {@code FIRST_STORAGE + i} could hold: its stack space
	 * for a non-full stack, or a positive value for an empty slot, and {@code 0} for a full stack.
	 *
	 * <p>A HOPPER pot pauses its growth cycle while this returns {@code false}, so a full pot holds at
	 * mature instead of auto-harvesting into a buffer with nowhere to put the drops — no item loss, no
	 * infinite re-harvest — and resumes the moment space frees up.
	 */
	public static boolean storageBufferHasRoom(final int[] freeCapacity) {
		for (final int free : freeCapacity) {
			if (free > 0) {
				return true;
			}
		}
		return false;
	}

	// ---- right-click interaction order (§B.2) — pure decision logic ----

	/** The empty-hand ({@code useWithoutItem}) branch resolved for a right-click (§B.2). */
	public enum EmptyHandBranch {
		/** Basic pot with a mature crop: run the manual harvest (§B.2 step 1). */
		HARVEST,
		/** Basic/Hopper pot: open the container GUI (§B.2 step 4). */
		OPEN_MENU,
		/** Waxed (decorative) pot: ignore the interaction entirely. */
		IGNORE
	}

	/** The held-item ({@code useItemOn}) branch resolved for a right-click (§B.2 steps 2–3). */
	public enum HeldItemBranch {
		/** Held item matches a fertilizer recipe (§B.2 step 2) — attempted first. */
		FERTILIZE,
		/** Held item matches a pot-interaction recipe (§B.2 step 3). */
		INTERACT,
		/** No held-item recipe matched — defer to the empty-hand path (harvest / open menu). */
		DEFER,
		/** Waxed (decorative) pot: ignore the interaction entirely. */
		IGNORE
	}

	/** How a matched pot-interaction consumes the held item (§B.6): damage wins over consume. */
	public enum HeldConsumption {
		/** Damage the held item by 1 ({@code damage_held}, default). */
		DAMAGE,
		/** Consume 1 held item ({@code consume_held}, only honoured when not damaging). */
		CONSUME,
		/** Leave the held item untouched. */
		NONE
	}

	/**
	 * Resolve the empty-hand right-click branch (§B.2): a waxed pot ignores all interaction; a
	 * Basic pot with a mature crop harvests (step 1); every other Basic/Hopper pot opens the menu
	 * (step 4).
	 */
	public static EmptyHandBranch emptyHandBranch(final boolean waxed, final boolean basic, final boolean harvestable) {
		if (waxed) {
			return EmptyHandBranch.IGNORE;
		}
		if (basic && harvestable) {
			return EmptyHandBranch.HARVEST;
		}
		return EmptyHandBranch.OPEN_MENU;
	}

	/**
	 * Whether a held-item right-click must be preempted by the empty-hand harvest (§B.2 strict
	 * stop-at-first order: HARVEST is step 1). True only for a non-waxed Basic pot with a mature
	 * crop, so a held fertilizer / pot-interaction never runs ahead of the harvest; the caller then
	 * defers ({@code TRY_WITH_EMPTY_HAND}) to the empty-hand harvest path.
	 */
	public static boolean harvestPreemptsHeldItem(final boolean waxed, final boolean basic, final boolean harvestable) {
		return !waxed && basic && harvestable;
	}

	/**
	 * Resolve the held-item right-click branch (§B.2): a waxed pot ignores all interaction;
	 * otherwise fertilizer (step 2) is tried before pot-interaction (step 3); if neither the held
	 * item matched a fertilizer nor a pot-interaction recipe, defer to the empty-hand path.
	 */
	public static HeldItemBranch heldItemBranch(final boolean waxed, final boolean fertilizerMatches, final boolean interactionMatches) {
		if (waxed) {
			return HeldItemBranch.IGNORE;
		}
		if (fertilizerMatches) {
			return HeldItemBranch.FERTILIZE;
		}
		if (interactionMatches) {
			return HeldItemBranch.INTERACT;
		}
		return HeldItemBranch.DEFER;
	}

	/**
	 * How a matched pot-interaction should consume the held item (§B.6): {@code damage_held} takes
	 * priority, then {@code consume_held} (honoured only when not damaging), else nothing.
	 */
	public static HeldConsumption heldConsumption(final boolean damageHeld, final boolean consumeHeld) {
		if (damageHeld) {
			return HeldConsumption.DAMAGE;
		}
		if (consumeHeld) {
			return HeldConsumption.CONSUME;
		}
		return HeldConsumption.NONE;
	}
}
