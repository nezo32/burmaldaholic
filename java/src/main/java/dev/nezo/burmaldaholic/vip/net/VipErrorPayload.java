package dev.nezo.burmaldaholic.vip.net;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Server -> client: a refused Casino Menu action (red line on the screen, UI.md §12). */
public record VipErrorPayload(Component message) implements CustomPacketPayload {
	public static CustomPacketPayload.Type<VipErrorPayload> TYPE;
	public static final StreamCodec<RegistryFriendlyByteBuf, VipErrorPayload> CODEC =
		ComponentSerialization.TRUSTED_STREAM_CODEC.map(VipErrorPayload::new, VipErrorPayload::message);

	@Override
	public Type<VipErrorPayload> type() {
		return TYPE;
	}
}
