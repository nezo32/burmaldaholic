package dev.nezo.burmaldaholic.comps.net;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Client → server: Comps tab actions (EC-SPEC §5): {@code sync}, {@code redeem} (reward {@code id}, {@code qty} ≥ 1)
 * or {@code claim_daily}. Validated server-side (cooldown, counter session, gates).
 */
public record CompsActionPayload(String action, String id, int qty) implements CustomPacketPayload {
	public static final String SYNC = "sync";
	public static final String REDEEM = "redeem";
	public static final String CLAIM_DAILY = "claim_daily";

	public static CustomPacketPayload.Type<CompsActionPayload> TYPE;
	public static final StreamCodec<RegistryFriendlyByteBuf, CompsActionPayload> CODEC = StreamCodec.composite(
		ByteBufCodecs.stringUtf8(16), CompsActionPayload::action,
		ByteBufCodecs.stringUtf8(32), CompsActionPayload::id,
		ByteBufCodecs.VAR_INT, CompsActionPayload::qty,
		CompsActionPayload::new);

	@Override
	public Type<CompsActionPayload> type() {
		return TYPE;
	}
}
