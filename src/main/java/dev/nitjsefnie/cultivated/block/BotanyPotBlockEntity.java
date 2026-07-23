package dev.nitjsefnie.cultivated.block;

import dev.nitjsefnie.cultivated.cache.PotRecipeCaches;
import dev.nitjsefnie.cultivated.config.CultivatedConfig;
import dev.nitjsefnie.cultivated.data.drop.DropProvider;
import dev.nitjsefnie.cultivated.data.formula.GrowthFormula;
import dev.nitjsefnie.cultivated.data.formula.YieldFormula;
import dev.nitjsefnie.cultivated.recipe.CropRecipe;
import dev.nitjsefnie.cultivated.recipe.FertilizerRecipe;
import dev.nitjsefnie.cultivated.recipe.LiveContext;
import dev.nitjsefnie.cultivated.recipe.PotContext;
import dev.nitjsefnie.cultivated.recipe.PotInteractionRecipe;
import dev.nitjsefnie.cultivated.recipe.SimplePotContext;
import dev.nitjsefnie.cultivated.recipe.SoilRecipe;
import dev.nitjsefnie.cultivated.registry.ModAttributes;
import dev.nitjsefnie.cultivated.registry.ModComponents;
import dev.nitjsefnie.cultivated.registry.ModTags;
import dev.nitjsefnie.cultivated.util.EnchantmentLevelHelper;
import dev.nitjsefnie.cultivated.util.ReloadableCache;
import dev.nitjsefnie.cultivated.util.TickAccumulator;
import dev.nitjsefnie.cultivated.util.ToolAttributes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.core.particles.ParticleTypes;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ProblemReporter;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

/**
 * Phase B §B.3/§B.4/§B.5 — the stateful heart of the botany pot: a 15-slot world-aware container
 * ({@code SOIL=0, SEED=1, TOOL=2}, storage {@code 3..14}) that resolves its crop/soil (override
 * component first, then the sided recipe cache), grows on a game-tick accumulator (one step per
 * game tick, so {@code /tick rate} scales growth speed — §G #4, user decision 2026-07-20),
 * drives its comparator output, auto-harvests + exports (hopper pots) and holds at mature (basic
 * pots). Waxed pots are decorative and force growth to max without ticking.
 *
 * <p>The block, block-entity-type registration and ticker wiring are Task B2; this class references
 * its own type lazily through the settable {@link #TYPE} holder that B2 assigns, so it compiles and
 * its pure logic is unit-testable now (see {@link PotMechanics}).
 */
public class BotanyPotBlockEntity extends BlockEntity implements WorldlyContainer {
	/**
	 * Per-tier block-entity types, one registered over the blocks of each {@link Tier} (base by Task B2,
	 * the three tiers by Task D2). A pot block entity reports the type registered for its <em>block's</em>
	 * tier — required so {@link BlockEntityType#isValid} accepts the block on construction and ticking and
	 * so save/load round-trips under the correct id (§D). Populated by
	 * {@link dev.nitjsefnie.cultivated.registry.ModBlockEntities} via {@link #registerType}.
	 */
	private static final java.util.Map<Tier, BlockEntityType<BotanyPotBlockEntity>> TYPES = new java.util.EnumMap<>(Tier.class);

	/**
	 * The base ({@link Tier#BASE}) type, kept as a named handle for the {@code cultivated:botany_pot}
	 * consumers that reference it directly (client renderer wiring). Assigned by {@link #registerType} for
	 * the base tier; {@code null} until Task B2 registration runs.
	 */
	public static @Nullable BlockEntityType<BotanyPotBlockEntity> TYPE;

	/** Ticks between hopper-export attempts (inferred — the source's exact cadence was not visible). */
	private static final float EXPORT_INTERVAL_TICKS = 8.0f;
	/** Re-check maturity every 5 ticks once a crop is mature (§B.5). */
	private static final float MATURE_RECHECK_TICKS = 5.0f;

	private final PotType potType;
	private final Tier tier;
	private NonNullList<ItemStack> items = NonNullList.withSize(PotMechanics.SIZE, ItemStack.EMPTY);
	private final TickAccumulator growthTime = new TickAccumulator();
	private final TickAccumulator growCooldown = new TickAccumulator();
	private final TickAccumulator exportDelay = new TickAccumulator();
	private int comparatorLevel;
	private int bonemealCooldown;

	private final ReloadableCache<CropRecipe> cropCache = ReloadableCache.of(this::computeCrop);
	private final ReloadableCache<SoilRecipe> soilCache = ReloadableCache.of(this::computeSoil);

	public BotanyPotBlockEntity(final BlockPos pos, final BlockState state) {
		super(typeFor(tierOf(state)), pos, state);
		this.potType = state.getBlock() instanceof PotType.Provider provider ? provider.potType() : PotType.BASIC;
		this.tier = state.getBlock() instanceof Tier.Provider provider ? provider.tier() : Tier.BASE;
	}

