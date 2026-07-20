package dev.nitjsefnie.cultivated.command;

import java.util.ArrayList;
import java.util.List;

/**
 * Pure layout math for {@code /cultivated debug perftest}: arrange {@code count} pot units into a
 * roughly-cubic field. Each unit is two blocks tall (a hopper pot with a chest directly below), so
 * the vertical extent is bounded — the caller passes {@code maxLayers} derived from the world's build
 * height and this widens the horizontal footprint to keep every requested unit placeable.
 *
 * <p>World-free and deterministic so the count → grid mapping is unit-testable without a live level.
 */
public final class PerfTestGrid {
	/** Default unit count when the command is run with no argument. */
	public static final int DEFAULT_COUNT = 256;
	/** Hard cap on the requested unit count (safety valve against accidental huge fields). */
	public static final int MAX_COUNT = 4096;

	private PerfTestGrid() {
	}

	/** Clamp a requested count into {@code [1, MAX_COUNT]}. */
	public static int clampCount(final int requested) {
		if (requested < 1) {
			return 1;
		}
		return Math.min(requested, MAX_COUNT);
	}

	/**
	 * A cubic-ish arrangement of {@code count} units: {@code nx} wide (X) × {@code nz} deep (Z) ×
	 * {@code ny} tall (vertical layers, each layer two blocks). The footprint {@code nx*nz*ny} is at
	 * least {@code count}; the final (top) layer may be partly filled.
	 */
	public record Layout(int nx, int nz, int ny, int count) {
		/**
		 * Row-major {@code {dx, layer, dz}} offsets for each of the {@code count} units, filled one
		 * horizontal layer at a time from the bottom up. {@code dx ∈ [0,nx)}, {@code dz ∈ [0,nz)},
		 * {@code layer ∈ [0,ny)}.
		 */
		public List<int[]> offsets() {
			final int perLayer = this.nx * this.nz;
			final List<int[]> out = new ArrayList<>(this.count);
			for (int i = 0; i < this.count; i++) {
				final int layer = i / perLayer;
				final int rem = i % perLayer;
				out.add(new int[] {rem % this.nx, layer, rem / this.nx});
			}
			return out;
		}
	}

	/**
	 * Lay {@code count} units out as close to a cube as the vertical cap allows. The vertical layer
	 * count is {@code min(ceil(cbrt(count)), maxLayers)}; the horizontal footprint then widens to hold
	 * {@code ceil(count/ny)} units per layer, so all {@code count} units fit regardless of the cap
	 * (the world imposes no horizontal bound). {@code count} is clamped to {@code [1, MAX_COUNT]} and
	 * {@code maxLayers} to at least 1.
	 */
	public static Layout layout(final int count, final int maxLayers) {
		final int c = clampCount(count);
		final int m = Math.max(1, maxLayers);
		final int cubeSide = (int) Math.ceil(Math.cbrt(c));
		final int ny = Math.max(1, Math.min(cubeSide, m));
		final int perLayer = (int) Math.ceil((double) c / ny);
		final int nx = Math.max(1, (int) Math.ceil(Math.sqrt(perLayer)));
		final int nz = Math.max(1, (int) Math.ceil((double) perLayer / nx));
		return new Layout(nx, nz, ny, c);
	}
}
