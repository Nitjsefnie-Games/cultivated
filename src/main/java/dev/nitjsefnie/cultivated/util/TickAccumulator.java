package dev.nitjsefnie.cultivated.util;

/**
 * Phase G #4 — tracks elapsed ticks as a float that advances/retreats proportionally to the
 * world's current tick rate, so that growth and pot cooldowns behave stably under {@code /tick
 * rate}. Callers pass the level's current tick rate (e.g. {@code level.tickRateManager()
 * .tickrate()}); the accumulator normalises against the vanilla 20 t/s baseline so that one
 * real second always advances the accumulator by ~20 regardless of the configured rate.
 */
public final class TickAccumulator {
	public static final float NORMAL_TICK_RATE = 20.0f;

	private float value;

	public TickAccumulator() {
		this(0.0f);
	}

	public TickAccumulator(final float initial) {
		this.value = initial;
	}

	/** Amount added per game-tick for a given world tick rate. */
	public static float step(final float currentTickRate) {
		final float rate = currentTickRate <= 0.0f ? NORMAL_TICK_RATE : currentTickRate;
		return NORMAL_TICK_RATE / rate;
	}

	public void tickUp(final float currentTickRate) {
		this.value += step(currentTickRate);
	}

	public void tickDown(final float currentTickRate) {
		this.value -= step(currentTickRate);
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
