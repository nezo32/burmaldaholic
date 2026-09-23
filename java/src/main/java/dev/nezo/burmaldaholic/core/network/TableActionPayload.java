package dev.nezo.burmaldaholic.core.network;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Client -> server: "player pressed {@code action} on the table at {@code pos}". Generic for all tables. */
public record TableActionPayload(BlockPos pos, String action, CompoundTag args) implements CustomPacketPayload {
	public static final int MAX_ACTION_LENGTH = 64;
	public static CustomPacketPayload.Type<TableActionPayload> TYPE;
	public static final StreamCodec<RegistryFriendlyByteBuf, TableActionPayload> CODEC = StreamCodec.composite(
		BlockPos.STREAM_CODEC, TableActionPayload::pos,
		ByteBufCodecs.stringUtf8(MAX_ACTION_LENGTH), TableActionPayload::action,
		ByteBufCodecs.COMPOUND_TAG, TableActionPayload::args,
		TableActionPayload::new);

	@Override
	public Type<TableActionPayload> type() {
		return TYPE;
	}
}