	private static Tier tierOf(final BlockState state) {
		return state.getBlock() instanceof Tier.Provider provider ? provider.tier() : Tier.BASE;
	}

	/** Record the block-entity type registered for {@code tier}; called once per tier by Task B2/D2. */
	public static void registerType(final Tier tier, final BlockEntityType<BotanyPotBlockEntity> type) {
		TYPES.put(tier, type);
		if (tier == Tier.BASE) {
			TYPE = type;
		}
	}

	/**
	 * The block-entity type registered for {@code tier}. Read lazily by the constructor and the block's
	 * ticker wiring, so this class links and its static logic tests without a live registry; resolving a
	 * tier before Task B2/D2 registration has run throws a clear error.
	 */
	public static BlockEntityType<BotanyPotBlockEntity> typeFor(final Tier tier) {
		final BlockEntityType<BotanyPotBlockEntity> type = TYPES.get(tier);
		if (type == null) {
			throw new IllegalStateException(
				"BotanyPotBlockEntity type for tier " + tier + " not registered yet (assigned in Task B2/D2)");
		}
		return type;
	}

	public PotType getPotType() {
		return this.potType;
	}

	public Tier getTier() {
		return this.tier;
	}

	// ---- tier modifiers (§D): additive into the growth divisor (§A.7) / totalYield (§A.8); BASE = 0 ----

	/** Additive pot growth-speed modifier (§A.7); 0 for base pots, the tier speed for tiered pots. */
	public double growthModifier() {
		return this.tier.speed();
	}

	/** Additive pot output modifier (§A.8); 0 for base pots, the tier output for tiered pots. */
	public double yieldModifier() {
		return this.tier.output();
	}

	// ---- container ----

	@Override
	public int getContainerSize() {
		return PotMechanics.SIZE;
	}

	@Override
	public boolean isEmpty() {
		for (final ItemStack stack : this.items) {
			if (!stack.isEmpty()) {
				return false;
			}
		}
		return true;
	}

	@Override
	public ItemStack getItem(final int slot) {
		return slot >= 0 && slot < this.items.size() ? this.items.get(slot) : ItemStack.EMPTY;
	}

	@Override
	public ItemStack removeItem(final int slot, final int count) {
		final ItemStack removed = ContainerHelper.removeItem(this.items, slot, count);
		if (!removed.isEmpty()) {
			this.onSlotChanged(slot);
		}
		return removed;
	}

	@Override
	public ItemStack removeItemNoUpdate(final int slot) {
		final ItemStack removed = ContainerHelper.takeItem(this.items, slot);
		this.onSlotChanged(slot);
		return removed;
	}

	@Override
	public void setItem(final int slot, final ItemStack stack) {
		if (slot < 0 || slot >= this.items.size()) {
			return;
		}
		this.items.set(slot, stack);
		// Soil/seed hold at most one item, even when set by automation/setItem (§B.3, PF2b).
		stack.limitSize(PotMechanics.maxStackSizeForSlot(slot, this.getMaxStackSize(stack)));
		this.onSlotChanged(slot);
	}

	/** Setting soil/seed resets the pot; any input change invalidates caches and re-syncs clients. */
	private void onSlotChanged(final int slot) {
		if (slot == PotMechanics.SOIL || slot == PotMechanics.SEED) {
			this.resetPot();
		}
		this.cropCache.invalidate();
		this.soilCache.invalidate();
		this.setChanged();
		if (PotMechanics.isInputSlot(slot)) {
			this.syncToClients();
		}
	}

	/** Full pot reset (§B.3): clear growth, invalidate cached crop/soil, reset comparator + cooldowns. */
	private void resetPot() {
		this.growthTime.reset();
		this.growCooldown.reset();
		this.setComparatorLevel(0);
		this.bonemealCooldown = 0;
		this.cropCache.invalidate();
		this.soilCache.invalidate();
	}

	@Override
	public boolean canPlaceItem(final int slot, final ItemStack stack) {
		return switch (slot) {
			case PotMechanics.SOIL, PotMechanics.SEED -> true;
			case PotMechanics.TOOL -> stack.is(ModTags.HARVEST_ITEMS);
			default -> false; // storage slots are output-only
		};
	}

	@Override
	public boolean stillValid(final Player player) {
		return Container.stillValidBlockEntity(this, player);
	}

	@Override
	public void clearContent() {
		for (int i = 0; i < this.items.size(); i++) {
			this.items.set(i, ItemStack.EMPTY);
		}
		this.resetPot();
		this.setChanged();
	}

	// ---- automation faces (§B.3) ----

	@Override
	public int[] getSlotsForFace(final Direction direction) {
		return PotMechanics.automationSlotsForFace(this.potType.isHopper(), direction);
	}

	@Override
	public boolean canPlaceItemThroughFace(final int slot, final ItemStack stack, final @Nullable Direction direction) {
		return PotMechanics.canAutomationPlace();
	}

	@Override
	public boolean canTakeItemThroughFace(final int slot, final ItemStack stack, final Direction direction) {
		return PotMechanics.canAutomationTake(this.potType.isHopper(), slot, direction);
	}

