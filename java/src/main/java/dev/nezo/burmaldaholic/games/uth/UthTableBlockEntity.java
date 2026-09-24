package dev.nezo.burmaldaholic.games.uth;

import dev.nezo.burmaldaholic.core.config.sections.UthConfig;
import dev.nezo.burmaldaholic.core.data.OfflineMail;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.nezo.burmaldaholic.Burmaldaholic;
import dev.nezo.burmaldaholic.core.CoreSounds;
import dev.nezo.burmaldaholic.core.bots.BotJobs;
import dev.nezo.burmaldaholic.core.bots.BotTable;
import dev.nezo.burmaldaholic.core.bots.Bots;
import dev.nezo.burmaldaholic.core.bots.TableBots;
import dev.nezo.burmaldaholic.core.bots.logic.BotDifficulty;
import dev.nezo.burmaldaholic.core.bots.logic.BotProfile;
import dev.nezo.burmaldaholic.core.bots.logic.BotRole;
import dev.nezo.burmaldaholic.core.bots.logic.BotRoster;
import dev.nezo.burmaldaholic.core.bots.logic.BotSettings;
import dev.nezo.burmaldaholic.core.bots.logic.BotWork;
import dev.nezo.burmaldaholic.core.bots.logic.OwnerControls;
import dev.nezo.burmaldaholic.core.bots.logic.Purse;
import dev.nezo.burmaldaholic.core.bots.logic.SeatOccupant;
import dev.nezo.burmaldaholic.core.bots.logic.SeatPolicy;
import dev.nezo.burmaldaholic.core.bots.logic.SeatingMath;
import dev.nezo.burmaldaholic.core.config.CasinoConfig;
import dev.nezo.burmaldaholic.core.economy.AccountId;
import dev.nezo.burmaldaholic.core.economy.Economies;
import dev.nezo.burmaldaholic.core.economy.Economy;
import dev.nezo.burmaldaholic.core.economy.Economy.Transaction;
import dev.nezo.burmaldaholic.core.events.CasinoEvents.PlayResult;
import dev.nezo.burmaldaholic.core.events.PlayResults;
import dev.nezo.burmaldaholic.core.mode.CasinoMode;
import dev.nezo.burmaldaholic.core.rng.OddsService;
import dev.nezo.burmaldaholic.core.service.CoreServices;
import dev.nezo.burmaldaholic.core.service.TableOwnershipProvider.OwnedTable;
import dev.nezo.burmaldaholic.core.service.VipTiers;
import dev.nezo.burmaldaholic.core.table.CasinoTableBlockEntity;
import dev.nezo.burmaldaholic.core.table.TableSeats;
import dev.nezo.burmaldaholic.core.table.TableType;
import dev.nezo.burmaldaholic.core.text.Texts;
import dev.nezo.burmaldaholic.core.util.Result;
import dev.nezo.burmaldaholic.core.wager.Stake;
import dev.nezo.burmaldaholic.core.wager.WagerVeto;
import dev.nezo.burmaldaholic.core.wager.Wagers;
import dev.nezo.burmaldaholic.client.dealer.DealerCueSource;
import dev.nezo.burmaldaholic.client.dealer.DealerMotion;
import dev.nezo.burmaldaholic.core.anim.SeedMix;
import dev.nezo.burmaldaholic.core.anim.Timeline;
import dev.nezo.burmaldaholic.games.poker.logic.BestFive;
import dev.nezo.burmaldaholic.games.uth.logic.BankRules;
import dev.nezo.burmaldaholic.games.uth.logic.UthBeats;
import dev.nezo.burmaldaholic.games.uth.present.UthPub;
import dev.nezo.burmaldaholic.games.uth.logic.Decision;
import dev.nezo.burmaldaholic.games.uth.logic.PayHand;
import dev.nezo.burmaldaholic.games.uth.logic.Paytables;
import dev.nezo.burmaldaholic.games.uth.logic.SeatDecider;
import dev.nezo.burmaldaholic.games.uth.logic.UthBotPolicy;
import dev.nezo.burmaldaholic.games.uth.logic.UthBotRules;
import dev.nezo.burmaldaholic.games.uth.logic.Settlement;
import dev.nezo.burmaldaholic.games.uth.logic.UthCards;
import dev.nezo.burmaldaholic.games.uth.logic.UthLimits;
import dev.nezo.burmaldaholic.games.uth.logic.UthRound;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

/**
 * Server-authoritative Ultimate Texas Hold'em table (GAME_DESIGN §21): a shared table of up to 6 seats
 * against one dealer hand, plus (on {@code uth_table_player_banked}) the dealer seat a player may take
 * and bank (§21.9). The rules are pure ({@link UthRound}, {@link Settlement}, {@link BankRules}); this
 * class runs phases, timers and money.
 *
 * <pre>
 * betting   seated players confirm Ante (= Blind) + optional Trips ("bet"); starts when every seated
 *           player confirmed, or uth.betTimerTicks after the first confirmation ("bet" timer)
 * preflop   Check · Bet ×3 · Bet ×4 — every seat at once, one shared "decide" timer
 * flop      reveal 3 ("step" 20 t), seats without a Play bet: Check · Bet ×2
 * river     reveal turn + river ("step" 20 t), seats without a Play bet: Bet ×1 · Fold
 * showdown  dealer cards shown ("step" 20 t) → every seat settled → result (80 t) → betting
 * </pre>
 *
 * <b>Money.</b> House rounds use core stakes: Ante + Blind + Trips via {@code placeBet} (the §21.6 worst case
 * {@code 505 × Ante + 50 × Trips} is reserved at owned tables), an online Play bet raises that stake, one
 * {@code settle} per seat. A Play bet made for a seat whose player is gone (default action) is collected
 * directly from the offline balance and added to the round's report. Player-banked rounds (§21.9) do not
 * use core stakes: the bank and the seats' bets are escrowed in the world bank by this table, saved with the
 * block entity (an orphaned escrow found on load after a crash is returned), and settled here.
 *
 * <p><b>Bots</b> (BOTS.md §4.5, §4.6; pvp-bots.md §4.6): atmosphere bots sit in free player seats (core
 * {@link TableBots}; safe point = the start of BETTING and any time during it), put down virtual bets 60–160 t
 * into BETTING (never delaying the round), are dealt AFTER the board (the humans' cards, the dealer's hand and
 * the board never depend on them), decide through {@link UthBotPolicy} after a think delay (NORMAL / HARD
 * rivers: the 990-hand enumeration as a {@link BotJobs} job, deadline → fallback rule) and are never settled:
 * no money, no reports, no advancements. Under a human banker they only watch; with nobody banking a house
 * round may show a bot stand-in on the dealer plate (flavour only).
 */
public class UthTableBlockEntity extends CasinoTableBlockEntity implements BotTable, DealerCueSource {
	public static final String BETTING = "betting", PREFLOP = "preflop", FLOP = "flop", RIVER = "river", SHOWDOWN = "showdown",
		RESULT = "result";
	static final int REVEAL_TICKS = 20;
	static final int RESULT_TICKS = 80;
	private static final String ESCROW_KEY = "burmaldaholic_uth_escrow";

	public enum Variant {
		STANDARD, HIGH_ROLLER, PLAYER_BANKED
	}

	/** Chips this table holds outside core stakes (bank, player-banked bets, offline Play bets). */
	public record Escrow(UUID player, String kind, long amount, String bankroll) {
		static final Codec<Escrow> CODEC = RecordCodecBuilder.create(i -> i.group(
			UUIDUtil.CODEC.fieldOf("player").forGetter(Escrow::player),
			Codec.STRING.fieldOf("kind").forGetter(Escrow::kind),
			Codec.LONG.fieldOf("amount").forGetter(Escrow::amount),
			Codec.STRING.optionalFieldOf("bankroll", "").forGetter(Escrow::bankroll)
		).apply(i, Escrow::new));
	}

	/** A confirmed bet of the betting phase. */
	record Bet(long ante, long trips, int seat) {}

	/** The player in the dealer seat. */
	static final class Banker {
		final UUID player;
		final String name;
		final int formerSeat;
		long bank;
		long reserved;
		int rounds;
		boolean leaving;

		Banker(UUID player, String name, int formerSeat, long bank) {
			this.player = player;
			this.name = name;
			this.formerSeat = formerSeat;
			this.bank = bank;
		}
	}

	private final Variant variant;
	private final Map<UUID, Bet> confirmed = new LinkedHashMap<>();
	private final Map<UUID, long[]> lastBets = new HashMap<>();
	private final Map<UUID, String> names = new HashMap<>();
	private @Nullable UthRound round;
	/** Round participants who left (defaults apply; still settled, offline-safe). */
	private final Set<UUID> away = new HashSet<>();
	/** Play bets of house rounds collected directly (player gone): player → [amount], bankroll of the stake. */
	private final Map<UUID, Long> directPlay = new HashMap<>();
	private final Map<UUID, String> directPlayBank = new HashMap<>();
	/** Player-banked round: every seat's chips held against the bank, and each seat's reservation. */
	private boolean pvpRound;
	private final Map<UUID, Long> pvpStakes = new LinkedHashMap<>();
	private final Map<UUID, Long> pvpReserved = new HashMap<>();
	private @Nullable Banker banker;
	private @Nullable UUID offeredTo;
	private long lastBankResult;
	private long lastRake;
	private boolean bankResultShown;
	private boolean royalThisRound;
	private int @Nullable [] stackedDeck;
	// ---- presentation (animation/cards.md §3.2, task J-C3) -----------------------------------------------------
	/** Segment counter / kind ({@link UthPub#DEAL} …) / start tick of the running segment. */
	private int stageSeq;
	private int stageKind;
	private long stageStart;
	/** Last published public tag (block updates only on change) and, client side, the last one received. */
	private @Nullable CompoundTag lastPub;
	private CompoundTag clientPub = new CompoundTag();
	private @Nullable UthPub clientPubCache;
	private @Nullable Timeline clientTl;
	private int clientTlSeq = -1;
	private final List<Escrow> orphaned = new ArrayList<>();

	// ---- bots (BOTS.md §4.5, §4.6) ----
	private static final String BOTS_KEY = "burmaldaholic_bots";
	/** Chips a bot seat "has" for its virtual Play bets (never debited). */
	private static final long BOT_BALANCE = Long.MAX_VALUE / 4;
	private @Nullable TableBots tableBots;
	private @Nullable CompoundTag savedBots;
	private final UthBotRules.BotSlots slots = new UthBotRules.BotSlots();
	/** Seated bots by key ({@code bot:<id>}). */
	private final Map<String, BotProfile> botSeats = new LinkedHashMap<>();
	/** Virtual bets placed this BETTING (bot key → bets). */
	private final Map<String, UthBotRules.VirtualBet> botBets = new LinkedHashMap<>();
	private final Set<String> botBetScheduled = new HashSet<>();
	/** Bot participants of the dealt round (seat id → profile). */
	private final Map<UUID, BotProfile> roundBots = new HashMap<>();
	/** Bot actions due at a game time; {@link #botEpoch} invalidates them. */
	private record BotTask(long due, int epoch, Runnable run) {}
	private final List<BotTask> botTasks = new ArrayList<>();
	private int botEpoch;
	/** Humans in sit-down order (host = longest seated). */
	private final List<UUID> sitOrder = new ArrayList<>();
	private @Nullable BotProfile standIn;
	private int houseRounds;
	private int standInOfferFrom = -1;
	private boolean inBotSafePoint;

