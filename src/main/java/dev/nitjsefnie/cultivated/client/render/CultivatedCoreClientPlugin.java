package dev.nitjsefnie.cultivated.client.render;

import dev.nitjsefnie.cultivated.data.display.Display;
import dev.nitjsefnie.cultivated.plugin.CultivatedClientPlugin;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

/**
 * Task F3 §F.3 — the core's own {@link CultivatedClientPlugin}: binds the built-in display renderers
 * ({@code simple} §C.4, the phased {@code aging}/{@code transitional} §C.3, and {@code entity}/{@code
 * textured_cube} §C.5) through the same client-plugin path add-ons use. Bound first, before any add-on
 * client plugin, when {@link DisplayRendererRegistry#createDefault()} builds a registry.
 */
@Environment(EnvType.CLIENT)
public final class CultivatedCoreClientPlugin implements CultivatedClientPlugin {
	@Override
	public void bindDisplayRenderers(final DisplayRendererRegistry registry) {
		registry.register(Display.SIMPLE_TYPE, new SimpleDisplayRenderer());
		final PhasedDisplayRenderer phased = new PhasedDisplayRenderer();
		registry.register(Display.AGING_TYPE, phased);
		registry.register(Display.TRANSITIONAL_TYPE, phased);
		registry.register(Display.ENTITY_TYPE, new EntityDisplayRenderer());
		registry.register(Display.TEXTURED_CUBE_TYPE, new TexturedCubeDisplayRenderer());
	}
}
