package dev.nezo.burmaldaholic.core.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Server -> client: the effective config (JSON) so client UIs show the server's limits/paytables. */
public record ConfigSyncPayload(String json) implements CustomPacketPayload {
	public static final int MAX_LENGTH = 1 << 20;
	public static CustomPacketPayload.Type<ConfigSyncPayload> TYPE;
	public static final StreamCodec<RegistryFriendlyByteBuf, ConfigSyncPayload> CODEC =
		ByteBufCodecs.stringUtf8(MAX_LENGTH).<RegistryFriendlyByteBuf>cast().map(ConfigSyncPayload::new, ConfigSyncPayload::json);

	@Override
	public Type<ConfigSyncPayload> type() {
		return TYPE;
	}
}
