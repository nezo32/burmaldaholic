package dev.nezo.burmaldaholic.games.blackjack;

import dev.nezo.burmaldaholic.Burmaldaholic;
import dev.nezo.burmaldaholic.core.CoreSounds;
import dev.nezo.burmaldaholic.core.advancement.CasinoAdvancements;
import dev.nezo.burmaldaholic.core.bots.AtmosphereBots;
import dev.nezo.burmaldaholic.core.bots.BotNames;
import dev.nezo.burmaldaholic.core.bots.BotTable;
import dev.nezo.burmaldaholic.core.bots.logic.VirtualSeats;
import dev.nezo.burmaldaholic.core.bots.logic.VirtualSeats.VirtualBot;
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
import dev.nezo.burmaldaholic.core.anim.WinTier;
import dev.nezo.burmaldaholic.core.anim.WinTierTable;
import dev.nezo.burmaldaholic.core.anim.cards.DealerGesture;
import dev.nezo.burmaldaholic.games.blackjack.logic.BlackjackBeats;
import dev.nezo.burmaldaholic.games.blackjack.logic.BlackjackBotPolicy;
import dev.nezo.burmaldaholic.games.blackjack.logic.BlackjackBotPolicy.BetMemory;
import dev.nezo.burmaldaholic.games.blackjack.logic.BlackjackRound;
import dev.nezo.burmaldaholic.games.blackjack.logic.BlackjackRound.Action;
import dev.nezo.burmaldaholic.games.blackjack.logic.BlackjackRound.Hand;
import dev.nezo.burmaldaholic.games.blackjack.logic.BlackjackRound.Offer;
import dev.nezo.burmaldaholic.games.blackjack.logic.BlackjackRound.Outcome;
import dev.nezo.burmaldaholic.games.blackjack.logic.BlackjackRound.Phase;
import dev.nezo.burmaldaholic.games.blackjack.logic.BlackjackRound.Seat;
import dev.nezo.burmaldaholic.games.blackjack.logic.BlackjackRound.SeatBet;
import dev.nezo.burmaldaholic.games.blackjack.logic.BlackjackRound.Turn;
import dev.nezo.burmaldaholic.games.blackjack.logic.BlackjackRules;
import dev.nezo.burmaldaholic.games.blackjack.logic.Card;
import dev.nezo.burmaldaholic.games.blackjack.logic.CardSource;
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
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

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
 *
 * <p><b>Seats &amp; Bots</b> (BOTS.md §4.7): ATMOSPHERE bots sit on free seats ({@link AtmosphereBots},
 * highest seat first) with VIRTUAL bets (never placed, paid or reserved; only human seats are settled)
 * and play REAL cards from the shared shoe with {@link BlackjackBotPolicy} and the bot rng only. They
 * are always ready (a single human deals as fast as solo play), act in seat order after a short think
 * (timer "bot") and never use the human turn timer. Safe point: start of / any time during BETTING.
 */
public class BlackjackTableBlockEntity extends CasinoTableBlockEntity implements BotTable.Delegating {
	public static final String BETTING = "betting", INSURANCE = "insurance", TURNS = "turns", RESULT = "result";
	/** RESULT after the reveal gate (cards.md §8 {@code blackjack.resultTicks}; was 60). */
	static final int RESULT_TICKS = 80;
	static final int SHUFFLE_NOTICE_TICKS = 40;
	static final int PEEK_NOTICE_TICKS = 30;
	static final String PEEK_NOTICE = "gui.burmaldaholic.blackjack.dealer_peeks";

	private final boolean highRoller;
	private Shoe shoe;
	private BlackjackRound round;
	/** When each card of the round becomes public (C1 / J-C1): the round resolves at once, publication is paced. */
	private BlackjackBeats beats;
	/** Next tick at which something becomes public (-1 = nothing pending). */
	private long nextPub = -1;
	private long lastPub;
	private int roundSeq;
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
	/** Seats &amp; Bots: core TableBots + virtual bot seats (atmosphere: bets are virtual) */
	private final AtmosphereBots bots = new AtmosphereBots(this, BlackjackModule.ID, true);
	/** per bot: virtual betting memory and the game time its bet shows in the lobby */
	private final Map<String, BetMemory> botMem = new HashMap<>();
	private final Map<String, Long> botBetAt = new HashMap<>();
	/** bot seats of the current round (seat index → bot) */
	private final Map<Integer, VirtualBot> roundBots = new HashMap<>();
	/** the bots' round results were applied to their betting memory */
	private boolean botsDone;
	/** bots whose virtual bet is visible in the lobby (re-sync when it changes) */
	private int botBetsShown;

