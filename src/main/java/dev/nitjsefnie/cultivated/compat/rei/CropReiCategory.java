package dev.nitjsefnie.cultivated.compat.rei;

import dev.nitjsefnie.cultivated.Cultivated;
import dev.nitjsefnie.cultivated.compat.CultivatedViewerData;
import java.util.ArrayList;
import java.util.List;
import me.shedaniel.math.Point;
import me.shedaniel.math.Rectangle;
import me.shedaniel.rei.api.client.gui.Renderer;
import me.shedaniel.rei.api.client.gui.widgets.Widget;
import me.shedaniel.rei.api.client.gui.widgets.Widgets;
import me.shedaniel.rei.api.client.registry.display.DisplayCategory;
import me.shedaniel.rei.api.common.category.CategoryIdentifier;
import me.shedaniel.rei.api.common.entry.EntryStack;
import me.shedaniel.rei.api.common.util.EntryStacks;
import net.minecraft.network.chat.Component;

/**
 * REI category for the mod's growing recipes: seed + accepted soil, the grow-time line, and the harvest
 * drops (or a note for the generic "growable mob" entry).
 */
public final class CropReiCategory implements DisplayCategory<CropDisplay> {
	public static final CategoryIdentifier<CropDisplay> ID = CategoryIdentifier.of(Cultivated.id("crop"));

	private static final int DROPS_PER_ROW = 8;
	private static final int LABEL_COLOR = 0xFF404040;

	@Override
	public CategoryIdentifier<? extends CropDisplay> getCategoryIdentifier() {
		return ID;
	}

	@Override
	public Component getTitle() {
		return Component.translatable("category.cultivated.crop");
	}

	@Override
	public Renderer getIcon() {
		return EntryStacks.of(CultivatedViewerData.iconStack());
	}

	@Override
	public int getDisplayWidth(final CropDisplay display) {
		return 150;
	}

	@Override
	public int getDisplayHeight() {
		return 92;
	}

	@Override
	public List<Widget> setupDisplay(final CropDisplay display, final Rectangle bounds) {
		final List<Widget> widgets = new ArrayList<>();
		widgets.add(Widgets.createRecipeBase(bounds));

		final int left = bounds.x + 6;
		final int top = bounds.y + 6;
		widgets.add(Widgets.createSlot(new Point(left, top)).markInput().entries(display.getInputEntries().get(0)));
		widgets.add(Widgets.createSlot(new Point(left + 24, top)).markInput().entries(display.getInputEntries().get(1)));

		widgets.add(Widgets.createLabel(new Point(left, top + 24), CultivatedViewerData.growTimeLine(display.view().growTicks()))
			.leftAligned().noShadow().color(LABEL_COLOR));

		if (display.view().anySpawnEgg()) {
			widgets.add(Widgets.createLabel(new Point(left, top + 38), Component.translatable("gui.cultivated.viewer.spawn_egg_drops"))
				.leftAligned().noShadow().color(LABEL_COLOR));
		} else {
			int index = 0;
			for (final EntryStack<?> drop : display.getOutputEntries().get(0)) {
				final int x = left + (index % DROPS_PER_ROW) * 18;
				final int y = top + 40 + (index / DROPS_PER_ROW) * 18;
				widgets.add(Widgets.createSlot(new Point(x, y)).markOutput().entry(drop));
				index++;
			}
		}
		return widgets;
	}
}
