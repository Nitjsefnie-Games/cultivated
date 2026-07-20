package dev.nitjsefnie.cultivated.client.render;

import dev.nitjsefnie.cultivated.data.display.Display;
import java.util.List;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.block.BlockModelRenderState;

/**
 * Phase C §C.4 — renders a {@link Display.Simple}: bakes the block state's model (cutout/translucent
 * sheet) with the option's tint applied, at the current growth scale. This is also the delegate the
 * {@link PhasedDisplayRenderer} dispatches block-state phases to (§C.3).
 *
 * <p>The option's face set and fluid layer (§C.4/§C.6) are carried in the {@link ResolvedDisplay}'s
 * {@code options} but are not applied through the high-level block-model submit path in Task C1 (see
 * the C1 report / deferred to Task C2's lower-level geometry path).
 */
@Environment(EnvType.CLIENT)
public final class SimpleDisplayRenderer implements DisplayRenderer<Display> {
	@Override
	public void resolve(final Display display, final DisplayResolveContext context, final List<ResolvedDisplay> out) {
		if (!(display instanceof Display.Simple simple)) {
			return;
		}
		final BlockModelRenderState model = context.bakeBlockModel(simple.blockState(), simple.options());
		if (model.isEmpty()) {
			return;
		}
		out.add(new ResolvedDisplay(model, simple.options(), context.growthScale()));
	}
}
