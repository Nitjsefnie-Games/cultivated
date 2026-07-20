package dev.nitjsefnie.cultivated.client.render;

import dev.nitjsefnie.cultivated.Cultivated;
import dev.nitjsefnie.cultivated.data.display.Display;
import java.util.HashMap;
import java.util.Map;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.EntityProcessor;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntitySpawnRequest;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

/**
 * Phase C §C.5 — a small, reload-aware cache of display entities built from NBT. Building an entity
 * from NBT ({@link EntityType#loadEntityRecursive}) is expensive, so each distinct {@code entity} tag
 * is built once and reused across frames. The cache is keyed by the NBT tag and rebuilt when the
 * client {@link Level} changes (relog / dimension change) so stale level-bound entities are dropped.
 * It needs no explicit resource-reload hook: the block-entity render dispatcher recreates renderers
 * (and this per-renderer cache with them) on a client resource reload, so cached entities rebuild then.
 *
 * <p>Each entry also records the last game time it was ticked so a shared entity's animation advances
 * at most once per game tick — regardless of how many pots reference it or the framerate (§C.5).
 */
@Environment(EnvType.CLIENT)
public final class DisplayEntityCache {
	/** Sentinel for "never ticked": any real game time is greater, so the first tick always runs. */
	private static final long NEVER_TICKED = Long.MIN_VALUE;

	private final Map<CompoundTag, Entry> byNbt = new HashMap<>();
	private @Nullable Level level;

	/** The cache key for an {@code entity} display: its NBT tag (value equality over the whole tag). */
	public static CompoundTag cacheKey(final Display.Entity display) {
		return display.entity();
	}

	/**
	 * The pure tick-once-per-game-time decision (§C.5): a display entity due for animation should advance
	 * only when game time has advanced past its last tick — so multiple pots and multiple frames within a
	 * single game tick do not re-advance it.
	 */
	public static boolean shouldTick(final long lastTickedGameTime, final long currentGameTime) {
		return currentGameTime > lastTickedGameTime;
	}

	/**
	 * The cached entity for {@code display}, built against {@code level} on first use (or after a level
	 * change). Returns {@code null} if the NBT could not be resolved into an entity.
	 */
	public @Nullable Entity get(final Display.Entity display, final Level level) {
		if (this.level != level) {
			this.byNbt.clear();
			this.level = level;
		}
		final CompoundTag key = cacheKey(display);
		Entry entry = this.byNbt.get(key);
		if (entry == null) {
			final Entity built = build(key, level);
			if (built == null) {
				return null;
			}
			entry = new Entry(built);
			this.byNbt.put(key, entry);
		}
		return entry.entity;
	}

	/**
	 * Whether {@code display}'s cached entity should advance its animation at {@code gameTime}, recording
	 * the tick when it should. Returns {@code true} at most once per game tick per cached entity, so N
	 * pots sharing one entity — and multiple frames within a tick — advance it only once (§C.5). Returns
	 * {@code false} for an entity that is not (yet) cached.
	 */
	public boolean markTickedIfDue(final Display.Entity display, final long gameTime) {
		final Entry entry = this.byNbt.get(cacheKey(display));
		if (entry == null || !shouldTick(entry.lastTickedGameTime, gameTime)) {
			return false;
		}
		entry.lastTickedGameTime = gameTime;
		return true;
	}

	private static @Nullable Entity build(final CompoundTag tag, final Level level) {
		try {
			return EntityType.loadEntityRecursive(
				tag.copy(), level, new EntitySpawnRequest(EntitySpawnReason.LOAD, true), EntityProcessor.NOP
			);
		} catch (final Exception e) {
			Cultivated.LOGGER.warn("Failed to build display entity from NBT {}", tag, e);
			return null;
		}
	}

	/** A cached entity plus the game time it was last ticked (per-NBT animation state). */
	private static final class Entry {
		private final Entity entity;
		private long lastTickedGameTime = NEVER_TICKED;

		private Entry(final Entity entity) {
			this.entity = entity;
		}
	}
}
