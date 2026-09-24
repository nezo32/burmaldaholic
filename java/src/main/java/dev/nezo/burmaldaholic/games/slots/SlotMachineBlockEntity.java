package dev.nezo.burmaldaholic.games.slots;

import dev.nezo.burmaldaholic.core.wager.HouseEdges;
import dev.nezo.burmaldaholic.Burmaldaholic;
import dev.nezo.burmaldaholic.core.config.CasinoConfig;
import dev.nezo.burmaldaholic.core.economy.Economies;
import dev.nezo.burmaldaholic.core.mode.CasinoMode;
import dev.nezo.burmaldaholic.core.rng.CasinoRng;
import dev.nezo.burmaldaholic.core.rng.OddsContext;
import dev.nezo.burmaldaholic.core.rng.OddsService;
import dev.nezo.burmaldaholic.core.service.CoreServices;
import dev.nezo.burmaldaholic.core.service.VipTiers;
import dev.nezo.burmaldaholic.core.table.CasinoTableBlockEntity;
import dev.nezo.burmaldaholic.core.table.CasinoTableMenu;
import dev.nezo.burmaldaholic.core.table.TableType;
import dev.nezo.burmaldaholic.core.text.Texts;
import dev.nezo.burmaldaholic.core.util.Result;
import dev.nezo.burmaldaholic.games.slots.api.SlotsApi;
import dev.nezo.burmaldaholic.games.slots.logic.JackpotPool;
import dev.nezo.burmaldaholic.games.slots.logic.LineBets;
import dev.nezo.burmaldaholic.games.slots.logic.SlotEngine;
import dev.nezo.burmaldaholic.games.slots.logic.SlotEngine.LineWin;
import dev.nezo.burmaldaholic.games.slots.logic.SlotEngine.SpinEval;
import dev.nezo.burmaldaholic.games.slots.logic.SlotTable;
import dev.nezo.burmaldaholic.games.slots.logic.Symbol;
import dev.nezo.burmaldaholic.games.slots.logic.Tier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * One slot machine (GAME_DESIGN.md §8, UI.md §6). Server-authoritative flow:
 *
 * <ol>
 *   <li>{@code spin} / {@code auto} action (line bet) → validation (casino mode, {@code slots.enabled}, VIP tier,
 *       line-bet range) → stake debited via {@link #placeBet} → outcome drawn through {@code OddsService.play}
 *       (§14 streak re-draw) — final before the animation starts;</li>
 *   <li>the grid is synced, the client animates for {@code slots.spinTicks};</li>
 *   <li>timer {@code spin} → jackpot pool updated (contribution, award, seed top-up) → {@link #settle} (pays,
 *       fires {@code PLAY_RESOLVED}) → {@link SlotsApi#SPIN} → messages → at most one {@link SlotsApi#TRIGGER};</li>
 *   <li>auto-spin chains up to 10 spins while the player keeps the screen open; stops on a win ≥ 20× the spin
 *       bet, when the balance is below the bet, or on "Stop".</li>
 * </ol>
 *
 * Leaving mid-spin (disconnect, walking away, machine broken) settles immediately without chaos events.
 * A server stop mid-spin refunds the stake (core), and because the pool is only touched at settlement
 * the jackpot stays consistent.
 */
public class SlotMachineBlockEntity extends CasinoTableBlockEntity {
	static final String TIMER_SPIN = "spin";
	static final String TIMER_AUTO = "auto_next";
	private static final int AUTO_GAP_TICKS = 10;

	private final Tier tier;
	private @Nullable Pending pending;
	private @Nullable CompoundTag lastResult;
	private int seq;
	private final Map<UUID, Long> lineBets = new HashMap<>();
	private @Nullable Auto auto;
	private final Map<UUID, CompoundTag> autoSummaries = new HashMap<>();

	private record Pending(UUID player, String playerName, long lineBet, long spinBet, boolean owned, SlotTable table, SpinEval eval,
			int seq) {}

	private static final class Auto {
		final UUID player;
		int left;
		int spins;
		long bet;
		long won;
		String notice = "";

		Auto(UUID player, int left) {
			this.player = player;
			this.left = left;
		}
	}

	public SlotMachineBlockEntity(TableType<SlotMachineBlockEntity> type, BlockPos pos, BlockState state, Tier tier) {
		super(type, pos, state);
		this.tier = tier;
	}

	public Tier tier() {
		return tier;
	}

	@Override
	public String gameId() {
		return SlotsModule.ID;
	}

	@Override
	protected int seatCount() {
		return 1;
	}

	/** §17 RTP per tier (cashback uses the machine's own edge). */
	@Override
	protected double houseEdge() {
		return switch (tier) {
			case COPPER -> HouseEdges.SLOTS_COPPER;
			case GOLD -> HouseEdges.SLOTS_GOLD;
			case NETHERITE -> HouseEdges.SLOTS_NETHERITE;
		};
	}

	@Override
	protected long minBet() {
		return SlotsMath.lineBets(tier).minLineBet() * tier.lines();
	}

	@Override
	protected long tableMaxBet() {
		return SlotsMath.lineBets(tier).maxLineBet() * tier.lines();
	}

	/** A spinning player may walk away: the spin is settled at once (§4.1). */
	@Override
	protected boolean canLeaveNow(UUID player) {
		return true;
	}

	/** Test/automation hook: true while a spin is waiting for its animation to end. */
	public boolean spinning() {
		return pending != null;
	}

	// ---- actions ------------------------------------------------------------------------------

	@Override
	public void onAction(ServerPlayer player, String action, CompoundTag args) {
		switch (action) {
			case "spin" -> {
				stopAuto(player.getUUID(), "");
				startSpin(player, args.getLongOr("line_bet", -1));
			}
			case "auto" -> {
				if (pending != null || auto != null) {
					sendError(player, Component.translatable("gui.burmaldaholic.error.round_in_progress"));
					break;
				}
				auto = new Auto(player.getUUID(), LineBets.AUTO_SPINS);
				autoSummaries.remove(player.getUUID());
				if (!startSpin(player, args.getLongOr("line_bet", -1))) {
					auto = null;
				}
			}
			case "stop_auto" -> {
				if (auto != null && auto.player.equals(player.getUUID())) {
					auto.left = 0;
					if (pending == null) {
						endAuto("");
					}
				}
			}
			default -> {
				return;
			}
		}
		syncViewers();
	}

	/**
	 * Validates, takes the stake and draws the outcome.
	 *
	 * @param requestedLineBet the client's line bet (clamped into the player's range; &lt; 1 = remembered/min)
	 * @return true if a spin started
	 */
	boolean startSpin(ServerPlayer player, long requestedLineBet) {
		if (!(level instanceof ServerLevel serverLevel)) {
			return false;
		}
		MinecraftServer server = serverLevel.getServer();
		if (pending != null) {
			sendError(player, Component.translatable("gui.burmaldaholic.error.round_in_progress"));
			return false;
		}
		if (!CasinoMode.isEnabled(player)) {
			sendError(player, Component.translatable("gui.burmaldaholic.error.casino_off"));
			return false;
		}
		if (!CasinoConfig.slots().enabled) {
			sendError(player, Component.translatable("gui.burmaldaholic.error.disabled"));
			return false;
		}
		int minVip = SlotsMath.minVipTier(tier);
		if (CoreServices.vip().tier(server, player.getUUID()) < minVip) {
			sendError(player, Component.translatable("gui.burmaldaholic.error.vip_required", VipTiers.name(minVip)));
			return false;
		}
		boolean owned = ownership().isPresent();
		SlotsMath.Machine machine = SlotsMath.machine(tier, owned);
		SlotTable table = machine.table();
		if (table.empty()) {
			sendError(player, Component.translatable("gui.burmaldaholic.error.disabled"));
			return false;
		}
		LineBets bets = SlotsMath.lineBets(tier);
		long tierMax = CoreServices.vip().maxBet(server, player.getUUID());
		LineBets.Range range = bets.range(tierMax, table.lines());
		if (!range.playable()) {
			int vip = CoreServices.vip().tier(server, player.getUUID());
			sendError(player, Component.translatable("gui.burmaldaholic.error.bet_too_high", Texts.number(tierMax), VipTiers.name(vip)));
			return false;
		}
		if (!claimSeat(player)) {
			return false;
		}
		long lineBet = range.clamp(requestedLineBet > 0 ? requestedLineBet : lineBets.getOrDefault(player.getUUID(), range.min()));
		lineBets.put(player.getUUID(), lineBet);
		long spinBet = lineBet * table.lines();
		Result<Long> stake = placeBet(player, spinBet, bets.minLineBet() * table.lines(), bets.maxLineBet() * table.lines(),
			SlotEngine.worstCaseReturn(table, lineBet), true);
		if (!stake.isOk()) {
			return false;
		}
		OddsContext ctx = new OddsContext(player.getUUID(), SlotsModule.ID, spinBet);
		CasinoRng rng = OddsService.get().rng(ctx);
		SpinEval eval = OddsService.get().play(ctx, machine.rtp(), () -> SlotEngine.spin(table, lineBet, rng::nextInt),
			e -> SlotEngine.losing(e, spinBet));
		autoSummaries.remove(player.getUUID());
		pending = new Pending(player.getUUID(), player.getName().getString(), lineBet, spinBet, owned, table, eval, ++seq);
		setPhase("spinning");
		startTimer(TIMER_SPIN, spinTicks());
		syncViewers();
		return true;
	}

	static int spinTicks() {
		return Math.max(10, CasinoConfig.slots().spinTicks);
	}

	/** Seats the player; a seat hogged by someone who closed the screen (and is not spinning) is freed. */
	private boolean claimSeat(ServerPlayer player) {
		if (isSeated(player)) {
			return true;
		}
		if (level instanceof ServerLevel serverLevel && seats().isFull()) {
			for (var seat : seats().occupied()) {
				ServerPlayer other = serverLevel.getServer().getPlayerList().getPlayer(seat.player());
				boolean busy = pending != null && pending.player.equals(seat.player());
				if (!busy && (other == null || !viewing(other))) {
					leave(seat.player(), LeaveReason.LEFT);
				}
			}
		}
		return sit(player);
	}

	private boolean viewing(ServerPlayer p) {
		return p.containerMenu instanceof CasinoTableMenu menu && menu.pos().equals(worldPosition) && !p.isRemoved();
	}

	// ---- settlement ---------------------------------------------------------------------------

	@Override
	protected void onTimer(String id) {
		if (TIMER_SPIN.equals(id)) {
			finish(false);
		} else if (TIMER_AUTO.equals(id)) {
			continueAuto();
		}
	}

	@Override
	protected void onPlayerLeft(UUID player, LeaveReason reason) {
		if (pending != null && pending.player.equals(player)) {
			cancelTimer(TIMER_SPIN);
			finish(true);
		}
		if (auto != null && auto.player.equals(player)) {
			cancelTimer(TIMER_AUTO);
			auto = null;
		}
		super.onPlayerLeft(player, reason); // refunds anything still open (nothing, normally)
	}

	/**
	 * Settles the pending spin now (normally called by the {@code spin} timer; public for GameTests).
	 * {@code quiet}: the player is leaving (no chaos events, no auto-continue).
	 */
	public void finish(boolean quiet) {
		Pending p = pending;
		if (p == null || !(level instanceof ServerLevel serverLevel)) {
			return;
		}
		pending = null;
		setPhase("idle");
		MinecraftServer server = serverLevel.getServer();
		long award = 0;
		if (p.table.progressive()) {
			award = updateJackpot(server, p);
		}
		long total = p.eval.basePayout() + award;
		String jackpotTag = award > 0 ? "jackpot" : "";
		settle(p.player, total, r -> r.withTags(tier.id(), jackpotTag));
		lastResult = resultTag(p, award, total);
		ServerPlayer online = server.getPlayerList().getPlayer(p.player);
		if (online != null && online.isRemoved()) {
			online = null;
		}
		afterSettle(serverLevel, online, p, award, total, quiet);
		if (auto != null && auto.player.equals(p.player)) {
			auto.spins++;
			auto.bet += p.spinBet;
			auto.won += total;
			auto.left--;
			if (quiet || online == null) {
				auto = null;
			} else if (LineBets.autoStopsOnWin(total, p.spinBet)) {
				endAuto("big_win");
			} else if (auto.left <= 0 || !viewing(online)) {
				endAuto("");
			} else if (Economies.get().balance(online) < p.lineBet * p.table.lines()) {
				endAuto("funds");
			} else {
				startTimer(TIMER_AUTO, AUTO_GAP_TICKS);
			}
		}
		syncViewers();
	}

	private long updateJackpot(MinecraftServer server, Pending p) {
		JackpotData data = JackpotData.get(server);
		JackpotPool state = data.pool(tier).contribute(p.spinBet, SlotsMath.contribution(tier)).state();
		long award = 0;
		if (p.eval.jackpotHit()) {
			long max = SlotsMath.lineBets(tier).machineMaxSpinBet(tier.lines());
			JackpotPool.Payout pay = state.pay(p.spinBet, max, SlotsMath.seed(tier));
			state = pay.state();
			award = pay.award();
			if (pay.toppedUp() > 0) {
				Burmaldaholic.LOGGER.info("Bank topped up the {} jackpot by {}", tier.id(), pay.toppedUp());
			}
		}
		data.set(tier, state);
		return award;
	}

	private void continueAuto() {
		Auto a = auto;
		if (a == null || !(level instanceof ServerLevel serverLevel)) {
			return;
		}
		ServerPlayer player = serverLevel.getServer().getPlayerList().getPlayer(a.player);
		if (player == null || !viewing(player)) {
			auto = null;
			return;
		}
		if (!startSpin(player, lineBets.getOrDefault(a.player, -1L))) {
			endAuto("");
		}
		syncViewers();
	}

	private void stopAuto(UUID player, String notice) {
		if (auto != null && auto.player.equals(player)) {
			cancelTimer(TIMER_AUTO);
			endAuto(notice);
		}
	}

	private void endAuto(String notice) {
		Auto a = auto;
		auto = null;
		cancelTimer(TIMER_AUTO);
		if (a == null || a.spins == 0) {
			return;
		}
		CompoundTag t = new CompoundTag();
		t.putInt("spins", a.spins);
		t.putLong("bet", a.bet);
		t.putLong("won", a.won);
		t.putString("notice", notice);
		autoSummaries.put(a.player, t);
	}

	private void afterSettle(ServerLevel level, @Nullable ServerPlayer player, Pending p, long award, long total, boolean quiet) {
		MinecraftServer server = level.getServer();
		Component machineName = Component.translatable("block.burmaldaholic." + tier.blockName());
		if (award > 0) {
			Component msg = Component.translatable("msg.burmaldaholic.slots.jackpot_broadcast", Texts.raw(p.playerName),
				Texts.chipsAcc(award), machineName).withStyle(ChatFormatting.GOLD);
			server.getPlayerList().broadcastSystemMessage(msg, false);
			if (player != null) {
				player.sendSystemMessage(Component.translatable("msg.burmaldaholic.slots.jackpot_self", Texts.chipsAcc(award))
					.withStyle(ChatFormatting.GOLD));
			}
		}
		List<SlotsApi.LineWin> wins = new ArrayList<>();
		for (LineWin w : p.eval.wins()) {
			wins.add(new SlotsApi.LineWin(w.line(), w.kind().name().toLowerCase(Locale.ROOT), w.symbol().id(), w.multiplier(), w.payout()));
		}
		try {
			SlotsApi.SPIN.invoker().onSpin(new SlotsApi.Spin(player, p.player, tier.id(), p.lineBet, p.spinBet, total, List.copyOf(wins), award,
				p.eval.threeSevens(), p.owned));
		} catch (RuntimeException e) {
			Burmaldaholic.LOGGER.error("slots SPIN listener failed", e);
		}
		if (!quiet && player != null && p.eval.special() != null && CasinoMode.isEnabled(server)) {
			trigger(level, player, p.eval.special(), award);
		}
	}

	/**
	 * §8.1: at most one chaos event per spin, after crediting; nothing when chaos is off. Publishes
	 * {@link SlotsApi#TRIGGER} (informational stream) and then asks chaos to run the event through
	 * {@link ChaosBridge} (Fabric ObjectShare); chaos applies its own toggles, safety rules and cooldowns.
	 */
	private void trigger(ServerLevel level, ServerPlayer player, Symbol special, long award) {
		if (!CasinoConfig.chaos().enabled) {
			return;
		}
		SlotsApi.ChaosEvent event = SlotsApi.ChaosEvent.forSymbol(special.id());
		if (event == null) {
			return;
		}
		MinecraftServer server = level.getServer();
		List<ServerPlayer> nearby = List.of();
		if (event == SlotsApi.ChaosEvent.JACKPOT) {
			double r = SlotsApi.JACKPOT_SHOWER_RADIUS;
			nearby = level.players().stream()
				.filter(q -> q != player && !q.isRemoved() && q.distanceToSqr(player) <= r * r)
				.toList();
		}
		long ghBefore = CoreServices.goldenHour().remainingTicks(server);
		try {
			SlotsApi.TRIGGER.invoker().onTrigger(new SlotsApi.Trigger(player, event, special.id(), tier.id(), level, worldPosition,
				event == SlotsApi.ChaosEvent.JACKPOT ? award : 0, nearby));
		} catch (RuntimeException e) {
			Burmaldaholic.LOGGER.error("slots TRIGGER listener failed", e);
		}
		switch (event) {
			case JACKPOT -> ChaosBridge.jackpot(player);
			case GOLDEN_HOUR -> {
				String result = ChaosBridge.trigger(player, event.id());
				boolean started = "started".equals(result) || CoreServices.goldenHour().remainingTicks(server) > ghBefore;
				player.sendSystemMessage(Component.translatable(started ? "msg.burmaldaholic.slots.three_clocks"
					: "msg.burmaldaholic.slots.three_clocks_cooldown").withStyle(ChatFormatting.GOLD));
			}
			default -> {
				String result = ChaosBridge.trigger(player, event.id());
				String key = switch (special) {
					case CREEPER -> "three_creepers";
					case TNT -> "three_tnt";
					case PEARL -> "three_pearls";
					default -> null;
				};
				if (key != null && ChaosBridge.happened(result)) {
					player.sendSystemMessage(Component.translatable("msg.burmaldaholic.slots." + key).withStyle(ChatFormatting.YELLOW));
				}
			}
		}
	}

	// ---- client state -------------------------------------------------------------------------

	private CompoundTag resultTag(Pending p, long award, long total) {
		CompoundTag t = new CompoundTag();
		t.putInt("seq", p.seq);
		t.putIntArray("grid", gridArray(p.eval.grid()));
		ListTag wins = new ListTag();
		for (LineWin w : p.eval.wins()) {
			CompoundTag wt = new CompoundTag();
			wt.putInt("line", w.line());
			wt.putString("kind", w.kind().name().toLowerCase(Locale.ROOT));
			wt.putInt("symbol", w.symbol().ordinal());
			wt.putDouble("mult", w.multiplier());
			wt.putLong("payout", w.payout());
			wins.add(wt);
		}
		t.put("wins", wins);
		t.putLong("base", p.eval.basePayout());
		t.putLong("award", award);
		t.putLong("total", total);
		t.putLong("spin_bet", p.spinBet);
		t.putString("player", p.playerName);
		t.putString("player_id", p.player.toString());
		t.putBoolean("sevens", p.eval.threeSevens());
		t.putInt("special", p.eval.special() == null ? -1 : p.eval.special().ordinal());
		return t;
	}

	static int[] gridArray(Symbol[][] grid) {
		int[] a = new int[9];
		for (int r = 0; r < 3; r++) {
			for (int c = 0; c < 3; c++) {
				a[r * 3 + c] = grid[r][c].ordinal();
			}
		}
		return a;
	}

	@Override
	public CompoundTag writeClientState(ServerPlayer viewer) {
		CompoundTag t = baseState(viewer);
		MinecraftServer server = viewer.level().getServer();
		boolean owned = ownership().isPresent();
		SlotsMath.Machine machine = SlotsMath.machine(tier, owned);
		SlotTable table = machine.table();
		LineBets bets = SlotsMath.lineBets(tier);
		LineBets.Range range = bets.range(CoreServices.vip().maxBet(server, viewer.getUUID()), table.lines());
		t.putString("tier", tier.id());
		t.putInt("lines", table.lines());
		t.putLong("line_min", range.min());
		t.putLong("line_max", range.max());
		t.putLong("line_bet", range.clamp(lineBets.getOrDefault(viewer.getUUID(), range.min())));
		t.putBoolean("enabled", CasinoConfig.slots().enabled && !table.empty());
		int minVip = SlotsMath.minVipTier(tier);
		t.putInt("min_vip", minVip);
		t.putBoolean("vip_ok", CoreServices.vip().tier(server, viewer.getUUID()) >= minVip);
		t.putBoolean("owned", owned);
		t.putBoolean("progressive", table.progressive());
		if (table.progressive()) {
			t.putLong("jackpot", JackpotData.get(server).pool(tier).pool());
		}
		t.putInt("spin_ticks", spinTicks());
		// paytable: symbols with weight, their 3-of-a-kind pay (star: 0 on progressive machines)
		ListTag pays = new ListTag();
		for (Symbol s : table.present()) {
			CompoundTag pt = new CompoundTag();
			pt.putInt("symbol", s.ordinal());
			pt.putDouble("pay", s == Symbol.STAR && table.progressive() ? 0 : table.pay(s));
			pays.add(pt);
		}
		t.put("paytable", pays);
		t.putDouble("berry1", table.berryPartial(1));
		t.putDouble("berry2", table.berryPartial(2));
		Pending p = pending;
		if (p != null) {
			CompoundTag st = new CompoundTag();
			st.putInt("seq", p.seq);
			st.putIntArray("grid", gridArray(p.eval.grid()));
			st.putLong("ticks_left", ticksLeft(TIMER_SPIN));
			st.putString("player", p.playerName);
			st.putBoolean("mine", p.player.equals(viewer.getUUID()));
			t.put("spin", st);
		}
		if (lastResult != null) {
			CompoundTag r = lastResult.copy();
			r.putBoolean("mine", r.getStringOr("player_id", "").equals(viewer.getUUID().toString()));
			r.remove("player_id");
			t.put("result", r);
		}
		if (auto != null) {
			CompoundTag a = new CompoundTag();
			a.putInt("left", auto.left);
			a.putBoolean("mine", auto.player.equals(viewer.getUUID()));
			t.put("auto", a);
		}
		CompoundTag summary = autoSummaries.get(viewer.getUUID());
		if (summary != null) {
			t.put("auto_summary", summary.copy());
		}
		return t;
	}
}
