package dev.nezo.burmaldaholic.games.roulette;

import com.mojang.serialization.Codec;
import dev.nezo.burmaldaholic.core.config.CasinoConfig;
import dev.nezo.burmaldaholic.core.config.sections.RouletteConfig;
import dev.nezo.burmaldaholic.core.mode.CasinoMode;
import dev.nezo.burmaldaholic.core.rng.OddsService;
import dev.nezo.burmaldaholic.core.service.CoreServices;
import dev.nezo.burmaldaholic.core.service.TableOwnershipProvider.OwnedTable;
import dev.nezo.burmaldaholic.core.table.CasinoTableBlockEntity;
import dev.nezo.burmaldaholic.core.table.TableSeats;
import dev.nezo.burmaldaholic.core.table.TableType;
import dev.nezo.burmaldaholic.core.text.Texts;
import dev.nezo.burmaldaholic.core.util.Result;
import dev.nezo.burmaldaholic.games.roulette.logic.BetType;
import dev.nezo.burmaldaholic.games.roulette.logic.Bets;
import dev.nezo.burmaldaholic.games.roulette.logic.Bets.Bet;
import dev.nezo.burmaldaholic.games.roulette.logic.RouletteRound;
import dev.nezo.burmaldaholic.games.roulette.logic.RouletteRound.Transition;
import dev.nezo.burmaldaholic.games.roulette.logic.SlipLimits;
import dev.nezo.burmaldaholic.games.roulette.logic.Spot;
import dev.nezo.burmaldaholic.games.roulette.logic.Wheel;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * One roulette table (normal or High-Roller). Server-authoritative shared spin (GAME_DESIGN.md §9):
 * everybody at the table bets into the same round; the bet timer starts at the first bet once 2+ players
 * are seated; a lone player spins with "Spin". Money goes through {@link #placeBet} (one open stake per
 * player per spin, bankroll reservation = worst case over the 37 outcomes) and {@link #settle}.
 *
 * <p>Client actions: {@code bet} {type, nums[], amount}, {@code rebet}, {@code clear}, {@code spin}
 * (= ready), plus core's {@code sit}/{@code leave}. Leaving or disconnecting never cancels confirmed bets:
 * the spin proceeds and offline players are settled to their balance (§4.1). A server restart refunds
 * open bets (core). Casino mode switched off mid-round refunds everything.
 */
public class RouletteTableBlockEntity extends CasinoTableBlockEntity {
	private static final String HISTORY_KEY = "roulette_history";
	private static final int NO_MORE_BETS_TICKS = 20;
	private static final int RESULT_TICKS = 60;

	private final boolean highRoller;
	private final RouletteRound<UUID> round;
	/** Last settled slip per player (Rebet, and what the RESULT screen shows). */
	private final Map<UUID, List<Bet>> lastSlips = new HashMap<>();
	/** Staked / returned of the spin being shown in RESULT. */
	private final Map<UUID, long[]> outcomes = new HashMap<>();

	public RouletteTableBlockEntity(TableType<RouletteTableBlockEntity> type, BlockPos pos, BlockState state) {
		super(type, pos, state);
		this.highRoller = RouletteModule.HIGH_ROLLER_NAME.equals(type.name());
		this.round = new RouletteRound<>(RouletteRound.Timings.DEFAULT, 12);
	}

	private static RouletteConfig cfg() {
		return CasinoConfig.roulette();
	}

	private static RouletteRound.Timings timings() {
		RouletteConfig c = cfg();
		return new RouletteRound.Timings(c.betTimerTicks, NO_MORE_BETS_TICKS, c.spinTicks, RESULT_TICKS);
	}

	public boolean isHighRoller() {
		return highRoller;
	}

	@Override
	protected int seatCount() {
		return cfg().maxBettors;
	}

	@Override
	protected long minBet() {
		return cfg().minBet;
	}

	private long minTotal() {
		return highRoller ? cfg().highRollerMinTotal : 0;
	}

	/** Limits for one player: VIP tier max (× High-Roller multiplier), owner min/max, config fractions. */
	SlipLimits limits(ServerPlayer player) {
		RouletteConfig c = cfg();
		MinecraftServer server = player.level().getServer();
		long tierMax = CoreServices.vip().maxBet(server, player.getUUID());
		long totalMax = highRoller ? (long) Math.floor(tierMax * c.highRollerMaxMultiplier) : tierMax;
		long min = c.minBet;
		Optional<OwnedTable> owned = ownership();
		if (owned.isPresent()) {
			if (owned.get().minBet() > 0) {
				min = Math.max(min, owned.get().minBet());
			}
			if (owned.get().maxBet() > 0) {
				totalMax = Math.min(totalMax, owned.get().maxBet());
			}
		}
		return SlipLimits.of(min, totalMax, c.insideMaxFraction, 1.0, minTotal());
	}

	// ---- actions ------------------------------------------------------------------------------

	@Override
	public void onAction(ServerPlayer player, String action, CompoundTag args) {
		switch (action) {
			case "bet" -> {
				Optional<BetType> type = BetType.byId(args.getStringOr("type", ""));
				int[] nums = args.getIntArray("nums").orElse(new int[0]);
				long amount = args.getLongOr("amount", 0);
				if (type.isEmpty() || nums.length == 0 || nums.length > 18) {
					sendError(player, Component.translatable("gui.burmaldaholic.error.invalid_bet_position"));
					return;
				}
				List<Integer> list = new ArrayList<>();
				for (int n : nums) {
					list.add(n);
				}
				addBets(player, List.of(new Bet(Spot.of(type.get(), list), amount)));
			}
			case "rebet" -> {
				List<Bet> last = lastSlips.get(player.getUUID());
				if (last == null || last.isEmpty()) {
					sendError(player, Component.translatable("gui.burmaldaholic.roulette.no_bets"));
					return;
				}
				addBets(player, last);
			}
			case "clear" -> {
				if (!round.canBet()) {
					sendError(player, Component.translatable("gui.burmaldaholic.roulette.no_more_bets"));
					return;
				}
				if (!round.clear(player.getUUID()).isEmpty()) {
					refund(player.getUUID());
				}
				syncViewers();
			}
			case "spin" -> {
				if (!round.canBet()) {
					return;
				}
				List<Bet> mine = round.bets(player.getUUID());
				if (mine.isEmpty()) {
					sendError(player, Component.translatable("gui.burmaldaholic.roulette.no_bets"));
					return;
				}
				Optional<SlipLimits.Violation> v = limits(player).checkSpin(mine);
				if (v.isPresent()) {
					sendError(player, message(v.get()));
					return;
				}
				round.setReady(player.getUUID(), true);
				syncViewers();
			}
			default -> { }
		}
	}

	private void addBets(ServerPlayer player, List<Bet> bets) {
		RouletteConfig c = cfg();
		if (!c.enabled) {
			sendError(player, Component.translatable("gui.burmaldaholic.error.disabled"));
			return;
		}
		if (!round.canBet()) {
			sendError(player, Component.translatable("gui.burmaldaholic.roulette.no_more_bets"));
			return;
		}
		if (!isSeated(player) && !sit(player)) {
			return; // sit() already explained why (table full / own table)
		}
		UUID id = player.getUUID();
		List<Bet> current = round.bets(id);
		Optional<SlipLimits.Violation> v = limits(player).checkAdd(current, bets);
		if (v.isPresent()) {
			sendError(player, message(v.get()));
			return;
		}
		long amount = Bets.totalStaked(bets);
		List<Bet> next = Bets.mergeAll(current, bets);
		long reserve = Bets.worstCase(next, c.laPartage) - Bets.worstCase(current, c.laPartage);
		// Limits were checked above (per bet / per spot / per spin); placeBet re-checks funds and owner rules.
		Result<Long> r = placeBet(player, amount, 1, 0, reserve, false);
		if (r.isOk()) {
			round.addBets(id, bets);
			syncViewers();
		}
	}

	static Component message(SlipLimits.Violation v) {
		return switch (v.code()) {
			case INVALID_AMOUNT -> Component.translatable("gui.burmaldaholic.error.invalid_amount");
			case INVALID_POSITION -> Component.translatable("gui.burmaldaholic.error.invalid_bet_position");
			case BET_TOO_LOW -> Component.translatable("gui.burmaldaholic.error.bet_too_low", Texts.number(v.value()));
			case INSIDE_MAX -> Component.translatable("gui.burmaldaholic.roulette.error.inside_max", Texts.number(v.value()));
			case TOTAL_MAX -> Component.translatable("gui.burmaldaholic.roulette.error.total_max", Texts.number(v.value()));
			case MIN_TOTAL -> Component.translatable("gui.burmaldaholic.roulette.error.min_total", Texts.number(v.value()));
		};
	}

	// ---- seats --------------------------------------------------------------------------------

	/** Bets ride when a player walks away: they may always leave, the spin proceeds without them. */
	@Override
	protected boolean canLeaveNow(UUID player) {
		return true;
	}

	@Override
	protected void onPlayerLeft(UUID player, LeaveReason reason) {
		if (reason == LeaveReason.REMOVED) {
			round.clear(player); // core refunds the open stake when the table is broken
		}
		// Otherwise the confirmed bets stay in the round (§4.1 "roulette: spin proceeds").
	}

	// ---- the shared spin ----------------------------------------------------------------------

	@Override
	protected void serverTick(ServerLevel level) {
		if (!CasinoMode.isEnabled(level)) {
			if (round.hasBets() || round.phase() != RouletteRound.Phase.BETTING) {
				round.abort().keySet().forEach(this::refund);
				outcomes.clear();
				setPhase(round.phase().id());
				syncViewers();
			}
			return;
		}
		round.configure(timings(), cfg().historyLength);
		List<UUID> seated = seats().occupied().stream().map(TableSeats.Seat::player).toList();
		Transition<UUID> t = round.update(gameTime(), seated, () -> OddsService.get().fair().nextInt(Wheel.POCKETS), minTotal());
		if (t == null) {
			return;
		}
		switch (t) {
			case Transition.NoMoreBets<UUID> n -> refundDropped(level, n.dropped());
			case Transition.Abandoned<UUID> a -> refundDropped(level, a.dropped());
			case Transition.Spin<UUID> s -> level.playSound(null, worldPosition, RouletteModule.SPIN_SOUND, SoundSource.BLOCKS, 1.0f, 1.0f);
			case Transition.Result<UUID> r -> settleAll(level, r.result(), r.slips());
			case Transition.Reset<UUID> x -> outcomes.clear();
		}
		setPhase(round.phase().id());
		setChanged();
		syncViewers();
	}

	private void refundDropped(ServerLevel level, List<UUID> dropped) {
		for (UUID id : dropped) {
			refund(id);
			ServerPlayer p = level.getServer().getPlayerList().getPlayer(id);
			if (p != null) {
				sendError(p, Component.translatable("gui.burmaldaholic.roulette.error.min_total", Texts.number(minTotal())));
			}
		}
	}

	private void settleAll(ServerLevel level, int result, Map<UUID, List<Bet>> slips) {
		boolean laPartage = cfg().laPartage;
		outcomes.clear();
		for (Map.Entry<UUID, List<Bet>> e : slips.entrySet()) {
			UUID id = e.getKey();
			List<Bet> bets = e.getValue();
			long staked = Bets.totalStaked(bets);
			long ret = Bets.totalReturn(bets, result, laPartage);
			settle(id, ret);
			lastSlips.put(id, bets);
			outcomes.put(id, new long[] {staked, ret});
			ServerPlayer p = level.getServer().getPlayerList().getPlayer(id);
			if (p == null) {
				continue;
			}
			p.sendOverlayMessage(resultLine(result, staked, ret));
			if (result == 0) {
				boolean zeroWon = bets.stream().anyMatch(b -> b.spot().covers(0));
				boolean evenMoney = bets.stream().anyMatch(b -> b.type().evenMoney());
				if (zeroWon) {
					p.sendSystemMessage(Component.translatable("msg.burmaldaholic.roulette.zero_hero"));
				} else if (laPartage && evenMoney) {
					p.sendSystemMessage(Component.translatable("gui.burmaldaholic.roulette.la_partage"));
				}
			}
		}
	}

	/** A pocket number in its colour ("17" red / black / green). */
	public static MutableComponent pocket(int n) {
		ChatFormatting color = switch (Wheel.color(n)) {
			case RED -> ChatFormatting.RED;
			case BLACK -> ChatFormatting.WHITE;
			case GREEN -> ChatFormatting.GREEN;
		};
		return Texts.number(n).withStyle(color);
	}

	/** "17 Black — you win 180 chips" / "— not this time" / just "17 Black" for spectators. */
	public static Component resultLine(int result, long staked, long returned) {
		Component n = pocket(result);
		Component color = Component.translatable("gui.burmaldaholic.roulette.color." + Wheel.color(result).id());
		if (staked <= 0) {
			return Component.translatable("gui.burmaldaholic.roulette.result", n, color);
		}
		if (returned > 0) {
			return Component.translatable("gui.burmaldaholic.roulette.result_win", n, color, Texts.chipsAcc(returned)).withStyle(ChatFormatting.GREEN);
		}
		return Component.translatable("gui.burmaldaholic.roulette.result_lose", n, color);
	}

	// ---- client state -------------------------------------------------------------------------

	@Override
	public CompoundTag writeClientState(ServerPlayer viewer) {
		CompoundTag t = baseState(viewer);
		UUID id = viewer.getUUID();
		SlipLimits l = limits(viewer);
		t.putString("phase", round.phase().id());
		t.putLong("min", l.minBet());
		t.putLong("max", l.totalMax());
		t.putLong("inside_max", l.insideMax());
		t.putLong("min_total", l.minTotal());
		t.putBoolean("high_roller", highRoller);
		t.putBoolean("enabled", cfg().enabled);
		t.putLong("ticks_left", round.remaining(gameTime()));
		t.putInt("spin_ticks", round.timings().spinTicks());
		t.putInt("result", round.result());
		t.putIntArray("history", round.history().stream().mapToInt(Integer::intValue).toArray());
		boolean showingResult = round.phase() == RouletteRound.Phase.RESULT && outcomes.containsKey(id);
		List<Bet> mine = showingResult ? lastSlips.getOrDefault(id, List.of()) : round.bets(id);
		t.put("bets", writeBets(mine));
		t.putLong("total", Bets.totalStaked(mine));
		t.putBoolean("can_rebet", round.canBet() && lastSlips.containsKey(id));
		t.putBoolean("ready", round.isReady(id));
		t.putInt("ready_count", round.readyCount());
		t.putInt("bettors", round.bettors().size());
		// Other players' chips on the layout (spot key → amount), aggregated.
		Map<String, Long> others = new TreeMap<>();
		for (UUID other : round.bettors()) {
			if (!other.equals(id)) {
				round.bets(other).forEach(b -> others.merge(b.spot().key(), b.amount(), Long::sum));
			}
		}
		CompoundTag othersTag = new CompoundTag();
		others.forEach(othersTag::putLong);
		t.put("others", othersTag);
		long[] outcome = outcomes.get(id);
		if (outcome != null) {
			t.putLong("out_staked", outcome[0]);
			t.putLong("out_return", outcome[1]);
		}
		return t;
	}

	private static ListTag writeBets(List<Bet> bets) {
		ListTag list = new ListTag();
		for (Bet b : bets) {
			CompoundTag bt = new CompoundTag();
			bt.putString("spot", b.spot().key());
			bt.putLong("amount", b.amount());
			list.add(bt);
		}
		return list;
	}

	// ---- persistence (history only; open stakes are core's and refunded on reload) --------------

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		if (!round.history().isEmpty()) {
			output.store(HISTORY_KEY, Codec.INT.listOf(), round.history());
		}
	}

	@Override
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		round.setHistory(input.read(HISTORY_KEY, Codec.INT.listOf()).orElse(List.of()));
	}
}
