package dev.nitjsefnie.cultivated.plugin;

import dev.nitjsefnie.cultivated.client.render.DisplayRendererRegistry;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

/**
 * Task F3 §F.3 — the client-only companion to {@link CultivatedPlugin}, loaded via the Fabric custom
 * entrypoint {@code cultivated:client_plugin}. Lets an add-on bind a client display renderer for any
 * display {@code type} it registered common-side, without editing core. An add-on declares it in its
 * own {@code fabric.mod.json}:
 *
 * <pre>{@code
 * "entrypoints": { "cultivated:client_plugin": [ "com.example.MyCultivatedClientPlugin" ] }
 * }</pre>
 *
 * <p>Invoked while the pot block-entity renderer builds its {@link DisplayRendererRegistry}, after the
 * core's own renderers are bound.
 */
@Environment(EnvType.CLIENT)
@FunctionalInterface
public interface CultivatedClientPlugin {
	/** Bind this add-on's display renderers into {@code registry} (§C.2). */
	void bindDisplayRenderers(DisplayRendererRegistry registry);
}
