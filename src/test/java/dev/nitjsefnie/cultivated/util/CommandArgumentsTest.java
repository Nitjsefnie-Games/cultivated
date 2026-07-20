package dev.nitjsefnie.cultivated.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

/**
 * Pure optional-argument-default getter (§G#13): {@link CommandArguments#orDefault} returns the
 * getter's value when present, substitutes the fallback on brigadier's "no such argument"
 * {@link IllegalArgumentException}, and lets any other failure propagate.
 */
class CommandArgumentsTest {
	@Test
	void returnsGetterValueWhenPresent() {
		assertEquals(true, CommandArguments.orDefault(() -> true, false));
		assertEquals(7, CommandArguments.orDefault(() -> 7, 0));
	}

	@Test
	void returnsFallbackWhenArgumentAbsent() {
		assertEquals(false, CommandArguments.orDefault(() -> {
			throw new IllegalArgumentException("No such argument 'generate' exists on this command");
		}, false));
		assertEquals(42, CommandArguments.orDefault(() -> {
			throw new IllegalArgumentException("absent");
		}, 42));
	}

	@Test
	void propagatesUnrelatedExceptions() {
		assertThrows(IllegalStateException.class, () ->
			CommandArguments.orDefault(() -> {
				throw new IllegalStateException("something else went wrong");
			}, "fallback"));
	}
}
