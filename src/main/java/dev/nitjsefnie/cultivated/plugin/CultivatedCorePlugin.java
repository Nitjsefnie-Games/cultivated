package dev.nitjsefnie.cultivated.plugin;

import dev.nitjsefnie.cultivated.command.generator.TaggedSoilGenerator;
import dev.nitjsefnie.cultivated.condition.LoadCondition;
import dev.nitjsefnie.cultivated.data.display.Display;
import dev.nitjsefnie.cultivated.data.drop.DropProvider;
import dev.nitjsefnie.cultivated.data.growth.GrowthAmount;
import dev.nitjsefnie.cultivated.ingredient.CultivatedIngredient;
import dev.nitjsefnie.cultivated.registry.ModTags;

/**
 * Task F3 §F.3 — the core's own {@link CultivatedPlugin}. The mod registers ALL of its built-in
 * {@code type}-dispatched value kinds and generators through the exact same {@link CultivatedPluginContext}
 * path add-ons use, so there is a single registration mechanism (I2). Invoked directly first by
 * {@link CultivatedPlugins#loadCommon()} (before any add-on plugin), rather than through the Fabric
 * entrypoint, so it also runs in unit tests that never launch the Fabric loader.
 */
public final class CultivatedCorePlugin implements CultivatedPlugin {
	@Override
	public void register(final CultivatedPluginContext context) {
		// The five type-dispatched value kinds (I2) — each feeds its built-ins through the context.
		Display.registerBuiltins(context::registerDisplay);
		DropProvider.registerBuiltins(context::registerDropProvider);
		GrowthAmount.registerBuiltins(context::registerGrowthAmount);
		CultivatedIngredient.registerBuiltins(context::registerIngredient);
		LoadCondition.registerBuiltins(context::registerLoadCondition);

		// Built-in tagged fluid/special soil generators (§F.2); they win for their tagged members and
		// are consulted before the always-last fallback generators.
		context.registerSoilGenerator(new TaggedSoilGenerator(ModTags.SOIL_WATER, "minecraft:water", true, 0.0, 0));
		context.registerSoilGenerator(new TaggedSoilGenerator(ModTags.SOIL_LAVA, "minecraft:lava", true, 0.2, 15));
		context.registerSoilGenerator(new TaggedSoilGenerator(ModTags.SOIL_SNOW, "minecraft:snow_block", false, 0.0, 0));
	}
}
