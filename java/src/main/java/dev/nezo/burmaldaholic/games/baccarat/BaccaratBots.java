package dev.nezo.burmaldaholic.games.baccarat;

import dev.nezo.burmaldaholic.Burmaldaholic;
import dev.nezo.burmaldaholic.core.bots.BotNames;
import dev.nezo.burmaldaholic.core.bots.BotTable;
import dev.nezo.burmaldaholic.core.bots.TableBots;
import dev.nezo.burmaldaholic.core.bots.logic.BotProfile;
import dev.nezo.burmaldaholic.core.bots.logic.BotRole;
import dev.nezo.burmaldaholic.core.bots.logic.BotSettings;
import dev.nezo.burmaldaholic.core.bots.logic.BotSpeed;
import dev.nezo.burmaldaholic.core.bots.logic.OwnerControls;
import dev.nezo.burmaldaholic.core.bots.logic.Purse;
import dev.nezo.burmaldaholic.core.bots.logic.SeatOccupant;
import dev.nezo.burmaldaholic.core.bots.logic.SeatingMath;
import dev.nezo.burmaldaholic.core.config.CasinoConfig;
import dev.nezo.burmaldaholic.core.table.TableSeats;
import dev.nezo.burmaldaholic.core.text.Texts;
import dev.nezo.burmaldaholic.games.baccarat.logic.BaccaratBettor;
import dev.nezo.burmaldaholic.games.baccarat.logic.BetKind;
import dev.nezo.burmaldaholic.games.baccarat.logic.BotSeatMap;
import dev.nezo.burmaldaholic.games.baccarat.logic.ChemmyBotPolicy;
import dev.nezo.burmaldaholic.games.baccarat.logic.Slips;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.Nullable;

/**
 * Seats &amp; Bots wiring of one baccarat table (BOTS.md, pvp-bots.md §4.6); mirrors Bedrock
 * {@code games/baccarat/bots.ts}.
 *
 * <ul>
 *   <li>Chemin de fer: MONEY bots (game {@code chemmy}, yield rule CHEMMY_PUNTER_FIRST — a bot punter yields
 *       first, the bot banker only if no punter bot). Their chips live in the bank escrow like a human bank
 *       (core purses: BANK mints / sinks, BANKROLL is debited at sit-down and credited when the bot leaves);
 *       this class keeps each bot's split (free chips) and reports free + stake / bank to core for crash
 *       safety. Decisions: {@link ChemmyBotPolicy}. Safe point: after RESULT, before BANK_OFFER.</li>
 *   <li>Punto Banco (standard / High Roller): ATMOSPHERE bots (game {@code baccarat}, highest seat yields).
 *       Virtual bets from {@link BaccaratBettor} placed 60–160 t into BETTING; they never touch money and
 *       never delay a coup. Safe point: any time during BETTING.</li>
 * </ul>
 * In the table's own maps (bank, punts) a bot is keyed by a synthetic UUID ({@link #uuidOf}); bots are not
 * in core's {@link TableSeats} (humans only) — {@link BotSeatMap} gives them the seats humans do not hold.
 * The bots UI module (J-B2) is optional: reached through {@link Ui}, a no-op until registered.
 */
public final class BaccaratBots implements BotTable {
	/** Optional hooks of the bots UI module (settings screen, quips with emotes); every member has a no-op default. */
	public interface Ui {
		/** A table with bots was created (the UI may keep a handle for its settings screen). Default: core's {@code BotTableUi}. */
		default void attach(BotTable table, TableBots bots) {
			dev.nezo.burmaldaholic.core.bots.BotTableUi.get().attach(table, bots);
		}

		/** Delivers a quip itself; false = use the core chatter queue ({@link TableBots#say}). */
		default boolean quip(ServerLevel level, TableBots bots, BotProfile bot, String event, @Nullable String human) {
			return false;
		}
	}

	private static Ui ui = new Ui() {};

	/** Registers the bots UI hooks (bots module). */
	public static void setUi(@Nullable Ui hooks) {
		ui = hooks == null ? new Ui() {} : hooks;
	}

