package dev.nezo.burmaldaholic.comps.logic;

import dev.nezo.burmaldaholic.core.economy.RebateCaps;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Comp points, redemptions and the daily bonus (EC-SPEC §4). Pure: no Minecraft classes.
 *
 * <p>Points are kept in milli-points. Each eligible settled round earns
 * {@code round(1000 × stake × edge × compRate[tier])}; points are never cashable and capped at 10M.
 */
public final class CompsRules {
	public static final int BRONZE = 0, SILVER = 1, GOLD = 2, PLATINUM = 3, DIAMOND = 4, NETHERITE = 5;
	/** Cap on the balance: 10 000 000 points. */
	public static final long CAP_MILLI = 10_000_000L * 1000L;
	/** Daily bonus chips Bronze..Netherite (EC-SPEC §4.4). */
	public static final long[] DEFAULT_DAILY = {25, 50, 120, 300, 750, 2000};
	public static final long TICKS_PER_DAY = 24000L;
	/** A token stays valid for 7 in-game days. */
	public static final long TOKEN_LIFETIME_TICKS = 7 * TICKS_PER_DAY;

	private CompsRules() {}

	// ---- earning ----------------------------------------------------------------------------

	/** Milli-points of one round: {@code round(1000 × stake × edge × rate)}; 0 for anything non-positive. */
	public static long pointsMilli(long stake, double edge, double rate) {
		if (stake <= 0 || !(edge > 0) || !(rate > 0) || !Double.isFinite(edge) || !Double.isFinite(rate)) {
			return 0;
		}
		double v = 1000.0 * stake * edge * rate;
		return v >= Long.MAX_VALUE ? Long.MAX_VALUE : Math.round(v);
	}

	/** Combined cap: comp rate + cashback rate ≤ 0.35, so at least 65 % of the edge always stays with the house. */
	public static final double CAP_WITH_CASHBACK = RebateCaps.MAX_COMP_PLUS_CASHBACK;

	/** Comp rate of a tier ({@code rates} Bronze..Netherite), clamped to {@code 0.35 − cashbackRate}. */
	public static double rate(int tier, double[] rates, double cashbackRate) {
		if (rates == null || rates.length == 0) {
			return 0;
		}
		int t = Math.max(0, Math.min(tier, rates.length - 1));
		return RebateCaps.comp(rates[t], cashbackRate);
	}

	/** {@code balance + add}, never above {@link #CAP_MILLI}, never negative. */
	public static long addCapped(long balance, long add) {
		long b = Math.max(0, balance);
		long a = Math.max(0, add);
		return a >= CAP_MILLI - Math.min(b, CAP_MILLI) ? CAP_MILLI : b + a;
	}

	/** Whole points shown to the player (floor). */
	public static long points(long milli) {
		return Math.max(0, milli) / 1000;
	}

	// ---- per-player state -------------------------------------------------------------------

	/**
	 * The {@code burmaldaholic:comps} attachment (EC-SPEC §5).
	 *
	 * @param day              world day the "today" fields belong to
	 * @param dailyClaimDay    last day the daily bonus was claimed (-1 never)
	 * @param activeTicksToday active ticks today (daily-bonus gate)
	 * @param suiteDay         day of the suite-night credit (-1 none)
	 * @param redeemedToday    redemptions per reward id today
	 */
	public record State(long pointsMilli, long lifetimeMilli, long earnedTodayMilli, long day, long dailyClaimDay, long activeTicksToday,
		long suiteDay, Map<String, Integer> redeemedToday) {
		public State {
			redeemedToday = redeemedToday == null ? Map.of() : Map.copyOf(redeemedToday);
		}

		public static State empty(long day) {
			return new State(0, 0, 0, day, -1, 0, -1, Map.of());
		}

		/** Rolls the "today" fields to {@code today} (no-op if already there). */
		public State roll(long today) {
			if (day == today) {
				return this;
			}
			return new State(pointsMilli, lifetimeMilli, 0, today, dailyClaimDay, 0, suiteDay, Map.of());
		}

		public State earn(long milli) {
			long m = Math.max(0, milli);
			return new State(addCapped(pointsMilli, m), addCapped(lifetimeMilli, m), addCapped(earnedTodayMilli, m), day, dailyClaimDay,
				activeTicksToday, suiteDay, redeemedToday);
		}

		public State active(long ticks) {
			return new State(pointsMilli, lifetimeMilli, earnedTodayMilli, day, dailyClaimDay, activeTicksToday + Math.max(0, ticks), suiteDay,
				redeemedToday);
		}

		public State claimed(long today) {
			return new State(pointsMilli, lifetimeMilli, earnedTodayMilli, day, today, activeTicksToday, suiteDay, redeemedToday);
		}

		public State suite(long today) {
			return new State(pointsMilli, lifetimeMilli, earnedTodayMilli, day, dailyClaimDay, activeTicksToday, today, redeemedToday);
		}

		public int redeemed(String id) {
			return redeemedToday.getOrDefault(id, 0);
		}

		/** Spends {@code costPoints} (whole points) and counts one redemption of {@code id}. */
		public State spend(String id, long costPoints) {
			Map<String, Integer> m = new HashMap<>(redeemedToday);
			m.merge(id, 1, Integer::sum);
			long left = Math.max(0, pointsMilli - Math.max(0, costPoints) * 1000);
			return new State(left, lifetimeMilli, earnedTodayMilli, day, dailyClaimDay, activeTicksToday, suiteDay, m);
		}
	}

