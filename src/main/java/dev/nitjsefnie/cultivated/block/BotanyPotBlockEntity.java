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
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
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
 * component first, then the sided recipe cache), grows on a tick-rate-normalised accumulator,
 * drives its comparator output, auto-harvests + exports (hopper pots) and holds at mature (basic
 * pots). Waxed pots are decorative and force growth to max without ticking.
 *
 * <p>The block, block-entity-type registration and ticker wiring are Task B2; this class references
 * its own type lazily through the settable {@link #TYPE} holder that B2 assigns, so it compiles and
 * its pure logic is unit-testable now (see {@link PotMechanics}).
 */
public class BotanyPotBlockEntity extends BlockEntity implements WorldlyContainer {
	/**
	 * Assigned by Task B2 when it registers {@code cultivated:botany_pot} over the pot blocks. The
	 * constructor reads it lazily, so this class links and its static logic tests without a live
	 * registry; constructing an instance before B2 assigns this throws a clear error.
	 */
	public static @Nullable BlockEntityType<BotanyPotBlockEntity> TYPE;

	/** Ticks between hopper-export attempts (inferred — the source's exact cadence was not visible). */
	private static final float EXPORT_INTERVAL_TICKS = 8.0f;
	/** Re-check maturity every 5 ticks once a crop is mature (§B.5). */
	private static final float MATURE_RECHECK_TICKS = 5.0f;

	private final PotType potType;
	private NonNullList<ItemStack> items = NonNullList.withSize(PotMechanics.SIZE, ItemStack.EMPTY);
	private final TickAccumulator growthTime = new TickAccumulator();
	private final TickAccumulator growCooldown = new TickAccumulator();
	private final TickAccumulator exportDelay = new TickAccumulator();
	private int comparatorLevel;
	private int bonemealCooldown;

	private final ReloadableCache<CropRecipe> cropCache = ReloadableCache.of(this::computeCrop);
	private final ReloadableCache<SoilRecipe> soilCache = ReloadableCache.of(this::computeSoil);

	public BotanyPotBlockEntity(final BlockPos pos, final BlockState state) {
		super(requireType(), pos, state);
		this.potType = state.getBlock() instanceof PotType.Provider provider ? provider.potType() : PotType.BASIC;
	}

	private static BlockEntityType<BotanyPotBlockEntity> requireType() {
		if (TYPE == null) {
			throw new IllegalStateException("BotanyPotBlockEntity.TYPE not registered yet (assigned in Task B2)");
		}
		return TYPE;
	}

	public PotType getPotType() {
		return this.potType;
	}

	// ---- tiers hooks (0 for base pots; overridden by tiered pots later, §D) ----

	protected double growthModifier() {
		return 0.0;
	}

	protected double yieldModifier() {
		return 0.0;
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
		stack.limitSize(this.getMaxStackSize(stack));
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
		if (cached != null && this.level != null && !cached.matches(this.matchContext(), this.level)) {
			this.cropCache.invalidate();
			cached = this.cropCache.get();
		}
		return cached;
	}

	/** The pot's resolved soil (override component first, then the sided recipe cache), or null. */
	public @Nullable SoilRecipe resolveSoil() {
		SoilRecipe cached = this.soilCache.get();
		if (cached != null && this.level != null && !cached.matches(this.matchContext(), this.level)) {
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
		final CropRecipe candidate = PotRecipeCaches.crops(this.level.isClientSide()).lookup(seed, this.matchContext());
		return candidate != null && candidate.matches(this.matchContext(), this.level) ? candidate : null;
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
	private int computeLightLevel() {
		int light = 0;
		final CropRecipe crop = this.resolveCrop();
		if (crop != null) {
			light = Math.max(light, crop.lightLevel());
		}
		final SoilRecipe soil = this.resolveSoil();
		if (soil != null) {
			light = Math.max(light, soil.lightLevel());
		}
		return Math.max(0, Math.min(15, light));
	}

	/** Keep the block's {@code level} state (which drives light emission) in sync with the crop/soil. */
	private void updateLightLevel(final Level level) {
		if (level.isClientSide()) {
			return; // block state is server-authoritative and replicated to clients
		}
		final BlockState state = this.getBlockState();
		if (!state.hasProperty(BotanyPotBlock.LEVEL)) {
			return;
		}
		final int desired = this.computeLightLevel();
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
		final float rate = level.tickRateManager().tickrate();

		// Keep the emitted-light block state aligned with the resolved crop/soil (§B.1), for every
		// pot type — waxed pots also light from their decorative full-grown crop.
		this.updateLightLevel(level);

		if (this.potType.isWaxed()) {
			// Decorative: force growth to "max" so the renderer shows a full-grown crop; never harvest.
			final CropRecipe crop = this.resolveCrop();
			final int required = crop != null ? this.requiredGrowthTicks() : 0;
			this.growthTime.set(Math.max(required, 1));
			return;
		}

		if (this.bonemealCooldown > 0) {
			this.bonemealCooldown--;
		}

		// Resolve soil (its per-tick hook, if any, would run here — soils carry none in the data model).
		final SoilRecipe soil = this.resolveSoil();
		final CropRecipe crop = this.resolveCrop();

		if (crop != null) {
			if (this.growCooldown.get() > 0.0f) {
				this.growCooldown.tickDown(rate);
			}
			final boolean sustained = crop.acceptsSoil(this.getItem(PotMechanics.SOIL));
			if (this.growCooldown.get() <= 0.0f && sustained) {
				this.growthTime.tickUp(rate);
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
				this.exportDelay.tickDown(rate);
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
		this.setComparatorLevel(this.growthTime.get() >= required
			? PotMechanics.MATURE_SIGNAL
			: PotMechanics.comparatorWhileGrowing(this.growthTime.get(), required));

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

	/** Merge a drop into the storage slots (3..14); anything that does not fit pops above the pot. */
	private void insertIntoStorage(final ItemStack drop) {
		ItemStack remaining = drop;
		for (int slot = PotMechanics.FIRST_STORAGE; slot <= PotMechanics.LAST_STORAGE && !remaining.isEmpty(); slot++) {
			final ItemStack current = this.items.get(slot);
			if (current.isEmpty()) {
				final int move = Math.min(remaining.getCount(), this.getMaxStackSize(remaining));
				this.items.set(slot, remaining.split(move));
			} else if (ItemStack.isSameItemSameComponents(current, remaining)) {
				final int space = Math.min(this.getMaxStackSize(current), current.getMaxStackSize()) - current.getCount();
				if (space > 0) {
					final int move = Math.min(space, remaining.getCount());
					current.grow(move);
					remaining.shrink(move);
				}
			}
		}
		if (!remaining.isEmpty() && this.level != null) {
			Block.popResource(this.level, this.worldPosition.above(), remaining);
		}
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
		// Sync only the input slots (soil/seed/tool) — the output buffer is not needed for rendering,
		// and the comparator level is server-only (§B.3).
		final TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, registries);
		final NonNullList<ItemStack> syncItems = NonNullList.withSize(PotMechanics.SIZE, ItemStack.EMPTY);
		for (int slot = 0; slot <= PotMechanics.TOOL; slot++) {
			syncItems.set(slot, this.items.get(slot));
		}
		ContainerHelper.saveAllItems(output, syncItems, false);
		return output.buildResult();
	}

	private void syncToClients() {
		if (this.level != null && !this.level.isClientSide()) {
			this.level.sendBlockUpdated(this.worldPosition, this.getBlockState(), this.getBlockState(), Block.UPDATE_CLIENTS);
		}
	}
}
