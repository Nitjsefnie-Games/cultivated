package dev.nitjsefnie.cultivated.client.render;

import dev.nitjsefnie.cultivated.data.display.Display;
import dev.nitjsefnie.cultivated.data.display.RenderOptions;
import it.unimi.dsi.fastutil.ints.IntList;
import java.util.List;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.block.BlockModelRenderState;
import net.minecraft.client.renderer.block.BlockModelResolver;
import net.minecraft.client.renderer.block.model.BlockDisplayContext;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Phase C — the inputs a {@link DisplayRenderer} needs while resolving a {@link Display} into
 * {@link ResolvedDisplay} draws during {@code extractRenderState}: the block-model resolver, the
 * dispatch registry (so phased displays can recurse into their picked sub-display), the current
 * growth progress (for phase selection, §C.3) and the growth scale applied to each draw (§C.1).
 */
@Environment(EnvType.CLIENT)
public final class DisplayResolveContext {
	private static final BlockDisplayContext BLOCK_DISPLAY_CONTEXT = BlockDisplayContext.create();

	private final BlockModelResolver blockModelResolver;
	private final DisplayRendererRegistry registry;
	private final float progress;
	private final float growthScale;

	public DisplayResolveContext(
		final BlockModelResolver blockModelResolver,
		final DisplayRendererRegistry registry,
		final float progress,
		final float growthScale
	) {
		this.blockModelResolver = blockModelResolver;
		this.registry = registry;
		this.progress = progress;
		this.growthScale = growthScale;
	}

	/** Growth progress in {@code [0,1]} used to pick a phased display's sub-display (§C.3). */
	public float progress() {
		return this.progress;
	}

	/** The scale applied on top of a display's own {@link RenderOptions#scale()} (§C.1). */
	public float growthScale() {
		return this.growthScale;
	}

	/** Dispatch a (sub-)display back through the registry — used by phased displays to delegate (§C.3). */
	public void resolve(final Display display, final List<ResolvedDisplay> out) {
		this.registry.resolve(display, this, out);
	}

	/**
	 * Bake a block state's model into a fresh {@link BlockModelRenderState} on the cutout/translucent
	 * sheet (§C.4), then apply the option's tint to the model's tint layers if one is set — otherwise
	 * the block's own quad tints stand (§C.4 "else uses the block's world tint if the quad is tinted").
	 */
	public BlockModelRenderState bakeBlockModel(final BlockState blockState, final RenderOptions options) {
		final BlockModelRenderState model = new BlockModelRenderState();
		this.blockModelResolver.update(model, blockState, BLOCK_DISPLAY_CONTEXT);
		options.color().ifPresent(color -> {
			final IntList tints = model.tintLayers();
			for (int i = 0; i < tints.size(); i++) {
				tints.set(i, color.argb());
			}
		});
		return model;
	}
}
