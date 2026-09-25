package dev.nezo.burmaldaholic.multiplayer.net;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Client → server: an action on the Casino Charter screen ({@code refresh}, {@code deposit}, {@code withdraw},
 * {@code table}, {@code link}). Every field is validated server-side (owner / operator, amounts, table key).
 */
public record CharterActionPayload(String casinoId, String action, CompoundTag args) implements CustomPacketPayload {
	public static CustomPacketPayload.Type<CharterActionPayload> TYPE;
	public static final StreamCodec<RegistryFriendlyByteBuf, CharterActionPayload> CODEC = StreamCodec.composite(
		ByteBufCodecs.stringUtf8(32), CharterActionPayload::casinoId,
		ByteBufCodecs.stringUtf8(32), CharterActionPayload::action,
		ByteBufCodecs.COMPOUND_TAG, CharterActionPayload::args,
		CharterActionPayload::new);

	@Override
	public Type<CharterActionPayload> type() {
		return TYPE;
	}
}
