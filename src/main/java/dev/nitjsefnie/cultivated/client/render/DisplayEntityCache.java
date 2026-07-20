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
 * client {@link Level} changes (relog / dimension change) so stale level-bound entities are dropped;
 * {@link #invalidate()} clears it on a resource reload.
 */
@Environment(EnvType.CLIENT)
public final class DisplayEntityCache {
	private final Map<CompoundTag, Entity> byNbt = new HashMap<>();
	private @Nullable Level level;

	/** The cache key for an {@code entity} display: its NBT tag (value equality over the whole tag). */
	public static CompoundTag cacheKey(final Display.Entity display) {
		return display.entity();
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
		Entity entity = this.byNbt.get(key);
		if (entity == null) {
			entity = build(key, level);
			if (entity != null) {
				this.byNbt.put(key, entity);
			}
		}
		return entity;
	}

	/** Drop all cached entities (call on resource reload). */
	public void invalidate() {
		this.byNbt.clear();
		this.level = null;
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
}