	// ---- crop / soil resolution (§A.2, §A.11, S2/S3) ----

	/** The pot's resolved crop (override component first, then the sided recipe cache), or null. */
	public @Nullable CropRecipe resolveCrop() {
		CropRecipe cached = this.cropCache.get();
		// Override-sourced values are authoritative from the seed's item component and are re-derived on
		// any slot change; skip the per-tick matches() re-confirm (an override recipe's matches()
		// typically returns false against the seed stack, which would needlessly invalidate + recompute
		// every tick). Only cache-sourced values are re-confirmed.
		if (cached != null && this.getItem(PotMechanics.SEED).get(ModComponents.CROP_OVERRIDE) == null
			&& this.level != null && !cached.matches(this.matchContext(), this.level)) {
			this.cropCache.invalidate();
			cached = this.cropCache.get();
		}
		return cached;
	}

	/** The pot's resolved soil (override component first, then the sided recipe cache), or null. */
	public @Nullable SoilRecipe resolveSoil() {
		SoilRecipe cached = this.soilCache.get();
		// See resolveCrop: override-sourced soil skips the per-tick re-confirm; cache-sourced re-confirms.
		if (cached != null && this.getItem(PotMechanics.SOIL).get(ModComponents.SOIL_OVERRIDE) == null
			&& this.level != null && !cached.matches(this.matchContext(), this.level)) {
			this.soilCache.invalidate();
			cached = this.soilCache.get();
		}
		return cached;
	}

	private @Nullable CropRecipe computeCrop() {
		final ItemStack seed = this.getItem(PotMechanics.SEED);
		if (seed.isEmpty()) {
			return null;
		}
		final CropRecipe override = seed.get(ModComponents.CROP_OVERRIDE);
		if (override != null) {
			return override;
		}
		if (this.level == null) {
			return null;
		}
		final boolean clientSide = this.level.isClientSide();
		final CropRecipe candidate = PotRecipeCaches.crops(clientSide).lookup(seed, this.matchContext());
		if (candidate != null && candidate.matches(this.matchContext(), this.level)) {
			return candidate;
		}
		// Generic growable-mob path: a spawn egg (vanilla or modded) resolves to a synthetic crop whose
		// entity display + equipment-aware entity death-loot drop are derived from THAT egg's entity type.
		final dev.nitjsefnie.cultivated.recipe.SpawnEggCropRecipe eggCrop =
			PotRecipeCaches.spawnEggCrops(clientSide).lookup(seed, this.matchContext());
		if (eggCrop != null && eggCrop.matches(this.matchContext(), this.level)) {
			return eggCrop.resolveFor(seed);
		}
		return null;
	}

	private @Nullable SoilRecipe computeSoil() {
		final ItemStack soil = this.getItem(PotMechanics.SOIL);
		if (soil.isEmpty()) {
			return null;
		}
		final SoilRecipe override = soil.get(ModComponents.SOIL_OVERRIDE);
		if (override != null) {
			return override;
		}
		if (this.level == null) {
			return null;
		}
		final SoilRecipe candidate = PotRecipeCaches.soils(this.level.isClientSide()).lookup(soil, this.matchContext());
		return candidate != null && candidate.matches(this.matchContext(), this.level) ? candidate : null;
	}

	private PotContext matchContext() {
		final boolean serverSide = this.level != null && !this.level.isClientSide();
		return new SimplePotContext(
			this.getItem(PotMechanics.SOIL), this.getItem(PotMechanics.SEED), this.getItem(PotMechanics.TOOL),
			ItemStack.EMPTY, this.level, serverSide, 0
		);
	}

	/** The crop's required growth ticks under this pot's current soil/tool/pot modifiers (§A.7). */
	public int requiredGrowthTicks() {
		final CropRecipe crop = this.resolveCrop();
		if (crop == null) {
			return 0;
		}
		final SoilRecipe soil = this.resolveSoil();
		final ItemStack tool = this.getHarvestTool();
		final double soilGrowth = soil != null ? soil.growthModifier() : 0.0;
		final double toolEfficiency = GrowthFormula.toolEfficiencyModifier(tool, ToolAttributes.sum(tool, ModAttributes.GROWTH));
		return GrowthFormula.requiredGrowthTicks(crop.growTime(), soilGrowth, toolEfficiency, this.growthModifier());
	}

	public ItemStack getHarvestTool() {
		return this.getItem(PotMechanics.TOOL);
	}

	public int getComparatorLevel() {
		return this.comparatorLevel;
	}

	/**
	 * The single owner of every comparator-level change (B1 review carry-over): on an actual change
	 * it marks the block entity dirty AND notifies redstone neighbours via
	 * {@link Level#updateNeighbourForOutputSignal}, so a comparator can never lag the stored signal.
	 * Server-authoritative; the neighbour notification is skipped client-side.
	 */
	private void setComparatorLevel(final int value) {
		if (this.comparatorLevel == value) {
			return;
		}
		this.comparatorLevel = value;
		this.setChanged();
		if (this.level != null && !this.level.isClientSide()) {
			this.level.updateNeighbourForOutputSignal(this.worldPosition, this.getBlockState().getBlock());
		}
	}

