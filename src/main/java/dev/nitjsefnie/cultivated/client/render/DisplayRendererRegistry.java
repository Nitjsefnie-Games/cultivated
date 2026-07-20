package dev.nitjsefnie.cultivated.client.render;

import dev.nitjsefnie.cultivated.Cultivated;
import dev.nitjsefnie.cultivated.data.display.Display;
import dev.nitjsefnie.cultivated.plugin.CultivatedClientPlugin;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.entrypoint.EntrypointContainer;

/**
 * Phase C §C.2 — the display→renderer dispatch: maps each {@link Display} {@code typeId} to its bound
 * {@link DisplayRenderer}. Task C1 registers {@code simple} (§C.4) and the phased {@code aging}/{@code
 * transitional} renderers (§C.3); Task C2 adds {@code textured_cube}/{@code entity} by calling
 * {@link #register} — no edit to this class's core is needed. An unknown display type resolves to
 * nothing (logged once) rather than throwing during a render frame.
 */
@Environment(EnvType.CLIENT)
public final class DisplayRendererRegistry {
	private final Map<String, DisplayRenderer<?>> byType = new HashMap<>();
	private final java.util.Set<String> warned = new java.util.HashSet<>();

	public <D extends Display> void register(final String typeId, final DisplayRenderer<D> renderer) {
		this.byType.put(typeId, renderer);
	}

	/** Dispatch {@code display} to its bound renderer, appending resolved draws to {@code out}. */
	@SuppressWarnings("unchecked")
	public void resolve(final Display display, final DisplayResolveContext context, final List<ResolvedDisplay> out) {
		final DisplayRenderer<Display> renderer = (DisplayRenderer<Display>)this.byType.get(display.typeId());
		if (renderer == null) {
			if (this.warned.add(display.typeId())) {
				Cultivated.LOGGER.warn("No display renderer bound for type {} (rendered as empty)", display.typeId());
			}
			return;
		}
		renderer.resolve(display, context, out);
	}

	/** The Fabric custom entrypoint key add-ons register their client display renderers under (§F.3). */
	private static final String CLIENT_ENTRYPOINT_KEY = "cultivated:client_plugin";

	/**
	 * The default registry, built through the client-plugin path (§F.3): the core's own renderers
	 * ({@code simple} §C.4, phased {@code aging}/{@code transitional} §C.3, {@code entity} + {@code
	 * textured_cube} §C.5) are bound first via {@link CultivatedCoreClientPlugin}, then every add-on
	 * {@link CultivatedClientPlugin} declared under {@code cultivated:client_plugin} — so add-ons bind
	 * renderers for their own display types without editing this class.
	 */
	public static DisplayRendererRegistry createDefault() {
		final DisplayRendererRegistry registry = new DisplayRendererRegistry();
		new CultivatedCoreClientPlugin().bindDisplayRenderers(registry);
		for (final EntrypointContainer<CultivatedClientPlugin> container : clientPlugins()) {
			final CultivatedClientPlugin plugin = container.getEntrypoint();
			try {
				plugin.bindDisplayRenderers(registry);
			} catch (final RuntimeException failure) {
				Cultivated.LOGGER.error("Cultivated client plugin {} failed to bind renderers", plugin.getClass().getName(), failure);
			}
		}
		return registry;
	}

	private static List<EntrypointContainer<CultivatedClientPlugin>> clientPlugins() {
		try {
			return FabricLoader.getInstance().getEntrypointContainers(CLIENT_ENTRYPOINT_KEY, CultivatedClientPlugin.class);
		} catch (final RuntimeException | LinkageError unavailable) {
			return List.of();
		}
	}
}
