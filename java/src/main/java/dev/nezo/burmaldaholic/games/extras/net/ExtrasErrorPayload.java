package dev.nezo.burmaldaholic.games.extras.net;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Server -&gt; client: red error line on the open extras screen (UI.md §12). */
public record ExtrasErrorPayload(Component message) implements CustomPacketPayload {
	public static CustomPacketPayload.Type<ExtrasErrorPayload> TYPE;
	public static final StreamCodec<RegistryFriendlyByteBuf, ExtrasErrorPayload> CODEC = StreamCodec.composite(
		ComponentSerialization.TRUSTED_STREAM_CODEC, ExtrasErrorPayload::message,
		ExtrasErrorPayload::new);

	@Override
	public Type<ExtrasErrorPayload> type() {
		return TYPE;
	}
}
