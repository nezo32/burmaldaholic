package dev.nezo.burmaldaholic.core.store.net;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server → client: the outcome of a counter action (store buy, comps redeem, …) for the open counter screen's result
 * line. {@code message} is a translatable component (key + args, EC-SPEC §5 "ok, key, args").
 */
public record CounterResultPayload(boolean ok, Component message) implements CustomPacketPayload {
	public static CustomPacketPayload.Type<CounterResultPayload> TYPE;
	public static final StreamCodec<RegistryFriendlyByteBuf, CounterResultPayload> CODEC = StreamCodec.composite(
		ByteBufCodecs.BOOL, CounterResultPayload::ok,
		ComponentSerialization.TRUSTED_STREAM_CODEC, CounterResultPayload::message,
		CounterResultPayload::new);

	@Override
	public Type<CounterResultPayload> type() {
		return TYPE;
	}
}
