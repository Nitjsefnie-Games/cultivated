package dev.nitjsefnie.cultivated.compat.jei;

import dev.nitjsefnie.cultivated.Cultivated;
import dev.nitjsefnie.cultivated.compat.CultivatedViewerData;
import dev.nitjsefnie.cultivated.compat.InteractionView;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.recipe.types.IRecipeType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

/**
 * JEI category for {@code cultivated:pot_interaction} recipes: the held item plus any required soil/seed
 * on the left, the resulting soil/seed on the right, and the held-item fate (consumed / damaged) as a
 * text line below.
 */
public final class InteractionCategory implements IRecipeCategory<InteractionView> {
	public static final IRecipeType<InteractionView> TYPE =
		IRecipeType.create(Cultivated.MOD_ID, "pot_interaction", InteractionView.class);

	private static final int WIDTH = 160;
	private static final int HEIGHT = 54;
	private static final int TEXT_COLOR = 0xFF404040;

	private final IDrawable icon;

	public InteractionCategory(final IGuiHelper guiHelper) {
		this.icon = guiHelper.createDrawableItemStack(CultivatedViewerData.iconStack());
	}

	@Override
	public IRecipeType<InteractionView> getRecipeType() {
		return TYPE;
	}

	@Override
	public Component getTitle() {
		return Component.translatable("category.cultivated.interaction");
	}

	@Override
	public int getWidth() {
		return WIDTH;
	}

	@Override
	public int getHeight() {
		return HEIGHT;
	}

	@Override
	public IDrawable getIcon() {
		return this.icon;
	}

	@Override
	public void setRecipe(final IRecipeLayoutBuilder builder, final InteractionView recipe, final IFocusGroup focuses) {
		builder.addSlot(RecipeIngredientRole.INPUT, 6, 4)
			.setStandardSlotBackground()
			.addItemStacks(recipe.held());
		if (!recipe.requiredSoil().isEmpty()) {
			builder.addSlot(RecipeIngredientRole.INPUT, 30, 4)
				.setStandardSlotBackground()
				.addItemStacks(recipe.requiredSoil());
		}
		if (!recipe.requiredSeed().isEmpty()) {
			builder.addSlot(RecipeIngredientRole.INPUT, 48, 4)
				.setStandardSlotBackground()
				.addItemStacks(recipe.requiredSeed());
		}

		if (!recipe.resultSoil().isEmpty()) {
			builder.addSlot(RecipeIngredientRole.OUTPUT, 116, 4)
				.setOutputSlotBackground()
				.addItemStacks(recipe.resultSoil());
		}
		if (!recipe.resultSeed().isEmpty()) {
			builder.addSlot(RecipeIngredientRole.OUTPUT, 134, 4)
				.setOutputSlotBackground()
				.addItemStacks(recipe.resultSeed());
		}
	}

	@Override
	public void draw(
		final InteractionView recipe,
		final IRecipeSlotsView slotsView,
		final GuiGraphicsExtractor guiGraphics,
		final double mouseX,
		final double mouseY
	) {
		final Font font = Minecraft.getInstance().font;
		final String key = recipe.consumesHeld()
			? "gui.cultivated.viewer.consumes_held"
			: recipe.damagesHeld() ? "gui.cultivated.viewer.damages_held" : "gui.cultivated.viewer.keeps_held";
		guiGraphics.text(font, Component.translatable(key), 6, 30, TEXT_COLOR);
	}
}
