package dev.nezo.burmaldaholic.games.baccarat;

import dev.nezo.burmaldaholic.Burmaldaholic;
import dev.nezo.burmaldaholic.core.CoreSounds;
import dev.nezo.burmaldaholic.core.config.CasinoConfig;
import dev.nezo.burmaldaholic.core.economy.Economies;
import dev.nezo.burmaldaholic.core.economy.Economy;
import dev.nezo.burmaldaholic.core.economy.Economy.Transaction;
import dev.nezo.burmaldaholic.core.events.CasinoEvents.PlayResult;
import dev.nezo.burmaldaholic.core.events.PlayResults;
import dev.nezo.burmaldaholic.core.mode.CasinoMode;
import dev.nezo.burmaldaholic.core.rng.CasinoRng;
import dev.nezo.burmaldaholic.core.rng.OddsService;
import dev.nezo.burmaldaholic.core.service.CoreServices;
import dev.nezo.burmaldaholic.core.service.TableOwnershipProvider.OwnedTable;
import dev.nezo.burmaldaholic.core.service.VipTiers;
import dev.nezo.burmaldaholic.core.table.CasinoTableBlockEntity;
import dev.nezo.burmaldaholic.core.table.CasinoTableMenu;
import dev.nezo.burmaldaholic.core.table.TableSeats;
import dev.nezo.burmaldaholic.core.table.TableType;
import dev.nezo.burmaldaholic.core.text.Texts;
import dev.nezo.burmaldaholic.core.util.Result;
import dev.nezo.burmaldaholic.core.wager.Stake;
import dev.nezo.burmaldaholic.core.wager.WagerVeto;
import dev.nezo.burmaldaholic.core.wager.Wagers;
import dev.nezo.burmaldaholic.games.baccarat.logic.BaccaratOdds;
import dev.nezo.burmaldaholic.games.baccarat.logic.BaccaratRules;
import dev.nezo.burmaldaholic.games.baccarat.logic.BaccaratRules.Coup;
import dev.nezo.burmaldaholic.games.baccarat.logic.BaccaratRules.Side;
import dev.nezo.burmaldaholic.games.baccarat.logic.BaccaratShoe;
import dev.nezo.burmaldaholic.games.baccarat.logic.BetKind;
import dev.nezo.burmaldaholic.games.baccarat.logic.Card;
import dev.nezo.burmaldaholic.games.baccarat.logic.ChemmyBank;
import dev.nezo.burmaldaholic.games.baccarat.logic.Paytable;
import dev.nezo.burmaldaholic.games.baccarat.logic.SeatDecider;
import dev.nezo.burmaldaholic.games.baccarat.logic.Slips;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiFunction;
import java.util.function.IntUnaryOperator;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * One baccarat table (GAME_DESIGN §20): standard, High Roller or Chemin de fer (player-banked, §20.9).
 * Server-authoritative shared coup: up to {@code baccarat.seats} seats bet on the same Player and Banker
 * hands from one persistent shoe.
 *
 * <p><b>House coups</b> (§20.5): BETTING (window of {@code betTimerTicks} from the first bet, or all bettors
 * Ready) → NO_MORE_BETS (20 t) → SHUFFLE (40 t, only when due) → the coup is drawn and saved → REVEAL
 * ({@code revealTicks}) → RESULT (60 t, everybody settled through the table stake API). Stakes are core
 * open stakes (one aggregate stake per bettor and coup, bankroll reservation = the bettor's worst case
 * over the 12 outcome classes).
 *
 * <p><b>Chemin de fer</b> (§20.9): BANK_OFFER → BETTING (punters against the bank, Banco) → … → RESULT
 * → BANK_OFFER. Bank and punts are escrowed in the world bank by this table (PvP: the house never pays,
 * it only takes the rake) and saved with it; anything found open on load is returned. Nobody banks → a
 * house coup on the same shoe.
 *
 * <p>Client actions: house {@code bet {box, amount}}, {@code unbet {box}}, {@code clear}, {@code rebet},
 * {@code ready}; chemin de fer {@code take_bank {amount}}, {@code keep_bank}, {@code pass}, {@code punt {amount}},
 * {@code banco}, {@code clear}, {@code ready}; core's {@code sit}/{@code leave}. Every seat decision can
 * also come from a {@link SeatDecider} (future bots) through the same code paths.
 */
public class BaccaratTableBlockEntity extends CasinoTableBlockEntity {
	public enum Variant { STANDARD, HIGH_ROLLER, CHEMMY }

	public static final String P_IDLE = "idle";
	public static final String P_BETTING = "betting";
	public static final String P_NO_MORE_BETS = "no_more_bets";
	public static final String P_SHUFFLE = "shuffle";
	public static final String P_REVEAL = "reveal";
	public static final String P_RESULT = "result";
	public static final String P_BANK_OFFER = "bank_offer";
	public static final String P_WAITING = "waiting";
	static final String T_BET = "bet";
	static final String T_PHASE = "phase";
	static final String T_OFFER = "offer";
	static final String T_IDLE = "idle";
	static final int NO_MORE_BETS_TICKS = 20;
	static final int SHUFFLE_TICKS = 40;
	static final int RESULT_TICKS = 60;
	private static final String STATE_KEY = "baccarat";
	private static final String CHIP_SHOWER_KEY = "burmaldaholic:chaos/trigger";

	private final Variant variant;
	private final BaccaratShoe shoe = new BaccaratShoe(8);
	private final List<Integer> beads = new ArrayList<>();
	private int tieRun;
	private long coupNo;
	private int burned;
	private String phase = P_IDLE;

	// ---- house coup ------------------------------------------------------------------------------
	private final Map<UUID, EnumMap<BetKind, Long>> bets = new LinkedHashMap<>();
	private final Set<UUID> ready = new HashSet<>();
	private final Map<UUID, String> names = new LinkedHashMap<>();
	private final Map<UUID, EnumMap<BetKind, Long>> lastSlips = new LinkedHashMap<>();
	private Set<UUID> lastTieWinners = new HashSet<>();
	/** The chemmy table plays a house coup this round (always true at house tables). */
	private boolean houseCoup = true;

	// ---- the drawn / shown coup -----------------------------------------------------------------
	private @Nullable Coup coup;
	/** The drawn coup has not been settled yet (REVEAL). */
	private boolean coupPending;
	private final Map<UUID, EnumMap<BetKind, Long>> coupSlips = new LinkedHashMap<>();
	private final Map<UUID, EnumMap<BetKind, Long>> coupReturns = new LinkedHashMap<>();
	private final Map<UUID, Long> coupPunts = new LinkedHashMap<>();
	private final Map<UUID, Long> coupPuntReturns = new LinkedHashMap<>();
	private @Nullable UUID coupBanker;
	private long coupBankDelta;
	private long coupRake;
	private boolean coupWasHouse = true;

	// ---- chemin de fer ---------------------------------------------------------------------------
	private final ChemmyBank<UUID> bank = new ChemmyBank<>();
	private int candidateSeat = -1;
	private @Nullable UUID candidate;
	private boolean offerKeep;
	private int passes;
	private int lastOfferSeat = -1;
	private long lastBank;
	private final Set<UUID> puntReady = new HashSet<>();
	/** Escrow found in the saved table (crash): returned on the first tick. */
	private final Map<UUID, Long> orphanPunts = new LinkedHashMap<>();
	private @Nullable UUID orphanBanker;
	private long orphanBank;
	private long orphanInvested;

	public BaccaratTableBlockEntity(TableType<BaccaratTableBlockEntity> type, BlockPos pos, BlockState state) {
		super(type, pos, state);
		this.variant = BaccaratModule.CHEMMY_NAME.equals(type.name()) ? Variant.CHEMMY
			: BaccaratModule.HIGH_ROLLER_NAME.equals(type.name()) ? Variant.HIGH_ROLLER : Variant.STANDARD;
	}

	private static BaccaratConfig cfg() {
		return BaccaratModule.config();
	}

	private static Paytable pay() {
		return BaccaratModule.paytable();
	}

	public Variant variant() {
		return variant;
	}

	public boolean isChemmy() {
		return variant == Variant.CHEMMY;
	}

	public boolean isHighRoller() {
		return variant == Variant.HIGH_ROLLER || preset().map(p -> p.id().startsWith("high_roller")).orElse(false);
	}

	@Override
	public String phase() {
		return phase;
	}

	private void phase(String next) {
		phase = next;
		setPhase(next);
		setChanged();
	}

