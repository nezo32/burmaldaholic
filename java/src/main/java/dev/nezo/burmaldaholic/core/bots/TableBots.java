package dev.nezo.burmaldaholic.core.bots;

import dev.nezo.burmaldaholic.Burmaldaholic;
import dev.nezo.burmaldaholic.core.bots.logic.BotDifficulty;
import dev.nezo.burmaldaholic.core.bots.logic.BotProfile;
import dev.nezo.burmaldaholic.core.bots.logic.BotRng;
import dev.nezo.burmaldaholic.core.bots.logic.BotRole;
import dev.nezo.burmaldaholic.core.bots.logic.BotRoster;
import dev.nezo.burmaldaholic.core.bots.logic.BotSettings;
import dev.nezo.burmaldaholic.core.bots.logic.BotSpeed;
import dev.nezo.burmaldaholic.core.bots.logic.BotsMode;
import dev.nezo.burmaldaholic.core.bots.logic.HeatStage;
import dev.nezo.burmaldaholic.core.bots.logic.OwnerControls;
import dev.nezo.burmaldaholic.core.bots.logic.Personality;
import dev.nezo.burmaldaholic.core.bots.logic.Purse;
import dev.nezo.burmaldaholic.core.bots.logic.SeatAdmission;
import dev.nezo.burmaldaholic.core.bots.logic.SeatOccupant;
import dev.nezo.burmaldaholic.core.bots.logic.SeatPlan;
import dev.nezo.burmaldaholic.core.bots.logic.SeatPolicy;
import dev.nezo.burmaldaholic.core.bots.logic.SeatingMath;
import dev.nezo.burmaldaholic.core.bots.logic.SettingsChange;
import dev.nezo.burmaldaholic.core.bots.logic.TableAccess;
import dev.nezo.burmaldaholic.core.config.CasinoConfig;
import dev.nezo.burmaldaholic.core.config.sections.BotsConfig;
import dev.nezo.burmaldaholic.core.service.TableOwnershipProvider.OwnedTable;
import dev.nezo.burmaldaholic.core.text.Texts;
import dev.nezo.burmaldaholic.core.util.Result;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.Nullable;

/**
 * Per-table bot state, OWNED by a table block entity that implements {@link BotTable} (composition, so
 * {@code CasinoTableBlockEntity} stays unchanged). Holds the saved table defaults, owner/keeper limits,
 * access (private + invites) and the live session (host, pending settings, seated bots with stacks and
 * purses, claimants, bot rng). Persist with {@link #save}/{@link #load} inside the BE's
 * saveAdditional/loadAdditional (format: pvp-bots.md §5.3). Server thread.
 *
 * <p>Game integration contract:
 * <ol>
 *   <li>construct in the BE constructor: {@code bots = new TableBots(this, TableBots.defaultsFor("poker"), OwnerControls.unowned(seats))};</li>
 *   <li>on every "a human wants to sit" call {@link #admit} (private tables, BOTS_ONLY, claimant);</li>
 *   <li>at the game's safe point call {@link #safePoint} — it applies pending settings, picks the host,
 *       makes bots leave / join through the {@link BotTable} hooks, funds / returns purses and reports
 *       the claimants the game must now seat (in the seats the leaving bots freed); announce with {@link #announce};</li>
 *   <li>keep money bots' chips current with {@link #setStack} / {@link #setBankEscrow} (crash safety);</li>
 *   <li>for every bot decision: build the game's view, call the game's {@code BotPolicy.act} after
 *       {@link #thinkTicks} using {@link #rng()} — never the game rng;</li>
 *   <li>on table stop (break, unload, server stop, casino off) after the round settled: {@link #endSession}.</li>
 * </ol>
 *
 * <p>Messages: this class tells players about table events it decides ({@code seat_after_round},
 * {@code seat_ready}, {@code host_now}, {@code settings_pending}, {@code let_in_request}, heat stages,
 * {@code regulars_gone}, {@code none_available}, {@code session_ended}); acknowledgements of UI actions
 * (invites sent, errors shown on screens) are the bots module's job, from the returned {@link Result}s.
 */
public final class TableBots {
	/** A seated bot's persistent state (BOTS.md §6.1). */
	public static final class SeatedBot {
		public final BotProfile profile;
		public final Purse purse;
		/** Chips in front of the bot (money bots), kept current by the game via {@link TableBots#setStack}. */
		public long stack;
		/** Chemin de fer bank the bot holds (money), via {@link TableBots#setBankEscrow}. */
		public long bankEscrow;
		/** Increasing per join in the session (PvP-style "last joined" yield rule). */
		public int joinOrder;

		public SeatedBot(BotProfile profile, Purse purse, long stack) {
			this.profile = profile;
			this.purse = purse;
			this.stack = stack;
		}

		public String key() {
			return profile.key();
		}

		/** Everything the bot holds (goes back to its purse when it leaves). */
		public long held() {
			return Math.max(0, stack) + Math.max(0, bankEscrow);
		}
	}

	/** What happened at a safe point (for the game's announcements / chatter). */
	public record SafePointResult(List<SeatedBot> joined, List<SeatedBot> left, List<UUID> seatedClaimants, boolean settingsApplied) {}

