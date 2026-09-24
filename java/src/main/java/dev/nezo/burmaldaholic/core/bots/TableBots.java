package dev.nezo.burmaldaholic.core.bots;

import dev.nezo.burmaldaholic.core.bots.logic.BotProfile;
import dev.nezo.burmaldaholic.core.bots.logic.BotRng;
import dev.nezo.burmaldaholic.core.bots.logic.BotSettings;
import dev.nezo.burmaldaholic.core.bots.logic.OwnerControls;
import dev.nezo.burmaldaholic.core.bots.logic.Purse;
import dev.nezo.burmaldaholic.core.bots.logic.TableAccess;
import dev.nezo.burmaldaholic.core.util.Result;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.Nullable;

/**
 * Per-table bot state, OWNED by a table block entity that implements {@link BotTable} (composition, so
 * {@code CasinoTableBlockEntity} stays unchanged). Holds the saved table defaults, owner/keeper limits,
 * access (private + invites) and the live session (host, pending settings, seated bots with stacks and
 * purses, bot rng). Persist with {@link #save}/{@link #load} inside the BE's saveAdditional/loadAdditional
 * (format: pvp-bots.md §5.3).
 *
 * <p>Game integration contract:
 * <ol>
 *   <li>construct in the BE constructor: {@code bots = new TableBots(this, defaultsFor("poker"))};</li>
 *   <li>on every "a human wants to sit" call {@link #admit} (private tables, BOTS_ONLY, claimant);</li>
 *   <li>at the game's safe point call {@link #safePoint} — it applies pending settings, seats claimants,
 *       makes bots join / leave through the {@link BotTable} hooks and funds / returns purses;</li>
 *   <li>for every bot decision: build the game's view, call the game's {@code BotPolicy.act} after
 *       {@link #thinkTicks} using {@link #rng()} — never the game rng;</li>
 *   <li>on table stop (break, unload, server stop, casino off) after the round settled: {@link #endSession}.</li>
 * </ol>
 */
public final class TableBots {
	/** A seated bot's persistent state (BOTS.md §6.1). */
	public static final class SeatedBot {
		public final BotProfile profile;
		public final Purse purse;
		public long stack;
		public long bankEscrow;

		public SeatedBot(BotProfile profile, Purse purse, long stack) {
			this.profile = profile;
			this.purse = purse;
			this.stack = stack;
		}
	}

	/** What happened at a safe point (for the game's announcements / chatter). */
	public record SafePointResult(List<SeatedBot> joined, List<SeatedBot> left, List<UUID> seatedClaimants, boolean settingsApplied) {}

	private final BotTable table;
	private BotSettings defaults;
	private OwnerControls limits;
	private @Nullable UUID keeper;
	private final TableAccess access = new TableAccess();
	private @Nullable BotSettings session;
	private @Nullable BotSettings pending;
	private @Nullable UUID host;
	private final List<SeatedBot> bots = new ArrayList<>();
	private final List<UUID> claimants = new ArrayList<>();
	private @Nullable BotRng rng;

	public TableBots(BotTable table, BotSettings defaults, OwnerControls limits) {
		this.table = table;
		this.defaults = defaults;
		this.limits = limits;
	}

	/** Effective settings of the running session (defaults outside a session). */
	public BotSettings settings() {
		return session != null ? session : defaults;
	}

	public @Nullable BotSettings pending() {
		return pending;
	}

	public BotSettings defaults() {
		return defaults;
	}

	public OwnerControls limits() {
		return limits;
	}

	public TableAccess access() {
		return access;
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

	/** The session's bot random stream (created lazily at session start, BOTS.md §4.1). */
	public BotRng rng() {
		if (rng == null) {
			rng = Bots.newRng();
		}
		return rng;
	}

	/**
	 * A human uses the table. Returns ok(true) = sit now, ok(false) = claimant (seat after the round,
	 * {@code …bots.seat_after_round} already told), or fail with {@code …bots.error.private_table} /
	 * {@code …bots_only_table} / {@code gui.burmaldaholic.error.table_full}.
	 */
	public Result<Boolean> admit(ServerPlayer player) {
		return Result.ok(true); // TODO(J-B1) BOTS.md §3.2
	}

	/**
	 * Host / keeper / owner / op changes settings (BOTS.md §2.4). Stored as pending until the next safe
	 * point; {@code asDefaults} (keeper/owner/op) also saves them as the table defaults.
	 */
	public Result<BotSettings> requestChange(ServerPlayer actor, BotSettings wanted, boolean asDefaults) {
		return Result.fail(Component.translatable("gui.burmaldaholic.error.disabled")); // TODO(J-B1)
	}

	/** Owner / keeper controls changed (charter, BOTS.md §6.2). Applies at the next safe point. */
	public void setLimits(OwnerControls limits) {
		this.limits = limits;
	}

	/** Call at the game's safe point (BOTS.md §3.1). */
	public SafePointResult safePoint(ServerLevel level) {
		// TODO(J-B1): apply pending; host = SeatingMath.host(keeper, table.seatedHumans()); wanted =
		//   SeatingMath.wantedBots(...) capped by SeatingMath.capped(...) with Bots.activeBudgetLeft(), purse
		//   affordability (BotPurses) and the win cap of seated humans (BotLedger); yield bots to claimants
		//   (table.yieldRule()); create profiles with BotRoster.create(rng(), …, mix) and table.seatBot(...).
		return new SafePointResult(List.of(), List.of(), List.of(), false);
	}

	/** Think delay for the next decision of {@code bot} (BOTS.md §7.3), from the bot rng. */
	public int thinkTicks(BotProfile bot, boolean bigDecision, int humanTimer) {
		return Bots.thinkTicks(rng(), bot, settings().effectiveSpeed(), bigDecision, humanTimer, table.botGameId());
	}

	/** Every bot leaves (stacks / banks back to their purses); host invites expire; defaults restored. */
	public void endSession(ServerLevel level) {
		// TODO(J-B1)
		session = null;
		pending = null;
		host = null;
		claimants.clear();
		access.endSession();
		rng = null;
	}

	// ---- persistence (pvp-bots.md §5.3) ------------------------------------------------------------

	public void save(CompoundTag out) {
		// TODO(J-B1) defaults, limits, keeper, access (private, guests), session {host, pending, bots[]}
	}

	/** Orphaned bot stacks found on load (after a crash) are returned to their purses and not restored. */
	public void load(CompoundTag in) {
		// TODO(J-B1)
	}
}
