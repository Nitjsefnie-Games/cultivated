package dev.nitjsefnie.cultivated.menu;

import dev.nitjsefnie.cultivated.block.PotMechanics;
import dev.nitjsefnie.cultivated.cache.PotRecipeCaches;
import dev.nitjsefnie.cultivated.cache.RecipeLookupCache;
import dev.nitjsefnie.cultivated.recipe.CropRecipe;
import dev.nitjsefnie.cultivated.recipe.SimplePotContext;
import dev.nitjsefnie.cultivated.recipe.SpawnEggCropRecipe;
import dev.nitjsefnie.cultivated.registry.ModComponents;
import dev.nitjsefnie.cultivated.registry.ModTags;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * Phase B §B.7 — shared base for the botany pot menus. Wraps the pot's 15-slot {@link Container}
 * (the live {@link dev.nitjsefnie.cultivated.block.BotanyPotBlockEntity} on the server, a throwaway
 * {@link net.minecraft.world.SimpleContainer} on the client), carries the pot's {@link BlockPos} so
 * client-side tooltip code (Task B5) can reach the live block entity, and implements the bespoke
 * shift-click routing (§B.7). Subclasses add their own pot slots (soil/seed[/tool/outputs]) in
 * container-slot order — so a pot slot's menu index equals its container index — then call
 * {@link #addPlayerInventory(Inventory)}.
 */
public abstract class AbstractPotMenu extends AbstractContainerMenu {
	/** The player inventory's top edge inside the 176×166 container background (§B.8). */
	protected static final int INVENTORY_LEFT = 8;
	protected static final int INVENTORY_TOP = 84;
	/** Number of player-inventory + hotbar slots added by {@link #addStandardInventorySlots}. */
	private static final int PLAYER_SLOT_COUNT = 36;
	/** The first 27 player slots are the main inventory; the last 9 are the hotbar. */
	private static final int MAIN_INVENTORY_COUNT = 27;

	protected final Container container;
	private final BlockPos pos;
	private final boolean clientSide;

	protected AbstractPotMenu(
		final MenuType<?> type, final int containerId, final Inventory inventory, final Container container, final BlockPos pos
	) {
		super(type, containerId);
		checkContainerSize(container, PotMechanics.SIZE);
		this.container = container;
		this.pos = pos;
		this.clientSide = inventory.player.level().isClientSide();
	}

	/** Number of pot slots this menu exposes at the front of the slot list (equal to their container indices). */
	protected abstract int potSlotCount();

	/** Whether this menu exposes a harvest-tool slot (basic pots do not). */
	protected abstract boolean hasToolSlot();

	/** The pot's world position — needed client-side (Task B5) to reach the live block entity. */
	public BlockPos getPos() {
		return this.pos;
	}

	/** Add the standard 3×9 inventory grid + hotbar at the shared (§B.8) offset. */
	protected final void addPlayerInventory(final Inventory inventory) {
		this.addStandardInventorySlots(inventory, INVENTORY_LEFT, INVENTORY_TOP);
	}

	@Override
	public boolean stillValid(final Player player) {
		return this.container.stillValid(player);
	}

	@Override
	public ItemStack quickMoveStack(final Player player, final int slotIndex) {
		final var slot = this.slots.get(slotIndex);
		if (slot == null || !slot.hasItem()) {
			return ItemStack.EMPTY;
		}
		final ItemStack stack = slot.getItem();
		final ItemStack original = stack.copy();

		final int potSlots = this.potSlotCount();
		final int invStart = potSlots;
		final int invEnd = potSlots + PLAYER_SLOT_COUNT;
		final int hotbarStart = invStart + MAIN_INVENTORY_COUNT;

		if (slotIndex < potSlots) {
			// From a pot slot (soil/seed/tool/output) back into the player inventory.
			if (!this.moveItemStackTo(stack, invStart, invEnd, true)) {
				return ItemStack.EMPTY;
			}
		} else {
			// From the player inventory — route to a pot input slot, else move within inv/hotbar.
			final int target = PotMenuRouting.routeFromInventory(
				this.hasToolSlot(),
				stack.is(ModTags.HARVEST_ITEMS),
				this.container.getItem(PotMechanics.TOOL).isEmpty(),
				this.resolvesSoil(stack),
				this.resolvesCrop(stack)
			);
			boolean moved = false;
			if (target != PotMenuRouting.ROUTE_INVENTORY) {
				// A pot input slot's menu index equals its container index (subclasses add in order).
				moved = this.moveItemStackTo(stack, target, target + 1, false);
			}
			if (!moved) {
				if (slotIndex < hotbarStart) {
					// From main inventory → hotbar.
					if (!this.moveItemStackTo(stack, hotbarStart, invEnd, false)) {
						return ItemStack.EMPTY;
					}
				} else if (!this.moveItemStackTo(stack, invStart, hotbarStart, false)) {
					// From hotbar → main inventory.
					return ItemStack.EMPTY;
				}
			}
		}

		if (stack.isEmpty()) {
			slot.setByPlayer(ItemStack.EMPTY);
		} else {
			slot.setChanged();
		}
		if (stack.getCount() == original.getCount()) {
			return ItemStack.EMPTY;
		}
		slot.onTake(player, stack);
		return original;
	}

	/** True if {@code stack} resolves to a soil (item override component first, then the sided cache). */
	protected boolean resolvesSoil(final ItemStack stack) {
		if (stack.isEmpty()) {
			return false;
		}
		if (stack.get(ModComponents.SOIL_OVERRIDE) != null) {
			return true;
		}
		return PotRecipeCaches.soils(this.clientSide).lookup(stack, SimplePotContext.ofSoil(stack)) != null;
	}

	/**
	 * True if {@code stack} resolves to a crop (item override component first, then the sided caches).
	 * Mirrors the block entity's {@code computeCrop} fallback so the seed slot accepts — and shift-clicks
	 * route to it — exactly what the pot can grow: a normal crop, OR (the generic growable-mob mechanism)
	 * any spawn egg that derives a synthetic crop from its own entity type.
	 */
	protected boolean resolvesCrop(final ItemStack stack) {
		if (stack.isEmpty()) {
			return false;
		}
		if (stack.get(ModComponents.CROP_OVERRIDE) != null) {
			return true;
		}
		return cropResolves(
			PotRecipeCaches.crops(this.clientSide), PotRecipeCaches.spawnEggCrops(this.clientSide), stack
		);
	}

	/**
	 * Pure crop resolution shared with the seed-slot acceptance test and matching {@code
	 * BotanyPotBlockEntity#computeCrop}: a stack resolves to a crop when the normal crop cache indexes it,
	 * OR when the generic spawn-egg cache indexes it AND that recipe can {@linkplain
	 * SpawnEggCropRecipe#resolveFor derive} a concrete crop from the stack (i.e. it is a spawn egg with a
	 * resolvable entity type). Not one mob is special-cased — any spawn egg is placeable.
	 */
	static boolean cropResolves(
		final RecipeLookupCache<CropRecipe> crops, final RecipeLookupCache<SpawnEggCropRecipe> spawnEggCrops,
		final ItemStack stack
	) {
		return resolveCrop(crops, spawnEggCrops, stack) != null;
	}

	/**
	 * The concrete {@link CropRecipe} a seed stack resolves to (or {@code null}), mirroring {@code
	 * BotanyPotBlockEntity#computeCrop}: the normal crop cache first, else — the generic growable-mob
	 * mechanism — the spawn-egg cache {@linkplain SpawnEggCropRecipe#resolveFor derived} into a per-egg
	 * crop from the stack's own entity type. Shared with the client tooltip (Task B5) so a hovered spawn
	 * egg surfaces the same grow-time/yield lines as any crop. Callers pass the {@code CROP_OVERRIDE}
	 * component first; this covers only the cache paths.
	 */
	public static @Nullable CropRecipe resolveCrop(
		final RecipeLookupCache<CropRecipe> crops, final RecipeLookupCache<SpawnEggCropRecipe> spawnEggCrops,
		final ItemStack stack
	) {
		if (stack.isEmpty()) {
			return null;
		}
		final SimplePotContext context = SimplePotContext.ofSeed(stack);
		final CropRecipe crop = crops.lookup(stack, context);
		if (crop != null) {
			return crop;
		}
		final SpawnEggCropRecipe eggCrop = spawnEggCrops.lookup(stack, context);
		return eggCrop != null ? eggCrop.resolveFor(stack) : null;
	}
}