	@Override
	protected int seatCount() {
		return cfg().seats;
	}

	@Override
	protected long minBet() {
		return cfg().minBet;
	}

	@Override
	protected double houseEdge() {
		return BaccaratOdds.of(cfg().decks).edge(BetKind.BANKER, pay());
	}

	/** Seat decider (humans wait for the screen; the bot framework will return a policy here). */
	protected SeatDecider deciderFor(UUID participant) {
		return SeatDecider.HUMAN;
	}

	// ---- limits ----------------------------------------------------------------------------------

	private long ownerMin() {
		return ownership().map(OwnedTable::minBet).filter(v -> v > 0).orElse(0L);
	}

	private long ownerMax() {
		return ownership().map(OwnedTable::maxBet).filter(v -> v > 0).orElse(0L);
	}

	/** Max total per coup for a player (§20.4): min(owner max, tier max) — High Roller: tier max × multiplier. */
	long maxFor(MinecraftServer server, UUID player) {
		long tierMax = CoreServices.vip().maxBet(server, player);
		long max = isHighRoller() ? (long) Math.floor(tierMax * cfg().highRollerMaxMultiplier) : tierMax;
		long owner = ownerMax();
		return owner > 0 ? Math.min(max, owner) : max;
	}

	Slips.Limits limits(ServerPlayer player) {
		BaccaratConfig c = cfg();
		long minTotal = Math.max(isHighRoller() ? c.highRollerMinTotal : 0, ownerMin());
		return Slips.Limits.of(c.minBet, maxFor(player.level().getServer(), player.getUUID()), c.sideMaxFraction,
			pay().bankerStep(), minTotal, c.pairBets);
	}

	@Override
	public long[] limitsFor(ServerPlayer player) {
		Slips.Limits l = limits(player);
		return new long[] {Math.max(l.minBet(), ownerMin()), l.totalMax()};
	}

	private boolean vipAllowed(ServerPlayer player) {
		if (!isHighRoller()) {
			return true;
		}
		int need = cfg().highRollerMinVipTier;
		int tier = CoreServices.vip().tier(player.level().getServer(), player.getUUID());
		if (tier >= need) {
			return true;
		}
		sendError(player, Component.translatable("gui.burmaldaholic.error.vip_required", VipTiers.name(need)));
		return false;
	}

	private boolean owing(MinecraftServer server, UUID player) {
		return CoreServices.debt().owed(server, player) > 0 || CoreServices.debt().inDefault(server, player);
	}

	// ---- seats -----------------------------------------------------------------------------------

	@Override
	public boolean sit(ServerPlayer player) {
		if (isSeated(player)) {
			return true;
		}
		if (!vipAllowed(player)) {
			return false;
		}
		boolean ok = super.sit(player);
		if (ok) {
			names.put(player.getUUID(), player.getName().getString());
			tableMessage(Component.translatable("msg.burmaldaholic.baccarat.player_joined", Texts.raw(player.getName().getString())), player.getUUID());
			if (isChemmy() && (P_IDLE.equals(phase) || P_WAITING.equals(phase)) && chemmyEnabled()) {
				startOffer(false);
			} else if (!isChemmy() && P_IDLE.equals(phase)) {
				phase(P_BETTING);
			}
		}
		return ok;
	}

	/** Bets ride or are cleared by the rules below: a player may always leave. */
	@Override
	protected boolean canLeaveNow(UUID player) {
		return true;
	}

	@Override
	protected void onPlayerLeft(UUID player, LeaveReason reason) {
		MinecraftServer server = server();
		String name = names.getOrDefault(player, "");
		if (server != null && reason != LeaveReason.REMOVED) {
			tableMessage(Component.translatable("msg.burmaldaholic.baccarat.player_left", Texts.raw(name)), player);
		}
		if (reason == LeaveReason.REMOVED) {
			return; // playOutForRemoval settles or returns everything
		}
		boolean walkedAway = reason == LeaveReason.LEFT || reason == LeaveReason.TOO_FAR;
		ready.remove(player);
		puntReady.remove(player);
		if (P_BETTING.equals(phase)) {
			if (houseCoup && walkedAway && bets.remove(player) != null) {
				refund(player, true);
				notify(player, Component.translatable("msg.burmaldaholic.baccarat.left_refunded"));
			}
			if (!houseCoup && walkedAway && bank.punts().containsKey(player)) {
				long back = bank.removePunt(player);
				giveBack(player, back, back, "chemmy_refund");
				notify(player, Component.translatable("msg.burmaldaholic.baccarat.left_refunded"));
			}
		}
		if (isChemmy() && !houseCoup && player.equals(bank.banker()) && (P_BANK_OFFER.equals(phase) || P_BETTING.equals(phase))) {
			// §20.9: banker leaves before the deal → punts refunded, bank returned, next seat offered
			refundPunts(false);
			returnBank(true);
			startOffer(false);
		} else if (isChemmy() && P_BANK_OFFER.equals(phase) && player.equals(candidate)) {
			decideBank(player, SeatDecider.BankDecision.pass());
		}
		if (P_BETTING.equals(phase)) {
			checkAllReady();
		}
		if (seats().isEmpty() && P_BETTING.equals(phase) && bets.isEmpty() && bank.punts().isEmpty() && !bank.held()) {
			cancelTimer(T_BET);
			cancelTimer(T_IDLE);
			phase(P_IDLE);
		}
		syncViewers();
	}

	// ---- actions ---------------------------------------------------------------------------------

	@Override
	public void onAction(ServerPlayer player, String action, CompoundTag args) {
		if (!cfg().enabled || (isChemmy() && !chemmyEnabled())) {
			sendError(player, Component.translatable("gui.burmaldaholic.error.disabled"));
			return;
		}
		switch (action) {
			case "bet" -> BetKind.byId(args.getStringOr("box", "")).ifPresentOrElse(
				k -> houseBet(player, k, args.getLongOr("amount", 0), true),
				() -> sendError(player, Component.translatable("gui.burmaldaholic.error.invalid_bet_position")));
			case "unbet" -> BetKind.byId(args.getStringOr("box", "")).ifPresent(k -> unbet(player, k));
			case "clear" -> clear(player);
			case "rebet" -> rebet(player);
			case "ready", "deal" -> markReady(player);
			case "take_bank" -> decideBank(player, new SeatDecider.BankDecision(SeatDecider.BankChoice.TAKE, args.getLongOr("amount", 0)));
			case "keep_bank" -> decideBank(player, new SeatDecider.BankDecision(SeatDecider.BankChoice.KEEP, 0));
			case "pass" -> decideBank(player, SeatDecider.BankDecision.pass());
			case "punt" -> punt(player, args.getLongOr("amount", 0));
			case "banco" -> banco(player);
			default -> { }
		}
		syncViewers();
	}

	private boolean houseBetting() {
		return P_BETTING.equals(phase) && houseCoup;
	}

	/** Adds {@code amount} to the player's box (Banker snapped down to the step, §20.1). */
	boolean houseBet(ServerPlayer player, BetKind kind, long amount, boolean tellSnap) {
		if (!isChemmy() && P_IDLE.equals(phase)) {
			phase(P_BETTING);
		}
		if (!houseBetting()) {
			sendError(player, Component.translatable("gui.burmaldaholic.baccarat.no_more_bets"));
			return false;
		}
		if (!isSeated(player) && !sit(player)) {
			return false;
		}
		if (!vipAllowed(player)) {
			return false;
		}
		UUID id = player.getUUID();
		EnumMap<BetKind, Long> slip = bets.computeIfAbsent(id, k -> new EnumMap<>(BetKind.class));
		Slips.Limits l = limits(player);
		long current = slip.getOrDefault(kind, 0L);
		if (kind == BetKind.BANKER && amount > 0) {
			long snapped = l.snapBanker(current + amount);
			if (snapped <= current) {
				dropIfEmpty(id);
				sendError(player, Component.translatable("gui.burmaldaholic.baccarat.error.banker_step", Texts.number(l.step())));
				return false;
			}
			if (snapped != current + amount && tellSnap) {
				sendError(player, Component.translatable("gui.burmaldaholic.baccarat.snapped", Texts.number(snapped)));
			}
			amount = snapped - current;
		}
		Optional<Slips.Violation> v = l.checkAdd(slip, kind, amount);
		if (v.isPresent()) {
			dropIfEmpty(id);
			sendError(player, message(v.get()));
			return false;
		}
		EnumMap<BetKind, Long> next = Slips.copy(slip);
		next.merge(kind, amount, Long::sum);
		long reserve = pay().worstCase(next) - pay().worstCase(slip);
		Result<Long> r = placeBet(player, amount, 1, 0, reserve, false);
		if (!r.isOk()) {
			dropIfEmpty(id);
			return false;
		}
		boolean firstAtTable = bets.values().stream().allMatch(Map::isEmpty) && ticksLeft(T_BET) < 0;
		slip.merge(kind, amount, Long::sum);
		names.put(id, player.getName().getString());
		ready.remove(id);
		if (firstAtTable) {
			startTimer(T_BET, cfg().betTimerTicks);
		}
		setChanged();
		return true;
	}

