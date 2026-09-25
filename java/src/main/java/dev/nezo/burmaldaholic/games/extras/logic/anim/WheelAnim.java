package dev.nezo.burmaldaholic.games.extras.logic.anim;

import dev.nezo.burmaldaholic.core.anim.Ease;
import dev.nezo.burmaldaholic.core.anim.SeedMix;

/**
 * Wheel of Fortune / Wheel Party motion (extras-pvp.md §3.3, §4.2). PURE; angles in degrees, the wheel face turns
 * clockwise (positive) and the pointer is at the top. Segment {@code i} of {@code n} is drawn at clockwise angle
 * {@code i·s} (s = 360/n) from the top when the face is at rotation 0, so the face rests on segment {@code i} at
 * rotation {@code −i·s} (+ a seeded jitter inside the segment).
 *
 * <p>A spin runs {@code T} ms (the server's {@code spin_ticks × 50}); the reference storyboard is 4 000 ms: pull-back
 * 0–220, the {@code outQuint} spin 220–3 750 and the settle 3 750–4 000; other lengths scale these beats. The last
 * frame is exactly the rest angle, and the settle never crosses a boundary (§0.3 rule 5). The same curve is used for
 * every outcome (no near-miss shaping), except Wheel Party's truthful "By a hair" crawl.
 */
public final class WheelAnim {
	public static final int REFERENCE_MS = 4000;
	public static final int PULL_MS = 220;
	public static final int SETTLE_MS = 250;
	public static final double PULL_DEG = 7;
	public static final int EXTRA_TURNS = 3;
	public static final int REDUCED_MS = 600;
	public static final int SKIP_MS = 150;
	/** "By a hair" crawl: the last second covers at most this many degrees. */
	public static final double HAIR_MAX_DEG = 8;
	public static final int HAIR_MS = 1000;

	private WheelAnim() {}

	public static double segment(int n) {
		return 360.0 / Math.max(1, n);
	}

	/** Rest rotation for segment {@code index}: the centre plus a jitter of ±30 % of the half-width from {@code seed}. */
	public static double restAngle(int index, int n, int seed) {
		double s = segment(n);
		double jitter = (new SeedMix.FxRng(seed).nextDouble() * 2 - 1) * 0.3 * (s / 2);
		return -index * s + jitter;
	}

	/** Segment under the pointer at rotation {@code theta}. */
	public static int segmentAt(double theta, int n) {
		double s = segment(n);
		double a = ((-theta + s / 2) % 360 + 360) % 360;
		return (int) Math.floor(a / s) % n;
	}

	/** Total travel from {@code from} to {@code rest}: whole extra turns plus the forward remainder. */
	public static double travel(double from, double rest, int turns) {
		return turns * 360 + (((rest - from) % 360) + 360) % 360;
	}

	/**
	 * Rotation at {@code ms} of a spin from {@code from} onto {@code rest} lasting {@code totalMs}.
	 *
	 * @param settleRoom degrees from the rest angle to the boundary behind it (the settle rocks back at most
	 *                   {@code min(2°, 0.4·settleRoom)})
	 */
	public static double angle(double from, double rest, int totalMs, double ms, double settleRoom) {
		double delta = travel(from, rest, EXTRA_TURNS);
		double k = totalMs / (double) REFERENCE_MS;
		double pull = PULL_MS * k;
		double settle = SETTLE_MS * k;
		double end = totalMs - settle;
		if (ms <= 0) return from;
		if (ms >= totalMs) return from + delta;
		if (ms < pull) return from - PULL_DEG * Math.sin(Math.PI / 2 * ms / pull);
		if (ms < end) return from - PULL_DEG + (delta + PULL_DEG) * Ease.OUT_QUINT.apply((ms - pull) / (end - pull));
		double q = (ms - end) / settle;
		double r = -Math.min(2, 0.4 * Math.max(0, settleRoom));
		return from + delta + r * Math.sin(Math.PI * q) * (1 - q);
	}

