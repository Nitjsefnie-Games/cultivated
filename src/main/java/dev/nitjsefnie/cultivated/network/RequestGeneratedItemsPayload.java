package dev.nitjsefnie.cultivated.network;

import dev.nitjsefnie.cultivated.Cultivated;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * C2S: ask the server for the generated-items list of the hopper pot at {@link #pos}. Sent when the
 * player opens the generated-items screen via the hopper GUI's cogwheel button.
 */
public record RequestGeneratedItemsPayload(BlockPos pos) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<RequestGeneratedItemsPayload> TYPE =
		new CustomPacketPayload.Type<>(Cultivated.id("request_generated_items"));

	public static final StreamCodec<FriendlyByteBuf, RequestGeneratedItemsPayload> CODEC = StreamCodec.composite(
		BlockPos.STREAM_CODEC, RequestGeneratedItemsPayload::pos,
		RequestGeneratedItemsPayload::new
	);

	@Override
	public CustomPacketPayload.Type<RequestGeneratedItemsPayload> type() {
		return TYPE;
	}
}