	public UthTableBlockEntity(TableType<UthTableBlockEntity> type, BlockPos pos, BlockState state) {
		super(type, pos, state);
		String name = type.name();
		this.variant = name.endsWith("high_roller") ? Variant.HIGH_ROLLER : name.endsWith("player_banked") ? Variant.PLAYER_BANKED : Variant.STANDARD;
		setPhase(BETTING);
	}

	static UthConfig cfg() {
		return CasinoConfig.uth();
	}

	static Paytables pays() {
		return UthMath.paytables();
	}

	public Variant variant() {
		return variant;
	}

	/** High-Roller rules: the block variant, or a worldgen High-Roller-lounge preset (§16.3). */
	public boolean isHighRoller() {
		return variant == Variant.HIGH_ROLLER || preset().map(p -> p.id().startsWith("high_roller")).orElse(false);
	}

	public boolean playerBanked() {
		return variant == Variant.PLAYER_BANKED && cfg().pvp.enabled;
	}

	// ---- test hooks -----------------------------------------------------------------------------

	/** GameTests: the next deal uses this deck (52 distinct cards, top = index 0). */
	public void stackDeckForTests(int[] deck) {
		this.stackedDeck = deck.clone();
	}

	/**
	 * GameTests: fires the running timers (earliest first) as if their time had passed, at most
	 * {@code steps} times or until the table is back in BETTING (e.g. {@code fastForwardForTests(1)} = let the
	 * decision timer expire). Returns the number of timers fired.
	 */
	public int fastForwardForTests(int steps) {
		int fired = 0;
		for (; fired < steps; fired++) {
			runBotTasksForTests();
			String next = null;
			long best = Long.MAX_VALUE;
			for (String id : new String[] {"bet", "decide", "step"}) {
				long left = ticksLeft(id);
				if (left >= 0 && left < best) {
					best = left;
					next = id;
				}
			}
			if (next == null) {
				break;
			}
			cancelTimer(next);
			onTimer(next);
		}
		return fired;
	}

	/** GameTests / screen state: the round in play (null between rounds). */
	public @Nullable UthRound round() {
		return round;
	}

	public long bank() {
		return banker == null ? 0 : banker.bank;
	}

	public @Nullable UUID bankerId() {
		return banker == null ? null : banker.player;
	}

	/** Chips this table holds outside core stakes right now (bank + player-banked bets + direct Play bets). */
	public long escrowTotal() {
		long sum = banker == null ? 0 : banker.bank;
		for (long v : pvpStakes.values()) {
			sum += v;
		}
		for (long v : directPlay.values()) {
			sum += v;
		}
		return sum;
	}

	// ---- table configuration --------------------------------------------------------------------

	@Override
	protected int seatCount() {
		return cfg().seats;
	}

	@Override
	protected long minBet() {
		return isHighRoller() ? cfg().highRollerMinAnte : cfg().minAnte;
	}

	@Override
	protected double houseEdge() {
		return UthMath.ELEMENT_OF_RISK;
	}

	/** [minimum Ante, maximum W = 6 × Ante + Trips] for the player (§21.2, owner settings §21.6). */
	@Override
	public long[] limitsFor(ServerPlayer player) {
		long[] base = super.limitsFor(player);
		if (!isHighRoller()) {
			return base;
		}
		long tierMax = CoreServices.vip().maxBet(player.level().getServer(), player.getUUID());
		long max = (long) Math.floor(tierMax * cfg().highRollerMaxMultiplier);
		long ownerMax = ownership().map(OwnedTable::maxBet).orElse(0L);
		if (ownerMax > 0) {
			max = Math.min(max, ownerMax);
		}
		return new long[] {base[0], max};
	}

	private boolean vipAllowed(ServerPlayer player, boolean tell) {
		if (!isHighRoller()) {
			return true;
		}
		int need = preset().filter(p -> p.minTier() > 0).map(p -> p.minTier()).orElse(cfg().highRollerMinVipTier);
		if (CoreServices.vip().tier(player.level().getServer(), player.getUUID()) >= need) {
			return true;
		}
		if (tell) {
			sendError(player, Component.translatable("gui.burmaldaholic.error.vip_required", VipTiers.name(need)));
		}
		return false;
	}

	// ---- helpers --------------------------------------------------------------------------------

	private @Nullable MinecraftServer server() {
		return level instanceof ServerLevel sl ? sl.getServer() : null;
	}

	private @Nullable ServerPlayer online(UUID id) {
		MinecraftServer s = server();
		return s == null ? null : s.getPlayerList().getPlayer(id);
	}

	private void tell(UUID id, Component message) {
		ServerPlayer p = online(id);
		if (p != null) {
			p.sendSystemMessage(message);
		}
	}

	private void tellTable(Component message, @Nullable UUID except) {
		Set<UUID> sent = new HashSet<>();
		for (TableSeats.Seat s : seats().occupied()) {
			if (!s.player().equals(except) && sent.add(s.player())) {
				tell(s.player(), message);
			}
		}
		if (banker != null && !banker.player.equals(except) && sent.add(banker.player)) {
			tell(banker.player, message);
		}
	}

	private String nameOf(UUID id) {
		return names.getOrDefault(id, "");
	}

	private long balanceOf(UUID id) {
		MinecraftServer s = server();
		return s == null ? 0 : Economies.get().balance(s, id);
	}

	private boolean bankerSeat(UUID id) {
		return banker != null && banker.player.equals(id);
	}

	// ---- seats ----------------------------------------------------------------------------------

	@Override
	public boolean sit(ServerPlayer player) {
		if (isSeated(player)) {
			return true;
		}
		if (bankerSeat(player.getUUID())) {
			return false; // the banker gave up their player seat (§21.9)
		}
		if (!vipAllowed(player, true) || !admitBots(player) || !super.sit(player)) {
			return false;
		}
		names.put(player.getUUID(), player.getName().getString());
		sitOrder.remove(player.getUUID());
		sitOrder.add(player.getUUID());
		tellTable(Component.translatable("msg.burmaldaholic.uth.player_joined", player.getDisplayName()), player.getUUID());
		// atmosphere safe point: any time during BETTING (a bot yields its seat at once)
		if (BETTING.equals(phase()) && !inBotSafePoint) {
			botSafePoint();
		}
		return true;
	}

	/** Leaving is always possible: after the deal the seat's decisions take their defaults (§21.5). */
	@Override
	protected boolean canLeaveNow(UUID player) {
		return true;
	}

	private boolean quietLeave;

	@Override
	protected void onPlayerLeft(UUID player, LeaveReason reason) {
		sitOrder.remove(player);
		if (!quietLeave) {
			tellTable(Component.translatable("msg.burmaldaholic.uth.player_left", Texts.raw(nameOf(player))), player);
		}
		if (BETTING.equals(phase()) && round == null && !removing()) {
			botSafePoint(); // the last human gone ends the bot session; otherwise pending changes / claimants apply
		}
		UthRound r = round;
		UthRound.Seat seat = r == null || r.settled() ? null : r.seat(player);
		if (seat != null) {
			// §21.5: pending decisions take the default action at once; the round plays out and is credited.
			away.add(player);
			if (r.pending(seat)) {
				applyDefault(seat, false);
				afterDecision();
			}
			return;
		}
		Bet bet = confirmed.get(player);
		if (bet == null) {
			return;
		}
		if (reason == LeaveReason.DISCONNECT) {
			return; // §21.5: a disconnect during BETTING leaves the bets in play (dealt with default actions)
		}
		refundConfirmed(player, reason == LeaveReason.REMOVED ? "msg.burmaldaholic.uth.bets_refunded" : "msg.burmaldaholic.uth.left_refunded");
		if (confirmed.isEmpty()) {
			cancelTimer("bet");
			pvpRound = false;
		} else if (allSeatedConfirmed() && !removing()) {
			startRound();
		}
	}

	/** Returns a confirmed, undrawn bet (house stake or player-banked escrow). */
	private void refundConfirmed(UUID player, @Nullable String messageKey) {
		Bet bet = confirmed.remove(player);
		if (bet == null) {
			return;
		}
		if (pvpStakes.containsKey(player)) {
			long amount = pvpStakes.remove(player);
			releaseReservation(player);
			transferOrLog(AccountId.HOUSE, AccountId.player(player), amount, Transaction.refund(UthModule.ID));
		} else {
			refund(player, true);
		}
		if (messageKey != null) {
			tell(player, Component.translatable(messageKey));
		}
		setChanged();
	}

	private void releaseReservation(UUID player) {
		Long r = pvpReserved.remove(player);
		if (r != null && banker != null) {
			banker.reserved = Math.max(0, banker.reserved - r);
		}
	}

	private boolean allSeatedConfirmed() {
		if (confirmed.isEmpty()) {
			return false;
		}
		for (TableSeats.Seat s : seats().occupied()) {
			if (!confirmed.containsKey(s.player())) {
				return false;
			}
		}
		return true;
	}

	// ---- actions --------------------------------------------------------------------------------

	@Override
	public void onAction(ServerPlayer player, String action, CompoundTag args) {
		if (!cfg().enabled) {
			sendError(player, Component.translatable("gui.burmaldaholic.error.disabled"));
			return;
		}
		switch (action) {
			case "bet" -> confirm(player, args.getLongOr("ante", 0), args.getLongOr("trips", 0));
			case "clear" -> clear(player);
			case "take_bank" -> takeBank(player, args.getLongOr("amount", 0));
			case "leave_bank" -> leaveBank(player);
			case "bot_settings" -> {
				if (Bots.enabled()) {
					UthBotUi.get().openSettings(player, this);
				}
			}
			default -> {
				Decision d = Decision.byId(action);
				if (d != null) {
					decide(player, d);
				}
			}
		}
		syncViewers();
	}

