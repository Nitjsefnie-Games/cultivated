package dev.nitjsefnie.cultivated.menu;

import dev.nitjsefnie.cultivated.block.PotMechanics;

/**
 * Phase B §B.7 — the pure {@code quickMoveStack} routing decision for a botany pot menu, factored out
 * of the (world-coupled) menu classes so it can be unit-tested without a game runtime.
 *
 * <p>When a player shift-clicks a stack in their inventory, this decides which pot input slot (if any)
 * the stack should be routed to. The caller supplies the already-resolved facts (is the stack a
 * harvest tool? does it resolve to a soil / crop via the recipe cache? is the tool slot empty?), and
 * this returns the target container slot index, or {@link #ROUTE_INVENTORY} to fall back to the normal
 * inventory↔hotbar movement.
 */
public final class PotMenuRouting {
	/** Sentinel: the stack does not belong in any pot input slot; do the normal inventory move. */
	public static final int ROUTE_INVENTORY = -1;

	private PotMenuRouting() {
	}

	/**
	 * Decide where a shift-clicked inventory stack should go (§B.7):
	 * <ol>
	 *   <li>the {@link PotMechanics#TOOL} slot, if the menu has one, the stack is a harvest tool and
	 *       the tool slot is empty;</li>
	 *   <li>else the {@link PotMechanics#SOIL} slot, if the stack resolves to a soil;</li>
	 *   <li>else the {@link PotMechanics#SEED} slot, if the stack resolves to a crop;</li>
	 *   <li>else {@link #ROUTE_INVENTORY}.</li>
	 * </ol>
	 *
	 * @param hasToolSlot    whether this menu exposes a tool slot (basic pots do not)
	 * @param isHarvestTool  whether the stack is tagged {@code cultivated:harvest_items}
	 * @param toolSlotEmpty  whether the tool slot is currently empty
	 * @param resolvesSoil   whether the stack resolves to a soil (override component or soil cache)
	 * @param resolvesCrop   whether the stack resolves to a crop (override component or crop cache)
	 * @return the target container slot index, or {@link #ROUTE_INVENTORY}
	 */
	public static int routeFromInventory(
		final boolean hasToolSlot,
		final boolean isHarvestTool,
		final boolean toolSlotEmpty,
		final boolean resolvesSoil,
		final boolean resolvesCrop
	) {
		if (hasToolSlot && isHarvestTool && toolSlotEmpty) {
			return PotMechanics.TOOL;
		}
		if (resolvesSoil) {
			return PotMechanics.SOIL;
		}
		if (resolvesCrop) {
			return PotMechanics.SEED;
		}
		return ROUTE_INVENTORY;
	}
}
