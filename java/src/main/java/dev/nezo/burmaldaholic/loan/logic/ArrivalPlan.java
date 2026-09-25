package dev.nezo.burmaldaholic.loan.logic;

/**
 * The Debt Collectors' arrival (global.md §4.9), PURE: the three door knocks, the red vignette pulses and the smoke
 * columns at each member. Everything starts from the server's "wave spawned" moment (the members already exist);
 * the columns are drawn only at the real member positions the server sends.
 */
public final class ArrivalPlan {
	/** Knock times (ms) and pitches: {@code block.wooden_door.close} p0.55 / 0.6 / 0.5 v0.9. */
	public static final int[] KNOCK_MS = {0, 280, 520};
	public static final float[] KNOCK_PITCH = {0.55f, 0.6f, 0.5f};
	/** Vignette: two pulses over 1 500 ms, ≤ 30 %; flashes off: one static 20 % tint for 1 s. */
	public static final int PULSE_MS = 1500;
	/** One pulse every 750 ms (two in the 1.5 s window; each rise is 375 ms, slower than the 250 ms flash rule). */
	public static final int PULSE_PERIOD_MS = 750;
	public static final double PULSE_MAX = 0.30;
	public static final int STATIC_MS = 1000;
	public static final double STATIC_ALPHA = 0.20;
	/** Smoke column: particles per member, height (blocks) and spread over time (ms). */
	public static final int SMOKE_PER_MEMBER = 10;
	public static final double SMOKE_HEIGHT = 2;
	public static final int SMOKE_SPREAD_MS = 450;
	/** Title card (the "Knock knock" fist) hold. */
	public static final int CARD_MS = 2500;

	private ArrivalPlan() {}

	/** Knocks due by {@code ms} (0–3). */
	public static int knocksDue(double ms) {
		int n = 0;
		for (int k : KNOCK_MS) if (ms >= k) n++;
		return n;
	}

	/**
	 * Red vignette alpha at {@code ms}: {@code 0.3 · sin²(π · t / 750 ms)} (two soft pulses), 0 after 1 500 ms.
	 * Flashes off: a static 20 % for 1 s.
	 */
	public static double vignette(double ms, boolean flashes) {
		if (ms < 0) return 0;
		if (!flashes) return ms < STATIC_MS ? STATIC_ALPHA : 0;
		if (ms >= PULSE_MS) return 0;
		double s = Math.sin(Math.PI * ms / PULSE_PERIOD_MS);
		double env = ms > PULSE_MS - 200 ? (PULSE_MS - ms) / 200.0 : 1;
		return PULSE_MAX * s * s * env;
	}

	/**
	 * Smoke particle {@code i} of a member's column: {@code {dx, dy, dz, delayMs}} — a thin rising column (radius ≤
	 * 0.35 blocks) spread over {@link #SMOKE_SPREAD_MS}, the particles climbing the column's 2 blocks in order.
	 * {@code seed} (public: squad member index) only rotates the column.
	 */
	public static double[] smoke(int i, int seed) {
		int n = SMOKE_PER_MEMBER;
		double a = (seed * 2.399963 + i * Math.PI * 2 * 0.382) % (Math.PI * 2);
		double r = 0.18 + 0.17 * ((i * 7 + seed) % 5) / 4.0;
		double y = SMOKE_HEIGHT * i / (double) n;
		return new double[] {Math.cos(a) * r, y * 0.35, Math.sin(a) * r, (double) i * SMOKE_SPREAD_MS / n};
	}

	/** Card (fist + title) alpha: in 200 ms, hold, out over the last 400 ms. */
	public static double cardAlpha(double ms) {
		if (ms < 0 || ms >= CARD_MS) return 0;
		return Math.min(1, Math.min(ms / 200.0, (CARD_MS - ms) / 400.0));
	}

	/** Card shake (px) on each knock: a 120 ms {@code shake} of 2 px (0 under reduced motion). */
	public static double knockShake(double ms, boolean reduced) {
		if (reduced) return 0;
		for (int k : KNOCK_MS) {
			double t = ms - k;
			if (t >= 0 && t < 120) return 2 * Math.sin(6 * Math.PI * t / 120) * (1 - t / 120);
		}
		return 0;
	}
}
