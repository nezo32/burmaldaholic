package dev.nezo.burmaldaholic.lastchance.logic;

import dev.nezo.burmaldaholic.core.anim.Ease;

/**
 * The Last Chance coin-flip cinematic (global.md §4.8), PURE: every frame is a function of the time since the
 * server's {@code CoinFlipPayload} arrived and of the <b>already decided</b> face. The spin is a monotonic phase in
 * half-turns that ends exactly on the payload face (an even count for heads, odd for tails), so the last frames only
 * approach that face — never past it and back (global §6.3; {@code CoinFlipTimelineTest}).
 *
 * <p>Sheet {@code textures/gui/lastchance/coin_spin.png}: 12 frames of 32 × 32 — 0 heads, 1–5 tilting, 6 edge,
 * 7–11 tilting back to 11 = tails. The server delays the heads sound / totem burst by {@link #LAND_TICKS} so the
 * world agrees with the landing.
 */
public final class CoinFlipTimeline {
	public static final int FRAMES = 12;
	public static final int HEADS_FRAME = 0;
	public static final int EDGE_FRAME = 6;
	public static final int TAILS_FRAME = 11;
	public static final int LAUNCH_MS = 150;
	public static final int APEX_MS = 550;
	public static final int LAND_MS = 1300;
	/** Server-side delay of the heads sound and totem particles (26 ticks = 1 300 ms). */
	public static final int LAND_TICKS = LAND_MS / 50;
	public static final int REDUCED_LAND_MS = 750;
	public static final int RESULT_POP_MS = 150;
	public static final int FADE_AT_MS = 2800;
	public static final int TOTAL_MS = 3300;
	public static final int LAUNCH_PX = 18;
	/** Half-turns of the spin: an even count lands on heads, an odd count on tails. */
	public static final int HEADS_HALF_TURNS = 8;
	public static final int TAILS_HALF_TURNS = 9;
	public static final int HEARTBEAT_2_MS = 350;
	public static final int SPARKLES = 20;

	/** Vignette colours (ARGB): red-black (chip.dark), red (chip.red), gold. */
	public static final int RED_BLACK = 0xFF8C1834;
	public static final int RED = 0xFFD83440;
	public static final int GOLD = 0xFFFFD640;

	/**
	 * One frame.
	 *
	 * @param coinFrame     sheet frame 0–11
	 * @param coinY         vertical offset in GUI px (negative = up)
	 * @param crack         tails crack overlay: −1 none, 0 or 1
	 * @param vignette      edge vignette alpha 0–1
	 * @param vignetteColor ARGB of the vignette
	 * @param tint          full-screen {@code #140822} tint alpha (the "desaturated" world)
	 * @param title         0 none, 1 "Last Chance…", 2 the result (HEADS!/TAILS…)
	 * @param titleAlpha    title alpha 0–1
	 * @param titleScale    multiplier on the title's 2× scale (result pop 1.3 → 1)
	 * @param alpha         overall alpha (final fade)
	 * @param landed        the coin shows the payload face (and stays on it)
	 */
	public record Frame(int coinFrame, double coinY, int crack, double vignette, int vignetteColor, double tint, int title, double titleAlpha,
			double titleScale, double alpha, boolean landed) {}

	private CoinFlipTimeline() {}

	/** The payload face's frame. */
	public static int face(boolean heads) {
		return heads ? HEADS_FRAME : TAILS_FRAME;
	}

	/** When the coin shows the result (reduced motion lands earlier: no launch). */
	public static int landMs(boolean reduced) {
		return reduced ? REDUCED_LAND_MS : LAND_MS;
	}

	/** Spin phase in half-turns at {@code ms}: 0 before the launch, monotonic, exactly the landing count from {@link #LAND_MS}. */
	public static double phase(double ms, boolean heads) {
		int turns = heads ? HEADS_HALF_TURNS : TAILS_HALF_TURNS;
		if (ms <= LAUNCH_MS) return 0;
		if (ms >= LAND_MS) return turns;
		return turns * Ease.OUT_CUBIC.apply((ms - LAUNCH_MS) / (double) (LAND_MS - LAUNCH_MS));
	}

	/** Sheet frame of a spin phase: heads → tails on even half-turns, tails → heads on odd ones. */
	public static int frameOfPhase(double phase) {
		long k = (long) Math.floor(phase);
		double f = phase - k;
		int step = (int) Math.round(f * (FRAMES - 1));
		return (k % 2 == 0) ? step : FRAMES - 1 - step;
	}

