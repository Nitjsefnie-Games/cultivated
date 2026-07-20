package dev.nitjsefnie.cultivated.compat.jei;

import dev.nitjsefnie.cultivated.Cultivated;
import dev.nitjsefnie.cultivated.compat.CultivatedViewerData;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import net.minecraft.resources.Identifier;

/**
 * JEI integration entry point (discovered by JEI's {@code @JeiPlugin} annotation scan — never
 * referenced from the mod's own common/client init, so nothing in {@code compat.jei} loads when JEI is
 * absent). Registers the two pot categories and feeds them from {@link CultivatedViewerData}.
 */
@JeiPlugin
public final class CultivatedJeiPlugin implements IModPlugin {
	private static final Identifier PLUGIN_UID = Cultivated.id("jei");

	@Override
	public Identifier getPluginUid() {
		return PLUGIN_UID;
	}

	@Override
	public void registerCategories(final IRecipeCategoryRegistration registration) {
		registration.addRecipeCategories(
			new CropCategory(registration.getJeiHelpers().getGuiHelper()),
			new InteractionCategory(registration.getJeiHelpers().getGuiHelper())
		);
	}

	@Override
	public void registerRecipes(final IRecipeRegistration registration) {
		registration.addRecipes(CropCategory.TYPE, CultivatedViewerData.crops());
		registration.addRecipes(InteractionCategory.TYPE, CultivatedViewerData.interactions());
	}
}
