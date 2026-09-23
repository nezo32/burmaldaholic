package dev.nezo.burmaldaholic.games.blackjack;

import dev.nezo.burmaldaholic.core.config.CasinoConfig;
import dev.nezo.burmaldaholic.core.config.sections.BlackjackConfig;
import dev.nezo.burmaldaholic.core.economy.Economies;
import dev.nezo.burmaldaholic.core.rng.OddsService;
import dev.nezo.burmaldaholic.core.service.CoreServices;
import dev.nezo.burmaldaholic.core.service.VipTiers;
import dev.nezo.burmaldaholic.core.table.CasinoTableBlockEntity;
import dev.nezo.burmaldaholic.core.table.TableSeats;
import dev.nezo.burmaldaholic.core.table.TableType;
import dev.nezo.burmaldaholic.core.text.Texts;
import dev.nezo.burmaldaholic.core.util.Result;
import dev.nezo.burmaldaholic.core.wager.BetLimits;
import dev.nezo.burmaldaholic.games.blackjack.logic.BlackjackRound;
import dev.nezo.burmaldaholic.games.blackjack.logic.BlackjackRound.Action;
import dev.nezo.burmaldaholic.games.blackjack.logic.BlackjackRound.Hand;
import dev.nezo.burmaldaholic.games.blackjack.logic.BlackjackRound.Offer;
import dev.nezo.burmaldaholic.games.blackjack.logic.BlackjackRound.Phase;
import dev.nezo.burmaldaholic.games.blackjack.logic.BlackjackRound.Seat;
import dev.nezo.burmaldaholic.games.blackjack.logic.BlackjackRound.SeatBet;
import dev.nezo.burmaldaholic.games.blackjack.logic.BlackjackRound.Turn;
import dev.nezo.burmaldaholic.games.blackjack.logic.BlackjackRules;
import dev.nezo.burmaldaholic.games.blackjack.logic.Card;
import dev.nezo.burmaldaholic.games.blackjack.logic.Shoe;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Server-authoritative blackjack table (GAME_DESIGN §6). The rules live in {@link BlackjackRound}
 * (pure); this class runs the table phases, timers and money:
 *
 * <pre>
 * betting   seated players bet ("Deal"); the round starts when every seated player has bet, or
 *           blackjack.betTimerTicks after the first bet (timer "bet"). One player: immediately.
 * insurance dealer shows an ace: insurance 0..bet/2 or even money; timer "insurance" → no
 * turns     seats in order; timer "turn" per decision → auto stand; disconnect → stand
 * result    everything settled; 60 ticks (timer "result") → betting
 * </pre>
 *
 * All chips put at risk (main bet, doubles, splits, insurance) go through {@code placeBet} into one open
 * stake per player, settled once with the seat's total return when the seat is resolved. Unfinished
 * rounds are refunded by core on reload (the round itself is not persisted, GAME_DESIGN §4.1).
 */
public class BlackjackTableBlockEntity extends CasinoTableBlockEntity {
	public static final String BETTING = "betting", INSURANCE = "insurance", TURNS = "turns", RESULT = "result";
	static final int RESULT_TICKS = 60;
	static final int SHUFFLE_NOTICE_TICKS = 40;
	static final int PEEK_NOTICE_TICKS = 30;

	private final boolean highRoller;
	private Shoe shoe;
	private BlackjackRound round;
	/** main bets placed in the current betting phase */
	private final Map<UUID, Long> mainBets = new LinkedHashMap<>();
	private final Map<UUID, Long> lastBet = new HashMap<>();
	/** round participants who left (auto-stand; still settled, offline-safe) */
	private final Set<UUID> away = new HashSet<>();
	private final Set<UUID> paid = new HashSet<>();
	private final Map<UUID, String> names = new HashMap<>();
	private String noticeKey = "";
	private long noticeUntil;
	private int lastTurnSeat = -1;

	public BlackjackTableBlockEntity(TableType<BlackjackTableBlockEntity> type, BlockPos pos, BlockState state) {
		super(type, pos, state);
		this.highRoller = type.name().endsWith("high_roller");
		setPhase(BETTING);
	}

	public boolean isHighRoller() {
		return highRoller;
	}

	/** Current round (null between rounds). Exposed for GameTests. */
	public BlackjackRound round() {
		return round;
	}

