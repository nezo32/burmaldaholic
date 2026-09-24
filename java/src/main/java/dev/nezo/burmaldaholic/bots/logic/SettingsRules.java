package dev.nezo.burmaldaholic.bots.logic;

import dev.nezo.burmaldaholic.core.bots.logic.BotDifficulty;
import dev.nezo.burmaldaholic.core.bots.logic.BotRole;
import dev.nezo.burmaldaholic.core.bots.logic.BotSettings;
import dev.nezo.burmaldaholic.core.bots.logic.BotsMode;
import dev.nezo.burmaldaholic.core.bots.logic.OwnerControls;
import dev.nezo.burmaldaholic.core.bots.logic.SeatPolicy;
import dev.nezo.burmaldaholic.core.bots.logic.SeatingMath;
import org.jspecify.annotations.Nullable;

/**
 * Who may change what in the Table settings screen / {@code /casino table} (BOTS.md §2.1, §2.4, §2.5, §6.2,
 * §8.2), and how a wanted change is cleaned up before it is handed to {@code TableBots.requestChange}. Pure;
 * the core {@code TableBots} stays the authority (it re-checks at the safe point).
 */
public final class SettingsRules {
	public static final String ERR = "gui.burmaldaholic.bots.error.";
	public static final String OTHERS_SEATED = ERR + "others_seated";
	public static final String OWNER_OFF = ERR + "owner_off";
	public static final String OWNER_LOCKED = ERR + "owner_locked";
	public static final String HOST_LOCKED = ERR + "host_locked";
	public static final String PRIVATE_FORBIDDEN = ERR + "private_forbidden";
	public static final String DISABLED = ERR + "disabled";
	public static final String NOT_HOST = ERR + "not_host";

	private SettingsRules() {}

	/** The viewer's roles at this table (they combine: an op may also be the host). */
	public record Actor(boolean operator, boolean owner, boolean keeper, boolean host, boolean seated) {
		/** Operator, owner or keeper: edits table defaults and limits, invites are saved with the table. */
		public boolean manager() {
			return operator || owner || keeper;
		}
	}

	/**
	 * Facts about the table.
	 *
	 * @param owned             owned table (charter)
	 * @param seats             seats for occupants
	 * @param humansSeated      humans seated now
	 * @param roleCap           extra cap on bot seats (atmosphere {@code bots.atmosphere.maxPerTable.<game>}; -1 = none)
	 * @param difficultyMatters false where bots make no decisions (difficulty hidden, BOTS.md §4.2)
	 * @param styleNames        show Wild / Steady / Cool-headed instead of Easy / Normal / Hard (chemin de fer)
	 */
	public record Table(boolean owned, OwnerControls limits, BotRole role, int seats, int humansSeated, int roleCap,
		boolean difficultyMatters, boolean styleNames, boolean botsEnabled, boolean privateEnabled) {}

	public enum DifficultyMode { LEVEL, STYLE, HIDDEN }

	/**
	 * What the viewer may do. A null reason means the control is enabled; otherwise it is the translation key
	 * shown as the disabled control's tooltip.
	 */
	public record View(boolean mayEdit, @Nullable String lockReason, boolean mayEditDefaults, boolean botsVisible,
		@Nullable String botsReason, boolean botsOnlyAllowed, int maxCount, DifficultyMode difficultyMode,
		boolean mayEditAccess, boolean privateAllowed, @Nullable String privateReason, boolean mayEditLimits,
		boolean mayEditBotsMode) {}

	/** Result of {@link #validate}: the cleaned settings, or an error key. */
	public record Checked(@Nullable BotSettings settings, @Nullable String error) {
		public boolean ok() {
			return error == null;
		}
	}