	private void confirm(ServerPlayer player, long ante, long trips) {
		UUID id = player.getUUID();
		if (!BETTING.equals(phase())) {
			sendError(player, Component.translatable("gui.burmaldaholic.error.round_in_progress"));
			return;
		}
		if (confirmed.containsKey(id) || bankerSeat(id) || !sit(player)) {
			return;
		}
		UthConfig c = cfg();
		boolean pvp = confirmed.isEmpty() ? playerBanked() && banker != null && !banker.leaving : pvpRound;
		if (confirmed.isEmpty() && playerBanked() && banker == null && !c.pvp.houseRoundsWhenNoBanker) {
			sendError(player, Component.translatable("msg.burmaldaholic.uth.pvp.seat_offered"));
			return;
		}
		long[] lim = limitsFor(player);
		long balance = Economies.get().balance(player);
		UthLimits.Problem problem = UthLimits.check(ante, trips, lim[0], lim[1], c.tripsEnabled, c.minAnte, balance);
		Component err = switch (problem) {
			case NONE -> null;
			case INVALID -> Component.translatable("gui.burmaldaholic.error.invalid_amount");
			case ANTE_MIN -> Component.translatable("gui.burmaldaholic.uth.error.ante_min", Texts.number(lim[0]));
			case TRIPS_OFF -> Component.translatable("gui.burmaldaholic.uth.error.trips_off");
			case TRIPS_MIN -> Component.translatable("gui.burmaldaholic.error.bet_too_low", Texts.number(c.minAnte));
			case WORST_CASE_MAX -> Component.translatable("gui.burmaldaholic.uth.error.worst_case_max", Texts.number(lim[1]));
			case INSUFFICIENT_FUNDS -> Component.translatable("gui.burmaldaholic.error.insufficient_funds", Texts.number(balance));
			case KEEP_FOR_RIVER -> Component.translatable("gui.burmaldaholic.uth.error.keep_for_river", Texts.number(ante));
		};
		if (err == null && !vipAllowed(player, true)) {
			return;
		}
		if (err != null) {
			sendError(player, err);
			return;
		}
		long cost = UthLimits.confirmCost(ante, trips);
		long worstCase = pays().worstCase(ante, trips);
		int seat = seats().seatOf(id).orElse(-1);
		if (pvp) {
			Component veto = Wagers.check(player, new WagerVeto.Context(UthModule.ID, Stake.Kind.CHIPS, worldPosition, true));
			if (veto != null) {
				sendError(player, veto);
				return;
			}
			Banker b = banker;
			if (!BankRules.covers(b.bank, b.reserved, worstCase)) {
				sendError(player, Component.translatable("gui.burmaldaholic.uth.error.bank_cover",
					Texts.number(BankRules.maxCoveredAnte(b.bank, b.reserved, trips, pays()))));
				return;
			}
			MinecraftServer server = player.level().getServer();
			if (!Economies.get().transfer(server, AccountId.player(id), AccountId.HOUSE, cost, Transaction.bet(UthModule.ID)).ok()) {
				sendError(player, Component.translatable("gui.burmaldaholic.error.insufficient_funds", Texts.number(balance)));
				return;
			}
			b.reserved += worstCase;
			pvpReserved.put(id, worstCase);
			pvpStakes.put(id, cost);
			playChip();
		} else {
			Result<Long> r = placeBet(player, cost, 1, 0, worstCase, false);
			if (!r.isOk()) {
				return;
			}
		}
		if (confirmed.isEmpty()) {
			pvpRound = pvp;
			offeredTo = null; // the offered dealer seat lapses with the first confirmed bet (§21.9 rotation)
		}
		confirmed.put(id, new Bet(ante, trips, seat));
		lastBets.put(id, new long[] {ante, trips});
		names.put(id, player.getName().getString());
		setChanged();
		if (allSeatedConfirmed()) {
			startRound();
		} else if (ticksLeft("bet") < 0) {
			int ticks = c.betTimerTicks;
			startTimer("bet", ticks);
			tellTable(Component.translatable("msg.burmaldaholic.uth.round_starts_in", Texts.plural("unit.burmaldaholic.second_acc", (ticks + 19) / 20)),
				id);
		}
	}

	private void clear(ServerPlayer player) {
		if (!BETTING.equals(phase()) || !confirmed.containsKey(player.getUUID())) {
			return;
		}
		refundConfirmed(player.getUUID(), null);
		if (confirmed.isEmpty()) {
			cancelTimer("bet");
			pvpRound = false;
		} else if (allSeatedConfirmed()) {
			startRound();
		}
	}

	private void playChip() {
		if (CoreSounds.CHIP_PLACE != null && level != null) {
			level.playSound(null, worldPosition, CoreSounds.CHIP_PLACE, SoundSource.BLOCKS, 0.6f, 1.2f);
		}
	}

	// ---- round flow -----------------------------------------------------------------------------

	private void startRound() {
		cancelTimer("bet");
		List<UthRound.Entry> entries = new ArrayList<>();
		int fallbackSeat = 100;
		for (Map.Entry<UUID, Bet> e : List.copyOf(confirmed.entrySet())) {
			UUID id = e.getKey();
			Bet b = e.getValue();
			boolean staked = pvpStakes.containsKey(id) || stakeOf(id) >= UthLimits.confirmCost(b.ante(), b.trips());
			if (!staked) {
				confirmed.remove(id);
				continue;
			}
			int seat = seats().seatOf(id).orElse(b.seat() >= 0 ? b.seat() : fallbackSeat++);
			entries.add(new UthRound.Entry(seat, id, nameOf(id), b.ante(), b.trips()));
		}
		confirmed.clear();
		if (entries.isEmpty()) {
			pvpRound = false;
			setPhase(BETTING);
			return;
		}
		addBotEntries(entries);
		int[] deck = stackedDeck != null ? stackedDeck : UthCards.shuffledDeck(bound -> OddsService.get().fair().nextInt(bound));
		stackedDeck = null;
		round = UthRound.deal(entries, deck);
		away.clear();
		royalThisRound = false;
		bankResultShown = false;
		for (UthRound.Seat s : round.seats()) {
			if (!s.bot && (online(s.player) == null || !seats().isSeated(s.player))) {
				away.add(s.player);
			}
		}
		if (level != null && CoreSounds.CARD_SHUFFLE != null) {
			level.playSound(null, worldPosition, CoreSounds.CARD_SHUFFLE, SoundSource.BLOCKS, 0.8f, 1.0f);
		}
		setPhase(PREFLOP);
		stage(UthPub.DEAL);
		setChanged();
		openDecisions();
	}

	private void stage(int kind) {
		stageSeq++;
		stageKind = kind;
		stageStart = level == null ? 0 : level.getGameTime();
	}

	/** Seats dealt into the running round (lane count of {@link UthBeats}). */
	private int roundSeats() {
		return round == null ? 1 : Math.max(1, round.seats().size());
	}

	/**
	 * Starts a decision street: every pending seat asks its {@link SeatDecider} (players who left: the safe
	 * default at once; humans: wait for input); the shared timer runs if anybody is left to decide.
	 */
	private void openDecisions() {
		UthRound r = round;
		if (r == null) {
			return;
		}
		for (UthRound.Seat s : r.pendingSeats()) {
			if (s.bot) {
				continue; // table bots think below, inside the street's decision window
			}
			if (online(s.player) == null) {
				away.add(s.player);
			}
			Decision d = deciderFor(s).decide(r, s, deciderContext(s));
			if (d != null) {
				applyDecision(s, d, false);
			}
		}
		for (UthRound.Seat s : r.pendingSeats()) {
			if (s.bot) {
				scheduleBotDecision(r, s);
			}
		}
		if (r.anyPending()) {
			// preflop: the decision window opens with the deal and runs its full length after the last card lands
			int deal = r.street() == UthRound.Street.PREFLOP ? UthBeats.dealTicks(roundSeats()) : 0;
			startTimer("decide", cfg().decisionTimerTicks + deal);
		} else {
			advanceStreet();
		}
	}

	/**
	 * Who decides for a seat. Players at the table decide on the screen ({@link SeatDecider#HUMAN}); players
	 * who left get the §21.4 safe default. Bot participants (BOTS.md, later) plug in here.
	 */
	protected SeatDecider deciderFor(UthRound.Seat seat) {
		return away.contains(seat.player) ? SeatDecider.SAFE_DEFAULT : SeatDecider.HUMAN;
	}

	private SeatDecider.Context deciderContext(UthRound.Seat seat) {
		return new SeatDecider.Context(cfg().allow3x, cfg().autoPlayMadeHands, seat.bot ? BOT_BALANCE : balanceOf(seat.player));
	}

	private void decide(ServerPlayer player, Decision d) {
		UthRound r = round;
		UthRound.Seat seat = r == null ? null : r.seat(player.getUUID());
		if (seat == null || away.contains(player.getUUID()) || !r.pending(seat)) {
			sendError(player, Component.translatable("gui.burmaldaholic.error.not_your_turn"));
			return;
		}
		if (!r.legal(seat, cfg().allow3x).contains(d)) {
			return;
		}
		if (d.isBet() && !collectPlay(seat, d.multiple() * seat.ante, player)) {
			return;
		}
		r.decide(seat, d, cfg().allow3x);
		setChanged();
		afterDecision();
	}

	private void afterDecision() {
		UthRound r = round;
		if (r != null && !r.settled() && !r.anyPending() && isDecisionPhase()) {
			cancelTimer("decide");
			advanceStreet();
		}
	}

	private boolean isDecisionPhase() {
		String p = phase();
		return (PREFLOP.equals(p) || FLOP.equals(p) || RIVER.equals(p)) && ticksLeft("step") < 0;
	}

	/**
	 * Collects a Play bet. Online seat of a house round: raises the core stake. A gone seat (default Bet
	 * ×1) or a player-banked round: moved directly from the balance (works offline).
	 */
	private boolean collectPlay(UthRound.Seat seat, long amount, @Nullable ServerPlayer online) {
		MinecraftServer server = server();
		if (server == null || amount <= 0) {
			return false;
		}
		if (pvpRound) {
			if (!Economies.get().transfer(server, AccountId.player(seat.player), AccountId.HOUSE, amount, Transaction.bet(UthModule.ID)).ok()) {
				if (online != null) {
					sendError(online, Component.translatable("gui.burmaldaholic.uth.unaffordable"));
				}
				return false;
			}
			pvpStakes.merge(seat.player, amount, Long::sum);
			setChanged();
			return true;
		}
		if (online != null && !away.contains(seat.player) && stakeOf(seat.player) > 0) {
			return placeBet(online, amount, 1, 0, 0, false).isOk();
		}
		String bankroll = openStakes().stream().filter(s -> s.player().equals(seat.player)).map(OpenStake::bankroll).findFirst().orElse("");
		AccountId bank = bankroll.isEmpty() ? AccountId.HOUSE : AccountId.bankroll(bankroll);
		if (!Economies.get().transfer(server, AccountId.player(seat.player), bank, amount, Transaction.bet(UthModule.ID)).ok()) {
			return false;
		}
		directPlay.merge(seat.player, amount, Long::sum);
		directPlayBank.put(seat.player, bankroll);
		setChanged();
		return true;
	}

	/** §21.4 default action for a seat (timeout, gone player, play-out). */
	private void applyDefault(UthRound.Seat seat, boolean timeout) {
		UthRound r = round;
		if (r == null || !r.pending(seat)) {
			return;
		}
		Decision d = SeatDecider.SAFE_DEFAULT.decide(r, seat, deciderContext(seat));
		applyDecision(seat, d == null ? Decision.CHECK : d, timeout);
	}

