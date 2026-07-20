package dev.nitjsefnie.cultivated.block;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.nitjsefnie.cultivated.menu.PotMenuProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/**
 * Phase B §B.1 — the placeable botany pot block, one registered instance per material × pot type
 * ({@link PotMaterials}). Hosts the {@link BotanyPotBlockEntity} from B1 and carries its
 * {@link PotType} so the block entity can derive its behaviour.
 *
 * <p>State: {@code facing} (horizontal, set opposite the placer's look direction), {@code waterlogged},
 * and {@code level} 0–15 which the block emits as light — the block entity keeps {@code level} in
 * sync with {@code max(crop light, soil light)}. Shape is a single {@code (2,0,2)→(14,8,14)} box with
 * a MODEL render shape and no occlusion. Comparator output mirrors the block entity's stored signal.
 *
 * <p>Interaction ({@link #useWithoutItem}) is a minimal server-side stub for now: Basic and Hopper
 * pots will open the pot menu (Task B3) and gain the full harvest/fertilizer/interaction order
 * (Task B4); Waxed pots ignore all interaction. Container contents drop automatically on removal via
 * the block entity's {@code preRemoveSideEffects}; the block itself self-drops through its loot table.
 */
public class BotanyPotBlock extends BaseEntityBlock implements SimpleWaterloggedBlock, PotType.Provider {
	public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
	public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;
	/** Emitted light level (0–15); driven by the block entity from the resolved crop/soil light. */
	public static final IntegerProperty LEVEL = BlockStateProperties.LEVEL;

	private static final Codec<PotType> POT_TYPE_CODEC = Codec.STRING.xmap(PotType::valueOf, PotType::name);

	public static final MapCodec<BotanyPotBlock> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
		POT_TYPE_CODEC.fieldOf("pot_type").forGetter(block -> block.potType),
		propertiesCodec()
	).apply(instance, BotanyPotBlock::new));

	private static final VoxelShape SHAPE = Block.box(2.0, 0.0, 2.0, 14.0, 8.0, 14.0);

	private final PotType potType;

	public BotanyPotBlock(final PotType potType, final BlockBehaviour.Properties properties) {
		super(properties);
		this.potType = potType;
		this.registerDefaultState(this.stateDefinition.any()
			.setValue(FACING, Direction.NORTH)
			.setValue(WATERLOGGED, false)
			.setValue(LEVEL, 0));
	}

	@Override
	public PotType potType() {
		return this.potType;
	}

	@Override
	protected MapCodec<BotanyPotBlock> codec() {
		return CODEC;
	}

	@Override
	protected void createBlockStateDefinition(final StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING, WATERLOGGED, LEVEL);
	}

	// ---- placement / shape / render ----

	@Override
	public BlockState getStateForPlacement(final BlockPlaceContext context) {
		final FluidState replacedFluid = context.getLevel().getFluidState(context.getClickedPos());
		return this.defaultBlockState()
			// Face toward the placer: opposite of the nearest looking direction (§B.1).
			.setValue(FACING, context.getHorizontalDirection().getOpposite())
			.setValue(WATERLOGGED, replacedFluid.is(Fluids.WATER));
	}

	@Override
	protected VoxelShape getShape(final BlockState state, final BlockGetter level, final BlockPos pos, final CollisionContext context) {
		return SHAPE;
	}

	@Override
	protected VoxelShape getCollisionShape(final BlockState state, final BlockGetter level, final BlockPos pos, final CollisionContext context) {
		return SHAPE;
	}

	@Override
	protected RenderShape getRenderShape(final BlockState state) {
		return RenderShape.MODEL;
	}

	// ---- waterlogging ----

	@Override
	protected FluidState getFluidState(final BlockState state) {
		return state.getValue(WATERLOGGED) ? Fluids.WATER.getSource(false) : super.getFluidState(state);
	}

	@Override
	protected BlockState updateShape(
		final BlockState state,
		final LevelReader level,
		final ScheduledTickAccess ticks,
		final BlockPos pos,
		final Direction directionToNeighbour,
		final BlockPos neighbourPos,
		final BlockState neighbourState,
		final RandomSource random
	) {
		if (state.getValue(WATERLOGGED)) {
			ticks.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level));
		}
		return super.updateShape(state, level, ticks, pos, directionToNeighbour, neighbourPos, neighbourState, random);
	}

	/** Skylight passes down only when the pot is not water-filled (§B.1). */
	@Override
	protected boolean propagatesSkylightDown(final BlockState state) {
		return !state.getValue(WATERLOGGED);
	}

	// ---- block entity + ticker ----

	@Override
	public @Nullable BlockEntity newBlockEntity(final BlockPos pos, final BlockState state) {
		return new BotanyPotBlockEntity(pos, state);
	}

	@Override
	public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(
		final Level level, final BlockState state, final BlockEntityType<T> type
	) {
		// Server and client both tick (§B.5); waxed pots short-circuit inside the tick body.
		return createTickerHelper(type, BotanyPotBlockEntity.TYPE, BotanyPotBlockEntity::tick);
	}

	// ---- comparator (§B.3, B1 review carry-over) ----

	@Override
	protected boolean hasAnalogOutputSignal(final BlockState state) {
		return true;
	}

	@Override
	protected int getAnalogOutputSignal(final BlockState state, final Level level, final BlockPos pos, final Direction direction) {
		return level.getBlockEntity(pos) instanceof BotanyPotBlockEntity pot ? pot.getComparatorLevel() : 0;
	}

	// ---- interaction (Task B3 open-menu path; Task B4 layers the full order ahead of it) ----

	@Override
	protected InteractionResult useWithoutItem(
		final BlockState state, final Level level, final BlockPos pos, final Player player, final BlockHitResult hitResult
	) {
		if (this.potType.isWaxed()) {
			return InteractionResult.PASS; // decorative: ignores all interaction (§B.2)
		}
		if (level.isClientSide()) {
			return InteractionResult.SUCCESS;
		}
		// TODO(B4): the full harvest → fertilizer → pot-interaction order is inserted here, ahead of
		// this open-menu fallback (the B4 branches return before reaching openMenu when they handle it).
		return this.openMenu(level, pos, player);
	}

	/**
	 * Open the pot menu for Basic and Hopper pots (§B.7). Kept as a dedicated fallback so Task B4's
	 * interaction order can cleanly fall through to it after its earlier branches. Server-side only
	 * (callers guard {@code level.isClientSide()}); does nothing if the block entity is missing.
	 */
	protected InteractionResult openMenu(final Level level, final BlockPos pos, final Player player) {
		if (level.getBlockEntity(pos) instanceof BotanyPotBlockEntity pot) {
			player.openMenu(new PotMenuProvider(pot));
		}
		return InteractionResult.SUCCESS;
	}

	// ---- removal / rotation ----

	@Override
	protected void affectNeighborsAfterRemoval(final BlockState state, final ServerLevel level, final BlockPos pos, final boolean movedByPiston) {
		// Contents are dropped by BlockEntity#preRemoveSideEffects (the pot is a Container); here we
		// only refresh comparators reading the now-removed pot.
		Containers.updateNeighboursAfterDestroy(state, level, pos);
	}

	@Override
	protected BlockState rotate(final BlockState state, final Rotation rotation) {
		return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
	}

	@Override
	protected BlockState mirror(final BlockState state, final Mirror mirror) {
		return state.rotate(mirror.getRotation(state.getValue(FACING)));
	}
}
