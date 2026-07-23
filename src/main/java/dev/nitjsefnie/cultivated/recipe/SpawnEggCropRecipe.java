package dev.nitjsefnie.cultivated.recipe;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.nitjsefnie.cultivated.condition.LoadCondition;
import dev.nitjsefnie.cultivated.data.display.Display;
import dev.nitjsefnie.cultivated.data.display.RenderOptions.Vec3f;
import dev.nitjsefnie.cultivated.data.drop.DropProvider;
import dev.nitjsefnie.cultivated.ingredient.CultivatedIngredient;
import dev.nitjsefnie.cultivated.registry.ModRecipes;
import dev.nitjsefnie.cultivated.util.LazyItemStack;
import java.util.List;
import java.util.Optional;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

/**
 * A single generic "growable mob" crop ({@code cultivated:spawn_egg_crop}). Its primary ingredient is
 * {@link CultivatedIngredient.SpawnEgg} — "any spawn egg" — so one shipped JSON is indexed under EVERY
 * spawn egg item (vanilla AND modded) by {@link dev.nitjsefnie.cultivated.cache.RecipeLookupCache}. It
 * is not itself a renderable crop: at resolve time the pot {@linkplain #resolveFor(ItemStack) derives}
 * a concrete {@link CropRecipe} from whichever spawn egg is planted — an {@code entity} display of that
 * egg's {@link EntityType} and an {@code entity} drop that rolls that mob's death loot AND (via
 * finalizeSpawn) its worn equipment. So the mechanism is generic: no per-mob recipe and no hardcoded
 * entity id anywhere.
 */
public record SpawnEggCropRecipe(
	Optional<CultivatedIngredient> soil,
	int growTime,
	int lightLevel,
	boolean shouldTick,
	float spinSpeed,
	Vec3f scale,
	Optional<Vec3f> offset,
	Optional<Identifier> damageSource,
	float yield,
	float yieldScale,
	List<LoadCondition> conditions
) implements BotanyRecipe {
	public static final MapCodec<SpawnEggCropRecipe> EXPLICIT_CODEC = RecordCodecBuilder.mapCodec(
		i -> i.group(
				CultivatedIngredient.CODEC.optionalFieldOf("soil").forGetter(SpawnEggCropRecipe::soil),
				Codec.intRange(1, Integer.MAX_VALUE).optionalFieldOf("grow_time", 1200).forGetter(SpawnEggCropRecipe::growTime),
				Codec.intRange(0, 15).optionalFieldOf("light_level", 0).forGetter(SpawnEggCropRecipe::lightLevel),
				Codec.BOOL.optionalFieldOf("should_tick", true).forGetter(SpawnEggCropRecipe::shouldTick),
				Codec.FLOAT.optionalFieldOf("spin_speed", 0.0f).forGetter(SpawnEggCropRecipe::spinSpeed),
				Vec3f.CODEC.optionalFieldOf("scale", Display.Entity.DEFAULT_SCALE).forGetter(SpawnEggCropRecipe::scale),
				Vec3f.CODEC.optionalFieldOf("offset").forGetter(SpawnEggCropRecipe::offset),
				Identifier.CODEC.optionalFieldOf("damage_source").forGetter(SpawnEggCropRecipe::damageSource),
				Codec.floatRange(0.0f, Float.MAX_VALUE).optionalFieldOf("yield", 1.0f).forGetter(SpawnEggCropRecipe::yield),
				Codec.floatRange(0.0f, Float.MAX_VALUE).optionalFieldOf("yield_scale", 1.0f).forGetter(SpawnEggCropRecipe::yieldScale),
				LoadCondition.CONDITIONS_CODEC.forGetter(SpawnEggCropRecipe::conditions)
			)
			.apply(i, SpawnEggCropRecipe::new)
	);

	@Override
	public CultivatedIngredient primaryIngredient() {
		return CultivatedIngredient.SpawnEgg.INSTANCE;
	}

	@Override
	public boolean matches(final PotContext input, final Level level) {
		return this.primaryIngredient().test(input.getSeedStack());
	}

	/**
	 * Derive the concrete per-seed crop for the {@code seedEgg} planted in the pot: an {@code entity}
	 * display and an equipment-aware {@code entity} death-loot drop for the egg's {@link EntityType}.
	 * Returns {@code null} when the stack is not a spawn egg or carries no resolvable entity type.
	 */
	public @Nullable CropRecipe resolveFor(final ItemStack seedEgg) {
		if (!(seedEgg.getItem() instanceof SpawnEggItem)) {
			return null;
		}
		final EntityType<?> type = SpawnEggItem.getType(seedEgg);
		if (type == null) {
			return null;
		}
		final Identifier entityId = EntityType.getKey(type);
		final CompoundTag entityNbt = new CompoundTag();
		entityNbt.putString("id", entityId.toString());

		final Display display = new Display.Entity(entityNbt.copy(), this.shouldTick, this.spinSpeed, this.scale, this.offset);
		// finalize_spawn = true: the harvest builds the mob through its own finalizeSpawn (small chance
		// armored) and rolls both its death loot table and its worn equipment.
		final DropProvider drop = new DropProvider.EntityDrop(entityNbt.copy(), this.damageSource, true);
		// A rare (0.1%) bonus: harvest can also yield the mob's own spawn egg back, ON TOP of the death
		// loot + equipment. Built from the planted seed directly (components bound at harvest time), so it
		// is generic for any spawn egg — vanilla or modded.
		// An even rarer (0.01%) bonus: the same harvest may also drop an empty mob spawner, matching the
		// crop's soil item. Each entry is rolled independently.
		final DropProvider eggDrop = new DropProvider.Items(List.of(
			new DropProvider.Items.Entry(LazyItemStack.of(seedEgg.copyWithCount(1)), 0.001f),
			new DropProvider.Items.Entry(LazyItemStack.of(new ItemStack(Items.SPAWNER)), 0.0001f)
		));

		return new CropRecipe(
			this.primaryIngredient(),
			this.soil,
			this.growTime,
			List.of(display),
			this.lightLevel,
			List.of(drop, eggDrop),
			Optional.empty(),
			Optional.empty(),
			this.yield,
			this.yieldScale,
			List.of()
		);
	}

	@Override
	public RecipeSerializer<SpawnEggCropRecipe> getSerializer() {
		return ModRecipes.SPAWN_EGG_CROP_SERIALIZER;
	}

	@Override
	public RecipeType<SpawnEggCropRecipe> getType() {
		return ModRecipes.SPAWN_EGG_CROP_TYPE;
	}
}
