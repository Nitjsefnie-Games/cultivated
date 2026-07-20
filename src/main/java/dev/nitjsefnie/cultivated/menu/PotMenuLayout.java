package dev.nitjsefnie.cultivated.menu;

import dev.nitjsefnie.cultivated.block.PotMechanics;

/**
 * Phase B §B.8 — the pure slot-coordinate and output-grid rules for the pot menus, factored out so
 * they can be unit-tested without a game runtime. All coordinates are relative to the top-left of the
 * 176×166 container background.
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
}