	private void dropIfEmpty(UUID id) {
		EnumMap<BetKind, Long> slip = bets.get(id);
		if (slip != null && slip.isEmpty()) {
			bets.remove(id);
		}
	}

	/** Removes one box: the slip is returned and the rest placed again (keeps the reservation exact). */
	private void unbet(ServerPlayer player, BetKind kind) {
		if (!houseBetting()) {
			return;
		}
		EnumMap<BetKind, Long> slip = bets.get(player.getUUID());
		if (slip == null || !slip.containsKey(kind)) {
			return;
		}
		EnumMap<BetKind, Long> rest = Slips.copy(slip);
		rest.remove(kind);
		clear(player);
		placeSlip(player, rest);
	}

	private void placeSlip(ServerPlayer player, Map<BetKind, Long> slip) {
		for (Map.Entry<BetKind, Long> e : slip.entrySet()) {
			if (!houseBet(player, e.getKey(), e.getValue(), false)) {
				break;
			}
		}
	}

	private void clear(ServerPlayer player) {
		UUID id = player.getUUID();
		if (P_BETTING.equals(phase) && !houseCoup) {
			if (bank.punts().containsKey(id) && bank.banco() == null) {
				long back = bank.removePunt(id);
				giveBack(id, back, back, "chemmy_refund");
				puntReady.remove(id);
				setChanged();
			}
			return;
		}
		if (!houseBetting()) {
			sendError(player, Component.translatable("gui.burmaldaholic.baccarat.no_more_bets"));
			return;
		}
		if (bets.remove(id) != null) {
			refund(id, true);
		}
		ready.remove(id);
		if (bets.isEmpty()) {
			cancelTimer(T_BET);
		}
		setChanged();
	}

	private void rebet(ServerPlayer player) {
		EnumMap<BetKind, Long> last = lastSlips.get(player.getUUID());
		if (last == null || last.isEmpty()) {
			sendError(player, Component.translatable("gui.burmaldaholic.baccarat.no_bets"));
			return;
		}
		if (!houseBetting()) {
			sendError(player, Component.translatable("gui.burmaldaholic.baccarat.no_more_bets"));
			return;
		}
		if (bets.containsKey(player.getUUID())) {
			clear(player);
		}
		placeSlip(player, limits(player).fit(last));
	}

	private void markReady(ServerPlayer player) {
		UUID id = player.getUUID();
		if (!P_BETTING.equals(phase)) {
			return;
		}
		if (houseCoup) {
			EnumMap<BetKind, Long> slip = bets.get(id);
			if (slip == null || slip.isEmpty()) {
				sendError(player, Component.translatable("gui.burmaldaholic.baccarat.no_bets"));
				return;
			}
			Optional<Slips.Violation> v = limits(player).checkSlip(slip);
			if (v.isPresent()) {
				sendError(player, message(v.get()));
				return;
			}
			ready.add(id);
		} else {
			if (!bank.punts().containsKey(id)) {
				sendError(player, Component.translatable("gui.burmaldaholic.baccarat.no_bets"));
				return;
			}
			puntReady.add(id);
		}
		checkAllReady();
	}

	private void checkAllReady() {
		if (!P_BETTING.equals(phase)) {
			return;
		}
		if (houseCoup) {
			if (!bets.isEmpty() && bets.keySet().stream().allMatch(ready::contains)) {
				closeBetting();
			}
		} else if (!bank.punts().isEmpty() && bank.punts().keySet().stream().allMatch(puntReady::contains)) {
			closeBetting();
		}
	}

	static Component message(Slips.Violation v) {
		return switch (v.code()) {
			case INVALID_AMOUNT -> Component.translatable("gui.burmaldaholic.error.invalid_amount");
			case BET_TOO_LOW -> Component.translatable("gui.burmaldaholic.error.bet_too_low", Texts.number(v.value()));
			case BANKER_STEP -> Component.translatable("gui.burmaldaholic.baccarat.error.banker_step", Texts.number(v.value()));
			case SIDE_MAX -> Component.translatable("gui.burmaldaholic.baccarat.error.side_max", Texts.number(v.value()));
			case TOTAL_MAX -> Component.translatable("gui.burmaldaholic.baccarat.error.total_max", Texts.number(v.value()));
			case MIN_TOTAL -> Component.translatable("gui.burmaldaholic.baccarat.error.min_total", Texts.number(v.value()));
			case PAIRS_OFF -> Component.translatable("gui.burmaldaholic.baccarat.error.pairs_off");
		};
	}

	// ---- chemin de fer: bank -----------------------------------------------------------------------

	private boolean chemmyEnabled() {
		return cfg().chemmy.enabled;
	}

	/** Opens BANK_OFFER: the winning banker may keep, else the next seat clockwise is asked. */
	private void startOffer(boolean keep) {
		startOffer(keep, 0);
	}

	/** @param passed seats that already passed in this rotation (all seats passed → house coup / wait) */
	private void startOffer(boolean keep, int passed) {
		passes = passed;
		cancelTimer(T_BET);
		cancelTimer(T_IDLE);
		puntReady.clear();
		houseCoup = false;
		if (seats().isEmpty() || !chemmyEnabled()) {
			if (bank.held()) {
				returnBank(true);
			}
			candidate = null;
			candidateSeat = -1;
			phase(P_IDLE);
			return;
		}
		if (keep && bank.held() && seats().isSeated(bank.banker())) {
			candidate = bank.banker();
			candidateSeat = seats().seatOf(candidate).orElse(-1);
			offerKeep = true;
		} else {
			if (bank.held()) {
				returnBank(true);
			}
			offerKeep = false;
			OptionalInt next = seats().nextOccupied(lastOfferSeat < 0 ? seats().size() - 1 : lastOfferSeat);
			if (next.isEmpty()) {
				phase(P_IDLE);
				return;
			}
			candidateSeat = next.getAsInt();
			candidate = seats().get(candidateSeat).player();
		}
		lastOfferSeat = candidateSeat;
		phase(P_BANK_OFFER);
		startTimer(T_OFFER, cfg().chemmy.bankOfferTicks);
		askBankDecider();
	}

	private void askBankDecider() {
		MinecraftServer server = server();
		if (candidate == null || server == null) {
			return;
		}
		long min = cfg().chemmy.minBank;
		long balance = Economies.get().balance(server, candidate);
		long def = Math.max(min, Math.min(balance, lastBank > 0 ? lastBank : min));
		deciderFor(candidate).bankOffer(new SeatDecider.BankOffer(offerKeep, bank.bank(), min, def, balance))
			.ifPresent(d -> decideBank(candidate, d));
	}

	private void decideBank(ServerPlayer player, SeatDecider.BankDecision d) {
		UUID id = player.getUUID();
		if (!isChemmy()) {
			return;
		}
		if (P_WAITING.equals(phase) && d.choice() == SeatDecider.BankChoice.TAKE && isSeated(player)) {
			candidate = id;
			candidateSeat = seats().seatOf(id).orElse(-1);
			offerKeep = false;
		}
		if (!(P_BANK_OFFER.equals(phase) || P_WAITING.equals(phase)) || !id.equals(candidate)) {
			sendError(player, Component.translatable("gui.burmaldaholic.error.not_your_turn"));
			return;
		}
		decideBank(id, d);
	}

