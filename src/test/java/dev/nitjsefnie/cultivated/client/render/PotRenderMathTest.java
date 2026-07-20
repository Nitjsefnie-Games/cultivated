package dev.nitjsefnie.cultivated.client.render;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.nitjsefnie.cultivated.data.display.RenderOptions;
import dev.nitjsefnie.cultivated.data.display.RenderOptions.AxisRotation;
import dev.nitjsefnie.cultivated.data.display.RenderOptions.Vec3f;
import dev.nitjsefnie.cultivated.data.display.TintColor;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * Pure render maths behind the pot block-entity renderer (§C.1/§C.3/§C.6). Rendering itself cannot
 * be verified headlessly (Task C1 brief), so this pins the extractable arithmetic: growth-progress
 * clamping, the crop scale ramp, phased-display index selection, height accumulation, axis-rotation
 * angle conversion and tint→ARGB resolution.
 */
class PotRenderMathTest {

	private static final float EPS = 1.0e-6f;

	// ---- growth progress = clamp(growthTime / requiredTicks, 0..1) ----

	@Test
	void growthProgressIsRatioClampedToUnitInterval() {
		assertEquals(0.0f, PotRenderMath.growthProgress(0.0f, 100), EPS);
		assertEquals(0.5f, PotRenderMath.growthProgress(50.0f, 100), EPS);
		assertEquals(1.0f, PotRenderMath.growthProgress(100.0f, 100), EPS);
	}

	@Test
	void growthProgressClampsOvershootAndUndershoot() {
		assertEquals(1.0f, PotRenderMath.growthProgress(250.0f, 100), EPS);
		assertEquals(0.0f, PotRenderMath.growthProgress(-10.0f, 100), EPS);
	}

	@Test
	void growthProgressIsZeroWhenNoRequiredTicks() {
		assertEquals(0.0f, PotRenderMath.growthProgress(50.0f, 0), EPS);
		assertEquals(0.0f, PotRenderMath.growthProgress(50.0f, -5), EPS);
	}

	// ---- crop scale = 0.40 + 0.60 * progress (or full when animation off) ----

	@Test
	void cropScaleRampsFromFortyToHundredPercent() {
		assertEquals(0.40f, PotRenderMath.cropScale(0.0f, true), EPS);
		assertEquals(0.70f, PotRenderMath.cropScale(0.5f, true), EPS);
		assertEquals(1.00f, PotRenderMath.cropScale(1.0f, true), EPS);
	}

	@Test
	void cropScaleClampsProgressBeforeRamping() {
		assertEquals(0.40f, PotRenderMath.cropScale(-1.0f, true), EPS);
		assertEquals(1.00f, PotRenderMath.cropScale(2.0f, true), EPS);
	}

	@Test
	void cropScaleIsFullWhenAnimationDisabled() {
		assertEquals(1.0f, PotRenderMath.cropScale(0.0f, false), EPS);
		assertEquals(1.0f, PotRenderMath.cropScale(0.5f, false), EPS);
	}

	// ---- phase index = clamp(floor(progress * (count-1)), 0, count-1) ----

	@Test
	void phaseIndexSelectsAcrossTheRange() {
		// count = 4 → boundaries at 1/3, 2/3, 1.
		assertEquals(0, PotRenderMath.phaseIndex(0.0f, 4));
		assertEquals(0, PotRenderMath.phaseIndex(0.30f, 4));
		assertEquals(1, PotRenderMath.phaseIndex(0.34f, 4));
		assertEquals(2, PotRenderMath.phaseIndex(0.67f, 4));
		assertEquals(3, PotRenderMath.phaseIndex(1.0f, 4));
	}

	@Test
	void phaseIndexClampsAndHandlesDegenerateCounts() {
		assertEquals(3, PotRenderMath.phaseIndex(2.0f, 4));
		assertEquals(0, PotRenderMath.phaseIndex(-1.0f, 4));
		assertEquals(0, PotRenderMath.phaseIndex(0.9f, 1));
		assertEquals(0, PotRenderMath.phaseIndex(0.9f, 0));
	}

	// ---- height accumulation for bottom-up stacking ----

	@Test
	void displayHeightIsVerticalScaleTimesGrowthScale() {
		final RenderOptions options = optionsWithScale(0.625f);
		assertEquals(0.625f, PotRenderMath.displayHeight(options, 1.0f), EPS);
		assertEquals(0.3125f, PotRenderMath.displayHeight(options, 0.5f), EPS);
	}

	@Test
	void nextStackBaseAccumulatesHeights() {
		final RenderOptions a = optionsWithScale(0.5f);
		final RenderOptions b = optionsWithScale(0.25f);
		float base = 0.0f;
		base = PotRenderMath.nextStackBase(base, a, 1.0f);
		assertEquals(0.5f, base, EPS);
		base = PotRenderMath.nextStackBase(base, b, 1.0f);
		assertEquals(0.75f, base, EPS);
	}

	// ---- axis-rotation angle conversion ----

	@Test
	void rotationRadiansConvertsAxisRotationDegrees() {
		assertEquals(0.0f, PotRenderMath.rotationRadians(AxisRotation.Y_0), EPS);
		assertEquals((float)(Math.PI / 2.0), PotRenderMath.rotationRadians(AxisRotation.X_90), EPS);
		assertEquals((float)Math.PI, PotRenderMath.rotationRadians(AxisRotation.Z_180), EPS);
		assertEquals((float)(3.0 * Math.PI / 2.0), PotRenderMath.rotationRadians(AxisRotation.Y_270), EPS);
	}

	// ---- tint → ARGB ----

	@Test
	void renderColorUsesExplicitTintWhenPresent() {
		final int red = TintColor.fromComponents(255, 255, 0, 0).argb();
		final RenderOptions tinted = optionsWithColor(Optional.of(new TintColor(red)));
		assertEquals(red, PotRenderMath.renderColor(tinted, 0xFFFFFFFF));
	}

	@Test
	void renderColorFallsBackToDefaultWhenNoTint() {
		final RenderOptions untinted = optionsWithColor(Optional.empty());
		assertEquals(0xFFFFFFFF, PotRenderMath.renderColor(untinted, 0xFFFFFFFF));
	}

	@Test
	void tintColorPacksAndUnpacksArgbComponents() {
		final TintColor color = TintColor.fromComponents(0x80, 0x12, 0x34, 0x56);
		assertEquals(0x80, color.alpha());
		assertEquals(0x12, color.red());
		assertEquals(0x34, color.green());
		assertEquals(0x56, color.blue());
		assertEquals(0x80123456, color.argb());
	}

	private static RenderOptions optionsWithScale(final float y) {
		return new RenderOptions(
			new Vec3f(0.625f, y, 0.625f), RenderOptions.ZERO, List.of(),
			false, Optional.empty(), RenderOptions.ALL_FACES
		);
	}

	private static RenderOptions optionsWithColor(final Optional<TintColor> color) {
		return new RenderOptions(
			RenderOptions.DEFAULT_SCALE, RenderOptions.ZERO, List.of(),
			false, color, RenderOptions.ALL_FACES
		);
	}
}
