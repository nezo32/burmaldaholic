package dev.nezo.burmaldaholic.core.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Server -> client: an error line for the table screen at {@code pos} (e.g. "Minimum bet here is 5"). */
public record TableErrorPayload(BlockPos pos, Component message) implements CustomPacketPayload {
	public static CustomPacketPayload.Type<TableErrorPayload> TYPE;
	public static final StreamCodec<RegistryFriendlyByteBuf, TableErrorPayload> CODEC = StreamCodec.composite(
		BlockPos.STREAM_CODEC, TableErrorPayload::pos,
		ComponentSerialization.TRUSTED_STREAM_CODEC, TableErrorPayload::message,
		TableErrorPayload::new);

	@Override
	public Type<TableErrorPayload> type() {
		return TYPE;
	}
}