	/** Frame at {@code ms} since the flip started. */
	public static Frame frame(double ms, boolean heads, boolean reduced, boolean flashes) {
		int land = landMs(reduced);
		boolean landed = ms >= land;
		// coin
		int coin;
		double y = 0;
		if (reduced) {
			// face frames swap every 100 ms (heads / edge / tails), then the result face
			if (landed || ms < LAUNCH_MS) coin = landed ? face(heads) : HEADS_FRAME;
			else coin = new int[] {HEADS_FRAME, EDGE_FRAME, TAILS_FRAME, EDGE_FRAME}[(int) ((ms - LAUNCH_MS) / 100) % 4];
		} else {
			coin = landed ? face(heads) : frameOfPhase(phase(ms, heads));
			if (ms > LAUNCH_MS && ms < APEX_MS) y = -LAUNCH_PX * Ease.OUT_CUBIC.apply((ms - LAUNCH_MS) / (double) (APEX_MS - LAUNCH_MS));
			else if (ms >= APEX_MS && ms < LAND_MS) y = -LAUNCH_PX * (1 - Ease.IN_CUBIC.apply((ms - APEX_MS) / (double) (LAND_MS - APEX_MS)));
		}
		int crack = !heads && landed ? (ms - land < 100 ? 0 : 1) : -1;

		// vignette + tint
		double max = flashes ? 0.45 : 0.25;
		double vig;
		int color = RED_BLACK;
		double tint = 0.25;
		if (reduced) {
			vig = 0;
		} else if (!landed) {
			vig = max * Ease.OUT_QUAD.apply(ms / 300.0);
		} else if (heads) {
			double k = (ms - land) / 600.0;
			if (flashes && k < 0.5) {
				color = mix(RED_BLACK, GOLD, Ease.IN_OUT_QUAD.apply(k * 2));
				vig = max + (0.20 - max) * Ease.IN_OUT_QUAD.apply(k * 2);
			} else if (flashes) {
				color = GOLD;
				vig = 0.20 * (1 - Ease.IN_OUT_QUAD.apply((k - 0.5) * 2));
			} else {
				vig = max * (1 - Ease.IN_OUT_QUAD.apply(k));
			}
			tint = 0.25 * (1 - Ease.IN_OUT_QUAD.apply(k));
		} else {
			vig = max;
			if (flashes) color = mix(RED_BLACK, RED, Ease.IN_OUT_QUAD.apply((ms - land) / 300.0));
		}
		if (!landed && !reduced) tint = 0.25 * Ease.OUT_QUAD.apply(ms / 300.0);

		// titles
		int title;
		double titleAlpha;
		double titleScale = 1;
		if (!landed) {
			title = 1;
			titleAlpha = Math.min(1, ms / 150.0);
		} else {
			title = 2;
			titleAlpha = 1;
			if (!reduced) titleScale = 1.3 - 0.3 * Ease.OUT_BACK.apply((ms - land) / (double) RESULT_POP_MS);
		}
		double alpha = ms < FADE_AT_MS ? 1 : Math.max(0, 1 - (ms - FADE_AT_MS) / (double) (TOTAL_MS - FADE_AT_MS));
		return new Frame(coin, y, crack, Math.max(0, vig), color, Math.max(0, tint), title, titleAlpha, titleScale, alpha, landed);
	}

	/** True while the overlay draws ({@code 0 ≤ ms < TOTAL_MS}). */
	public static boolean active(double ms) {
		return ms >= 0 && ms < TOTAL_MS;
	}

	/** Linear mix of two opaque ARGB colours. */
	static int mix(int a, int b, double t) {
		double u = t <= 0 ? 0 : t >= 1 ? 1 : t;
		int r = (int) Math.round(((a >> 16) & 0xFF) + (((b >> 16) & 0xFF) - ((a >> 16) & 0xFF)) * u);
		int g = (int) Math.round(((a >> 8) & 0xFF) + (((b >> 8) & 0xFF) - ((a >> 8) & 0xFF)) * u);
		int bl = (int) Math.round((a & 0xFF) + ((b & 0xFF) - (a & 0xFF)) * u);
		return 0xFF000000 | (r << 16) | (g << 8) | bl;
	}
}
