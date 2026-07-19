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
		Cultivated.LOGGER.info("Registered Cultivated recipe types, serializers, components and attributes");
	}
}
