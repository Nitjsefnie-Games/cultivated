package dev.nitjsefnie.cultivated.plugin;

import com.mojang.serialization.MapCodec;
import dev.nitjsefnie.cultivated.Cultivated;
import dev.nitjsefnie.cultivated.command.generator.CropGenerator;
import dev.nitjsefnie.cultivated.command.generator.Generators;
import dev.nitjsefnie.cultivated.command.generator.SoilGenerator;
import dev.nitjsefnie.cultivated.condition.LoadCondition;
import dev.nitjsefnie.cultivated.data.display.Display;
import dev.nitjsefnie.cultivated.data.drop.DropProvider;
import dev.nitjsefnie.cultivated.data.growth.GrowthAmount;
import dev.nitjsefnie.cultivated.ingredient.CultivatedIngredient;
import java.util.List;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.entrypoint.EntrypointContainer;

/**
 * Task F3 §F.3 — the common-side plugin loader. Runs the {@link CultivatedCorePlugin} first (so the
 * built-in {@code type}s / generators are seeded through the plugin path), then every add-on plugin
 * declared under the Fabric custom entrypoint {@code cultivated:plugin}. Called once from
 * {@link Cultivated#onInitialize()} — and from the test bootstrap — BEFORE recipe/datapack load, so
 * every registered {@code type} is available when the data engine first parses JSON.
 */
public final class CultivatedPlugins {
	/** The Fabric custom entrypoint key add-ons register their {@link CultivatedPlugin} under. */
	public static final String ENTRYPOINT_KEY = "cultivated:plugin";

	private static boolean loaded;

	private CultivatedPlugins() {
	}

	/** Idempotent: seed the core built-ins, then load and register every add-on plugin. */
	public static synchronized void loadCommon() {
		if (loaded) {
			return;
		}
		loaded = true;

		final CultivatedPluginContext context = new ContextImpl();
		new CultivatedCorePlugin().register(context);

		for (final EntrypointContainer<CultivatedPlugin> container : entrypointContainers()) {
			final CultivatedPlugin plugin = container.getEntrypoint();
			try {
				plugin.register(context);
				Cultivated.LOGGER.info("Loaded Cultivated plugin {}", plugin.getClass().getName());
			} catch (final RuntimeException failure) {
				Cultivated.LOGGER.error("Cultivated plugin {} failed to register", plugin.getClass().getName(), failure);
			}
		}
	}

	private static List<EntrypointContainer<CultivatedPlugin>> entrypointContainers() {
		try {
			return FabricLoader.getInstance().getEntrypointContainers(ENTRYPOINT_KEY, CultivatedPlugin.class);
		} catch (final RuntimeException | LinkageError unavailable) {
			// A plain unit-test JVM has no Fabric game launch, so the loader cannot enumerate
			// entrypoints; the directly-invoked core plugin has already seeded the built-ins.
			return List.of();
		}
	}

	/** Routes each plugin registration to the matching kind's static {@code register} (I2). */
	private static final class ContextImpl implements CultivatedPluginContext {
		@Override
		public void registerDisplay(final String typeId, final MapCodec<? extends Display> codec) {
			Display.register(typeId, codec);
		}

		@Override
		public void registerDropProvider(final String typeId, final MapCodec<? extends DropProvider> codec) {
			DropProvider.register(typeId, codec);
		}

		@Override
		public void registerGrowthAmount(final String typeId, final MapCodec<? extends GrowthAmount> codec) {
			GrowthAmount.register(typeId, codec);
		}

		@Override
		public void registerIngredient(final String typeId, final MapCodec<? extends CultivatedIngredient> codec) {
			CultivatedIngredient.register(typeId, codec);
		}

		@Override
		public void registerLoadCondition(final String typeId, final MapCodec<? extends LoadCondition> codec) {
			LoadCondition.register(typeId, codec);
		}

		@Override
		public void registerCropGenerator(final CropGenerator generator) {
			Generators.registerCrop(generator);
		}

		@Override
		public void registerSoilGenerator(final SoilGenerator generator) {
			Generators.registerSoil(generator);
		}
	}
}
