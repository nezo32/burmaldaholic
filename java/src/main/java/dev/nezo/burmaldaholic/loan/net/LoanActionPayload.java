package dev.nezo.burmaldaholic.loan.net;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Client → server: a button on a loan screen. Loan screen: {@code take} (index = product), {@code pay}
 * (amount), {@code pay_all}, {@code close}. Negotiation: {@code pay_all}, {@code pay_part}, {@code refuse}.
 * Everything is re-validated on the server.
 */
public record LoanActionPayload(String screen, String action, int index, long amount) implements CustomPacketPayload {
	public static CustomPacketPayload.Type<LoanActionPayload> TYPE;
	public static final StreamCodec<RegistryFriendlyByteBuf, LoanActionPayload> CODEC = StreamCodec.composite(
		ByteBufCodecs.stringUtf8(32), LoanActionPayload::screen,
		ByteBufCodecs.stringUtf8(32), LoanActionPayload::action,
		ByteBufCodecs.VAR_INT, LoanActionPayload::index,
		ByteBufCodecs.VAR_LONG, LoanActionPayload::amount,
		LoanActionPayload::new);

	@Override
	public Type<LoanActionPayload> type() {
		return TYPE;
	}
}