	/** Emitted light for this pot (§B.1): {@code max(crop light, soil light)}, 0 when empty. */
	private int computeLightLevel(final @Nullable CropRecipe crop, final @Nullable SoilRecipe soil) {
		int light = 0;
		if (crop != null) {
			light = Math.max(light, crop.lightLevel());
		}
		if (soil != null) {
			light = Math.max(light, soil.lightLevel());
		}
		return Math.max(0, Math.min(15, light));
	}

	/** Keep the block's {@code level} state (which drives light emission) in sync with the crop/soil. */
	private void updateLightLevel(final Level level, final @Nullable CropRecipe crop, final @Nullable SoilRecipe soil) {
		if (level.isClientSide()) {
			return; // block state is server-authoritative and replicated to clients
		}
		final BlockState state = this.getBlockState();
		if (!state.hasProperty(BotanyPotBlock.LEVEL)) {
			return;
		}
		final int desired = this.computeLightLevel(crop, soil);
		if (state.getValue(BotanyPotBlock.LEVEL) != desired) {
			level.setBlock(this.worldPosition, state.setValue(BotanyPotBlock.LEVEL, desired), Block.UPDATE_ALL);
		}
	}

	public float getGrowthTime() {
		return this.growthTime.get();
	}

	/** Growth progress in {@code [0,1]} for the renderer (§C.1); 0 when there is no crop. */
	public float getGrowthProgress() {
		final CropRecipe crop = this.resolveCrop();
		if (crop == null) {
			return 0.0f;
		}
		final int required = this.requiredGrowthTicks();
		if (required <= 0) {
			return 0.0f;
		}
		return Math.min(1.0f, this.growthTime.get() / required);
	}

	/** True if there is a mature crop ready to harvest. */
	public boolean isHarvestable() {
		final CropRecipe crop = this.resolveCrop();
		if (crop == null) {
			return false;
		}
		final int required = this.requiredGrowthTicks();
		return required > 0 && this.growthTime.get() >= required;
	}

	// ---- tick loop (§B.5) ----

	public static void tick(final Level level, final BlockPos pos, final BlockState state, final BotanyPotBlockEntity pot) {
		pot.tick(level);
	}

	private void tick(final Level level) {
		final boolean client = level.isClientSide();

		// Resolve crop/soil once per tick (both are cache-backed) and reuse everywhere below — the
		// light update, the waxed short-circuit and the growth body all read these same values.
		// (Soil's per-tick hook, if any, would run here — soils carry none in the data model.)
		final CropRecipe crop = this.resolveCrop();
		final SoilRecipe soil = this.resolveSoil();

		// Keep the emitted-light block state aligned with the resolved crop/soil (§B.1), for every
		// pot type — waxed pots also light from their decorative full-grown crop.
		this.updateLightLevel(level, crop, soil);

		if (this.potType.isWaxed()) {
			// Decorative: force growth to "max" so the renderer shows a full-grown crop; never harvest.
			final int required = crop != null ? this.requiredGrowthTicks() : 0;
			this.growthTime.set(Math.max(required, 1));
			return;
		}

		if (this.bonemealCooldown > 0) {
			this.bonemealCooldown--;
		}

		if (crop != null) {
			if (this.growCooldown.get() > 0.0f) {
				this.growCooldown.tickDown();
			}
			final boolean sustained = crop.acceptsSoil(this.getItem(PotMechanics.SOIL));
			// R2d: a HOPPER pot whose output buffer is full pauses its growth cycle — do NOT advance
			// growthTime and do NOT auto-harvest while there is nowhere to put the drops. It holds at
			// mature (comparator untouched) and resumes automatically the moment space frees up (an
			// export pushed items below, or the player drained the buffer). Basic/waxed pots never gate.
			if (this.growCooldown.get() <= 0.0f && sustained && this.canAdvanceGrowth()) {
				this.growthTime.tickUp();
				final int required = this.requiredGrowthTicks();
				if (!client && crop.function().isPresent()) {
					new LiveContext(this).runFunction(crop.function().get());
				}
				if (required > 0 && this.growthTime.get() >= required) {
					if (!client) {
						this.setComparatorLevel(PotMechanics.MATURE_SIGNAL);
						this.growCooldown.set(MATURE_RECHECK_TICKS);
						if (this.potType.isHopper()) {
							this.autoHarvest(level, crop, soil);
						}
					}
				} else if (!client) {
					this.setComparatorLevel(PotMechanics.comparatorWhileGrowing(this.growthTime.get(), required));
				}
			}
		} else if (!client) {
			this.setComparatorLevel(0);
		}

		if (!client && this.potType.isHopper()) {
			if (this.exportDelay.get() > 0.0f) {
				this.exportDelay.tickDown();
			}
			if (this.exportDelay.get() <= 0.0f) {
				this.exportDelay.set(EXPORT_INTERVAL_TICKS);
				this.exportToBelow(level);
			}
		}
	}

