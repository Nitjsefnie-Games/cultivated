package dev.nitjsefnie.cultivated.util;

import java.util.Optional;
import java.util.function.Predicate;

/** Phase G #16 — null-safe "test an optional predicate" utility. */
public final class FunctionHelper {
	private FunctionHelper() {
	}

	/** If the predicate is absent, treat as satisfied ({@code true}); else evaluate it. */
	public static <T> boolean testOptional(final Optional<? extends Predicate<T>> predicate, final T value) {
		return predicate.map(p -> p.test(value)).orElse(true);
	}

	public static <T> boolean testOptional(final Predicate<T> predicate, final T value) {
		return predicate == null || predicate.test(value);
	}
}