	static BlackjackRules rules() {
		BlackjackConfig c = CasinoConfig.blackjack();
		return new BlackjackRules(c.decks, c.penetration, c.dealerHitsSoft17, c.blackjackPayout, c.doubleAfterSplit, c.maxHands,
			c.resplitAces, c.insurance, c.lateSurrender);
	}

	// ---- table configuration ------------------------------------------------------------------

	@Override
	protected int seatCount() {
		return CasinoConfig.blackjack().seats;
	}

	@Override
	protected long minBet() {
		return highRoller ? CasinoConfig.blackjack().highRollerMinBet : CasinoConfig.blackjack().minBet;
	}

	/** High-Roller table: max = highRollerMaxMultiplier × tier max (GAME_DESIGN §6.4), owner max still applies. */
	@Override
	public long[] limitsFor(ServerPlayer player) {
		long[] base = super.limitsFor(player);
		if (!highRoller) {
			return base;
		}
		MinecraftServer server = player.level().getServer();
		long tierMax = CoreServices.vip().maxBet(server, player.getUUID());
		long max = (long) Math.floor(tierMax * CasinoConfig.blackjack().highRollerMaxMultiplier);
		long ownerMax = ownership().map(o -> o.maxBet()).orElse(0L);
		if (ownerMax > 0) {
			max = Math.min(max, ownerMax);
		}
		return new long[] {base[0], max};
	}

	private boolean vipAllowed(ServerPlayer player) {
		if (!highRoller) {
			return true;
		}
		int tier = CoreServices.vip().tier(player.level().getServer(), player.getUUID());
		if (tier >= VipTiers.GOLD) {
			return true;
		}
		sendError(player, Component.translatable("gui.burmaldaholic.error.vip_required", VipTiers.name(VipTiers.GOLD)));
		return false;
	}

	// ---- seats ----------------------------------------------------------------------------------

	@Override
	public boolean sit(ServerPlayer player) {
		if (isSeated(player)) {
			return true;
		}
		if (!vipAllowed(player) || !super.sit(player)) {
			return false;
		}
		names.put(player.getUUID(), player.getName().getString());
		tellSeated(Component.translatable("msg.burmaldaholic.blackjack.player_joined", player.getDisplayName()), player.getUUID());
		return true;
	}

	@Override
	protected boolean canLeaveNow(UUID player) {
		return !isActiveParticipant(player);
	}

	@Override
	protected void onPlayerLeft(UUID player, LeaveReason reason) {
		String name = names.getOrDefault(player, "");
		tellSeated(Component.translatable("msg.burmaldaholic.blackjack.player_left", Texts.raw(name)), player);
		if (isActiveParticipant(player)) {
			// GAME_DESIGN §4.1: the round is auto-completed with the default action (stand), paid out later.
			away.add(player);
			Seat seat = seatOf(player);
			if (seat != null && round.phase() != Phase.DONE) {
				round.standAll(seat.seat);
				step();
			}
			return;
		}
		mainBets.remove(player);
		super.onPlayerLeft(player, reason); // refunds a bet placed in the betting phase
		if (BETTING.equals(phase())) {
			if (mainBets.isEmpty()) {
				cancelTimer("bet");
			} else if (allSeatedHaveBet()) {
				startRound();
			}
		}
	}

	private boolean isActiveParticipant(UUID player) {
		if (round == null || paid.contains(player)) {
			return false;
		}
		return seatOf(player) != null;
	}

	private Seat seatOf(UUID player) {
		if (round == null) {
			return null;
		}
		for (Seat s : round.seats()) {
			if (s.player.equals(player)) {
				return s;
			}
		}
		return null;
	}

	private void tellSeated(Component message, UUID except) {
		if (!(level instanceof ServerLevel serverLevel)) {
			return;
		}
		for (TableSeats.Seat s : seats().occupied()) {
			if (s.player().equals(except)) {
				continue;
			}
			ServerPlayer p = serverLevel.getServer().getPlayerList().getPlayer(s.player());
			if (p != null) {
				p.sendSystemMessage(message);
			}
		}
	}

	private ServerPlayer online(UUID id) {
		return level instanceof ServerLevel serverLevel ? serverLevel.getServer().getPlayerList().getPlayer(id) : null;
	}

	// ---- actions --------------------------------------------------------------------------------

