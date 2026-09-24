package dev.nezo.burmaldaholic.games.craps;

import dev.nezo.burmaldaholic.core.config.CasinoConfig;
import dev.nezo.burmaldaholic.core.config.sections.CrapsConfig;
import dev.nezo.burmaldaholic.core.mode.CasinoMode;
import dev.nezo.burmaldaholic.core.rng.CasinoRng;
import dev.nezo.burmaldaholic.core.rng.OddsContext;
import dev.nezo.burmaldaholic.core.rng.OddsService;
import dev.nezo.burmaldaholic.core.table.CasinoTableBlockEntity;
import dev.nezo.burmaldaholic.core.table.TableSeats;
import dev.nezo.burmaldaholic.core.table.TableType;
import dev.nezo.burmaldaholic.core.text.Texts;
import dev.nezo.burmaldaholic.core.advancement.CasinoAdvancements;
import dev.nezo.burmaldaholic.core.wager.HouseEdges;
import dev.nezo.burmaldaholic.games.craps.logic.Bet;
import dev.nezo.burmaldaholic.games.craps.logic.BetKind;
import dev.nezo.burmaldaholic.games.craps.logic.BetResolution;
import dev.nezo.burmaldaholic.games.craps.logic.CrapsMath;
import dev.nezo.burmaldaholic.games.craps.logic.CrapsResolver;
import dev.nezo.burmaldaholic.games.craps.logic.CrapsRules;
import dev.nezo.burmaldaholic.games.craps.logic.CrapsTable;
import dev.nezo.burmaldaholic.games.craps.logic.CrapsTable.OddsInfo;
import dev.nezo.burmaldaholic.games.craps.logic.RollEvent;
import dev.nezo.burmaldaholic.games.craps.logic.ShooterRotation.SeatInfo;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * Server side of a craps table (GAME_DESIGN §10). Rules and the point state machine live in the
 * pure {@link CrapsTable}; this class adds money, timers, seats, messages and client sync.
 *
 * <p><b>Money</b>: craps bets resolve one by one over many rolls, so every bet (flat + odds) is a
 * separate core stake keyed by the bet id ({@code placeBet(player, betId, ...)} / {@code settleBet} /
 * {@code refundBet}): limits, the wager gate, owned-table bankroll reservation (§18.2), one reported round
 * per settled bet (with the bet's own edge: Odds 0 %), persistence and restart refunds (§4.1) all come from core.
 *
 * <p><b>Leaving</b> (button, disconnect, distance, table broken): the player's bets are played out
 * immediately with honest dice (§4.1 "bets stay working until resolved", same expected value; review B1:
 * breaking the table never refunds a contract bet). Casino mode switched off: bets are refunded.
 */
public class CrapsTableBlockEntity extends CasinoTableBlockEntity {
	static final String TIMER_WINDOW = "window";
	static final String TIMER_ROLL = "roll";
	private static final String K = "gui.burmaldaholic.craps.";
	private static final String M = "msg.burmaldaholic.craps.";

	private final CrapsTable table = new CrapsTable(rules());
	/** Shooter of the current "points in a row" run (§19 hot_shooter) and its length. */
	private @Nullable UUID runShooter;
	private int pointsInRow;
	private long lastRollTime = Long.MIN_VALUE / 2;
	private int rollTimerFor = -1;
	private @Nullable UUID rollTimerShooter;
	private @Nullable UUID announcedShooter;

	public CrapsTableBlockEntity(TableType<CrapsTableBlockEntity> type, BlockPos pos, BlockState state) {
		super(type, pos, state);
	}

	static CrapsRules rules() {
		CrapsConfig c = CasinoConfig.craps();
		return new CrapsRules(c.fieldPays2, c.fieldPays12, c.maxOdds4_10, c.maxOdds5_9, c.maxOdds6_8);
	}

	/** Pure table state (tests / inspection). */
	public CrapsTable table() {
		return table;
	}

	@Override
	protected int seatCount() {
		return CasinoConfig.craps().seats;
	}

	@Override
	protected long minBet() {
		return CasinoConfig.craps().minBet;
	}

	// ---- actions --------------------------------------------------------------------------------

	@Override
	public void onAction(ServerPlayer player, String action, CompoundTag args) {
		table.setRules(rules());
		switch (action) {
			case "bet" -> placeFlat(player, BetKind.byId(args.getStringOr("kind", "")), args.getLongOr("amount", 0));
			case "odds" -> placeOdds(player, args.getIntOr("bet", -1), args.getLongOr("amount", 0));
			case "roll" -> shooterRolls(player);
			default -> {
				return;
			}
		}
		syncViewers();
	}

	@Override
	public boolean sit(ServerPlayer player) {
		boolean ok = super.sit(player);
		if (ok) {
			rearm();
			syncViewers();
		}
		return ok;
	}

	void placeFlat(ServerPlayer player, @Nullable BetKind kind, long amount) {
		if (!CasinoConfig.craps().enabled) {
			sendError(player, Component.translatable("gui.burmaldaholic.error.disabled"));
			return;
		}
		if (kind == null) {
			sendError(player, Component.translatable("gui.burmaldaholic.error.invalid_bet_position"));
			return;
		}
		if (!isSeated(player) && !sit(player)) {
			return;
		}
		CrapsTable.PlaceError err = table.placeError(player.getUUID(), kind);
		if (err != null) {
			sendError(player, Component.translatable(err == CrapsTable.PlaceError.LINE_ONLY_COME_OUT
				? K + "line_only_come_out" : "gui.burmaldaholic.error.invalid_bet_position"));
			return;
		}
		Bet bet = table.addBet(player.getUUID(), kind, amount);
		if (!placeBet(player, key(bet.id()), amount, minBet(), 0, CrapsMath.flatWorstCase(kind, amount, table.rules()), true).isOk()) {
			table.removeBet(bet.id());
			return;
		}
		setChanged();
		rearm();
	}

	void placeOdds(ServerPlayer player, int betId, long amount) {
		Bet bet = table.bet(betId);
		if (bet == null || !bet.owner().equals(player.getUUID()) || stakeOf(player.getUUID(), key(betId)) <= 0) {
			sendError(player, Component.translatable("gui.burmaldaholic.error.invalid_bet_position"));
			return;
		}
		CrapsTable.OddsError err = table.oddsError(bet, amount);
		OddsInfo info = table.oddsInfo(bet);
		if (err != null || info == null) {
			sendError(player, switch (err == null ? CrapsTable.OddsError.NO_POINT : err) {
				case MULTIPLE -> Component.translatable(K + "odds_multiple", Texts.chips(info == null ? 1 : info.unit()));
				case MAX -> Component.translatable(K + "odds_max", Texts.chips(info == null ? 0 : info.max()));
				case NO_POINT -> Component.translatable("gui.burmaldaholic.error.invalid_bet_position");
			});
			return;
		}
		// §10.2: odds are not counted in the max bet; funds are still checked.
		if (!placeBet(player, key(betId), amount, 1, 0, CrapsMath.oddsWorstCase(info.side(), info.point(), amount), false).isOk()) {
			return;
		}
		table.addOdds(betId, amount);
		setChanged();
	}

	void shooterRolls(ServerPlayer player) {
		if (!player.getUUID().equals(table.shooter())) {
			sendError(player, Component.translatable("gui.burmaldaholic.error.not_your_turn"));
			return;
		}
		if (!table.canRoll()) {
			sendError(player, Component.translatable(K + "need_line_bet"));
			return;
		}
		long wait = windowEnd() - gameTime();
		if (wait > 0) {
			sendError(player, Component.translatable(K + "bet_window", seconds(wait)));
			return;
		}
		roll();
	}

	// ---- flow: betting window, auto roll, shooter ------------------------------------------------

	List<SeatInfo> seatInfos() {
		List<SeatInfo> list = new ArrayList<>();
		for (TableSeats.Seat s : seats().occupied()) {
			list.add(new SeatInfo(s.player(), s.index(), table.hasLineBet(s.player())));
		}
		return list;
	}

	private String nameOf(@Nullable UUID player) {
		if (player != null) {
			for (TableSeats.Seat s : seats().occupied()) {
				if (s.player().equals(player)) {
					return s.name();
				}
			}
		}
		return "?";
	}

	/** Bets close {@code craps.betWindowTicks} after each roll when more than one player is seated. */
	long windowEnd() {
		return lastRollTime + (seats().occupied().size() > 1 ? CasinoConfig.craps().betWindowTicks : 0);
	}

	boolean windowOpen() {
		return gameTime() < windowEnd();
	}

	/** Re-evaluates shooter and timers after anything changed (bet, sit, leave, roll). */
	void rearm() {
		List<SeatInfo> seats = seatInfos();
		if (seats.isEmpty()) {
			cancelTimer(TIMER_WINDOW);
			cancelTimer(TIMER_ROLL);
			rollTimerFor = -1;
			rollTimerShooter = null;
			table.ensureShooter(seats, false);
			announcedShooter = null;
			return;
		}
		table.ensureShooter(seats, false);
		long wait = windowEnd() - gameTime();
		if (wait > 0) {
			if (ticksLeft(TIMER_WINDOW) < 0) {
				startTimer(TIMER_WINDOW, (int) Math.min(Integer.MAX_VALUE, wait));
			}
		} else {
			cancelTimer(TIMER_WINDOW);
			windowClosed();
		}
		announceShooter();
		setPhase(table.comeOut() ? "come_out" : "point");
	}

	/** Betting window over: pass the dice if the shooter has no line bet, arm the auto roll. */
	private void windowClosed() {
		table.ensureShooter(seatInfos(), true);
		if (table.canRoll()) {
			if (rollTimerFor != table.rollCount() || !Objects.equals(rollTimerShooter, table.shooter())) {
				rollTimerFor = table.rollCount();
				rollTimerShooter = table.shooter();
				startTimer(TIMER_ROLL, CasinoConfig.craps().rollTimerTicks);
			}
		} else {
			cancelTimer(TIMER_ROLL);
			rollTimerFor = -1;
			rollTimerShooter = null;
		}
	}

	private void announceShooter() {
		UUID shooter = table.shooter();
		if (shooter == null || shooter.equals(announcedShooter)) {
			return;
		}
		announcedShooter = shooter;
		if (seats().occupied().size() > 1) {
			broadcast(Component.translatable(M + "new_shooter", Texts.raw(nameOf(shooter))).withStyle(ChatFormatting.YELLOW));
		}
	}

	@Override
	protected void onTimer(String id) {
		if (TIMER_WINDOW.equals(id)) {
			rearm();
			syncViewers();
		} else if (TIMER_ROLL.equals(id)) {
			if (level != null && CasinoMode.isEnabled(level) && table.canRoll() && !windowOpen()) {
				broadcast(Component.translatable(M + "auto_roll").withStyle(ChatFormatting.GRAY));
				roll();
				syncViewers();
			}
		}
	}

	// ---- the roll -------------------------------------------------------------------------------

	void roll() {
		UUID shooter = table.shooter();
		CasinoRng rng = OddsService.get().rng(new OddsContext(shooter == null ? new UUID(0, 0) : shooter, gameId(), 0));
		int[] dice = CrapsResolver.rollDice(rng::nextInt);
		roll(dice[0], dice[1]);
	}

	/** Applies a roll with the given dice (the RNG draw is in {@link #roll()}; GameTests call this). */
	public void roll(int d1, int d2) {
		table.setRules(rules());
		UUID shooterForRun = table.shooter();
		String shooterName = nameOf(shooterForRun);
		List<SeatInfo> seats = seatInfos();
		CrapsTable.TableRoll tr = table.roll(d1, d2, seats);
		lastRollTime = gameTime();
		cancelTimer(TIMER_ROLL);
		rollTimerFor = -1;
		rollTimerShooter = null;

		Map<UUID, List<Component>> personal = new LinkedHashMap<>();
		for (BetResolution res : tr.result().resolutions()) {
			Bet bet = res.bet();
			List<Component> lines = personal.computeIfAbsent(bet.owner(), u -> new ArrayList<>());
			if (res.outcome() == BetResolution.Outcome.MOVE) {
				lines.add(Component.translatable(M + (bet.kind() == BetKind.COME ? "come_moved" : "dont_come_moved"), Texts.number(res.movedTo())));
				continue;
			}
			if (!res.outcome().resolved()) {
				continue;
			}
			pay(bet, res.totalReturn());
			if (res.oddsReturned()) {
				lines.add(Component.translatable(M + "odds_returned").withStyle(ChatFormatting.GRAY));
			}
			lines.add(resultLine(res));
		}
		setChanged();
		trackShooter(shooterForRun, tr.result().event());

		int total = d1 + d2;
		Component headline = Component.translatable(M + "rolled", Texts.raw(shooterName), Texts.number(d1), Texts.number(d2), Texts.number(total));
		List<Component> event = eventLines(tr.result().event());
		MinecraftServer server = level instanceof ServerLevel sl ? sl.getServer() : null;
		for (TableSeats.Seat seat : seats().occupied()) {
			ServerPlayer p = server == null ? null : server.getPlayerList().getPlayer(seat.player());
			if (p == null) {
				continue;
			}
			p.sendSystemMessage(headline);
			event.forEach(p::sendSystemMessage);
			personal.getOrDefault(seat.player(), List.of()).forEach(p::sendSystemMessage);
		}
		if (tr.sevenOut()) {
			announcedShooter = null;
		}
		rearm();
	}

	static List<Component> eventLines(RollEvent e) {
		List<Component> out = new ArrayList<>();
		switch (e.kind()) {
			case NATURAL -> out.add(Component.translatable(M + "natural", Texts.number(e.total())).withStyle(ChatFormatting.GREEN));
			case CRAPS -> {
				out.add(Component.translatable(M + "craps", Texts.number(e.total())).withStyle(ChatFormatting.RED));
				if (e.total() == 12) {
					out.add(Component.translatable(M + "bar_12").withStyle(ChatFormatting.GRAY));
				}
			}
			case POINT_SET -> out.add(Component.translatable(M + "point_set", Texts.number(e.point())).withStyle(ChatFormatting.YELLOW));
			case POINT_MADE -> out.add(Component.translatable(M + "point_made", Texts.number(e.point())).withStyle(ChatFormatting.GREEN));
			case SEVEN_OUT -> out.add(Component.translatable(M + "seven_out").withStyle(ChatFormatting.RED));
			case ROLL -> {
			}
		}
		return out;
	}

	static MutableComponent betLabel(BetKind kind, int point) {
		if (kind == BetKind.COME && point != 0) {
			return Component.translatable(K + "come_point", Texts.number(point));
		}
		if (kind == BetKind.DONT_COME && point != 0) {
			return Component.translatable(K + "dont_come_point", Texts.number(point));
		}
		return Component.translatable(K + kind.id());
	}

	private static Component resultLine(BetResolution res) {
		long net = res.net();
		if (res.bet().kind() == BetKind.FIELD && net > 0) {
			return Component.translatable(M + "field_win", Texts.chips(net)).withStyle(ChatFormatting.GREEN);
		}
		return Component.translatable(M + "bet_result", betLabel(res.bet().kind(), res.bet().point()), outcome(net));
	}

	private static Component outcome(long net) {
		if (net > 0) {
			return Component.translatable("gui.burmaldaholic.common.result.win", Texts.chips(net)).withStyle(ChatFormatting.GREEN);
		}
		if (net < 0) {
			return Component.translatable("gui.burmaldaholic.common.result.loss", Texts.chips(-net)).withStyle(ChatFormatting.RED);
		}
		return Component.translatable("gui.burmaldaholic.common.result.push").withStyle(ChatFormatting.GRAY);
	}

	private void broadcast(Component message) {
		if (!(level instanceof ServerLevel sl)) {
			return;
		}
		for (TableSeats.Seat seat : seats().occupied()) {
			ServerPlayer p = sl.getServer().getPlayerList().getPlayer(seat.player());
			if (p != null) {
				p.sendSystemMessage(message);
			}
		}
	}

	private static Component seconds(long ticks) {
		return Texts.plural("unit.burmaldaholic.second", (ticks + 19) / 20);
	}

	// ---- leaving --------------------------------------------------------------------------------

	@Override
	protected void onPlayerLeft(UUID player, LeaveReason reason) {
		// No super call: the core default would refund the player's open (per-bet) stakes.
		int point = table.point();
		List<Bet> mine = table.removeOwner(player);
		if (!mine.isEmpty() && level instanceof ServerLevel sl) {
			MinecraftServer server = sl.getServer();
			ServerPlayer online = server.getPlayerList().getPlayer(player);
			CasinoRng rng = OddsService.get().rng(new OddsContext(player, gameId(), 0));
			Map<Integer, Long> results = CrapsResolver.autoComplete(point, mine, rng::nextInt, table.rules());
			long net = 0;
			for (Bet b : mine) {
				long staked = stakeOf(player, key(b.id()));
				long ret = results.getOrDefault(b.id(), b.staked());
				pay(b, ret);
				net += ret - staked;
			}
			if (online != null) {
				online.sendSystemMessage(Component.translatable(M + "bets_played_out", outcome(net)));
			}
			setChanged();
		}
		if (player.equals(runShooter)) {
			runShooter = null;
			pointsInRow = 0;
		}
		rearm();
	}

	/** §19 hot_shooter: three points made in a row by the same shooter (a seven-out or a new shooter resets). */
	private void trackShooter(@Nullable UUID shooter, RollEvent event) {
		if (shooter == null) {
			return;
		}
		if (!shooter.equals(runShooter)) {
			runShooter = shooter;
			pointsInRow = 0;
		}
		switch (event.kind()) {
			case POINT_MADE -> {
				pointsInRow++;
				if (pointsInRow >= 3 && level instanceof ServerLevel sl) {
					CasinoAdvancements.grant(sl.getServer(), shooter, "hot_shooter");
				}
			}
			case SEVEN_OUT -> {
				runShooter = null;
				pointsInRow = 0;
			}
			default -> {
			}
		}
	}

	/** Consecutive points made by the current shooter (tests). */
	public int pointsInRow() {
		return pointsInRow;
	}

	// ---- money ----------------------------------------------------------------------------------

	static String key(int betId) {
		return Integer.toString(betId);
	}

	/** §17 edge of a craps bet: flat edge by kind, Odds 0 % (stake-weighted). */
	static double edgeOf(BetKind kind, long flat, long odds) {
		double e = switch (kind) {
			case PASS, COME -> HouseEdges.CRAPS_PASS;
			case DONT_PASS, DONT_COME -> HouseEdges.CRAPS_DONT;
			case FIELD -> HouseEdges.CRAPS_FIELD;
		};
		long total = flat + odds;
		return total <= 0 ? 0 : e * flat / total;
	}

	/** Settles one bet's core stake with its total return (stake included). */
	private void pay(Bet bet, long totalReturn) {
		settleBet(bet.owner(), key(bet.id()), totalReturn, r -> r.withEdge(edgeOf(bet.kind(), bet.flat(), bet.odds()))
			.withTags(bet.kind().id(), bet.odds() > 0 ? "odds" : ""));
	}

	/** Returns every open bet (casino mode off). */
	private void refundAll(MinecraftServer server, boolean notify) {
		java.util.Set<UUID> players = new java.util.HashSet<>();
		for (OpenStake o : openStakes()) {
			refundBet(o.player(), o.bet(), true);
			players.add(o.player());
		}
		table.clear();
		setChanged();
		if (notify) {
			for (UUID id : players) {
				ServerPlayer p = server.getPlayerList().getPlayer(id);
				if (p != null) {
					p.sendSystemMessage(Component.translatable(M + "bets_refunded"));
				}
			}
		}
	}

	/** Total chips staked at this table (tests). */
	public long escrowed() {
		return openStakes().stream().mapToLong(OpenStake::amount).sum();
	}

	// ---- ticking & lifecycle --------------------------------------------------------------------

	@Override
	protected void serverTick(ServerLevel level) {
		MinecraftServer server = level.getServer();
		if (!table.bets().isEmpty() && openStakes().isEmpty()) {
			table.clear(); // stakes were returned by core after a reload (§4.1): the bets are gone too
		}
		if (server.getTickCount() % 20 == 0 && !openStakes().isEmpty() && !CasinoMode.isEnabled(level)) {
			refundAll(server, true);
			rearm();
			syncViewers();
		}
	}

	// ---- client state ---------------------------------------------------------------------------

	@Override
	public CompoundTag writeClientState(ServerPlayer viewer) {
		CompoundTag tag = baseState(viewer);
		UUID me = viewer.getUUID();
		UUID shooter = table.shooter();
		tag.putInt("point", table.point());
		tag.putInt("rolls", table.rollCount());
		tag.putInt("d1", table.lastD1());
		tag.putInt("d2", table.lastD2());
		RollEvent ev = table.lastEvent();
		tag.putString("event", ev == null ? "" : ev.kind().id());
		tag.putInt("event_total", ev == null ? 0 : ev.total());
		tag.putString("shooter", shooter == null ? "" : nameOf(shooter));
		tag.putBoolean("you_shoot", me.equals(shooter));
		tag.putBoolean("can_roll", me.equals(shooter) && table.canRoll() && !windowOpen());
		tag.putBoolean("need_line", shooter != null && table.comeOut() && !table.canRoll());
		tag.putBoolean("window", windowOpen() && seats().occupied().size() > 1);
		tag.putBoolean("enabled", CasinoConfig.craps().enabled);
		CompoundTag allowed = new CompoundTag();
		for (BetKind k : BetKind.values()) {
			allowed.putBoolean(k.id(), table.placeError(me, k) == null);
		}
		tag.put("allowed", allowed);
		ListTag list = new ListTag();
		for (Bet b : table.bets()) {
			CompoundTag bt = new CompoundTag();
			boolean mine = b.owner().equals(me);
			bt.putInt("id", mine ? b.id() : -1);
			bt.putString("kind", b.kind().id());
			bt.putLong("flat", b.flat());
			bt.putLong("odds", b.odds());
			bt.putInt("point", b.point());
			bt.putBoolean("mine", mine);
			OddsInfo info = mine ? table.oddsInfo(b) : null;
			if (info != null) {
				bt.putInt("unit", info.unit());
				bt.putLong("odds_max", info.max());
				bt.putLong("room", info.room());
				bt.putBoolean("off", info.off());
			}
			list.add(bt);
		}
		tag.put("bets", list);
		CrapsRules r = table.rules();
		tag.putInt("field2", r.fieldPays2());
		tag.putInt("field12", r.fieldPays12());
		return tag;
	}
}