	// ---- redemptions ------------------------------------------------------------------------

	public enum Kind {
		ITEMS, SUITE, TOKEN, RIDE_ITEMS, HORSE, STRIDER
	}

	public enum Dim {
		ANY, OVERWORLD, NETHER
	}

	/**
	 * A redemption (EC-SPEC §4.2).
	 *
	 * @param tokenValue chips of a free-bet token reward (0 otherwise)
	 * @param limit      per-player limit per in-game day (0 = none)
	 */
	public record Reward(String id, Kind kind, long cost, int minTier, int limit, Dim dim, long tokenValue) {}

	public static final Map<String, Reward> REWARDS;

	static {
		Map<String, Reward> m = new LinkedHashMap<>();
		for (Reward r : List.of(
			new Reward("snack", Kind.ITEMS, 25, BRONZE, 0, Dim.ANY, 0),
			new Reward("chef", Kind.ITEMS, 60, BRONZE, 0, Dim.ANY, 0),
			new Reward("feast", Kind.ITEMS, 200, GOLD, 0, Dim.ANY, 0),
			new Reward("suite", Kind.SUITE, 50, BRONZE, 1, Dim.ANY, 0),
			new Reward("token_10", Kind.TOKEN, 15, BRONZE, 5, Dim.ANY, 10),
			new Reward("token_50", Kind.TOKEN, 70, SILVER, 3, Dim.ANY, 50),
			new Reward("token_250", Kind.TOKEN, 325, PLATINUM, 2, Dim.ANY, 250),
			new Reward("rockets", Kind.RIDE_ITEMS, 30, BRONZE, 0, Dim.ANY, 0),
			new Reward("horse", Kind.HORSE, 40, BRONZE, 1, Dim.OVERWORLD, 0),
			new Reward("strider", Kind.STRIDER, 40, BRONZE, 1, Dim.NETHER, 0))) {
			m.put(r.id(), r);
		}
		REWARDS = java.util.Collections.unmodifiableMap(m);
	}

	/** Cost in whole points at {@code tier}; the suite night is 30 at Gold+ and free (once a day) at Diamond+. */
	public static long cost(Reward r, int tier, State s) {
		if (r.kind() == Kind.SUITE) {
			if (tier >= DIAMOND && s.redeemed(r.id()) == 0) {
				return 0;
			}
			return tier >= GOLD ? 30 : r.cost();
		}
		return r.cost();
	}

	/** Why a reward cannot be redeemed now (lang suffix), or null. */
	public static String lock(Reward r, int tier, State s, Dim here) {
		if (tier < r.minTier()) {
			return "tier";
		}
		if (r.dim() != Dim.ANY && r.dim() != here) {
			return "dimension";
		}
		if (r.limit() > 0 && s.redeemed(r.id()) >= r.limit()) {
			return "limit";
		}
		if (points(s.pointsMilli()) < cost(r, tier, s)) {
			return "points";
		}
		return null;
	}

	// ---- daily bonus ------------------------------------------------------------------------

	public enum Daily {
		CLAIMABLE, CLAIMED, LOCKED_WAGER, LOCKED_ACTIVE, LOCKED_IP
	}

	/**
	 * Daily bonus state (EC-SPEC §4.4): once per in-game day; lifetime wagered ≥ {@code minWager}; ≥
	 * {@code minActiveTicks} active ticks today; at most one claim per IP per day ({@code ipUsed}).
	 */
	public static Daily daily(State s, long today, long lifetimeWagered, long minWager, long minActiveTicks, boolean ipUsed) {
		if (s.dailyClaimDay() == today) {
			return Daily.CLAIMED;
		}
		if (lifetimeWagered < minWager) {
			return Daily.LOCKED_WAGER;
		}
		if ((s.day() == today ? s.activeTicksToday() : 0) < minActiveTicks) {
			return Daily.LOCKED_ACTIVE;
		}
		if (ipUsed) {
			return Daily.LOCKED_IP;
		}
		return Daily.CLAIMABLE;
	}

	/** Daily bonus chips of a tier ({@code table} = Bronze..Netherite). */
	public static long dailyAmount(int tier, long[] table) {
		long[] t = table == null || table.length == 0 ? DEFAULT_DAILY : table;
		int i = Math.max(0, Math.min(tier, t.length - 1));
		return Math.max(0, t[i]);
	}

	/** Ticks until the next world day starts. */
	public static long ticksToNextDay(long gameTime) {
		return TICKS_PER_DAY - Math.floorMod(gameTime, TICKS_PER_DAY);
	}

	/**
	 * Activity gate: a tick counts when the player moved more than 0.5 block or interacted within the last 60 s
	 * ({@code 1200} ticks) before {@code now}.
	 */
	public static boolean active(long now, long lastActivity) {
		return lastActivity >= 0 && now - lastActivity <= 1200;
	}

	/** Moved more than 0.5 block between two samples. */
	public static boolean moved(double dx, double dy, double dz) {
		return dx * dx + dy * dy + dz * dz > 0.25;
	}
}
