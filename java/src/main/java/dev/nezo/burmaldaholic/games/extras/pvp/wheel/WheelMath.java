package dev.nezo.burmaldaholic.games.extras.pvp.wheel;

import dev.nezo.burmaldaholic.core.bots.logic.BotDifficulty;
import dev.nezo.burmaldaholic.core.bots.logic.BotRng;
import dev.nezo.burmaldaholic.core.pvp.logic.PvpMath;

/**
 * Pure math of Wheel Party (PVP.md §6; bots BOTS.md §4.8). Integer only; identical in both editions.
 *
 * <p>Slices are in join order (participant index order): participant i owns {@code [C_{i−1}, C_i)} with
 * {@code C_i − C_{i−1} = s_i}; the winner is the owner of {@code u ∈ [0, P)} (Lemma 2: {@code Pr(i) = s_i/P}).
 */
public final class WheelMath {
	/** 2⁵³: the resolution of the tape's spin value (the same 53 bits Bedrock's {@code Math.random()} has). */
	public static final long SPIN_RESOLUTION = 1L << 53;

	public static final String ERROR_OVER_CAP = "gui.burmaldaholic.pvp.error.over_cap";
	public static final String ERROR_NO_MORE_BETS = "gui.burmaldaholic.pvp.error.no_more_bets";
	public static final String ERROR_STAKE_MIN = "gui.burmaldaholic.pvp.error.stake_min";
	public static final String ERROR_TIER_MAX = "gui.burmaldaholic.error.bet_too_high";

	private WheelMath() {}

	/**
	 * {@code u = floor(r × P / 2⁵³)} for the tape value {@code r ∈ [0, 2⁵³)}: uniform on {@code [0, P)} up to a
	 * relative error of {@code P / 2⁵³} (&lt; 10⁻⁶ for any pot ≤ 8 × 10⁹) — exactly Bedrock's
	 * {@code Math.floor(Math.random() * P)} (PVP.md §6.1). The pot is only known at START, after the tape
	 * contract's {@code draw(rng, n, params)} could see it, so the tape keeps r and score() maps it.
	 */
	public static long spinPoint(long r, long pot) {
		if (pot <= 0) {
			throw new IllegalArgumentException("empty wheel");
		}
		if (r < 0 || r >= SPIN_RESOLUTION) {
			throw new IllegalArgumentException("r outside [0, 2^53)");
		}
		// r < 2^53 and pot < 2^53: the 106-bit product split into high/low 64-bit words, then shifted by 53.
		long hi = Math.multiplyHigh(r, pot);
		long lo = r * pot;
		return (hi << 11) | (lo >>> 53);
	}

	/** Winner (slice owner) of point u. */
	public static int winner(long[] stakes, long u) {
		return PvpMath.sliceOwner(stakes, u);
	}

	/** "By a hair" window: {@code ceil(P × 0.02)}. */
	public static long hairWindow(long pot) {
		return Math.floorDiv(Math.addExact(Math.multiplyExact(pot, 2L), 99L), 100L);
	}

	/**
	 * PVP.md §6.2 "By a hair": with u in slice {@code [C_{i−1}, C_i)}, if {@code min(u − C_{i−1}, C_i − 1 − u)}
	 * ≤ {@link #hairWindow} the owner of the slice across the NEARER boundary is named (the circle wraps: the last
	 * slice borders the first). Returns that participant index, or -1. Equal distances name the previous slice.
	 */
	public static int byAHair(long[] stakes, long u) {
		int n = stakes.length;
		long pot = PvpMath.pot(stakes);
		int i = winner(stakes, u);
		long lo = 0;
		for (int k = 0; k < i; k++) {
			lo += stakes[k];
		}
		long hi = lo + stakes[i];
		long toLow = u - lo;
		long toHigh = hi - 1 - u;
		long window = hairWindow(pot);
		if (Math.min(toLow, toHigh) > window) {
			return -1;
		}
		int across = toLow <= toHigh ? Math.floorMod(i - 1, n) : (i + 1) % n;
		return across == i ? -1 : across;
	}

	/** UNDERDOG: the winner's share {@code s_w / P ≤ bp / 10000} ({@code pvp.wheel.underdogShareBasisPoints}). */
	public static boolean underdog(long winnerStake, long pot, int basisPoints) {
		return Math.multiplyExact(winnerStake, 10_000L) <= Math.multiplyExact(pot, (long) basisPoints);
	}

