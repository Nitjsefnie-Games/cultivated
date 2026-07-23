package dev.nitjsefnie.cultivated.menu;

import dev.nitjsefnie.cultivated.block.PotMechanics;
import dev.nitjsefnie.cultivated.registry.ModMenus;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;

/**
 * Phase B §B.7 — the {@code cultivated:basic_pot_menu}: a soil input slot (80,48) and a seed input
 * slot (80,22) over the player inventory. Basic pots pop their harvest to the world (Task B4), so the
 * menu exposes no output or tool slots — only the two input slots.
 */
public class BasicPotMenu extends AbstractPotMenu {
	private static final int POT_SLOTS = 2;

	/** Client-side factory (via {@link ModMenus#BASIC_POT}): no live container, only the synced pos. */
	public BasicPotMenu(final int containerId, final Inventory inventory, final BlockPos pos) {
		this(containerId, inventory, new SimpleContainer(PotMechanics.SIZE), pos);
	}

	/** Server-side factory: backed by the live pot block entity {@code container}. */
	public BasicPotMenu(final int containerId, final Inventory inventory, final Container container, final BlockPos pos) {
		super(ModMenus.BASIC_POT, containerId, inventory, container, pos);
		// Added in container-slot order so a pot slot's menu index equals its container index.
		this.addSlot(PotSlot.input(container, PotMechanics.SOIL,
			PotMenuLayout.BASIC_SOIL_X, PotMenuLayout.BASIC_SOIL_Y, this::resolvesSoil, PotMenuTextures.SOIL_SLOT));
		this.addSlot(PotSlot.input(container, PotMechanics.SEED,
			PotMenuLayout.BASIC_SEED_X, PotMenuLayout.BASIC_SEED_Y, this::resolvesCrop, PotMenuTextures.SEED_SLOT));
		this.addPlayerInventory(inventory);
	}

	@Override
	protected int potSlotCount() {
		return POT_SLOTS;
	}

	@Override
	protected boolean hasToolSlot() {
		return false;
	}

	@Override
	protected int inventoryTop() {
		return INVENTORY_TOP;
	}
}
