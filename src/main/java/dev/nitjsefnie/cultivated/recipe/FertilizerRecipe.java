package dev.nitjsefnie.cultivated.recipe;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.nitjsefnie.cultivated.condition.LoadCondition;
import dev.nitjsefnie.cultivated.data.growth.GrowthAmount;
import dev.nitjsefnie.cultivated.ingredient.CultivatedIngredient;
import dev.nitjsefnie.cultivated.registry.ModRecipes;
import dev.nitjsefnie.cultivated.util.SoundEffect;
import java.util.List;
import java.util.Optional;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;

/**
 * Phase A §A.5 — a fertilizer recipe ({@code cultivated:fertilizer}): a held item that instantly
 * advances growth when right-clicked on a pot (subject to the pot's bone-meal cooldown).
 */
public record FertilizerRecipe(
	CultivatedIngredient heldItem,
	Optional<CultivatedIngredient> soilItem,
	Optional<CultivatedIngredient> seedItem,
	GrowthAmount growth,
	int cooldown,
	boolean spawnParticles,
	boolean notifySculk,
	Optional<SoundEffect> soundEffect,
	List<LoadCondition> conditions
) implements BotanyRecipe {
	public static final MapCodec<FertilizerRecipe> CODEC = RecordCodecBuilder.mapCodec(
		i -> i.group(
				CultivatedIngredient.CODEC.fieldOf("held_item").forGetter(FertilizerRecipe::heldItem),
				CultivatedIngredient.CODEC.optionalFieldOf("soil_item").forGetter(FertilizerRecipe::soilItem),
				CultivatedIngredient.CODEC.optionalFieldOf("seed_item").forGetter(FertilizerRecipe::seedItem),
				GrowthAmount.CODEC.fieldOf("growth").forGetter(FertilizerRecipe::growth),
				Codec.INT.optionalFieldOf("cooldown", 20).forGetter(FertilizerRecipe::cooldown),
				Codec.BOOL.optionalFieldOf("spawn_particles", true).forGetter(FertilizerRecipe::spawnParticles),
				Codec.BOOL.optionalFieldOf("notify_sculk", true).forGetter(FertilizerRecipe::notifySculk),
				SoundEffect.CODEC.optionalFieldOf("sound_effect").forGetter(FertilizerRecipe::soundEffect),
				LoadCondition.LIST_CODEC.optionalFieldOf("cultivated:load_conditions", List.of()).forGetter(FertilizerRecipe::conditions)
			)
			.apply(i, FertilizerRecipe::new)
	);

	@Override
	public CultivatedIngredient primaryIngredient() {
		return this.heldItem;
	}

	@Override
	public boolean matches(final PotContext input, final Level level) {
		return this.heldItem.test(input.getHeldItem())
			&& this.soilItem.map(ing -> ing.test(input.getSoilStack())).orElse(true)
			&& this.seedItem.map(ing -> ing.test(input.getSeedStack())).orElse(true);
	}

	@Override
	public RecipeSerializer<FertilizerRecipe> getSerializer() {
		return ModRecipes.FERTILIZER_SERIALIZER;
	}

	@Override
	public RecipeType<FertilizerRecipe> getType() {
		return ModRecipes.FERTILIZER_TYPE;
	}
}
