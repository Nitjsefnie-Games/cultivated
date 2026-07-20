package dev.nitjsefnie.cultivated.menu;

import dev.nitjsefnie.cultivated.block.PotMechanics;
import dev.nitjsefnie.cultivated.registry.ModMenus;
import dev.nitjsefnie.cultivated.registry.ModTags;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;

/**
 * Phase B §B.7/§B.8 — the {@code cultivated:hopper_pot_menu}: soil (44,48), seed (44,22) and a
 * harvest-tool slot (18,35, accepting only tag {@code cultivated:harvest_items}) on the left, plus a
 * 4-wide × 3-tall grid of 12 extract-only output slots starting at (80,17) and stepping 18px, over
 * the player inventory.
 */
public class HopperPotMenu extends AbstractPotMenu {
	/** All 15 container slots are exposed by the hopper menu (soil, seed, tool, 12 outputs). */
	private static final int POT_SLOTS = PotMechanics.SIZE;

	/** Client-side factory (via {@link ModMenus#HOPPER_POT}): no live container, only the synced pos. */
	public HopperPotMenu(final int containerId, final Inventory inventory, final BlockPos pos) {
		this(containerId, inventory, new SimpleContainer(PotMechanics.SIZE), pos);
	}

	/** Server-side factory: backed by the live pot block entity {@code container}. */
	public HopperPotMenu(final int containerId, final Inventory inventory, final Container container, final BlockPos pos) {
		super(ModMenus.HOPPER_POT, containerId, inventory, container, pos);
		// Added in container-slot order so a pot slot's menu index equals its container index.
		this.addSlot(PotSlot.input(container, PotMechanics.SOIL,
			PotMenuLayout.HOPPER_SOIL_X, PotMenuLayout.HOPPER_SOIL_Y, stack -> true, PotMenuTextures.SOIL_SLOT));
		this.addSlot(PotSlot.input(container, PotMechanics.SEED,
			PotMenuLayout.HOPPER_SEED_X, PotMenuLayout.HOPPER_SEED_Y, stack -> true, PotMenuTextures.SEED_SLOT));
		this.addSlot(PotSlot.input(container, PotMechanics.TOOL,
			PotMenuLayout.HOPPER_TOOL_X, PotMenuLayout.HOPPER_TOOL_Y, stack -> stack.is(ModTags.HARVEST_ITEMS), PotMenuTextures.HOE_SLOT));
		for (int row = 0; row < PotMenuLayout.OUTPUT_ROWS; row++) {
			for (int column = 0; column < PotMenuLayout.OUTPUT_COLUMNS; column++) {
				this.addSlot(PotSlot.output(container, PotMenuLayout.outputContainerSlot(row, column),
					PotMenuLayout.outputX(column), PotMenuLayout.outputY(row)));
			}
		}
		this.addPlayerInventory(inventory);
	}

	@Override
	protected int potSlotCount() {
		return POT_SLOTS;
	}

	@Override
	protected boolean hasToolSlot() {
		return true;
	}
}
