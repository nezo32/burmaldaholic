package dev.nezo.burmaldaholic.core.network;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Server -> client: full (viewer-specific) state of the table at {@code pos}. */
public record TableSyncPayload(BlockPos pos, CompoundTag state) implements CustomPacketPayload {
	public static CustomPacketPayload.Type<TableSyncPayload> TYPE;
	public static final StreamCodec<RegistryFriendlyByteBuf, TableSyncPayload> CODEC = StreamCodec.composite(
		BlockPos.STREAM_CODEC, TableSyncPayload::pos,
		ByteBufCodecs.COMPOUND_TAG, TableSyncPayload::state,
		TableSyncPayload::new);

	@Override
	public Type<TableSyncPayload> type() {
		return TYPE;
	}
}