	@Override
	public void onAction(ServerPlayer player, String action, CompoundTag args) {
		if (!CasinoConfig.blackjack().enabled) {
			sendError(player, Component.translatable("gui.burmaldaholic.error.disabled"));
			return;
		}
		switch (action) {
			case "bet" -> bet(player, args.getLongOr("amount", 0));
			case "insurance" -> insurance(player, args.getLongOr("amount", -1));
			case "even_money" -> evenMoney(player, args.getBooleanOr("take", false));
			default -> {
				Action a = Action.byId(action);
				if (a != null) {
					play(player, a);
				}
			}
		}
		syncViewers();
	}

	private void bet(ServerPlayer player, long amount) {
		if (!BETTING.equals(phase())) {
			sendError(player, Component.translatable("gui.burmaldaholic.error.round_in_progress"));
			return;
		}
		if (mainBets.containsKey(player.getUUID()) || !sit(player)) {
			return;
		}
		long worstCase = BlackjackRules.worstCasePayout(amount);
		Result<Long> r;
		if (highRoller) {
			long[] lim = limitsFor(player);
			long balance = Economies.get().balance(player);
			int tier = CoreServices.vip().tier(player.level().getServer(), player.getUUID());
			BetLimits.Violation v = BetLimits.check(amount, lim[0], lim[1], lim[1], balance);
			Component err = BetLimits.message(v, lim[0], lim[1], lim[1], tier, balance);
			if (err != null) {
				sendError(player, err);
				return;
			}
			r = placeBet(player, amount, lim[0], 0, worstCase, false);
		} else {
			r = placeBet(player, amount, worstCase);
		}
		if (!r.isOk()) {
			return;
		}
		mainBets.put(player.getUUID(), amount);
		lastBet.put(player.getUUID(), amount);
		if (allSeatedHaveBet()) {
			startRound();
		} else if (ticksLeft("bet") < 0) {
			int ticks = CasinoConfig.blackjack().betTimerTicks;
			startTimer("bet", ticks);
			tellSeated(Component.translatable("msg.burmaldaholic.blackjack.round_starts_in",
				Texts.plural("unit.burmaldaholic.second_acc", (ticks + 19) / 20)), player.getUUID());
		}
	}

	private boolean allSeatedHaveBet() {
		if (mainBets.isEmpty()) {
			return false;
		}
		for (TableSeats.Seat s : seats().occupied()) {
			if (!mainBets.containsKey(s.player())) {
				return false;
			}
		}
		return true;
	}

	private void insurance(ServerPlayer player, long amount) {
		Seat seat = seatOf(player.getUUID());
		if (round == null || seat == null || away.contains(player.getUUID()) || round.offer(seat.seat) != Offer.INSURANCE) {
			return;
		}
		if (amount < 0 || amount > BlackjackRules.maxInsurance(seat.bet)) {
			sendError(player, Component.translatable("gui.burmaldaholic.error.invalid_amount"));
			return;
		}
		if (amount > 0 && !placeBet(player, amount, 1, 0, 0, false).isOk()) {
			return;
		}
		round.insure(seat.seat, amount);
		step();
	}

	private void evenMoney(ServerPlayer player, boolean take) {
		Seat seat = seatOf(player.getUUID());
		if (round == null || seat == null || away.contains(player.getUUID()) || round.offer(seat.seat) != Offer.EVEN_MONEY) {
			return;
		}
		round.evenMoney(seat.seat, take);
		step();
	}

	private void play(ServerPlayer player, Action action) {
		Turn t = round == null ? null : round.current();
		if (t == null || !t.seat().player.equals(player.getUUID())) {
			sendError(player, Component.translatable("gui.burmaldaholic.error.not_your_turn"));
			return;
		}
		int seatNo = t.seat().seat;
		if (!round.legal(seatNo, Economies.get().balance(player)).contains(action)) {
			return;
		}
		long extra = round.extraStake(seatNo, action);
		// Doubles/splits may exceed the table max (GAME_DESIGN §6.4); funds are still checked.
		if (extra > 0 && !placeBet(player, extra, 1, 0, 0, false).isOk()) {
			return;
		}
		round.act(seatNo, action);
		step();
	}

	// ---- round flow -------------------------------------------------------------------------------