	/** Applies a decider's decision (collects the Play bet; a bet that cannot be collected checks / folds). */
	private void applyDecision(UthRound.Seat seat, Decision d, boolean timeout) {
		UthRound r = round;
		if (r == null || !r.pending(seat)) {
			return;
		}
		if (!r.legal(seat, true).contains(d)) {
			d = r.street() == UthRound.Street.RIVER ? Decision.FOLD : Decision.CHECK;
		}
		ServerPlayer p = away.contains(seat.player) || seat.bot ? null : online(seat.player);
		// bots: virtual chips (BOTS.md §5.2) — nothing is collected
		if (d.isBet() && !seat.bot && !collectPlay(seat, (long) d.multiple() * seat.ante, p)) {
			d = r.street() == UthRound.Street.RIVER ? Decision.FOLD : Decision.CHECK;
		}
		r.decide(seat, d, true);
		if (timeout && p != null) {
			p.sendSystemMessage(switch (d) {
				case BET_1X -> Component.translatable("msg.burmaldaholic.uth.auto_play",
					Component.translatable("gui.burmaldaholic.poker.hand." + UthCards.handName(r.finalValue(seat))));
				case FOLD -> Component.translatable("msg.burmaldaholic.uth.auto_fold");
				default -> Component.translatable("msg.burmaldaholic.uth.auto_check");
			});
		}
		setChanged();
	}

	private void advanceStreet() {
		UthRound r = round;
		if (r == null || r.settled()) {
			return;
		}
		r.advance(pays());
		switch (r.street()) {
			case FLOP -> setPhase(FLOP);
			case RIVER -> setPhase(RIVER);
			default -> setPhase(SHOWDOWN);
		}
		stage(switch (r.street()) {
			case FLOP -> UthPub.FLOP;
			case RIVER -> UthPub.RIVER;
			default -> UthPub.SHOWDOWN;
		});
		if (level != null && CoreSounds.CARD_DEAL != null) {
			level.playSound(null, worldPosition, CoreSounds.CARD_DEAL, SoundSource.BLOCKS, 0.7f, 1.0f);
		}
		// the showdown settles at its gate: dealer flips, the qualify pause (same either way), seat reveal, per-seat settle
		startTimer("step", r.settled() ? Math.max(REVEAL_TICKS, UthBeats.showdownTicks(roundSeats())) : REVEAL_TICKS);
		setChanged();
	}

	@Override
	protected void onTimer(String id) {
		switch (id) {
			case "bet" -> {
				if (BETTING.equals(phase()) && !confirmed.isEmpty()) {
					startRound();
				}
			}
			case "decide" -> {
				UthRound r = round;
				if (r != null && !r.settled()) {
					for (UthRound.Seat s : r.pendingSeats()) {
						applyDefault(s, true);
					}
					afterDecision();
				}
			}
			case "step" -> {
				UthRound r = round;
				if (r == null) {
					break;
				}
				if (RESULT.equals(phase())) {
					toBetting();
				} else if (r.settled()) {
					settleAll();
					setPhase(RESULT);
					stage(UthPub.RESULT);
					startTimer("step", RESULT_TICKS);
				} else {
					openDecisions();
				}
			}
			default -> {
			}
		}
		syncViewers();
	}

	private void toBetting() {
		boolean houseRound = !pvpRound;
		round = null;
		away.clear();
		roundBots.clear();
		pvpRound = false;
		setPhase(BETTING);
		stageKind = 0;
		if (houseRound) {
			standInRotation();
		}
		// bots' safe point: the start of BETTING (join / leave / pending settings, then their bet timers)
		botSafePoint();
		setChanged();
	}

	// ---- settlement -----------------------------------------------------------------------------

	private void settleAll() {
		UthRound r = round;
		MinecraftServer server = server();
		if (r == null || server == null || !r.settled()) {
			return;
		}
		long bankerNet = 0;
		long wagered = 0;
		int players = 0;
		List<UthBotRules.SeatResultFacts> quipFacts = new ArrayList<>();
		for (UthRound.Seat s : r.seats()) {
			Settlement.Result res = s.result;
			if (res == null) {
				continue;
			}
			BotProfile botProfile = roundBots.get(s.player);
			quipFacts.add(new UthBotRules.SeatResultFacts(s.bot && botProfile != null ? botProfile.key() : s.player.toString(), s.bot,
				UthBotRules.Outcome.valueOf(res.outcome().name()), s.ante > 0 ? (double) res.net() / s.ante : 0, res.blindBonus()));
			if (s.bot) {
				continue; // atmosphere bots: shown only - never settled, never in a report or the bank's result (BOTS.md §5.2)
			}
			players++;
			long ret = res.totalReturn();
			List<String> tags = new ArrayList<>(List.of("uth", res.outcome().name().toLowerCase(java.util.Locale.ROOT)));
			if (s.playMultiple == 4 && s.playStreet == UthRound.Street.PREFLOP) {
				tags.add("four_x");
			}
			if (res.hand() == PayHand.ROYAL) {
				tags.add("royal");
				if (res.blindBonus()) {
					tags.add("royal_blind"); // §21.7: diamond rain replaces chaos' big-win roll for this settlement
				}
			}
			double edge = UthMath.edgeOf(res.staked() - res.trips(), res.trips());
			if (pvpRound) {
				long staked = pvpStakes.getOrDefault(s.player, res.staked());
				pvpStakes.remove(s.player);
				releaseReservation(s.player);
				payFromHouse(server, s.player, ret, staked);
				bankerNet += staked - ret;
				wagered += staked;
				PlayResult pr = PlayResult.of(UthModule.ID, staked, ret).pvp().withTable(level, worldPosition, "").withTags(tags.toArray(String[]::new));
				PlayResults.fire(server, s.player, pr);
			} else {
				long extra = directPlay.getOrDefault(s.player, 0L);
				directPlay.remove(s.player);
				directPlayBank.remove(s.player);
				settle(s.player, ret, pr -> withExtraBet(pr, extra).withEdge(edge).withTags(tags.toArray(String[]::new)));
			}
			grantAdvancements(server, s, res);
			if (away.contains(s.player)) {
				tell(s.player, Component.translatable("msg.burmaldaholic.core.auto_completed", netText(res.net())));
			}
		}
		if (pvpRound && banker != null) {
			settleBanker(server, bankerNet, wagered, players);
		}
		roundQuip(quipFacts);
		setChanged();
	}

	/** House round report including a Play bet collected directly from a gone player. */
	private static PlayResult withExtraBet(PlayResult r, long extra) {
		if (extra <= 0) {
			return r;
		}
		return new PlayResult(r.gameId(), r.bet() + extra, r.payout(), r.houseBanked(), r.stakeKind(), r.houseEdge(), r.tags(), r.table(),
			r.bankroll(), r.deferred(), r.goldenHour());
	}

	/** Player-banked seat: the returned stake as a non-garnishable transfer, the winnings as a payout (review m8). */
	private void payFromHouse(MinecraftServer server, UUID player, long ret, long staked) {
		long stakeBack = Math.min(ret, staked);
		if (stakeBack > 0) {
			transferOrLog(AccountId.HOUSE, AccountId.player(player), stakeBack, new Transaction(UthModule.ID, "stake_return", Transaction.Kind.TRANSFER));
		}
		if (ret - stakeBack > 0) {
			transferOrLog(AccountId.HOUSE, AccountId.player(player), ret - stakeBack, Transaction.payout(UthModule.ID));
		}
	}

	private void settleBanker(MinecraftServer server, long bankerNet, long wagered, int players) {
		Banker b = banker;
		long rake = BankRules.rake(bankerNet, cfg().pvp.rakePercent);
		b.bank += bankerNet - rake;
		b.reserved = 0;
		pvpReserved.clear();
		if (rake > 0) {
			collectRake(rake); // owned casino: to the owner's bankroll; else it stays in the bank (removed)
		}
		lastBankResult = bankerNet - rake;
		lastRake = rake;
		bankResultShown = true;
		PlayResult pr = PlayResult.of(UthModule.ID, wagered, Math.max(0, wagered + bankerNet - rake)).pvp().withTable(level, worldPosition, "")
			.withTags("uth", "banker");
		PlayResults.fire(server, b.player, pr);
		tell(b.player, Component.translatable("gui.burmaldaholic.uth.pvp.round_result", signed(bankerNet - rake), Texts.number(rake)));
		if (bankerNet - rake > 0 && players >= 2) {
			UthAdvancements.grant(server, b.player, UthAdvancements.HOUSE_SEAT);
		}
		b.rounds++;
		UthConfig c = cfg();
		if (b.leaving || online(b.player) == null) {
			returnBank(true);
			return;
		}
		if (!BankRules.canKeepBanking(b.bank, c.pvp.minBank, minBet(), pays())) {
			tell(b.player, Component.translatable("msg.burmaldaholic.uth.pvp.bank_too_low"));
			returnBank(true);
			return;
		}
		if (c.pvp.bankerRounds > 0 && b.rounds >= c.pvp.bankerRounds) {
			b.rounds = 0;
			offerSeatClockwise(b.formerSeat);
		}
	}

	private void offerSeatClockwise(int from) {
		int n = seats().size();
		for (int k = 1; k <= n; k++) {
			int idx = Math.floorMod(from + k, n);
			for (TableSeats.Seat s : seats().occupied()) {
				if (s.index() == idx) {
					offeredTo = s.player();
					tell(s.player(), Component.translatable("msg.burmaldaholic.uth.pvp.seat_offered"));
					return;
				}
			}
		}
	}

