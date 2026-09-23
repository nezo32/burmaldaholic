package dev.nezo.burmaldaholic.lastchance.logic;

import java.util.Collection;
import java.util.Set;

/**
 * Last Chance rules (GAME_DESIGN §15), pure Java: trigger decision, success costs, cooldowns and the
 * Hardcore High-Stakes scar. No Minecraft imports; the server layer feeds plain values.
 */
public final class LastChanceRules {
	private LastChanceRules() {}

	public enum Difficulty { PEACEFUL, EASY, NORMAL, HARD }

	public enum HardcoreMode { DISABLED, HIGH_STAKES }

	public enum Mode {
		STANDARD, HIGH_STAKES;

		public String id() {
			return this == STANDARD ? "standard" : "high_stakes";
		}
	}

	/** Why a lethal hit is not flipped. */
	public enum SkipReason {
		CASINO_OFF, DISABLED, HARDCORE_DISABLED, TOTEM, EXCLUDED_CAUSE, SOUL_WAGER, SQUAD_HARDCORE, COOLDOWN, NOT_ELIGIBLE
	}

	/** Debt-collector squad members (§5.5); no Last Chance against them in Hardcore (§5.7). */
	public static final Set<String> SQUAD_TYPES = Set.of(
		"burmaldaholic:debt_collector",
		"burmaldaholic:repo_man",
		"burmaldaholic:accountant",
		"burmaldaholic:enforcer");

	/** The {@code lastChance.*} config as plain values. */
	public record Settings(
		boolean enabled,
		double chanceEasy,
		double chanceNormal,
		double chanceHard,
		int cooldownTicks,
		int costPercent,
		HardcoreMode hardcoreMode,
		double hardcoreChance,
		int hardcoreCooldownTicks,
		long hardcoreMinStake,
		int hardcoreHeartCost,
		int hardcoreMinMaxHealth
	) {
		/** CONFIG.md defaults. */
		public static final Settings DEFAULTS = new Settings(true, 0.6, 0.5, 0.4, 24000, 10, HardcoreMode.DISABLED, 0.5, 120000, 100, 2, 8);

		public Settings withHardcoreMode(HardcoreMode mode) {
			return new Settings(enabled, chanceEasy, chanceNormal, chanceHard, cooldownTicks, costPercent, mode,
				hardcoreChance, hardcoreCooldownTicks, hardcoreMinStake, hardcoreHeartCost, hardcoreMinMaxHealth);
		}

		public Settings withEnabled(boolean on) {
			return new Settings(on, chanceEasy, chanceNormal, chanceHard, cooldownTicks, costPercent, hardcoreMode,
				hardcoreChance, hardcoreCooldownTicks, hardcoreMinStake, hardcoreHeartCost, hardcoreMinMaxHealth);
		}
	}

	/**
	 * The killing blow.
	 * @param excludedCause void / {@code /kill} ({@code out_of_world}, {@code generic_kill})
	 * @param soulWager     damage type {@code burmaldaholic:soul_wager}
	 * @param attackerTypes entity type ids of the causing and direct entity (e.g. {@code burmaldaholic:enforcer})
	 */
	public record Hit(boolean excludedCause, boolean soulWager, Collection<String> attackerTypes) {}

	/**
	 * @param maxHealth    current max health (already includes any scar)
	 * @param lastUsedAt   world time of the last flip; ignored when {@code !used}
	 */
	public record PlayerFacts(long balance, long chipsCarried, double maxHealth, boolean holdsTotem, boolean used, long lastUsedAt) {}

	public record WorldFacts(boolean casinoOn, boolean hardcore, Difficulty difficulty, long now) {}

	/** Either {@code skip != null}, or a flip in {@code mode} with base probability {@code chance}. */
	public record Decision(SkipReason skip, Mode mode, double chance, int cooldownTicks) {
		static Decision skip(SkipReason reason) {
			return new Decision(reason, null, 0, 0);
		}

		public boolean flips() {
			return skip == null;
		}
	}

	public static double clamp01(double p) {
		return Double.isNaN(p) ? 0.0 : Math.max(0.0, Math.min(1.0, p));
	}

	/** Standard success chance (§2.3); Peaceful uses the Easy value. */
	public static double chanceFor(Difficulty difficulty, Settings s) {
		return clamp01(switch (difficulty) {
			case PEACEFUL, EASY -> s.chanceEasy();
			case NORMAL -> s.chanceNormal();
			case HARD -> s.chanceHard();
		});
	}

	/** Which variant applies in this world, or null when Last Chance is off entirely. */
	public static Mode modeFor(boolean hardcore, Settings s) {
		if (!s.enabled()) {
			return null;
		}
		if (!hardcore) {
			return Mode.STANDARD;
		}
		return s.hardcoreMode() == HardcoreMode.HIGH_STAKES ? Mode.HIGH_STAKES : null;
	}

