package dev.nitjsefnie.cultivated.compat.rei;

import dev.nitjsefnie.cultivated.Cultivated;
import dev.nitjsefnie.cultivated.compat.CultivatedViewerData;
import dev.nitjsefnie.cultivated.compat.InteractionView;
import java.util.ArrayList;
import java.util.List;
import me.shedaniel.math.Point;
import me.shedaniel.math.Rectangle;
import me.shedaniel.rei.api.client.gui.Renderer;
import me.shedaniel.rei.api.client.gui.widgets.Slot;
import me.shedaniel.rei.api.client.gui.widgets.Widget;
import me.shedaniel.rei.api.client.gui.widgets.Widgets;
import me.shedaniel.rei.api.client.registry.display.DisplayCategory;
import me.shedaniel.rei.api.common.category.CategoryIdentifier;
import me.shedaniel.rei.api.common.entry.EntryIngredient;
import me.shedaniel.rei.api.common.util.EntryIngredients;
import me.shedaniel.rei.api.common.util.EntryStacks;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/**
 * REI category for {@code cultivated:pot_interaction} recipes: held item plus any required soil/seed on
 * the left, an arrow, the resulting soil/seed on the right, and the held-item fate as a text line.
 */
public final class InteractionReiCategory implements DisplayCategory<InteractionDisplay> {
	public static final CategoryIdentifier<InteractionDisplay> ID =
		CategoryIdentifier.of(Cultivated.id("pot_interaction"));

	private static final int LABEL_COLOR = 0xFF404040;

	@Override
	public CategoryIdentifier<? extends InteractionDisplay> getCategoryIdentifier() {
		return ID;
	}

	@Override
	public Component getTitle() {
		return Component.translatable("category.cultivated.interaction");
	}

	@Override
	public Renderer getIcon() {
		return EntryStacks.of(CultivatedViewerData.iconStack());
	}

	@Override
	public int getDisplayWidth(final InteractionDisplay display) {
		return 150;
	}

	@Override
	public int getDisplayHeight() {
		return 64;
	}

	@Override
	public List<Widget> setupDisplay(final InteractionDisplay display, final Rectangle bounds) {
		final InteractionView view = display.view();
		final List<Widget> widgets = new ArrayList<>();
		widgets.add(Widgets.createRecipeBase(bounds));

		final int top = bounds.y + 6;
		int x = bounds.x + 6;
		widgets.add(inputSlot(x, top, view.held()));
		x += 22;
		if (!view.requiredSoil().isEmpty()) {
			widgets.add(inputSlot(x, top, view.requiredSoil()));
			x += 22;
		}
		if (!view.requiredSeed().isEmpty()) {
			widgets.add(inputSlot(x, top, view.requiredSeed()));
			x += 22;
		}

		widgets.add(Widgets.createArrow(new Point(x + 2, top)));
		x += 30;

		if (!view.resultSoil().isEmpty()) {
			widgets.add(outputSlot(x, top, view.resultSoil()));
			x += 22;
		}
		if (!view.resultSeed().isEmpty()) {
			widgets.add(outputSlot(x, top, view.resultSeed()));
		}

		final String key = view.consumesHeld()
			? "gui.cultivated.viewer.consumes_held"
			: view.damagesHeld() ? "gui.cultivated.viewer.damages_held" : "gui.cultivated.viewer.keeps_held";
		widgets.add(Widgets.createLabel(new Point(bounds.x + 6, top + 30), Component.translatable(key))
			.leftAligned().noShadow().color(LABEL_COLOR));
		return widgets;
	}

	private static Slot inputSlot(final int x, final int y, final List<ItemStack> stacks) {
		final EntryIngredient ingredient = EntryIngredients.ofItemStacks(stacks);
		return Widgets.createSlot(new Point(x, y)).markInput().entries(ingredient);
	}

	private static Slot outputSlot(final int x, final int y, final List<ItemStack> stacks) {
		final EntryIngredient ingredient = EntryIngredients.ofItemStacks(stacks);
		return Widgets.createSlot(new Point(x, y)).markOutput().entries(ingredient);
	}
}
