package dev.nitjsefnie.cultivated.network;

import dev.nitjsefnie.cultivated.Cultivated;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.item.ItemStack;

/**
 * S2C: the full generated-items snapshot of the hopper pot at {@link #pos}, in discovery order. Sent
 * in response to {@link RequestGeneratedItemsPayload} and after every accepted
 * {@link ToggleGeneratedItemPayload}, so the open generated-items screen can rebuild its rows.
 */
public record GeneratedItemsSyncPayload(BlockPos pos, List<Entry> entries) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<GeneratedItemsSyncPayload> TYPE =
		new CustomPacketPayload.Type<>(Cultivated.id("generated_items_sync"));

	/** One generated item: its normalized representative stack plus its current suppression flag. */
	public record Entry(ItemStack stack, boolean suppressed) {
		public static final StreamCodec<RegistryFriendlyByteBuf, Entry> CODEC = StreamCodec.composite(
			ItemStack.OPTIONAL_STREAM_CODEC, Entry::stack,
			ByteBufCodecs.BOOL, Entry::suppressed,
			Entry::new
		);
	}

	public static final StreamCodec<RegistryFriendlyByteBuf, GeneratedItemsSyncPayload> CODEC = StreamCodec.composite(
		BlockPos.STREAM_CODEC, GeneratedItemsSyncPayload::pos,
		Entry.CODEC.apply(ByteBufCodecs.list()), GeneratedItemsSyncPayload::entries,
		GeneratedItemsSyncPayload::new
	);

	@Override
	public CustomPacketPayload.Type<GeneratedItemsSyncPayload> type() {
		return TYPE;
	}
}
