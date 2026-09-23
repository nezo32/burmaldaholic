package dev.nezo.burmaldaholic.games.craps;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.nezo.burmaldaholic.Burmaldaholic;
import dev.nezo.burmaldaholic.core.config.CasinoConfig;
import dev.nezo.burmaldaholic.core.config.sections.CrapsConfig;
import dev.nezo.burmaldaholic.core.economy.AccountId;
import dev.nezo.burmaldaholic.core.economy.Economies;
import dev.nezo.burmaldaholic.core.economy.Economy;
import dev.nezo.burmaldaholic.core.economy.Economy.Transaction;
import dev.nezo.burmaldaholic.core.events.CasinoEvents;
import dev.nezo.burmaldaholic.core.mode.CasinoMode;
import dev.nezo.burmaldaholic.core.rng.CasinoRng;
import dev.nezo.burmaldaholic.core.rng.OddsContext;
import dev.nezo.burmaldaholic.core.rng.OddsService;
import dev.nezo.burmaldaholic.core.service.TableOwnershipProvider.OwnedTable;
import dev.nezo.burmaldaholic.core.table.CasinoTableBlockEntity;
import dev.nezo.burmaldaholic.core.table.TableSeats;
import dev.nezo.burmaldaholic.core.table.TableType;
import dev.nezo.burmaldaholic.core.text.Texts;
import dev.nezo.burmaldaholic.core.wager.BetLimits;
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
import java.util.Optional;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

/**
 * Server side of a craps table (GAME_DESIGN §10). Rules and the point state machine live in the
 * pure {@link CrapsTable}; this class adds money, timers, seats, messages and client sync.
 *
 * <p><b>Money</b>: craps bets resolve one by one over many rolls, but core's open stake is one
 * aggregate per player that {@code settle} closes completely. So every bet (flat + odds) is escrowed
 * here on its own ({@link Escrow}), with the same semantics as core's {@code placeBet}/{@code settle}:
 * limits via {@link BetLimits}, owned-table bankroll reservation (§18.2), {@code PLAY_RESOLVED} per
 * settled bet, refund when the table is broken or loaded again with open bets (§4.1).
 *
 * <p><b>Leaving</b> (button, disconnect, distance): the player's bets are played out immediately
 * with honest dice (§4.1 "bets stay working until resolved", same expected value). Table broken or
 * casino mode switched off: bets are refunded.
 */
public class CrapsTableBlockEntity extends CasinoTableBlockEntity {
	static final String TIMER_WINDOW = "window";
	static final String TIMER_ROLL = "roll";
	private static final String ESCROW_KEY = "craps_escrow";
	private static final String K = "gui.burmaldaholic.craps.";
	private static final String M = "msg.burmaldaholic.craps.";

	/** Chips escrowed for one bet (flat + odds). {@code bankroll} empty = house table. */
	record Escrow(UUID player, long amount, long reserved, String bankroll) {
		static final Codec<Escrow> CODEC = RecordCodecBuilder.create(i -> i.group(
			UUIDUtil.CODEC.fieldOf("player").forGetter(Escrow::player),
			Codec.LONG.fieldOf("amount").forGetter(Escrow::amount),
			Codec.LONG.fieldOf("reserved").forGetter(Escrow::reserved),
			Codec.STRING.fieldOf("bankroll").forGetter(Escrow::bankroll)
		).apply(i, Escrow::new));

		boolean house() {
			return bankroll.isEmpty();
		}

		Escrow plus(Escrow more) {
			return new Escrow(player, amount + more.amount, reserved + more.reserved, bankroll);
		}
	}