	public static int cooldownFor(Mode mode, Settings s) {
		return Math.max(0, mode == Mode.HIGH_STAKES ? s.hardcoreCooldownTicks() : s.cooldownTicks());
	}

	/** Ticks until Last Chance is ready again (0 = ready). */
	public static long cooldownRemaining(long now, boolean used, long lastUsedAt, int cooldownTicks) {
		if (!used) {
			return 0;
		}
		return Math.max(0, lastUsedAt + cooldownTicks - now);
	}

	/** High-Stakes eligibility (§15.3): balance + carried chips ≥ minStake and max health ≥ minMaxHealth. */
	public static boolean highStakesEligible(long balance, long chipsCarried, double maxHealth, Settings s) {
		return Math.max(0, balance) + Math.max(0, chipsCarried) >= s.hardcoreMinStake()
			&& maxHealth >= s.hardcoreMinMaxHealth();
	}

	/** Decide whether a lethal hit triggers a coin flip (§15.1, §15.3, §5.7). */
	public static Decision decide(Hit hit, PlayerFacts p, WorldFacts w, Settings s) {
		if (!w.casinoOn()) {
			return Decision.skip(SkipReason.CASINO_OFF);
		}
		if (!s.enabled()) {
			return Decision.skip(SkipReason.DISABLED);
		}
		Mode mode = modeFor(w.hardcore(), s);
		if (mode == null) {
			return Decision.skip(SkipReason.HARDCORE_DISABLED);
		}
		if (p.holdsTotem()) {
			return Decision.skip(SkipReason.TOTEM);
		}
		if (hit.excludedCause()) {
			return Decision.skip(SkipReason.EXCLUDED_CAUSE);
		}
		if (hit.soulWager()) {
			return Decision.skip(SkipReason.SOUL_WAGER);
		}
		if (w.hardcore() && hit.attackerTypes().stream().anyMatch(SQUAD_TYPES::contains)) {
			return Decision.skip(SkipReason.SQUAD_HARDCORE);
		}
		int cd = cooldownFor(mode, s);
		if (cooldownRemaining(w.now(), p.used(), p.lastUsedAt(), cd) > 0) {
			return Decision.skip(SkipReason.COOLDOWN);
		}
		if (mode == Mode.HIGH_STAKES) {
			if (!highStakesEligible(p.balance(), p.chipsCarried(), p.maxHealth(), s)) {
				return Decision.skip(SkipReason.NOT_ELIGIBLE);
			}
			return new Decision(null, mode, clamp01(s.hardcoreChance()), cd);
		}
		return new Decision(null, mode, chanceFor(w.difficulty(), s), cd);
	}

	/** The coin: heads with probability {@code p}, given a uniform {@code roll} in [0, 1). */
	public static boolean flip(double roll, double p) {
		return roll < clamp01(p);
	}

	/**
	 * What a successful flip costs (§15.1 / §15.3).
	 * @param fee          chips taken from the balance
	 * @param destroyChips High Stakes: every carried chip item is destroyed too
	 * @param addScarHp    HP added to the permanent max-health scar
	 */
	public record SuccessPlan(long fee, boolean destroyChips, int addScarHp) {}

	public static SuccessPlan planSuccess(Mode mode, Settings s, long balance) {
		long bal = Math.max(0, balance);
		if (mode == Mode.HIGH_STAKES) {
			return new SuccessPlan(bal, true, Math.max(0, s.hardcoreHeartCost()));
		}
		int pct = Math.max(0, Math.min(100, s.costPercent()));
		// floor(balance × pct / 100) without overflow for balances up to Long.MAX_VALUE / 100
		long fee = bal / 100 * pct + (bal % 100) * pct / 100;
		return new SuccessPlan(fee, false, 0);
	}

	/** Revive health: ceil(maxHealth / 2), at least 1. */
	public static float reviveHealth(float maxHealth) {
		return Math.max(1.0f, (float) Math.ceil(maxHealth / 2.0f));
	}

	/** True when the "Last Chance has recharged" message is due. */
	public static boolean readyDue(boolean used, boolean notified, long lastUsedAt, long now, int cooldownTicks) {
		return used && !notified && cooldownTicks > 0 && cooldownRemaining(now, true, lastUsedAt, cooldownTicks) == 0;
	}

	/** Remaining cooldown as a whole number of minutes (rounded up), or -1 when under a minute. */
	public static long wholeMinutes(long ticks) {
		return ticks >= 1200 ? (ticks + 1199) / 1200 : -1;
	}

	/** Whole seconds, rounded up, at least 1. */
	public static long wholeSeconds(long ticks) {
		return Math.max(1, (ticks + 19) / 20);
	}

	/** HP → hearts text ("2", "1½"), language neutral. */
	public static String heartsNumber(int hp) {
		int whole = hp / 2;
		if (hp % 2 == 0) {
			return Integer.toString(whole);
		}
		return whole == 0 ? "½" : whole + "½";
	}
}
