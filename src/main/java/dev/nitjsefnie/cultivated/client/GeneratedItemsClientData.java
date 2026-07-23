package dev.nitjsefnie.cultivated.client;

import dev.nitjsefnie.cultivated.network.GeneratedItemsSyncPayload;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.core.BlockPos;
import org.jspecify.annotations.Nullable;

/**
 * Chunk 2 — client-side store for the generated-items snapshots received via
 * {@link GeneratedItemsSyncPayload}. The {@link GeneratedItemsScreen} reads its rows from here and
 * registers itself as the sync listener while open, so a sync triggered by a toggle (or a fresh
 * request) rebuilds the visible rows immediately. Runs entirely on the client thread (Fabric
 * delivers play payloads there), so plain collections are fine.
 */
@Environment(EnvType.CLIENT)
public final class GeneratedItemsClientData {
	private static final Map<BlockPos, List<GeneratedItemsSyncPayload.Entry>> DATA = new HashMap<>();
	private static @Nullable Consumer<BlockPos> syncListener;

	private GeneratedItemsClientData() {
	}

	/** Store an incoming snapshot and notify the open screen (if any) that {@code pos} changed. */
	public static void applySync(final GeneratedItemsSyncPayload payload) {
		DATA.put(payload.pos(), List.copyOf(payload.entries()));
		if (syncListener != null) {
			syncListener.accept(payload.pos());
		}
	}

	/** The last snapshot received for {@code pos}, or an empty list when nothing was synced yet. */
	public static List<GeneratedItemsSyncPayload.Entry> entries(final BlockPos pos) {
		return DATA.getOrDefault(pos, List.of());
	}

	/** The open screen's refresh hook; cleared again when the screen closes. */
	public static void setSyncListener(final @Nullable Consumer<BlockPos> listener) {
		syncListener = listener;
	}

	/** Forget a pot's snapshot (e.g. when its screen closes), so stale data never lingers. */
	public static void forget(final BlockPos pos) {
		DATA.remove(pos);
	}
}