	/** The table's view of a chemmy bot's chips in play (stake / bank), provided by the block entity. */
	interface Held {
		long staked(UUID bot);

		long bank(UUID bot);

		/** A leaving bot (safe point only): removes its stake and bank from play and returns them. */
		long release(UUID bot);

		boolean banker(UUID bot);
	}

	private final BaccaratTableBlockEntity be;
	private final boolean chemmy;
	private @Nullable TableBots tb;
	private @Nullable CompoundTag pendingLoad;
	private final BotSeatMap seatMap = new BotSeatMap();
	private final Map<String, SeatOccupant.Bot> occ = new LinkedHashMap<>();
	private final Map<UUID, String> keyOf = new HashMap<>();
	/** Chips a money bot holds besides its stake / bank in play. */
	private final Map<String, Long> free = new HashMap<>();
	private final Map<String, ChemmyBotPolicy.Memory> memories = new HashMap<>();
	/** Atmosphere: this coup's virtual bets and when each bot places them. */
	private final Map<String, EnumMap<BetKind, Long>> virtual = new LinkedHashMap<>();
	private final Map<String, Long> atmoDue = new HashMap<>();
	private final List<UUID> joinOrder = new ArrayList<>();
	/** Chemmy: individual human punter stakes of the last 5 coups. */
	private final Deque<List<Long>> recent = new ArrayDeque<>();
	private boolean wasBetting;
	private long bettingStart;
	private boolean inSafePoint;
	private boolean attached;
	@Nullable Held held;

	BaccaratBots(BaccaratTableBlockEntity be, boolean chemmy) {
		this.be = be;
		this.chemmy = chemmy;
	}

	/** Synthetic key of a bot in the table's UUID maps (never a player's: name-based v3 in our namespace). */
	public static UUID uuidOf(String botKey) {
		return UUID.nameUUIDFromBytes(("burmaldaholic:" + botKey).getBytes(StandardCharsets.UTF_8));
	}

	/** Core bot state of this table (created on first use, when the level is known). */
	TableBots tb() {
		if (tb == null) {
			boolean worldgen = be.getLevel() != null && be.preset().isPresent();
			// generated tables: the worldgen preset's defaults (J-G6); name theme / level mix in the hooks below
			tb = new TableBots(this, dev.nezo.burmaldaholic.core.bots.BotPresets.defaults(be, botGameId(), worldgen),
				OwnerControls.unowned(botSeatCount()));
			if (pendingLoad != null) {
				tb.load(pendingLoad);
				pendingLoad = null;
			}
		}
		if (!attached && be.getLevel() != null) {
			attached = true;
			try {
				ui.attach(this, tb);
			} catch (RuntimeException e) {
				Burmaldaholic.LOGGER.error("bots ui attach failed", e);
			}
		}
		return tb;
	}

	/**
	 * Replaces the table's saved bot defaults (commands / GameTests; the settings UI goes through
	 * {@link TableBots#requestChange}). Ends a running bot session like a reload does.
	 */
	public void setDefaults(BotSettings settings) {
		CompoundTag t = new CompoundTag();
		tb().save(t);
		CompoundTag bots = t.getCompoundOrEmpty("bots");
		CompoundTag d = bots.getCompoundOrEmpty("defaults");
		d.putString("policy", settings.policy().name());
		d.putInt("count", settings.count());
		d.putString("difficulty", settings.difficulty().name());
		d.putBoolean("keepFree", settings.keepFree());
		d.putBoolean("chatter", settings.chatter());
		d.putString("speed", settings.speed().name());
		bots.put("defaults", d);
		bots.remove("session");
		t.put("bots", bots);
		tb().load(t);
	}

	/** Every chip the seated bots hold: free + stakes + a bank (tests: conservation). */
	public long chipsHeld() {
		long sum = 0;
		for (String k : occ.keySet()) {
			UUID id = uuidOf(k);
			sum += free.getOrDefault(k, 0L) + (held != null ? held.staked(id) + held.bank(id) : 0);
		}
		return sum;
	}