	/** Hopper auto-harvest (§B.5/§A.8): roll yields into the storage slots, damage tool, reset growth. */
	private void autoHarvest(final Level level, final CropRecipe crop, final @Nullable SoilRecipe soil) {
		final LiveContext context = new LiveContext(this);
		final RandomSource random = level.getRandom();
		final double totalYield = YieldFormula.totalYield(
			crop.yield(),
			crop.yieldScale(),
			this.yieldModifier(),
			soil != null ? soil.yieldModifier() : 0.0,
			ToolAttributes.sum(this.getHarvestTool(), ModAttributes.YIELD)
		);
		final int rolls = YieldFormula.rolls(random, totalYield);
		for (int roll = 0; roll < rolls; roll++) {
			for (final DropProvider drop : crop.drops()) {
				drop.generateDrops(context, random, this::insertIntoStorage);
			}
		}
		this.damageHarvestTool();
		level.gameEvent(null, GameEvent.BLOCK_CHANGE, this.worldPosition);
		this.growthTime.reset();
		this.setComparatorLevel(0);
		this.setChanged();
		// PF2a: the client renders growth from its own synced GrowthTime; without a block update on
		// harvest it never sees the reset and freezes at mature. Push the reset so the crop visibly
		// re-grows and re-produces each cycle.
		this.syncToClients();
	}

	/** Basic-pot manual harvest (§B.2/§A.8): run drops exactly once, pop to world, reset growth. */
	public boolean harvestManually(final @Nullable Player player) {
		if (this.level == null || this.level.isClientSide()) {
			return false;
		}
		if (!this.isHarvestable()) {
			return false;
		}
		final CropRecipe crop = this.resolveCrop();
		if (crop == null) {
			return false;
		}
		final LiveContext context = new LiveContext(this, player, null);
		final RandomSource random = this.level.getRandom();
		for (final DropProvider drop : crop.drops()) {
			drop.generateDrops(context, random, stack -> Block.popResource(this.level, this.worldPosition.above(), stack));
		}
		this.level.gameEvent(player, GameEvent.BLOCK_CHANGE, this.worldPosition);
		this.growthTime.reset();
		this.growCooldown.reset();
		this.setComparatorLevel(0);
		this.setChanged();
		this.syncToClients();
		return true;
	}

	/**
	 * Force this pot's crop to full maturity (§F.2 debug tooling): set the growth accumulator to the
	 * crop's required ticks and refresh the comparator + emitted light, so display/test pots show a
	 * fully-grown crop and read as harvestable. Server-authoritative for the comparator; a no-op
	 * without a level. Does not roll drops. Reuses {@link #requiredGrowthTicks()}/{@link #updateLightLevel}.
	 */
	public void forceFullGrowth() {
		if (this.level == null) {
			return;
		}
		final CropRecipe crop = this.resolveCrop();
		final SoilRecipe soil = this.resolveSoil();
		final int required = crop != null ? this.requiredGrowthTicks() : 0;
		this.growthTime.set(Math.max(required, 1));
		this.updateLightLevel(this.level, crop, soil);
		if (!this.level.isClientSide()) {
			this.setComparatorLevel(crop != null ? PotMechanics.MATURE_SIGNAL : 0);
		}
		this.setChanged();
		this.syncToClients();
	}

	/**
	 * Roll this pot's crop drop providers once using {@code tool} as the harvest tool for the loot
	 * context (§F.2 debug audit), collecting the produced non-empty stacks. Does not mutate the
	 * world, growth or comparator: the TOOL slot is swapped in only for the duration of the roll and
	 * restored in a {@code finally}. Returns an empty list when there is no server level or no
	 * resolved crop. Reuses the same {@link LiveContext}/{@link DropProvider} path as the live harvest.
	 */
	public List<ItemStack> collectHarvestDrops(final ItemStack tool) {
		if (!(this.level instanceof ServerLevel server)) {
			return List.of();
		}
		final CropRecipe crop = this.resolveCrop();
		if (crop == null) {
			return List.of();
		}
		final ItemStack previousTool = this.items.get(PotMechanics.TOOL);
		this.items.set(PotMechanics.TOOL, tool);
		try {
			final LiveContext context = new LiveContext(this);
			final RandomSource random = server.getRandom();
			final List<ItemStack> drops = new ArrayList<>();
			for (final DropProvider drop : crop.drops()) {
				drop.generateDrops(context, random, stack -> {
					if (!stack.isEmpty()) {
						drops.add(stack);
					}
				});
			}
			return drops;
		} finally {
			this.items.set(PotMechanics.TOOL, previousTool);
		}
	}

