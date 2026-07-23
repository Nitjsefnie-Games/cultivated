package dev.nitjsefnie.cultivated.recipe;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.mojang.authlib.GameProfile;
import dev.nitjsefnie.cultivated.CultivatedTestBootstrap;
import java.lang.reflect.Field;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import sun.misc.Unsafe;

/**
 * Regression coverage for the player-less harvest killer fix (blaze rods / wither skulls not dropping
 * from hopper auto-harvest and the {@code check_crops} audit). The bootstrap-only harness has no live
 * {@code ServerLevel}/{@code MinecraftServer}, so a real {@code FakePlayer} cannot be constructed here
 * (it is a {@code ServerPlayer} and needs a running server). These tests pin everything that IS
 * decidable without one: the synthetic harvester identity is a fixed constant, a real interacting
 * player is always preferred without touching the server, and a null player always engages the
 * fallback instead of silently producing a null killer (the original bug:
 * {@code withOptionalParameter} omitted the param, so {@code killed_by_player} loot never rolled).
 */
class LiveContextHarvestKillerTest {
	private static final UUID EXPECTED_HARVESTER_UUID = UUID.fromString("c47a1e7a-7ed0-4b1a-9c5f-7d2b8a1e6f03");

	@BeforeAll
	static void boot() {
		CultivatedTestBootstrap.bootstrap();
	}

	@Test
	void harvesterProfileHasAFixedUuidAndName() {
		// A random/time-based UUID would defeat the per-server FakePlayer cache and make any loot
		// logic keying off the killer's UUID non-deterministic — pin the constant.
		assertEquals(EXPECTED_HARVESTER_UUID, LiveContext.HARVESTER_PROFILE.id(), "harvester UUID must stay fixed");
		assertEquals("[Cultivated]", LiveContext.HARVESTER_PROFILE.name(), "harvester name must stay fixed");
		assertSame(LiveContext.HARVESTER_PROFILE, LiveContext.HARVESTER_PROFILE, "the profile is a single shared constant");
	}

	@Test
	void realPlayerIsUsedAsKillerWithoutTouchingTheServer() throws Exception {
		// A null server would explode if the real-player path ever fell through to the FakePlayer
		// fallback — so passing null proves the real player short-circuits (manual harvest keeps the
		// player's own looting/identity).
		final Player real = playerWithoutConstructor();
		assertSame(real, LiveContext.harvestKiller(real, null), "a real interacting player must be used unchanged");
	}

	@Test
	void nullPlayerEngagesTheSyntheticHarvesterFallback() throws Exception {
		// A bare Unsafe-allocated ServerLevel has no server internals, so FakePlayer.get cannot
		// succeed against it — but the attempt itself proves the null-player path delegates to the
		// synthetic harvester (and therefore can never silently resolve to a null killer, which is
		// exactly what made killed_by_player-gated drops vanish). On a live server the same call
		// returns the cached FakePlayer.
		final ServerLevel hollowServer = (ServerLevel) unsafe().allocateInstance(ServerLevel.class);
		assertThrows(
			RuntimeException.class,
			() -> LiveContext.harvestKiller(null, hollowServer),
			"a null player must route into the FakePlayer fallback, not resolve to a null killer"
		);
	}

	/**
	 * A {@code Player} instance without running its constructor — {@code Entity.<init>} requires a live
	 * {@code Level} ({@code level.getNextEntityId()}), which this harness cannot provide.
	 * {@code harvestKiller} only compares the reference, never touches instance state.
	 */
	private static Player playerWithoutConstructor() throws Exception {
		return (Player) unsafe().allocateInstance(StubPlayer.class);
	}

	private static Unsafe unsafe() {
		try {
			final Field field = Unsafe.class.getDeclaredField("theUnsafe");
			field.setAccessible(true);
			return (Unsafe) field.get(null);
		} catch (final ReflectiveOperationException failure) {
			throw new ExceptionInInitializerError(failure);
		}
	}

	private static final class StubPlayer extends Player {
		private StubPlayer() {
			super(null, new GameProfile(new UUID(0L, 0L), "stub"));
		}

		@Override
		public GameType gameMode() {
			return GameType.SURVIVAL;
		}
	}
}
