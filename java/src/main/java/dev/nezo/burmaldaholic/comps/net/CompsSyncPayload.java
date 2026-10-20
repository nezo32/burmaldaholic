package dev.nezo.burmaldaholic.comps.net;

import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server → client: the Comps tab state (EC-SPEC §5).
 *
 * @param pointsMilli     comp-point balance (milli-points)
 * @param rateTenths      comp rate of the player's tier, tenths of a percent (150 = 15.0 %)
 * @param theoTodayMilli  today's theoretical loss × 1000
 * @param cbEstimate      cashback the day roll would pay at the current tier
 * @param daily           daily-bonus state ({@code CompsRules.Daily} name)
 * @param dailyAmount     chips the daily bonus pays at the current tier
 * @param ticksToNextDay  ticks until the next in-game day
 * @param activeTicks     active ticks today
 * @param minActiveTicks  active ticks the daily bonus needs
 * @param rewards         redemption rows
 */
public record CompsSyncPayload(long pointsMilli, int rateTenths, long theoTodayMilli, long cbEstimate, String daily, long dailyAmount,
	long ticksToNextDay, long activeTicks, long minActiveTicks, List<RewardView> rewards) implements CustomPacketPayload {

	/**
	 * @param lock "" = redeemable, else the reason ({@code tier}, {@code points}, {@code limit}, {@code dimension}, {@code disabled})
	 */
	public record RewardView(String id, long cost, int minTier, int limit, int used, String lock) {}

	public static CustomPacketPayload.Type<CompsSyncPayload> TYPE;

	public static final CompsSyncPayload EMPTY = new CompsSyncPayload(0, 0, 0, 0, "LOCKED_WAGER", 0, 0, 0, 0, List.of());

	private static final int MAX_ROWS = 32;

	private static final StreamCodec<ByteBuf, CompsSyncPayload> RAW = new StreamCodec<>() {
		@Override
		public CompsSyncPayload decode(ByteBuf buf) {
			long points = ByteBufCodecs.VAR_LONG.decode(buf);
			int rate = ByteBufCodecs.VAR_INT.decode(buf);
			long theo = ByteBufCodecs.VAR_LONG.decode(buf);
			long cb = ByteBufCodecs.VAR_LONG.decode(buf);
			String daily = ByteBufCodecs.stringUtf8(32).decode(buf);
			long amount = ByteBufCodecs.VAR_LONG.decode(buf);
			long next = ByteBufCodecs.VAR_LONG.decode(buf);
			long active = ByteBufCodecs.VAR_LONG.decode(buf);
			long minActive = ByteBufCodecs.VAR_LONG.decode(buf);
			int n = Math.min(MAX_ROWS, ByteBufCodecs.VAR_INT.decode(buf));
			List<RewardView> rows = new ArrayList<>(n);
			for (int i = 0; i < n; i++) {
				rows.add(new RewardView(ByteBufCodecs.stringUtf8(32).decode(buf), ByteBufCodecs.VAR_LONG.decode(buf), ByteBufCodecs.VAR_INT.decode(buf),
					ByteBufCodecs.VAR_INT.decode(buf), ByteBufCodecs.VAR_INT.decode(buf), ByteBufCodecs.stringUtf8(32).decode(buf)));
			}
			return new CompsSyncPayload(points, rate, theo, cb, daily, amount, next, active, minActive, List.copyOf(rows));
		}

		@Override
		public void encode(ByteBuf buf, CompsSyncPayload p) {
			ByteBufCodecs.VAR_LONG.encode(buf, p.pointsMilli);
			ByteBufCodecs.VAR_INT.encode(buf, p.rateTenths);
			ByteBufCodecs.VAR_LONG.encode(buf, p.theoTodayMilli);
			ByteBufCodecs.VAR_LONG.encode(buf, p.cbEstimate);
			ByteBufCodecs.stringUtf8(32).encode(buf, p.daily);
			ByteBufCodecs.VAR_LONG.encode(buf, p.dailyAmount);
			ByteBufCodecs.VAR_LONG.encode(buf, p.ticksToNextDay);
			ByteBufCodecs.VAR_LONG.encode(buf, p.activeTicks);
			ByteBufCodecs.VAR_LONG.encode(buf, p.minActiveTicks);
			int n = Math.min(MAX_ROWS, p.rewards.size());
			ByteBufCodecs.VAR_INT.encode(buf, n);
			for (int i = 0; i < n; i++) {
				RewardView r = p.rewards.get(i);
				ByteBufCodecs.stringUtf8(32).encode(buf, r.id());
				ByteBufCodecs.VAR_LONG.encode(buf, r.cost());
				ByteBufCodecs.VAR_INT.encode(buf, r.minTier());
				ByteBufCodecs.VAR_INT.encode(buf, r.limit());
				ByteBufCodecs.VAR_INT.encode(buf, r.used());
				ByteBufCodecs.stringUtf8(32).encode(buf, r.lock());
			}
		}
	};
	public static final StreamCodec<RegistryFriendlyByteBuf, CompsSyncPayload> CODEC = RAW.cast();

	@Override
	public Type<CompsSyncPayload> type() {
		return TYPE;
	}
}