	/** Applies a bank decision of the current candidate (screen action, timeout or a seat decider). */
	private void decideBank(UUID id, SeatDecider.BankDecision d) {
		MinecraftServer server = server();
		if (server == null || !id.equals(candidate)) {
			return;
		}
		ServerPlayer online = server.getPlayerList().getPlayer(id);
		switch (d.choice()) {
			case KEEP -> {
				if (!offerKeep || !id.equals(bank.banker())) {
					return;
				}
				if (bank.bank() < cfg().chemmy.minBank) {
					decideBank(id, SeatDecider.BankDecision.pass());
					return;
				}
				startChemmyBetting();
			}
			case TAKE -> {
				if (offerKeep || online == null) {
					return;
				}
				long amount = d.amount();
				long min = cfg().chemmy.minBank;
				Component err = null;
				if (owing(server, id)) {
					err = Component.translatable("gui.burmaldaholic.baccarat.error.pvp_owing");
				} else if (amount < min) {
					err = Component.translatable("gui.burmaldaholic.baccarat.chemmy.error.min_bank", Texts.number(min));
				} else {
					err = Wagers.check(online, new WagerVeto.Context(gameId(), Stake.Kind.CHIPS, worldPosition, true));
				}
				if (err == null && !escrow(online, amount, "chemmy_bank")) {
					err = Component.translatable("gui.burmaldaholic.error.insufficient_funds", Texts.number(Economies.get().balance(online)));
				}
				if (err != null) {
					sendError(online, err);
					return;
				}
				bank.take(id, amount);
				lastBank = amount;
				tableMessage(Component.translatable("msg.burmaldaholic.baccarat.chemmy.took_bank", Texts.raw(name(id)), Texts.chips(amount)), null);
				startChemmyBetting();
			}
			case PASS -> {
				cancelTimer(T_OFFER);
				if (offerKeep && id.equals(bank.banker())) {
					tableMessage(Component.translatable("msg.burmaldaholic.baccarat.chemmy.passed_bank", Texts.raw(name(id))), null);
					returnBank(true);
					offerKeep = false;
					passes = 1;
				} else {
					passes++;
				}
				if (passes >= seats().occupied().size()) {
					allPassed();
				} else {
					startOffer(false, passes);
				}
			}
		}
		setChanged();
	}

	/** Every seat passed (§20.9): a house coup, or the table waits for anyone to take the bank. */
	private void allPassed() {
		passes = 0;
		candidate = null;
		candidateSeat = -1;
		cancelTimer(T_OFFER);
		if (cfg().chemmy.houseCoupWhenNoBanker) {
			houseCoup = true;
			bets.clear();
			ready.clear();
			phase(P_BETTING);
			tableMessage(Component.translatable("gui.burmaldaholic.baccarat.chemmy.house_coup"), null);
			askHouseDeciders();
		} else {
			phase(P_WAITING);
		}
	}

	private void startChemmyBetting() {
		cancelTimer(T_OFFER);
		houseCoup = false;
		passes = 0;
		candidate = null;
		offerKeep = false;
		puntReady.clear();
		phase(P_BETTING);
		startTimer(T_IDLE, cfg().chemmy.idleTicks);
		askPuntDeciders();
	}

	private void askPuntDeciders() {
		MinecraftServer server = server();
		if (server == null || !bank.held()) {
			return;
		}
		long bankerMax = maxFor(server, bank.banker());
		for (TableSeats.Seat s : seats().occupied()) {
			if (s.player().equals(bank.banker())) {
				continue;
			}
			ServerPlayer p = server.getPlayerList().getPlayer(s.player());
			if (p == null) {
				continue;
			}
			long cov = bank.coverage(bankerMax);
			long max = maxFor(server, s.player());
			long balance = Economies.get().balance(p);
			Optional<SeatDecider.PuntDecision> d = deciderFor(s.player()).punt(new SeatDecider.PuntWindow(cov, bank.open(bankerMax),
				limitsFor(p)[0], max, balance, bank.banco() == null && balance >= cov && max >= cov));
			if (d.isPresent()) {
				if (d.get().banco()) {
					banco(p);
				} else if (d.get().amount() > 0) {
					punt(p, d.get().amount());
				}
				if (d.get().ready()) {
					markReady(p);
				}
			}
		}
	}

	private void askHouseDeciders() {
		MinecraftServer server = server();
		if (server == null) {
			return;
		}
		for (TableSeats.Seat s : seats().occupied()) {
			ServerPlayer p = server.getPlayerList().getPlayer(s.player());
			if (p == null) {
				continue;
			}
			Optional<SeatDecider.HouseDecision> d = deciderFor(s.player()).houseBets(new SeatDecider.HouseWindow(limits(p),
				Economies.get().balance(p), lastSlips.getOrDefault(s.player(), new EnumMap<>(BetKind.class)), pay()));
			if (d.isPresent()) {
				placeSlip(p, d.get().bets());
				if (d.get().ready()) {
					markReady(p);
				}
			}
		}
	}

	private void punt(ServerPlayer player, long amount) {
		UUID id = player.getUUID();
		MinecraftServer server = player.level().getServer();
		if (houseCoup || !P_BETTING.equals(phase) || !bank.held()) {
			sendError(player, Component.translatable("gui.burmaldaholic.baccarat.no_more_bets"));
			return;
		}
		if (!isSeated(player) && !sit(player)) {
			return;
		}
		if (owing(server, id)) {
			sendError(player, Component.translatable("gui.burmaldaholic.baccarat.error.pvp_owing"));
			return;
		}
		Component veto = Wagers.check(player, new WagerVeto.Context(gameId(), Stake.Kind.CHIPS, worldPosition, true));
		if (veto != null) {
			sendError(player, veto);
			return;
		}
		long bankerMax = maxFor(server, bank.banker());
		long min = limitsFor(player)[0];
		ChemmyBank.Punt p = bank.checkPunt(id, amount, min, maxFor(server, id), bankerMax);
		if (p.error().isPresent()) {
			sendError(player, switch (p.error().get()) {
				case INVALID -> Component.translatable("gui.burmaldaholic.error.invalid_amount");
				case TOO_LOW -> Component.translatable("gui.burmaldaholic.error.bet_too_low", Texts.number(p.limit()));
				case COVERAGE -> Component.translatable("gui.burmaldaholic.baccarat.chemmy.error.coverage", Texts.number(p.limit()));
				case OVER_MAX -> Component.translatable("gui.burmaldaholic.baccarat.error.total_max", Texts.number(p.limit()));
				case BANCO_CALLED, IS_BANKER -> Component.translatable("gui.burmaldaholic.baccarat.no_more_bets");
			});
			return;
		}
		if (!escrow(player, p.accepted(), "chemmy_punt")) {
			sendError(player, Component.translatable("gui.burmaldaholic.error.insufficient_funds", Texts.number(Economies.get().balance(player))));
			return;
		}
		if (p.accepted() < amount) {
			sendError(player, Component.translatable("gui.burmaldaholic.baccarat.chemmy.error.coverage", Texts.number(p.accepted())));
		}
		boolean first = bank.punts().isEmpty();
		bank.addPunt(id, p.accepted());
		names.put(id, player.getName().getString());
		puntReady.remove(id);
		if (first) {
			cancelTimer(T_IDLE);
			startTimer(T_BET, cfg().betTimerTicks);
		}
		playChip();
		setChanged();
	}

	private void banco(ServerPlayer player) {
		UUID id = player.getUUID();
		MinecraftServer server = player.level().getServer();
		if (houseCoup || !P_BETTING.equals(phase) || !bank.held() || id.equals(bank.banker())) {
			sendError(player, Component.translatable("gui.burmaldaholic.baccarat.no_more_bets"));
			return;
		}
		if (bank.banco() != null) {
			sendError(player, Component.translatable("gui.burmaldaholic.baccarat.no_more_bets"));
			return;
		}
		if (!isSeated(player) && !sit(player)) {
			return;
		}
		if (owing(server, id)) {
			sendError(player, Component.translatable("gui.burmaldaholic.baccarat.error.pvp_owing"));
			return;
		}
		Component veto = Wagers.check(player, new WagerVeto.Context(gameId(), Stake.Kind.CHIPS, worldPosition, true));
		if (veto != null) {
			sendError(player, veto);
			return;
		}
		long cov = bank.coverage(maxFor(server, bank.banker()));
		long own = bank.punts().getOrDefault(id, 0L);
		if (maxFor(server, id) < cov || Economies.get().balance(player) + own < cov || cov <= 0) {
			sendError(player, Component.translatable("gui.burmaldaholic.baccarat.chemmy.error.banco_funds", Texts.number(cov)));
			return;
		}
		// Refund every punt (the caller's own included), then escrow the whole coverage.
		Map<UUID, Long> others = bank.banco(id);
		others.forEach((who, amt) -> {
			giveBack(who, amt, amt, "chemmy_refund");
			if (!who.equals(id)) {
				notify(who, Component.translatable("gui.burmaldaholic.baccarat.chemmy.banco.tooltip"));
			}
		});
		if (!escrow(player, cov, "chemmy_banco")) {
			// balance changed in between: nobody plays; reopen betting
			sendError(player, Component.translatable("gui.burmaldaholic.baccarat.chemmy.error.banco_funds", Texts.number(cov)));
			bank.cancelBanco();
			return;
		}
		bank.addPunt(id, cov);
		names.put(id, player.getName().getString());
		tableMessage(Component.translatable("msg.burmaldaholic.baccarat.chemmy.banco_called", Texts.raw(player.getName().getString())), null);
		setChanged();
		closeBetting();
	}