	void save(CompoundTag out) {
		if (tb != null) {
			tb.save(out);
		} else if (pendingLoad != null) {
			out.put("bots", pendingLoad.getCompoundOrEmpty("bots").copy());
		}
	}

	void load(CompoundTag in) {
		if (tb != null) {
			tb.load(in);
		} else {
			pendingLoad = in.copy();
		}
	}

	// ---- BotTable ---------------------------------------------------------------------------------

	/** This table's {@link TableBots} (the bots UI's settings screen finds tables through it; the BE delegates here). */
	@Override
	public @Nullable TableBots tableBots() {
		return be.getLevel() == null ? tb : tb();
	}

	/** The worldgen preset's level mix when present (J-G6). */
	@Override
	public int[] botDifficultyMix() {
		int[] mix = dev.nezo.burmaldaholic.core.bots.BotPresets.mix(be, botGameId());
		return mix != null ? mix : BotTable.super.botDifficultyMix();
	}

	/** The worldgen preset's name theme when present (J-G6). */
	@Override
	public dev.nezo.burmaldaholic.core.bots.logic.BotRoster.Theme botNameTheme() {
		return dev.nezo.burmaldaholic.core.bots.BotPresets.theme(be, botGameId(), BotTable.super.botNameTheme());
	}

	@Override
	public String botGameId() {
		return chemmy ? "chemmy" : "baccarat";
	}

	@Override
	public BotRole botRole() {
		return chemmy ? BotRole.MONEY : BotRole.ATMOSPHERE;
	}

	@Override
	public int botSeatCount() {
		return BaccaratModule.config().seats;
	}

	@Override
	public List<UUID> seatedHumans() {
		List<UUID> ids = new ArrayList<>();
		for (TableSeats.Seat s : be.seats().occupied()) {
			ids.add(s.player());
		}
		List<UUID> out = new ArrayList<>();
		for (UUID id : joinOrder) {
			if (ids.contains(id)) {
				out.add(id);
			}
		}
		for (UUID id : ids) {
			if (!out.contains(id)) {
				out.add(id);
			}
		}
		return out;
	}

	private List<Integer> humanSeats() {
		List<Integer> out = new ArrayList<>();
		for (TableSeats.Seat s : be.seats().occupied()) {
			out.add(s.index());
		}
		return out;
	}

	@Override
	public List<SeatOccupant> occupants() {
		int n = botSeatCount();
		List<SeatOccupant> out = new ArrayList<>(n);
		for (int i = 0; i < n; i++) {
			out.add(null);
		}
		for (TableSeats.Seat s : be.seats().occupied()) {
			if (s.index() >= 0 && s.index() < n) {
				out.set(s.index(), new SeatOccupant.Human(s.player(), s.name()));
			}
		}
		seatMap.resolve(humanSeats(), n);
		for (Map.Entry<String, SeatOccupant.Bot> e : occ.entrySet()) {
			OptionalInt seat = seatMap.seat(e.getKey());
			if (seat.isPresent() && seat.getAsInt() < n && out.get(seat.getAsInt()) == null) {
				out.set(seat.getAsInt(), e.getValue());
			}
		}
		return out;
	}

	@Override
	public boolean seatBot(SeatOccupant.Bot bot, long stack) {
		String key = bot.key();
		if (!seatMap.add(key, humanSeats(), botSeatCount())) {
			return false;
		}
		occ.put(key, bot);
		keyOf.put(uuidOf(key), key);
		free.put(key, chemmy ? Math.max(0, stack) : 0L);
		if (chemmy) {
			memories.put(key, ChemmyBotPolicy.newMemory(bot.profile().level(), tableMin(), tb().rng()));
		}
		be.setChanged();
		return true;
	}

