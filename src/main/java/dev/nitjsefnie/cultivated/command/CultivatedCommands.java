package dev.nitjsefnie.cultivated.command;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.Commands;

/**
 * Phase F.2 — registers the {@code /cultivated} command root and its owner-only {@code debug}
 * subtree via Fabric's {@link CommandRegistrationCallback}. Called once from
 * {@link dev.nitjsefnie.cultivated.Cultivated#onInitialize()}.
 */
public final class CultivatedCommands {
	private CultivatedCommands() {
	}

	/** Register the command-registration callback (idempotent per Fabric event contract). */
	public static void register() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
			dispatcher.register(Commands.literal("cultivated")
				.then(DebugCommands.build())));
	}
}
