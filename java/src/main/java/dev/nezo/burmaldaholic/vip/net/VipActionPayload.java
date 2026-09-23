package dev.nezo.burmaldaholic.vip.net;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Client -> server: Casino Menu actions. {@code "sync"} (menu opened: send fresh data) or
 * {@code "reroll"} with the contract slot {@code index}. Validated server-side.
 */
public record VipActionPayload(String action, int index) implements CustomPacketPayload {
	public static final String SYNC = "sync";
	public static final String REROLL = "reroll";
	public static CustomPacketPayload.Type<VipActionPayload> TYPE;
	public static final StreamCodec<RegistryFriendlyByteBuf, VipActionPayload> CODEC = StreamCodec.composite(
		ByteBufCodecs.stringUtf8(16), VipActionPayload::action,
		ByteBufCodecs.VAR_INT, VipActionPayload::index,
		VipActionPayload::new);

	@Override
	public Type<VipActionPayload> type() {
		return TYPE;
	}
}
