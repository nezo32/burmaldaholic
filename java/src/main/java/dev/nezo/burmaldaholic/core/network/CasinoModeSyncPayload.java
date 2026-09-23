package dev.nezo.burmaldaholic.core.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Server -> client: current value of the casino_mode game rule (on join and on change). */
public record CasinoModeSyncPayload(boolean enabled) implements CustomPacketPayload {
	public static CustomPacketPayload.Type<CasinoModeSyncPayload> TYPE;
	public static final StreamCodec<RegistryFriendlyByteBuf, CasinoModeSyncPayload> CODEC =
		ByteBufCodecs.BOOL.<RegistryFriendlyByteBuf>cast().map(CasinoModeSyncPayload::new, CasinoModeSyncPayload::enabled);

	@Override
	public Type<CasinoModeSyncPayload> type() {
		return TYPE;
	}
}
