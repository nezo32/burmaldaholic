package dev.nezo.burmaldaholic.loan.net;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server → client: open / update / close a loan screen. {@code screen} is {@link #LOAN} (Loan Shark,
 * UI.md §10) or {@link #NEGOTIATE} (collector negotiation dialog). The client only renders {@code data};
 * every decision is made on the server.
 */
public record LoanUiPayload(String screen, boolean open, CompoundTag data) implements CustomPacketPayload {
	public static final String LOAN = "loan";
	public static final String NEGOTIATE = "negotiate";
	public static CustomPacketPayload.Type<LoanUiPayload> TYPE;
	public static final StreamCodec<RegistryFriendlyByteBuf, LoanUiPayload> CODEC = StreamCodec.composite(
		ByteBufCodecs.STRING_UTF8, LoanUiPayload::screen,
		ByteBufCodecs.BOOL, LoanUiPayload::open,
		ByteBufCodecs.COMPOUND_TAG, LoanUiPayload::data,
		LoanUiPayload::new);

	@Override
	public Type<LoanUiPayload> type() {
		return TYPE;
	}
}
