package dev.nitjsefnie.cultivated.network;

import dev.nitjsefnie.cultivated.block.BotanyPotBlockEntity;
import dev.nitjsefnie.cultivated.block.GeneratedItems;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jspecify.annotations.Nullable;

/**
 * Chunk 2 — payload type registration and the server-side receivers for the generated-items
 * feature. All three payload types are registered from common init so both sides can encode/decode
 * them; the S2C client receiver lives in {@code CultivatedClient}. Every receiver treats the packet
 * as untrusted: it re-resolves the pot at the claimed position, requires a hopper pot within reach
 * of the sender and bounds-checks the row index. Anything invalid is ignored — a malformed packet
 * must never crash the server.
 */
public final class ModNetworking {
	/** How far (in blocks) a player may be from the pot to query or toggle its generated items. */
	private static final double MAX_DISTANCE = 8.0;

	private ModNetworking() {
	}

	/** Register all three payload types plus the two serverbound receivers. Called from common init. */
	public static void register() {
		PayloadTypeRegistry.serverboundPlay().register(RequestGeneratedItemsPayload.TYPE, RequestGeneratedItemsPayload.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(ToggleGeneratedItemPayload.TYPE, ToggleGeneratedItemPayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(GeneratedItemsSyncPayload.TYPE, GeneratedItemsSyncPayload.CODEC);

		ServerPlayNetworking.registerGlobalReceiver(RequestGeneratedItemsPayload.TYPE, (payload, context) ->
			handleRequest(payload, context.player()));
		ServerPlayNetworking.registerGlobalReceiver(ToggleGeneratedItemPayload.TYPE, (payload, context) ->
			handleToggle(payload, context.player()));
	}

	private static void handleRequest(final RequestGeneratedItemsPayload payload, final ServerPlayer player) {
		final BotanyPotBlockEntity pot = resolveReachableHopperPot(player, payload.pos());
		if (pot != null) {
			sendSync(player, pot, payload.pos());
		}
	}

	private static void handleToggle(final ToggleGeneratedItemPayload payload, final ServerPlayer player) {
		final BotanyPotBlockEntity pot = resolveReachableHopperPot(player, payload.pos());
		if (pot == null) {
			return;
		}
		final List<GeneratedItems.Entry> entries = pot.getGeneratedItems();
		if (payload.index() < 0 || payload.index() >= entries.size()) {
			return;
		}
		pot.setGeneratedSuppressed(payload.index(), !entries.get(payload.index()).suppressed());
		sendSync(player, pot, payload.pos());
	}

	/**
	 * The hopper pot at {@code pos}, or {@code null} when the claim is unusable: unloaded chunk,
	 * player too far away, no pot block entity there, or a non-hopper pot. Never throws.
	 */
	private static @Nullable BotanyPotBlockEntity resolveReachableHopperPot(final ServerPlayer player, final BlockPos pos) {
		final Level level = player.level();
		if (!level.isLoaded(pos)) {
			return null;
		}
		if (player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) > MAX_DISTANCE * MAX_DISTANCE) {
			return null;
		}
		final BlockEntity blockEntity = level.getBlockEntity(pos);
		if (!(blockEntity instanceof BotanyPotBlockEntity pot) || !pot.getPotType().isHopper()) {
			return null;
		}
		return pot;
	}

	/** Snapshot the pot's generated entries (stack copies, so later server mutation can't leak) and send them. */
	private static void sendSync(final ServerPlayer player, final BotanyPotBlockEntity pot, final BlockPos pos) {
		final List<GeneratedItemsSyncPayload.Entry> snapshot = new ArrayList<>();
		for (final GeneratedItems.Entry entry : pot.getGeneratedItems()) {
			snapshot.add(new GeneratedItemsSyncPayload.Entry(entry.representative().copy(), entry.suppressed()));
		}
		ServerPlayNetworking.send(player, new GeneratedItemsSyncPayload(pos, snapshot));
	}
}
