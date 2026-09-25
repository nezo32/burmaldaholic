package dev.nezo.burmaldaholic.games.extras.net;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Client -&gt; server: an action on one of the item-driven extras screens (coin flip, dice duel, scratch
 * card). {@code game} is {@code coin}, {@code dice} or {@code scratch}; {@code args} is untrusted input.
 */
public record ExtrasActionPayload(String game, String action, CompoundTag args) implements CustomPacketPayload {
	public static CustomPacketPayload.Type<ExtrasActionPayload> TYPE;
	public static final StreamCodec<RegistryFriendlyByteBuf, ExtrasActionPayload> CODEC = StreamCodec.composite(
		ByteBufCodecs.stringUtf8(32), ExtrasActionPayload::game,
		ByteBufCodecs.stringUtf8(32), ExtrasActionPayload::action,
		ByteBufCodecs.COMPOUND_TAG, ExtrasActionPayload::args,
		ExtrasActionPayload::new);

	@Override
	public Type<ExtrasActionPayload> type() {
		return TYPE;
	}
}
