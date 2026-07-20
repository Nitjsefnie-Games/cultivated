package dev.nitjsefnie.cultivated.util;

import java.util.function.Predicate;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;

/**
 * Phase G #13 — command permission-level predicates. MC 26.2 replaced the numeric
 * {@code hasPermission(int)} check with the {@code net.minecraft.server.permissions} model, so the
 * "owner" predicate wraps {@link Commands#hasPermission(net.minecraft.server.permissions.PermissionCheck)}
 * with {@link Commands#LEVEL_OWNERS}. Kept as a tiny reusable helper so command trees do not hard-code
 * the vanilla permission plumbing.
 */
public final class CommandPermissions {
	private CommandPermissions() {
	}

	/** Predicate accepting only sources at owner permission level (single-player host / server owner). */
	public static Predicate<CommandSourceStack> owner() {
		return Commands.hasPermission(Commands.LEVEL_OWNERS);
	}

	/** Predicate accepting sources at admin permission level or above. */
	public static Predicate<CommandSourceStack> admins() {
		return Commands.hasPermission(Commands.LEVEL_ADMINS);
	}
}
