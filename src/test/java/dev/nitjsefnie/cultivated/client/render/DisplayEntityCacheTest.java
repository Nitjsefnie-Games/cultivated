package dev.nitjsefnie.cultivated.client.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import dev.nitjsefnie.cultivated.data.display.Display;
import dev.nitjsefnie.cultivated.data.display.RenderOptions.Vec3f;
import java.util.Optional;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

/**
 * The entity-display cache key (§C.5): distinct entity NBT must key distinct cached entities, and two
 * displays with equal NBT must collapse to the same key so an entity is built once and reused. Only
 * the {@code entity} tag participates — the spin/scale/tick fields are render-time transforms, not
 * part of the built entity.
 */
class DisplayEntityCacheTest {

	@Test
	void cacheKeyIsTheEntityNbtTag() {
		final CompoundTag tag = pigTag();
		final Display.Entity display = entity(tag, 1.5f);
		assertEquals(tag, DisplayEntityCache.cacheKey(display));
	}

	@Test
	void equalNbtProducesEqualKeysAcrossDifferentTransforms() {
		final Display.Entity a = entity(pigTag(), 0.0f);
		final Display.Entity b = entity(pigTag(), 3.0f);
		assertEquals(DisplayEntityCache.cacheKey(a), DisplayEntityCache.cacheKey(b));
	}

	@Test
	void differentNbtProducesDifferentKeys() {
		final Display.Entity pig = entity(pigTag(), 0.0f);
		final CompoundTag cowTag = new CompoundTag();
		cowTag.putString("id", "minecraft:cow");
		final Display.Entity cow = entity(cowTag, 0.0f);
		assertNotEquals(DisplayEntityCache.cacheKey(pig), DisplayEntityCache.cacheKey(cow));
	}

	private static CompoundTag pigTag() {
		final CompoundTag tag = new CompoundTag();
		tag.putString("id", "minecraft:pig");
		return tag;
	}

	private static Display.Entity entity(final CompoundTag tag, final float spinSpeed) {
		return new Display.Entity(tag, true, spinSpeed, new Vec3f(0.5f, 0.5f, 0.5f), Optional.empty());
	}
}
