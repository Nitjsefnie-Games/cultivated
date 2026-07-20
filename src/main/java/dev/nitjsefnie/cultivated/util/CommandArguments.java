package dev.nitjsefnie.cultivated.util;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.context.CommandContext;
import java.util.function.Supplier;

/**
 * Phase G #13 — argument getters that return a default when an optional argument node was not
 * supplied. Brigadier throws {@link IllegalArgumentException} from {@code CommandContext.getArgument}
 * when the named argument is absent (the executor was reached through a shorter branch of the tree);
 * {@link #orDefault} turns that into a fallback so a single executor can back several optional-tail
 * command overloads.
 */
public final class CommandArguments {
	private CommandArguments() {
	}

	/**
	 * Run {@code getter}, returning {@code fallback} if the argument it reads was not present on this
	 * invocation. Only the "no such argument" {@link IllegalArgumentException} is swallowed; any other
	 * failure propagates. This is the pure, world-free core of every {@code get*Or} convenience below.
	 */
	public static <T> T orDefault(final Supplier<T> getter, final T fallback) {
		try {
			return getter.get();
		} catch (final IllegalArgumentException absent) {
			return fallback;
		}
	}

	/** Boolean argument {@code name}, or {@code fallback} when the optional argument was omitted. */
	public static boolean getBoolOr(final CommandContext<?> context, final String name, final boolean fallback) {
		return orDefault(() -> BoolArgumentType.getBool(context, name), fallback);
	}
}
