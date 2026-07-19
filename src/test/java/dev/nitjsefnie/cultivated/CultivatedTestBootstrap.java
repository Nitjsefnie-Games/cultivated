package dev.nitjsefnie.cultivated;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;

/** Boots the vanilla registries so tests can evaluate load conditions and derive from blocks. */
public final class CultivatedTestBootstrap {
	private CultivatedTestBootstrap() {
	}

	/** Idempotent — safe to call from every test class's {@code @BeforeAll}. */
	public static void bootstrap() {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
	}
}