	/** Fertilizer application (§A.5): clamped growth, cooldown, effects, consume held (unless creative). */
	public boolean applyFertilizer(final FertilizerRecipe recipe, final @Nullable Player player, final @Nullable InteractionHand hand) {
		if (this.level == null || this.level.isClientSide()) {
			return false;
		}
		if (this.bonemealCooldown > 0) {
			return false;
		}
		final int required = this.requiredGrowthTicks();
		if (!PotMechanics.canFertilize(this.growthTime.get(), required)) {
			return false;
		}
		final int added = recipe.growth().resolve(required, this.level.getRandom());
		this.growthTime.set(PotMechanics.clampFertilizedGrowth(this.growthTime.get(), added, required));
		this.bonemealCooldown = recipe.cooldown();
		// Growth was clamped to at most (required - 20), so it can never reach maturity here (§A.5) —
		// the comparator always reflects the still-growing scale.
		this.setComparatorLevel(PotMechanics.comparatorWhileGrowing(this.growthTime.get(), required));

		if (recipe.spawnParticles()) {
			this.spawnGrowthParticles();
		}
		recipe.soundEffect().ifPresent(sound ->
			this.level.playSound(null, this.worldPosition, sound.sound().value(), sound.category(), sound.volume(), sound.pitch()));
		if (recipe.notifySculk()) {
			this.level.gameEvent(player, GameEvent.BLOCK_CHANGE, this.worldPosition);
		}

		if (player != null && hand != null && !player.getAbilities().instabuild) {
			player.getItemInHand(hand).shrink(1);
		}
		this.setChanged();
		return true;
	}

	/**
	 * Right-click held-item interaction (§B.2 steps 2–3): resolve the held item against the
	 * fertilizer cache first (step 2), then the pot-interaction cache (step 3), applying the first
	 * that both {@code matches} the live context and successfully takes effect. Server-side only
	 * (returns false on the client / a world-less BE, or when nothing applied).
	 */
	public boolean tryHeldInteraction(final Player player, final InteractionHand hand) {
		if (this.level == null || this.level.isClientSide()) {
			return false;
		}
		final ItemStack held = player.getItemInHand(hand);
		if (held.isEmpty()) {
			return false;
		}
		final PotContext context = this.heldContext(held);
		// Resolve the FIRST fully-matching recipe of each kind, not merely the first that cheap-matches the
		// held item. Several recipes can share a held ingredient (every #minecraft:hoes pot-interaction —
		// coarse_dirt/dirt+grass/rooted_dirt); a single-result lookup would return whichever indexed first
		// and, when its soil constraint failed, report no match while a sibling recipe (e.g. dirt→farmland)
		// would have matched. firstMatching confirms each candidate against the live context (R2a fix).
		final FertilizerRecipe fertilizer = PotRecipeCaches.fertilizers(false).firstMatching(held, context, this.level);
		final boolean fertilizerMatches = fertilizer != null;
		final PotInteractionRecipe interaction = PotRecipeCaches.interactions(false).firstMatching(held, context, this.level);
		final boolean interactionMatches = interaction != null;

		// §B.2: fertilizer (step 2) is attempted before pot-interaction (step 3). A matched fertilizer
		// always consumes the click: it either fertilizes, or no-ops, but it never falls through to the
		// empty-hand path (which would open the menu). If the fertilizer no-ops and a pot-interaction
		// also matches, the interaction is still applied as a fallback.
		return switch (PotMechanics.heldItemBranch(this.potType.isWaxed(), fertilizerMatches, interactionMatches)) {
			case FERTILIZE -> {
				boolean applied = this.applyFertilizer(fertilizer, player, hand);
				if (!applied && interactionMatches) {
					applied = this.applyInteraction(interaction, player, hand);
				}
				yield PotMechanics.heldFertilizerConsumesClick(fertilizerMatches);
			}
			case INTERACT -> this.applyInteraction(interaction, player, hand);
			case DEFER, IGNORE -> false;
		};
	}

	/** Apply a matched pot-interaction (§B.6) in world: transform soil/seed, roll drops, effects, consume held. */
	public boolean applyInteraction(final PotInteractionRecipe recipe, final @Nullable Player player, final @Nullable InteractionHand hand) {
		if (this.level == null || this.level.isClientSide()) {
			return false;
		}
		// Resolve the replacement soil/seed and guard against an EMPTY resolution: a new_soil/new_seed whose
		// deferred LazyItemStack fails to decode (e.g. a bad override component) must NOT silently clear the
		// slot — skip the replace and warn instead, so a malformed recipe never destroys the pot's soil/seed
		// (PF1-review hardening).
		recipe.newSoil().ifPresent(newSoil -> this.replaceInputIfResolved(PotMechanics.SOIL, newSoil.get(), "new_soil"));
		recipe.newSeed().ifPresent(newSeed -> this.replaceInputIfResolved(PotMechanics.SEED, newSeed.get(), "new_seed"));

		recipe.extraDrops().ifPresent(tableId -> {
			final LiveContext context = new LiveContext(this, player, hand);
			for (final ItemStack drop : context.rollLootTable(tableId, this.level.getRandom())) {
				Block.popResource(this.level, this.worldPosition.above(), drop);
			}
		});

		recipe.soundEffect().ifPresent(sound ->
			this.level.playSound(null, this.worldPosition, sound.sound().value(), sound.category(), sound.volume(), sound.pitch()));
		if (recipe.notifySculk()) {
			this.level.gameEvent(player, GameEvent.BLOCK_CHANGE, this.worldPosition);
		}

		if (player != null && hand != null && !player.getAbilities().instabuild) {
			switch (PotMechanics.heldConsumption(recipe.damageHeld(), recipe.consumeHeld())) {
				case DAMAGE -> player.getItemInHand(hand).hurtAndBreak(1, player, hand);
				case CONSUME -> player.getItemInHand(hand).shrink(1);
				case NONE -> { }
			}
		}
		this.setChanged();
		return true;
	}