	/** Share in basis points, rounded half up (display and events). */
	public static long shareBasisPoints(long stake, long pot) {
		return pot <= 0 ? 0 : Math.floorDiv(Math.addExact(Math.multiplyExact(stake, 20_000L), pot), 2 * pot);
	}

	/** Pointer angle at rest, in degrees from slice 0's start: {@code 360 × (u + 0.5) / P} (§6.2). */
	public static double pointerDegrees(long u, long pot) {
		return 360.0 * (u + 0.5) / pot;
	}

	/** {@code EV_i = −s_i × R / P} (Lemma 2), exact as a fraction: returns {numerator, denominator}. */
	public static long[] expectedValue(long[] stakes, int i, int rakeBasisPoints) {
		long pot = PvpMath.pot(stakes);
		long rake = PvpMath.rake(pot, rakeBasisPoints);
		return new long[] {-Math.multiplyExact(stakes[i], rake), pot};
	}

	/**
	 * Stake / top-up check (PVP.md §6.1, test W6): the total after adding {@code extra} to {@code current} must be
	 * ≤ min(cap, tier max) and ≥ {@code pvp.minStake}, and bets close at No more bets. Returns null or the error key
	 * (args: over_cap = cap, stake_min = minStake, bet_too_high = tier max).
	 */
	public static String checkStake(long current, long extra, long cap, long tierMax, long minStake, boolean betsClosed) {
		if (betsClosed) {
			return ERROR_NO_MORE_BETS;
		}
		if (extra <= 0) {
			return ERROR_STAKE_MIN;
		}
		long total = current + extra;
		if (total < 0 || total > cap) {
			return ERROR_OVER_CAP;
		}
		if (total > tierMax) {
			return ERROR_TIER_MAX;
		}
		if (total < minStake) {
			return ERROR_STAKE_MIN;
		}
		return null;
	}

	/** Median of the human stakes so far (lower middle for an even count; 0 when none). */
	public static long median(long[] humanStakes) {
		if (humanStakes.length == 0) {
			return 0;
		}
		long[] s = humanStakes.clone();
		java.util.Arrays.sort(s);
		return s[(s.length - 1) / 2];
	}

	// ---- bots (BOTS.md §4.8: stake size and top-up timing are EV-neutral per chip, Lemma 2) -------------

	/**
	 * A bot's opening stake: Wild (EASY) {@code pvp.minStake}; Steady (NORMAL) the median human stake (a random
	 * 20–60 % of the cap before any human staked); Cool-headed (HARD) the cap C. Clamped to [min, cap].
	 */
	public static long botStake(BotDifficulty level, long minStake, long cap, long medianHumanStake, BotRng rng) {
		long want = switch (effective(level)) {
			case EASY -> minStake;
			case HARD -> cap;
			default -> medianHumanStake > 0 ? medianHumanStake : cap * rng.between(20, 60) / 100;
		};
		return clamp(want, minStake, cap);
	}

	/**
	 * Extra chips a bot adds when asked (0 = none): Wild adds {@code pvp.minStake} with 25 % chance per ask
	 * ("random top-ups"); Steady never tops up; Cool-headed fills up to the cap. Never beyond the cap.
	 */
	public static long botTopUp(BotDifficulty level, long minStake, long cap, long currentStake, BotRng rng) {
		long room = Math.max(0, cap - currentStake);
		long extra = switch (effective(level)) {
			case EASY -> rng.chance(0.25) ? minStake : 0;
			case HARD -> room;
			default -> 0;
		};
		return Math.min(room, Math.max(0, extra));
	}

	/**
	 * When a bot joins, in ticks after the spin countdown started (BOTS.md §4.8: Wild joins early, Cool-headed
	 * "up to the cap, early", Steady somewhere in the first half). Always before No more bets.
	 */
	public static int botJoinDelay(BotDifficulty level, int countdownTicks, int noMoreBetsTicks, BotRng rng) {
		int open = Math.max(1, countdownTicks - noMoreBetsTicks - 1);
		int delay = switch (effective(level)) {
			case EASY -> rng.between(20, 60);
			case HARD -> rng.between(10, 40);
			default -> rng.between(40, Math.max(40, open / 2));
		};
		return Math.min(open, delay);
	}

	private static long clamp(long v, long lo, long hi) {
		return Math.max(lo, Math.min(hi, v));
	}

	static BotDifficulty effective(BotDifficulty level) {
		return level == null || level.isMixed() ? BotDifficulty.NORMAL : level;
	}
}
