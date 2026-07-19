package dev.nitjsefnie.cultivated.util;

import java.util.function.Function;
import java.util.function.Supplier;
import net.minecraft.core.Registry;
import net.minecraft.resources.Identifier;

/**
 * Phase G #1 — a supplier that resolves its value once and memoizes it. Used pervasively for
 * lazy registry-handle lookups (blocks, items, block-entity types, etc.) so that referencing a
 * registry object does not force it to resolve before the registry is populated.
 */
public final class CachedSupplier<T> implements Supplier<T> {
	private final Supplier<T> source;
	private boolean resolved;
	private T value;

	private CachedSupplier(final Supplier<T> source) {
		this.source = source;
	}

	public static <T> CachedSupplier<T> of(final Supplier<T> source) {
		return new CachedSupplier<>(source);
	}

	/** Lazily look up a registry entry by id. */
	public static <T> CachedSupplier<T> ofRegistry(final Registry<T> registry, final Identifier id) {
		return of(() -> registry.getValue(id));
	}

	@Override
	public T get() {
		if (!this.resolved) {
			this.value = this.source.get();
			this.resolved = true;
		}
		return this.value;
	}

	public <O> CachedSupplier<O> map(final Function<T, O> mapper) {
		return of(() -> mapper.apply(this.get()));
	}

	public void invalidate() {
		this.resolved = false;
		this.value = null;
	}
}