	private void startRound() {
		cancelTimer("bet");
		List<SeatBet> bets = new ArrayList<>();
		for (Map.Entry<UUID, Long> e : mainBets.entrySet()) {
			var seat = seats().seatOf(e.getKey());
			if (seat.isPresent() && stakeOf(e.getKey()) >= e.getValue()) {
				bets.add(new SeatBet(seat.getAsInt(), e.getKey(), e.getValue()));
			} else {
				refund(e.getKey());
			}
		}
		mainBets.clear();
		if (bets.isEmpty()) {
			setPhase(BETTING);
			return;
		}
		BlackjackRules rules = rules();
		if (shoe == null || shoe.decks() != rules.decks()) {
			shoe = new Shoe(bound -> OddsService.get().fair().nextInt(bound), rules.decks());
			notice("gui.burmaldaholic.blackjack.shuffling", SHUFFLE_NOTICE_TICKS);
		} else if (shoe.needsShuffle(rules.penetration())) {
			shoe.shuffle();
			notice("gui.burmaldaholic.blackjack.shuffling", SHUFFLE_NOTICE_TICKS);
		}
		away.clear();
		paid.clear();
		lastTurnSeat = -1;
		for (SeatBet b : bets) {
			ServerPlayer p = online(b.player());
			if (p != null) {
				names.put(b.player(), p.getName().getString());
			}
		}
		round = new BlackjackRound(rules, shoe, bets);
		if (round.peeked() && round.phase() == Phase.TURNS && !noticeKey.equals("gui.burmaldaholic.blackjack.shuffling")) {
			notice("gui.burmaldaholic.blackjack.dealer_peeks", PEEK_NOTICE_TICKS);
		}
		setChanged();
		step();
	}

	private void notice(String key, int ticks) {
		noticeKey = key;
		noticeUntil = gameTime() + ticks;
	}

	/** Drives the round after every change: pays settled seats, starts the right timer or finishes. */
	private void step() {
		if (round == null) {
			return;
		}
		payNewlySettled();
		switch (round.phase()) {
			case INSURANCE -> {
				for (Seat s : round.pendingInsurance()) {
					if (away.contains(s.player)) {
						round.decline(s.seat);
					}
				}
				if (round.phase() != Phase.INSURANCE) {
					step();
					return;
				}
				setPhase(INSURANCE);
				if (ticksLeft("insurance") < 0) {
					startTimer("insurance", CasinoConfig.blackjack().insuranceTimerTicks);
				}
			}
			case TURNS -> {
				cancelTimer("insurance");
				if (INSURANCE.equals(phase()) && round.peeked()) {
					notice("gui.burmaldaholic.blackjack.dealer_peeks", PEEK_NOTICE_TICKS);
				}
				setPhase(TURNS);
				Turn t = round.current();
				if (t == null) {
					finish();
					return;
				}
				if (away.contains(t.seat().player) || online(t.seat().player) == null) {
					round.standAll(t.seat().seat);
					step();
					return;
				}
				startTimer("turn", CasinoConfig.blackjack().turnTimerTicks);
				if (t.seat().seat != lastTurnSeat) {
					lastTurnSeat = t.seat().seat;
					ServerPlayer p = online(t.seat().player);
					if (p != null) {
						p.sendOverlayMessage(Component.translatable("gui.burmaldaholic.blackjack.your_turn"));
					}
				}
			}
			case DONE -> finish();
		}
	}

	private void payNewlySettled() {
		for (Seat s : round.seats()) {
			if (!s.settled || paid.contains(s.player)) {
				continue;
			}
			paid.add(s.player);
			long ret = round.returnOf(s.seat);
			long net = ret - round.stakedOf(s.seat);
			settle(s.player, ret);
			ServerPlayer p = online(s.player);
			if (p != null && away.contains(s.player)) {
				p.sendSystemMessage(Component.translatable("msg.burmaldaholic.core.auto_completed", netText(net)));
			}
		}
	}

	static Component netText(long net) {
		if (net > 0) {
			return Component.translatable("gui.burmaldaholic.common.result.win", Texts.number(net));
		}
		if (net < 0) {
			return Component.translatable("gui.burmaldaholic.common.result.loss", Texts.number(-net));
		}
		return Component.translatable("gui.burmaldaholic.common.result.push");
	}

	private void finish() {
		payNewlySettled();
		cancelTimer("turn");
		cancelTimer("insurance");
		setPhase(RESULT);
		startTimer("result", RESULT_TICKS);
		setChanged();
	}

	private void toBetting() {
		round = null;
		away.clear();
		paid.clear();
		lastTurnSeat = -1;
		setPhase(BETTING);
	}

