package dev.nitjsefnie.cultivated.plugin;

/**
 * Task F3 §F.3 — a Cultivated add-on entry point, loaded via the Fabric custom entrypoint
 * {@code cultivated:plugin}. An add-on declares its implementation in its own {@code fabric.mod.json}:
 *
 * <pre>{@code
 * "entrypoints": { "cultivated:plugin": [ "com.example.MyCultivatedPlugin" ] }
 * }</pre>
 *
 * <p>All registrations happen through the supplied {@link CultivatedPluginContext} during common
 * initialisation, before recipe/datapack load — so new display / drop-provider / growth-amount /
 * ingredient / load-condition {@code type}s and soil/crop generators are available when the data
 * engine first parses JSON. Client-side display renderers bind through the separate
 * {@link CultivatedClientPlugin} ({@code cultivated:client_plugin}).
 *
 * <p>The core registers its own built-ins through this same path ({@link CultivatedCorePlugin}).
 */
@FunctionalInterface
public interface CultivatedPlugin {
	/** Register this add-on's generators and {@code type}-dispatched value kinds. */
	void register(CultivatedPluginContext context);
}
