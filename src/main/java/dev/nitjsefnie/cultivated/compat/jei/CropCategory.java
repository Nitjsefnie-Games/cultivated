package dev.nitjsefnie.cultivated.compat.jei;

import dev.nitjsefnie.cultivated.Cultivated;
import dev.nitjsefnie.cultivated.compat.CropView;
import dev.nitjsefnie.cultivated.compat.CultivatedViewerData;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import java.util.List;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.recipe.types.IRecipeType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

/**
 * JEI category for the mod's growing recipes: seed + accepted soil in the top row, the grow-time line
 * below, and the possible harvest drops in a wrapped bottom row. The single generic "growable mob"
 * entry ({@link CropView#anySpawnEgg()}) shows a note in place of drops (its per-mob loot cannot be
 * enumerated statically).
 */
public final class CropCategory implements IRecipeCategory<CropView> {
	public static final IRecipeType<CropView> TYPE = IRecipeType.create(Cultivated.MOD_ID, "crop", CropView.class);

	private static final int WIDTH = 160;
	private static final int HEIGHT = 58;
	private static final int DROPS_PER_ROW = 8;
	private static final int TEXT_COLOR = 0xFF404040;

	private final IDrawable icon;

	public CropCategory(final IGuiHelper guiHelper) {
		this.icon = guiHelper.createDrawableItemStack(CultivatedViewerData.iconStack());
	}

	@Override
	public IRecipeType<CropView> getRecipeType() {
		return TYPE;
	}

	@Override
	public Component getTitle() {
		return Component.translatable("category.cultivated.crop");
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
	public void setRecipe(final IRecipeLayoutBuilder builder, final CropView recipe, final IFocusGroup focuses) {
		builder.addSlot(RecipeIngredientRole.INPUT, 6, 4)
			.setStandardSlotBackground()
			.addItemStacks(recipe.seeds());
		builder.addSlot(RecipeIngredientRole.INPUT, 30, 4)
			.setStandardSlotBackground()
			.addItemStacks(recipe.soils());

		int index = 0;
		for (final var drop : recipe.drops()) {
			final int x = 6 + (index % DROPS_PER_ROW) * 18;
			final int y = 36 + (index / DROPS_PER_ROW) * 18;
			builder.addSlot(RecipeIngredientRole.OUTPUT, x, y)
				.setOutputSlotBackground()
				.addItemStacks(List.of(drop));
			index++;
		}
	}

	@Override
	public void draw(
		final CropView recipe,
		final IRecipeSlotsView slotsView,
		final GuiGraphicsExtractor guiGraphics,
		final double mouseX,
		final double mouseY
	) {
		final Font font = Minecraft.getInstance().font;
		guiGraphics.text(font, CultivatedViewerData.growTimeLine(recipe.growTicks()), 6, 24, TEXT_COLOR);
		if (recipe.anySpawnEgg()) {
			guiGraphics.text(font, Component.translatable("gui.cultivated.viewer.spawn_egg_drops"), 6, 38, TEXT_COLOR);
		}
	}
}
