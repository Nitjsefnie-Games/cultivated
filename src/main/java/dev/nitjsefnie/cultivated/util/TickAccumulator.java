package dev.nitjsefnie.cultivated.util;

/**
 * Tracks elapsed game ticks as a float, advancing/retreating by a FIXED amount ({@link #TICK_STEP})
 * per game tick, INDEPENDENT of the world's current tick rate. Botany-pot growth and every pot
 * cooldown are driven by this accumulator, so more game ticks per second (a higher {@code /tick
 * rate}) grows crops faster and fewer grows them slower — exactly like a vanilla crop. {@code /tick
 * freeze} pauses growth for free, because a frozen world does not tick block entities at all.
 *
 * <p>User decision 2026-07-20: growth tracks game ticks and {@code /tick rate} affects growth speed
 * — this supersedes the original real-time-stable design (see §G #4), where the step was scaled by
 * {@code NORMAL_TICK_RATE / currentRate} to hold wall-clock growth constant across tick rates.
 */
public final class TickAccumulator {
	/** Vanilla baseline tick rate (20 t/s); the reference for converting tick counts to seconds. */
	public static final float NORMAL_TICK_RATE = 20.0f;

	/** Fixed progress added/removed per game tick — one tick of progress per game tick. */
	public static final float TICK_STEP = 1.0f;

	private float value;

	public TickAccumulator() {
		this(0.0f);
	}

	public TickAccumulator(final float initial) {
		this.value = initial;
	}

	/** Advance by one game tick's worth of progress ({@link #TICK_STEP}). */
	public void tickUp() {
		this.value += TICK_STEP;
	}

	/** Retreat by one game tick's worth of progress ({@link #TICK_STEP}). */
	public void tickDown() {
		this.value -= TICK_STEP;
	}

	public float get() {
		return this.value;
	}

	public void set(final float value) {
		this.value = value;
	}

	public void add(final float amount) {
		this.value += amount;
	}

	public void reset() {
		this.value = 0.0f;
	}
}