	/** @param defaultsMode editing the table defaults (keeper / owner / op) instead of the running session */
	public static View view(Actor a, Table t, boolean defaultsMode) {
		boolean manager = a.manager();
		boolean mayEditSession = manager || (a.host() && t.limits().hostMayChange());
		String lock = null;
		if (!mayEditSession) {
			lock = a.host() ? OWNER_LOCKED : HOST_LOCKED;
		}
		boolean mayEdit = defaultsMode ? manager : mayEditSession;
		if (defaultsMode && !manager) {
			lock = HOST_LOCKED;
		}
		String botsReason = null;
		if (!t.botsEnabled()) {
			botsReason = DISABLED;
		} else if (!t.limits().botsMode().allows(t.role())) {
			botsReason = OWNER_OFF;
		}
		int max = maxCount(t);
		if (botsReason == null && max <= 0) {
			botsReason = OWNER_OFF;
		}
		boolean botsOnly = defaultsMode || SeatingMath.botsOnlyAllowed(t.humansSeated());
		DifficultyMode dm = !t.difficultyMatters() ? DifficultyMode.HIDDEN : t.styleNames() ? DifficultyMode.STYLE : DifficultyMode.LEVEL;
		boolean mayAccess = manager || a.host();
		String privateReason = null;
		if (!t.privateEnabled() || !t.limits().allowPrivate()) {
			privateReason = PRIVATE_FORBIDDEN;
		} else if (!mayAccess) {
			privateReason = a.seated() ? HOST_LOCKED : NOT_HOST;
		}
		boolean mayLimits = a.operator() || (t.owned() ? a.owner() : a.keeper());
		boolean mayMode = t.owned() && (a.operator() || a.owner());
		return new View(mayEdit, lock, manager, botsReason == null, botsReason, botsOnly, max, dm, mayAccess,
			privateReason == null, privateReason, mayLimits, mayMode);
	}

	/** Highest bot count the table allows: {@code min(seats − 1, owner max, role cap)}. */
	public static int maxCount(Table t) {
		int max = Math.min(Math.max(0, t.seats() - 1), Math.max(0, t.limits().maxBots()));
		if (t.roleCap() >= 0) {
			max = Math.min(max, t.roleCap());
		}
		return Math.max(0, max);
	}

	/**
	 * Cleans a wanted change: count clamped to {@code [0, max]} (BOTS_ONLY ≥ 1), hidden difficulty kept from
	 * {@code current}; refuses edits the viewer may not make, BOTS_ONLY with other humans seated and bot
	 * policies where bots are off.
	 */
	public static Checked validate(BotSettings wanted, BotSettings current, View v) {
		if (!v.mayEdit()) {
			return new Checked(null, v.lockReason() != null ? v.lockReason() : HOST_LOCKED);
		}
		SeatPolicy policy = wanted.policy();
		if (policy != SeatPolicy.HUMANS_ONLY && !v.botsVisible()) {
			return new Checked(null, v.botsReason());
		}
		if (policy == SeatPolicy.BOTS_ONLY && !v.botsOnlyAllowed()) {
			return new Checked(null, OTHERS_SEATED);
		}
		int count = Math.max(0, Math.min(wanted.count(), v.maxCount()));
		if (policy == SeatPolicy.BOTS_ONLY) {
			count = Math.max(1, count);
		}
		if (policy == SeatPolicy.HUMANS_ONLY && !v.botsVisible()) {
			count = current.count();
		}
		BotDifficulty difficulty = v.difficultyMode() == DifficultyMode.HIDDEN ? current.difficulty() : wanted.difficulty();
		return new Checked(new BotSettings(policy, count, difficulty, wanted.keepFree(), wanted.chatter(), wanted.speed()), null);
	}

	/** Invites by the keeper / owner (or an op who is not hosting) are saved with the table (BOTS.md §2.5). */
	public static boolean invitesAreSaved(Actor a) {
		return a.keeper() || a.owner() || (a.operator() && !a.host());
	}

	/**
	 * Owner / keeper limits (BOTS.md §6.2): max bots clamped to {@code [0, seats − 1]}; the Bots mode is
	 * always ALLOWED at unowned tables and only the owner / an op changes it at owned tables.
	 */
	public static OwnerControls clampLimits(OwnerControls wanted, OwnerControls current, int seats, boolean owned, boolean mayEditBotsMode) {
		BotsMode mode = !owned ? BotsMode.ALLOWED : mayEditBotsMode ? wanted.botsMode() : current.botsMode();
		if (mode == null) {
			mode = current.botsMode();
		}
		int max = Math.max(0, Math.min(wanted.maxBots(), Math.max(0, seats - 1)));
		return new OwnerControls(mode, wanted.hostMayChange(), max, wanted.allowPrivate());
	}
}
