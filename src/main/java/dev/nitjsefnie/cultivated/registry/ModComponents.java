package dev.nitjsefnie.cultivated.registry;

import dev.nitjsefnie.cultivated.Cultivated;
import dev.nitjsefnie.cultivated.recipe.CropRecipe;
import dev.nitjsefnie.cultivated.recipe.SoilRecipe;
import dev.nitjsefnie.cultivated.util.CodecHelper;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;

/**
 * Phase A §A.11 — item-override data components. A stack carrying one of these overrides its
 * recipe-based behavior with an embedded crop/soil definition (serialised as an explicit recipe
 * of the matching kind, both persistently and over the network). Consumed by the pot in Phase B.
 */
public final class ModComponents {
	public static final DataComponentType<CropRecipe> CROP_OVERRIDE = DataComponentType.<CropRecipe>builder()
		.persistent(CropRecipe.EXPLICIT_CODEC.codec())
		.networkSynchronized(CodecHelper.streamOf(CropRecipe.EXPLICIT_CODEC.codec()))
		.build();

	public static final DataComponentType<SoilRecipe> SOIL_OVERRIDE = DataComponentType.<SoilRecipe>builder()
		.persistent(SoilRecipe.EXPLICIT_CODEC.codec())
		.networkSynchronized(CodecHelper.streamOf(SoilRecipe.EXPLICIT_CODEC.codec()))
		.build();

	private ModComponents() {
	}

	public static void register() {
		Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, Cultivated.id("crop"), CROP_OVERRIDE);
		Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, Cultivated.id("soil"), SOIL_OVERRIDE);
	}
}