	@Override
	public long unseatBot(String botKey) {
		UUID id = uuidOf(botKey);
		long extra = held != null && chemmy ? held.release(id) : 0;
		long holdings = free.getOrDefault(botKey, 0L) + extra;
		seatMap.remove(botKey);
		occ.remove(botKey);
		keyOf.remove(id);
		free.remove(botKey);
		memories.remove(botKey);
		virtual.remove(botKey);
		atmoDue.remove(botKey);
		be.setChanged();
		return chemmy ? holdings : 0;
	}

	@Override
	public SeatingMath.YieldRule yieldRule() {
		return chemmy ? SeatingMath.YieldRule.CHEMMY_PUNTER_FIRST : SeatingMath.YieldRule.HIGHEST_SEAT;
	}

	@Override
	public long botBuyIn() {
		return chemmy ? ChemmyBotPolicy.buyIn(bankCap(), minBank()) : 0;
	}

	@Override
	public boolean botDifficultyMatters() {
		return chemmy;
	}

	@Override
	public String botTableKey() {
		if (be.getLevel() != null) {
			BlockPos p = be.getBlockPos();
			return be.getLevel().dimension().identifier() + "@" + p.getX() + "," + p.getY() + "," + p.getZ();
		}
		return "baccarat@" + System.identityHashCode(be);
	}

	@Override
	public @Nullable BlockPos botTablePos() {
		return be.getBlockPos();
	}

	@Override
	public boolean botTableActive() {
		return !be.isRemoved();
	}

	@Override
	public boolean isBotBanker(String botKey) {
		return held != null && held.banker(uuidOf(botKey));
	}

	// ---- queries ------------------------------------------------------------------------------------

	boolean chemmy() {
		return chemmy;
	}

	boolean isBot(@Nullable UUID id) {
		return id != null && keyOf.containsKey(id);
	}

	public int count() {
		return occ.size();
	}

	@Nullable String keyOf(UUID id) {
		return keyOf.get(id);
	}

	@Nullable BotProfile profile(UUID id) {
		String k = keyOf.get(id);
		SeatOccupant.Bot b = k == null ? null : occ.get(k);
		return b == null ? null : b.profile();
	}

	Purse purse(UUID id) {
		String k = keyOf.get(id);
		SeatOccupant.Bot b = k == null ? null : occ.get(k);
		return b == null ? Purse.NONE : b.purse();
	}

	/** Funded by the bank (heat applies, debtors may punt against its bank). */
	boolean houseBot(UUID id) {
		return purse(id).houseFunded();
	}

	/** Bots in seat order: (seat index, synthetic id). */
	List<Map.Entry<Integer, UUID>> seated() {
		occupants();
		List<Map.Entry<Integer, UUID>> out = new ArrayList<>();
		for (String k : occ.keySet()) {
			OptionalInt s = seatMap.seat(k);
			if (s.isPresent()) {
				out.add(Map.entry(s.getAsInt(), uuidOf(k)));
			}
		}
		out.sort(Map.Entry.comparingByKey());
		return out;
	}

	Component display(UUID id) {
		BotProfile p = profile(id);
		return p == null ? Texts.raw("?") : BotNames.display(p);
	}

	/** Free chips (money bots). */
	long balance(UUID id) {
		String k = keyOf.get(id);
		return k == null ? 0 : free.getOrDefault(k, 0L);
	}

	long stackOf(UUID id) {
		return balance(id) + (held != null ? held.staked(id) : 0);
	}

	// ---- money (chemmy): chips stay in the bank escrow; only the bot's split changes ------------

	boolean take(UUID id, long amount) {
		String k = keyOf.get(id);
		Long f = k == null ? null : free.get(k);
		if (f == null || amount < 0 || f < amount) {
			return false;
		}
		free.put(k, f - amount);
		be.setChanged();
		return true;
	}

	void give(UUID id, long amount) {
		String k = keyOf.get(id);
		if (k == null || amount <= 0) {
			return;
		}
		free.merge(k, amount, Long::sum);
		be.setChanged();
	}

