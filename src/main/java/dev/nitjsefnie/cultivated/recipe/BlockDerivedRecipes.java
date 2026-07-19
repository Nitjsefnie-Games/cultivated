package dev.nitjsefnie.cultivated.recipe;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.nitjsefnie.cultivated.condition.LoadCondition;
import dev.nitjsefnie.cultivated.data.display.Display;
import dev.nitjsefnie.cultivated.data.display.RenderOptions;
import dev.nitjsefnie.cultivated.data.drop.DropProvider;
import dev.nitjsefnie.cultivated.ingredient.CultivatedIngredient;
import dev.nitjsefnie.cultivated.mixin.CropBlockAccessor;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import net.minecraft.advancements.predicates.BlockPredicate;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.Property;

/**
 * Phase A §A.3/§A.4 — the {@code block_derived_soil} / {@code block_derived_crop} serializers.
 * These are decode-time conveniences: they derive a resolved {@link SoilRecipe}/{@link CropRecipe}
 * from a block. The resulting instances re-serialise via their explicit serializers, so these
 * derived codecs are decode-only (encode is never invoked at runtime).
 */
public final class BlockDerivedRecipes {
	private BlockDerivedRecipes() {
	}

	/** Up-face render options used for the derived soil display (§A.3). */
	private static final RenderOptions UP_FACE_OPTIONS = new RenderOptions(
		RenderOptions.DEFAULT_SCALE, RenderOptions.ZERO, List.of(), false, Optional.empty(), Set.of(Direction.UP)
	);

	// ---- block_derived_soil ----

	private record SoilSpec(
		Block block,
		Optional<CultivatedIngredient> input,
		Optional<Display> display,
		float growthModifier,
		int lightLevel,
		float yieldModifier,
		Optional<RenderOptions> renderOptions,
		List<LoadCondition> conditions
	) {
		SoilRecipe resolve() {
			final RenderOptions options = this.renderOptions.orElse(UP_FACE_OPTIONS);
			final CultivatedIngredient ingredient = this.input.orElseGet(() -> vanillaOf(this.block.asItem()));
			final Display resolvedDisplay = this.display.orElseGet(() -> new Display.Simple(this.block.defaultBlockState(), options));
			return new SoilRecipe(ingredient, resolvedDisplay, this.growthModifier, this.lightLevel, this.yieldModifier, this.conditions);
		}
	}

	public static final MapCodec<SoilRecipe> SOIL_CODEC = RecordCodecBuilder.<SoilSpec>mapCodec(
			i -> i.group(
					BuiltInRegistries.BLOCK.byNameCodec().fieldOf("block").forGetter(SoilSpec::block),
					CultivatedIngredient.CODEC.optionalFieldOf("input").forGetter(SoilSpec::input),
					Display.CODEC.optionalFieldOf("display").forGetter(SoilSpec::display),
					Codec.FLOAT.optionalFieldOf("growth_modifier", 0.0f).forGetter(SoilSpec::growthModifier),
					Codec.INT.optionalFieldOf("light_level", 0).forGetter(SoilSpec::lightLevel),
					Codec.floatRange(0.0f, Float.MAX_VALUE).optionalFieldOf("yield_modifier", 0.0f).forGetter(SoilSpec::yieldModifier),
					RenderOptions.CODEC.optionalFieldOf("render_options").forGetter(SoilSpec::renderOptions),
					LoadCondition.LIST_CODEC.optionalFieldOf("cultivated:load_conditions", List.of()).forGetter(SoilSpec::conditions)
				)
				.apply(i, SoilSpec::new)
		)
		.xmap(SoilSpec::resolve, recipe -> {
			throw new UnsupportedOperationException("block_derived_soil is decode-only");
		});

	// ---- block_derived_crop ----

	private record CropSpec(
		Block block,
		Optional<CultivatedIngredient> input,
		Optional<CultivatedIngredient> soil,
		int growTime,
		Optional<List<Display>> display,
		int lightLevel,
		Optional<List<DropProvider>> drops,
		Optional<RenderOptions> renderOptions,
		Optional<Identifier> function,
		Optional<BlockPredicate> potPredicate,
		float yield,
		float yieldScale,
		List<LoadCondition> conditions
	) {
		CropRecipe resolve() {
			final RenderOptions options = this.renderOptions.orElse(RenderOptions.DEFAULT);
			final CultivatedIngredient ingredient = this.input.orElseGet(() -> deriveSeed(this.block));
			final List<Display> resolvedDisplay = this.display.orElseGet(() -> autoDisplay(this.block, options));
			final List<DropProvider> resolvedDrops = this.drops.orElseGet(() -> List.of(new DropProvider.BlockStateDrop(harvestState(this.block))));
			return new CropRecipe(
				ingredient, this.soil, this.growTime, resolvedDisplay, this.lightLevel, resolvedDrops,
				this.function, this.potPredicate, this.yield, this.yieldScale, this.conditions
			);
		}
	}

