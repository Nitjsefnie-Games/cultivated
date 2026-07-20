package dev.nitjsefnie.cultivated.client.render;

import dev.nitjsefnie.cultivated.data.display.RenderOptions;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.block.BlockModelRenderState;

/**
 * Phase C §C.4/§C.6 — a single block-model draw resolved during {@code extractRenderState} and
 * replayed during {@code submit}. The 26.2 render pipeline splits per-frame extraction (which needs
 * the live block entity + a {@link net.minecraft.client.renderer.block.BlockModelResolver}) from the
 * later {@code submit} pass (which only has the render state), so the resolved {@link BlockModelRenderState}
 * and the transform inputs ({@link RenderOptions} + the applied growth scale) are captured here.
 */
@Environment(EnvType.CLIENT)
public final class ResolvedDisplay {
	private final BlockModelRenderState model;
	private final RenderOptions options;
	private final float growthScale;

	public ResolvedDisplay(final BlockModelRenderState model, final RenderOptions options, final float growthScale) {
		this.model = model;
		this.options = options;
		this.growthScale = growthScale;
	}

	public BlockModelRenderState model() {
		return this.model;
	}

	public RenderOptions options() {
		return this.options;
	}

	/** The scale applied on top of {@link RenderOptions#scale()} (crop growth ramp, or 1.0). */
	public float growthScale() {
		return this.growthScale;
	}
}
