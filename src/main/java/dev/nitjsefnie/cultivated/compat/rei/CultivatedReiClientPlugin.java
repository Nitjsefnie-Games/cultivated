package dev.nitjsefnie.cultivated.compat.rei;

import dev.nitjsefnie.cultivated.compat.CropView;
import dev.nitjsefnie.cultivated.compat.CultivatedViewerData;
import dev.nitjsefnie.cultivated.compat.InteractionView;
import me.shedaniel.rei.api.client.plugins.REIClientPlugin;
import me.shedaniel.rei.api.client.registry.category.CategoryRegistry;
import me.shedaniel.rei.api.client.registry.display.DisplayRegistry;
import me.shedaniel.rei.api.common.entry.EntryStack;
import me.shedaniel.rei.api.common.util.EntryStacks;
import net.minecraft.world.item.ItemStack;

/**
 * REI integration entry point (declared as the {@code rei_client} entrypoint in {@code fabric.mod.json},
 * so it is only loaded through REI's own plugin discovery — nothing in {@code compat.rei} is referenced
 * from the mod's common/client init, keeping REI a truly optional dependency). Registers the Crop and
 * Interaction categories, marks the botany pot as their workstation, and feeds displays from
 * {@link CultivatedViewerData}.
 */
public final class CultivatedReiClientPlugin implements REIClientPlugin {
	@Override
	public void registerCategories(final CategoryRegistry registry) {
		registry.add(new CropReiCategory());
		registry.add(new InteractionReiCategory());

		final EntryStack<ItemStack> pot = EntryStacks.of(CultivatedViewerData.iconStack());
		registry.addWorkstations(CropReiCategory.ID, pot);
		registry.addWorkstations(InteractionReiCategory.ID, pot);
	}

	@Override
	public void registerDisplays(final DisplayRegistry registry) {
		for (final CropView view : CultivatedViewerData.crops()) {
			registry.add(new CropDisplay(view));
		}
		for (final InteractionView view : CultivatedViewerData.interactions()) {
			registry.add(new InteractionDisplay(view));
		}
	}
}
