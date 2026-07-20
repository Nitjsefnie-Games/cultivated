package dev.nitjsefnie.cultivated.command;

import java.util.List;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Phase F.2 — the pure, world-independent decisions behind {@code /cultivated debug check_crops}'s
 * live per-tool harvest audit. The command builds the actual (enchanted) tool stacks and rolls each
 * crop's drops against a live block entity; this class only holds the tool-variety composition and
 * the "produced nothing with any tool" verdict, so both are unit-testable without a game runtime.
 */
public final class HarvestAudit {
	/**
	 * The non-empty-hand tool items tried against every crop, in order: silk-touchable shears and
	 * pickaxe (the command applies silk touch to these two), then axe, sword, shovel and hoe. The
	 * empty hand is the seventh variety and is represented by {@link ItemStack#EMPTY} at the call
	 * site, not by an item here.
	 */
	public static final List<Item> AUDIT_TOOL_ITEMS = List.of(
		Items.SHEARS,
		Items.DIAMOND_PICKAXE,
		Items.DIAMOND_AXE,
		Items.DIAMOND_SWORD,
		Items.DIAMOND_SHOVEL,
		Items.DIAMOND_HOE);

	private HarvestAudit() {
	}

	/**
	 * True if a crop produced no drops with ANY tried tool — i.e. every per-tool roll came back with
	 * an empty list. A single tool that yielded at least one drop makes this false. The lists are
	 * expected to contain only real drops (the harvest collector never records empty stacks), so the
	 * verdict is a pure per-list emptiness check.
	 */
	public static boolean producedNothing(final List<List<ItemStack>> perToolDrops) {
		for (final List<ItemStack> drops : perToolDrops) {
			if (!drops.isEmpty()) {
				return false;
			}
		}
		return true;
	}
}
