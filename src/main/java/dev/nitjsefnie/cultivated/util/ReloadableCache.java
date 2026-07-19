package dev.nitjsefnie.cultivated.util;

import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Phase G #2 — a value derived from some source that is rebuilt when the world/recipes reload
 * and may be explicitly invalidated. Rebuild is lazy: the first {@link #get()} after an
 * invalidation recomputes via the supplied factory.
 */
public final class ReloadableCache<T> {
	private final Supplier<T> factory;
	private boolean valid;
	private T value;

	private ReloadableCache(final Supplier<T> factory) {
		this.factory = factory;
	}

	public static <T> ReloadableCache<T> of(final Supplier<T> factory) {
		return new ReloadableCache<>(factory);
	}

	public T get() {
		if (!this.valid) {
			this.value = this.factory.get();
			this.valid = true;
		}
		return this.value;
	}

	public void invalidate() {
		this.valid = false;
		this.value = null;
	}

	public boolean isValid() {
		return this.valid;
	}

	public Optional<T> asOptional() {
		return Optional.ofNullable(this.get());
	}

	public void ifPresent(final Consumer<T> consumer) {
		final T current = this.get();
		if (current != null) {
			consumer.accept(current);
		}
	}

	public <O> Optional<O> map(final Function<T, O> mapper) {
		final T current = this.get();
		return current == null ? Optional.empty() : Optional.ofNullable(mapper.apply(current));
	}
}
