package dev.nitjsefnie.cultivated.recipe;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.nitjsefnie.cultivated.condition.LoadCondition;
import dev.nitjsefnie.cultivated.data.display.Display;
import dev.nitjsefnie.cultivated.data.drop.DropProvider;
import dev.nitjsefnie.cultivated.ingredient.CultivatedIngredient;
import dev.nitjsefnie.cultivated.registry.ModRecipes;
import dev.nitjsefnie.cultivated.registry.ModTags;
import java.util.List;
import java.util.Optional;
import net.minecraft.advancements.predicates.BlockPredicate;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;

/**
 * Phase A §A.4 — a crop recipe ({@code cultivated:crop} / {@code cultivated:block_derived_crop}).
 * Marks an item as a seed and defines grow time, accepted soils, drops, display, light and yield.
 * An empty {@code soil} means the default tag {@code cultivated:soil/dirt}.
 */
public record CropRecipe(
	CultivatedIngredient input,
	Optional<CultivatedIngredient> soil,
	int growTime,
	List<Display> displays,
	int lightLevel,
	List<DropProvider> drops,
	Optional<Identifier> function,
	Optional<BlockPredicate> potPredicate,
	float yield,
	float yieldScale,
	List<LoadCondition> conditions
) implements BotanyRecipe {
	public static final MapCodec<CropRecipe> EXPLICIT_CODEC = RecordCodecBuilder.mapCodec(
		i -> i.group(
				CultivatedIngredient.CODEC.fieldOf("input").forGetter(CropRecipe::input),
				CultivatedIngredient.CODEC.optionalFieldOf("soil").forGetter(CropRecipe::soil),
				Codec.intRange(1, Integer.MAX_VALUE).optionalFieldOf("grow_time", 1200).forGetter(CropRecipe::growTime),
				Display.LIST_CODEC.fieldOf("display").forGetter(CropRecipe::displays),
				Codec.intRange(0, 15).optionalFieldOf("light_level", 0).forGetter(CropRecipe::lightLevel),
				DropProvider.LIST_CODEC.optionalFieldOf("drops", List.of()).forGetter(CropRecipe::drops),
				Identifier.CODEC.optionalFieldOf("function").forGetter(CropRecipe::function),
				BlockPredicate.CODEC.optionalFieldOf("pot_predicate").forGetter(CropRecipe::potPredicate),
				Codec.floatRange(0.0f, Float.MAX_VALUE).optionalFieldOf("yield", 1.0f).forGetter(CropRecipe::yield),
				Codec.floatRange(0.0f, Float.MAX_VALUE).optionalFieldOf("yield_scale", 1.0f).forGetter(CropRecipe::yieldScale),
				LoadCondition.LIST_CODEC.optionalFieldOf("cultivated:load_conditions", List.of()).forGetter(CropRecipe::conditions)
			)
			.apply(i, CropRecipe::new)
	);

	@Override
	public CultivatedIngredient primaryIngredient() {
		return this.input;
	}

	@Override
	public boolean matches(final PotContext input, final Level level) {
		return this.input.test(input.getSeedStack());
	}

	/** True if {@code soilStack} is accepted by this crop's soil test (default: tag soil/dirt). */
	public boolean acceptsSoil(final ItemStack soilStack) {
		return this.soil.map(ingredient -> ingredient.test(soilStack)).orElseGet(() -> soilStack.is(ModTags.SOIL_DIRT));
	}

	@Override
	public RecipeSerializer<CropRecipe> getSerializer() {
		return ModRecipes.CROP_SERIALIZER;
	}

	@Override
	public RecipeType<CropRecipe> getType() {
		return ModRecipes.CROP_TYPE;
	}
}
