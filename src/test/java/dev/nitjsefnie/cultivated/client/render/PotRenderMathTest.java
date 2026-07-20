package dev.nitjsefnie.cultivated.client.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.nitjsefnie.cultivated.data.display.RenderOptions;
import dev.nitjsefnie.cultivated.data.display.RenderOptions.Vec3f;
import dev.nitjsefnie.cultivated.data.display.TintColor;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;

/**
 * Pure render maths behind the pot block-entity renderer (§C.1/§C.3/§C.6). Rendering itself cannot
 * be verified headlessly (Task C1 brief), so this pins the extractable arithmetic: growth-progress
 * clamping, the crop scale ramp, phased-display index selection, height accumulation, axis-rotation
 * angle conversion and tint→ARGB resolution.
 */
class PotRenderMathTest {

	private static final float EPS = 1.0e-6f;

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

	// ---- crop render decision: waxed always renders, others require accepted soil (§B.1) ----

	@Test
	void waxedPotRendersCropRegardlessOfSoil() {
		assertTrue(PotRenderMath.shouldRenderCrop(true, true));
		assertTrue(PotRenderMath.shouldRenderCrop(true, false));
	}

	@Test
	void basicPotRendersCropOnlyWhenSoilAccepted() {
		assertTrue(PotRenderMath.shouldRenderCrop(false, true));
		assertFalse(PotRenderMath.shouldRenderCrop(false, false));
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

	// ---- entity spin angle = spin_speed * 360 * progress (progress clamped) ----

	@Test
	void spinDegreesSweepsRevolutionsAcrossProgress() {
		assertEquals(0.0f, PotRenderMath.spinDegrees(1.0f, 0.0f), EPS);
		assertEquals(180.0f, PotRenderMath.spinDegrees(1.0f, 0.5f), EPS);
		assertEquals(360.0f, PotRenderMath.spinDegrees(1.0f, 1.0f), EPS);
		assertEquals(720.0f, PotRenderMath.spinDegrees(2.0f, 1.0f), EPS);
	}

	@Test
	void spinDegreesIsZeroWithoutSpinSpeedAndClampsProgress() {
		assertEquals(0.0f, PotRenderMath.spinDegrees(0.0f, 0.7f), EPS);
		assertEquals(360.0f, PotRenderMath.spinDegrees(1.0f, 2.0f), EPS);
		assertEquals(0.0f, PotRenderMath.spinDegrees(1.0f, -1.0f), EPS);
	}

	// ---- face-filter decision ----

	@Test
	void shouldDrawFaceAlwaysDrawsNullDirectionGeometry() {
		assertTrue(PotRenderMath.shouldDrawFace(Set.of(Direction.UP), null));
		assertTrue(PotRenderMath.shouldDrawFace(Set.of(), null));
	}

	@Test
	void shouldDrawFaceHonorsTheFaceSet() {
		final Set<Direction> onlyUp = Set.of(Direction.UP);
		assertTrue(PotRenderMath.shouldDrawFace(onlyUp, Direction.UP));
		assertFalse(PotRenderMath.shouldDrawFace(onlyUp, Direction.DOWN));
		assertFalse(PotRenderMath.shouldDrawFace(onlyUp, Direction.NORTH));
	}

	@Test
	void shouldDrawFaceDrawsAllListedFaces() {
		final Set<Direction> all = Set.of(Direction.values());
		for (final Direction direction : Direction.values()) {
			assertTrue(PotRenderMath.shouldDrawFace(all, direction));
		}
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