	/** Distance from the rest angle of segment {@code index} to the boundary the settle rocks towards. */
	public static double settleRoom(double rest, int index, int n) {
		double s = segment(n);
		double offset = rest - (-index * s);
		offset = ((offset + 180) % 360 + 360) % 360 - 180;
		return s / 2 + offset;
	}

	/** Reduce motion: straight to the rest angle over 600 ms, no extra turns, no pull-back or settle. */
	public static double reduced(double from, double rest, double ms) {
		double delta = travel(from, rest, 0);
		if (ms >= REDUCED_MS) return from + delta;
		return from + delta * Ease.OUT_CUBIC.apply(Math.max(0, ms) / REDUCED_MS);
	}

	/** Skip: from the angle shown at the skip to the final angle over 150 ms. */
	public static double skip(double shown, double end, double msSinceSkip) {
		if (msSinceSkip >= SKIP_MS) return end;
		return shown + (end - shown) * Ease.OUT_CUBIC.apply(Math.max(0, msSinceSkip) / SKIP_MS);
	}

	/**
	 * Wheel Party "By a hair" (extras-pvp §4.2; truthful: only when the server flags a hair): the same length and final
	 * angle, but the last {@link #HAIR_MS} crawl through the final {@code crawlDeg} degrees at walking speed. With
	 * {@link #hairCrawl} the crawl contains the real boundary crossing when the pointer rests just past it.
	 */
	public static double angleHair(double from, double rest, int totalMs, double ms, double crawlDeg) {
		double delta = travel(from, rest, EXTRA_TURNS);
		double h = Math.max(0.2, Math.min(HAIR_MAX_DEG, crawlDeg));
		double crawl = Math.min(HAIR_MS, totalMs * 0.4);
		double main = totalMs - crawl;
		double k = main / REFERENCE_MS;
		double pull = PULL_MS * k;
		if (ms <= 0) return from;
		if (ms >= totalMs) return from + delta;
		if (ms < pull) return from - PULL_DEG * Math.sin(Math.PI / 2 * ms / pull);
		if (ms < main) return from - PULL_DEG + (delta - h + PULL_DEG) * Ease.OUT_QUINT.apply((ms - pull) / (main - pull));
		return from + delta - h + h * Ease.IN_OUT_SINE.apply((ms - main) / crawl);
	}

	/**
	 * Crawl length for a hair: {@code behind} = degrees from the rest angle back to the boundary the wheel crossed last.
	 * Twice that (≤ 8°), so the crossing happens in the middle of the crawl; 8° when the near boundary lies ahead.
	 */
	public static double hairCrawl(double behind) {
		return Math.min(HAIR_MAX_DEG, Math.max(0.5, 2 * behind));
	}

	// ---- flapper and ticks (pure functions of the angle, so a replay looks identical) ------------------------------

	/** Pegs (segment boundaries) passed at rotation {@code theta}: changes by one at every boundary. */
	public static long pegCount(double theta, double pitch) {
		return (long) Math.floor((theta + pitch / 2) / pitch);
	}

	/**
	 * Flapper deflection in degrees (negative = pushed left) at rotation {@code theta} with pegs every
	 * {@code pitch} degrees: {@code −14·(1−φ)³} while the peg is under the flap (φ &lt; 0.35), then an elastic spring
	 * back (extras-pvp §3.3).
	 */
	public static double flapper(double theta, double pitch) {
		double phi = (((theta + pitch / 2) % pitch) + pitch) % pitch / pitch;
		if (phi < 0.35) {
			double u = 1 - phi;
			return -14 * u * u * u;
		}
		double held = -14 * 0.65 * 0.65 * 0.65;
		return held * (1 - Ease.OUT_ELASTIC.apply(Math.min(1, (phi - 0.35) / 0.3)));
	}

	/** Tick pitch: rises as the wheel slows ({@code 0.9 + 0.5·(1 − speed/maxSpeed)}). */
	public static float tickPitch(double speed, double maxSpeed) {
		double r = maxSpeed <= 0 ? 0 : Math.min(1, Math.abs(speed) / maxSpeed);
		return (float) (0.9 + 0.5 * (1 - r));
	}
}