	/** Returns the whole bank to the banker (online message, else a notice on the next join). */
	private void returnBank(boolean tell) {
		if (!bank.held()) {
			return;
		}
		UUID who = bank.banker();
		long invested = bank.invested();
		long amount = bank.close();
		if (amount > 0) {
			giveBack(who, amount, invested, "chemmy_bank");
			MinecraftServer server = server();
			if (tell && server != null) {
				ServerPlayer online = server.getPlayerList().getPlayer(who);
				if (online != null) {
					online.sendSystemMessage(Component.translatable("msg.burmaldaholic.baccarat.bank_returned", Texts.chips(amount)));
				} else {
					BaccaratData.get(server).queueBankReturned(who, amount);
				}
			}
		}
		setChanged();
	}

	private void refundPunts(boolean tableClosed) {
		for (Map.Entry<UUID, Long> e : new LinkedHashMap<>(bank.punts()).entrySet()) {
			bank.removePunt(e.getKey());
			giveBack(e.getKey(), e.getValue(), e.getValue(), "chemmy_refund");
			if (tableClosed) {
				notify(e.getKey(), Component.translatable("msg.burmaldaholic.baccarat.bets_refunded"));
			}
		}
		puntReady.clear();
	}

	// ---- chemin de fer: money ----------------------------------------------------------------------

	private boolean escrow(ServerPlayer player, long amount, String detail) {
		return amount > 0 && Economies.get().tryWithdraw(player, amount, new Transaction(gameId(), detail, Transaction.Kind.TRANSFER));
	}

	/**
	 * Pays {@code total} to a player (offline-safe): the part up to {@code principal} is their own escrowed
	 * money (non-garnishable transfer), the rest is a win (garnishable payout), like poker's cash-out.
	 */
	private void giveBack(UUID player, long total, long principal, String detail) {
		MinecraftServer server = server();
		if (server == null || total <= 0) {
			return;
		}
		Economy eco = Economies.get();
		long back = Math.min(total, Math.max(0, principal));
		if (back > 0) {
			eco.deposit(server, player, back, new Transaction(gameId(), detail, Transaction.Kind.TRANSFER));
		}
		if (total > back) {
			eco.deposit(server, player, total - back, Transaction.payout(gameId()));
		}
	}

	// ---- the coup ------------------------------------------------------------------------------------

	@Override
	protected void onTimer(String id) {
		switch (id) {
			case T_BET -> closeBetting();
			case T_PHASE -> advance();
			case T_OFFER -> {
				if (candidate != null) {
					decideBank(candidate, SeatDecider.BankDecision.pass());
				}
			}
			case T_IDLE -> {
				if (!houseCoup && P_BETTING.equals(phase) && bank.punts().isEmpty() && bank.held()) {
					// §20.9: no punter bet within idleTicks → the bank is passed
					tableMessage(Component.translatable("msg.burmaldaholic.baccarat.chemmy.passed_bank", Texts.raw(name(bank.banker()))), null);
					returnBank(true);
					startOffer(false);
				}
			}
			default -> { }
		}
		syncViewers();
	}

	private void closeBetting() {
		if (!P_BETTING.equals(phase)) {
			return;
		}
		cancelTimer(T_BET);
		cancelTimer(T_IDLE);
		MinecraftServer server = server();
		if (houseCoup) {
			// bettors whose slip is below the per-coup minimum (High Roller / owner) are dropped and refunded
			for (UUID id : List.copyOf(bets.keySet())) {
				ServerPlayer p = server == null ? null : server.getPlayerList().getPlayer(id);
				EnumMap<BetKind, Long> slip = bets.get(id);
				if (slip.isEmpty()) {
					bets.remove(id);
					continue;
				}
				long minTotal = Math.max(isHighRoller() ? cfg().highRollerMinTotal : 0, ownerMin());
				if (minTotal > 0 && Slips.total(slip) < minTotal) {
					bets.remove(id);
					refund(id, true);
					if (p != null) {
						sendError(p, Component.translatable("gui.burmaldaholic.baccarat.error.min_total", Texts.number(minTotal)));
					}
				}
			}
			if (bets.isEmpty()) {
				ready.clear();
				phase(P_BETTING);
				return;
			}
		} else if (bank.punts().isEmpty()) {
			startTimer(T_IDLE, cfg().chemmy.idleTicks);
			return;
		}
		phase(P_NO_MORE_BETS);
		startTimer(T_PHASE, NO_MORE_BETS_TICKS);
		tableMessage(Component.translatable("gui.burmaldaholic.baccarat.no_more_bets"), null, true);
	}

	private void advance() {
		switch (phase) {
			case P_NO_MORE_BETS -> {
				if (shoe.needsShuffle(cfg().penetration, cfg().decks)) {
					shuffle();
					phase(P_SHUFFLE);
					startTimer(T_PHASE, SHUFFLE_TICKS);
				} else {
					deal();
				}
			}
			case P_SHUFFLE -> deal();
			case P_REVEAL -> finishCoup();
			case P_RESULT -> nextRound();
			default -> { }
		}
	}

	private IntUnaryOperator rng() {
		CasinoRng fair = OddsService.get().fair();
		return fair::nextInt;
	}

	private void shuffle() {
		burned = shoe.shuffle(rng(), cfg().decks, cfg().burnCards);
		beads.clear();
		tieRun = 0;
		if (level != null && CoreSounds.CARD_SHUFFLE != null) {
			level.playSound(null, worldPosition, CoreSounds.CARD_SHUFFLE, SoundSource.BLOCKS, 0.8f, 1.0f);
		}
		tableMessage(Component.translatable("msg.burmaldaholic.baccarat.new_shoe"), null);
		setChanged();
	}

	/** DEAL (§20.5): the complete coup is drawn and persisted with every stake BEFORE the reveal. */
	private void deal() {
		IntUnaryOperator rng = rng();
		coup = BaccaratRules.deal(() -> shoe.draw(rng));
		coupPending = true;
		coupWasHouse = houseCoup;
		coupSlips.clear();
		coupReturns.clear();
		coupPunts.clear();
		coupPuntReturns.clear();
		coupBankDelta = 0;
		coupRake = 0;
		if (houseCoup) {
			bets.forEach((id, slip) -> coupSlips.put(id, Slips.copy(slip)));
			coupBanker = null;
		} else {
			coupPunts.putAll(bank.punts());
			coupBanker = bank.banker();
		}
		phase(P_REVEAL);
		startTimer(T_PHASE, cfg().revealTicks);
		if (level != null && CoreSounds.CARD_DEAL != null) {
			level.playSound(null, worldPosition, CoreSounds.CARD_DEAL, SoundSource.BLOCKS, 0.8f, 1.0f);
		}
		setChanged();
	}

	/** RESULT (§20.5 / §20.9): settles the drawn coup. */
	private void finishCoup() {
		if (coup == null || !coupPending) {
			return;
		}
		coupPending = false;
		coupNo++;
		if (coupWasHouse) {
			settleHouse(coup);
		} else {
			settleChemmy(coup);
		}
		Side winner = coup.winner();
		int bead = winner.ordinal() | (coup.playerPair() ? 4 : 0) | (coup.bankerPair() ? 8 : 0);
		beads.add(bead);
		int max = Math.max(0, cfg().historyLength);
		while (beads.size() > max) {
			beads.removeFirst();
		}
		spectatorSummary();
		phase(P_RESULT);
		startTimer(T_PHASE, RESULT_TICKS);
		setChanged();
	}

