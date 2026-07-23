package dev.nitjsefnie.cultivated.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.nitjsefnie.cultivated.CultivatedTestBootstrap;
import dev.nitjsefnie.cultivated.block.GeneratedItems;
import io.netty.buffer.Unpooled;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Chunk 2 — StreamCodec round-trips for the three generated-items payloads. Encoding and decoding
 * must be lossless (position, index, entry order, normalized stacks and suppression flags), since
 * the server answers every accepted toggle with a full re-sync the screen renders verbatim.
 */
class PayloadStreamCodecTest {
	private static RegistryAccess registryAccess;

	@BeforeAll
	static void boot() {
		CultivatedTestBootstrap.bootstrap();
		registryAccess = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
	}

	private static RegistryFriendlyByteBuf buffer() {
		return new RegistryFriendlyByteBuf(Unpooled.buffer(), registryAccess);
	}

	@Test
	void requestRoundTrip() {
		final RequestGeneratedItemsPayload payload = new RequestGeneratedItemsPayload(new BlockPos(-12, 64, 340));
		final RegistryFriendlyByteBuf buf = buffer();
		RequestGeneratedItemsPayload.CODEC.encode(buf, payload);
		assertEquals(payload, RequestGeneratedItemsPayload.CODEC.decode(buf));
	}

	@Test
	void toggleRoundTrip() {
		final ToggleGeneratedItemPayload payload = new ToggleGeneratedItemPayload(new BlockPos(1, 2, 3), 7);
		final RegistryFriendlyByteBuf buf = buffer();
		ToggleGeneratedItemPayload.CODEC.encode(buf, payload);
		assertEquals(payload, ToggleGeneratedItemPayload.CODEC.decode(buf));
	}

	@Test
	void syncRoundTripPreservesOrderStacksAndFlags() {
		final ItemStack wheat = GeneratedItems.normalize(new ItemStack(Items.WHEAT, 16));
		final ItemStack sword = GeneratedItems.normalize(new ItemStack(Items.DIAMOND_SWORD));
		final GeneratedItemsSyncPayload payload = new GeneratedItemsSyncPayload(
			new BlockPos(8, 70, -40),
			List.of(
				new GeneratedItemsSyncPayload.Entry(wheat, false),
				new GeneratedItemsSyncPayload.Entry(sword, true)
			)
		);
		final RegistryFriendlyByteBuf buf = buffer();
		GeneratedItemsSyncPayload.CODEC.encode(buf, payload);
		final GeneratedItemsSyncPayload decoded = GeneratedItemsSyncPayload.CODEC.decode(buf);

		assertEquals(payload.pos(), decoded.pos());
		assertEquals(2, decoded.entries().size());
		for (int i = 0; i < payload.entries().size(); i++) {
			assertTrue(
				ItemStack.matches(payload.entries().get(i).stack(), decoded.entries().get(i).stack()),
				"entry " + i + " stack must survive the round-trip");
			assertEquals(payload.entries().get(i).suppressed(), decoded.entries().get(i).suppressed());
		}
	}

	@Test
	void syncRoundTripWithEmptyList() {
		final GeneratedItemsSyncPayload payload = new GeneratedItemsSyncPayload(BlockPos.ZERO, List.of());
		final RegistryFriendlyByteBuf buf = buffer();
		GeneratedItemsSyncPayload.CODEC.encode(buf, payload);
		final GeneratedItemsSyncPayload decoded = GeneratedItemsSyncPayload.CODEC.decode(buf);
		assertEquals(BlockPos.ZERO, decoded.pos());
		assertTrue(decoded.entries().isEmpty());
	}
}
