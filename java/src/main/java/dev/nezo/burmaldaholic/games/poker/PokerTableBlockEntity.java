package dev.nezo.burmaldaholic.games.poker;

import com.mojang.serialization.Codec;
import dev.nezo.burmaldaholic.Burmaldaholic;
import dev.nezo.burmaldaholic.core.advancement.CasinoAdvancements;
import dev.nezo.burmaldaholic.core.bots.BotJobs;
import dev.nezo.burmaldaholic.core.bots.BotLedger;
import dev.nezo.burmaldaholic.core.bots.BotRounds;
import dev.nezo.burmaldaholic.core.bots.BotTable;
import dev.nezo.burmaldaholic.core.bots.TableBots;
import dev.nezo.burmaldaholic.core.bots.logic.BotDifficulty;
import dev.nezo.burmaldaholic.core.bots.logic.BotProfile;
import dev.nezo.burmaldaholic.core.bots.logic.BotRole;
import dev.nezo.burmaldaholic.core.bots.logic.BotRoster;
import dev.nezo.burmaldaholic.core.bots.logic.BotSettings;
import dev.nezo.burmaldaholic.core.bots.logic.BotWork;
import dev.nezo.burmaldaholic.core.bots.logic.OwnerControls;
import dev.nezo.burmaldaholic.core.bots.logic.SeatOccupant;
import dev.nezo.burmaldaholic.core.bots.logic.SeatPolicy;
import dev.nezo.burmaldaholic.core.bots.logic.SeatingMath;
import dev.nezo.burmaldaholic.core.config.CasinoConfig;
import dev.nezo.burmaldaholic.core.config.sections.PokerConfig;
import dev.nezo.burmaldaholic.core.economy.AccountId;
import dev.nezo.burmaldaholic.core.economy.Economies;
import dev.nezo.burmaldaholic.core.economy.Economy;
import dev.nezo.burmaldaholic.core.economy.Economy.Transaction;
import dev.nezo.burmaldaholic.core.events.CasinoEvents;
import dev.nezo.burmaldaholic.core.events.PlayResults;
import dev.nezo.burmaldaholic.core.mode.CasinoMode;
import dev.nezo.burmaldaholic.core.rng.CasinoRng;
import dev.nezo.burmaldaholic.core.rng.OddsService;
import dev.nezo.burmaldaholic.core.service.CoreServices;
import dev.nezo.burmaldaholic.core.service.TableOwnershipProvider.OwnedTable;
import dev.nezo.burmaldaholic.core.service.TablePresetProvider;
import dev.nezo.burmaldaholic.core.service.VipTiers;
import dev.nezo.burmaldaholic.core.table.CasinoTableBlockEntity;
import dev.nezo.burmaldaholic.core.table.TableType;
import dev.nezo.burmaldaholic.core.text.Texts;
import dev.nezo.burmaldaholic.core.util.Result;
import dev.nezo.burmaldaholic.core.wager.Stake;
import dev.nezo.burmaldaholic.core.wager.WagerVeto;
import dev.nezo.burmaldaholic.core.wager.Wagers;
import dev.nezo.burmaldaholic.core.anim.SeedMix;
import dev.nezo.burmaldaholic.core.anim.Timeline;
import dev.nezo.burmaldaholic.games.poker.logic.BestFive;
import dev.nezo.burmaldaholic.games.poker.logic.Hand;
import dev.nezo.burmaldaholic.games.poker.logic.PokerBeats;
import dev.nezo.burmaldaholic.games.poker.present.PokerPub;
import dev.nezo.burmaldaholic.games.poker.logic.HandEvaluator;
import dev.nezo.burmaldaholic.games.poker.logic.PokerBotPolicy;
import dev.nezo.burmaldaholic.games.poker.logic.PokerMoney;
import dev.nezo.burmaldaholic.games.poker.logic.PokerRng;
import dev.nezo.burmaldaholic.games.poker.logic.PokerTable;
import dev.nezo.burmaldaholic.games.poker.logic.Pots;
import dev.nezo.burmaldaholic.games.poker.logic.StakeLevel;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Texas Hold'em cash table (GAME_DESIGN.md §7, UI.md §5). Humans buy in (balance → table stack), money
 * bots take seats through core Seats &amp; Bots ({@link TableBots}, BOTS.md: seat policy, Easy / Normal /
 * Hard = Fish / Regular / Shark, personalities), hands run on {@link Hand} with per-action timers, side
 * pots and rake.
 *
 * <p><b>Bots</b> (pvp-bots.md §4.7): {@link TableBots} decides who sits at the safe point (between hands,
 * before the button moves; claimants take the seat of the bot that just posted the big blind), funds bots
 * from their purse (the bank at house tables, the owner's bankroll at owned tables with Bots = Allowed) and
 * returns their stacks when they leave. Every bot decision goes through the pure {@link PokerBotPolicy}
 * with the table's BOT rng ({@link TableBots#rng()}); the game rng only shuffles. The Monte-Carlo work runs
 * as a {@link BotJobs} job within the think delay. EASY sits only up to {@code bots.poker.easyMaxStake}
 * ({@link #botLevelAllowed}, gated MIXED weights); heat ({@link BotLedger#record}); results against bots
 * carry the bot tags ({@link BotRounds}: no streak, weighted VIP credit, no house bonuses); the rake never
 * takes bot chips.
 *
 * <p><b>Money</b>: poker is PvP, so the core open-stake/bankroll machinery is not used. The buy-in is
 * escrowed in the world bank ({@link AccountId#HOUSE}) and the stack is paid back on stand-up / removal
 * (the invested part as a transfer, the profit as a garnishable payout). A claimant (every seat taken, a
 * bot yields at the next safe point) pays the buy-in up front; it is escrowed the same way and returned if
 * the claim lapses. The rake of raked pots goes to the owner's bankroll at owned tables (§18.2), otherwise
 * it stays in the bank (sink). A table that stops running (broken, chunk unloaded, server stopping, casino
 * mode off) plays the hand out, cashes everybody out and ends the bots' session (review M1).
 *
 * <p><b>Crash safety</b>: during a hand the hand's DRAWN outcome is kept current (after every action the
 * hand is played out on a copy as if every human left now: humans check/fold, bots play on, the dealt deck
 * decides): the humans' final stacks are saved with the block entity as refunds and the bots' final stacks
 * go to {@link TableBots#setStack} (bankroll bots: the world ledger returns them on the next start), so a
 * crash mid-hand settles both sides at the same result (GAME_DESIGN §4.1).
 */
public class PokerTableBlockEntity extends CasinoTableBlockEntity implements BotTable {
	private static final String STAKE_KEY = "burmaldaholic_poker_stake";
	private static final String REFUND_KEY = "burmaldaholic_poker_refunds";
	private static final String BOTS_KEY = "burmaldaholic_poker_bots";
	private static final Codec<Map<String, Long>> REFUND_CODEC = Codec.unboundedMap(Codec.STRING, Codec.LONG);
	private static final int LOG_SIZE = 8;
	private static final String GAME = "poker";
	/** Player → table they are seated (or waiting as a claimant) at: one poker seat per player. */
	private static final Map<UUID, PokerTableBlockEntity> SEATED = new ConcurrentHashMap<>();

	private StakeLevel stake;
	private PokerTable table;
	private int eventIdx;
	private int actionSeq = -1;
	private final Deque<Component> log = new ArrayDeque<>();
	private final List<Component> lastResult = new ArrayList<>();
	private final Map<String, Long> pendingRefunds = new LinkedHashMap<>();
	private boolean refundsLoaded;
	/** Seats marked leaving only because the player disconnected (re-attached if they come back). */
	private final Set<String> droppedSeats = new HashSet<>();

	/** Core Seats &amp; Bots state (created on first use; see {@link #tableBots()}). */
	private @Nullable TableBots bots;
	/** Saved bot state read before the level was known (applied when {@link #bots} is created). */
	private @Nullable CompoundTag savedBots;
	/** Claimants who paid their buy-in and wait for a bot's seat: player id → escrowed buy-in. */
	private final Map<String, Long> waiting = new LinkedHashMap<>();
	/** Drawn-outcome stacks of the humans in the running hand (crash refunds; empty between hands). */
	private final Map<String, Long> drawn = new HashMap<>();
	/** Increments whenever a pending bot decision must be dropped (table stopped, hand aborted). */
	private int botSeq;
	/** The bot decision being prepared (think delay / Monte-Carlo job). */
	private @Nullable BotTurn botTurn;
	/** BOTS_ONLY sessions: NORMAL/HARD bots busted per human ({@code clean_sweep}). */
	private final Map<String, Integer> sweeps = new HashMap<>();

	// ---- presentation pacing (animation/cards.md §2.2, task J-C2) -------------------------------------------
	/** Segment counter (a change restarts the clients' players). */
	private int stageSeq;
	/** "deal", "street", "finish" or "" (none yet). */
	private String stageKind = "";
	private long stageStart;
	private int[] stageArgs = new int[0];
	private @Nullable Timeline stageTl;
	/** The running segment has reached its end (its continuation ran). */
	private boolean stageDone = true;
	/** Board cards visible before the running segment started. */
	private int boardBase;
	/** Board cards covered by the segments started so far (all visible once they end). */
	private int boardPresented;
	/** The finished hand's moment ("big" / "monster" / ""), computed when its finish segment starts. */
	private String moment = "";
	/** Last published public tag (BER), to send block updates only when it changes. */
	private @Nullable CompoundTag lastPub;
	/** Client side: the public tag from the last block update. */
	private CompoundTag clientPub = new CompoundTag();

	/** A pending bot decision: taken when the "bot" timer fires and the hand is still at {@code seq}. */
	private static final class BotTurn {
		final int seq;
		final int botSeq;
		final BotProfile bot;
		final PokerBotPolicy.View view;
		/** the Monte-Carlo result, or null (none needed / not finished) */
		Object work;
		/** the job (if any) finished or was cut at its deadline */
		boolean workDone;
		boolean timerDone;

		BotTurn(int seq, int botSeq, BotProfile bot, PokerBotPolicy.View view) {
			this.seq = seq;
			this.botSeq = botSeq;
			this.bot = bot;
			this.view = view;
		}
	}

	public PokerTableBlockEntity(TableType<PokerTableBlockEntity> type, BlockPos pos, BlockState state) {
		super(type, pos, state);
	}

	/** Seats are managed by the poker table model (humans + bots), not by core's seat list. */
	@Override
	protected int seatCount() {
		return 0;
	}

	private static PokerConfig cfg() {
		return CasinoConfig.poker();
	}

	private static long bbFor(StakeLevel level) {
		PokerConfig.Stakes s = cfg().stakes;
		return switch (level) {
			case MICRO -> s.micro.bb;
			case LOW -> s.low.bb;
			case MID -> s.mid.bb;
			case HIGH -> s.high.bb;
		};
	}

	private static int[] botMix(StakeLevel level) {
		PokerConfig.BotMix m = cfg().botMix;
		return switch (level) {
			case MICRO -> m.micro;
			case LOW -> m.low;
			case MID -> m.mid;
			case HIGH -> m.high;
		};
	}

	private static Pots.RakeConfig rakeConfig() {
		return new Pots.RakeConfig(cfg().rakePercent, cfg().rakeCapBb, cfg().rakeNoFlopNoDrop);
	}

	private static PokerBotPolicy policy() {
		return new PokerBotPolicy(new PokerBotPolicy.Config(cfg().bot.regularSamples, cfg().bot.sharkSamples));
	}

	private static String easyMaxStake() {
		return CasinoConfig.bots().poker.easyMaxStake.name();
	}

	/** The GAME rng (shuffles only, fair — poker is never odds-adjusted). Bot code never calls it (BOTS.md §4.1). */
	private static PokerRng gameRng() {
		CasinoRng r = OddsService.get().fair();
		return new PokerRng() {
			@Override
			public int nextInt(int bound) {
				return r.nextInt(bound);
			}

			@Override
			public double nextDouble() {
				return r.nextDouble();
			}
		};
	}

	private MinecraftServer server() {
		return level instanceof ServerLevel sl ? sl.getServer() : null;
	}

	private ServerPlayer online(String id) {
		MinecraftServer server = server();
		if (server == null || SeatOccupant.isBotKey(id)) {
			return null;
		}
		try {
			return server.getPlayerList().getPlayer(UUID.fromString(id));
		} catch (IllegalArgumentException e) {
			return null;
		}
	}

	private List<ServerPlayer> seatedOnline() {
		List<ServerPlayer> out = new ArrayList<>();
		if (table == null) {
			return out;
		}
		for (PokerTable.Seat s : table.humans()) {
			ServerPlayer p = online(s.id);
			if (p != null && !s.disconnected) {
				out.add(p);
			}
		}
		return out;
	}

	private int vipTier(ServerPlayer player) {
		return CoreServices.vip().tier(player.level().getServer(), player.getUUID());
	}

	private static @Nullable UUID uuid(String id) {
		try {
			return UUID.fromString(id);
		} catch (IllegalArgumentException e) {
			return null;
		}
	}

	// ---- Seats & Bots (core TableBots hooks) ---------------------------------------------------------

	/** This table's Seats &amp; Bots state (bots UI, commands). Created with the saved / worldgen / configured defaults. */
	@Override
	public TableBots tableBots() {
		if (bots == null) {
			boolean worldgen = preset().isPresent();
			// Generated tables: the worldgen preset's defaults (J-G6; bots.table.poker.worldgen*, not the old fixed pokerBots).
			BotSettings d = dev.nezo.burmaldaholic.core.bots.BotPresets.defaults(this, GAME, worldgen);
			bots = new TableBots(this, d, OwnerControls.unowned(botSeatCount()));
			if (savedBots != null) {
				bots.load(savedBots);
				savedBots = null;
			}
			try {
				PokerBotsUi.get().attach(this, bots);
			} catch (RuntimeException e) {
				Burmaldaholic.LOGGER.warn("Poker: bots UI attach failed", e);
			}
		}
		return bots;
	}

	@Override
	public String botGameId() {
		return GAME;
	}

	@Override
	public BotRole botRole() {
		return BotRole.MONEY;
	}

	@Override
	public int botSeatCount() {
		return table != null ? table.size() : Math.max(2, cfg().maxSeats);
	}

	@Override
	public List<UUID> seatedHumans() {
		if (table == null) {
			return List.of();
		}
		List<UUID> out = new ArrayList<>();
		for (String id : table.seatedHumans()) {
			UUID u = uuid(id);
			if (u != null) {
				out.add(u);
			}
		}
		return out;
	}

	@Override
	public List<SeatOccupant> occupants() {
		return table != null ? table.occupants() : Collections.nCopies(botSeatCount(), null);
	}

	@Override
	public boolean seatBot(SeatOccupant.Bot bot, long stack) {
		if (table == null || table.inHand()) {
			return false;
		}
		boolean ok = table.seatBot(bot.profile(), bot.purse(), stack) >= 0;
		if (ok) {
			setChanged();
		}
		return ok;
	}

	@Override
	public long unseatBot(String botKey) {
		if (table == null) {
			return 0;
		}
		setChanged();
		return table.unseatBot(botKey);
	}

	@Override
	public SeatingMath.YieldRule yieldRule() {
		return SeatingMath.YieldRule.POKER_BIG_BLIND;
	}

	@Override
	public long botBuyIn() {
		return table == null ? 0 : (long) cfg().botBuyInBb * table.bb();
	}

	/** Stake level fixed right now: the running table's, else the generated table's preset. */
	private @Nullable StakeLevel currentStake() {
		if (stake != null && table != null) {
			return stake;
		}
		String fixed = preset().map(TablePresetProvider.TablePreset::pokerStakes).orElse("");
		return fixed.isEmpty() ? stake : StakeLevel.byId(fixed);
	}

	@Override
	public int[] botDifficultyMix() {
		// the worldgen preset's mix first (J-G6; Piglin Parlor: Regular-heavy), still stake-gated below
		StakeLevel s = currentStake();
		int[] fixed = dev.nezo.burmaldaholic.core.bots.BotPresets.mix(this, GAME);
		int[] mix = fixed != null && fixed.length == 3 ? fixed : preset().map(TablePresetProvider.TablePreset::pokerBotMix).filter(m -> m.size() == 3)
			.map(m -> m.stream().mapToInt(Integer::intValue).toArray())
			.orElseGet(() -> botMix(s == null ? StakeLevel.MICRO : s));
		return PokerBotPolicy.gatedMix(mix, s, easyMaxStake());
	}

	/** Stake gate (BOTS.md §4.2): no EASY above {@code bots.poker.easyMaxStake}. */
	@Override
	public boolean botLevelAllowed(BotDifficulty level) {
		return level != BotDifficulty.EASY || PokerBotPolicy.easyAllowed(currentStake(), easyMaxStake());
	}

	@Override
	public BotRoster.Theme botNameTheme() {
		return dev.nezo.burmaldaholic.core.bots.BotPresets.theme(this, GAME,
			preset().map(p -> p.id().contains("parlor") ? BotRoster.Theme.PIGLIN : BotRoster.Theme.ANY).orElse(BotRoster.Theme.ANY));
	}

	@Override
	public int handsSinceBigBlind(String botKey) {
		return table == null ? Integer.MAX_VALUE : table.handsSinceBigBlind(botKey);
	}

	// ---- actions ----------------------------------------------------------------------------------

	@Override
	public void onAction(ServerPlayer player, String action, CompoundTag args) {
		switch (action) {
			case "buy_in" -> buyIn(player, args.getStringOr("level", ""), args.getLongOr("amount", 0));
			case "top_up" -> topUp(player, args.getLongOr("amount", 0));
			case "stand_up" -> standUp(player);
			case "sit_out" -> {
				if (table != null && table.seatOf(id(player)) != null) {
					table.sitOut(id(player));
				}
			}
			case "sit_in" -> {
				if (table != null && table.seatOf(id(player)) != null) {
					table.sitIn(id(player));
					scheduleHand(20);
				}
			}
			case "act" -> act(player, args);
			default -> {
			}
		}
		syncViewers();
	}

	private static String id(ServerPlayer player) {
		return player.getUUID().toString();
	}

	private boolean humansSeated() {
		return table != null && !table.humans().isEmpty();
	}

	private void buyIn(ServerPlayer player, String levelId, long amount) {
		if (!cfg().enabled) {
			sendError(player, Component.translatable("gui.burmaldaholic.error.disabled"));
			return;
		}
		// Wager gate for the PvP entry: owner can't play at their own table, closed casino, loan Asset Freeze.
		Component veto = Wagers.check(player, new WagerVeto.Context(gameId(), Stake.Kind.CHIPS, worldPosition, true));
		if (veto != null) {
			sendError(player, veto);
			return;
		}
		PokerTableBlockEntity other = SEATED.get(player.getUUID());
		if (other != null && other != this && !other.isRemoved() && other.holds(id(player))) {
			sendError(player, Component.translatable("gui.burmaldaholic.error.busy"));
			return;
		}
		if (holds(id(player))) {
			return;
		}
		if (table != null && table.dealtInto(id(player))) {
			// Review B1: still dealt into the running hand (folded, then stood up): no new seat until it ends.
			sendError(player, Component.translatable("gui.burmaldaholic.error.round_in_progress"));
			return;
		}
		// A generated table may fix the stake level (Piglin Parlor: Low, §16.2).
		String fixed = preset().map(TablePresetProvider.TablePreset::pokerStakes).orElse("");
		StakeLevel lvl = humansSeated() ? stake : StakeLevel.byId(fixed.isEmpty() ? levelId : fixed);
		if (lvl == null) {
			sendError(player, Component.translatable("gui.burmaldaholic.error.invalid_amount"));
			return;
		}
		if (vipTier(player) < lvl.minTier()) {
			sendError(player, Component.translatable("gui.burmaldaholic.error.vip_required", VipTiers.name(lvl.minTier())));
			return;
		}
		Economy eco = Economies.get();
		long balance = eco.balance(player);
		long bb = bbFor(lvl);
		long[] range = StakeLevel.buyInRange(bb, cfg().minBuyInBb, cfg().maxBuyInBb, balance, 0);
		if (range[1] < range[0]) {
			sendError(player, Component.translatable("gui.burmaldaholic.error.insufficient_funds", Texts.number(balance)));
			return;
		}
		if (amount < range[0] || amount > range[1]) {
			sendError(player, Component.translatable("gui.burmaldaholic.poker.buy_in_range", Texts.number(range[0]), Texts.number(range[1])));
			return;
		}
		// Seats & Bots admission (BOTS.md §3.2): private table, someone's BOTS_ONLY table, or a claim on a bot's seat.
		if (!humansSeated() && waiting.isEmpty()) {
			stake = lvl; // the stake gate of the admission / first safe point reads it
		}
		Result<Boolean> admit = tableBots().admit(player);
		if (!admit.isOk()) {
			sendError(player, admit.error());
			endIdleSession();
			return;
		}
		boolean claimant = !Boolean.TRUE.equals(admit.value());
		if (claimant && table == null) {
			tableBots().withdrawClaim(player.getUUID());
			sendError(player, Component.translatable("gui.burmaldaholic.error.table_full"));
			return;
		}
		if (!eco.tryWithdraw(player, amount, new Transaction(PokerModule.ID, "buy_in", Transaction.Kind.TRANSFER))) {
			if (claimant) {
				tableBots().withdrawClaim(player.getUUID());
			}
			endIdleSession();
			sendError(player, Component.translatable("gui.burmaldaholic.error.insufficient_funds", Texts.number(eco.balance(player))));
			return;
		}
		SEATED.put(player.getUUID(), this);
		if (claimant) {
			// Claimant: seated at the next safe point in the seat of the bot that leaves (BOTS.md §3.2);
			// the buy-in waits in escrow and is returned if the claim lapses.
			waiting.put(id(player), amount);
			setChanged();
			if (!table.inHand()) {
				scheduleHand(40);
			}
			return;
		}
		if (table == null) {
			stake = lvl;
			table = new PokerTable(cfg().maxSeats, bb, rakeConfig());
			log.clear();
			lastResult.clear();
			sweeps.clear();
			setPhase("waiting");
		}
		int seat = table.addHuman(id(player), player.getName().getString(), amount);
		if (seat < 0) {
			SEATED.remove(player.getUUID(), this);
			payOut(player.getUUID(), amount, amount);
			sendError(player, Component.translatable(table.inHand() ? "gui.burmaldaholic.error.round_in_progress" : "gui.burmaldaholic.error.table_full"));
			return;
		}
		setChanged();
		if (!table.inHand()) {
			scheduleHand(40);
		}
	}

	/** Seated at this table or waiting here as a claimant. */
	private boolean holds(String id) {
		return (table != null && table.seatOf(id) != null) || waiting.containsKey(id);
	}

	/** A session {@code admit} opened for nobody (refused buy-in at an empty table) ends again. */
	private void endIdleSession() {
		if (!humansSeated() && waiting.isEmpty() && table == null && bots != null && bots.inSession() && level instanceof ServerLevel sl) {
			bots.endSession(sl);
		}
	}

	private void topUp(ServerPlayer player, long amount) {
		if (table == null || table.seatOf(id(player)) == null) {
			return;
		}
		if (!table.canTopUp(id(player))) {
			sendError(player, Component.translatable("gui.burmaldaholic.error.round_in_progress"));
			return;
		}
		Economy eco = Economies.get();
		long balance = eco.balance(player);
		long stack = table.seatOf(id(player)).stack;
		long[] range = StakeLevel.buyInRange(table.bb(), cfg().minBuyInBb, cfg().maxBuyInBb, balance, stack);
		if (range[1] < range[0]) {
			sendError(player, range[1] <= 0 && balance > 0
				? Component.translatable("gui.burmaldaholic.error.table_max", Texts.number(stack))
				: Component.translatable("gui.burmaldaholic.error.insufficient_funds", Texts.number(balance)));
			return;
		}
		if (amount < range[0] || amount > range[1]) {
			sendError(player, Component.translatable("gui.burmaldaholic.error.table_max", Texts.number(range[1])));
			return;
		}
		if (!eco.tryWithdraw(player, amount, new Transaction(PokerModule.ID, "buy_in", Transaction.Kind.TRANSFER))) {
			sendError(player, Component.translatable("gui.burmaldaholic.error.insufficient_funds", Texts.number(eco.balance(player))));
			return;
		}
		table.topUp(id(player), amount);
		setChanged();
		if (!table.inHand()) {
			scheduleHand(20);
		}
	}

	private void standUp(ServerPlayer player) {
		String id = id(player);
		if (waiting.containsKey(id)) {
			refundWaiting(id);
			afterHumanLeft();
			return;
		}
		if (table == null) {
			return;
		}
		PokerTable.Seat seat = table.seatOf(id);
		if (seat == null) {
			return;
		}
		if (table.liveInHand(id)) {
			seat.leaving = true;
			Hand h = table.hand();
			if (h.toAct() >= 0 && h.player(h.toAct()).id.equals(id)) {
				apply(Hand.Action.fold());
			}
			if (table != null && table.seatOf(id) != null) { // the fold may have ended the hand and closed the table
				player.sendSystemMessage(PokerText.msg("leaving_after_hand"));
			}
			return;
		}
		if (table.inHand() && table.handIndexOf(id) >= 0) {
			// folded in the running hand: the seat's stack is final, leave now
			seat.leaving = true;
		}
		cashOut(id);
		afterHumanLeft();
	}

	private void act(ServerPlayer player, CompoundTag args) {
		if (table == null || !table.inHand()) {
			return;
		}
		Hand h = table.hand();
		String id = id(player);
		if (stageRunning()) {
			return; // cards are still moving: the turn opens when the segment ends
		}
		if (h.toAct() < 0 || !h.player(h.toAct()).id.equals(id)) {
			sendError(player, Component.translatable("gui.burmaldaholic.error.not_your_turn"));
			return;
		}
		if (args.getIntOr("seq", h.seq()) != h.seq()) {
			return; // stale click
		}
		Hand.Action a = switch (args.getStringOr("kind", "")) {
			case "fold" -> Hand.Action.fold();
			case "check" -> Hand.Action.check();
			case "call" -> Hand.Action.call();
			case "raise" -> Hand.Action.raiseTo(args.getLongOr("to", 0));
			case "all_in" -> Hand.Action.allIn();
			default -> null;
		};
		if (a == null) {
			return;
		}
		table.recordAction(id);
		PokerTable.Seat seat = table.seatOf(id);
		if (seat != null && seat.sittingOut) {
			table.sitIn(id);
		}
		apply(a);
	}

	// ---- hand loop --------------------------------------------------------------------------------

	private void scheduleHand(int ticks) {
		if (table == null || table.inHand() || ticksLeft("next_hand") >= 0) {
			return;
		}
		startTimer("next_hand", ticks);
	}

	@Override
	protected void onTimer(String id) {
		try {
			switch (id) {
				case "next_hand" -> beginHand();
				case "action" -> onActionTimeout();
				case "bot" -> onBotTimer();
				case "auto" -> autoAct();
				case "beat" -> onBeat();
				default -> {
				}
			}
		} catch (RuntimeException e) {
			Burmaldaholic.LOGGER.error("Poker table {} timer '{}' failed", worldPosition, id, e);
			shutdown();
		}
		syncViewers();
	}

	private void beginHand() {
		if (table == null || table.inHand()) {
			return;
		}
		if (!cfg().enabled || !(level instanceof ServerLevel serverLevel) || !CasinoMode.isEnabled(serverLevel)) {
			shutdown();
			return;
		}
		table.configure(bbFor(stake), rakeConfig());
		double max = cfg().maxDistance;
		for (PokerTable.Seat s : table.humans()) {
			ServerPlayer p = online(s.id);
			if (p == null) {
				s.disconnected = true;
				continue;
			}
			boolean far = p.level() != level || p.distanceToSqr(Vec3.atCenterOf(worldPosition)) > max * max;
			if (far && !s.sittingOut) {
				table.sitOut(s.id);
				p.sendSystemMessage(Component.translatable("gui.burmaldaholic.error.too_far"));
			}
		}
		removeFinished();
		if (!humansSeated() && waiting.isEmpty()) {
			afterHumanLeft();
			return;
		}
		safePoint(serverLevel);
		if (!humansSeated()) {
			afterHumanLeft();
			return;
		}
		if (stageRunning()) {
			// the previous hand's showdown is still on the felt
			startTimer("next_hand", Math.max(1, stageTicksLeft()));
			return;
		}
		if (!table.canStart()) {
			boolean allSittingOut = table.humans().stream().allMatch(s -> s.sittingOut || s.stack <= 0);
			if (allSittingOut) {
				table.countIdleHand();
				removeFinished();
				if (!humansSeated()) {
					afterHumanLeft();
					return;
				}
			}
			setPhase("waiting");
			startTimer("next_hand", 100);
			return;
		}
		// The GAME rng shuffles; bots never draw from it (BOTS.md §4.1).
		Hand h = table.startHand(gameRng());
		if (h == null) {
			return;
		}
		setPhase("hand");
		eventIdx = 0;
		lastResult.clear();
		addLog(PokerText.msg("new_hand", Texts.number(table.handNo()), Texts.number(table.sb()), Texts.number(table.bb()))
			.withStyle(ChatFormatting.GRAY));
		setChanged();
		boardBase = 0;
		boardPresented = 0;
		moment = "";
		streamEvents();
		startStage("deal", new int[] {h.players().size(), h.sbIndex()},
			PokerBeats.deal(h.players().size(), h.sbIndex(), PokerBeats.Pacing.DEFAULT, stageSeed()));
	}

	/**
	 * The safe point (BOTS.md §3.1: between hands, before the button moves): pending settings apply, bots
	 * leave / join (core messages), busted bots are replaced, claimants take the freed seats with their
	 * escrowed buy-in; lapsed claims are refunded.
	 */
	private void safePoint(ServerLevel sl) {
		TableBots tb = tableBots();
		if (!cfg().botsEnabled && tb.settings().policy() != SeatPolicy.HUMANS_ONLY) {
			tb.clear(); // legacy alias: poker.botsEnabled = false forces HUMANS_ONLY (BOTS.md §9.3)
		}
		TableBots.SafePointResult r;
		try {
			r = tb.safePoint(sl);
		} catch (RuntimeException e) {
			Burmaldaholic.LOGGER.error("Poker table {}: bots safe point failed", worldPosition, e);
			return;
		}
		for (UUID id : r.seatedClaimants()) {
			Long amount = waiting.remove(id.toString());
			if (amount == null) {
				continue;
			}
			ServerPlayer p = online(id.toString());
			String name = p != null ? p.getName().getString() : "?";
			if (table.addHuman(id.toString(), name, amount) < 0) {
				waiting.put(id.toString(), amount);
				refundWaiting(id.toString());
			}
		}
		// Claims that lapsed in core (walked away, logged out) get their buy-in back.
		for (String id : List.copyOf(waiting.keySet())) {
			UUID u = uuid(id);
			if (u == null || !tb.claimants().contains(u)) {
				refundWaiting(id);
			}
		}
		try {
			tb.announce(sl, r);
			if (!r.joined().isEmpty()) {
				quip(sl, r.joined().getFirst().profile, "join", null);
			}
		} catch (RuntimeException e) {
			Burmaldaholic.LOGGER.warn("Poker table {}: bot announcements failed", worldPosition, e);
		}
		setChanged();
	}

	/** A waiting claimant leaves / the claim lapsed: the escrowed buy-in goes back (offline-safe). */
	private void refundWaiting(String id) {
		Long amount = waiting.remove(id);
		UUID u = uuid(id);
		if (u == null) {
			return;
		}
		if (bots != null) {
			bots.withdrawClaim(u);
		}
		if (table == null || table.seatOf(id) == null) {
			SEATED.remove(u, this);
		}
		if (amount != null && amount > 0) {
			payOut(u, amount, amount);
			ServerPlayer p = online(id);
			if (p != null) {
				p.sendSystemMessage(PokerText.msg("removed", Texts.chips(amount)));
			}
		}
		setChanged();
	}

	private void addLog(Component line) {
		log.addLast(line);
		while (log.size() > LOG_SIZE) {
			log.removeFirst();
		}
	}

	private void broadcast(Component msg) {
		for (ServerPlayer p : seatedOnline()) {
			p.sendSystemMessage(msg);
		}
	}

	/** New hand events → table log + action bar of seated players. */
	private void streamEvents() {
		Hand h = table.hand();
		if (h == null) {
			return;
		}
		List<Hand.Event> events = h.events();
		Component last = null;
		int board = 0;
		for (int i = 0; i < eventIdx && i < events.size(); i++) {
			if (events.get(i) instanceof Hand.Dealt d) {
				board += d.cards().length;
			}
		}
		int visible = visibleBoard(h);
		int i = eventIdx;
		for (; i < events.size(); i++) {
			if (events.get(i) instanceof Hand.Dealt d) {
				// a street's log line waits until its cards are dealt on the felt (the slow run-out)
				if (board + d.cards().length > visible) {
					break;
				}
				board += d.cards().length;
			}
			last = PokerText.event(table, h, events.get(i));
			addLog(last);
		}
		eventIdx = i;
		if (last != null) {
			for (ServerPlayer p : seatedOnline()) {
				p.sendOverlayMessage(last);
			}
		}
	}

	/** Hands the turn to the next actor (human timer / bot think / auto-fold) or ends the hand. */
	private void drive() {
		cancelTimer("action");
		cancelTimer("bot");
		cancelTimer("auto");
		botTurn = null;
		Hand h = table.hand();
		if (h == null) {
			return;
		}
		if (stageRunning()) {
			return; // the running segment calls drive() again when it ends
		}
		if (h.complete()) {
			endHand();
			return;
		}
		saveDrawn(h);
		if (h.board().size() > boardPresented) {
			// a street was dealt: present it (gather, burn, slide, flip) before the next turn opens
			int target = boardPresented < 3 ? 3 : boardPresented + 1;
			int street = target == 3 ? 1 : target - 2;
			boardBase = boardPresented;
			boardPresented = target;
			startStage("street", new int[] {street}, PokerBeats.street(street, PokerBeats.Pacing.DEFAULT, stageSeed()));
			return;
		}
		Hand.Player p = h.player(h.toAct());
		PokerTable.Seat seat = table.seatOf(p.id);
		actionSeq = h.seq();
		if (seat == null || !seat.human) {
			scheduleBot(h, seat);
			return;
		}
		ServerPlayer player = online(p.id);
		if (player == null || seat.sittingOut || seat.leaving || seat.disconnected) {
			startTimer("auto", 10);
			return;
		}
		startTimer("action", cfg().actionTimerTicks);
		player.sendOverlayMessage(PokerText.gui("your_turn").withStyle(ChatFormatting.GREEN));
	}

	private boolean current() {
		return table != null && table.inHand() && table.hand().seq() == actionSeq;
	}

	private void apply(Hand.Action a) {
		Hand h = table.hand();
		if (h == null || h.complete()) {
			return;
		}
		try {
			h.apply(h.coerce(a));
		} catch (IllegalStateException e) {
			Burmaldaholic.LOGGER.warn("Poker: illegal action {} coerced to check/fold", a, e);
			h.apply(h.legal().canCheck() ? Hand.Action.check() : Hand.Action.fold());
		}
		setChanged();
		streamEvents();
		drive();
	}

	private void onActionTimeout() {
		if (!current()) {
			return;
		}
		Hand h = table.hand();
		String id = h.player(h.toAct()).id;
		Hand.Action auto = h.legal().canCheck() ? Hand.Action.check() : Hand.Action.fold();
		ServerPlayer player = online(id);
		if (player != null) {
			player.sendSystemMessage(PokerText.msg("timeout", PokerText.gui(auto.kind() == Hand.Action.Kind.CHECK ? "check" : "fold")));
		}
		if (table.recordTimeout(id, cfg().timeoutsToSitOut) && player != null) {
			player.sendSystemMessage(PokerText.msg("sat_out"));
		}
		apply(auto);
	}

	private void autoAct() {
		if (!current()) {
			return;
		}
		apply(Hand.Action.fold());
	}

	// ---- bots' decisions ------------------------------------------------------------------------------

	/**
	 * A bot's turn (BOTS.md §7.3): its think delay (from the BOT rng) runs while the heavy work (range
	 * equity, {@link BotWork}) runs as a {@link BotJobs} job; at the deadline the partial result is used.
	 * Stale decisions (the hand moved on, the table stopped) are dropped; bot code errors check / fold.
	 */
	private void scheduleBot(Hand h, PokerTable.@Nullable Seat seat) {
		if (seat == null || seat.bot == null) {
			startTimer("auto", 10);
			return;
		}
		try {
			int i = h.toAct();
			PokerBotPolicy.View view = PokerBotPolicy.view(h, i, table::statsOf, seat.tilt > 0);
			TableBots tb = tableBots();
			int humanTimer = cfg().actionTimerTicks;
			boolean big = view.toCall() > 0.25 * (view.stack() + view.bet());
			int think = tb.thinkTicks(seat.bot, big, humanTimer);
			// EASY: half the delay and a readable tell (+40 t) with a strong hand (BOTS.md §4.3, documented in the Rules page).
			if (seat.bot.level() == BotDifficulty.EASY) {
				think = think / 2 + (PokerBotPolicy.fishStrong(view) ? 40 : 0);
			}
			think = Math.max(0, Math.min(think, Math.min(humanTimer / 2, CasinoConfig.bots().think.maxTicks + 40)));
			BotTurn turn = new BotTurn(h.seq(), botSeq, seat.bot, view);
			botTurn = turn;
			BotWork work = policy().work(seat.bot, view, tb.rng());
			if (work == null) {
				turn.workDone = true;
			} else {
				BotJobs.submit(work, Math.max(1, think), () -> botTurn == turn && turn.botSeq == botSeq && current(), result -> {
					turn.work = result;
					turn.workDone = true;
					if (turn.timerDone) {
						botDecide(turn);
					}
				});
			}
			startTimer("bot", think);
		} catch (RuntimeException e) {
			Burmaldaholic.LOGGER.error("Poker: bot turn failed; checking / folding", e);
			startTimer("auto", 10);
		}
	}

	private void onBotTimer() {
		BotTurn turn = botTurn;
		if (turn == null || !current()) {
			return;
		}
		turn.timerDone = true;
		if (turn.workDone) {
			botDecide(turn);
		}
		// else: the job's deadline equals the think delay; it calls back with its partial result
	}

	private void botDecide(BotTurn turn) {
		if (botTurn != turn || turn.botSeq != botSeq || !current() || table.hand().seq() != turn.seq) {
			return;
		}
		botTurn = null;
		Hand.Action a;
		try {
			a = policy().act(turn.bot, turn.view, turn.work, tableBots().rng());
		} catch (RuntimeException e) {
			Burmaldaholic.LOGGER.error("Poker bot decision failed; checking / folding", e);
			a = table.hand().legal().canCheck() ? Hand.Action.check() : Hand.Action.fold();
		}
		apply(a);
		syncViewers();
	}

	/** Action in the drawn play-out: humans check/fold (they left), bots decide as usual (bot rng). */
	private Hand.Action leaveAction(Hand h, int i) {
		Hand.Player p = h.player(i);
		PokerTable.Seat seat = table.seatOf(p.id);
		if (p.human || seat == null || seat.bot == null) {
			return h.legal(i).canCheck() ? Hand.Action.check() : Hand.Action.fold();
		}
		PokerBotPolicy.View v = PokerBotPolicy.view(h, i, table::statsOf, seat.tilt > 0);
		return policy().decideNow(seat.bot, v, tableBots().rng(), PokerBotPolicy.PLAYOUT_SAMPLES);
	}

	/**
	 * Keeps the hand's drawn outcome current (GAME_DESIGN §4.1, review M1/M2): the stacks every seat ends
	 * with if every human left now (auto check/fold) and the bots played on, on the deck already dealt.
	 * Humans' stacks are saved as crash refunds; bots' stacks go to TableBots (bankroll bots: the ledger),
	 * both from the same play-out, so a crash settles both sides at the same result.
	 */
	private void saveDrawn(Hand h) {
		Hand end;
		try {
			end = h.playOut(this::leaveAction, 500);
		} catch (RuntimeException e) {
			Burmaldaholic.LOGGER.error("Poker: drawn play-out failed", e);
			return;
		}
		if (end == null) {
			return;
		}
		drawn.clear();
		TableBots tb = tableBots();
		for (Map.Entry<String, Long> e : table.drawnHoldings(end).entrySet()) {
			PokerTable.Seat s = table.seatOf(e.getKey());
			if (s == null) {
				continue;
			}
			if (s.human) {
				drawn.put(e.getKey(), e.getValue());
			} else {
				tb.setStack(e.getKey(), e.getValue());
			}
		}
		setChanged();
	}

	// ---- presentation segments (J-C2) ------------------------------------------------------------------

	/** Cosmetic seed of the next segment (public values only, docs/architecture/animation.md §3.5). */
	private int stageSeed() {
		return SeedMix.mix(SeedMix.mixLong(worldPosition.asLong()), table == null ? 0 : table.handNo(), stageSeq + 1);
	}

	/** Starts a presentation segment now; publication beats sync viewers, the end runs the continuation. */
	private void startStage(String kind, int[] args, Timeline tl) {
		stageSeq++;
		stageKind = kind;
		stageArgs = args.clone();
		stageTl = tl;
		stageStart = gameTime();
		stageDone = false;
		scheduleBeat();
	}

	/** Milliseconds since the running segment started. */
	private double stageMs() {
		return (gameTime() - stageStart) * 50.0;
	}

	/** A segment is still playing (its continuation has not run yet). */
	private boolean stageRunning() {
		return !stageDone && stageTl != null;
	}

	private int stageTicksLeft() {
		return stageTl == null ? 0 : (int) Math.max(0, Timeline.ceilTicks(stageTl.sharedEndMs()) - (gameTime() - stageStart));
	}

	private void scheduleBeat() {
		Timeline tl = stageTl;
		if (tl == null || stageDone) {
			return;
		}
		double t = stageMs();
		int next = PokerBeats.nextPublication(tl, t);
		if (next < 0) {
			endStage();
			return;
		}
		startTimer("beat", Math.max(1, Timeline.ceilTicks((int) Math.ceil(next - t))));
	}

	private void onBeat() {
		if (table != null && table.hand() != null) {
			streamEvents();
		}
		scheduleBeat();
	}

	private void endStage() {
		if (stageDone) {
			return;
		}
		stageDone = true;
		cancelTimer("beat");
		boardBase = boardPresented;
		if (table == null) {
			return;
		}
		if ("finish".equals(stageKind)) {
			reveal();
		} else if (table.inHand()) {
			streamEvents();
			drive();
		}
	}

	/**
	 * The hand just completed (money already settled): the finish segment shows the rest of the board (slow all-in
	 * run-out), the showdown in order and the pot awards; the result lines and the next hand wait for its gate.
	 */
	private void startFinish(Hand h) {
		Hand.Result r = h.result();
		int dealt = h.board().size();
		int runoutFrom = 0;
		if (!r.uncontested() && dealt > boardPresented) {
			runoutFrom = boardPresented < 3 ? 1 : boardPresented - 1;
		}
		boolean gather = false;
		for (Hand.Event e : h.events()) {
			if (e instanceof Hand.Dealt) {
				gather = false;
			} else if (e instanceof Hand.Blind || e instanceof Hand.Acted a && a.amount() > 0) {
				gather = true;
			}
		}
		int shows = r.uncontested() ? 0 : r.shown().size();
		boolean expose = runoutFrom > 0 && shows >= 2;
		int pots = Math.max(1, r.pots().size());
		long total = 0;
		for (Hand.PotResult pot : r.pots()) {
			total += pot.amount();
		}
		long bb = Math.max(1, h.bb());
		moment = total >= 100 * bb ? "monster" : total >= 50 * bb ? "big" : "";
		boardBase = boardPresented;
		boardPresented = dealt;
		PokerBeats.Finish f = new PokerBeats.Finish(gather, runoutFrom, expose, shows, pots);
		startStage("finish", new int[] {gather ? 1 : 0, runoutFrom, expose ? 1 : 0, shows, pots},
			PokerBeats.finish(f, PokerBeats.Pacing.DEFAULT, stageSeed()));
	}

	/** The reveal gate of a finished hand: result lines in chat, the rest of the log, then the next hand. */
	private void reveal() {
		Hand h = table.lastHand();
		if (h != null && h.result() != null) {
			streamRemaining(h);
			for (Component line : lastResult) {
				broadcast(line);
			}
		}
		if (!table.inHand()) {
			boolean uncontested = h == null || h.result() == null || h.result().uncontested();
			scheduleHand(uncontested ? 40 : 60);
		}
	}

	/** Log lines of a finished hand that were held back by the presentation. */
	private void streamRemaining(Hand h) {
		List<Hand.Event> events = h.events();
		for (int i = eventIdx; i < events.size(); i++) {
			addLog(PokerText.event(table, h, events.get(i)));
		}
		eventIdx = events.size();
	}

	/** Board cards shown on the felt now (the segments publish them one slide beat at a time). */
	private int visibleBoard(Hand h) {
		int dealt = h.board().size();
		if (stageRunning() && stageTl != null && ("street".equals(stageKind) || "finish".equals(stageKind))) {
			return Math.min(dealt, Math.max(boardBase, PokerBeats.boardPublished(stageTl, stageMs())));
		}
		return Math.min(dealt, boardPresented);
	}

	/** Beats of {@code kind} started in the running finish segment ({@link Integer#MAX_VALUE} once it ended). */
	private int finishStarted(String kind) {
		if (!"finish".equals(stageKind) || stageTl == null) {
			return 0;
		}
		return stageDone ? Integer.MAX_VALUE : PokerBeats.started(stageTl, kind, stageMs());
	}

	// ---- settlement ----------------------------------------------------------------------------------

	private void endHand() {
		Hand h = table.hand();
		if (h == null || h.result() == null) {
			return;
		}
		Hand.Result r = h.result();
		lastResult.clear();
		lastResult.addAll(PokerText.resultLines(table, h)); // posted at the reveal gate (finish segment end)
		MinecraftServer server = server();
		String bankroll = ownership().map(OwnedTable::bankrollId).orElse("");
		int n = h.players().size();
		PokerTable.Seat[] seatOf = new PokerTable.Seat[n];
		for (int k = 0; k < n; k++) {
			seatOf[k] = table.seatOf(h.player(k).id);
		}
		java.util.function.IntPredicate isBot = k -> !h.player(k).human;
		java.util.function.IntPredicate isHouseBot = k -> isBot.test(k) && (seatOf[k] == null || seatOf[k].purse == null || seatOf[k].purse.houseFunded());
		PokerMoney.Attribution attr = PokerMoney.attribute(h, isBot, isHouseBot);
		boolean anyBot = false;
		boolean anyHouseBot = false;
		List<Integer> humans = new ArrayList<>();
		List<Integer> bustedBots = new ArrayList<>();
		for (int k = 0; k < n; k++) {
			if (isBot.test(k)) {
				anyBot = true;
				anyHouseBot |= isHouseBot.test(k);
				if (h.player(k).stack() <= 0) {
					bustedBots.add(k);
				}
			} else {
				humans.add(k);
			}
		}
		boolean sharkBusted = bustedBots.stream().anyMatch(k -> levelOf(seatOf[k]) == BotDifficulty.HARD);
		boolean botsOnly = tableBots().settings().policy() == SeatPolicy.BOTS_ONLY;
		for (int k : humans) {
			Hand.Player p = h.player(k);
			UUID uuid = uuid(p.id);
			if (uuid == null || server == null || !(level instanceof ServerLevel sl)) {
				continue;
			}
			// Heat (house-funded bots only, BOTS.md §5.4): the round that crosses a line is kept.
			if (anyHouseBot && attr.houseNet()[k] != 0) {
				try {
					BotLedger.record(server, uuid, attr.houseNet()[k]);
				} catch (RuntimeException e) {
					Burmaldaholic.LOGGER.error("Poker: heat ledger failed", e);
				}
			}
			if (anyHouseBot && h.bb() > 0) {
				BotLedger.recordPokerHand(server, uuid, attr.houseNet()[k] / (double) h.bb()); // adaptive heat (BOTS.md §5.4)
			}
			if (p.total() <= 0) {
				continue;
			}
			// PvP pot: no house edge, no Golden Hour, no cashback. Against money bots: bot tags (no streak,
			// VIP / wager credit weighted by the bots' share, no house bonuses — BOTS.md §5.3). Offline-safe.
			CasinoEvents.PlayResult result = CasinoEvents.PlayResult.of(gameId(), p.total(), r.won()[k]).pvp().withTable(sl, worldPosition, bankroll);
			if (anyBot) {
				result = BotRounds.withShare(BotRounds.tag(result, true, humans.size() <= 1), humans.size() <= 1 ? 1 : attr.botShare()[k]);
			}
			PlayResults.fire(server, uuid, result);
			if (r.won()[k] > 0) {
				final int kk = k;
				boolean royal = r.pots().stream().anyMatch(pot -> pot.winners().contains(kk) && pot.value() != 0
					&& "royal_flush".equals(HandEvaluator.handName(pot.value())));
				if (royal) {
					CasinoAdvancements.grant(server, uuid, "royal_flush");
				}
				if (sharkBusted) {
					CasinoAdvancements.grant(server, uuid, "shark_hunter");
				}
				// man_vs_machine: a pot won at showdown against a HARD bot still in it (bots module registers it).
				if (!r.uncontested() && r.pots().stream().anyMatch(pot -> pot.winners().contains(kk)
					&& pot.eligible().stream().anyMatch(e -> levelOf(seatOf[e]) == BotDifficulty.HARD))) {
					CasinoAdvancements.grant(server, uuid, "man_vs_machine");
				}
				if (botsOnly) {
					long swept = bustedBots.stream().filter(b -> levelOf(seatOf[b]) != BotDifficulty.EASY).count();
					if (swept > 0 && sweeps.merge(p.id, (int) swept, Integer::sum) >= 3) {
						CasinoAdvancements.grant(server, uuid, "clean_sweep");
					}
				}
			}
		}
		handQuips(h, seatOf);
		collectRake(r.rake()); // core: to the owner's bankroll at owned tables, else stays in the bank

		if (CasinoConfig.debug().logRounds) {
			Burmaldaholic.LOGGER.info("[round] poker at {}: hand #{} pots {} rake {}", worldPosition, table.handNo(),
				r.pots().stream().map(Hand.PotResult::amount).toList(), r.rake());
		}
		table.settleHand();
		drawn.clear();
		// Crash safety: the bots' settled stacks (bankroll bots: the world ledger returns them after a crash).
		TableBots tb = tableBots();
		for (PokerTable.Seat b : table.bots()) {
			tb.setStack(b.id, b.stack);
		}
		try {
			List<UUID> dealt = humans.stream().map(k -> uuid(h.player(k).id)).filter(java.util.Objects::nonNull).toList();
			PokerBotsUi.get().handPlayed(this, tb, dealt);
		} catch (RuntimeException e) {
			Burmaldaholic.LOGGER.warn("Poker: bots UI handPlayed failed", e);
		}
		setPhase("result");
		setChanged();
		startFinish(h);
		removeFinished();
		if (!humansSeated() && waiting.isEmpty()) {
			afterHumanLeft();
			return;
		}
	}

	private static @Nullable BotDifficulty levelOf(PokerTable.@Nullable Seat s) {
		return s == null || s.bot == null ? null : s.bot.level();
	}

	/**
	 * A bot event line (BOTS.md §7.4) through core's rate-limited chatter ({@link TableBots#say}).
	 * HOOK (bots UI, J-B2): chatter delivery lives behind TableBots.say / core BotChatter; if the bots UI's
	 * {@code BotChatter.event(level, pos, bot, event, human)} becomes the only route, call it here instead.
	 */
	private void quip(ServerLevel sl, BotProfile bot, String event, @Nullable String human) {
		try {
			tableBots().say(sl, bot, event, human);
		} catch (RuntimeException e) {
			Burmaldaholic.LOGGER.warn("Poker: bot quip failed", e);
		}
	}

	/** Bot quips after the hand (never about hidden cards before they were shown). */
	private void handQuips(Hand h, PokerTable.Seat[] seatOf) {
		if (!(level instanceof ServerLevel sl)) {
			return;
		}
		try {
			Hand.Result r = h.result();
			long big = 40 * h.bb();
			for (int k = 0; k < h.players().size(); k++) {
				BotProfile bot = seatOf[k] == null ? null : seatOf[k].bot;
				if (bot == null) {
					continue;
				}
				if (h.player(k).stack() <= 0) {
					quip(sl, bot, "bust", null);
					return;
				}
				if (r.net()[k] >= big) {
					quip(sl, bot, "win_big", null);
					return;
				}
			}
			for (int k = 0; k < h.players().size(); k++) {
				if (h.player(k).human && r.net()[k] >= big) {
					for (int j = 0; j < h.players().size(); j++) {
						if (!h.player(j).human && r.net()[j] < 0 && seatOf[j] != null && seatOf[j].bot != null) {
							quip(sl, seatOf[j].bot, "human_wins", seatOf[k] != null ? seatOf[k].name : "");
							return;
						}
					}
				}
			}
			// A bot folded to an all-in.
			boolean facingAllIn = false;
			for (Hand.Event e : h.events()) {
				if (!(e instanceof Hand.Acted a)) {
					continue;
				}
				if (a.allIn() && (a.type() == Hand.ActionType.BET || a.type() == Hand.ActionType.RAISE)) {
					facingAllIn = true;
				} else if (facingAllIn && a.type() == Hand.ActionType.FOLD && seatOf[a.player()] != null && seatOf[a.player()].bot != null) {
					quip(sl, seatOf[a.player()].bot, "fold_to_shove", null);
					return;
				}
			}
		} catch (RuntimeException e) {
			Burmaldaholic.LOGGER.warn("Poker: bot quip failed", e);
		}
	}

	/** Removes humans who stood up, disconnected, went broke or sat out too long (between hands). */
	private void removeFinished() {
		if (table == null || table.inHand()) {
			return;
		}
		for (PokerTable.Seat s : table.toRemove(cfg().sitOutHandsToRemove)) {
			cashOut(s.id);
		}
	}

	/** Removes a human seat and returns the stack to the balance (offline-safe). */
	private void cashOut(String id) {
		if (table == null) {
			return;
		}
		long amount = table.liveStack(id);
		PokerTable.Seat seat = table.removeSeat(id);
		if (seat == null) {
			return;
		}
		UUID uuid = UUID.fromString(id);
		SEATED.remove(uuid, this);
		droppedSeats.remove(id);
		drawn.remove(id);
		payOut(uuid, amount, seat.invested);
		firePlayerLeft(uuid, removing() ? LeaveReason.REMOVED : seat.disconnected ? LeaveReason.DISCONNECT : LeaveReason.LEFT);
		ServerPlayer p = online(id);
		if (p != null) {
			p.sendSystemMessage(PokerText.msg("removed", Texts.chips(amount)));
		}
		setChanged();
	}

	private void payOut(UUID player, long amount, long invested) {
		MinecraftServer server = server();
		if (server == null || amount <= 0) {
			return;
		}
		Economy eco = Economies.get();
		long back = Math.min(amount, Math.max(0, invested));
		if (back > 0) {
			eco.deposit(server, player, back, new Transaction(PokerModule.ID, "cash_out", Transaction.Kind.TRANSFER));
		}
		if (amount > back) {
			eco.deposit(server, player, amount - back, Transaction.payout(PokerModule.ID));
		}
	}

	/**
	 * The last human left (and no claimant waits): the table closes. A hand still running between bots only is
	 * undone (bots keep their start stacks), then every bot leaves (stacks back to their purses) and the
	 * session ends.
	 */
	private void afterHumanLeft() {
		if (table == null || humansSeated()) {
			return;
		}
		if (!waiting.isEmpty()) {
			// a claimant still waits: the next safe point seats it (or refunds a lapsed claim)
			if (!table.inHand()) {
				scheduleHand(20);
			}
			return;
		}
		if (table.inHand()) {
			table.abortHand();
		}
		cancelTimer("next_hand");
		cancelTimer("action");
		cancelTimer("bot");
		cancelTimer("auto");
		cancelTimer("beat");
		stageDone = true;
		botSeq++;
		botTurn = null;
		drawn.clear();
		endBotSession();
		table = null;
		stake = null;
		log.clear();
		lastResult.clear();
		sweeps.clear();
		setPhase("idle");
		setChanged();
	}

	/** Every bot leaves (stacks back to their purses), host invites expire, defaults restored. */
	private void endBotSession() {
		if (bots == null || !(level instanceof ServerLevel sl)) {
			return;
		}
		try {
			bots.endSession(sl);
		} catch (RuntimeException e) {
			Burmaldaholic.LOGGER.error("Poker table {}: bots session end failed", worldPosition, e);
		}
		try {
			PokerBotsUi.get().sessionEnded(this);
		} catch (RuntimeException e) {
			Burmaldaholic.LOGGER.warn("Poker: bots UI sessionEnded failed", e);
		}
	}

	/** Game disabled / internal error: cancel the running hand and pay everybody out (claimants too). */
	private void shutdown() {
		if (table == null) {
			return;
		}
		table.abortHand();
		for (PokerTable.Seat s : table.humans()) {
			cashOut(s.id);
		}
		for (String id : List.copyOf(waiting.keySet())) {
			refundWaiting(id);
		}
		afterHumanLeft();
	}

	// ---- ticking ----------------------------------------------------------------------------------

	@Override
	protected void serverTick(ServerLevel serverLevel) {
		MinecraftServer server = serverLevel.getServer();
		if (!refundsLoaded) {
			refundsLoaded = true;
			if (!pendingRefunds.isEmpty()) {
				for (Map.Entry<String, Long> e : pendingRefunds.entrySet()) {
					try {
						UUID uuid = UUID.fromString(e.getKey());
						Burmaldaholic.LOGGER.info("Poker table {}: returning {} chips to {} (table reloaded)", worldPosition, e.getValue(), uuid);
						Economies.get().deposit(server, uuid, e.getValue(), Transaction.refund(PokerModule.ID));
						ServerPlayer p = server.getPlayerList().getPlayer(uuid);
						if (p != null) {
							p.sendSystemMessage(PokerText.msg("removed", Texts.chips(e.getValue())));
						}
					} catch (IllegalArgumentException ignored) {
						// not a player id
					}
				}
				pendingRefunds.clear();
				stake = null;
				setChanged();
			}
		}
		if (table == null || server.getTickCount() % 20 != 0) {
			return;
		}
		if (!CasinoMode.isEnabled(serverLevel)) {
			// §4.1 CHANGED (review M1): the hand in play is played out (like a stopped table), never aborted.
			playOutNow("casino mode off");
			shutdown();
			syncViewers();
			return;
		}
		boolean changed = false;
		for (PokerTable.Seat s : table.humans()) {
			if (table == null) {
				break;
			}
			ServerPlayer p = online(s.id);
			if (p != null && !p.isRemoved() && s.disconnected && droppedSeats.remove(s.id)) {
				// Review M1: a player who rejoins mid-hand gets the seat back (no forced fold / cash-out);
				// the stack is only ever paid by cashOut (offline-safe), never lost or overwritten.
				s.disconnected = false;
				s.leaving = false;
				changed = true;
				continue;
			}
			if ((p == null || p.isRemoved()) && !s.disconnected) {
				s.disconnected = true;
				if (!s.leaving) {
					droppedSeats.add(s.id);
				}
				s.leaving = true;
				changed = true;
				Hand h = table.hand();
				if (table.inHand() && h.toAct() >= 0 && h.player(h.toAct()).id.equals(s.id)) {
					apply(Hand.Action.fold());
				}
			}
		}
		if (changed && table != null && !table.inHand()) {
			removeFinished();
			afterHumanLeft();
		}
		if (changed) {
			syncViewers();
		}
	}

	/**
	 * Table broken (review B1): the running hand is played out now — every human acts by the timeout rule
	 * (check, else fold; all-in players are already committed), bots decide as usual (bot rng), the board
	 * runs out and the pots are paid — then everybody is cashed out, waiting claimants are refunded and every
	 * bot leaves with its settled stack (BOTS.md §3.5). No abort: breaking a table never returns chips already
	 * in a pot. The same runs when the table's chunk unloads, the server stops or casino mode turns off
	 * (review M1, core {@code playOutNow}); only a disabled game / internal error aborts the hand.
	 */
	@Override
	protected void playOutForRemoval(ServerLevel level) {
		if (table == null) {
			for (String id : List.copyOf(waiting.keySet())) {
				refundWaiting(id);
			}
			return;
		}
		cancelTimer("next_hand");
		cancelTimer("action");
		cancelTimer("bot");
		cancelTimer("auto");
		botSeq++;
		botTurn = null;
		Hand h = table.hand();
		for (int guard = 0; guard < 400 && h != null && !h.complete(); guard++) {
			Hand.Action a;
			try {
				a = leaveAction(h, h.toAct());
			} catch (RuntimeException e) {
				a = h.legal().canCheck() ? Hand.Action.check() : Hand.Action.fold();
			}
			try {
				h.apply(h.coerce(a));
			} catch (IllegalStateException e) {
				h.apply(h.legal().canCheck() ? Hand.Action.check() : Hand.Action.fold());
			}
		}
		if (h != null && h.complete()) {
			streamEvents();
			endHand();
		}
		if (table != null && !stageDone) {
			// nobody is left to watch: post the result now
			stageDone = true;
			cancelTimer("beat");
			Hand last = table.lastHand();
			if (last != null && last.result() != null) {
				streamRemaining(last);
				for (Component line : lastResult) {
					broadcast(line);
				}
			}
		}
		if (table != null) {
			for (PokerTable.Seat s : table.humans()) {
				cashOut(s.id);
			}
			for (String id : List.copyOf(waiting.keySet())) {
				refundWaiting(id);
			}
			afterHumanLeft();
		}
	}

	/** A table with seats (humans) or waiting claimants is in play even without core stakes: {@code playOutNow} must run (review M1). */
	@Override
	protected boolean hasRoundInPlay() {
		return table != null || !waiting.isEmpty();
	}

	/**
	 * Seated at (or waiting for a seat at) any poker table: busy for PvP invites / lobbies (PVP.md eligibility
	 * rule 4). Registered with the PvP engine's busy checks ({@code PokerModule}).
	 */
	public static boolean isSeatedAnywhere(MinecraftServer server, UUID player) {
		PokerTableBlockEntity t = SEATED.get(player);
		return t != null && !t.isRemoved() && t.holds(player.toString());
	}

	@Override
	public void setRemoved() {
		super.setRemoved();
		SEATED.values().removeIf(t -> t == this);
	}

	// ---- public tag (in-world renderer, J-C10) ------------------------------------------------------------

	private static final String PUB_KEY = "burmaldaholic_poker_pub";

	/** The table's public state (no hole card that is not face up, no board card before its slide beat). */
	public PokerPub buildPub() {
		if (table == null) {
			return PokerPub.EMPTY;
		}
		int n = table.size();
		int[] flags = new int[n];
		long[] bets = new long[n];
		int[] cards = new int[n * 2];
		Arrays.fill(cards, -1);
		Hand h = table.hand() != null ? table.hand() : table.lastHand();
		boolean live = table.inHand();
		Hand.Result result = h != null && !live ? h.result() : null;
		int[] handSeats = table.handSeats();
		int[] board = new int[0];
		long pot = 0;
		if (h != null) {
			int vb = visibleBoard(h);
			board = new int[vb];
			for (int i = 0; i < vb; i++) {
				board[i] = h.board().get(i);
			}
			pot = live ? h.potTotal() : result != null && stageRunning() ? potOf(result) : 0;
		}
		for (int i = 0; i < n; i++) {
			PokerTable.Seat s = table.seat(i);
			if (s == null) {
				continue;
			}
			flags[i] = PokerPub.SEATED | (s.human ? 0 : PokerPub.BOT);
			int k = h != null ? h.indexOf(s.id) : -1;
			if (k < 0 || !(live || (result != null && handSeatsContain(handSeats, i, k)))) {
				continue;
			}
			Hand.Player p = h.player(k);
			flags[i] |= PokerPub.DEALT | (p.folded() ? PokerPub.FOLDED : 0) | (p.allIn() ? PokerPub.ALL_IN : 0);
			if (live && !stageRunning() && h.toAct() == k) {
				flags[i] |= PokerPub.ACTING;
			}
			bets[i] = live ? p.bet() : 0;
			if (result != null && shownNow(result, k)) {
				cards[i * 2] = p.hole()[0];
				cards[i * 2 + 1] = p.hole()[1];
			}
			if (result != null && finishStarted(PokerBeats.AWARD) > 0 && result.won()[k] > 0) {
				flags[i] |= PokerPub.WINNER;
			}
		}
		int button = h != null && h.button() < handSeats.length ? handSeats[h.button()] : -1;
		int seed = SeedMix.mix(SeedMix.mixLong(worldPosition.asLong()), table.handNo(), stageSeq);
		return new PokerPub(stageSeq, PokerPub.kindCode(stageKind), stageStart, stageArgs, seed, button, board, pot, flags, bets, cards);
	}

	/** Client: the last public state received with a block update. */
	public PokerPub clientPub() {
		return PokerPub.decode(clientPub.getIntArray("d").orElse(new int[0]));
	}

	@Override
	public void syncViewers() {
		super.syncViewers();
		publishPub();
	}

	/** Sends a block update when the public state changed (≤ 1 per beat, never per tick). */
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

	// ---- persistence ------------------------------------------------------------------------------

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		Map<String, Long> refunds = new HashMap<>(pendingRefunds);
		if (table != null) {
			for (PokerTable.Seat s : table.humans()) {
				// during a hand: the drawn outcome (both sides from one play-out); between hands: the stack
				long v = table.inHand() && table.handIndexOf(s) >= 0 ? drawn.getOrDefault(s.id, table.refundableStack(s.id)) : s.stack;
				if (v > 0) {
					refunds.merge(s.id, v, Long::sum);
				}
			}
		}
		waiting.forEach((id, amount) -> {
			if (amount > 0) {
				refunds.merge(id, amount, Long::sum);
			}
		});
		if (!refunds.isEmpty()) {
			output.store(REFUND_KEY, REFUND_CODEC, refunds);
		}
		if (stake != null) {
			output.putString(STAKE_KEY, stake.id());
		}
		CompoundTag botsTag = new CompoundTag();
		if (bots != null) {
			bots.save(botsTag);
		} else if (savedBots != null) {
			botsTag = savedBots.copy();
		}
		if (!botsTag.isEmpty()) {
			output.store(BOTS_KEY, CompoundTag.CODEC, botsTag);
		}
	}

	@Override
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		pendingRefunds.clear();
		input.read(REFUND_KEY, REFUND_CODEC).ifPresent(pendingRefunds::putAll);
		stake = StakeLevel.byId(input.getStringOr(STAKE_KEY, ""));
		refundsLoaded = false;
		savedBots = input.read(BOTS_KEY, CompoundTag.CODEC).orElse(null);
		input.read(PUB_KEY, CompoundTag.CODEC).ifPresent(t -> clientPub = t);
		if (bots != null && savedBots != null) {
			bots.load(savedBots);
			savedBots = null;
		}
	}

	// ---- client state -----------------------------------------------------------------------------

	private static Tag encode(RegistryOps<Tag> ops, Component c) {
		return ComponentSerialization.CODEC.encodeStart(ops, c).result().orElseGet(CompoundTag::new);
	}

	@Override
	public CompoundTag writeClientState(ServerPlayer viewer) {
		CompoundTag tag = baseState(viewer);
		RegistryOps<Tag> ops = viewer.level().registryAccess().createSerializationContext(NbtOps.INSTANCE);
		String me = id(viewer);
		int tier = vipTier(viewer);
		long balance = Economies.get().balance(viewer);
		tag.putBoolean("enabled", cfg().enabled);
		tag.putBoolean("seated", false);
		tag.putBoolean("waiting_seat", waiting.containsKey(me));
		tag.putString("stake", stake == null || !humansSeated() ? "" : stake.id());
		tag.putInt("vip", tier);
		tag.putLong("rake_permille", Math.round(cfg().rakePercent * 1000));
		ListTag levels = new ListTag();
		for (StakeLevel l : StakeLevel.values()) {
			CompoundTag lt = new CompoundTag();
			long bb = bbFor(l);
			long[] range = StakeLevel.buyInRange(bb, cfg().minBuyInBb, cfg().maxBuyInBb, balance, 0);
			lt.putString("id", l.id());
			lt.putLong("bb", bb);
			lt.putLong("sb", StakeLevel.smallBlind(bb));
			lt.putInt("min_tier", l.minTier());
			lt.putLong("buy_min", range[0]);
			lt.putLong("buy_max", range[1]);
			lt.putLong("full_min", (long) Math.min(cfg().minBuyInBb, cfg().maxBuyInBb) * bb);
			lt.putLong("full_max", (long) Math.max(cfg().minBuyInBb, cfg().maxBuyInBb) * bb);
			lt.putLong("rake_cap", (long) cfg().rakeCapBb * bb);
			levels.add(lt);
		}
		tag.put("levels", levels);
		if (table == null) {
			tag.putInt("table_size", cfg().maxSeats);
			return tag;
		}
		tag.putInt("table_size", table.size());
		tag.putLong("bb", table.bb());
		tag.putLong("sb", table.sb());
		tag.putInt("hand_no", table.handNo());
		tag.putInt("button", table.button());
		PokerTable.Seat mySeat = table.seatOf(me);
		tag.putBoolean("seated", mySeat != null);
		if (mySeat != null) {
			tag.putBoolean("sitting_out", mySeat.sittingOut);
			tag.putBoolean("leaving", mySeat.leaving);
			tag.putBoolean("can_top_up", table.canTopUp(me));
			long[] top = StakeLevel.buyInRange(table.bb(), cfg().minBuyInBb, cfg().maxBuyInBb, balance, mySeat.stack);
			tag.putLong("top_max", top[1]);
		}
		Hand h = table.hand() != null ? table.hand() : table.lastHand();
		boolean live = table.inHand();
		int[] handSeats = table.handSeats();
		Hand.Result result = h != null && !live ? h.result() : null;
		// the finish segment still shows the showdown: results, stacks and chat lines wait for its gate
		boolean presenting = result != null && stageRunning() && "finish".equals(stageKind);
		boolean gated = result != null && !presenting;
		tag.putBoolean("live", live);
		tag.putBoolean("presenting", presenting);
		tag.putIntArray("hand_seats", handSeats);
		writeStage(tag);
		int[] visibleBoard = new int[0];
		if (h != null) {
			int vb = visibleBoard(h);
			visibleBoard = new int[vb];
			for (int i = 0; i < vb; i++) {
				visibleBoard[i] = h.board().get(i);
			}
			tag.putString("street", live ? streetId(vb) : (result != null && !result.uncontested() ? "showdown" : ""));
			tag.putIntArray("board", visibleBoard);
			boolean turnOpen = live && !stageRunning();
			tag.putInt("to_act", turnOpen && h.toAct() >= 0 ? handSeats[h.toAct()] : -1);
			tag.putLongArray("pots", live ? h.displayPots().stream().mapToLong(Long::longValue).toArray() : new long[0]);
			tag.putLong("pot_total", live ? h.potTotal() : presenting ? potOf(result) : 0);
			tag.putInt("button_index", h.button());
			writeLastAction(tag, h, handSeats);
			if (presenting || gated) {
				writeAwards(tag, h, result, handSeats, visibleBoard);
			}
		}
		Map<Integer, Component> lastAct = new HashMap<>();
		if (live) {
			for (Hand.Event e : h.events()) {
				if (e instanceof Hand.Dealt) {
					lastAct.clear();
				} else {
					lastAct.put(e instanceof Hand.Blind b ? b.player() : ((Hand.Acted) e).player(), PokerText.shortAction(h, e));
				}
			}
		}
		ListTag seats = new ListTag();
		for (int i = 0; i < table.size(); i++) {
			PokerTable.Seat s = table.seat(i);
			if (s == null) {
				continue;
			}
			CompoundTag st = new CompoundTag();
			st.putInt("index", i);
			st.put("name", encode(ops, PokerText.seatName(s)));
			st.putBoolean("bot", !s.human);
			if (s.bot != null) {
				st.putString("level", s.bot.level().id());
			}
			st.putBoolean("you", s.id.equals(me));
			st.putBoolean("out", s.sittingOut);
			if (s.human) {
				st.putString("uuid", s.id);
			}
			int k = h != null ? h.indexOf(s.id) : -1;
			boolean dealt = k >= 0 && (live || (result != null && handSeatsContain(handSeats, i, k)));
			if (dealt) {
				Hand.Player p = h.player(k);
				// before the gate the plate shows the stack without this hand's winnings (the pot is still sliding)
				long stack = live ? p.stack() : presenting ? Math.max(0, p.stack() - result.won()[k] - uncalledTo(result, k)) : s.stack;
				st.putLong("stack", stack);
				st.putLong("bet", live ? p.bet() : 0);
				st.putBoolean("folded", p.folded());
				st.putBoolean("all_in", p.allIn() && (live || presenting));
				st.putInt("hand_index", k);
				if (lastAct.containsKey(k)) {
					st.put("act", encode(ops, lastAct.get(k)));
				}
				boolean show = s.id.equals(me) || (result != null && shownNow(result, k));
				if (show) {
					st.putIntArray("cards", p.hole());
					if (visibleBoard.length >= 3 && !p.folded()) {
						// the hand name from the cards on the felt only (a slow run-out never names the river early)
						int[] cards = new int[2 + visibleBoard.length];
						cards[0] = p.hole()[0];
						cards[1] = p.hole()[1];
						System.arraycopy(visibleBoard, 0, cards, 2, visibleBoard.length);
						int value = HandEvaluator.evaluate(cards, cards.length);
						st.put("hand", encode(ops, PokerText.handName(value)));
						st.putString("hand_id", HandEvaluator.handName(value));
						if (result != null && finishStarted(PokerBeats.BEST) > 0 && visibleBoard.length == 5 && !result.uncontested()) {
							st.putInt("best", BestFive.mask(p.hole(), visibleBoard));
						}
					}
				} else if (!p.folded()) {
					st.putInt("hidden", 2);
				}
				if (result != null && result.shown().contains(k) == false && !result.uncontested() && !p.folded() && finishStarted(PokerBeats.BEST) > 0) {
					st.putBoolean("mucked", true);
				}
				if (gated) {
					st.putLong("won", result.won()[k]);
					st.putLong("net", result.net()[k]);
				}
			} else {
				st.putLong("stack", s.stack);
			}
			seats.add(st);
		}
		tag.put("table", seats);
		if (live && !stageRunning() && h.toAct() >= 0 && h.player(h.toAct()).id.equals(me)) {
			Hand.Legal l = h.legal();
			Hand.Player p = h.player(h.toAct());
			CompoundTag lt = new CompoundTag();
			lt.putInt("seq", h.seq());
			lt.putLong("to_call", l.toCall());
			lt.putBoolean("can_check", l.canCheck());
			lt.putBoolean("can_raise", l.canRaise());
			lt.putLong("min_to", l.minRaiseTo());
			lt.putLong("max_to", l.maxRaiseTo());
			lt.putBoolean("is_bet", l.isBet());
			lt.putLong("my_bet", p.bet());
			lt.putLong("my_stack", p.stack());
			lt.putLong("current_bet", h.currentBet());
			tag.put("legal", lt);
		}
		ListTag logTag = new ListTag();
		for (Component c : log) {
			logTag.add(encode(ops, c));
		}
		tag.put("log", logTag);
		ListTag resultTag = new ListTag();
		if (!live && !presenting) {
			for (Component c : lastResult) {
				resultTag.add(encode(ops, c));
			}
		}
		tag.put("result", resultTag);
		return tag;
	}

	private static String streetId(int visibleBoard) {
		return switch (visibleBoard) {
			case 0 -> Hand.Street.PREFLOP.id();
			case 3 -> Hand.Street.FLOP.id();
			case 4 -> Hand.Street.TURN.id();
			default -> Hand.Street.RIVER.id();
		};
	}

	private static long potOf(Hand.Result r) {
		long t = 0;
		for (Hand.PotResult p : r.pots()) {
			t += p.amount();
		}
		return t;
	}

	private static long uncalledTo(Hand.Result r, int k) {
		return r.uncalled() != null && r.uncalled().player() == k ? r.uncalled().amount() : 0;
	}

	/** A hand is face up now: its show beat started, or the all-in exposure did (everybody in {@code shown} shows). */
	private boolean shownNow(Hand.Result r, int k) {
		int pos = r.shown().indexOf(k);
		if (pos < 0) {
			return false;
		}
		return pos < finishStarted(PokerBeats.SHOW) || finishStarted(PokerBeats.EXPOSE) > 0;
	}

	/** The running (or last) presentation segment: clients rebuild the same {@link PokerBeats} timeline from it. */
	private void writeStage(CompoundTag tag) {
		if (stageKind.isEmpty()) {
			return;
		}
		CompoundTag fx = new CompoundTag();
		fx.putInt("seq", stageSeq);
		fx.putString("kind", stageKind);
		fx.putLong("start", stageStart);
		fx.putIntArray("args", stageArgs);
		fx.putInt("seed", SeedMix.mix(SeedMix.mixLong(worldPosition.asLong()), table == null ? 0 : table.handNo(), stageSeq));
		fx.putBoolean("done", stageDone);
		fx.putString("moment", finishStarted(PokerBeats.AWARD) > 0 ? moment : "");
		tag.put("fx", fx);
	}

	/** The latest action of the hand (K12 tags): seat, type id, amount and its event number. */
	private static void writeLastAction(CompoundTag tag, Hand h, int[] handSeats) {
		List<Hand.Event> events = h.events();
		for (int i = events.size() - 1; i >= 0; i--) {
			Hand.Event e = events.get(i);
			int player;
			String type;
			long amount;
			boolean allIn;
			if (e instanceof Hand.Acted a) {
				player = a.player();
				type = a.type().id();
				amount = a.amount();
				allIn = a.allIn();
			} else if (e instanceof Hand.Blind b) {
				player = b.player();
				type = b.big() ? "big_blind" : "small_blind";
				amount = b.amount();
				allIn = b.allIn();
			} else {
				continue;
			}
			CompoundTag la = new CompoundTag();
			la.putInt("seat", player < handSeats.length ? handSeats[player] : -1);
			la.putString("type", type);
			la.putLong("amount", amount);
			la.putBoolean("all_in", allIn);
			la.putInt("n", i);
			tag.put("last_action", la);
			return;
		}
	}

	/**
	 * Pot awards of a finished hand once the first award beat started: side pots first (the order they slide), each
	 * with its winners' seats and shares.
	 */
	private void writeAwards(CompoundTag tag, Hand h, Hand.Result r, int[] handSeats, int[] visibleBoard) {
		if (finishStarted(PokerBeats.AWARD) <= 0) {
			return;
		}
		ListTag awards = new ListTag();
		List<Hand.PotResult> pots = r.pots();
		for (int i = pots.size() - 1; i >= 0; i--) {
			Hand.PotResult pot = pots.get(i);
			CompoundTag a = new CompoundTag();
			a.putInt("pot", i);
			long net = 0;
			int[] seats = new int[pot.winners().size()];
			long[] shares = new long[pot.winners().size()];
			for (int w = 0; w < seats.length; w++) {
				int k = pot.winners().get(w);
				seats[w] = k < handSeats.length ? handSeats[k] : -1;
				shares[w] = w < pot.shares().length ? pot.shares()[w] : 0;
				net += shares[w];
			}
			a.putLong("amount", net);
			a.putIntArray("seats", seats);
			a.putLongArray("shares", shares);
			if (pot.value() != 0 && visibleBoard.length == 5) {
				a.putString("hand_id", HandEvaluator.handName(pot.value()));
			}
			awards.add(a);
		}
		tag.put("awards", awards);
	}

	private static boolean handSeatsContain(int[] handSeats, int seat, int k) {
		return k < handSeats.length && handSeats[k] == seat;
	}
}