	private final BotTable table;
	private BotSettings defaults;
	private boolean defaultPrivate;
	private OwnerControls limits;
	private boolean limitsSet;
	private @Nullable UUID keeper;
	private final TableAccess access = new TableAccess();
	private @Nullable BotSettings session;
	private @Nullable BotSettings pending;
	private @Nullable UUID host;
	private final List<SeatedBot> bots = new ArrayList<>();
	private final List<UUID> claimants = new ArrayList<>();
	private @Nullable BotRng rng;
	private int joinCounter;
	private boolean orphansChecked;
	private boolean bankrollShort;
	private final Set<String> said = new HashSet<>();
	private @Nullable MinecraftServer server;

	public TableBots(BotTable table, BotSettings defaults, OwnerControls limits) {
		this.table = Objects.requireNonNull(table);
		this.defaults = Objects.requireNonNull(defaults);
		this.limits = Objects.requireNonNull(limits);
	}

	/** Craftable-table defaults of a game from {@code bots.table.<game>.*} (BOTS.md §2.3, §9.2). */
	public static BotSettings defaultsFor(String gameId) {
		return defaultsFor(gameId, false);
	}

	/** Defaults of a craftable ({@code worldgen} false) or generated table. Unknown game → HUMANS_ONLY. */
	public static BotSettings defaultsFor(String gameId, boolean worldgen) {
		var b = CasinoConfig.bots();
		BotsConfig.TableDefaults t = b.table.get(gameId);
		if (t == null) {
			return BotSettings.HUMANS_ONLY;
		}
		SeatPolicy policy = worldgen ? t.worldgenPolicy : t.policy;
		if (worldgen && policy == SeatPolicy.BOTS_ONLY) {
			policy = SeatPolicy.MIXED;
		}
		return new BotSettings(policy, worldgen ? t.worldgenCount : t.count, t.difficulty, b.keepFreeSeatDefault, true, BotSpeed.NORMAL);
	}

	// ---- state ---------------------------------------------------------------------------------------

	/** Stable table key ({@link BotTable#botTableKey}). */
	public String key() {
		return table.botTableKey();
	}

	boolean tableActive() {
		return table.botTableActive();
	}

	/** Effective settings of the running session (defaults outside a session). */
	public BotSettings settings() {
		return session != null ? session : defaults;
	}

	public @Nullable BotSettings pending() {
		return pending;
	}

	public boolean inSession() {
		return session != null;
	}

	public BotSettings defaults() {
		return defaults;
	}

	/** The stored owner / keeper limits (see {@link #effectiveLimits} for what applies). */
	public OwnerControls limits() {
		return limits;
	}

	/** Limits that apply now (§6.2): unowned → Bots always Allowed; owned → the owner's, or owned defaults until set. */
	public OwnerControls effectiveLimits(ServerLevel level) {
		return BotPurses.effectiveControls(ownership(level), limits, limitsSet, table.botSeatCount());
	}

	public TableAccess access() {
		return access;
	}

	public boolean defaultPrivate() {
		return defaultPrivate;
	}

	public @Nullable UUID keeper() {
		return keeper;
	}

	public void setKeeper(@Nullable UUID keeper) {
		this.keeper = keeper;
	}

	public @Nullable UUID host() {
		return host;
	}

	public List<SeatedBot> bots() {
		return List.copyOf(bots);
	}

	public @Nullable SeatedBot bot(String botKey) {
		for (SeatedBot b : bots) {
			if (b.key().equals(botKey)) {
				return b;
			}
		}
		return null;
	}

	/** Humans waiting for a bot's seat, in claim order. */
	public List<UUID> claimants() {
		return List.copyOf(claimants);
	}

	/** At the last safe point an owned table's bankroll could not fund a wanted bot (charter {@code bankroll_short}). */
	public boolean bankrollShort() {
		return bankrollShort;
	}

	/** Bot keys that will leave at the next safe point to seat the claimants, in claim order (seat plates). */
	public List<String> yieldingBots() {
		List<SeatPlan.Bot> in = planBots(false);
		List<String> order = SeatingMath.yieldOrder(table.yieldRule(),
			in.stream().map(b -> new SeatingMath.YieldCandidate(b.key(), b.seat(), b.stack(), b.joinOrder(), b.banker(),
				b.handsSinceBigBlind())).toList());
		int free = (int) table.occupants().stream().filter(Objects::isNull).count();
		int need = Math.max(0, claimants.size() - free);
		return List.copyOf(order.subList(0, Math.min(need, order.size())));
	}

	/** The session's bot random stream (created lazily at session start, BOTS.md §4.1). */
	public BotRng rng() {
		if (rng == null) {
			rng = Bots.newRng();
		}
		return rng;
	}

	/** Updates what a money bot has in front of it (after each hand / coup). */
	public void setStack(String botKey, long stack) {
		SeatedBot b = bot(botKey);
		if (b != null) {
			b.stack = Math.max(0, stack);
			syncEscrow(b);
		}
	}

	/** Updates the bank a chemin de fer bot holds. */
	public void setBankEscrow(String botKey, long bank) {
		SeatedBot b = bot(botKey);
		if (b != null) {
			b.bankEscrow = Math.max(0, bank);
			syncEscrow(b);
		}
	}

