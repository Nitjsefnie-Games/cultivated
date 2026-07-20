package dev.nitjsefnie.cultivated.client.render;

import dev.nitjsefnie.cultivated.data.display.Display;
import java.util.List;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

/**
 * Phase C §C.4 — renders a {@link Display.Simple}: draws the block state's model honoring the option's
 * {@code faces} set (a full model takes the fast high-level submit path; a restricted face set takes
 * the low-level VertexConsumer path), its {@code render_fluid} layer (§C.4, for water/lava-style
 * soils) and tint (explicit {@code color}, else the quad's world tint). Delegated to by
 * {@link PhasedDisplayRenderer} for {@code aging}/{@code transitional} block-state phases (§C.3);
 * the geometry decision itself lives in {@link DisplayResolveContext#resolveSimple}.
 */
@Environment(EnvType.CLIENT)
public final class SimpleDisplayRenderer implements DisplayRenderer<Display> {
	@Override
	public void resolve(final Display display, final DisplayResolveContext context, final List<ResolvedDisplay> out) {
		if (!(display instanceof Display.Simple simple)) {
			return;
		}
		context.resolveSimple(simple.blockState(), simple.options(), out);
	}
}