	private void settleHouse(Coup c) {
		MinecraftServer server = server();
		Paytable pay = pay();
		BaccaratOdds.Counts odds = BaccaratOdds.of(shoe.decks());
		Side winner = c.winner();
		tieRun = winner == Side.TIE ? tieRun + 1 : 0;
		Set<UUID> tieWinners = new HashSet<>();
		for (Map.Entry<UUID, EnumMap<BetKind, Long>> e : coupSlips.entrySet()) {
			UUID id = e.getKey();
			EnumMap<BetKind, Long> slip = e.getValue();
			EnumMap<BetKind, Long> rets = new EnumMap<>(BetKind.class);
			long total = 0;
			double edgeWeighted = 0;
			long staked = 0;
			for (Map.Entry<BetKind, Long> b : slip.entrySet()) {
				long r = pay.returnOf(b.getKey(), b.getValue(), c);
				rets.put(b.getKey(), r);
				total += r;
				staked += b.getValue();
				edgeWeighted += b.getValue() * Math.max(0, odds.edge(b.getKey(), pay));
			}
			coupReturns.put(id, rets);
			double edge = staked > 0 ? edgeWeighted / staked : 0;
			List<String> tags = new ArrayList<>();
			slip.keySet().forEach(k -> tags.add(k.id()));
			boolean naturalWin = c.winnerNaturalNine() && ((winner == Side.PLAYER && slip.containsKey(BetKind.PLAYER))
				|| (winner == Side.BANKER && slip.containsKey(BetKind.BANKER)));
			if (naturalWin) {
				tags.add("natural");
			}
			String[] tagArray = tags.toArray(String[]::new);
			settle(id, total, r -> r.withEdge(edge).withTags(tagArray));
			lastSlips.put(id, Slips.copy(slip));
			if (winner == Side.TIE && slip.containsKey(BetKind.TIE)) {
				tieWinners.add(id);
			}
			if (server == null) {
				continue;
			}
			if (naturalWin) {
				notify(id, Component.translatable("msg.burmaldaholic.baccarat.natural_nine").withStyle(ChatFormatting.GOLD));
				BaccaratAdvancements.grant(server, id, "baccarat_natural");
			}
			if (tieWinners.contains(id) && lastTieWinners.contains(id)) {
				BaccaratAdvancements.grant(server, id, "tie_streak");
			}
			ServerPlayer p = server.getPlayerList().getPlayer(id);
			if (p != null) {
				p.sendOverlayMessage(netLine(total - staked));
			}
		}
		lastTieWinners = tieWinners;
		int chaos = cfg().tieStreakChaos;
		if (winner == Side.TIE && chaos > 0 && tieRun >= chaos && server != null) {
			tableMessage(Texts.plural("msg.burmaldaholic.baccarat.tie_run", tieRun).withStyle(ChatFormatting.GREEN), null);
			for (UUID id : tieWinners) {
				ServerPlayer p = server.getPlayerList().getPlayer(id);
				if (p != null) {
					chipShower(p);
				}
			}
		}
		bets.clear();
		ready.clear();
	}

	private void settleChemmy(Coup c) {
		MinecraftServer server = server();
		if (server == null) {
			return;
		}
		Side winner = c.winner();
		UUID banker = bank.banker();
		UUID bancoCaller = bank.banco();
		long matched = bank.punted();
		int rakeBp = Paytable.basisPoints(cfg().chemmy.rakePercent);
		ChemmyBank.Settlement<UUID> s = bank.settle(winner, rakeBp);
		String bankroll = ownership().map(OwnedTable::bankrollId).orElse("");
		ServerLevel sl = (ServerLevel) level;
		for (Map.Entry<UUID, Long> e : coupPunts.entrySet()) {
			UUID id = e.getKey();
			long stake = e.getValue();
			long ret = s.punterReturns().getOrDefault(id, 0L);
			coupPuntReturns.put(id, ret);
			giveBack(id, ret, stake, "chemmy_stake_return");
			List<String> tags = new ArrayList<>(List.of("chemmy", "player"));
			if (id.equals(bancoCaller)) {
				tags.add("banco");
			}
			boolean naturalWin = winner == Side.PLAYER && c.winnerNaturalNine();
			if (naturalWin) {
				tags.add("natural");
			}
			PlayResults.fire(server, id, PlayResult.of(gameId(), stake, ret).pvp().withTags(tags.toArray(String[]::new))
				.withTable(sl, worldPosition, bankroll));
			if (naturalWin) {
				notify(id, Component.translatable("msg.burmaldaholic.baccarat.natural_nine").withStyle(ChatFormatting.GOLD));
				BaccaratAdvancements.grant(server, id, "baccarat_natural");
			}
			if (id.equals(bancoCaller) && winner == Side.PLAYER) {
				BaccaratAdvancements.grant(server, id, "banco");
			}
			ServerPlayer p = server.getPlayerList().getPlayer(id);
			if (p != null) {
				p.sendOverlayMessage(netLine(ret - stake));
			}
		}
		coupBankDelta = s.bankDelta();
		coupRake = s.rake();
		if (banker != null && matched > 0) {
			long bankerReturn = matched + s.bankDelta();
			boolean naturalWin = winner == Side.BANKER && c.winnerNaturalNine();
			PlayResults.fire(server, banker, PlayResult.of(gameId(), matched, bankerReturn).pvp()
				.withTags(naturalWin ? new String[] {"chemmy", "bank", "natural"} : new String[] {"chemmy", "bank"}).withTable(sl, worldPosition, bankroll));
			if (naturalWin) {
				notify(banker, Component.translatable("msg.burmaldaholic.baccarat.natural_nine").withStyle(ChatFormatting.GOLD));
				BaccaratAdvancements.grant(server, banker, "baccarat_natural");
			}
			if (winner == Side.BANKER && bank.wins() >= 5) {
				BaccaratAdvancements.grant(server, banker, "bank_holder");
			}
		}
		collectRake(s.rake());
		tieRun = winner == Side.TIE ? tieRun + 1 : 0;
		lastTieWinners = new HashSet<>();
		if (winner == Side.BANKER) {
			tableMessage(Component.translatable("msg.burmaldaholic.baccarat.chemmy.bank_wins", Texts.chips(s.bankerWin()), Texts.chips(s.rake())), null);
		} else if (winner == Side.PLAYER) {
			tableMessage(Component.translatable("msg.burmaldaholic.baccarat.chemmy.bank_pays", Texts.chips(s.bankLoss())), null);
		}
		puntReady.clear();
	}

	private void nextRound() {
		coupPending = false;
		if (!isChemmy()) {
			phase(seats().isEmpty() ? P_IDLE : P_BETTING);
			if (P_BETTING.equals(phase)) {
				askHouseDeciders();
			}
			return;
		}
		// Chemin de fer rotation (§20.9)
		if (!coupWasHouse && bank.held()) {
			UUID banker = bank.banker();
			boolean lost = coup != null && coup.winner() == Side.PLAYER;
			boolean stays = seats().isSeated(banker) && !lost && bank.bank() >= cfg().chemmy.minBank && chemmyEnabled();
			if (stays) {
				startOffer(true);
				return;
			}
			returnBank(true);
		}
		startOffer(false);
	}

	// ---- chaos, messages -------------------------------------------------------------------------

	@SuppressWarnings("unchecked")
	private static void chipShower(ServerPlayer player) {
		Object fn = FabricLoader.getInstance().getObjectShare().get(CHIP_SHOWER_KEY);
		if (fn instanceof BiFunction<?, ?, ?>) {
			try {
				((BiFunction<ServerPlayer, String, Object>) fn).apply(player, "chip_shower@baccarat");
			} catch (RuntimeException e) {
				Burmaldaholic.LOGGER.error("baccarat chip shower failed", e);
			}
		}
	}

	private static Component netLine(long net) {
		return net > 0 ? Component.translatable("gui.burmaldaholic.common.result.win", Texts.chips(net)).withStyle(ChatFormatting.GREEN)
			: net < 0 ? Component.translatable("gui.burmaldaholic.common.result.loss", Texts.chips(-net))
			: Component.translatable("gui.burmaldaholic.common.result.push");
	}

	private @Nullable MinecraftServer server() {
		return level instanceof ServerLevel sl ? sl.getServer() : null;
	}

	private String name(@Nullable UUID id) {
		if (id == null) {
			return "";
		}
		String n = names.get(id);
		if (n != null) {
			return n;
		}
		MinecraftServer server = server();
		ServerPlayer p = server == null ? null : server.getPlayerList().getPlayer(id);
		return p == null ? "" : p.getName().getString();
	}

