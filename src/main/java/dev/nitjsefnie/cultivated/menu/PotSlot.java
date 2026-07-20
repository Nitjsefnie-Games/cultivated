package dev.nitjsefnie.cultivated.menu;

import java.util.function.Predicate;
import net.minecraft.resources.Identifier;
import net.minecraft.world.Container;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * Phase B §B.7 — the two bespoke pot menu slot kinds:
 * <ul>
 *   <li>an <b>input</b> slot, which only accepts stacks matching a predicate and shows a placeholder
 *       empty-slot icon (soil / seed / hoe);</li>
 *   <li>an <b>output</b> slot, which is extract-only (no manual insertion).</li>
 * </ul>
 */
public final class PotSlot {
	private PotSlot() {
	}

	/** An input slot that only accepts {@code predicate}-matching stacks and draws {@code icon} when empty. */
	public static Slot input(
		final Container container, final int slot, final int x, final int y,
		final Predicate<ItemStack> predicate, final @Nullable Identifier icon
	) {
		return new Slot(container, slot, x, y) {
			@Override
			public boolean mayPlace(final ItemStack stack) {
				return predicate.test(stack);
			}

			@Override
			public @Nullable Identifier getNoItemIcon() {
				return icon;
			}
		};
	}

	/** An extract-only output slot: contents can be taken, but nothing can be placed into it. */
	public static Slot output(final Container container, final int slot, final int x, final int y) {
		return new Slot(container, slot, x, y) {
			@Override
			public boolean mayPlace(final ItemStack stack) {
				return false;
			}
		};
	}
}