	// ---- admission ------------------------------------------------------------------------------------

	/**
	 * A human uses the table. Returns ok(true) = sit now, ok(false) = claimant (seat after the round,
	 * {@code …bots.seat_after_round} already told), or fail with {@code …bots.error.private_table} /
	 * {@code …bots_only_table} / {@code gui.burmaldaholic.error.table_full}.
	 */
	public Result<Boolean> admit(ServerPlayer player) {
		ServerLevel level = (ServerLevel) player.level();
		server = level.getServer();
		UUID id = player.getUUID();
		List<UUID> humans = table.seatedHumans();
		claimants.removeIf(humans::contains);
		List<SeatOccupant> occ = table.occupants();
		int empty = 0;
		int botSeats = 0;
		for (SeatOccupant o : occ) {
			if (o == null) {
				empty++;
			} else if (o.isBot()) {
				botSeats++;
			}
		}
		int claimsOnBots = Math.max(0, claimants.size() - empty);
		int free = Math.max(0, empty - claimants.size());
		boolean accessOk = !CasinoConfig.bots().privateTables.enabled || access.mayJoin(id, isOperator(player));
		SeatAdmission.Verdict v = SeatAdmission.admit(humans.contains(id), claimants.contains(id), accessOk, settings().policy(),
			id.equals(host), humans.size(), free, botSeats - claimsOnBots);
		switch (v) {
			case SIT -> {
				if (session == null) {
					startSession();
				}
				if (host == null || humans.isEmpty() || id.equals(keeper)) {
					host = id; // first to sit at the empty table, or the keeper (the next safe point re-checks)
				}
				return Result.ok(true);
			}
			case CLAIMANT -> {
				if (!claimants.contains(id)) {
					claimants.add(id);
					player.sendSystemMessage(Component.translatable("msg.burmaldaholic.bots.seat_after_round"));
				}
				return Result.ok(false);
			}
			case PRIVATE -> {
				return Result.fail(Component.translatable("gui.burmaldaholic.bots.error.private_table", nameOf(level, host != null ? host : keeper)));
			}
			case BOTS_ONLY -> {
				ServerPlayer h = host == null ? null : level.getServer().getPlayerList().getPlayer(host);
				if (h != null) {
					h.sendSystemMessage(Component.translatable("msg.burmaldaholic.bots.let_in_request", player.getDisplayName()));
				}
				return Result.fail(Component.translatable("gui.burmaldaholic.bots.error.bots_only_table", nameOf(level, host)));
			}
			default -> {
				return Result.fail(Component.translatable("gui.burmaldaholic.error.table_full"));
			}
		}
	}

	// ---- settings -------------------------------------------------------------------------------------

	/** The actor's strongest role at this table (§2.4). */
	public SettingsChange.Role roleOf(ServerPlayer actor) {
		UUID id = actor.getUUID();
		if (isOperator(actor)) {
			return SettingsChange.Role.OPERATOR;
		}
		Optional<OwnedTable> owned = ownership((ServerLevel) actor.level());
		if (owned.isPresent() && owned.get().owner().equals(id)) {
			return SettingsChange.Role.OWNER;
		}
		if (owned.isEmpty() && id.equals(keeper)) {
			return SettingsChange.Role.KEEPER;
		}
		if (id.equals(host) && table.seatedHumans().contains(id)) {
			return SettingsChange.Role.HOST;
		}
		return SettingsChange.Role.NONE;
	}

	/**
	 * Host / keeper / owner / op changes settings (BOTS.md §2.4). Stored as pending until the next safe
	 * point (the last change wins); {@code asDefaults} (keeper/owner/op) also saves them as the table defaults.
	 */
	public Result<BotSettings> requestChange(ServerPlayer actor, BotSettings wanted, boolean asDefaults) {
		ServerLevel level = (ServerLevel) actor.level();
		server = level.getServer();
		List<UUID> humans = table.seatedHumans();
		SettingsChange.Outcome o = SettingsChange.validate(roleOf(actor), humans.contains(actor.getUUID()), effectiveLimits(level), table.botRole(),
			Bots.enabled(), wanted, asDefaults, humans.size(), table.botSeatCount());
		if (!o.ok()) {
			return Result.fail(Component.translatable(o.error()));
		}
		if (o.asDefaults()) {
			defaults = o.settings();
		}
		if (session != null && !humans.isEmpty()) {
			pending = o.settings().equals(session) ? null : o.settings();
			if (pending != null) {
				tell(level, humans, Component.translatable("msg.burmaldaholic.bots.settings_pending", actor.getDisplayName(), summary(level, pending)));
			}
		}
		return Result.ok(o.settings());
	}

	/** Owner / keeper controls changed (charter, BOTS.md §6.2). Applies at the next safe point. */
	public void setLimits(OwnerControls limits) {
		this.limits = Objects.requireNonNull(limits);
		this.limitsSet = true;
	}

	/** {@code /casino bots clear}: this session's bots leave at the next safe point. */
	public void clear() {
		pending = settings().withPolicy(SeatPolicy.HUMANS_ONLY);
	}