	private void notify(UUID player, Component message) {
		MinecraftServer server = server();
		ServerPlayer p = server == null ? null : server.getPlayerList().getPlayer(player);
		if (p != null) {
			p.sendSystemMessage(message);
		}
	}

	/** Players who see this table: seated (online) and everybody within {@code multiplayer.spectatorRadius}. */
	private List<ServerPlayer> audience() {
		List<ServerPlayer> out = new ArrayList<>();
		if (!(level instanceof ServerLevel sl)) {
			return out;
		}
		double r = CasinoConfig.multiplayer().spectatorRadius;
		Vec3 center = Vec3.atCenterOf(worldPosition);
		for (ServerPlayer p : sl.players()) {
			if (isSeated(p) || p.distanceToSqr(center) <= r * r) {
				out.add(p);
			}
		}
		return out;
	}

	private void tableMessage(Component message, @Nullable UUID except) {
		tableMessage(message, except, false);
	}

	private void tableMessage(Component message, @Nullable UUID except, boolean overlay) {
		for (ServerPlayer p : audience()) {
			if (!p.getUUID().equals(except)) {
				if (overlay) {
					p.sendOverlayMessage(message);
				} else {
					p.sendSystemMessage(message);
				}
			}
		}
	}

	/** Players around the table without its screen open get the action-bar summary (§20.5). */
	private void spectatorSummary() {
		if (coup == null) {
			return;
		}
		Component line = Component.translatable("gui.burmaldaholic.baccarat.actionbar", handText(coup.player()), handText(coup.banker()));
		for (ServerPlayer p : audience()) {
			if (!(p.containerMenu instanceof CasinoTableMenu menu && menu.pos().equals(worldPosition))) {
				p.sendOverlayMessage(line);
			}
		}
	}

	private static Component handText(List<Card> cards) {
		StringBuilder sb = new StringBuilder();
		for (Card c : cards) {
			sb.append('[').append(c.rankLabel()).append(c.suitSymbol()).append(']');
		}
		sb.append(' ').append(BaccaratRules.total(cards));
		return Texts.raw(sb.toString());
	}

	private void playChip() {
		if (level != null && CoreSounds.CHIP_PLACE != null) {
			level.playSound(null, worldPosition, CoreSounds.CHIP_PLACE, SoundSource.BLOCKS, 0.6f, 1.2f);
		}
	}

	// ---- ticking, casino mode, play-out ----------------------------------------------------------

	@Override
	protected void serverTick(ServerLevel level) {
		if (!orphanPunts.isEmpty() || orphanBanker != null) {
			returnOrphans();
		}
		if (!CasinoMode.isEnabled(level) && hasRoundInPlay()) {
			playOutNow("casino mode off"); // drawn coups settle, undrawn bets and the bank go back (§20.5)
			syncViewers();
		}
	}

	/** An escrow found in the saved table (after a crash) is always returned (§20.9). */
	private void returnOrphans() {
		MinecraftServer server = server();
		if (server == null) {
			return;
		}
		orphanPunts.forEach((id, amt) -> {
			Burmaldaholic.LOGGER.info("Baccarat table {}: returning {} chips of an unfinished chemin de fer coup to {}", worldPosition, amt, id);
			giveBack(id, amt, amt, "chemmy_refund");
		});
		orphanPunts.clear();
		if (orphanBanker != null && orphanBank > 0) {
			Burmaldaholic.LOGGER.info("Baccarat table {}: returning a bank of {} chips to {}", worldPosition, orphanBank, orphanBanker);
			giveBack(orphanBanker, orphanBank, orphanInvested, "chemmy_bank");
			ServerPlayer online = server.getPlayerList().getPlayer(orphanBanker);
			if (online != null) {
				online.sendSystemMessage(Component.translatable("msg.burmaldaholic.baccarat.bank_returned", Texts.chips(orphanBank)));
			} else {
				BaccaratData.get(server).queueBankReturned(orphanBanker, orphanBank);
			}
		}
		orphanBanker = null;
		orphanBank = 0;
		setChanged();
	}

	@Override
	protected boolean hasRoundInPlay() {
		return bank.held() || !bank.punts().isEmpty() || !bets.isEmpty() || coupPending;
	}

	/**
	 * Table break / chunk unload / server stop / casino mode off (§20.5, §20.9): a drawn coup is settled at
	 * its stored cards; undrawn bets are returned ({@code msg.burmaldaholic.baccarat.bets_refunded}); a
	 * chemin de fer bank goes back to its holder.
	 */
	@Override
	protected void playOutForRemoval(ServerLevel level) {
		cancelTimer(T_BET);
		cancelTimer(T_PHASE);
		cancelTimer(T_OFFER);
		cancelTimer(T_IDLE);
		if (coupPending) {
			finishCoup();
		} else {
			for (UUID id : List.copyOf(bets.keySet())) {
				refund(id, true);
				notify(id, Component.translatable("msg.burmaldaholic.baccarat.bets_refunded"));
			}
			bets.clear();
			refundPunts(true);
		}
		returnBank(true);
		ready.clear();
		puntReady.clear();
		candidate = null;
		candidateSeat = -1;
		houseCoup = !isChemmy();
		phase(P_IDLE);
	}

	// ---- tests ----------------------------------------------------------------------------------

	/** GameTests: the next cards dealt (in deal order), before the shoe continues. */
	public void stackCardsForTests(List<Card> next) {
		if (shoe.size() == 0) {
			shoe.shuffle(rng(), cfg().decks, false);
		}
		shoe.stack(next);
	}

	/** GameTests: runs the current phase's pending timer now. */
	public void fastForwardForTests() {
		for (String t : List.of(T_BET, T_IDLE, T_OFFER, T_PHASE)) {
			if (ticksLeft(t) >= 0) {
				cancelTimer(t);
				onTimer(t);
				return;
			}
		}
	}

	public @Nullable Coup lastCoup() {
		return coup;
	}

	public ChemmyBank<UUID> bank() {
		return bank;
	}

	public Map<BetKind, Long> betsOf(UUID player) {
		return bets.getOrDefault(player, new EnumMap<>(BetKind.class));
	}

	public List<Integer> beads() {
		return List.copyOf(beads);
	}

	public int shoeRemaining() {
		return shoe.remaining();
	}

	// ---- client state ----------------------------------------------------------------------------

