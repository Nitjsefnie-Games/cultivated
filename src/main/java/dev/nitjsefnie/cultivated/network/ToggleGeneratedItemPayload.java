package dev.nitjsefnie.cultivated.network;

import dev.nitjsefnie.cultivated.Cultivated;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * C2S: flip the suppressed flag of generated entry {@link #index} in the hopper pot at {@link #pos}.
 * The server re-validates the position, the player's distance and the index bounds before applying.
 */
public record ToggleGeneratedItemPayload(BlockPos pos, int index) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<ToggleGeneratedItemPayload> TYPE =
		new CustomPacketPayload.Type<>(Cultivated.id("toggle_generated_item"));

	public static final StreamCodec<FriendlyByteBuf, ToggleGeneratedItemPayload> CODEC = StreamCodec.composite(
		BlockPos.STREAM_CODEC, ToggleGeneratedItemPayload::pos,
		ByteBufCodecs.INT, ToggleGeneratedItemPayload::index,
		ToggleGeneratedItemPayload::new
	);

	@Override
	public CustomPacketPayload.Type<ToggleGeneratedItemPayload> type() {
		return TYPE;
	}
}
