package dev.nitjsefnie.cultivated.recipe;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
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
import java.util.function.Supplier;
import net.minecraft.advancements.predicates.BlockPredicate;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.Half;
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

	/**
	 * A tolerant {@code block} reference (§A.9 / C1): a missing id resolves to {@link Optional#empty()}
	 * rather than raising a decode error, so a guarded file for a block from another mod that is absent still
	 * parses. The recipe is then dropped at cache-build time by its failing load condition; a file
	 * with no gating condition is reported as a genuine error (see {@code resolve}).
	 */
	private record BlockRef(Identifier id, Optional<Block> block) {
		static final Codec<BlockRef> CODEC = Identifier.CODEC.xmap(
			id -> new BlockRef(id, BuiltInRegistries.BLOCK.getOptional(id)),
			BlockRef::id
		);
	}

	// ---- block_derived_soil ----

	private record SoilSpec(
		BlockRef block,
		Optional<CultivatedIngredient> input,
		Optional<Display> display,
		float growthModifier,
		int lightLevel,
		float yieldModifier,
		Optional<RenderOptions> renderOptions,
		List<LoadCondition> conditions
	) {
		DataResult<SoilRecipe> resolve() {
			final Optional<Block> resolved = this.block.block();
			if (resolved.isEmpty()) {
				return absentBlock("block_derived_soil", this.block.id(), this.conditions, () -> inertSoil(this.conditions));
			}
			final Block block = resolved.get();
			final RenderOptions options = this.renderOptions.orElse(UP_FACE_OPTIONS);
			final CultivatedIngredient ingredient = this.input.orElseGet(() -> vanillaOf(block.asItem()));
			final Display resolvedDisplay = this.display.orElseGet(() -> new Display.Simple(block.defaultBlockState(), options));
			return DataResult.success(new SoilRecipe(ingredient, resolvedDisplay, this.growthModifier, this.lightLevel, this.yieldModifier, this.conditions));
		}
	}

	public static final MapCodec<SoilRecipe> SOIL_CODEC = RecordCodecBuilder.<SoilSpec>mapCodec(
			i -> i.group(
					BlockRef.CODEC.fieldOf("block").forGetter(SoilSpec::block),
					CultivatedIngredient.CODEC.optionalFieldOf("input").forGetter(SoilSpec::input),
					Display.CODEC.optionalFieldOf("display").forGetter(SoilSpec::display),
					Codec.FLOAT.optionalFieldOf("growth_modifier", 0.0f).forGetter(SoilSpec::growthModifier),
					Codec.INT.optionalFieldOf("light_level", 0).forGetter(SoilSpec::lightLevel),
					Codec.floatRange(0.0f, Float.MAX_VALUE).optionalFieldOf("yield_modifier", 0.0f).forGetter(SoilSpec::yieldModifier),
					RenderOptions.CODEC.optionalFieldOf("render_options").forGetter(SoilSpec::renderOptions),
					LoadCondition.CONDITIONS_CODEC.forGetter(SoilSpec::conditions)
				)
				.apply(i, SoilSpec::new)
		)
		.flatXmap(SoilSpec::resolve, recipe -> DataResult.error(() -> "block_derived_soil is decode-only"));

	// ---- block_derived_crop ----

	private record CropSpec(
		BlockRef block,
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
		DataResult<CropRecipe> resolve() {
			final Optional<Block> resolved = this.block.block();
			if (resolved.isEmpty()) {
				return absentBlock("block_derived_crop", this.block.id(), this.conditions, () -> inertCrop(this.conditions));
			}
			final Block block = resolved.get();
			final RenderOptions options = this.renderOptions.orElse(RenderOptions.DEFAULT);
			final CultivatedIngredient ingredient = this.input.orElseGet(() -> deriveSeed(block));
			final List<Display> resolvedDisplay = this.display.orElseGet(() -> autoDisplay(block, options));
			final List<DropProvider> resolvedDrops = this.drops.orElseGet(() -> List.of(new DropProvider.BlockStateDrop(harvestState(block))));
			return DataResult.success(new CropRecipe(
				ingredient, this.soil, this.growTime, resolvedDisplay, this.lightLevel, resolvedDrops,
				this.function, this.potPredicate, this.yield, this.yieldScale, this.conditions
			));
		}
	}

	public static final MapCodec<CropRecipe> CROP_CODEC = RecordCodecBuilder.<CropSpec>mapCodec(
			i -> i.group(
					BlockRef.CODEC.fieldOf("block").forGetter(CropSpec::block),
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
					LoadCondition.CONDITIONS_CODEC.forGetter(CropSpec::conditions)
				)
				.apply(i, CropSpec::new)
		)
		.flatXmap(CropSpec::resolve, recipe -> DataResult.error(() -> "block_derived_crop is decode-only"));

	// ---- absent-block handling (C1) ----

	/**
	 * Decide how a derived recipe whose {@code block} is absent resolves (§A.9). If a load condition
	 * gates the file (so {@link LoadCondition#testAll} is already false), it is a guarded file for
	 * a block from another mod: succeed with an inert placeholder that carries the failing conditions, so the recipe is
	 * dropped at cache-build with no error. Otherwise the missing block is a genuine datapack error
	 * and is reported as such.
	 */
	private static <T> DataResult<T> absentBlock(
		final String kind, final Identifier blockId, final List<LoadCondition> conditions, final Supplier<T> inert
	) {
		if (!LoadCondition.testAll(conditions)) {
			return DataResult.success(inert.get());
		}
		return DataResult.error(() -> kind + " references unknown block " + blockId + " and has no load condition gating it");
	}

	/** A never-matching, never-rendered placeholder crop for a guarded, absent-block file (C1). */
	private static CropRecipe inertCrop(final List<LoadCondition> conditions) {
		return new CropRecipe(
			INERT_INGREDIENT, Optional.empty(), 1, List.of(), 0, List.of(),
			Optional.empty(), Optional.empty(), 1.0f, 1.0f, conditions
		);
	}

	/** A never-matching, never-rendered placeholder soil for a guarded, absent-block file (C1). */
	private static SoilRecipe inertSoil(final List<LoadCondition> conditions) {
		return new SoilRecipe(
			INERT_INGREDIENT, new Display.Simple(Blocks.AIR.defaultBlockState(), UP_FACE_OPTIONS), 0.0f, 0, 0.0f, conditions
		);
	}

	/** An empty "either" ingredient — matches nothing (used only by inert placeholders). */
	private static final CultivatedIngredient INERT_INGREDIENT = new CultivatedIngredient.Either_(List.of());

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
	 * Auto display: a {@code half} (top/bottom) or {@code double_block_half} (upper/lower) property
	 * → two stacked simple displays (lower, then upper); otherwise an {@code aging} display built
	 * from the block (§A.4, §C.3).
	 */
	static List<Display> autoDisplay(final Block block, final RenderOptions options) {
		final BlockState state = block.defaultBlockState();
		final Property<?> half = findHalfProperty(block);
		if (half != null) {
			final List<Display> phases = new ArrayList<>(2);
			for (final BlockState halfState : orderedHalfStates(state, half)) {
				phases.add(new Display.Simple(halfState, options));
			}
			return phases;
		}
		return List.of(new Display.Aging(block, options));
	}

	/**
	 * The block's half-style property, if any: a {@link Half} (top/bottom) or {@link DoubleBlockHalf}
	 * (upper/lower) property marks a two-part block that renders as two stacked halves (§A.4). Both
	 * serialise under the state name {@code "half"} in current Minecraft, but detecting by value type
	 * makes the "half OR double_block_half" rule explicit and robust to alternate property names.
	 */
	private static Property<?> findHalfProperty(final Block block) {
		for (final Property<?> property : block.getStateDefinition().getProperties()) {
			final Class<?> valueClass = property.getValueClass();
			if (valueClass == Half.class || valueClass == DoubleBlockHalf.class) {
				return property;
			}
		}
		return null;
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
