package dev.nitjsefnie.cultivated.recipe;

import com.mojang.authlib.GameProfile;
import dev.nitjsefnie.cultivated.block.BotanyPotBlockEntity;
import dev.nitjsefnie.cultivated.block.PotMechanics;
import dev.nitjsefnie.cultivated.config.CultivatedConfig;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.minecraft.core.BlockPos;
import java.util.Optional;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntitySpawnRequest;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.DropChances;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentEffectComponents;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
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
	/**
	 * Stable identity of the synthetic harvester credited with the kill when no real player triggered
	 * the harvest (hopper auto-harvest, {@code check_crops} audit). The UUID is a fixed constant so the
	 * per-server {@link FakePlayer} cache key — and any loot logic reading the killer's UUID — is
	 * deterministic across restarts.
	 */
	static final GameProfile HARVESTER_PROFILE =
		new GameProfile(UUID.fromString("c47a1e7a-7ed0-4b1a-9c5f-7d2b8a1e6f03"), "[Cultivated]");

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
		// When the pot's TOOL slot is empty, fall back to the server-configured default harvest stack
		// (§A.6) so a server can e.g. set silk-touch shears to make every pot yield silk-touch drops.
		// If that config stack is also empty, behave as today (no tool).
		final ItemStack tool = this.pot.getHarvestTool();
		if (!tool.isEmpty()) {
			return tool;
		}
		return CultivatedConfig.defaultHarvestStack();
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
	public List<ItemStack> rollEntityDrops(
		final CompoundTag entity, final Optional<Identifier> damageSource, final boolean finalizeSpawn, final RandomSource random
	) {
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
			final Vec3 origin = Vec3.atCenterOf(this.pot.getBlockPos());
			// Position the throwaway mob at the pot so any position-dependent finalize/equipment logic reads
			// a sane location (its NBT would otherwise place it at the world origin).
			created.snapTo(origin.x, origin.y, origin.z, created.getYRot(), created.getXRot());
			// Vanilla-faithful equipment: run the mob through its OWN finalizeSpawn (small chance armored/
			// equipped), generically for vanilla and modded mobs — the mob's own logic decides its gear.
			if (finalizeSpawn && created instanceof Mob mob) {
				finalizeMob(server, mob, this.pot.getBlockPos());
			}

			final List<ItemStack> drops = new ArrayList<>();
			// 1) the entity's main death loot table (the existing behaviour).
			final Optional<ResourceKey<LootTable>> key = created.getLootTable();
			if (key.isPresent()) {
				final LootTable table = server.getServer().reloadableRegistries().getLootTable(key.get());
				// Killer attribution: never omit these params - withOptionalParameter drops a null
				// value, silently failing killed_by_player-gated loot (blaze rods, wither skulls)
				// on player-less harvests, so resolve a non-null killer first.
				final Player killer = harvestKiller(this.player, server);
				final LootParams params = new LootParams.Builder(server)
					.withParameter(LootContextParams.THIS_ENTITY, created)
					.withParameter(LootContextParams.ORIGIN, origin)
					.withParameter(LootContextParams.DAMAGE_SOURCE, server.damageSources().generic())
					.withParameter(LootContextParams.LAST_DAMAGE_PLAYER, killer)
					.withParameter(LootContextParams.ATTACKING_ENTITY, killer)
					.withParameter(LootContextParams.DIRECT_ATTACKING_ENTITY, killer)
					.create(LootContextParamSets.ENTITY);
				drops.addAll(table.getRandomItems(params, random));
			}
			// 2) worn equipment on the vanilla small per-piece chance (only meaningful once finalized).
			if (finalizeSpawn && created instanceof Mob mob) {
				collectEquipmentDrops(mob, random, drops);
			}
			return drops;
		} catch (final RuntimeException failure) {
			return List.of();
		}
	}

	/**
	 * The killer credited for entity death loot: the interacting player when one is present (a real
	 * player's looting enchant and identity still apply — manual Basic-pot harvest is unchanged), else
	 * a per-server cached {@link FakePlayer} under {@link #HARVESTER_PROFILE}. Player-kill-gated loot
	 * conditions ({@code minecraft:killed_by_player} — blaze rods, wither skeleton skulls, …) require
	 * a NON-NULL {@code LAST_DAMAGE_PLAYER}; {@code LootParams.Builder.withOptionalParameter} silently
	 * omits a null value, which is exactly what broke hopper auto-harvest and the {@code check_crops}
	 * audit path (both build a player-less {@code LiveContext}).
	 */
	static Player harvestKiller(final @Nullable Player player, final ServerLevel server) {
		return player != null ? player : FakePlayer.get(server, HARVESTER_PROFILE);
	}

	/** Run a detached mob through its own spawn-finalize so it has the vanilla small chance of equipment. */
	private static void finalizeMob(final ServerLevel server, final Mob mob, final BlockPos pos) {
		try {
			final DifficultyInstance difficulty = server.getCurrentDifficultyAt(pos);
			mob.finalizeSpawn(server, difficulty, EntitySpawnReason.SPAWNER, null);
		} catch (final RuntimeException failure) {
			// A mob whose finalize misbehaves off a detached spawn is left unequipped rather than failing
			// the whole harvest — the death loot table still rolls.
		}
	}

	/**
	 * Yield a finalized mob's worn gear on the vanilla small per-piece drop chance — a mirror of
	 * {@code Mob.dropCustomDeathLoot}'s equipment loop, driven entirely by the mob's OWN {@code DropChances}
	 * (set by its own finalizeSpawn), so an armored zombie has the same small chance to drop its armor and
	 * this generalizes to any modded mob without hardcoding a single item id.
	 */
	private static void collectEquipmentDrops(final Mob mob, final RandomSource random, final List<ItemStack> out) {
		final DropChances dropChances = mob.getDropChances();
		for (final EquipmentSlot slot : EquipmentSlot.VALUES) {
			final ItemStack item = mob.getItemBySlot(slot);
			if (item.isEmpty()) {
				continue;
			}
			final float chance = dropChances.byEquipment(slot);
			if (chance <= 0.0f) {
				continue;
			}
			if (EnchantmentHelper.has(item, EnchantmentEffectComponents.PREVENT_EQUIPMENT_DROP)) {
				continue;
			}
			if (random.nextFloat() < chance) {
				final ItemStack dropped = item.copy();
				if (!dropChances.isPreserved(slot) && dropped.isDamageableItem()) {
					dropped.setDamageValue(
						dropped.getMaxDamage() - random.nextInt(1 + random.nextInt(Math.max(dropped.getMaxDamage() - 3, 1)))
					);
				}
				out.add(dropped);
			}
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
