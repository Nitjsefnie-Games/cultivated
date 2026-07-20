package dev.nitjsefnie.cultivated.client.render;

import dev.nitjsefnie.cultivated.data.display.RenderOptions;
import dev.nitjsefnie.cultivated.data.display.TintColor;
import java.util.Set;
import net.minecraft.core.Direction;
import org.jspecify.annotations.Nullable;

/**
 * Phase C §C.1/§C.3/§C.6 — the pure, side-effect-free maths behind the pot block-entity renderer,
 * factored out of the client render path so it is unit-testable without a live MC render pipeline
 * (rendering itself cannot be verified headlessly — see the Task C1 brief).
 *
 * <p>Covers: growth-progress clamping, the crop render scale ramp {@code 0.40 + 0.60*progress},
 * phased-display index selection {@code clamp(floor(progress*(count-1)), 0, count-1)}, per-display
 * height accumulation for bottom-up stacking, axis-rotation angle conversion, and tint→ARGB
 * resolution. No client/render classes are referenced so JUnit can exercise every branch.
 */
public final class PotRenderMath {
	/** Young crops render at 40 % of the display's own scale (§C.1). */
	public static final float CROP_MIN_SCALE = 0.40f;
	/** …ramping the remaining 60 % linearly to full size at maturity (§C.1). */
	public static final float CROP_SCALE_RANGE = 0.60f;
	/** Soil is drawn compressed to roughly this fraction of a block's height (§C.1). */
	public static final float SOIL_HEIGHT_SCALE = 0.6375f;
	/** Re-centring pivot for axis-aligned rotations: the block's own centre (§C.6). */
	public static final float ROTATION_PIVOT = 0.5f;

	private PotRenderMath() {
	}

	/**
	 * Crop render scale for a given progress (§C.1): {@code 0.40 + 0.60*progress}, so a fresh crop is
	 * 40 % and a mature one 100 % of the display's own scale. With the growth animation disabled
	 * (§C.7) crops always render at full size.
	 */
	public static float cropScale(final float progress, final boolean animate) {
		if (!animate) {
			return 1.0f;
		}
		return CROP_MIN_SCALE + CROP_SCALE_RANGE * clamp01(progress);
	}

	/**
	 * Phase index for a phased display (§C.3): {@code clamp(floor(progress*(count-1)), 0, count-1)}.
	 * A single-phase (or empty) list always resolves to index 0.
	 */
	public static int phaseIndex(final float progress, final int count) {
		if (count <= 1) {
			return 0;
		}
		final int index = (int)Math.floor(clamp01(progress) * (count - 1));
		if (index < 0) {
			return 0;
		}
		return Math.min(index, count - 1);
	}

	/**
	 * The vertical extent a display occupies once its own {@code scale} and the applied growth scale
	 * are combined (§C.6) — the amount the next stacked display must sit above this one.
	 */
	public static float displayHeight(final RenderOptions options, final float growthScale) {
		return options.scale().y() * growthScale;
	}

	/**
	 * Spin angle in degrees for an {@code entity} display (§C.5): {@code spin_speed * 360 * progress},
	 * so a full unit of {@code spin_speed} sweeps one revolution across the crop's growth. Progress is
	 * clamped to {@code [0,1]} (it is drawn only while growth is sustained).
	 */
	public static float spinDegrees(final float spinSpeed, final float progress) {
		return spinSpeed * 360.0f * clamp01(progress);
	}

	/**
	 * Whether a model face should be drawn under the option's {@code faces} set (§C.4/§C.6). A quad with
	 * no cull direction ({@code null} — general/interior geometry such as a crop's cross model) is always
	 * drawn; a direction-culled quad is drawn only when its direction is in the set.
	 */
	public static boolean shouldDrawFace(final Set<Direction> faces, final @Nullable Direction direction) {
		return direction == null || faces.contains(direction);
	}

	/**
	 * The ARGB tint applied to a display (§C.4/§C.6): the option's explicit {@link TintColor} if set,
	 * otherwise the supplied default (the caller passes the block's own world tint / white).
	 */
	public static int renderColor(final RenderOptions options, final int defaultArgb) {
		return options.color().map(TintColor::argb).orElse(defaultArgb);
	}

	private static float clamp01(final float value) {
		if (value < 0.0f) {
			return 0.0f;
		}
		return Math.min(value, 1.0f);
	}
}
