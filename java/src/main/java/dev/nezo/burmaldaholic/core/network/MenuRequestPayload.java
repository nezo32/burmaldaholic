package dev.nezo.burmaldaholic.core.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Client -&gt; server: show Casino Menu page {@code page} ({@code action} empty) or press one of its
 * buttons ({@code amount} = the amount field, 0 if none). Validated server-side.
 */
public record MenuRequestPayload(String page, String action, long amount) implements CustomPacketPayload {
	public static CustomPacketPayload.Type<MenuRequestPayload> TYPE;
	public static final StreamCodec<RegistryFriendlyByteBuf, MenuRequestPayload> CODEC = StreamCodec.composite(
		ByteBufCodecs.stringUtf8(64), MenuRequestPayload::page,
		ByteBufCodecs.stringUtf8(64), MenuRequestPayload::action,
		ByteBufCodecs.VAR_LONG, MenuRequestPayload::amount,
		MenuRequestPayload::new);

	@Override
	public Type<MenuRequestPayload> type() {
		return TYPE;
	}
}
