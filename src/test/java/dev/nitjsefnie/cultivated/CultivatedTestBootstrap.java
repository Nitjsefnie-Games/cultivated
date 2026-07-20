package dev.nitjsefnie.cultivated;

import dev.nitjsefnie.cultivated.plugin.CultivatedPlugins;
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
		// Seed the built-in type-dispatched value kinds + generators through the plugin path (F3 §F.3),
		// exactly as Cultivated#onInitialize does at runtime — so the codecs the tests exercise resolve
		// their built-in types. (No Fabric game launch here, so add-on entrypoints are simply empty.)
		CultivatedPlugins.loadCommon();
	}
}
