package dev.nezo.burmaldaholic.core.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server -> client: the HUD/status numbers of the receiving player. Sent on join and whenever a
 * value changes (checked every 10 ticks, balance changes immediately).
 *
 * @param goldenHourTicks remaining Golden Hour ticks (0 = inactive)
 * @param debt            outstanding loan debt (0 = none), {@code inDefault} loan overdue
 * @param debtTicks       world ticks to the loan deadline
 * @param debtPrincipal   principal of the current loan (0 = none / unknown): the Casino Menu's debt meter
 */
public record PlayerStatusPayload(long balance, int streak, int vipTier, long goldenHourTicks, long debt, boolean inDefault,
		long debtTicks, long debtPrincipal) implements CustomPacketPayload {
	/** No status yet (the client's placeholder). */
	public static final PlayerStatusPayload EMPTY = new PlayerStatusPayload(0, 0, 0, 0, 0, false, 0, 0);

	public static CustomPacketPayload.Type<PlayerStatusPayload> TYPE;
	private static final StreamCodec<ByteBuf, PlayerStatusPayload> RAW = new StreamCodec<>() {
		@Override
		public PlayerStatusPayload decode(ByteBuf buf) {
			return new PlayerStatusPayload(ByteBufCodecs.VAR_LONG.decode(buf), ByteBufCodecs.VAR_INT.decode(buf), ByteBufCodecs.VAR_INT.decode(buf),
				ByteBufCodecs.VAR_LONG.decode(buf), ByteBufCodecs.VAR_LONG.decode(buf), ByteBufCodecs.BOOL.decode(buf), ByteBufCodecs.VAR_LONG.decode(buf),
				ByteBufCodecs.VAR_LONG.decode(buf));
		}

		@Override
		public void encode(ByteBuf buf, PlayerStatusPayload p) {
			ByteBufCodecs.VAR_LONG.encode(buf, p.balance);
			ByteBufCodecs.VAR_INT.encode(buf, p.streak);
			ByteBufCodecs.VAR_INT.encode(buf, p.vipTier);
			ByteBufCodecs.VAR_LONG.encode(buf, p.goldenHourTicks);
			ByteBufCodecs.VAR_LONG.encode(buf, p.debt);
			ByteBufCodecs.BOOL.encode(buf, p.inDefault);
			ByteBufCodecs.VAR_LONG.encode(buf, p.debtTicks);
			ByteBufCodecs.VAR_LONG.encode(buf, p.debtPrincipal);
		}
	};
	public static final StreamCodec<RegistryFriendlyByteBuf, PlayerStatusPayload> CODEC = RAW.cast();

	@Override
	public Type<PlayerStatusPayload> type() {
		return TYPE;
	}
}
