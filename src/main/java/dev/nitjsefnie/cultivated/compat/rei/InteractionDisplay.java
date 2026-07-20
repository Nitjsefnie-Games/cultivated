package dev.nitjsefnie.cultivated.compat.rei;

import dev.nitjsefnie.cultivated.compat.InteractionView;
import java.util.ArrayList;
import java.util.List;
import me.shedaniel.rei.api.common.category.CategoryIdentifier;
import me.shedaniel.rei.api.common.display.Display;
import me.shedaniel.rei.api.common.display.DisplaySerializer;
import me.shedaniel.rei.api.common.display.basic.BasicDisplay;
import me.shedaniel.rei.api.common.entry.EntryIngredient;
import me.shedaniel.rei.api.common.util.EntryIngredients;
import net.minecraft.world.item.ItemStack;

/**
 * REI display wrapping one {@link InteractionView}. The held item plus any required soil/seed are REI
 * inputs and the resulting soil/seed are outputs; {@link InteractionReiCategory} lays out the slots.
 * Client-only in-memory display, so no serializer.
 */
public final class InteractionDisplay extends BasicDisplay {
	private final InteractionView view;

	public InteractionDisplay(final InteractionView view) {
		super(inputs(view), outputs(view));
		this.view = view;
	}

	private static List<EntryIngredient> inputs(final InteractionView view) {
		final List<EntryIngredient> in = new ArrayList<>();
		in.add(EntryIngredients.ofItemStacks(view.held()));
		addIfPresent(in, view.requiredSoil());
		addIfPresent(in, view.requiredSeed());
		return in;
	}

	private static List<EntryIngredient> outputs(final InteractionView view) {
		final List<EntryIngredient> out = new ArrayList<>();
		addIfPresent(out, view.resultSoil());
		addIfPresent(out, view.resultSeed());
		return out;
	}

	private static void addIfPresent(final List<EntryIngredient> target, final List<ItemStack> stacks) {
		if (!stacks.isEmpty()) {
			target.add(EntryIngredients.ofItemStacks(stacks));
		}
	}

	public InteractionView view() {
		return this.view;
	}

	@Override
	public CategoryIdentifier<?> getCategoryIdentifier() {
		return InteractionReiCategory.ID;
	}

	@Override
	public DisplaySerializer<? extends Display> getSerializer() {
		return null;
	}
}
