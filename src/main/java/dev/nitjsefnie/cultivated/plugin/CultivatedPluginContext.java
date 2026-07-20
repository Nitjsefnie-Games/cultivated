package dev.nitjsefnie.cultivated.plugin;

import com.mojang.serialization.MapCodec;
import dev.nitjsefnie.cultivated.command.generator.CropGenerator;
import dev.nitjsefnie.cultivated.command.generator.SoilGenerator;
import dev.nitjsefnie.cultivated.condition.LoadCondition;
import dev.nitjsefnie.cultivated.data.display.Display;
import dev.nitjsefnie.cultivated.data.drop.DropProvider;
import dev.nitjsefnie.cultivated.data.growth.GrowthAmount;
import dev.nitjsefnie.cultivated.ingredient.CultivatedIngredient;

/**
 * Task F3 §F.3 — the registration surface handed to a {@link CultivatedPlugin} during common
 * initialisation. Add-ons (and the core itself, via {@link CultivatedCorePlugin}) register their
 * generators and their new {@code type}-dispatched value kinds here, in one place, without editing
 * core. The {@code typeId} passed to the codec registrations is the serialized {@code type} string
 * (a namespaced id in string form, e.g. {@code "yourmod:special"}).
 */
public interface CultivatedPluginContext {
	/** Register a display {@code type} (§C.2). */
	void registerDisplay(String typeId, MapCodec<? extends Display> codec);

	/** Register a drop-provider {@code type} (§A.6). */
	void registerDropProvider(String typeId, MapCodec<? extends DropProvider> codec);

	/** Register a growth-amount {@code type} (§A.10). */
	void registerGrowthAmount(String typeId, MapCodec<? extends GrowthAmount> codec);

	/** Register a typed custom-ingredient {@code type} (§A.9). */
	void registerIngredient(String typeId, MapCodec<? extends CultivatedIngredient> codec);

	/** Register a load-condition {@code type} (§A.9). */
	void registerLoadCondition(String typeId, MapCodec<? extends LoadCondition> codec);

	/** Register a crop generator ahead of the built-in fallback (§F.2). */
	void registerCropGenerator(CropGenerator generator);

	/** Register a soil generator ahead of the built-in fallback (§F.2). */
	void registerSoilGenerator(SoilGenerator generator);
}
