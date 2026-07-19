package dev.nitjsefnie.cultivated.util;

import java.util.function.Supplier;

/**
 * Phase G #3 — keeps independent client and server instances of a {@link ReloadableCache} so
 * that recipe/soil/crop caches never bleed across logical sides (a single-player client hosts
 * both a logical client and a logical server in one JVM).
 */
public final class SidedReloadableCache<T> {
	private final ReloadableCache<T> client;
	private final ReloadableCache<T> server;

	private SidedReloadableCache(final Supplier<T> clientFactory, final Supplier<T> serverFactory) {
		this.client = ReloadableCache.of(clientFactory);
		this.server = ReloadableCache.of(serverFactory);
	}

	public static <T> SidedReloadableCache<T> of(final Supplier<T> factory) {
		return new SidedReloadableCache<>(factory, factory);
	}

	public static <T> SidedReloadableCache<T> of(final Supplier<T> clientFactory, final Supplier<T> serverFactory) {
		return new SidedReloadableCache<>(clientFactory, serverFactory);
	}

	public ReloadableCache<T> forSide(final boolean clientSide) {
		return clientSide ? this.client : this.server;
	}

	public T get(final boolean clientSide) {
		return this.forSide(clientSide).get();
	}

	public void invalidate(final boolean clientSide) {
		this.forSide(clientSide).invalidate();
	}

	public void invalidateAll() {
		this.client.invalidate();
		this.server.invalidate();
	}
}