	/**
	 * Replace an input slot only when {@code replacement} resolved to a non-empty stack; an empty
	 * resolution (a new_soil/new_seed whose deferred decode failed) is skipped and logged so a malformed
	 * recipe cannot silently clear the pot's soil/seed (PF1-review hardening).
	 */
	private void replaceInputIfResolved(final int slot, final ItemStack replacement, final String field) {
		if (replacement.isEmpty()) {
			dev.nitjsefnie.cultivated.Cultivated.LOGGER.warn(
				"Pot-interaction {} resolved to an empty stack; keeping the existing slot contents", field);
			return;
		}
		this.replaceInput(slot, replacement);
	}

	/**
	 * Replace an input slot (soil/seed) with {@code replacement} (§B.6). A genuinely DIFFERENT new item
	 * drops the old stack above the pot; a SAME-item replacement is an in-place conversion — the block
	 * inside the pot is converted, not swapped, so the old stack is NOT dropped (R3b: e.g. hoeing dirt →
	 * dirt carrying the {@code cultivated:soil} farmland override must not pop a spurious dirt). The
	 * override on the replacement drives BOTH growth (GrowthFormula soil modifier via computeSoil) and the
	 * farmland render — the full conversion, not merely visual.
	 */
	private void replaceInput(final int slot, final ItemStack replacement) {
		if (this.level == null) {
			return;
		}
		final ItemStack old = this.getItem(slot);
		if (PotMechanics.shouldDropReplacedInput(old, replacement)) {
			Block.popResource(this.level, this.worldPosition.above(), old);
		}
		this.setItem(slot, replacement.copy());
	}

	/** A live match context carrying the interacting player's held item (for soil/seed constraint tests). */
	private PotContext heldContext(final ItemStack held) {
		final boolean serverSide = this.level != null && !this.level.isClientSide();
		return new SimplePotContext(
			this.getItem(PotMechanics.SOIL), this.getItem(PotMechanics.SEED), this.getItem(PotMechanics.TOOL),
			held, this.level, serverSide, this.requiredGrowthTicks()
		);
	}

	/** Spawn the bee-growth (happy-villager) particles above the pot on a successful fertilize (§A.5). */
	private void spawnGrowthParticles() {
		if (!(this.level instanceof ServerLevel server)) {
			return;
		}
		server.sendParticles(
			ParticleTypes.HAPPY_VILLAGER,
			this.worldPosition.getX() + 0.5, this.worldPosition.getY() + 0.6, this.worldPosition.getZ() + 0.5,
			15, 0.35, 0.35, 0.35, 0.0
		);
	}

	/**
	 * Whether growth may advance this tick (R2d). Only a HOPPER pot gates on buffer space: it may not
	 * advance (grow / auto-harvest) while its output buffer is completely full, since the auto-harvest
	 * would have nowhere to put the drops. Basic and waxed pots always return {@code true} — their
	 * harvest paths (manual pop / none) do not depend on buffer space.
	 */
	private boolean canAdvanceGrowth() {
		if (!this.potType.isHopper()) {
			return true;
		}
		return PotMechanics.storageBufferHasRoom(this.storageFreeCapacity());
	}

	/**
	 * Free capacity of each of the 12 storage slots (3..14): a positive value for an empty slot, the
	 * remaining stack space for a non-full stack, or {@code 0} for a full stack. Mirrors the merge
	 * limits used by {@link #insertIntoStorage} so the pause decision agrees with what a harvest could
	 * actually deposit.
	 */
	private int[] storageFreeCapacity() {
		final int[] free = new int[PotMechanics.STORAGE_COUNT];
		for (int i = 0; i < PotMechanics.STORAGE_COUNT; i++) {
			final ItemStack stack = this.items.get(PotMechanics.FIRST_STORAGE + i);
			if (stack.isEmpty()) {
				free[i] = this.getMaxStackSize();
			} else {
				final int max = Math.min(this.getMaxStackSize(stack), stack.getMaxStackSize());
				free[i] = Math.max(0, max - stack.getCount());
			}
		}
		return free;
	}