	/** Crash-safe holdings for core: stack = free + stake in play, bank escrow = the bank it holds. */
	void sync(UUID id) {
		String k = keyOf.get(id);
		if (k == null || tb == null) {
			return;
		}
		long staked = held != null ? held.staked(id) : 0;
		long bank = held != null ? held.bank(id) : 0;
		tb.setStack(k, free.getOrDefault(k, 0L) + staked);
		tb.setBankEscrow(k, bank);
	}

	void syncAll() {
		for (String k : occ.keySet()) {
			sync(uuidOf(k));
		}
	}

	ChemmyBotPolicy.Memory memory(UUID id) {
		String k = keyOf.get(id);
		BotProfile p = profile(id);
		return memories.computeIfAbsent(k == null ? "" : k,
			x -> ChemmyBotPolicy.newMemory(p == null ? dev.nezo.burmaldaholic.core.bots.logic.BotDifficulty.NORMAL : p.level(), tableMin(), tb().rng()));
	}

	// ---- chemmy facts --------------------------------------------------------------------------------

	long tableMin() {
		return Math.max(1, BaccaratModule.config().minBet);
	}

	long minBank() {
		return BaccaratModule.config().chemmy.minBank;
	}

	long bankCap() {
		return ChemmyBotPolicy.botBankCap(CasinoConfig.bots().chemmy.bankCapMultiple, tableMin(), minBank());
	}

	/** Records the human punter stakes of a settled coup (HARD bank size). */
	void recordStakes(List<Long> stakes) {
		if (stakes.isEmpty()) {
			return;
		}
		recent.addLast(List.copyOf(stakes));
		while (recent.size() > 5) {
			recent.removeFirst();
		}
	}

	ChemmyBotPolicy.Facts facts(@Nullable UUID bankerOrCandidate) {
		List<UUID> humans = seatedHumans();
		int punters = (int) humans.stream().filter(h -> !h.equals(bankerOrCandidate)).count();
		List<Long> r = new ArrayList<>();
		recent.forEach(r::addAll);
		return new ChemmyBotPolicy.Facts(tableMin(), minBank(), bankCap(), punters, humans.size(), r);
	}

	/** Bot think delay (bot rng), never more than half the human timer. */
	int think(UUID id, int humanTimer) {
		BotProfile p = profile(id);
		return p == null ? 0 : tb().thinkTicks(p, false, humanTimer);
	}

	/** Random punt offset before the deadline: bots bet in the last 100 t. */
	int puntOffset() {
		return tb().rng().between(ChemmyBotPolicy.PUNT_OFFSET_MIN, ChemmyBotPolicy.PUNT_OFFSET_MAX);
	}

	// ---- sessions / safe point -----------------------------------------------------------------------

	/** A human asks to sit (private tables, BOTS_ONLY, claimant). True = sit now; false = refused or claimant (already told). */
	boolean admit(ServerPlayer player) {
		var r = tb().admit(player);
		if (!r.isOk()) {
			be.sendError(player, r.error());
			return false;
		}
		return r.value();
	}

	void onJoin(UUID id) {
		if (!joinOrder.contains(id)) {
			joinOrder.add(id);
		}
	}

	void onLeave(UUID id) {
		joinOrder.remove(id);
	}

	/** Runs the table's safe point: pending settings apply, bots join / yield / leave, claimants sit. */
	void safePoint() {
		if (inSafePoint || !(be.getLevel() instanceof ServerLevel level)) {
			return;
		}
		inSafePoint = true;
		try {
			TableBots.SafePointResult r = tb().safePoint(level);
			tb().announce(level, r);
			if (!r.joined().isEmpty()) {
				quip(r.joined().getFirst().profile, "join", null);
			}
			for (UUID id : r.seatedClaimants()) {
				ServerPlayer p = level.getServer().getPlayerList().getPlayer(id);
				if (p != null && be.sit(p)) {
					be.sendStateTo(p);
				}
			}
			if (chemmy) {
				syncAll();
			}
		} catch (RuntimeException e) {
			Burmaldaholic.LOGGER.error("baccarat bots safe point failed at {}", be.getBlockPos(), e);
		} finally {
			inSafePoint = false;
		}
	}

