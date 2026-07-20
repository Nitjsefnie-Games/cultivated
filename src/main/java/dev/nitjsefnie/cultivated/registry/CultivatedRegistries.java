package dev.nitjsefnie.cultivated.registry;

import dev.nitjsefnie.cultivated.Cultivated;

/**
 * Phase G #6 — the mod's registration entry point. Reimplemented directly against Fabric/vanilla
 * registries (no external "content provider" framework): registers the recipe types/serializers,
 * item-override data components, and item attributes.
 */
public final class CultivatedRegistries {
	private CultivatedRegistries() {
	}

	public static void register() {
		ModAttributes.register();
		ModRecipes.register();
		ModComponents.register();
		ModBlocks.register();
		ModBlockEntities.register();
		ModMenus.register();
		ModCreativeTab.register();
		Cultivated.LOGGER.info("Registered Cultivated recipes, components, attributes, blocks, block-entities, menus and creative tab");
	}
}