	private void grantAdvancements(MinecraftServer server, UthRound.Seat s, Settlement.Result res) {
		if (s.playMultiple == 4 && s.playStreet == UthRound.Street.PREFLOP && res.playNet() > 0) {
			UthAdvancements.grant(server, s.player, UthAdvancements.FOUR_X);
		}
		if (res.hand() == PayHand.ROYAL && (res.blindBonus() || res.tripsPaid())) {
			UthAdvancements.grant(server, s.player, UthAdvancements.ROYAL);
		}
		if (res.hand() == PayHand.ROYAL && res.blindBonus() && !royalThisRound) {
			// §21.7: server-wide broadcast + diamond rain for that player (chaos, if present)
			royalThisRound = true;
			server.getPlayerList().broadcastSystemMessage(Component.translatable("msg.burmaldaholic.uth.royal_broadcast",
				Texts.raw(nameOf(s.player)), Texts.chipsAcc(res.net())), false);
			ServerPlayer p = online(s.player);
			if (p != null) {
				UthChaos.diamondRain(p);
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

	/** "+1 200" / "−350" (sign and digits only, language neutral). */
	static Component signed(long n) {
		return Component.empty().append(Texts.raw(n >= 0 ? "+" : "\u2212")).append(Texts.number(Math.abs(n)));
	}

	private void transferOrLog(AccountId from, AccountId to, long amount, Transaction why) {
		MinecraftServer server = server();
		if (server == null || amount <= 0) {
			return;
		}
		Economy eco = Economies.get();
		if (!eco.transfer(server, from, to, amount, why).ok()) {
			Burmaldaholic.LOGGER.error("uth table {}: transfer {} {} -> {} failed", worldPosition, amount, from, to);
		}
	}

	// ---- dealer seat (§21.9) --------------------------------------------------------------------

	private void takeBank(ServerPlayer player, long amount) {
		UUID id = player.getUUID();
		UthConfig c = cfg();
		if (!playerBanked()) {
			sendError(player, Component.translatable("gui.burmaldaholic.error.disabled"));
			return;
		}
		boolean offered = id.equals(offeredTo);
		if (banker != null && !offered) {
			sendError(player, Component.translatable("gui.burmaldaholic.uth.error.seat_taken"));
			return;
		}
		if (!BETTING.equals(phase()) || !confirmed.isEmpty()) {
			sendError(player, Component.translatable("gui.burmaldaholic.uth.error.seat_next_round"));
			return;
		}
		if (!sit(player)) {
			return;
		}
		MinecraftServer server = player.level().getServer();
		if (CoreServices.vip().tier(server, id) < c.pvp.minBankerVip) {
			sendError(player, Component.translatable("gui.burmaldaholic.error.vip_required", VipTiers.name(c.pvp.minBankerVip)));
			return;
		}
		if (CoreServices.debt().owed(server, id) > 0) {
			sendError(player, Component.translatable("gui.burmaldaholic.uth.error.pvp_owing"));
			return;
		}
		Component veto = Wagers.check(player, new WagerVeto.Context(UthModule.ID, Stake.Kind.CHIPS, worldPosition, true));
		if (veto != null) {
			sendError(player, veto);
			return;
		}
		long minBank = Math.max(c.pvp.minBank, pays().worstCase(Math.max(1, minBet()), 0));
		if (amount < minBank) {
			sendError(player, Component.translatable("gui.burmaldaholic.uth.error.min_bank", Texts.number(minBank)));
			return;
		}
		long balance = Economies.get().balance(player);
		if (amount > balance) {
			sendError(player, Component.translatable("gui.burmaldaholic.error.insufficient_funds", Texts.number(balance)));
			return;
		}
		if (!Economies.get().transfer(server, AccountId.player(id), AccountId.HOUSE, amount, Transaction.of(UthModule.ID, "bank_escrow")).ok()) {
			sendError(player, Component.translatable("gui.burmaldaholic.error.insufficient_funds", Texts.number(balance)));
			return;
		}
		if (banker != null) {
			returnBank(false); // the current banker keeps the seat only if nobody accepts the offer
		}
		int seat = seats().seatOf(id).orElse(0);
		quietLeave = true;
		try {
			leave(id, LeaveReason.LEFT); // the banker gives up their player seat
		} finally {
			quietLeave = false;
		}
		offeredTo = null;
		names.put(id, player.getName().getString());
		banker = new Banker(id, player.getName().getString(), seat, amount);
		// under a human banker bots only watch (BOTS.md §4.6): their virtual bets vanish, the stand-in leaves
		clearBotBets();
		standIn = null;
		houseRounds = 0;
		lastBankResult = 0;
		lastRake = 0;
		bankResultShown = false;
		tellTable(Component.translatable("msg.burmaldaholic.uth.pvp.took_seat", player.getDisplayName(), Texts.chipsAcc(amount)), null);
		setChanged();
	}

	private void leaveBank(ServerPlayer player) {
		Banker b = banker;
		if (b == null || !b.player.equals(player.getUUID())) {
			return;
		}
		if (bankInPlay()) {
			b.leaving = true; // takes effect after the current round
			return;
		}
		returnBank(true);
	}

	/** The bank has seat bets against it (confirmed or dealt). */
	private boolean bankInPlay() {
		return pvpRound && (round != null || !confirmed.isEmpty() || !pvpStakes.isEmpty());
	}

	/** Returns the whole bank to the banker's balance (offline-safe) and frees the dealer seat. */
	private void returnBank(boolean announce) {
		Banker b = banker;
		if (b == null) {
			return;
		}
		banker = null;
		if (b.bank > 0) {
			transferOrLog(AccountId.HOUSE, AccountId.player(b.player), b.bank, new Transaction(UthModule.ID, "bank_return", Transaction.Kind.TRANSFER));
			OfflineMail.chips(server(), b.player, "msg.burmaldaholic.uth.bank_returned", b.bank, true);
		}
		if (announce) {
			tellTable(Component.translatable("msg.burmaldaholic.uth.pvp.left_seat", Texts.raw(b.name)), null);
		}
		// a house round again: seated bots are dealt in (virtual bets from now on)
		if (BETTING.equals(phase()) && round == null) {
			scheduleBotBets();
		}
		setChanged();
	}

	/**
	 * The banker left (disconnect / walked away). After the deal the round plays out against the bank and
	 * the bank returns afterwards; before the deal the confirmed seat bets stay and the round becomes a house
	 * round (bank reservations released, the house re-checks its own), §21.9.
	 */
	private void bankerGone() {
		Banker b = banker;
		if (b == null) {
			return;
		}
		if (round != null && pvpRound) {
			b.leaving = true;
			return;
		}
		if (pvpRound && !confirmed.isEmpty()) {
			convertToHouseRound();
		}
		returnBank(true);
	}

	private void convertToHouseRound() {
		pvpRound = false;
		for (Map.Entry<UUID, Bet> e : List.copyOf(confirmed.entrySet())) {
			UUID id = e.getKey();
			Bet bet = e.getValue();
			long amount = pvpStakes.getOrDefault(id, 0L);
			pvpStakes.remove(id);
			releaseReservation(id);
			transferOrLog(AccountId.HOUSE, AccountId.player(id), amount, Transaction.refund(UthModule.ID));
			ServerPlayer p = online(id);
			boolean ok = p != null && placeBet(p, UthLimits.confirmCost(bet.ante(), bet.trips()), 1, 0, pays().worstCase(bet.ante(), bet.trips()), false).isOk();
			if (!ok) {
				confirmed.remove(id);
				tell(id, Component.translatable("msg.burmaldaholic.uth.bets_refunded"));
			}
		}
		if (confirmed.isEmpty()) {
			cancelTimer("bet");
		}
	}

	// ---- ticking, removal, persistence ------------------------------------------------------------

	@Override
	protected void serverTick(ServerLevel level) {
		MinecraftServer server = level.getServer();
		if (!orphaned.isEmpty()) {
			returnOrphans(server);
		}
		if (!botTasks.isEmpty()) {
			runBotTasks(level.getGameTime());
		}
		if (server.getTickCount() % 20 != 0) {
			return;
		}
		if (!CasinoMode.isEnabled(level)) {
			if (round != null || !confirmed.isEmpty() || banker != null) {
				playOutNow("casino mode off"); // §4.1: drawn rounds play out, undrawn bets are refunded
			}
			return;
		}
		Banker b = banker;
		if (b != null && !b.leaving) {
			ServerPlayer p = server.getPlayerList().getPlayer(b.player);
			double max = CasinoConfig.multiplayer().tableLeaveDistance;
			if (p == null || p.isRemoved() || p.level() != level || p.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(worldPosition)) > max * max) {
				bankerGone();
				syncViewers();
			}
		}
	}

	@Override
	protected boolean hasRoundInPlay() {
		return round != null || banker != null || !confirmed.isEmpty() || !pvpStakes.isEmpty() || !directPlay.isEmpty();
	}

	/**
	 * Table broken, chunk unloaded, server stopping or casino mode off (§21.5, §4.1): a dealt round is
	 * played out on its deck with every pending decision at its default and settled; confirmed bets of a
	 * round not dealt yet are returned; the bank is returned last.
	 */
	@Override
	protected void playOutForRemoval(ServerLevel level) {
		cancelTimer("bet");
		cancelTimer("decide");
		cancelTimer("step");
		UthRound r = round;
		if (r != null) {
			for (UthRound.Seat s : r.seats()) {
				away.add(s.player);
			}
			for (int guard = 0; guard < 8 && !r.settled(); guard++) {
				for (UthRound.Seat s : r.pendingSeats()) {
					applyDefault(s, false);
				}
				r.advance(pays());
			}
			if (!RESULT.equals(phase())) {
				settleAll();
			}
		}
		for (UUID id : List.copyOf(confirmed.keySet())) {
			refundConfirmed(id, "msg.burmaldaholic.uth.bets_refunded");
		}
		// anything still held for a seat (should be nothing) goes back to its owner
		for (Map.Entry<UUID, Long> e : List.copyOf(pvpStakes.entrySet())) {
			transferOrLog(AccountId.HOUSE, AccountId.player(e.getKey()), e.getValue(), Transaction.refund(UthModule.ID));
		}
		pvpStakes.clear();
		pvpReserved.clear();
		for (Map.Entry<UUID, Long> e : List.copyOf(directPlay.entrySet())) {
			String bankroll = directPlayBank.getOrDefault(e.getKey(), "");
			transferOrLog(bankroll.isEmpty() ? AccountId.HOUSE : AccountId.bankroll(bankroll), AccountId.player(e.getKey()), e.getValue(),
				Transaction.refund(UthModule.ID));
		}
		directPlay.clear();
		directPlayBank.clear();
		returnBank(false);
		offeredTo = null;
		round = null;
		away.clear();
		roundBots.clear();
		pvpRound = false;
		setPhase(BETTING);
		// the round is paid: every bot leaves (table broken / unloaded, server stopping, casino off)
		endBots(level);
		setChanged();
	}

	private List<Escrow> escrows() {
		List<Escrow> out = new ArrayList<>();
		if (banker != null && banker.bank > 0) {
			out.add(new Escrow(banker.player, "bank", banker.bank, ""));
		}
		pvpStakes.forEach((id, amount) -> out.add(new Escrow(id, "pvp_bet", amount, "")));
		directPlay.forEach((id, amount) -> out.add(new Escrow(id, "play", amount, directPlayBank.getOrDefault(id, ""))));
		return out;
	}

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		List<Escrow> list = escrows();
		list.addAll(orphaned);
		if (!list.isEmpty()) {
			output.store(ESCROW_KEY, Escrow.CODEC.listOf(), list);
		}
		CompoundTag bots = savedBots;
		if (tableBots != null) {
			bots = new CompoundTag();
			tableBots.save(bots);
		}
		if (bots != null) {
			output.store(BOTS_KEY, CompoundTag.CODEC, bots);
		}
	}

	@Override
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		input.read(PUB_KEY, CompoundTag.CODEC).ifPresent(t -> {
			clientPub = t;
			clientPubCache = null;
		});
		orphaned.clear();
		input.read(ESCROW_KEY, Escrow.CODEC.listOf()).ifPresent(orphaned::addAll);
		savedBots = input.read(BOTS_KEY, CompoundTag.CODEC).orElse(null);
		if (tableBots != null && savedBots != null) {
			tableBots.load(savedBots);
		}
	}

	/** After a crash: every escrow saved with the table goes back to its owner (§21.9: an orphaned bank is always returned). */
	private void returnOrphans(MinecraftServer server) {
		for (Escrow e : List.copyOf(orphaned)) {
			AccountId from = e.bankroll().isEmpty() ? AccountId.HOUSE : AccountId.bankroll(e.bankroll());
			Burmaldaholic.LOGGER.info("uth table {}: returning orphaned {} of {} chips to {}", worldPosition, e.kind(), e.amount(), e.player());
			if (!Economies.get().transfer(server, from, AccountId.player(e.player()), e.amount(), Transaction.refund(UthModule.ID)).ok()) {
				Economies.get().transfer(server, AccountId.HOUSE, AccountId.player(e.player()), e.amount(), Transaction.refund(UthModule.ID));
			}
			if (e.kind().equals("bank")) {
				OfflineMail.chips(server, e.player(), "msg.burmaldaholic.uth.bank_returned", e.amount(), true);
			}
		}
		orphaned.clear();
		setChanged();
	}