	/**
	 * Private on/off (§2.5), at once. Switching on invites every seated human. {@code asDefault}
	 * (keeper / owner / op) also stores it as the table default.
	 */
	public Result<Boolean> setPrivate(ServerPlayer actor, boolean on, boolean asDefault) {
		ServerLevel level = (ServerLevel) actor.level();
		SettingsChange.Role role = roleOf(actor);
		String err = SettingsChange.validateAccess(role, table.seatedHumans().contains(actor.getUUID()), effectiveLimits(level),
			CasinoConfig.bots().privateTables.enabled, on);
		if (err != null) {
			return Result.fail(Component.translatable(err));
		}
		access.setPrivate(on, table.seatedHumans());
		if (asDefault && role.editsDefaults()) {
			defaultPrivate = on;
		}
		return Result.ok(on);
	}

	/**
	 * Invites a player (§2.5): saved as a guest when the keeper / owner / an op invites, else a session
	 * invite. Fails with {@code …error.invites_full}. Messages to the invitee are the bots module's job.
	 */
	public Result<Boolean> invite(ServerPlayer actor, UUID guest) {
		SettingsChange.Role role = roleOf(actor);
		String err = SettingsChange.validateAccess(role, table.seatedHumans().contains(actor.getUUID()), effectiveLimits((ServerLevel) actor.level()),
			CasinoConfig.bots().privateTables.enabled, false);
		if (err != null) {
			return Result.fail(Component.translatable(err));
		}
		int max = CasinoConfig.bots().privateTables.maxInvites;
		if (!access.invite(guest, role.editsDefaults(), max)) {
			return Result.fail(Component.translatable("gui.burmaldaholic.bots.error.invites_full", Texts.number(max)));
		}
		return Result.ok(role.editsDefaults());
	}

	/** Withdraws an invite; a seated guest keeps the seat until they leave (§2.4). */
	public Result<Boolean> uninvite(ServerPlayer actor, UUID guest) {
		SettingsChange.Role role = roleOf(actor);
		String err = SettingsChange.validateAccess(role, table.seatedHumans().contains(actor.getUUID()), effectiveLimits((ServerLevel) actor.level()),
			CasinoConfig.bots().privateTables.enabled, false);
		if (err != null) {
			return Result.fail(Component.translatable(err));
		}
		access.uninvite(guest);
		return Result.ok(true);
	}

	// ---- safe point -----------------------------------------------------------------------------------

	/** Call at the game's safe point (BOTS.md §3.1). */
	public SafePointResult safePoint(ServerLevel level) {
		server = level.getServer();
		MinecraftServer srv = level.getServer();
		if (!orphansChecked) {
			orphansChecked = true;
			if (bots.isEmpty()) {
				BotLedger.returnOrphans(srv, key()); // a crash / reload left chips of this table's old bots
			}
		}
		List<UUID> humans = table.seatedHumans();
		claimants.removeIf(humans::contains);
		pruneClaimants(level);
		boolean applied = false;
		if (humans.isEmpty() && claimants.isEmpty()) {
			boolean had = session != null;
			List<SeatedBot> left = removeAll(srv);
			resetSession();
			return new SafePointResult(List.of(), left, List.of(), had);
		}
		if (session == null) {
			startSession();
		}
		// BOTS_ONLY ends when its host leaves (§2.1): back to the defaults
		if (session.policy() == SeatPolicy.BOTS_ONLY && host != null && !humans.contains(host)) {
			session = defaults;
			pending = null;
			applied = true;
		}
		if (pending != null) {
			BotSettings p = pending;
			if (p.policy() == SeatPolicy.BOTS_ONLY && humans.size() > 1) {
				p = p.withPolicy(SeatPolicy.MIXED); // someone sat down meanwhile: never unseat a human (pillar 4)
			}
			applied = applied || !p.equals(session);
			session = p;
			pending = null;
			if (applied) {
				tell(level, humans, Component.translatable("msg.burmaldaholic.bots.settings_applied", summary(level, session)));
			}
		}
		updateHost(level, humans);

		Optional<OwnedTable> owned = ownership(level);
		OwnerControls eff = BotPurses.effectiveControls(owned, limits, limitsSet, table.botSeatCount());
		BotRole role = table.botRole();
		Purse purse = BotPurses.purseFor(owned, eff, role);
		// A money bot whose purse is no longer this table's (a charter linked / unlinked the table, Bots turned
		// off by the owner) leaves with what it holds, back to its own purse (review wave 2, m5, BOTS.md §5.1).
		List<SeatedBot> repursed = new ArrayList<>();
		if (role == BotRole.MONEY) {
			for (SeatedBot b : List.copyOf(bots)) {
				if (!b.purse.equals(purse)) {
					leave(srv, b);
					repursed.add(b);
				}
			}
		}
		boolean houseMoney = role == BotRole.MONEY && purse != null && purse.houseFunded();
		boolean sulk = false;
		boolean hardOnly = false;
		if (houseMoney) {
			for (UUID h : humans) {
				HeatStage st = BotLedger.stage(srv, h);
				if (st == HeatStage.SULKING) {
					sulk = true;
					announceOnce(level, humans, "sulk:" + h, Component.translatable("msg.burmaldaholic.bots.sulking", nameOf(level, h)), "sulk", h);
				} else if (st == HeatStage.HARD_ONLY && "poker".equals(table.botGameId())) {
					hardOnly = true;
					announceOnce(level, humans, "heat:" + h, Component.translatable("msg.burmaldaholic.bots.word_got_around", nameOf(level, h)),
						"word_got_around", h);
				}
			}
		}
		long buyIn = Math.max(0, table.botBuyIn());
		int affordable = role == BotRole.ATMOSPHERE ? Integer.MAX_VALUE : purse == null ? 0 : BotPurses.affordable(srv, purse, buyIn, key());
		SeatPlan.Input in = new SeatPlan.Input(settings(), Bots.enabled(), table.botSeatCount(), humans.size(), claimants, planBots(true),
			table.yieldRule(), eff, role, atmosphereCap(), Bots.activeBudgetLeft(), !bots.isEmpty() || Bots.tableSlotAvailable(), affordable,
			hardOnly, sulk, applied && !hardOnly && table.botDifficultyMatters() ? settings().difficulty() : null);
		SeatPlan.Plan plan = SeatPlan.plan(in);

		List<SeatedBot> left = new ArrayList<>(repursed);
		for (String k : plan.leave()) {
			SeatedBot b = bot(k);
			if (b != null) {
				leave(srv, b);
				left.add(b);
			}
		}
		for (String k : plan.yielded()) {
			SeatedBot b = findIn(left, k);
			int idx = plan.yielded().indexOf(k);
			if (b != null && idx < plan.seatedClaimants().size()) {
				say(level, b.profile, "yield", nameOf(level, plan.seatedClaimants().get(idx)).getString());
			}
		}
		List<UUID> seated = plan.seatedClaimants();
		for (UUID c : seated) {
			claimants.remove(c);
			ServerPlayer p = srv.getPlayerList().getPlayer(c);
			if (p != null) {
				p.sendSystemMessage(Component.translatable("msg.burmaldaholic.bots.seat_ready"));
			}
		}
		List<SeatedBot> joined = join(level, plan, purse, buyIn);
		bankrollShort = purse != null && purse.kind() == Purse.Kind.BANKROLL && (plan.limit() == SeatPlan.Limit.PURSE || joined.size() < plan.join());
		if (plan.limit() == SeatPlan.Limit.WORLD && bots.isEmpty()) {
			announceOnce(level, humans, "none_available", Component.translatable("msg.burmaldaholic.bots.none_available"), null, null);
		}
		if (plan.limit() == SeatPlan.Limit.PURSE && purse != null && purse.kind() == Purse.Kind.BANK && buyIn > 0) {
			announceOnce(level, humans, "regulars_gone", Component.translatable("msg.burmaldaholic.bots.regulars_gone"), null, null);
		}
		Bots.track(this);
		return new SafePointResult(joined, left, seated, applied);
	}

