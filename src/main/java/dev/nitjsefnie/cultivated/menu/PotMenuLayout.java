package dev.nitjsefnie.cultivated.menu;

import dev.nitjsefnie.cultivated.block.PotMechanics;

/**
 * Phase B §B.8 — the pure slot-coordinate and output-grid rules for the pot menus, factored out so
 * they can be unit-tested without a game runtime. All coordinates are relative to the top-left of the
 * container background (176×166 for the basic pot; the hopper background grew to 176×215 to make room
 * for the fertilizer input grid above the player inventory).
 */
public final class PotMenuLayout {
	// Basic pot input slots (§B.8).
	public static final int BASIC_SOIL_X = 80;
	public static final int BASIC_SOIL_Y = 48;
	public static final int BASIC_SEED_X = 80;
	public static final int BASIC_SEED_Y = 22;

	// Hopper pot input slots (§B.8).
	public static final int HOPPER_SOIL_X = 44;
	public static final int HOPPER_SOIL_Y = 48;
	public static final int HOPPER_SEED_X = 44;
	public static final int HOPPER_SEED_Y = 22;
	public static final int HOPPER_TOOL_X = 18;
	public static final int HOPPER_TOOL_Y = 35;

	// Hopper pot output grid (§B.8): 4 columns × 3 rows from (80,17) stepping 18px, container slots 3..14.
	public static final int OUTPUT_ORIGIN_X = 80;
	public static final int OUTPUT_ORIGIN_Y = 17;
	public static final int OUTPUT_STEP = 18;
	public static final int OUTPUT_COLUMNS = 4;
	public static final int OUTPUT_ROWS = 3;

	// Hopper pot fertilizer input grid: 4 columns × 3 rows from (80,74) stepping 18px, container slots 15..26.
	public static final int FERTILIZER_ORIGIN_X = 80;
	public static final int FERTILIZER_ORIGIN_Y = 74;
	public static final int FERTILIZER_STEP = 18;
	public static final int FERTILIZER_COLUMNS = 4;
	public static final int FERTILIZER_ROWS = 3;

	private PotMenuLayout() {
	}

	/** The container slot index (3..14) for the output cell at {@code (row, column)}, filled row-major. */
	public static int outputContainerSlot(final int row, final int column) {
		return PotMechanics.FIRST_STORAGE + row * OUTPUT_COLUMNS + column;
	}

	/** The x coordinate of an output cell in {@code column}. */
	public static int outputX(final int column) {
		return OUTPUT_ORIGIN_X + column * OUTPUT_STEP;
	}

	/** The y coordinate of an output cell in {@code row}. */
	public static int outputY(final int row) {
		return OUTPUT_ORIGIN_Y + row * OUTPUT_STEP;
	}

	/** The container slot index (15..26) for the fertilizer cell at {@code (row, column)}, filled row-major. */
	public static int fertilizerContainerSlot(final int row, final int column) {
		return PotMechanics.FERTILIZER_INPUT_FIRST + row * FERTILIZER_COLUMNS + column;
	}

	/** The x coordinate of a fertilizer cell in {@code column}. */
	public static int fertilizerX(final int column) {
		return FERTILIZER_ORIGIN_X + column * FERTILIZER_STEP;
	}

	/** The y coordinate of a fertilizer cell in {@code row}. */
	public static int fertilizerY(final int row) {
		return FERTILIZER_ORIGIN_Y + row * FERTILIZER_STEP;
	}
}
