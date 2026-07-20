package dev.nitjsefnie.cultivated.compat.rei;

import dev.nitjsefnie.cultivated.compat.CropView;
import java.util.List;
import me.shedaniel.rei.api.common.category.CategoryIdentifier;
import me.shedaniel.rei.api.common.display.Display;
import me.shedaniel.rei.api.common.display.DisplaySerializer;
import me.shedaniel.rei.api.common.display.basic.BasicDisplay;
import me.shedaniel.rei.api.common.entry.EntryIngredient;
import me.shedaniel.rei.api.common.util.EntryIngredients;

/**
 * REI display wrapping one {@link CropView}. Seed + soil are exposed as REI inputs and the drops as
 * outputs (so REI's "show recipes / show uses" lookups work), while the concrete slot/label layout is
 * done by {@link CropReiCategory}. This is a client-only, in-memory display, so it has no serializer.
 */
public final class CropDisplay extends BasicDisplay {
	private final CropView view;

	public CropDisplay(final CropView view) {
		super(inputs(view), outputs(view));
		this.view = view;
	}

	private static List<EntryIngredient> inputs(final CropView view) {
		return List.of(
			EntryIngredients.ofItemStacks(view.seeds()),
			EntryIngredients.ofItemStacks(view.soils())
		);
	}

	private static List<EntryIngredient> outputs(final CropView view) {
		return List.of(EntryIngredients.ofItemStacks(view.drops()));
	}

	public CropView view() {
		return this.view;
	}

	@Override
	public CategoryIdentifier<?> getCategoryIdentifier() {
		return CropReiCategory.ID;
	}

	@Override
	public DisplaySerializer<? extends Display> getSerializer() {
		return null;
	}
}