	/**
	 * Sends the standard join / leave lines for a safe point to the seated humans (one line for all joiners)
	 * and fires the bots' {@code join} / {@code leave} chatter events (BOTS.md §7.4; yields already said theirs).
	 */
	public void announce(ServerLevel level, SafePointResult r) {
		List<UUID> to = table.seatedHumans();
		if (r.joined().size() == 1) {
			tell(level, to, Component.translatable("msg.burmaldaholic.bots.joined", BotNames.display(r.joined().getFirst().profile)));
		} else if (r.joined().size() > 1) {
			tell(level, to, Component.translatable("msg.burmaldaholic.bots.joined_many", BotNames.list(r.joined().stream().map(b -> b.profile).toList())));
		}
		if (!r.joined().isEmpty()) {
			say(level, r.joined().getFirst().profile, "join", null);
		}
		boolean saidLeave = false;
		for (SeatedBot b : r.left()) {
			tell(level, to, Component.translatable("msg.burmaldaholic.bots.left", BotNames.display(b.profile)));
			if (!saidLeave && !r.seatedClaimants().isEmpty()) {
				continue; // a yielding bot already said its line
			}
			if (!saidLeave) {
				saidLeave = true;
				say(level, b.profile, b.stack <= 0 && table.botRole() == BotRole.MONEY ? "bust" : "leave", null);
			}
		}
	}

	/** Think delay for the next decision of {@code bot} (BOTS.md §7.3), from the bot rng. */
	public int thinkTicks(BotProfile bot, boolean bigDecision, int humanTimer) {
		return Bots.thinkTicks(rng(), bot, settings().effectiveSpeed(), bigDecision, humanTimer, table.botGameId());
	}

	/**
	 * A bot quip (§7.4) through the table's rate-limited chatter queue ({@link BotChatter}); returns false
	 * if it was dropped. {@code human} = the name for {@code %1$s} lines (may be null).
	 */
	public boolean say(ServerLevel level, BotProfile bot, String event, @Nullable String human) {
		return BotChatter.say(level, table.botTablePos(), key(), table::seatedHumans, bot, event, human, settings().chatter(), rng()) != null;
	}

	/** Every bot leaves (stacks / banks back to their purses); host invites expire; defaults restored. */
	public void endSession(ServerLevel level) {
		server = level.getServer();
		List<SeatedBot> left = removeAll(level.getServer());
		if (!left.isEmpty()) {
			tell(level, table.seatedHumans(), Component.translatable("msg.burmaldaholic.bots.session_ended"));
		}
		resetSession();
	}

