package dev.nezo.burmaldaholic.core.store.net;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Client → server: {@code sync} (send the store) or {@code buy} offer {@code id} × {@code qty} ({@code all} = as many as
 * balance, room and limits allow, computed by the server). Validated by {@code CounterSessions}.
 */
public record StoreActionPayload(String action, String id, int qty, boolean all) implements CustomPacketPayload {
	public static CustomPacketPayload.Type<StoreActionPayload> TYPE;
	public static final StreamCodec<RegistryFriendlyByteBuf, StoreActionPayload> CODEC = StreamCodec.composite(
		ByteBufCodecs.stringUtf8(16), StoreActionPayload::action,
		ByteBufCodecs.stringUtf8(64), StoreActionPayload::id,
		ByteBufCodecs.VAR_INT, StoreActionPayload::qty,
		ByteBufCodecs.BOOL, StoreActionPayload::all,
		StoreActionPayload::new);

	public static StoreActionPayload sync() {
		return new StoreActionPayload("sync", "", 0, false);
	}

	public static StoreActionPayload buy(String id, int qty, boolean all) {
		return new StoreActionPayload("buy", id, qty, all);
	}

	@Override
	public Type<StoreActionPayload> type() {
		return TYPE;
	}
}
