package dev.nitjsefnie.cultivated.client.render;

import dev.nitjsefnie.cultivated.Cultivated;
import dev.nitjsefnie.cultivated.data.display.Display;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

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

	/** The default registry for Task C1: {@code simple} + phased {@code aging}/{@code transitional}. */
	public static DisplayRendererRegistry createDefault() {
		final DisplayRendererRegistry registry = new DisplayRendererRegistry();
		registry.register(Display.SIMPLE_TYPE, new SimpleDisplayRenderer());
		final PhasedDisplayRenderer phased = new PhasedDisplayRenderer();
		registry.register(Display.AGING_TYPE, phased);
		registry.register(Display.TRANSITIONAL_TYPE, phased);
		return registry;
	}
}