	/**
	 * Merge a hopper auto-harvest drop into the storage slots (3..14); anything that does not fit is
	 * DISCARDED — voided, never dropped into the world (R3d). This path is hopper-only (called from
	 * {@link #autoHarvest}); the basic-pot manual harvest pops its drops to the world on a separate path
	 * (§B.2). Together with the R2d full-buffer growth pause, an auto-harvesting pot never litters: a
	 * completely full buffer holds at mature without harvesting, and a partially-full buffer keeps what
	 * fits and silently destroys the overflow.
	 */
	private void insertIntoStorage(final ItemStack drop) {
		PotMechanics.fillStorage(this.items, drop, this.getMaxStackSize());
	}

	/** Hopper export (§B.5): push each storage slot into the inventory below, through its up face. */
	private void exportToBelow(final Level level) {
		final BlockPos below = this.worldPosition.below();
		if (level.getBlockState(below).isAir()) {
			return;
		}
		final Container target = HopperBlockEntity.getContainerAt(level, below);
		if (target == null) {
			return;
		}
		boolean changed = false;
		for (int slot = PotMechanics.FIRST_STORAGE; slot <= PotMechanics.LAST_STORAGE; slot++) {
			final ItemStack stack = this.items.get(slot);
			if (stack.isEmpty()) {
				continue;
			}
			final int before = stack.getCount();
			final ItemStack remainder = HopperBlockEntity.addItem(this, target, stack, Direction.UP);
			this.items.set(slot, remainder);
			if (remainder.getCount() != before) {
				changed = true;
			}
		}
		if (changed) {
			this.setChanged();
			target.setChanged();
		}
	}

	private void damageHarvestTool() {
		if (!CultivatedConfig.damageHarvestTool) {
			return;
		}
		final ItemStack tool = this.getItem(PotMechanics.TOOL);
		if (tool.isEmpty() || !tool.isDamageableItem()) {
			return;
		}
		if (EnchantmentLevelHelper.hasAny(tool, ModTags.NEGATE_HARVEST_DAMAGE)) {
			return;
		}
		final int newDamage = tool.getDamageValue() + 1;
		if (newDamage >= tool.getMaxDamage()) {
			this.items.set(PotMechanics.TOOL, ItemStack.EMPTY);
		} else {
			tool.setDamageValue(newDamage);
		}
	}

	// ---- persistence + sync (§B.3) ----

	@Override
	protected void loadAdditional(final ValueInput input) {
		super.loadAdditional(input);
		this.items = NonNullList.withSize(PotMechanics.SIZE, ItemStack.EMPTY);
		ContainerHelper.loadAllItems(input, this.items);
		this.growthTime.set(input.getFloatOr("GrowthTime", 0.0f));
		this.comparatorLevel = input.getIntOr("ComparatorLevel", 0);
		this.growCooldown.set(input.getFloatOr("GrowCooldown", 0.0f));
		this.exportDelay.set(input.getFloatOr("ExportDelay", 0.0f));
		this.bonemealCooldown = input.getIntOr("BonemealCooldown", 0);
		this.cropCache.invalidate();
		this.soilCache.invalidate();
	}

	@Override
	protected void saveAdditional(final ValueOutput output) {
		super.saveAdditional(output);
		ContainerHelper.saveAllItems(output, this.items);
		output.putFloat("GrowthTime", this.growthTime.get());
		output.putInt("ComparatorLevel", this.comparatorLevel);
		output.putFloat("GrowCooldown", this.growCooldown.get());
		output.putFloat("ExportDelay", this.exportDelay.get());
		output.putInt("BonemealCooldown", this.bonemealCooldown);
	}

	@Override
	public @Nullable ClientboundBlockEntityDataPacket getUpdatePacket() {
		return ClientboundBlockEntityDataPacket.create(this);
	}

	@Override
	public CompoundTag getUpdateTag(final HolderLookup.Provider registries) {
		// Sync the input slots (soil/seed/tool) — the output buffer is not needed for rendering, and the
		// comparator level is server-only (§B.3) — plus the growth accumulators. GrowthTime is what the
		// renderer reads for the crop's growth stage, so it MUST be replicated: without it the client
		// free-runs its own growth and never sees an auto-harvest reset, freezing the crop at mature
		// (PF2a). GrowCooldown keeps the post-harvest recheck delay in step so the client does not
		// re-grow a few ticks ahead of the server.
		final TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, registries);
		final NonNullList<ItemStack> syncItems = NonNullList.withSize(PotMechanics.SIZE, ItemStack.EMPTY);
		for (int slot = 0; slot <= PotMechanics.TOOL; slot++) {
			syncItems.set(slot, this.items.get(slot));
		}
		ContainerHelper.saveAllItems(output, syncItems, false);
		output.putFloat("GrowthTime", this.growthTime.get());
		output.putFloat("GrowCooldown", this.growCooldown.get());
		return output.buildResult();
	}

	private void syncToClients() {
		if (this.level != null && !this.level.isClientSide()) {
			this.level.sendBlockUpdated(this.worldPosition, this.getBlockState(), this.getBlockState(), Block.UPDATE_CLIENTS);
		}
	}
}
