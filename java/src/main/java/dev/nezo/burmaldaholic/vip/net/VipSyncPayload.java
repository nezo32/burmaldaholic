package dev.nezo.burmaldaholic.vip.net;

import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server -> client: the receiving player's VIP status and today's contracts (Casino Menu, HUD).
 * Sent on join, on every change and on request. {@code open} asks the client to open the Casino Menu
 * (Diamond Casino Card use).
 *
 * @param wagered       lifetime wagered W
 * @param tier          current tier (0..5)
 * @param todayStaked   all wagers settled today / {@code todayReturned} their returns
 * @param contractsOn   {@code contracts.enabled}
 * @param resetTicks    world ticks until the next day's contracts
 * @param rerollCost    chips per reroll
 * @param botNet        net winnings from money bots today (BOTS.md §5.4 heat, Wallet line)
 * @param botCap        today's heat cap ({@code 0} = bots off / no line)
 * @param biggestWin    biggest single net win of a settled round ({@code 0} = none yet), {@code biggestGame} its game id
 */
public record VipSyncPayload(boolean open, long wagered, int tier, long todayStaked, long todayReturned, boolean contractsOn,
		long resetTicks, long rerollCost, List<ContractView> contracts, long botNet, long botCap, long biggestWin, String biggestGame)
		implements CustomPacketPayload {
	public VipSyncPayload {
		biggestGame = biggestGame == null ? "" : biggestGame;
	}

	public VipSyncPayload(boolean open, long wagered, int tier, long todayStaked, long todayReturned, boolean contractsOn, long resetTicks,
			long rerollCost, List<ContractView> contracts) {
		this(open, wagered, tier, todayStaked, todayReturned, contractsOn, resetTicks, rerollCost, contracts, 0, 0, 0, "");
	}

	public VipSyncPayload(boolean open, long wagered, int tier, long todayStaked, long todayReturned, boolean contractsOn, long resetTicks,
			long rerollCost, List<ContractView> contracts, long botNet, long botCap) {
		this(open, wagered, tier, todayStaked, todayReturned, contractsOn, resetTicks, rerollCost, contracts, botNet, botCap, 0, "");
	}

	public static CustomPacketPayload.Type<VipSyncPayload> TYPE;

	/** One contract row. */
	public record ContractView(String id, long target, long progress, long reward, boolean done, boolean rerolled) {}

	public static final VipSyncPayload EMPTY = new VipSyncPayload(false, 0, 0, 0, 0, false, 0, 0, List.of());

	private static final StreamCodec<ByteBuf, VipSyncPayload> RAW = new StreamCodec<>() {
		@Override
		public VipSyncPayload decode(ByteBuf buf) {
			boolean open = ByteBufCodecs.BOOL.decode(buf);
			long wagered = ByteBufCodecs.VAR_LONG.decode(buf);
			int tier = ByteBufCodecs.VAR_INT.decode(buf);
			long staked = ByteBufCodecs.VAR_LONG.decode(buf);
			long returned = ByteBufCodecs.VAR_LONG.decode(buf);
			boolean on = ByteBufCodecs.BOOL.decode(buf);
			long reset = ByteBufCodecs.VAR_LONG.decode(buf);
			long cost = ByteBufCodecs.VAR_LONG.decode(buf);
			int n = Math.min(16, ByteBufCodecs.VAR_INT.decode(buf));
			List<ContractView> list = new ArrayList<>(n);
			for (int i = 0; i < n; i++) {
				list.add(new ContractView(ByteBufCodecs.stringUtf8(64).decode(buf), ByteBufCodecs.VAR_LONG.decode(buf), ByteBufCodecs.VAR_LONG.decode(buf),
					ByteBufCodecs.VAR_LONG.decode(buf), ByteBufCodecs.BOOL.decode(buf), ByteBufCodecs.BOOL.decode(buf)));
			}
			long botNet = ByteBufCodecs.VAR_LONG.decode(buf);
			long botCap = ByteBufCodecs.VAR_LONG.decode(buf);
			long biggest = ByteBufCodecs.VAR_LONG.decode(buf);
			String game = ByteBufCodecs.stringUtf8(64).decode(buf);
			return new VipSyncPayload(open, wagered, tier, staked, returned, on, reset, cost, List.copyOf(list), botNet, botCap, biggest, game);
		}

		@Override
		public void encode(ByteBuf buf, VipSyncPayload p) {
			ByteBufCodecs.BOOL.encode(buf, p.open);
			ByteBufCodecs.VAR_LONG.encode(buf, p.wagered);
			ByteBufCodecs.VAR_INT.encode(buf, p.tier);
			ByteBufCodecs.VAR_LONG.encode(buf, p.todayStaked);
			ByteBufCodecs.VAR_LONG.encode(buf, p.todayReturned);
			ByteBufCodecs.BOOL.encode(buf, p.contractsOn);
			ByteBufCodecs.VAR_LONG.encode(buf, p.resetTicks);
			ByteBufCodecs.VAR_LONG.encode(buf, p.rerollCost);
			int n = Math.min(16, p.contracts.size());
			ByteBufCodecs.VAR_INT.encode(buf, n);
			for (int i = 0; i < n; i++) {
				ContractView c = p.contracts.get(i);
				ByteBufCodecs.stringUtf8(64).encode(buf, c.id());
				ByteBufCodecs.VAR_LONG.encode(buf, c.target());
				ByteBufCodecs.VAR_LONG.encode(buf, c.progress());
				ByteBufCodecs.VAR_LONG.encode(buf, c.reward());
				ByteBufCodecs.BOOL.encode(buf, c.done());
				ByteBufCodecs.BOOL.encode(buf, c.rerolled());
			}
			ByteBufCodecs.VAR_LONG.encode(buf, p.botNet);
			ByteBufCodecs.VAR_LONG.encode(buf, p.botCap);
			ByteBufCodecs.VAR_LONG.encode(buf, p.biggestWin);
			ByteBufCodecs.stringUtf8(64).encode(buf, p.biggestGame.length() > 64 ? p.biggestGame.substring(0, 64) : p.biggestGame);
		}
	};
	public static final StreamCodec<RegistryFriendlyByteBuf, VipSyncPayload> CODEC = RAW.cast();

	/** Same data without the open request (what the client caches). */
	public VipSyncPayload withoutOpen() {
		return open ? new VipSyncPayload(false, wagered, tier, todayStaked, todayReturned, contractsOn, resetTicks, rerollCost, contracts, botNet, botCap,
			biggestWin, biggestGame) : this;
	}

	/** Change detection that ignores the ticking reset timer. */
	public boolean sameContent(VipSyncPayload o) {
		return o != null && wagered == o.wagered && tier == o.tier && todayStaked == o.todayStaked && todayReturned == o.todayReturned
			&& contractsOn == o.contractsOn && rerollCost == o.rerollCost && contracts.equals(o.contracts) && botNet == o.botNet && botCap == o.botCap
			&& biggestWin == o.biggestWin && biggestGame.equals(o.biggestGame);
	}

	@Override
	public Type<VipSyncPayload> type() {
		return TYPE;
	}
}
