package dev.nitjsefnie.cultivated.recipe;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.nitjsefnie.cultivated.condition.LoadCondition;
import dev.nitjsefnie.cultivated.data.display.Display;
import dev.nitjsefnie.cultivated.ingredient.CultivatedIngredient;
import dev.nitjsefnie.cultivated.registry.ModRecipes;
import java.util.List;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;

/**
 * Phase A §A.3 — a soil recipe ({@code cultivated:soil} / {@code cultivated:block_derived_soil}).
 * Marks an item as a valid soil and supplies growth/yield/light modifiers plus the rendered soil
 * display. A {@code block_derived_soil} JSON is a decode-time convenience that derives its fields
 * from a block; once resolved it is a plain soil (and re-serialises as {@code cultivated:soil}).
 */
public record SoilRecipe(
	CultivatedIngredient input,
	Display soilDisplay,
	float growthModifier,
	int lightLevel,
	float yieldModifier,
	List<LoadCondition> conditions
) implements BotanyRecipe {
	public static final MapCodec<SoilRecipe> EXPLICIT_CODEC = RecordCodecBuilder.mapCodec(
		i -> i.group(
				CultivatedIngredient.CODEC.fieldOf("input").forGetter(SoilRecipe::input),
				Display.CODEC.fieldOf("display").forGetter(SoilRecipe::soilDisplay),
				com.mojang.serialization.Codec.FLOAT.optionalFieldOf("growth_modifier", 0.0f).forGetter(SoilRecipe::growthModifier),
				com.mojang.serialization.Codec.INT.optionalFieldOf("light_level", 0).forGetter(SoilRecipe::lightLevel),
				com.mojang.serialization.Codec.floatRange(0.0f, Float.MAX_VALUE).optionalFieldOf("yield_modifier", 0.0f).forGetter(SoilRecipe::yieldModifier),
				LoadCondition.LIST_CODEC.optionalFieldOf("cultivated:load_conditions", List.of()).forGetter(SoilRecipe::conditions)
			)
			.apply(i, SoilRecipe::new)
	);

	@Override
	public CultivatedIngredient primaryIngredient() {
		return this.input;
	}

	@Override
	public boolean matches(final PotContext input, final Level level) {
		return this.input.test(input.getSoilStack());
	}

	@Override
	public RecipeSerializer<SoilRecipe> getSerializer() {
		return ModRecipes.SOIL_SERIALIZER;
	}

	@Override
	public RecipeType<SoilRecipe> getType() {
		return ModRecipes.SOIL_TYPE;
	}
}
