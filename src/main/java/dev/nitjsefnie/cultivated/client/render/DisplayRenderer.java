package dev.nitjsefnie.cultivated.client.render;

import dev.nitjsefnie.cultivated.data.display.Display;
import java.util.List;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

/**
 * Phase C §C.2 — the client renderer bound to a {@link Display} variant. Each implementation resolves
 * its display into zero or more {@link ResolvedDisplay} block-model draws (during {@code
 * extractRenderState}); the {@link BotanyPotBlockEntityRenderer} later replays those draws with the
 * {@link RenderOptions} transforms applied (§C.6).
 *
 * <p>Task C1 provides the {@link SimpleDisplayRenderer} (§C.4) and the {@link PhasedDisplayRenderer}
 * for {@code aging}/{@code transitional} (§C.3). The {@link DisplayRendererRegistry} is the extension
 * point Task C2 registers {@code textured_cube}/{@code entity} into without touching this core.
 */
@FunctionalInterface
@Environment(EnvType.CLIENT)
public interface DisplayRenderer<D extends Display> {
	/** Resolve {@code display} into block-model draws, appending them to {@code out}. */
	void resolve(D display, DisplayResolveContext context, List<ResolvedDisplay> out);
}