	// ---- persistence (pvp-bots.md §5.3) ------------------------------------------------------------

	public void save(CompoundTag out) {
		CompoundTag t = new CompoundTag();
		t.putInt("v", 1);
		if (keeper != null) {
			t.putString("keeper", keeper.toString());
		}
		CompoundTag d = settingsTag(defaults);
		d.putBoolean("private", defaultPrivate);
		d.put("guests", uuids(access.guests()));
		t.put("defaults", d);
		CompoundTag l = new CompoundTag();
		l.putString("botsMode", limits.botsMode().name());
		l.putBoolean("hostMayChange", limits.hostMayChange());
		l.putInt("maxBots", limits.maxBots());
		l.putBoolean("allowPrivate", limits.allowPrivate());
		l.putBoolean("set", limitsSet);
		t.put("limits", l);
		if (session != null) {
			CompoundTag s = settingsTag(session);
			if (host != null) {
				s.putString("host", host.toString());
			}
			if (pending != null) {
				s.put("pending", settingsTag(pending));
			}
			s.putBoolean("private", access.isPrivate());
			s.put("invites", uuids(access.sessionInvites()));
			s.put("claimants", uuids(claimants));
			ListTag bl = new ListTag();
			for (SeatedBot b : bots) {
				CompoundTag e = new CompoundTag();
				e.putString("id", b.profile.id());
				e.putString("nameId", b.profile.nameId());
				e.putString("level", b.profile.level().name());
				e.putString("personality", b.profile.personality().name());
				e.putString("purse", b.purse.kind().name());
				e.putString("bankroll", b.purse.bankrollId());
				e.putLong("stack", b.stack);
				e.putLong("bankEscrow", b.bankEscrow);
				bl.add(e);
			}
			s.put("bots", bl);
			t.put("session", s);
		}
		out.put("bots", t);
	}

	/**
	 * Restores defaults, limits, keeper and saved guests. A saved session is NOT restored: its bots are
	 * orphans of a crash (a clean stop ends every session first). What bankroll bots held is returned to
	 * the bankroll from the world ledger at server start / this table's first safe point; house bots'
	 * chips simply stay in the bank. Legacy name ids are mapped for callers that read old data.
	 */
	public void load(CompoundTag in) {
		CompoundTag t = in.getCompoundOrEmpty("bots");
		if (t.isEmpty()) {
			return;
		}
		keeper = uuid(t.getStringOr("keeper", ""));
		CompoundTag d = t.getCompoundOrEmpty("defaults");
		if (!d.isEmpty()) {
			defaults = readSettings(d, defaults);
			defaultPrivate = d.getBooleanOr("private", false);
			for (UUID g : readUuids(d.getListOrEmpty("guests"))) {
				access.invite(g, true, Integer.MAX_VALUE);
			}
		}
		access.setPrivate(defaultPrivate, List.of());
		CompoundTag l = t.getCompoundOrEmpty("limits");
		if (!l.isEmpty()) {
			BotsMode mode;
			try {
				mode = BotsMode.valueOf(l.getStringOr("botsMode", limits.botsMode().name()));
			} catch (IllegalArgumentException e) {
				mode = limits.botsMode();
			}
			limits = new OwnerControls(mode, l.getBooleanOr("hostMayChange", limits.hostMayChange()), l.getIntOr("maxBots", limits.maxBots()),
				l.getBooleanOr("allowPrivate", limits.allowPrivate()));
			limitsSet = l.getBooleanOr("set", false);
		}
		CompoundTag s = t.getCompoundOrEmpty("session");
		int dropped = s.getListOrEmpty("bots").size();
		if (dropped > 0) {
			Burmaldaholic.LOGGER.info("Table {}: {} bot(s) of an interrupted session are not restored (chips go back to their purse)", key(), dropped);
		}
		bots.clear();
		resetSession();
	}

	// ---- internals ------------------------------------------------------------------------------------

	private void startSession() {
		session = defaults;
		pending = null;
		access.setPrivate(defaultPrivate, table.seatedHumans());
	}

	private void resetSession() {
		session = null;
		pending = null;
		host = null;
		claimants.clear();
		access.endSession();
		access.setPrivate(defaultPrivate, List.of());
		rng = null;
		said.clear();
		bankrollShort = false;
		BotChatter.forget(key());
		Bots.untrack(this);
	}

	private void updateHost(ServerLevel level, List<UUID> humans) {
		UUID next = SeatingMath.host(keeper, humans);
		if (!Objects.equals(next, host)) {
			UUID before = host;
			host = next;
			if (before != null && next != null) {
				ServerPlayer p = level.getServer().getPlayerList().getPlayer(next);
				if (p != null) {
					p.sendSystemMessage(Component.translatable("msg.burmaldaholic.bots.host_now"));
				}
			}
		}
	}

