package dev.nitjsefnie.cultivated.client.render;

import dev.nitjsefnie.cultivated.data.display.Display;
import java.util.List;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

/**
 * Phase C §C.3 — the phased-display renderer, bound to both {@code aging} and {@code transitional}.
 * Both hold an ordered list of sub-displays and select one by growth progress
 * ({@link PotRenderMath#phaseIndex}); {@code aging} auto-builds its phases from the block
 * ({@link AgingPhases}), {@code transitional} carries them explicitly. The picked phase is delegated
 * back through the registry (a block-state phase lands on the {@link SimpleDisplayRenderer}, §C.3).
 */
@Environment(EnvType.CLIENT)
public final class PhasedDisplayRenderer implements DisplayRenderer<Display> {
	@Override
	public void resolve(final Display display, final DisplayResolveContext context, final List<ResolvedDisplay> out) {
		final List<Display> phases = phasesOf(display);
		if (phases.isEmpty()) {
			return;
		}
		final int index = PotRenderMath.phaseIndex(context.progress(), phases.size());
		context.resolve(phases.get(index), out);
	}

	private static List<Display> phasesOf(final Display display) {
		if (display instanceof Display.Transitional transitional) {
			return transitional.phases();
		}
		if (display instanceof Display.Aging aging) {
			return AgingPhases.build(aging);
		}
		return List.of();
	}
}