	@Override
	public CompoundTag writeClientState(ServerPlayer viewer) {
		CompoundTag t = baseState(viewer);
		UUID me = viewer.getUUID();
		BaccaratConfig c = cfg();
		Paytable pay = pay();
		Slips.Limits l = limits(viewer);
		t.putString("phase", phase);
		t.putString("variant", variant.name().toLowerCase(java.util.Locale.ROOT));
		t.putBoolean("high_roller", isHighRoller());
		t.putBoolean("enabled", c.enabled && (!isChemmy() || chemmyEnabled()));
		t.putBoolean("house_coup", houseCoup);
		t.putLong("coup_no", coupNo + 1);
		t.putInt("shoe_left", shoe.size() == 0 ? c.decks * 52 : shoe.remaining());
		t.putInt("decks", c.decks);
		t.putInt("burned", burned);
		t.putInt("commission_bp", pay.commissionBp());
		t.putInt("tie_pays", pay.tiePays());
		t.putInt("pair_pays", pay.pairPays());
		t.putBoolean("pairs", c.pairBets);
		t.putLong("step", l.step());
		t.putLong("banker_min", l.bankerMin());
		t.putLong("bet_min", l.minBet());
		t.putLong("side_max", l.sideMax());
		t.putLong("min_total", l.minTotal());
		t.putInt("reveal_total", c.revealTicks);
		t.putIntArray("beads", beads.stream().mapToInt(Integer::intValue).toArray());
		t.putInt("tie_run", tieRun);
		// the shown coup (during REVEAL the client animates the cards from reveal_left)
		if (coup != null) {
			t.putIntArray("player_cards", coup.playerCodes());
			t.putIntArray("banker_cards", coup.bankerCodes());
			t.putLong("reveal_left", P_REVEAL.equals(phase) ? Math.max(0, ticksLeft(T_PHASE)) : 0);
			EnumMap<BetKind, Long> myRets = coupReturns.get(me);
			EnumMap<BetKind, Long> mySlip = coupSlips.get(me);
			if (myRets != null && mySlip != null && !P_REVEAL.equals(phase)) {
				t.put("my_result", slipTag(mySlip, myRets, pay));
			}
			if (coupPuntReturns.containsKey(me) && !P_REVEAL.equals(phase)) {
				t.putLong("my_punt", coupPunts.getOrDefault(me, 0L));
				t.putLong("my_punt_return", coupPuntReturns.get(me));
			}
			if (me.equals(coupBanker) && !P_REVEAL.equals(phase) && !coupWasHouse) {
				t.putLong("my_bank_delta", coupBankDelta);
				t.putLong("my_bank_rake", coupRake);
			}
		}
		// my slip and the table's boxes
		EnumMap<BetKind, Long> mine = bets.getOrDefault(me, new EnumMap<>(BetKind.class));
		t.put("slip", slipTag(mine, null, pay));
		EnumMap<BetKind, Long> others = new EnumMap<>(BetKind.class);
		bets.forEach((id, slip) -> {
			if (!id.equals(me)) {
				slip.forEach((k, v) -> others.merge(k, v, Long::sum));
			}
		});
		t.put("others", slipTag(others, null, pay));
		t.putBoolean("ready", ready.contains(me) || puntReady.contains(me));
		t.putInt("ready_count", houseCoup ? (int) bets.keySet().stream().filter(ready::contains).count() : puntReady.size());
		t.putInt("bettors", houseCoup ? bets.size() : bank.punts().size());
		t.putBoolean("can_rebet", houseBetting() && lastSlips.containsKey(me));
		boolean inCoup = coupPending && (coupSlips.containsKey(me) || coupPunts.containsKey(me) || me.equals(coupBanker));
		t.putBoolean("waiting_next", isSeated(viewer) && !P_BETTING.equals(phase) && !P_BANK_OFFER.equals(phase)
			&& !P_RESULT.equals(phase) && !inCoup && !me.equals(bank.banker()));
		// seats: name, total at stake, ready
		ListTag seatList = new ListTag();
		for (TableSeats.Seat s : seats().occupied()) {
			CompoundTag st = new CompoundTag();
			st.putInt("index", s.index());
			st.putString("name", s.name());
			st.putBoolean("you", s.player().equals(me));
			long stake = houseCoup ? Slips.total(bets.getOrDefault(s.player(), new EnumMap<>(BetKind.class))) : bank.punts().getOrDefault(s.player(), 0L);
			st.putLong("stake", stake);
			st.putBoolean("ready", ready.contains(s.player()) || puntReady.contains(s.player()));
			st.putBoolean("banker", s.player().equals(bank.banker()));
			seatList.add(st);
		}
		t.put("seat_list", seatList);
		if (isChemmy()) {
			MinecraftServer server = viewer.level().getServer();
			CompoundTag ch = new CompoundTag();
			ch.putBoolean("held", bank.held());
			ch.putString("banker", name(bank.banker()));
			ch.putBoolean("you_bank", me.equals(bank.banker()));
			ch.putLong("bank", bank.bank());
			long cov = bank.held() ? bank.coverage(maxFor(server, bank.banker())) : 0;
			ch.putLong("coverage", cov);
			ch.putLong("open", bank.held() ? bank.open(maxFor(server, bank.banker())) : 0);
			ch.putString("banco", name(bank.banco()));
			ch.putLong("my_punt", bank.punts().getOrDefault(me, 0L));
			ch.putString("candidate", name(candidate));
			ch.putBoolean("offer_you", me.equals(candidate) && P_BANK_OFFER.equals(phase));
			ch.putBoolean("offer_keep", offerKeep);
			ch.putLong("min_bank", c.chemmy.minBank);
			ch.putLong("default_bank", Math.max(c.chemmy.minBank, Math.min(Economies.get().balance(viewer), lastBank > 0 ? lastBank : c.chemmy.minBank)));
			ch.putInt("rake_bp", Paytable.basisPoints(c.chemmy.rakePercent));
			ch.putLong("my_max", maxFor(server, me));
			ListTag punts = new ListTag();
			bank.punts().forEach((id, amt) -> {
				CompoundTag pt = new CompoundTag();
				pt.putString("name", name(id));
				pt.putLong("amount", amt);
				pt.putBoolean("you", id.equals(me));
				punts.add(pt);
			});
			ch.put("punts", punts);
			t.put("chemmy", ch);
		}
		return t;
	}

	private static CompoundTag slipTag(Map<BetKind, Long> slip, @Nullable Map<BetKind, Long> returns, Paytable pay) {
		CompoundTag tag = new CompoundTag();
		slip.forEach((k, v) -> tag.putLong(k.id(), v));
		if (returns != null) {
			CompoundTag r = new CompoundTag();
			returns.forEach((k, v) -> r.putLong(k.id(), v));
			tag.put("returns", r);
			long commission = 0;
			Long banker = slip.get(BetKind.BANKER);
			Long bankerRet = returns.get(BetKind.BANKER);
			if (banker != null && bankerRet != null && bankerRet > banker) {
				commission = pay.commission(banker);
			}
			tag.putLong("commission", commission);
		}
		return tag;
	}

	// ---- persistence -----------------------------------------------------------------------------

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		CompoundTag t = new CompoundTag();
		if (shoe.size() > 0) {
			t.putIntArray("shoe", shoe.codes());
			t.putInt("shoe_pos", shoe.dealt());
			t.putInt("shoe_decks", shoe.decks());
		}
		t.putIntArray("beads", beads.stream().mapToInt(Integer::intValue).toArray());
		t.putInt("tie_run", tieRun);
		t.putLong("coup_no", coupNo);
		t.putLong("last_bank", lastBank);
		t.putInt("last_offer_seat", lastOfferSeat);
		if (coup != null) {
			t.putIntArray("coup_player", coup.playerCodes());
			t.putIntArray("coup_banker", coup.bankerCodes());
		}
		// chemin de fer escrow: always returned on load (crash) — a clean stop plays the coup out first
		CompoundTag esc = new CompoundTag();
		bank.punts().forEach((id, v) -> esc.putLong(id.toString(), v));
		orphanPunts.forEach((id, v) -> esc.putLong(id.toString(), esc.getLongOr(id.toString(), 0) + v));
		t.put("punts", esc);
		UUID banker = bank.held() ? bank.banker() : orphanBanker;
		if (banker != null) {
			t.putString("banker", banker.toString());
			t.putLong("bank", bank.held() ? bank.bank() : orphanBank);
			t.putLong("bank_invested", bank.held() ? bank.invested() : orphanInvested);
		}
		output.store(STATE_KEY, CompoundTag.CODEC, t);
	}

	@Override
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		CompoundTag t = input.read(STATE_KEY, CompoundTag.CODEC).orElseGet(CompoundTag::new);
		int[] codes = t.getIntArray("shoe").orElse(new int[0]);
		shoe.restore(codes, t.getIntOr("shoe_pos", 0), t.getIntOr("shoe_decks", 8));
		beads.clear();
		for (int b : t.getIntArray("beads").orElse(new int[0])) {
			beads.add(b);
		}
		tieRun = t.getIntOr("tie_run", 0);
		coupNo = t.getLongOr("coup_no", 0);
		lastBank = t.getLongOr("last_bank", 0);
		lastOfferSeat = t.getIntOr("last_offer_seat", -1);
		int[] cp = t.getIntArray("coup_player").orElse(new int[0]);
		int[] cb = t.getIntArray("coup_banker").orElse(new int[0]);
		try {
			coup = cp.length > 0 ? Coup.fromCodes(cp, cb) : null;
		} catch (IllegalArgumentException e) {
			coup = null;
		}
		orphanPunts.clear();
		CompoundTag esc = t.getCompoundOrEmpty("punts");
		for (String key : esc.keySet()) {
			try {
				long v = esc.getLongOr(key, 0);
				if (v > 0) {
					orphanPunts.put(UUID.fromString(key), v);
				}
			} catch (IllegalArgumentException ignored) {
				// not a player id
			}
		}
		orphanBanker = null;
		orphanBank = 0;
		String banker = t.getStringOr("banker", "");
		if (!banker.isEmpty()) {
			try {
				orphanBanker = UUID.fromString(banker);
				orphanBank = t.getLongOr("bank", 0);
				orphanInvested = t.getLongOr("bank_invested", 0);
			} catch (IllegalArgumentException ignored) {
				orphanBanker = null;
			}
		}
		// Runtime round state never survives a reload: core refunds open house stakes after a crash.
		bets.clear();
		ready.clear();
		coupPending = false;
		phase = P_IDLE;
	}
}