	@Override
	protected void onTimer(String id) {
		switch (id) {
			case "bet" -> {
				if (BETTING.equals(phase())) {
					startRound();
				}
			}
			case "insurance" -> {
				if (round != null && round.phase() == Phase.INSURANCE) {
					for (Seat s : round.pendingInsurance()) {
						round.decline(s.seat);
					}
					step();
				}
			}
			case "turn" -> {
				Turn t = round == null ? null : round.current();
				if (t != null) {
					round.standAll(t.seat().seat);
					ServerPlayer p = online(t.seat().player);
					if (p != null) {
						p.sendSystemMessage(Component.translatable("msg.burmaldaholic.blackjack.auto_stand"));
					}
					step();
				}
			}
			case "result" -> toBetting();
			default -> {
			}
		}
		syncViewers();
	}

	// ---- client state -------------------------------------------------------------------------------

	@Override
	public CompoundTag writeClientState(ServerPlayer viewer) {
		CompoundTag tag = baseState(viewer);
		UUID me = viewer.getUUID();
		tag.putBoolean("high_roller", highRoller);
		tag.putLong("last_bet", lastBet.getOrDefault(me, 0L));
		tag.putBoolean("bet_placed", mainBets.containsKey(me));
		if (!noticeKey.isEmpty() && gameTime() < noticeUntil) {
			tag.putString("notice", noticeKey);
		}
		ListTag betList = new ListTag();
		for (Map.Entry<UUID, Long> e : mainBets.entrySet()) {
			seats().seatOf(e.getKey()).ifPresent(i -> {
				CompoundTag b = new CompoundTag();
				b.putInt("seat", i);
				b.putLong("amount", e.getValue());
				betList.add(b);
			});
		}
		tag.put("bets", betList);
		if (round == null) {
			return tag;
		}
		List<Card> dealer = round.dealerCards();
		boolean reveal = round.holeRevealed();
		tag.put("dealer", codes(reveal ? dealer : dealer.subList(0, 1)));
		tag.putBoolean("hole_hidden", !reveal);
		Turn turn = round.current();
		tag.putInt("current_seat", turn == null ? -1 : turn.seat().seat);
		tag.putInt("current_hand", turn == null ? -1 : turn.handIndex());
		ListTag players = new ListTag();
		for (Seat s : round.seats()) {
			CompoundTag p = new CompoundTag();
			p.putInt("seat", s.seat);
			p.putString("name", names.getOrDefault(s.player, ""));
			p.putBoolean("you", s.player.equals(me));
			p.putBoolean("away", away.contains(s.player));
			p.putLong("bet", s.bet);
			p.putLong("insurance", s.insurance);
			p.putBoolean("settled", s.settled);
			if (s.settled) {
				p.putLong("net", round.returnOf(s.seat) - round.stakedOf(s.seat));
				p.putLong("insurance_return", s.insuranceReturn);
			}
			ListTag hands = new ListTag();
			for (Hand h : s.hands) {
				CompoundTag ht = new CompoundTag();
				ht.put("cards", codes(h.cards));
				ht.putLong("bet", h.bet);
				ht.putBoolean("doubled", h.doubled);
				ht.putBoolean("split", h.split);
				if (h.outcome != null) {
					ht.putString("outcome", h.outcome.id());
					ht.putLong("ret", h.ret);
				}
				hands.add(ht);
			}
			p.put("hands", hands);
			players.add(p);
		}
		tag.put("players", players);
		Seat mine = seatOf(me);
		if (mine != null && !away.contains(me)) {
			Offer offer = round.offer(mine.seat);
			if (offer != null) {
				tag.putString("offer", offer == Offer.INSURANCE ? "insurance" : "even_money");
				tag.putLong("insurance_max", Math.min(BlackjackRules.maxInsurance(mine.bet), Economies.get().balance(viewer)));
			}
			ListTag legal = new ListTag();
			for (Action a : round.legal(mine.seat, Economies.get().balance(viewer))) {
				legal.add(StringTag.valueOf(a.id()));
			}
			tag.put("legal", legal);
		}
		return tag;
	}

	private static IntArrayTag codes(List<Card> cards) {
		int[] out = new int[cards.size()];
		for (int i = 0; i < out.length; i++) {
			out[i] = cards.get(i).code();
		}
		return new IntArrayTag(out);
	}

	/** For GameTests: the viewer's participation, if any. */
	Optional<Seat> participant(UUID player) {
		return Optional.ofNullable(seatOf(player));
	}
}