	// ---- bots: seats, safe points, virtual bets, decisions (BOTS.md §3, §4.5, §4.6) --------------------------

	@Override
	public String botGameId() {
		return UthModule.ID;
	}

	@Override
	public BotRole botRole() {
		return BotRole.ATMOSPHERE;
	}

	@Override
	public int botSeatCount() {
		return seats().size();
	}

	/** Humans in player seats (the banker is not one), in sit-down order. */
	@Override
	public List<UUID> seatedHumans() {
		List<UUID> seated = new ArrayList<>();
		for (TableSeats.Seat s : seats().occupied()) {
			seated.add(s.player());
		}
		List<UUID> out = new ArrayList<>();
		for (UUID id : sitOrder) {
			if (seated.contains(id)) {
				out.add(id);
			}
		}
		for (UUID id : seated) {
			if (!out.contains(id)) {
				out.add(id);
			}
		}
		return out;
	}

	private Set<Integer> humanSlots() {
		Set<Integer> out = new HashSet<>();
		for (TableSeats.Seat s : seats().occupied()) {
			out.add(s.index());
		}
		return out;
	}

	@Override
	public List<SeatOccupant> occupants() {
		int n = seats().size();
		Map<Integer, SeatOccupant> humans = new HashMap<>();
		for (TableSeats.Seat s : seats().occupied()) {
			humans.put(s.index(), new SeatOccupant.Human(s.player(), s.name()));
		}
		slots.settle(n, humans.keySet());
		Map<String, SeatOccupant> bots = new HashMap<>();
		botSeats.forEach((k, p) -> bots.put(k, new SeatOccupant.Bot(p, BotRole.ATMOSPHERE, Purse.NONE)));
		return slots.occupants(n, humans, bots);
	}

	@Override
	public boolean seatBot(SeatOccupant.Bot bot, long stack) {
		String key = bot.key();
		if (slots.place(key, seats().size(), humanSlots()) == null) {
			return false;
		}
		botSeats.put(key, bot.profile());
		return true;
	}

	@Override
	public long unseatBot(String botKey) {
		slots.remove(botKey);
		botSeats.remove(botKey);
		botBets.remove(botKey);
		botBetScheduled.remove(botKey);
		return 0; // atmosphere: nothing to return
	}

	@Override
	public SeatingMath.YieldRule yieldRule() {
		return SeatingMath.YieldRule.HIGHEST_SEAT;
	}

	/** The table's bot state (created on first use; the bots UI finds the table through it). Chatter: {@link TableBots#say}. */
	@Override
	public TableBots tableBots() {
		TableBots tb = tableBots;
		if (tb == null) {
			tb = new TableBots(this, botDefaults(), OwnerControls.unowned(Math.max(1, seats().size())));
			if (savedBots != null) {
				tb.load(savedBots);
			}
			tableBots = tb;
		}
		return tb;
	}

	/**
	 * Craftable / worldgen defaults; player-banked tables: stand-in dealer plate, atmosphere seats opt-in (BOTS.md §2.3).
	 * Generated tables use the worldgen preset (J-G6) as it is.
	 */
	private BotSettings botDefaults() {
		var preset = dev.nezo.burmaldaholic.core.bots.BotPresets.of(this, UthModule.ID);
		if (preset.isPresent()) {
			return preset.get().defaults();
		}
		boolean worldgen = preset().isPresent();
		BotSettings d = TableBots.defaultsFor(UthModule.ID, worldgen);
		return variant == Variant.PLAYER_BANKED && !worldgen ? d.withPolicy(SeatPolicy.MIXED).withCount(0) : d;
	}

	/** The worldgen preset's level mix when present (J-G6). */
	@Override
	public int[] botDifficultyMix() {
		int[] mix = dev.nezo.burmaldaholic.core.bots.BotPresets.mix(this, UthModule.ID);
		return mix != null ? mix : BotTable.super.botDifficultyMix();
	}

	/** The worldgen preset's name theme when present (J-G6; End lounge: ender). */
	@Override
	public BotRoster.Theme botNameTheme() {
		return dev.nezo.burmaldaholic.core.bots.BotPresets.theme(this, UthModule.ID, BotTable.super.botNameTheme());
	}

	/**
	 * Seats &amp; bots access (private tables, someone else's "Just me and bots", BOTS.md §2.1, §2.5) and the
	 * claim on a bot's seat: a claimant was told "seat after this round" by core and is refused quietly here;
	 * the next safe point seats them.
	 */
	private boolean admitBots(ServerPlayer player) {
		if (!Bots.enabled()) {
			return true;
		}
		try {
			Result<Boolean> r = tableBots().admit(player);
			if (!r.isOk()) {
				sendError(player, r.error());
				return false;
			}
			return Boolean.TRUE.equals(r.value());
		} catch (RuntimeException e) {
			Burmaldaholic.LOGGER.error("uth table {}: bot admission failed", worldPosition, e);
			return true;
		}
	}

	/**
	 * Safe point (start of BETTING, and any time during BETTING: virtual bets simply vanish). Bots join / leave,
	 * pending settings apply, claimants get their seat; bots that joined get their bet timers.
	 */
	private void botSafePoint() {
		if (!(level instanceof ServerLevel sl) || !BETTING.equals(phase()) || round != null || inBotSafePoint) {
			return;
		}
		if (tableBots == null && seats().isEmpty()) {
			return;
		}
		inBotSafePoint = true;
		try {
			TableBots tb = tableBots();
			TableBots.SafePointResult r = tb.safePoint(sl);
			// keep the local seats in step with core
			Set<String> live = new HashSet<>();
			for (TableBots.SeatedBot b : tb.bots()) {
				live.add(b.key());
			}
			for (String k : List.copyOf(botSeats.keySet())) {
				if (!live.contains(k)) {
					unseatBot(k);
				}
			}
			tb.announce(sl, r);
			for (TableBots.SeatedBot b : r.joined()) {
				tb.say(sl, b.profile, "join", null);
			}
			for (UUID c : r.seatedClaimants()) {
				ServerPlayer p = online(c);
				if (p != null && p.level() == level) {
					sit(p); // the seat a bot gave up
				}
			}
			scheduleBotBets();
		} catch (RuntimeException e) {
			Burmaldaholic.LOGGER.error("uth table {}: bot safe point failed", worldPosition, e);
		} finally {
			inBotSafePoint = false;
		}
		syncViewers();
	}

	/** The table stops (broken, unloaded, server stopping, casino off): every bot leaves, stored settings stay. */
	private void endBots(ServerLevel level) {
		clearBotBets();
		standIn = null;
		houseRounds = 0;
		roundBots.clear();
		TableBots tb = tableBots;
		if (tb != null && tb.inSession()) {
			try {
				tb.endSession(level);
			} catch (RuntimeException e) {
				Burmaldaholic.LOGGER.error("uth table {}: ending the bot session failed", worldPosition, e);
			}
		}
		botSeats.clear();
		slots.clear();
	}

	/** Drops every virtual bet and pending bot action of this BETTING / street. */
	private void clearBotBets() {
		botBets.clear();
		botBetScheduled.clear();
		botTasks.clear();
		botEpoch++;
	}

	private void botTask(int ticks, Runnable run) {
		long now = level == null ? 0 : level.getGameTime();
		botTasks.add(new BotTask(now + Math.max(0, ticks), botEpoch, run));
	}

	private void runBotTasks(long now) {
		for (BotTask t : List.copyOf(botTasks)) {
			if (t.due() > now) {
				continue;
			}
			botTasks.remove(t);
			if (t.epoch() != botEpoch) {
				continue;
			}
			try {
				t.run().run();
			} catch (RuntimeException e) {
				Burmaldaholic.LOGGER.error("uth table {}: bot action failed", worldPosition, e);
			}
		}
	}

	/** GameTests: every pending bot action happens now (queued river jobs fall back to their deadline rule). */
	private void runBotTasksForTests() {
		for (int guard = 0; guard < 64 && !botTasks.isEmpty(); guard++) {
			runBotTasks(Long.MAX_VALUE);
		}
	}

	/** Bots are dealt in (not under a human banker: they watch, BOTS.md §4.6). */
	private boolean botsPlay() {
		boolean humanBanker = round != null || !confirmed.isEmpty() ? pvpRound : playerBanked() && banker != null && !banker.leaving;
		return UthBotRules.botsDealtIn(humanBanker);
	}

	/** Largest virtual Ante: what the first seated human may bet (W / 6), else 11 × the minimum. */
	private long maxVirtualAnte(long min) {
		for (TableSeats.Seat s : seats().occupied()) {
			ServerPlayer p = online(s.player());
			if (p != null) {
				long w = limitsFor(p)[1];
				if (w > 0) {
					return Math.max(min, w / 6);
				}
			}
		}
		return min * 11;
	}

	/** Puts down a bot's virtual bets now (if it has none yet). */
	private void placeBotBet(String key) {
		BotProfile bot = botSeats.get(key);
		if (bot == null || botBets.containsKey(key) || !BETTING.equals(phase()) || round != null || !botsPlay() || slots.slotOf(key) == null) {
			return;
		}
		long min = Math.max(1, minBet());
		botBets.put(key, UthBotRules.virtualBet(bot, tableBots().rng(), min, maxVirtualAnte(min), cfg().tripsEnabled));
		setChanged();
	}

	/** Each seated bot bets at a random moment 60–160 t into BETTING; it never delays the round. */
	private void scheduleBotBets() {
		TableBots tb = tableBots;
		if (tb == null || !BETTING.equals(phase()) || round != null || !botsPlay()) {
			return;
		}
		for (String key : botSeats.keySet()) {
			if (botBets.containsKey(key) || !botBetScheduled.add(key)) {
				continue;
			}
			int delay = UthBotRules.virtualBetDelay(tb.rng(), tb.settings().effectiveSpeed(), CasinoConfig.bots().think.fastFactor);
			botTask(delay, () -> {
				placeBotBet(key);
				syncViewers();
			});
		}
	}

	/** Seat id of a bot in a round (stable per bot, never a player's UUID). */
	static UUID botSeatId(String key) {
		return UUID.nameUUIDFromBytes(key.getBytes(java.nio.charset.StandardCharsets.UTF_8));
	}