	private void pruneClaimants(ServerLevel level) {
		BlockPos pos = table.botTablePos();
		double max = CasinoConfig.multiplayer().tableLeaveDistance;
		claimants.removeIf(c -> {
			ServerPlayer p = level.getServer().getPlayerList().getPlayer(c);
			if (p == null || p.level() != level) {
				return true;
			}
			return pos != null && p.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) > max * max;
		});
	}

	private List<SeatPlan.Bot> planBots(boolean withBust) {
		List<SeatOccupant> occ = table.occupants();
		boolean money = table.botRole() == BotRole.MONEY;
		boolean needsChips = money && table.botBuyIn() > 0;
		List<SeatPlan.Bot> out = new ArrayList<>();
		for (SeatedBot b : bots) {
			int seat = -1;
			for (int i = 0; i < occ.size(); i++) {
				if (occ.get(i) != null && occ.get(i).key().equals(b.key())) {
					seat = i;
					break;
				}
			}
			out.add(new SeatPlan.Bot(b.key(), seat, b.stack, b.joinOrder, table.isBotBanker(b.key()), table.handsSinceBigBlind(b.key()),
				b.profile.level(), b.purse.houseFunded(), withBust && needsChips && b.held() <= 0));
		}
		return out;
	}

	private int atmosphereCap() {
		var m = CasinoConfig.bots().atmosphere.maxPerTable;
		return switch (table.botGameId()) {
			case "blackjack" -> m.blackjack;
			case "uth" -> m.uth;
			case "roulette" -> m.roulette;
			case "craps" -> m.craps;
			case "baccarat" -> m.baccarat;
			default -> table.botSeatCount();
		};
	}

	private List<SeatedBot> join(ServerLevel level, SeatPlan.Plan plan, @Nullable Purse purse, long buyIn) {
		List<SeatedBot> joined = new ArrayList<>();
		if (plan.join() <= 0 || purse == null) {
			return joined;
		}
		MinecraftServer srv = level.getServer();
		BotRole role = table.botRole();
		long stack = role == BotRole.MONEY ? buyIn : 0;
		BotDifficulty setting = plan.forcedLevel() != null ? plan.forcedLevel() : settings().difficulty();
		for (int i = 0; i < plan.join(); i++) {
			List<String> used = new ArrayList<>();
			for (SeatedBot b : bots) {
				used.add(b.profile.nameId());
			}
			BotProfile profile = BotRoster.create(rng(), setting, table.botDifficultyMix(), table.botNameTheme(), used, CasinoConfig.bots().personalities);
			if (stack > 0) {
				if (purse.kind() == Purse.Kind.BANK) {
					if (BotLedger.buyInsLeft(srv, key()) <= 0) {
						break;
					}
				} else if (!BotPurses.fund(srv, purse, stack, table.botGameId())) {
					break;
				}
			}
			SeatOccupant.Bot occ = new SeatOccupant.Bot(profile, role, role == BotRole.MONEY ? purse : Purse.NONE);
			boolean ok;
			try {
				ok = table.seatBot(occ, stack);
			} catch (RuntimeException e) {
				Burmaldaholic.LOGGER.error("seatBot failed at {}", key(), e);
				ok = false;
			}
			if (!ok) {
				if (stack > 0) {
					BotPurses.settle(srv, purse, stack, table.botGameId()); // give the buy-in back
				}
				break;
			}
			if (stack > 0 && purse.kind() == Purse.Kind.BANK) {
				BotLedger.countBuyIn(srv, key());
			}
			SeatedBot sb = new SeatedBot(profile, occ.purse(), stack);
			sb.joinOrder = ++joinCounter;
			bots.add(sb);
			syncEscrow(sb);
			joined.add(sb);
		}
		return joined;
	}

	private void leave(MinecraftServer srv, SeatedBot b) {
		long held;
		try {
			held = Math.max(0, table.unseatBot(b.key()));
		} catch (RuntimeException e) {
			Burmaldaholic.LOGGER.error("unseatBot failed at {}", key(), e);
			held = b.held();
		}
		bots.remove(b);
		b.stack = held;
		b.bankEscrow = 0;
		if (b.purse.kind() == Purse.Kind.BANKROLL) {
			if (!BotPurses.settle(srv, b.purse, held, table.botGameId())) {
				Burmaldaholic.LOGGER.warn("Bot {} at {}: bankroll {} is gone, {} chips stay in the bank", b.key(), key(), b.purse.bankrollId(), held);
			}
			BotLedger.clearEscrow(srv, key(), b.profile.id());
		}
	}

	private List<SeatedBot> removeAll(MinecraftServer srv) {
		List<SeatedBot> left = new ArrayList<>();
		for (SeatedBot b : List.copyOf(bots)) {
			leave(srv, b);
			left.add(b);
		}
		return left;
	}

	private void syncEscrow(SeatedBot b) {
		if (server != null && b.purse.kind() == Purse.Kind.BANKROLL && bots.contains(b)) {
			BotLedger.putEscrow(server, key(), b.profile.id(), b.purse.bankrollId(), b.held());
		}
	}

	private void announceOnce(ServerLevel level, List<UUID> to, String id, Component msg, @Nullable String quip, @Nullable UUID about) {
		if (!said.add(id)) {
			return;
		}
		tell(level, to, msg);
		if (quip != null && !bots.isEmpty()) {
			say(level, bots.getFirst().profile, quip, about == null ? null : nameOf(level, about).getString());
		}
	}

	private Optional<OwnedTable> ownership(ServerLevel level) {
		return BotPurses.ownership(level, table.botTablePos());
	}

	/** Header line of the running session (defaults outside one): "Humans + 3 bots · Mixed · Open to all" (+ pending). */
	public MutableComponent summary(ServerLevel level) {
		MutableComponent out = summary(level, settings());
		if (pending != null) {
			out.append(Texts.raw(" · ")).append(Component.translatable("gui.burmaldaholic.bots.pending"));
		}
		return out;
	}

	private MutableComponent summary(ServerLevel level, BotSettings s) {
		Component access = Component.translatable(this.access.isPrivate() ? "gui.burmaldaholic.bots.summary.private" : "gui.burmaldaholic.bots.summary.open");
		Component bots = Texts.plural("unit.burmaldaholic.bot", s.count());
		Component lvl = BotNames.level(s.difficulty(), table.botDifficultyMatters() ? BotNames.LevelLabel.LEVEL : BotNames.LevelLabel.STYLE);
		return switch (s.policy()) {
			case HUMANS_ONLY -> Component.translatable("gui.burmaldaholic.bots.summary.humans_only", access);
			case MIXED -> Component.translatable("gui.burmaldaholic.bots.summary.mixed", bots, lvl, access);
			case BOTS_ONLY -> Component.translatable("gui.burmaldaholic.bots.summary.bots_only", nameOf(level, host), bots, lvl);
		};
	}

	private static @Nullable SeatedBot findIn(List<SeatedBot> list, String key) {
		for (SeatedBot b : list) {
			if (b.key().equals(key)) {
				return b;
			}
		}
		return null;
	}

	private static void tell(ServerLevel level, Collection<UUID> to, Component msg) {
		for (UUID id : to) {
			ServerPlayer p = level.getServer().getPlayerList().getPlayer(id);
			if (p != null) {
				p.sendSystemMessage(msg);
			}
		}
	}

	private static Component nameOf(ServerLevel level, @Nullable UUID id) {
		ServerPlayer p = id == null ? null : level.getServer().getPlayerList().getPlayer(id);
		return p != null ? p.getDisplayName() : Texts.raw("?");
	}

	private static boolean isOperator(ServerPlayer player) {
		return Commands.LEVEL_GAMEMASTERS.check(player.permissions());
	}

	private static CompoundTag settingsTag(BotSettings s) {
		CompoundTag t = new CompoundTag();
		t.putString("policy", s.policy().name());
		t.putInt("count", s.count());
		t.putString("difficulty", s.difficulty().name());
		t.putBoolean("keepFree", s.keepFree());
		t.putBoolean("chatter", s.chatter());
		t.putString("speed", s.speed().name());
		return t;
	}

	private static BotSettings readSettings(CompoundTag t, BotSettings fallback) {
		BotSpeed speed;
		try {
			speed = BotSpeed.valueOf(t.getStringOr("speed", fallback.speed().name()));
		} catch (IllegalArgumentException e) {
			speed = fallback.speed();
		}
		String diff = t.getStringOr("difficulty", fallback.difficulty().name());
		BotDifficulty d = switch (diff.toLowerCase(java.util.Locale.ROOT)) {
			case "fish", "regular", "shark" -> BotDifficulty.fromPokerTier(diff);
			default -> BotDifficulty.byId(diff, fallback.difficulty());
		};
		return new BotSettings(SeatPolicy.byId(t.getStringOr("policy", fallback.policy().name()), fallback.policy()),
			t.getIntOr("count", fallback.count()), d, t.getBooleanOr("keepFree", fallback.keepFree()),
			t.getBooleanOr("chatter", fallback.chatter()), speed);
	}

	/** A saved bot profile (legacy names mapped; unknown ids → null). For games that read old seat data. */
	public static @Nullable BotProfile readProfile(CompoundTag e) {
		String nameId = BotRoster.canonical(e.getStringOr("nameId", ""));
		String id = e.getStringOr("id", "");
		if (nameId == null || id.isEmpty()) {
			return null;
		}
		BotDifficulty level = BotDifficulty.byId(e.getStringOr("level", "NORMAL"), BotDifficulty.NORMAL);
		if (level == BotDifficulty.MIXED) {
			level = BotDifficulty.NORMAL;
		}
		return new BotProfile(id, nameId, level, Personality.byId(e.getStringOr("personality", "tag")));
	}

	private static ListTag uuids(Collection<UUID> ids) {
		ListTag l = new ListTag();
		for (UUID id : ids) {
			CompoundTag e = new CompoundTag();
			e.putString("id", id.toString());
			l.add(e);
		}
		return l;
	}

	private static List<UUID> readUuids(ListTag l) {
		List<UUID> out = new ArrayList<>();
		for (Tag t : l) {
			if (t instanceof CompoundTag c) {
				UUID id = uuid(c.getStringOr("id", ""));
				if (id != null) {
					out.add(id);
				}
			}
		}
		return out;
	}

	private static @Nullable UUID uuid(String s) {
		if (s == null || s.isEmpty()) {
			return null;
		}
		try {
			return UUID.fromString(s);
		} catch (IllegalArgumentException e) {
			return null;
		}
	}
}
