package dev.nezo.burmaldaholic.bots.net;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Client -&gt; server: an action on a table's bot settings ({@code open, save, private, invite, uninvite,
 * limits}). {@code pos} = the table block; {@code args} is untrusted input (validated server-side).
 */
public record BotsActionPayload(BlockPos pos, String action, CompoundTag args) implements CustomPacketPayload {
	public static CustomPacketPayload.Type<BotsActionPayload> TYPE;
	public static final StreamCodec<RegistryFriendlyByteBuf, BotsActionPayload> CODEC = StreamCodec.composite(
		BlockPos.STREAM_CODEC, BotsActionPayload::pos,
		ByteBufCodecs.stringUtf8(32), BotsActionPayload::action,
		ByteBufCodecs.COMPOUND_TAG, BotsActionPayload::args,
		BotsActionPayload::new);

	@Override
	public Type<BotsActionPayload> type() {
		return TYPE;
	}
}