	public BlackjackTableBlockEntity(TableType<BlackjackTableBlockEntity> type, BlockPos pos, BlockState state) {
		super(type, pos, state);
		this.highRoller = type.name().endsWith("high_roller");
		setPhase(BETTING);
	}

	public boolean isHighRoller() {
		return highRoller || preset().map(p -> p.id().startsWith("high_roller")).orElse(false); // worldgen lounge preset
	}

	private final List<Card> stackedForTests = new ArrayList<>();

	/** GameTests: the next round deals these cards first (then the shoe), making it deterministic. */
	public void stackCardsForTests(List<Card> cards) {
		stackedForTests.clear();
		stackedForTests.addAll(cards);
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
		return isHighRoller() ? CasinoConfig.blackjack().highRollerMinBet : CasinoConfig.blackjack().minBet;
	}

	/** High-Roller table: max = highRollerMaxMultiplier × tier max (GAME_DESIGN §6.4), owner max still applies. */
	@Override
	public long[] limitsFor(ServerPlayer player) {
		long[] base = super.limitsFor(player);
		if (!isHighRoller()) {
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
		if (!isHighRoller()) {
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
		if (!vipAllowed(player)) {
			return false;
		}
		// Seats & Bots: private table, someone's BOTS_ONLY table, claimant of a bot seat (BOTS.md §3.2)
		Result<Boolean> admit = bots.admit(player);
		if (!admit.isOk()) {
			sendError(player, admit.error());
			return false;
		}
		if (!Boolean.TRUE.equals(admit.value()) || !super.sit(player)) {
			return false; // claimant: TableBots already said "a bot gives up its seat after this round"
		}
		bots.noteJoin(player.getUUID());
		names.put(player.getUUID(), player.getName().getString());
		tellSeated(Component.translatable("msg.burmaldaholic.blackjack.player_joined", player.getDisplayName()), player.getUUID());
		if (BETTING.equals(phase())) {
			botSafePoint(); // atmosphere safe point: any time during BETTING
		}
		return true;
	}

	@Override
	public BotTable botDelegate() {
		return bots;
	}

	/** Thinking dots: the bot on its turn while its think timer runs, else in BETTING the bot whose bet moment is next. */
	@Override
	public @org.jspecify.annotations.Nullable String botThinking() {
		if (ticksLeft("bot") >= 0) {
			Turn t = round == null ? null : round.current();
			VirtualBot bot = t == null ? null : roundBots.get(t.seat().seat);
			return bot == null ? null : bot.key;
		}
		return BETTING.equals(phase()) ? dev.nezo.burmaldaholic.core.bots.logic.ThinkingBot.next(botBetAt, gameTime()) : null;
	}

	/** Seats &amp; Bots of this table (tests, bots UI). */
	public AtmosphereBots bots() {
		return bots;
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
			botSafePoint(); // atmosphere safe point; the last human leaving ends the bot session
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
		if (isHighRoller()) {
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
		if (action == Action.STAND) {
			dealerGesture(DealerGesture.WAVE_OFF);
		}
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
		CardSource source = shoe;
		if (!stackedForTests.isEmpty()) {
			source = CardSource.stacked(List.copyOf(stackedForTests), shoe);
			stackedForTests.clear();
		}
		// Atmosphere bots: always ready, a VIRTUAL bet each (never placed through placeBet), real cards
		// from the shared shoe. Only human seats are ever settled (payNewlySettled).
		roundBots.clear();
		botsDone = false;
		List<SeatBet> all = new ArrayList<>(bets);
		for (VirtualBot bot : bots.bots()) {
			BetMemory mem = botMem.get(bot.key);
			if (mem == null || bets.stream().anyMatch(b -> b.seat() == bot.seat())) {
				continue;
			}
			roundBots.put(bot.seat(), bot);
			all.add(new SeatBet(bot.seat(), VirtualSeats.botUuid(bot.key), mem.next()));
		}
		round = new BlackjackRound(rules, source, all);
		boolean shuffling = noticeKey.equals("gui.burmaldaholic.blackjack.shuffling") && noticeUntil > gameTime();
		if (level != null && shuffling && CoreSounds.CARD_SHUFFLE != null) {
			level.playSound(null, worldPosition, CoreSounds.CARD_SHUFFLE, net.minecraft.sounds.SoundSource.BLOCKS, 0.8f, 1.0f);
			dealerGesture(DealerGesture.SHUFFLE);
		}
		// the deal is published one card per beat (after the riffle when the shoe was shuffled)
		BlackjackBeats.Config cfg = seats().occupied().size() == 1 ? BlackjackBeats.Config.DEFAULT.scaled(CasinoConfig.cards().soloSpeed) : BlackjackBeats.Config.DEFAULT;
		roundSeq++;
		lastPub = gameTime() - 1;
		beats = new BlackjackBeats(round, gameTime() + (shuffling ? SHUFFLE_NOTICE_TICKS / 2 : 0), cfg);
		nextPub = beats.nextChange(lastPub);
		if (round.peeked() && round.phase() == Phase.TURNS && !noticeKey.equals("gui.burmaldaholic.blackjack.shuffling")) {
			notice(PEEK_NOTICE, PEEK_NOTICE_TICKS);
		}
		setChanged();
		step();
	}

	private void notice(String key, int ticks) {
		noticeKey = key;
		noticeUntil = gameTime() + ticks;
	}

	/** Re-reads the round into the publication schedule (new cards one beat apart). */
	private void publish() {
		if (round == null || beats == null) {
			return;
		}
		beats.sync(round, gameTime());
		nextPub = beats.nextChange(lastPub);
	}

	/** Ticks until the last published card is readable (decision timers and RESULT start after it). */
	private int delay() {
		return beats == null ? 0 : beats.delay(gameTime());
	}

	/** A card is still moving: no decision, timer or result is shown yet. */
	private boolean busy() {
		return beats != null && beats.busy(gameTime());
	}

	/** GameTests: the publication schedule of the current round. */
	public BlackjackBeats beats() {
		return beats;
	}

	/** GameTests / screenshots: every scheduled beat is already public (timers move with it). */
	public void revealAllForTests() {
		if (beats != null) {
			long d = beats.delay(gameTime());
			if (d > 0) {
				for (String t : List.of("turn", "insurance", "bot", "result")) {
					if (ticksLeft(t) > 0) {
						startTimer(t, (int) Math.max(1, ticksLeft(t) - d));
					}
				}
				beats.shiftEarlier(d);
				lastPub = gameTime();
				nextPub = beats.nextChange(lastPub);
			}
		}
		syncViewers();
	}

	/** GameTests / screenshots: the atmosphere safe point now (pending bot settings apply, bots sit). */
	public void botSafePointForTests() {
		botSafePoint();
	}

	/** GameTests / screenshots: every beat public, then the pending bot decision is taken now. */
	public void fastForwardForTests() {
		revealAllForTests();
		if (ticksLeft("bot") >= 0) {
			cancelTimer("bot");
			onTimer("bot");
		}
	}

	/** Beats that became public since the last check: re-sync the viewers and move the dealer (cards.md §1.4). */
	private void publishTick() {
		if (beats == null || nextPub < 0) {
			return;
		}
		long now = gameTime();
		if (now < nextPub) {
			return;
		}
		List<String> kinds = beats.kindsBetween(lastPub, now);
		lastPub = now;
		nextPub = beats.nextChange(now);
		if (kinds.contains(BlackjackBeats.FLIP)) {
			dealerGesture(DealerGesture.FLIP);
		} else if (kinds.contains(BlackjackBeats.PEEK)) {
			dealerGesture(DealerGesture.PEEK);
		} else if (!kinds.isEmpty()) {
			dealerGesture(DealerGesture.DEAL);
		} else if (!busy() && RESULT.equals(phase()) && round != null) {
			boolean paid = round.seats().stream().anyMatch(s -> round.returnOf(s.seat) > round.stakedOf(s.seat));
			dealerGesture(paid ? DealerGesture.PAY : DealerGesture.SWEEP);
		}
		syncViewers();
	}

	/** The nearest blackjack dealer NPC within {@link BlackjackDealer#TABLE_RADIUS} blocks plays {@code g}. */
	private void dealerGesture(DealerGesture g) {
		if (!(level instanceof ServerLevel sl)) {
			return;
		}
		BlackjackDealer best = null;
		double bestD = Double.MAX_VALUE;
		net.minecraft.world.phys.Vec3 c = net.minecraft.world.phys.Vec3.atCenterOf(worldPosition);
		for (BlackjackDealer d : sl.getEntitiesOfClass(BlackjackDealer.class, new net.minecraft.world.phys.AABB(worldPosition).inflate(BlackjackDealer.TABLE_RADIUS))) {
			double dist = d.distanceToSqr(c);
			if (dist < bestD) {
				bestD = dist;
				best = d;
			}
		}
		if (best != null) {
			best.gesture(g);
		}
	}

	/** Drives the round after every change: pays settled seats, starts the right timer or finishes. */
	private void step() {
		if (round == null) {
			return;
		}
		publish();
		payNewlySettled();
		switch (round.phase()) {
			case INSURANCE -> {
				for (Seat s : round.pendingInsurance()) {
					VirtualBot bot = roundBots.get(s.seat);
					if (bot != null) {
						botInsurance(bot);
					}
				}
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
					startTimer("insurance", CasinoConfig.blackjack().insuranceTimerTicks + delay());
				}
			}
			case TURNS -> {
				cancelTimer("insurance");
				if (INSURANCE.equals(phase()) && round.peeked()) {
					notice(PEEK_NOTICE, PEEK_NOTICE_TICKS);
				}
				setPhase(TURNS);
				Turn t = round.current();
				if (t == null) {
					finish();
					return;
				}
				VirtualBot bot = roundBots.get(t.seat().seat);
				if (bot != null) {
					// Bots act in seat order after a short think; they never use the human turn timer.
					cancelTimer("turn");
					if (ticksLeft("bot") < 0) {
						startTimer("bot", Math.max(1, BlackjackBotPolicy.thinkTicks(bots.rng(), bot.profile().level(), bots.speed(), AtmosphereBots.fastFactor()))
							+ delay());
					}
					lastTurnSeat = t.seat().seat;
					return;
				}
				if (away.contains(t.seat().player) || online(t.seat().player) == null) {
					round.standAll(t.seat().seat);
					step();
					return;
				}
				startTimer("turn", CasinoConfig.blackjack().turnTimerTicks + delay());
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
		// Only human participants are ever paid; bot seats carry virtual bets (BOTS.md §5.2).
		for (Seat s : BlackjackBotPolicy.payableSeats(round, seat -> !roundBots.containsKey(seat))) {
			if (paid.contains(s.player)) {
				continue;
			}
			paid.add(s.player);
			long ret = round.returnOf(s.seat);
			long net = ret - round.stakedOf(s.seat);
			boolean natural = s.hands.stream().anyMatch(h -> h.outcome == Outcome.BLACKJACK || h.outcome == Outcome.EVEN_MONEY);
			boolean splits = s.hands.size() > 1;
			settle(s.player, ret, r -> r.withTags(natural ? "natural" : "", splits ? "split" : "", s.insurance > 0 ? "insurance" : ""));
			if (level instanceof ServerLevel sl) {
				if (natural) {
					CasinoAdvancements.grant(sl.getServer(), s.player, "natural");
				}
				if (s.hands.size() >= 4) {
					CasinoAdvancements.grant(sl.getServer(), s.player, "split_personality");
				}
			}
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
		cancelTimer("bot");
		botsAfterRound();
		setPhase(RESULT);
		// RESULT starts at the reveal gate (the dealer's last card is readable); money is settled already
		startTimer("result", delay() + RESULT_TICKS);
		setChanged();
	}

	private void toBetting() {
		round = null;
		beats = null;
		nextPub = -1;
		away.clear();
		paid.clear();
		lastTurnSeat = -1;
		roundBots.clear();
		setPhase(BETTING);
		// Safe point: start of BETTING. New bet moments for every bot.
		botBetAt.clear();
		botSafePoint();
	}

	// ---- bots (atmosphere: virtual bets, real cards, never the human timer) ------------------------

	/** Safe point at the start of / during BETTING: bots join / leave / yield; bet moments drawn. */
	private void botSafePoint() {
		if (!(level instanceof ServerLevel sl) || removing()) {
			return;
		}
		if (bots.safePoint(sl) == null) {
			return;
		}
		List<VirtualBot> seated = bots.bots();
		java.util.Set<String> live = new java.util.HashSet<>();
		seated.forEach(b -> live.add(b.key));
		botMem.keySet().retainAll(live);
		botBetAt.keySet().retainAll(live);
		for (VirtualBot b : seated) {
			if (!botMem.containsKey(b.key)) {
				botMem.put(b.key, BlackjackBotPolicy.newBetMemory(b.profile().level(), bots.rng(), minBet(), 0));
			}
			if (!botBetAt.containsKey(b.key)) {
				botBetAt.put(b.key, gameTime() + bots.betDelay());
			}
		}
		syncViewers();
	}

	/** A bot seat decides insurance / even money at once (virtual: nothing is raised). */
	private void botInsurance(VirtualBot bot) {
		Seat seat = round.seat(bot.seat());
		Offer offer = round.offer(bot.seat());
		if (seat == null || offer == null) {
			return;
		}
		boolean take = false;
		try {
			var view = new BlackjackBotPolicy.View.Insurance(offer, List.copyOf(seat.hands.getFirst().cards), round.upCard());
			take = BlackjackBotPolicy.INSTANCE.act(bot.profile(), view, null, bots.rng()) instanceof BlackjackBotPolicy.Act.Insure i && i.take();
		} catch (RuntimeException e) {
			Burmaldaholic.LOGGER.error("blackjack bot insurance failed", e);
		}
		if (offer == Offer.EVEN_MONEY) {
			round.evenMoney(bot.seat(), take);
		} else {
			round.insure(bot.seat(), take ? BlackjackRules.maxInsurance(seat.bet) : 0);
		}
	}

	/** The bot's think delay has passed: play one action (safe default on error: stand). */
	private void botPlay() {
		Turn t = round == null ? null : round.current();
		if (t == null) {
			return;
		}
		VirtualBot bot = roundBots.get(t.seat().seat);
		if (bot == null) {
			step();
			return;
		}
		int seatNo = t.seat().seat;
		try {
			var view = new BlackjackBotPolicy.View.Turn(List.copyOf(t.hand().cards), round.upCard(), round.legal(seatNo), round.rules(),
				t.seat().hands.size());
			BlackjackBotPolicy.Act a = BlackjackBotPolicy.INSTANCE.act(bot.profile(), view, null, bots.rng());
			if (!(a instanceof BlackjackBotPolicy.Act.Play p) || !round.act(seatNo, p.action())) {
				round.standAll(seatNo);
			}
		} catch (RuntimeException e) {
			Burmaldaholic.LOGGER.error("blackjack bot turn failed", e);
			round.standAll(seatNo);
		}
		step();
	}

	/** Round over: EASY's loss progression, the blackjack quip. Virtual only. */
	private void botsAfterRound() {
		if (round == null || botsDone) {
			return;
		}
		botsDone = true;
		for (Map.Entry<Integer, VirtualBot> e : roundBots.entrySet()) {
			Seat seat = round.seat(e.getKey());
			VirtualBot b = e.getValue();
			if (seat == null) {
				continue;
			}
			long net = round.returnOf(seat.seat) - round.stakedOf(seat.seat);
			BetMemory mem = botMem.get(b.key);
			if (mem != null) {
				botMem.put(b.key, BlackjackBotPolicy.afterRound(b.profile().level(), mem, net, bots.rng(), minBet(), 0));
			}
			if (seat.hands.stream().anyMatch(h -> h.outcome == Outcome.BLACKJACK) && level instanceof ServerLevel sl) {
				bots.quip(sl, b.profile(), "blackjack", null);
			}
		}
	}

	/** Shows a bot's virtual bet in the lobby once its bet moment has come (they never delay the deal). */
	@Override
	protected void serverTick(ServerLevel level) {
		publishTick();
		if (botBetAt.isEmpty()) {
			return;
		}
		long now = gameTime();
		int shown = (int) botBetAt.values().stream().filter(t -> t <= now).count();
		if (shown != botBetsShown) {
			botBetsShown = shown;
			if (BETTING.equals(phase())) {
				syncViewers();
			}
		}
	}

	private static MutableComponent botName(VirtualBot b) {
		return BotNames.display(b.profile(), BotNames.LevelLabel.LEVEL);
	}

	/** The table is broken: the round was played out by core; now every bot leaves (BOTS.md §3.5). */
	@Override
	public void preRemoveSideEffects(BlockPos pos, BlockState state) {
		super.preRemoveSideEffects(pos, state);
		if (level instanceof ServerLevel sl) {
			bots.endSession(sl);
		}
	}

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		bots.save(output);
	}

	@Override
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		bots.load(input);
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
			case "bot" -> botPlay();
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
		putCardsTheme(tag);
		UUID me = viewer.getUUID();
		tag.putBoolean("high_roller", isHighRoller());
		tag.putLong("last_bet", lastBet.getOrDefault(me, 0L));
		tag.putBoolean("bet_placed", mainBets.containsKey(me));
		// the peek notice follows the PEEK beat (both outcomes alike): set when the round resolved, it would tell
		// whether the dealer has blackjack before the peek is on the felt (cards.md §0.7.1)
		String notice = !noticeKey.isEmpty() && gameTime() < noticeUntil && !noticeKey.equals(PEEK_NOTICE) ? noticeKey : "";
		if (notice.isEmpty() && beats != null && beats.peekTick() >= 0 && gameTime() >= beats.peekTick()
			&& gameTime() < beats.peekTick() + PEEK_NOTICE_TICKS) {
			notice = PEEK_NOTICE;
		}
		if (!notice.isEmpty()) {
			tag.putString("notice", notice);
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
		writeBotState(tag);
		if (round == null) {
			return tag;
		}
		// Only PUBLISHED cards (C1 / J-C1): one card per beat, the hole card a back until its flip beat; results,
		// decisions and timers wait for the reveal gate (cards.md §0.7.1, §0.7.4).
		long now = gameTime();
		boolean busy = busy();
		long start = beats.startTick();
		tag.putInt("round_seq", roundSeq);
		tag.putLong("round_tick", start);
		tag.putBoolean("busy", busy);
		if (busy) {
			CompoundTag timers = tag.getCompoundOrEmpty("timers");
			for (String t : List.of("turn", "insurance", "bot", "result")) {
				timers.remove(t);
			}
			tag.put("timers", timers);
			// while cards are moving the phase must not run ahead of the felt: a dealer blackjack (RESULT) or an ace up
			// (INSURANCE) is only told by its own beat (cards.md §0.7.1)
			if (RESULT.equals(phase())) {
				tag.putString("phase", beats.holeShown(now) ? "dealer" : TURNS);
			} else if (INSURANCE.equals(phase()) && beats.dealerAt(now).isEmpty()) {
				tag.putString("phase", TURNS);
			}
		}
		putEvs(tag, "dealer", beats.dealerAt(now), start);
		boolean holeShown = beats.holeShown(now);
		tag.putBoolean("hole_hidden", !holeShown);
		if (holeShown) {
			tag.putInt("hole_flip_at", (int) (beats.holeFlipTick() - start));
		}
		if (beats.peekTick() >= 0 && beats.peekTick() <= now) {
			tag.putInt("peek_at", (int) (beats.peekTick() - start));
		}
		Turn turn = busy ? null : round.current();
		tag.putInt("current_seat", turn == null ? -1 : turn.seat().seat);
		tag.putInt("current_hand", turn == null ? -1 : turn.handIndex());
		ListTag players = new ListTag();
		for (Seat s : round.seats()) {
			CompoundTag p = new CompoundTag();
			p.putInt("seat", s.seat);
			p.putString("name", names.getOrDefault(s.player, ""));
			VirtualBot bot = roundBots.get(s.seat);
			if (bot != null && level instanceof ServerLevel sl) {
				p.putBoolean("bot", true);
				p.put("name_c", AtmosphereBots.encode(sl, botName(bot)));
				p.putInt("bot_level", Math.min(3, bot.profile().level().ordinal() + 1));
				p.putString("bot_name", bot.profile().nameKey());
			}
			p.putBoolean("you", s.player.equals(me));
			p.putBoolean("away", away.contains(s.player));
			p.putLong("bet", s.bet);
			p.putLong("insurance", s.insurance);
			boolean settled = s.settled && !busy;
			p.putBoolean("settled", settled);
			if (settled) {
				long ret = round.returnOf(s.seat);
				long staked = round.stakedOf(s.seat);
				p.putLong("net", ret - staked);
				p.putLong("ret", ret);
				p.putLong("staked", staked);
				p.putLong("insurance_return", s.insuranceReturn);
				// the viewer's tier, server-computed over all bets of the round (cards.md §0.5)
				p.putString("tier", WinTier.of(ret, staked, WinTierTable.DEFAULT).name());
			}
			List<List<BlackjackBeats.Ev>> pub = beats.handsAt(s.seat, now);
			ListTag hands = new ListTag();
			for (int h = 0; h < s.hands.size(); h++) {
				Hand hand = s.hands.get(h);
				CompoundTag ht = new CompoundTag();
				putEvs(ht, "cards", h < pub.size() ? pub.get(h) : List.of(), start);
				ht.putLong("bet", hand.bet);
				ht.putBoolean("doubled", hand.doubled);
				ht.putBoolean("split", hand.split);
				ht.putBoolean("done", hand.done && !busy);
				if (hand.outcome != null && !busy) {
					ht.putString("outcome", hand.outcome.id());
					ht.putLong("ret", hand.ret);
				}
				hands.add(ht);
			}
			p.put("hands", hands);
			players.add(p);
		}
		tag.put("players", players);
		Seat mine = seatOf(me);
		if (mine != null && !away.contains(me) && !busy) {
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

	/**
	 * Seats &amp; Bots sync: {@code bots_header} (component), {@code bot_seats} [{seat, name_c, amount?}] —
	 * a bot's virtual bet shows once its bet moment has come — and {@code virtual} when bots play this round.
	 */
	private void writeBotState(CompoundTag tag) {
		if (!(level instanceof ServerLevel sl)) {
			return;
		}
		Component header = bots.header(sl);
		if (header != null) {
			tag.put("bots_header", AtmosphereBots.encode(sl, header));
		}
		ListTag list = new ListTag();
		long now = gameTime();
		for (VirtualBot b : bots.bots()) {
			CompoundTag bt = new CompoundTag();
			bt.putInt("seat", b.seat());
			bt.put("name_c", AtmosphereBots.encode(sl, botName(b)));
			bt.putInt("bot_level", Math.min(3, b.profile().level().ordinal() + 1));
			bt.putString("bot_name", b.profile().nameKey());
			BetMemory mem = botMem.get(b.key);
			if (mem != null && now >= botBetAt.getOrDefault(b.key, Long.MAX_VALUE)) {
				bt.putLong("amount", mem.next());
			}
			list.add(bt);
		}
		tag.put("bot_seats", list);
		tag.putBoolean("virtual", !roundBots.isEmpty() || !list.isEmpty());
	}

	/** {@code key} = codes (−1 = back), {@code key_ids} = stable card ids, {@code key_at} = deal tick − round start. */
	private static void putEvs(CompoundTag tag, String key, List<BlackjackBeats.Ev> evs, long start) {
		int[] codes = new int[evs.size()];
		int[] ids = new int[evs.size()];
		int[] at = new int[evs.size()];
		for (int i = 0; i < codes.length; i++) {
			BlackjackBeats.Ev e = evs.get(i);
			codes[i] = e.code();
			ids[i] = e.id();
			at[i] = (int) (e.tick() - start);
		}
		tag.putIntArray(key, codes);
		tag.putIntArray(key + "_ids", ids);
		tag.putIntArray(key + "_at", at);
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