	/** Atmosphere bots are always ready: one that has not bet yet bets now; then they join the deal (dealt after the board). */
	private void addBotEntries(List<UthRound.Entry> entries) {
		roundBots.clear();
		if (pvpRound || !botsPlay()) {
			clearBotBets();
			return;
		}
		for (String key : botSeats.keySet()) {
			placeBotBet(key);
		}
		Set<Integer> taken = new HashSet<>();
		for (UthRound.Entry e : entries) {
			taken.add(e.seat());
		}
		for (Map.Entry<String, UthBotRules.VirtualBet> e : botBets.entrySet()) {
			BotProfile bot = botSeats.get(e.getKey());
			Integer slot = slots.slotOf(e.getKey());
			if (bot == null || slot == null || !taken.add(slot) || entries.size() >= 7) {
				continue;
			}
			UUID id = botSeatId(e.getKey());
			roundBots.put(id, bot);
			entries.add(new UthRound.Entry(slot, id, "", e.getValue().ante(), e.getValue().trips(), true));
		}
		clearBotBets();
	}

	/**
	 * A bot decides after its think delay (≤ half the decision timer; ≤ 10 t when no human is still deciding).
	 * NORMAL / HARD rivers run the enumeration as a heavy job within that window; at the deadline the partial
	 * result (or the fallback rule) is used. Stale decisions are dropped.
	 */
	private void scheduleBotDecision(UthRound r, UthRound.Seat seat) {
		BotProfile bot = roundBots.get(seat.player);
		if (bot == null) {
			applyDefault(seat, false);
			return;
		}
		TableBots tb = tableBots();
		UthRound.Street street = r.street();
		int epoch = botEpoch;
		BooleanSupplier wanted = () -> round == r && r.street() == street && r.pending(seat) && epoch == botEpoch && isDecisionPhase();
		boolean humansDeciding = false;
		for (UthRound.Seat s : r.pendingSeats()) {
			if (!s.bot && !away.contains(s.player)) {
				humansDeciding = true;
			}
		}
		int think = tb.thinkTicks(bot, false, cfg().decisionTimerTicks);
		if (!humansDeciding) {
			think = Math.min(think, 10);
		}
		UthBotPolicy.View view = UthBotPolicy.View.of(r, seat, BOT_BALANCE, cfg().allow3x, pays());
		boolean[] acted = new boolean[1];
		java.util.function.Consumer<Object> act = work -> {
			if (acted[0] || !wanted.getAsBoolean()) {
				return;
			}
			acted[0] = true;
			Decision d;
			try {
				d = UthBotPolicy.INSTANCE.act(bot, view, work, tb.rng());
			} catch (RuntimeException e) {
				Burmaldaholic.LOGGER.error("uth table {}: bot decision failed", worldPosition, e);
				d = UthBotPolicy.safeDefault(view);
			}
			applyDecision(seat, d, false);
			afterDecision();
			syncViewers();
		};
		BotWork job = null;
		try {
			job = UthBotPolicy.INSTANCE.work(bot, view, tb.rng());
		} catch (RuntimeException e) {
			Burmaldaholic.LOGGER.error("uth table {}: bot work failed", worldPosition, e);
		}
		if (job == null) {
			botTask(think, () -> act.accept(null));
			return;
		}
		Object[] result = new Object[1];
		boolean[] finished = new boolean[1];
		boolean[] thought = new boolean[1];
		BotJobs.submit(job, Math.max(1, think), wanted, res -> {
			result[0] = res;
			finished[0] = true;
			if (thought[0]) {
				act.accept(res);
			}
		});
		botTask(think, () -> {
			thought[0] = true;
			if (finished[0]) {
				act.accept(result[0]);
			}
		});
		// never wait for a queued job past the window: the fallback rule decides
		botTask(think + 20, () -> act.accept(finished[0] ? result[0] : null));
	}

	/** At most one quip after the reveal (never about hidden cards). */
	private void roundQuip(List<UthBotRules.SeatResultFacts> facts) {
		TableBots tb = tableBots;
		if (tb == null || botSeats.isEmpty() || !(level instanceof ServerLevel sl)) {
			return;
		}
		UthBotRules.Quip q = UthBotRules.roundQuip(facts);
		if (q == null) {
			return;
		}
		try {
			if (q.event().equals("win_big")) {
				BotProfile bot = botSeats.get(q.key());
				if (bot != null) {
					tb.say(sl, bot, "win_big", null);
				}
				return;
			}
			List<BotProfile> speakers = List.copyOf(botSeats.values());
			BotProfile who = speakers.get(tb.rng().nextInt(speakers.size()));
			String human = names.getOrDefault(UUID.fromString(q.key()), "");
			tb.say(sl, who, "human_wins", human);
		} catch (RuntimeException e) {
			Burmaldaholic.LOGGER.warn("uth table {}: bot quip failed: {}", worldPosition, e.toString());
		}
	}

	/** Dealer-plate stand-in of a player-banked house round (BOTS.md §4.6), else null. */
	private @Nullable BotProfile standInBot() {
		if (variant != Variant.PLAYER_BANKED || !cfg().pvp.enabled || !Bots.enabled()) {
			return standIn = null;
		}
		SeatPolicy policy = tableBots != null ? tableBots.settings().policy() : botDefaults().policy();
		boolean on = UthBotRules.standInShown(new UthBotRules.StandInFacts(true, true, banker != null, policy, cfg().pvp.houseRoundsWhenNoBanker));
		if (!on) {
			return standIn = null;
		}
		if (standIn == null) {
			List<String> used = new ArrayList<>();
			for (BotProfile b : botSeats.values()) {
				used.add(b.nameId());
			}
			standIn = BotRoster.create(tableBots().rng(), BotDifficulty.NORMAL, new int[] {0, 1, 0},
				dev.nezo.burmaldaholic.core.bots.BotPresets.theme(this, UthModule.ID, BotRoster.Theme.ENDER), used, false);
		}
		return standIn;
	}

	/** After a house round with a stand-in: offer the dealer seat at every rotation point (chat only). */
	private void standInRotation() {
		if (banker != null || !playerBanked() || standInBot() == null) {
			return;
		}
		houseRounds++;
		if (!UthBotRules.standInOfferDue(houseRounds, cfg().pvp.bankerRounds)) {
			return;
		}
		MinecraftServer server = server();
		int n = seats().size();
		for (int k = 1; server != null && k <= n; k++) {
			int idx = Math.floorMod(standInOfferFrom + k, n);
			TableSeats.Seat s = seats().get(idx);
			if (s == null) {
				continue;
			}
			ServerPlayer p = online(s.player());
			if (p == null || CoreServices.vip().tier(server, s.player()) < cfg().pvp.minBankerVip || CoreServices.debt().owed(server, s.player()) > 0) {
				continue;
			}
			standInOfferFrom = idx;
			p.sendSystemMessage(Component.translatable("msg.burmaldaholic.uth.pvp.seat_offered"));
			return;
		}
	}

	/** Bot seat plates for the screen: betting (virtual bets / Thinking… / Watching) or watching during a round. */
	private ListTag botPlates() {
		ListTag out = new ListTag();
		boolean play = botsPlay();
		UthRound r = round;
		for (Map.Entry<String, BotProfile> e : botSeats.entrySet()) {
			Integer slot = slots.slotOf(e.getKey());
			if (slot == null || (r != null && r.seat(botSeatId(e.getKey())) != null)) {
				continue; // dealt in: the round's players list shows it
			}
			CompoundTag t = new CompoundTag();
			t.putInt("seat", slot);
			t.putString("bot_name", e.getValue().nameId());
			t.putString("bot_level", e.getValue().level().id());
			UthBotRules.VirtualBet bet = botBets.get(e.getKey());
			if (!play || r != null) {
				t.putString("bot_state", "watching");
			} else if (bet != null) {
				t.putString("bot_state", "bet");
				t.putLong("ante", bet.ante());
				t.putLong("trips", bet.trips());
			} else {
				t.putString("bot_state", "thinking");
			}
			out.add(t);
		}
		return out;
	}

	/** Bot state for the screen (settings button, plates, pending settings, per-level cost lines). */
	private void writeBotState(CompoundTag tag, ServerPlayer viewer) {
		if (!Bots.enabled()) {
			return;
		}
		tag.putBoolean("bots_ui", UthBotUi.get().available(viewer));
		if (botSeats.isEmpty()) {
			return;
		}
		tag.put("bots", botPlates());
		tag.putBoolean("bots_virtual", botsPlay());
		TableBots tb = tableBots;
		if (tb != null && tb.pending() != null) {
			tag.putBoolean("bots_pending", true);
		}
		ListTag levels = new ListTag();
		Set<BotDifficulty> seen = new java.util.TreeSet<>();
		for (BotProfile b : botSeats.values()) {
			if (seen.add(b.level())) {
				levels.add(StringTag.valueOf(b.level().id()));
			}
		}
		tag.put("bot_levels", levels);
	}

	/** GameTests: seated bots (key → slot) and their virtual bets. */
	public Map<String, Integer> botSlotsForTests() {
		Map<String, Integer> out = new LinkedHashMap<>();
		for (String k : botSeats.keySet()) {
			Integer s = slots.slotOf(k);
			if (s != null) {
				out.put(k, s);
			}
		}
		return out;
	}

	// ---- client state -------------------------------------------------------------------------------

