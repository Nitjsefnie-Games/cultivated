package dev.nitjsefnie.cultivated.recipe;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.nitjsefnie.cultivated.condition.LoadCondition;
import dev.nitjsefnie.cultivated.ingredient.CultivatedIngredient;
import dev.nitjsefnie.cultivated.registry.ModRecipes;
import dev.nitjsefnie.cultivated.util.LazyItemStack;
import dev.nitjsefnie.cultivated.util.SoundEffect;
import java.util.List;
import java.util.Optional;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;

/**
 * Phase A §A.1 / §B.6 — a pot-interaction recipe ({@code cultivated:pot_interaction}): a held
 * item that transforms the pot's soil/seed in place. The apply-in-world behavior is Phase B; this
 * carries the parsed data, matching, and codecs.
 */
public record PotInteractionRecipe(
	CultivatedIngredient heldItem,
	boolean damageHeld,
	boolean consumeHeld,
	Optional<CultivatedIngredient> soilItem,
	Optional<CultivatedIngredient> seedItem,
	Optional<LazyItemStack> newSoil,
	Optional<LazyItemStack> newSeed,
	Optional<Identifier> extraDrops,
	Optional<SoundEffect> soundEffect,
	boolean notifySculk,
	List<LoadCondition> conditions
) implements BotanyRecipe {
	public static final MapCodec<PotInteractionRecipe> CODEC = RecordCodecBuilder.mapCodec(
		i -> i.group(
				CultivatedIngredient.CODEC.fieldOf("held_item").forGetter(PotInteractionRecipe::heldItem),
				Codec.BOOL.optionalFieldOf("damage_held", true).forGetter(PotInteractionRecipe::damageHeld),
				Codec.BOOL.optionalFieldOf("consume_held", false).forGetter(PotInteractionRecipe::consumeHeld),
				CultivatedIngredient.CODEC.optionalFieldOf("soil_item").forGetter(PotInteractionRecipe::soilItem),
				CultivatedIngredient.CODEC.optionalFieldOf("seed_item").forGetter(PotInteractionRecipe::seedItem),
				LazyItemStack.CODEC.optionalFieldOf("new_soil").forGetter(PotInteractionRecipe::newSoil),
				LazyItemStack.CODEC.optionalFieldOf("new_seed").forGetter(PotInteractionRecipe::newSeed),
				Identifier.CODEC.optionalFieldOf("extra_drops").forGetter(PotInteractionRecipe::extraDrops),
				SoundEffect.CODEC.optionalFieldOf("sound_effect").forGetter(PotInteractionRecipe::soundEffect),
				Codec.BOOL.optionalFieldOf("notify_sculk", true).forGetter(PotInteractionRecipe::notifySculk),
				LoadCondition.CONDITIONS_CODEC.forGetter(PotInteractionRecipe::conditions)
			)
			.apply(i, PotInteractionRecipe::new)
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
	public RecipeSerializer<PotInteractionRecipe> getSerializer() {
		return ModRecipes.POT_INTERACTION_SERIALIZER;
	}

	@Override
	public RecipeType<PotInteractionRecipe> getType() {
		return ModRecipes.POT_INTERACTION_TYPE;
	}
}