	/** Table stop (break, unload, server stop, casino off): every bot leaves after the round settled. */
	void end() {
		if (tb == null || !(be.getLevel() instanceof ServerLevel level)) {
			return;
		}
		try {
			tb.endSession(level);
		} catch (RuntimeException e) {
			Burmaldaholic.LOGGER.error("baccarat bots endSession failed at {}", be.getBlockPos(), e);
		}
	}

	void quip(@Nullable UUID id, String event, @Nullable String human) {
		BotProfile p = id == null ? null : profile(id);
		if (p != null) {
			quip(p, event, human);
		}
	}

	private void quip(BotProfile p, String event, @Nullable String human) {
		if (!(be.getLevel() instanceof ServerLevel level) || tb == null) {
			return;
		}
		try {
			if (!ui.quip(level, tb, p, event, human)) {
				tb.say(level, p, event, human);
			}
		} catch (RuntimeException e) {
			Burmaldaholic.LOGGER.error("bot quip failed", e);
		}
	}

	// ---- atmosphere (house coups) ---------------------------------------------------------------------

	/** This coup's virtual bets (atmosphere). */
	Map<String, EnumMap<BetKind, Long>> virtualBets() {
		return virtual;
	}

	/** House table in BETTING: the bot whose virtual bet moment comes next (thinking dots), or null. */
	@Nullable String nextVirtualBettor(long now) {
		if (chemmy || !wasBetting || atmoDue.isEmpty()) {
			return null;
		}
		Map<String, Long> due = new LinkedHashMap<>();
		for (String key : occ.keySet()) {
			due.put(key, virtual.containsKey(key) ? -1L : atmoDue.getOrDefault(key, -1L));
		}
		return dev.nezo.burmaldaholic.core.bots.logic.ThinkingBot.next(due, now);
	}

	/** Every tick of a house table: virtual bets 60–160 t into BETTING; the safe point runs during BETTING. */
	void tickHouse(long now, boolean betting, Slips.Limits limits, List<Integer> beads, long coupNo) {
		if (chemmy) {
			return;
		}
		if (!betting) {
			wasBetting = false;
			return;
		}
		if (!wasBetting) {
			// BETTING opened: last coup's virtual bets vanish; a safe point for atmosphere tables.
			wasBetting = true;
			bettingStart = now;
			virtual.clear();
			atmoDue.clear();
			safePoint();
		} else if (tb != null && (tb.pending() != null || !tb.claimants().isEmpty())) {
			safePoint();
		}
		if (occ.isEmpty() || be.seats().isEmpty()) {
			return;
		}
		double factor = speedFactor();
		for (Map.Entry<String, SeatOccupant.Bot> e : occ.entrySet()) {
			String key = e.getKey();
			if (virtual.containsKey(key)) {
				continue;
			}
			Long due = atmoDue.get(key);
			if (due == null) {
				due = bettingStart + Math.round(tb().rng().between(BaccaratBettor.DELAY_MIN, BaccaratBettor.DELAY_MAX) * factor);
				atmoDue.put(key, due);
			}
			if (now < due) {
				continue;
			}
			EnumMap<BetKind, Long> slip;
			try {
				slip = BaccaratBettor.INSTANCE.act(e.getValue().profile(), new BaccaratBettor.View(limits, BaccaratBettor.lastSide(beads), coupNo), null,
					tb().rng());
			} catch (RuntimeException ex) {
				Burmaldaholic.LOGGER.error("baccarat bettor failed", ex);
				slip = new EnumMap<>(BetKind.class);
			}
			virtual.put(key, slip);
			be.syncViewers();
		}
	}

	private double speedFactor() {
		BotSpeed s = tb().settings().effectiveSpeed();
		return s == BotSpeed.INSTANT ? 0 : s == BotSpeed.FAST ? CasinoConfig.bots().think.fastFactor : 1;
	}
}
