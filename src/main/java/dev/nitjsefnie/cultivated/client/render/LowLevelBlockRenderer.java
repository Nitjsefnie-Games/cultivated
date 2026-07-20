package dev.nitjsefnie.cultivated.client.render;

import com.mojang.blaze3d.vertex.QuadInstance;
import dev.nitjsefnie.cultivated.data.display.RenderOptions;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.color.block.BlockColors;
import net.minecraft.client.color.block.BlockTintSource;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * Phase C §C.4/§C.6 — the low-level, {@link com.mojang.blaze3d.vertex.VertexConsumer}-based path a
 * {@code simple} display takes when the high-level {@code BlockModelRenderState.submit} path cannot
 * express its {@code options}: a restricted {@code faces} set (draw only the listed faces) or a
 * per-quad world tint. It bakes the block state's model into {@link BakedQuad}s
 * ({@link BlockStateModel#collectParts}), then during {@code submit} emits each face-selected quad
 * through {@link net.minecraft.client.renderer.SubmitNodeCollector#submitCustomGeometry} using
 * {@link com.mojang.blaze3d.vertex.VertexConsumer#putBakedQuad}.
 *
 * <p>Tint (§C.4): an explicit option {@code color} tints the whole render (its own alpha honored, so
 * translucent tints work); otherwise a quad with a tint index takes the block's own world tint
 * ({@link BlockTintSource#color}); an untinted quad renders at its texture colors.
 */
@Environment(EnvType.CLIENT)
public final class LowLevelBlockRenderer {
	private static final long MODEL_SEED = 42L;

	private LowLevelBlockRenderer() {
	}

	/**
	 * Build the face-filtered, tinted quad geometry for {@code blockState} under {@code options}, or
	 * {@code null} if the model produces no quads (e.g. a fluid block whose visuals come only from its
	 * fluid layer) — so an empty draw does not occupy a stack-height slot.
	 */
	public static @Nullable DisplayGeometry geometry(final BlockState blockState, final RenderOptions options) {
		final Minecraft minecraft = Minecraft.getInstance();
		final BlockStateModel model = minecraft.getModelManager().getBlockStateModelSet().get(blockState);
		final BlockColors blockColors = minecraft.getBlockColors();

		final List<BlockStateModelPart> parts = new ArrayList<>();
		model.collectParts(RandomSource.create(MODEL_SEED), parts);
		if (!hasAnyQuad(parts)) {
			return null;
		}

		final int overrideColor = options.color().map(color -> color.argb()).orElse(0);
		final boolean hasOverride = options.color().isPresent();
		final boolean overrideTranslucent = hasOverride && (overrideColor >>> 24) != 0xFF;
		final boolean translucent = overrideTranslucent || model.hasMaterialFlag(BakedQuad.FLAG_TRANSLUCENT);
		final RenderType renderType = translucent ? Sheets.translucentBlockItemSheet() : Sheets.cutoutBlockItemSheet();
		final Set<Direction> faces = options.faces();

		return (poseStack, collector, lightCoords, camera) -> collector.submitCustomGeometry(poseStack, renderType, (pose, buffer) -> {
			final QuadInstance instance = new QuadInstance();
			instance.setLightCoords(lightCoords);
			for (final BlockStateModelPart part : parts) {
				for (final Direction direction : Direction.values()) {
					if (PotRenderMath.shouldDrawFace(faces, direction)) {
						for (final BakedQuad quad : part.getQuads(direction)) {
							instance.setColor(quadColor(quad, blockState, blockColors, hasOverride, overrideColor));
							buffer.putBakedQuad(pose, quad, instance);
						}
					}
				}
				for (final BakedQuad quad : part.getQuads(null)) {
					instance.setColor(quadColor(quad, blockState, blockColors, hasOverride, overrideColor));
					buffer.putBakedQuad(pose, quad, instance);
				}
			}
		});
	}

	private static boolean hasAnyQuad(final List<BlockStateModelPart> parts) {
		for (final BlockStateModelPart part : parts) {
			if (!part.getQuads(null).isEmpty()) {
				return true;
			}
			for (final Direction direction : Direction.values()) {
				if (!part.getQuads(direction).isEmpty()) {
					return true;
				}
			}
		}
		return false;
	}

	private static int quadColor(
		final BakedQuad quad, final BlockState blockState, final BlockColors blockColors, final boolean hasOverride, final int overrideColor
	) {
		if (hasOverride) {
			return overrideColor;
		}
		final int tintIndex = quad.materialInfo().tintIndex();
		if (tintIndex == -1) {
			return -1;
		}
		final BlockTintSource tintSource = blockColors.getTintSource(blockState, tintIndex);
		if (tintSource == null) {
			return -1;
		}
		return 0xFF000000 | tintSource.color(blockState);
	}
}