	private final CrapsTable table = new CrapsTable(rules());
	private final Map<Integer, Escrow> escrows = new LinkedHashMap<>();
	private boolean refundPending;
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
		Escrow escrow = debit(player, amount, CrapsMath.flatWorstCase(kind, amount, table.rules()), true);
		if (escrow == null) {
			return;
		}
		Bet bet = table.addBet(player.getUUID(), kind, amount);
		escrows.put(bet.id(), escrow);
		setChanged();
		rearm();
	}

	void placeOdds(ServerPlayer player, int betId, long amount) {
		Bet bet = table.bet(betId);
		if (bet == null || !bet.owner().equals(player.getUUID()) || !escrows.containsKey(betId)) {
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
		Escrow more = debit(player, amount, CrapsMath.oddsWorstCase(info.side(), info.point(), amount), false);
		if (more == null) {
			return;
		}
		table.addOdds(betId, amount);
		escrows.merge(betId, more, Escrow::plus);
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
		String shooterName = nameOf(table.shooter());
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
			Escrow escrow = escrows.remove(bet.id());
			if (escrow != null) {
				pay(escrow, res.totalReturn());
			}
			if (res.oddsReturned()) {
				lines.add(Component.translatable(M + "odds_returned").withStyle(ChatFormatting.GRAY));
			}
			lines.add(resultLine(res));
		}
		setChanged();

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
		super.onPlayerLeft(player, reason); // core aggregate stake: never used here, refunds if present
		int point = table.point();
		List<Bet> mine = table.removeOwner(player);
		if (!mine.isEmpty() && level instanceof ServerLevel sl) {
			MinecraftServer server = sl.getServer();
			ServerPlayer online = server.getPlayerList().getPlayer(player);
			if (reason == LeaveReason.REMOVED) {
				for (Bet b : mine) {
					Escrow e = escrows.remove(b.id());
					if (e != null) {
						refundEscrow(server, e, false);
					}
				}
				if (online != null) {
					online.sendSystemMessage(Component.translatable(M + "bets_refunded"));
				}
			} else {
				CasinoRng rng = OddsService.get().rng(new OddsContext(player, gameId(), 0));
				Map<Integer, Long> results = CrapsResolver.autoComplete(point, mine, rng::nextInt, table.rules());
				long net = 0;
				for (Bet b : mine) {
					Escrow e = escrows.remove(b.id());
					long ret = results.getOrDefault(b.id(), b.staked());
					if (e != null) {
						pay(e, ret);
						net += ret - e.amount();
					}
				}
				if (online != null) {
					online.sendSystemMessage(Component.translatable(M + "bets_played_out", outcome(net)));
				}
			}
			setChanged();
		}
		rearm();
	}

	// ---- money ----------------------------------------------------------------------------------

	/**
	 * Validates and debits one stake, same rules as core's {@code placeBet}.
	 *
	 * @return the escrow, or null (error already shown)
	 */
	@Nullable Escrow debit(ServerPlayer player, long amount, long worstCase, boolean checkLimits) {
		long min = minBet();
		long tableMax = 0;
		Optional<OwnedTable> owned = ownership();
		if (owned.isPresent()) {
			OwnedTable o = owned.get();
			if (o.owner().equals(player.getUUID())) {
				sendError(player, Component.translatable("gui.burmaldaholic.error.owner_cannot_play"));
				return null;
			}
			if (!o.open()) {
				sendError(player, Component.translatable("gui.burmaldaholic.error.table_closed"));
				return null;
			}
			min = Math.max(min, o.minBet());
			tableMax = o.maxBet() > 0 ? o.maxBet() : 0;
		}
		Economy eco = Economies.get();
		Component err;
		if (checkLimits) {
			err = BetLimits.validate(player, amount, min, tableMax);
		} else if (amount <= 0) {
			err = Component.translatable("gui.burmaldaholic.error.invalid_amount");
		} else if (eco.balance(player) < amount) {
			err = Component.translatable("gui.burmaldaholic.error.insufficient_funds", Texts.number(eco.balance(player)));
		} else {
			err = null;
		}
		if (err != null) {
			sendError(player, err);
			return null;
		}
		MinecraftServer server = player.level().getServer();
		String bankroll = owned.map(OwnedTable::bankrollId).orElse("");
		long reserve = 0;
		if (!bankroll.isEmpty()) {
			reserve = Math.max(0, worstCase);
			if (!eco.bankrolls(server).reserve(bankroll, reserve)) {
				long available = eco.bankrolls(server).get(bankroll).map(Economy.BankrollInfo::available).orElse(0L);
				sendError(player, Component.translatable(available <= 0 ? "gui.burmaldaholic.error.house_broke" : "gui.burmaldaholic.error.exposure"));
				return null;
			}
		}
		AccountId bank = bankroll.isEmpty() ? AccountId.HOUSE : AccountId.bankroll(bankroll);
		if (!eco.transfer(server, AccountId.player(player.getUUID()), bank, amount, Transaction.bet(gameId())).ok()) {
			if (reserve > 0) {
				eco.bankrolls(server).release(bankroll, reserve);
			}
			sendError(player, Component.translatable("gui.burmaldaholic.error.insufficient_funds", Texts.number(eco.balance(player))));
			return null;
		}
		return new Escrow(player.getUUID(), amount, reserve, bankroll);
	}

	/** Settles one escrow with a total return (stake included); fires PLAY_RESOLVED for online players. */
	private void pay(Escrow e, long totalReturn) {
		if (!(level instanceof ServerLevel sl)) {
			return;
		}
		MinecraftServer server = sl.getServer();
		Economy eco = Economies.get();
		if (!e.house()) {
			eco.bankrolls(server).release(e.bankroll(), e.reserved());
		}
		if (totalReturn > 0) {
			AccountId bank = e.house() ? AccountId.HOUSE : AccountId.bankroll(e.bankroll());
			if (!eco.transfer(server, bank, AccountId.player(e.player()), totalReturn, Transaction.payout(gameId())).ok()) {
				Burmaldaholic.LOGGER.error("Bankroll {} could not pay {} to {} at {}", e.bankroll(), totalReturn, e.player(), worldPosition);
				eco.transfer(server, AccountId.HOUSE, AccountId.player(e.player()), totalReturn, Transaction.payout(gameId()));
			}
		}
		ServerPlayer online = server.getPlayerList().getPlayer(e.player());
		if (online != null) {
			CasinoEvents.PLAY_RESOLVED.invoker().onPlayResolved(online, new CasinoEvents.PlayResult(gameId(), e.amount(), totalReturn));
		}
		if (CasinoConfig.debug().logRounds) {
			Burmaldaholic.LOGGER.info("[round] {} at {}: player {} staked {} payout {}", gameId(), worldPosition, e.player(), e.amount(), totalReturn);
		}
	}

	private void refundEscrow(MinecraftServer server, Escrow e, boolean notify) {
		Economy eco = Economies.get();
		AccountId bank = e.house() ? AccountId.HOUSE : AccountId.bankroll(e.bankroll());
		if (!e.house()) {
			eco.bankrolls(server).release(e.bankroll(), e.reserved());
		}
		if (!eco.transfer(server, bank, AccountId.player(e.player()), e.amount(), Transaction.refund(gameId())).ok()) {
			eco.transfer(server, AccountId.HOUSE, AccountId.player(e.player()), e.amount(), Transaction.refund(gameId()));
		}
		ServerPlayer online = server.getPlayerList().getPlayer(e.player());
		if (notify && online != null) {
			online.sendSystemMessage(Component.translatable("msg.burmaldaholic.core.round_refunded", Texts.chipsAcc(e.amount())));
		}
	}

	/** Returns every open bet (casino mode off, table broken). */
	private void refundAll(MinecraftServer server, boolean notify) {
		java.util.Set<UUID> players = new java.util.HashSet<>();
		for (Escrow e : new ArrayList<>(escrows.values())) {
			refundEscrow(server, e, false);
			players.add(e.player());
		}
		escrows.clear();
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

	/** Total chips escrowed at this table (tests). */
	public long escrowed() {
		return escrows.values().stream().mapToLong(Escrow::amount).sum();
	}

	// ---- ticking & lifecycle --------------------------------------------------------------------

	@Override
	protected void serverTick(ServerLevel level) {
		MinecraftServer server = level.getServer();
		if (refundPending) {
			refundPending = false;
			if (CasinoConfig.core().roundTimeoutRefund) {
				escrows.values().forEach(e -> Burmaldaholic.LOGGER.info("Refunding craps bet at {}: {} chips to {}", worldPosition, e.amount(), e.player()));
				new ArrayList<>(escrows.values()).forEach(e -> refundEscrow(server, e, true));
			}
			escrows.clear();
			table.clear();
			setChanged();
		}
		if (server.getTickCount() % 20 == 0 && !escrows.isEmpty() && !CasinoMode.isEnabled(level)) {
			refundAll(server, true);
			rearm();
			syncViewers();
		}
	}

	@Override
	public void preRemoveSideEffects(BlockPos pos, BlockState state) {
		super.preRemoveSideEffects(pos, state);
		if (level instanceof ServerLevel sl && !escrows.isEmpty()) {
			refundAll(sl.getServer(), true);
		}
	}

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		if (!escrows.isEmpty()) {
			output.store(ESCROW_KEY, Escrow.CODEC.listOf(), List.copyOf(escrows.values()));
		}
	}

	@Override
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		escrows.clear();
		// Bets are never restored: open ones are refunded on the first tick (§4.1). Keys are placeholders.
		input.read(ESCROW_KEY, Escrow.CODEC.listOf()).ifPresent(list -> {
			int i = 0;
			for (Escrow e : list) {
				escrows.put(--i, e);
			}
		});
		refundPending = !escrows.isEmpty();
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