	public static final MapCodec<CropRecipe> CROP_CODEC = RecordCodecBuilder.<CropSpec>mapCodec(
			i -> i.group(
					BuiltInRegistries.BLOCK.byNameCodec().fieldOf("block").forGetter(CropSpec::block),
					CultivatedIngredient.CODEC.optionalFieldOf("input").forGetter(CropSpec::input),
					CultivatedIngredient.CODEC.optionalFieldOf("soil").forGetter(CropSpec::soil),
					Codec.intRange(1, Integer.MAX_VALUE).optionalFieldOf("grow_time", 1200).forGetter(CropSpec::growTime),
					Display.LIST_CODEC.optionalFieldOf("display").forGetter(CropSpec::display),
					Codec.intRange(0, 15).optionalFieldOf("light_level", 0).forGetter(CropSpec::lightLevel),
					DropProvider.LIST_CODEC.optionalFieldOf("drops").forGetter(CropSpec::drops),
					RenderOptions.CODEC.optionalFieldOf("render_options").forGetter(CropSpec::renderOptions),
					Identifier.CODEC.optionalFieldOf("function").forGetter(CropSpec::function),
					BlockPredicate.CODEC.optionalFieldOf("pot_predicate").forGetter(CropSpec::potPredicate),
					Codec.floatRange(0.0f, Float.MAX_VALUE).optionalFieldOf("yield", 1.0f).forGetter(CropSpec::yield),
					Codec.floatRange(0.0f, Float.MAX_VALUE).optionalFieldOf("yield_scale", 1.0f).forGetter(CropSpec::yieldScale),
					LoadCondition.LIST_CODEC.optionalFieldOf("cultivated:load_conditions", List.of()).forGetter(CropSpec::conditions)
				)
				.apply(i, CropSpec::new)
		)
		.xmap(CropSpec::resolve, recipe -> {
			throw new UnsupportedOperationException("block_derived_crop is decode-only");
		});

	// ---- derivation helpers ----

	private static CultivatedIngredient vanillaOf(final Item item) {
		return new CultivatedIngredient.Vanilla(Ingredient.of(item));
	}

	/** Derive the seed item for a block: a crop block's seed, else the block's own item (§A.4). */
	private static CultivatedIngredient deriveSeed(final Block block) {
		if (block instanceof CropBlock) {
			final ItemLike seed = ((CropBlockAccessor)block).cultivated$getBaseSeedId();
			if (seed != null && seed.asItem() != Items.AIR) {
				return vanillaOf(seed.asItem());
			}
		}
		final Item own = block.asItem();
		if (own == Items.AIR) {
			throw new IllegalArgumentException("block_derived_crop: block " + BuiltInRegistries.BLOCK.getKey(block) + " has no seed or item to derive from");
		}
		return vanillaOf(own);
	}

	/**
	 * Auto display: a {@code half}-style property → two stacked simple displays (lower, then
	 * upper); otherwise an {@code aging} display built from the block (§A.4, §C.3).
	 */
	private static List<Display> autoDisplay(final Block block, final RenderOptions options) {
		final BlockState state = block.defaultBlockState();
		final Property<?> half = block.getStateDefinition().getProperty("half");
		if (half != null) {
			final List<Display> phases = new ArrayList<>(2);
			for (final BlockState halfState : orderedHalfStates(state, half)) {
				phases.add(new Display.Simple(halfState, options));
			}
			return phases;
		}
		return List.of(new Display.Aging(block, options));
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	private static List<BlockState> orderedHalfStates(final BlockState base, final Property<?> half) {
		final Property property = half;
		final List<Comparable> values = new ArrayList<>((List<Comparable>)half.getPossibleValues());
		// Lower/bottom first so the crop stacks from the ground up.
		values.sort(Comparator.comparingInt(v -> lowerFirstRank(property, v)));
		final List<BlockState> states = new ArrayList<>();
		for (final Comparable value : values) {
			states.add(base.setValue(property, value));
		}
		return states;
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	private static int lowerFirstRank(final Property property, final Comparable value) {
		final String name = property.getName(value).toLowerCase();
		if (name.contains("lower") || name.contains("bottom")) {
			return 0;
		}
		return 1;
	}

	/** The block's harvest state: max age (+ berries if present) so fully-grown loot conditions pass. */
	@SuppressWarnings({"unchecked", "rawtypes"})
	private static BlockState harvestState(final Block block) {
		BlockState state = block.defaultBlockState();
		if (block instanceof CropBlock crop) {
			state = crop.getStateForAge(crop.getMaxAge());
		} else {
			for (final Property<?> property : state.getProperties()) {
				if (property instanceof IntegerProperty ageProperty && "age".equals(ageProperty.getName())) {
					final int max = ageProperty.getPossibleValues().stream().max(Comparator.naturalOrder()).orElse(0);
					state = state.setValue(ageProperty, max);
				}
			}
		}
		final Property<?> berries = state.getBlock().getStateDefinition().getProperty("berries");
		if (berries instanceof BooleanProperty berriesProperty) {
			state = state.setValue(berriesProperty, Boolean.TRUE);
		}
		return state;
	}
}