	@Override
	public CompoundTag writeClientState(ServerPlayer viewer) {
		CompoundTag tag = baseState(viewer);
		UUID me = viewer.getUUID();
		UthConfig c = cfg();
		Paytables pays = pays();
		tag.putString("variant", variant.name().toLowerCase(java.util.Locale.ROOT));
		tag.putBoolean("high_roller", isHighRoller());
		tag.putBoolean("allow3x", c.allow3x);
		tag.putBoolean("trips_enabled", c.tripsEnabled);
		tag.putLong("trips_min", c.minAnte);
		tag.putInt("decision_ticks", c.decisionTimerTicks);
		long[] last = lastBets.get(me);
		if (last != null) {
			tag.putLong("last_ante", last[0]);
			tag.putLong("last_trips", last[1]);
		}
		Bet mine = confirmed.get(me);
		if (mine != null) {
			tag.putLong("my_ante", mine.ante());
			tag.putLong("my_trips", mine.trips());
		}
		tag.putInt("confirmed", confirmed.size());
		ListTag bets = new ListTag();
		confirmed.forEach((id, b) -> {
			CompoundTag t = new CompoundTag();
			t.putInt("seat", seats().seatOf(id).orElse(b.seat()));
			t.putLong("ante", b.ante());
			t.putLong("trips", b.trips());
			bets.add(t);
		});
		tag.put("bets", bets);
		// paytables for the overlay (effective config)
		CompoundTag pt = new CompoundTag();
		for (PayHand h : PayHand.values()) {
			if (h != PayHand.NONE) {
				pt.putDouble("blind_" + h.key(), pays.blindPay(h));
				pt.putInt("trips_" + h.key(), pays.tripsPay(h));
			}
		}
		tag.put("pays", pt);
		writeBankState(tag, viewer);
		writeBotState(tag, viewer);
		UthRound r = round;
		if (r == null) {
			return tag;
		}
		writeStage(tag);
		tag.put("board", new IntArrayTag(r.visibleBoardCards()));
		boolean showdown = r.settled();
		// money and the per-bet lines are shown after the showdown gate (RESULT); the cards are public at the showdown
		boolean settledNow = RESULT.equals(phase());
		if (showdown) {
			tag.put("dealer", new IntArrayTag(r.dealerCards()));
			tag.putString("dealer_hand", UthCards.handName(r.dealerValue()));
			tag.putBoolean("qualifies", r.dealerQualifies());
			tag.putBoolean("settled", settledNow);
			tag.putInt("dealer_best", BestFive.mask(r.dealerCards(), r.board()));
		}
		int deciding = 0;
		ListTag players = new ListTag();
		for (UthRound.Seat s : r.seats()) {
			CompoundTag p = new CompoundTag();
			boolean you = s.player.equals(me);
			p.putInt("seat", s.seat);
			p.putString("name", s.name);
			BotProfile bot = s.bot ? roundBots.get(s.player) : null;
			if (bot != null) {
				p.putBoolean("bot", true);
				p.putString("bot_name", bot.nameId());
				p.putString("bot_level", bot.level().id());
			}
			p.putBoolean("you", you);
			p.putLong("ante", s.ante);
			p.putLong("trips", s.trips);
			p.putInt("play", s.playMultiple);
			p.putString("tag", statusTag(r, s));
			if (r.pending(s)) {
				deciding++;
			}
			if (you || showdown) {
				p.put("cards", new IntArrayTag(s.hole.clone()));
			}
			Settlement.Result res = s.result;
			if (showdown && res != null) {
				p.putString("hand", UthCards.handName(res.playerValue()));
				p.putInt("best", BestFive.mask(s.hole, r.board()));
				if (settledNow) {
					p.putLong("net", res.net());
				}
			}
			players.add(p);
		}
		tag.put("players", players);
		tag.putInt("deciding", deciding);
		UthRound.Seat my = r.seat(me);
		if (my != null) {
			tag.putBoolean("in_round", true);
			tag.putBoolean("away", away.contains(me));
			int v = r.visibleValue(my);
			if (v >= 0) {
				tag.putString("my_hand", UthCards.handName(v));
			}
			if (r.pending(my) && !away.contains(me)) {
				ListTag legal = new ListTag();
				for (Decision d : r.legal(my, c.allow3x)) {
					legal.add(StringTag.valueOf(d.id()));
				}
				tag.put("legal", legal);
			}
			Settlement.Result res = my.result;
			if (showdown && res != null) {
				tag.putInt("my_best", BestFive.mask(my.hole, r.board()));
			}
			if (showdown && settledNow && res != null) {
				CompoundTag rt = new CompoundTag();
				rt.putString("outcome", res.outcome().name().toLowerCase(java.util.Locale.ROOT));
				rt.putLong("ante_net", res.anteNet());
				rt.putLong("blind_net", res.blindNet());
				rt.putLong("play_net", res.playNet());
				rt.putLong("trips_net", res.tripsNet());
				rt.putLong("play", res.play());
				rt.putLong("trips", res.trips());
				rt.putLong("net", res.net());
				rt.putString("pay_hand", res.hand().key());
				rt.putDouble("blind_pay", pays.blindPay(res.hand()));
				rt.putInt("trips_pay", pays.tripsPay(res.hand()));
				tag.put("result", rt);
			}
		} else if (seats().isSeated(me)) {
			tag.putBoolean("waiting_next", true);
		}
		return tag;
	}

	/** The running segment: clients rebuild the same {@link UthBeats} timeline from it. */
	private void writeStage(CompoundTag tag) {
		if (stageKind == 0) {
			return;
		}
		CompoundTag fx = new CompoundTag();
		fx.putInt("seq", stageSeq);
		fx.putInt("kind", stageKind);
		fx.putLong("start", stageStart);
		fx.putInt("seats", roundSeats());
		fx.putInt("seed", SeedMix.mix(SeedMix.mixLong(worldPosition.asLong()), stageSeq));
		tag.put("fx", fx);
	}

	// ---- public tag (in-world renderer + dealer NPC, J-C10 / J-C11) ------------------------------------------

	private static final String PUB_KEY = "burmaldaholic_uth_pub";

	/** Public state: the board face up, the dealer's cards from the showdown on, seats' cards only at the showdown. */
	public UthPub buildPub() {
		UthRound r = round;
		if (r == null || stageKind == 0) {
			return UthPub.EMPTY;
		}
		List<UthRound.Seat> list = r.seats();
		int n = list.size();
		int[] seatIdx = new int[n];
		int[] flags = new int[n];
		long[] bets = new long[n];
		int[] cards = new int[n * 2];
		java.util.Arrays.fill(cards, -1);
		boolean showdown = r.settled();
		for (int i = 0; i < n; i++) {
			UthRound.Seat s = list.get(i);
			seatIdx[i] = s.seat;
			boolean won = RESULT.equals(phase()) && s.result != null && s.result.net() > 0;
			flags[i] = UthPub.flags(s.bot, s.folded, won, s.playMultiple);
			bets[i] = s.ante * 2 + s.trips + s.play();
			if (showdown) {
				cards[i * 2] = s.hole[0];
				cards[i * 2 + 1] = s.hole[1];
			}
		}
		int seed = SeedMix.mix(SeedMix.mixLong(worldPosition.asLong()), stageSeq);
		return new UthPub(stageSeq, stageKind, stageStart, seed, r.visibleBoardCards(), showdown ? r.dealerCards() : new int[0], seatIdx, flags,
			bets, cards);
	}

	/** Client: the last public state received with a block update. */
	public UthPub clientPub() {
		UthPub p = clientPubCache;
		if (p == null) {
			p = UthPub.decode(clientPub.getIntArray("d").orElse(new int[0]));
			clientPubCache = p;
		}
		return p;
	}

	@Override
	public void syncViewers() {
		super.syncViewers();
		publishPub();
	}

	private void publishPub() {
		if (!(level instanceof ServerLevel serverLevel)) {
			return;
		}
		CompoundTag pub = new CompoundTag();
		pub.putIntArray("d", buildPub().encode());
		if (pub.equals(lastPub)) {
			return;
		}
		lastPub = pub;
		BlockState st = getBlockState();
		serverLevel.sendBlockUpdated(worldPosition, st, st, net.minecraft.world.level.block.Block.UPDATE_CLIENTS);
	}

	@Override
	public CompoundTag getUpdateTag(net.minecraft.core.HolderLookup.Provider registries) {
		CompoundTag t = new CompoundTag();
		CompoundTag pub = new CompoundTag();
		pub.putIntArray("d", buildPub().encode());
		t.put(PUB_KEY, pub);
		return t;
	}

	@Override
	public net.minecraft.network.protocol.@Nullable Packet<net.minecraft.network.protocol.game.ClientGamePacketListener> getUpdatePacket() {
		return net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket.create(this);
	}

	/** The timeline of the public segment (client, cached per segment). */
	public @Nullable Timeline clientTimeline() {
		UthPub p = clientPub();
		if (p.seq() == 0) {
			return null;
		}
		if (clientTl == null || clientTlSeq != p.seq()) {
			clientTlSeq = p.seq();
			clientTl = timelineOf(p.kind(), p.seats().length, p.seed());
		}
		return clientTl;
	}

	/** The {@link UthBeats} timeline of a segment kind. */
	public static @Nullable Timeline timelineOf(int kind, int seats, int seed) {
		return switch (kind) {
			case UthPub.DEAL -> UthBeats.deal(seats, seed);
			case UthPub.FLOP -> UthBeats.street(1, seed);
			case UthPub.RIVER -> UthBeats.street(2, seed);
			case UthPub.SHOWDOWN -> UthBeats.showdown(seats, seed);
			case UthPub.RESULT -> UthBeats.result(seed);
			default -> null;
		};
	}

	@Override
	public DealerCueSource.@Nullable Cue dealerCue(long gameTime, float partialTick) {
		UthPub p = clientPub();
		Timeline tl = clientTimeline();
		if (tl == null) {
			return null;
		}
		double t = (gameTime - p.startTick() + partialTick) * 50.0;
		int n = Math.max(1, p.seats().length);
		return DealerCueSource.latest(tl, t, b -> switch (b.kind()) {
			case UthBeats.DEAL, UthBeats.DEALER_DEAL, UthBeats.BOARD -> DealerMotion.Gesture.DEAL;
			case UthBeats.DEALER_FLIP -> DealerMotion.Gesture.FLIP;
			case UthBeats.SETTLE -> b.arg(0) == UthBeats.SETTLE_ORDER[0] ? DealerMotion.Gesture.PAY : null;
			case UthBeats.GATHER -> DealerMotion.Gesture.SWEEP;
			default -> null;
		}, b -> b.lane() < 0 || b.kind().equals(UthBeats.DEALER_DEAL) || b.kind().equals(UthBeats.BOARD) ? 0f
			: n <= 1 ? 0f : (b.lane() / (float) (n - 1)) * 2f - 1f);
	}

	private String statusTag(UthRound r, UthRound.Seat s) {
		if (s.folded) {
			return r.street() == UthRound.Street.RIVER || r.settled() ? "folded" : "checked";
		}
		if (s.playMultiple > 0) {
			return "play";
		}
		if (r.pending(s)) {
			return "deciding";
		}
		return s.last == Decision.CHECK || s.decided || r.street() != UthRound.Street.PREFLOP ? "checked" : "";
	}

	private void writeBankState(CompoundTag tag, ServerPlayer viewer) {
		if (variant != Variant.PLAYER_BANKED) {
			return;
		}
		CompoundTag b = new CompoundTag();
		UthConfig c = cfg();
		b.putBoolean("enabled", c.pvp.enabled);
		b.putLong("min_bank", Math.max(c.pvp.minBank, pays().worstCase(Math.max(1, minBet()), 0)));
		b.putDouble("rake", c.pvp.rakePercent);
		b.putLong("ante_factor", pays().anteFactor());
		b.putLong("trips_factor", pays().tripsFactor());
		Banker bk = banker;
		boolean canTake = c.pvp.enabled && BETTING.equals(phase()) && confirmed.isEmpty()
			&& (bk == null || viewer.getUUID().equals(offeredTo));
		b.putBoolean("can_take", canTake && !bankerSeat(viewer.getUUID()));
		b.putBoolean("offered", viewer.getUUID().equals(offeredTo));
		b.putBoolean("house_rounds", c.pvp.houseRoundsWhenNoBanker);
		BotProfile stand = bk == null ? standInBot() : null;
		if (stand != null) {
			b.putString("stand_in", stand.nameId());
		}
		if (bk != null) {
			b.putString("name", bk.name);
			b.putLong("bank", bk.bank);
			b.putLong("reserved", bk.reserved);
			b.putBoolean("leaving", bk.leaving);
			b.putBoolean("you", bk.player.equals(viewer.getUUID()));
			b.putBoolean("this_round", pvpRound);
			if (bankResultShown) {
				b.putLong("last_result", lastBankResult);
				b.putLong("last_rake", lastRake);
			}
		}
		tag.put("bank", b);
	}

	/** For GameTests: the confirmed bet of a player in the betting phase. */
	public Optional<long[]> confirmedBet(UUID player) {
		Bet b = confirmed.get(player);
		return b == null ? Optional.empty() : Optional.of(new long[] {b.ante(), b.trips()});
	}
}
