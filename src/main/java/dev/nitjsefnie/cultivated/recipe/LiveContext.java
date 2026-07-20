package dev.nitjsefnie.cultivated.recipe;

import dev.nitjsefnie.cultivated.block.BotanyPotBlockEntity;
import dev.nitjsefnie.cultivated.block.PotMechanics;
import java.util.List;
import net.minecraft.core.BlockPos;
import java.util.Optional;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntitySpawnRequest;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Phase B §B.4 — the live {@link PotContext} wrapping a real {@link BotanyPotBlockEntity} (plus an
 * optional interacting player + hand). It exposes the pot's slots, resolved crop/soil and required
 * growth ticks, and — on the logical server — builds {@code LootParams} and rolls the crop's drop
 * providers (§A.6) and runs the crop's mcfunction (§B.3). On a client level the loot/function
 * methods return empty / no-op, so a shared drop provider stays side-safe.
 */
public final class LiveContext implements PotContext {
	private final BotanyPotBlockEntity pot;
	private final @Nullable Player player;
	private final @Nullable InteractionHand hand;

	public LiveContext(final BotanyPotBlockEntity pot) {
		this(pot, null, null);
	}

	public LiveContext(final BotanyPotBlockEntity pot, final @Nullable Player player, final @Nullable InteractionHand hand) {
		this.pot = pot;
		this.player = player;
		this.hand = hand;
	}

	@Override
	public ItemStack getSoilStack() {
		return this.pot.getItem(PotMechanics.SOIL);
	}

	@Override
	public ItemStack getSeedStack() {
		return this.pot.getItem(PotMechanics.SEED);
	}

	@Override
	public ItemStack getToolStack() {
		return this.pot.getItem(PotMechanics.TOOL);
	}

	@Override
	public ItemStack getHeldItem() {
		return this.player != null && this.hand != null ? this.player.getItemInHand(this.hand) : ItemStack.EMPTY;
	}

	@Override
	@Nullable
	public Level getLevel() {
		return this.pot.getLevel();
	}

	@Override
	public boolean isServerSide() {
		return this.pot.getLevel() instanceof ServerLevel;
	}

	@Override
	public int getRequiredGrowthTicks() {
		return this.pot.requiredGrowthTicks();
	}

	@Override
	@Nullable
	public BlockPos getPotPos() {
		return this.pot.getBlockPos();
	}

	@Override
	public BlockState getPotState() {
		return this.pot.getBlockState();
	}

	@Override
	@Nullable
	public CropRecipe getCrop() {
		return this.pot.resolveCrop();
	}

	@Override
	@Nullable
	public SoilRecipe getSoil() {
		return this.pot.resolveSoil();
	}

	@Override
	public ItemStack getHarvestTool() {
		return this.pot.getHarvestTool();
	}

	@Override
	public List<ItemStack> rollBlockDrops(final BlockState dropState, final RandomSource random) {
		if (!(this.pot.getLevel() instanceof ServerLevel server)) {
			return List.of();
		}
		final Optional<ResourceKey<LootTable>> key = dropState.getBlock().getLootTable();
		if (key.isEmpty()) {
			return List.of();
		}
		final LootTable table = server.getServer().reloadableRegistries().getLootTable(key.get());
		return table.getRandomItems(this.blockLootParams(server, dropState), random);
	}

	@Override
	public List<ItemStack> rollLootTable(final Identifier tableId, final RandomSource random) {
		if (!(this.pot.getLevel() instanceof ServerLevel server)) {
			return List.of();
		}
		final ResourceKey<LootTable> key = ResourceKey.create(Registries.LOOT_TABLE, tableId);
		final LootTable table = server.getServer().reloadableRegistries().getLootTable(key);
		return table.getRandomItems(this.blockLootParams(server, this.pot.getBlockState()), random);
	}

	@Override
	public List<ItemStack> rollEntityDrops(final CompoundTag entity, final Optional<Identifier> damageSource, final RandomSource random) {
		if (!(this.pot.getLevel() instanceof ServerLevel server)) {
			return List.of();
		}
		try {
			final Entity created = EntityType.loadEntityRecursive(
				entity, server, new EntitySpawnRequest(EntitySpawnReason.COMMAND, false), e -> e
			);
			if (created == null) {
				return List.of();
			}
			final Optional<ResourceKey<LootTable>> key = created.getLootTable();
			if (key.isEmpty()) {
				return List.of();
			}
			final LootTable table = server.getServer().reloadableRegistries().getLootTable(key.get());
			final LootParams params = new LootParams.Builder(server)
				.withParameter(LootContextParams.THIS_ENTITY, created)
				.withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(this.pot.getBlockPos()))
				.withParameter(LootContextParams.DAMAGE_SOURCE, server.damageSources().generic())
				.withOptionalParameter(LootContextParams.LAST_DAMAGE_PLAYER, this.player)
				.withOptionalParameter(LootContextParams.ATTACKING_ENTITY, this.player)
				.withOptionalParameter(LootContextParams.DIRECT_ATTACKING_ENTITY, this.player)
				.create(LootContextParamSets.ENTITY);
			return table.getRandomItems(params, random);
		} catch (final RuntimeException failure) {
			return List.of();
		}
	}

	@Override
	public void runFunction(final Identifier functionId) {
		if (!(this.pot.getLevel() instanceof ServerLevel server)) {
			return;
		}
		final MinecraftServer minecraftServer = server.getServer();
		minecraftServer.getFunctions().get(functionId).ifPresent(function -> {
			final var source = minecraftServer.createCommandSourceStack()
				.withLevel(server)
				.withPosition(Vec3.atCenterOf(this.pot.getBlockPos()))
				.withSuppressedOutput();
			minecraftServer.getFunctions().execute(function, source);
		});
	}

	private LootParams blockLootParams(final ServerLevel server, final BlockState state) {
		return new LootParams.Builder(server)
			.withParameter(LootContextParams.BLOCK_STATE, state)
			.withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(this.pot.getBlockPos()))
			.withParameter(LootContextParams.TOOL, this.getHarvestTool())
			.withOptionalParameter(LootContextParams.BLOCK_ENTITY, this.pot)
			.withOptionalParameter(LootContextParams.THIS_ENTITY, this.player)
			.create(LootContextParamSets.BLOCK);
	}
}
