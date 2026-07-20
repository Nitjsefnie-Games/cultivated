package dev.nitjsefnie.cultivated.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.nitjsefnie.cultivated.data.display.RenderOptions;
import java.util.Set;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Direction;

/**
 * Phase C §C.5 — draws a unit cube {@code (0..1)} from a single block-atlas sprite and an ARGB tint,
 * on the translucent block-item sheet. Shared by the {@code textured_cube} display and by a {@code
 * simple} display's {@code render_fluid} layer (which uses the fluid's still sprite + tint). Faces are
 * restricted to the option {@code faces} set (§C.6). Emitted via
 * {@link net.minecraft.client.renderer.SubmitNodeCollector#submitCustomGeometry}.
 */
@Environment(EnvType.CLIENT)
public final class CubeRenderer {
	/** The four unit-cube corners of each face, wound counter-clockwise as seen from outside. */
	private static final float[][][] FACE_CORNERS = buildFaceCorners();

	private CubeRenderer() {
	}

	/** A {@link DisplayGeometry} drawing a unit cube of {@code sprite} tinted {@code argb}, on listed faces. */
	public static DisplayGeometry geometry(final TextureAtlasSprite sprite, final int argb, final RenderOptions options) {
		final RenderType renderType = Sheets.translucentBlockItemSheet();
		final Set<Direction> faces = options.faces();
		return (poseStack, collector, lightCoords, camera) -> collector.submitCustomGeometry(
			poseStack, renderType, (pose, buffer) -> emit(pose, buffer, sprite, argb, faces, lightCoords)
		);
	}

	private static void emit(
		final PoseStack.Pose pose,
		final VertexConsumer buffer,
		final TextureAtlasSprite sprite,
		final int argb,
		final Set<Direction> faces,
		final int lightCoords
	) {
		final float u0 = sprite.getU0();
		final float u1 = sprite.getU1();
		final float v0 = sprite.getV0();
		final float v1 = sprite.getV1();
		for (final Direction direction : Direction.values()) {
			if (!faces.contains(direction)) {
				continue;
			}
			final float[][] corners = FACE_CORNERS[direction.ordinal()];
			final float nx = direction.getStepX();
			final float ny = direction.getStepY();
			final float nz = direction.getStepZ();
			addVertex(buffer, pose, corners[0], argb, u0, v0, lightCoords, nx, ny, nz);
			addVertex(buffer, pose, corners[1], argb, u0, v1, lightCoords, nx, ny, nz);
			addVertex(buffer, pose, corners[2], argb, u1, v1, lightCoords, nx, ny, nz);
			addVertex(buffer, pose, corners[3], argb, u1, v0, lightCoords, nx, ny, nz);
		}
	}

	private static void addVertex(
		final VertexConsumer buffer,
		final PoseStack.Pose pose,
		final float[] corner,
		final int color,
		final float u,
		final float v,
		final int lightCoords,
		final float nx,
		final float ny,
		final float nz
	) {
		buffer.addVertex(pose, corner[0], corner[1], corner[2])
			.setColor(color)
			.setUv(u, v)
			.setOverlay(OverlayTexture.NO_OVERLAY)
			.setLight(lightCoords)
			.setNormal(pose, nx, ny, nz);
	}

	private static float[][][] buildFaceCorners() {
		final float[][][] corners = new float[6][][];
		corners[Direction.DOWN.ordinal()] = new float[][] {{0, 0, 0}, {0, 0, 1}, {1, 0, 1}, {1, 0, 0}};
		corners[Direction.UP.ordinal()] = new float[][] {{0, 1, 1}, {0, 1, 0}, {1, 1, 0}, {1, 1, 1}};
		corners[Direction.NORTH.ordinal()] = new float[][] {{1, 1, 0}, {1, 0, 0}, {0, 0, 0}, {0, 1, 0}};
		corners[Direction.SOUTH.ordinal()] = new float[][] {{0, 1, 1}, {0, 0, 1}, {1, 0, 1}, {1, 1, 1}};
		corners[Direction.WEST.ordinal()] = new float[][] {{0, 1, 0}, {0, 0, 0}, {0, 0, 1}, {0, 1, 1}};
		corners[Direction.EAST.ordinal()] = new float[][] {{1, 1, 1}, {1, 0, 1}, {1, 0, 0}, {1, 1, 0}};
		return corners;
	}
}
