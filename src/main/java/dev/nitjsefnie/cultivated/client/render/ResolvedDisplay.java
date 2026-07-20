package dev.nitjsefnie.cultivated.client.render;

import dev.nitjsefnie.cultivated.data.display.RenderOptions;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

/**
 * Phase C §C.4/§C.5/§C.6 — a single display draw resolved during {@code extractRenderState} and
 * replayed during {@code submit}. The 26.2 render pipeline splits per-frame extraction (which needs
 * the live block entity + the model/sprite/entity resolvers) from the later {@code submit} pass
 * (which only has the render state), so the resolved {@link DisplayGeometry} and the transform inputs
 * ({@link RenderOptions} + the applied growth scale) are captured here.
 */
@Environment(EnvType.CLIENT)
public final class ResolvedDisplay {
	private final DisplayGeometry geometry;
	private final RenderOptions options;
	private final float growthScale;

	public ResolvedDisplay(final DisplayGeometry geometry, final RenderOptions options, final float growthScale) {
		this.geometry = geometry;
		this.options = options;
		this.growthScale = growthScale;
	}

	public DisplayGeometry geometry() {
		return this.geometry;
	}

	public RenderOptions options() {
		return this.options;
	}

	/** The scale applied on top of {@link RenderOptions#scale()} (crop growth ramp, or 1.0). */
	public float growthScale() {
		return this.growthScale;
	}
}
