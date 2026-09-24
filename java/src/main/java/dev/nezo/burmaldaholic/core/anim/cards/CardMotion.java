package dev.nezo.burmaldaholic.core.anim.cards;

import dev.nezo.burmaldaholic.core.anim.Ease;
import dev.nezo.burmaldaholic.core.anim.SeedMix;

/**
 * The card-table motion primitives K1–K13 of docs/design/animation/cards.md §0.3 as PURE functions of time (C0):
 * deal arc, landing settle, flip, squeeze reveal fraction, slide, gather, stamp, shimmer, glow pulse, total-badge roll,
 * desaturate, action tag, knock, chip flight / sweep. Shared by the Java screens (lanes J-L4, J-L5) and the BERs; no
 * Minecraft imports, no allocation (callers pass a reusable {@link Pose}). Golden vectors:
 * {@code src/test/resources/fx/vectors/cards.json}.
 *
 * <p>Cosmetic jitter comes only from {@link #seed} of public values (faithfulness §0.7.8); every function ends exactly
 * on its target at {@code t = 1} (the server-decided layout).
 */
public final class CardMotion {
	/** K1 deal arc (poker {@link #DEAL_MS_POKER}). */
	public static final int DEAL_MS = 240;
	public static final int DEAL_MS_POKER = 200;
	/** K1b landing settle. */
	public static final int SETTLE_MS = 60;
	/** K2 flip; the face replaces the back at the half. */
	public static final int FLIP_MS = 280;
	/** K1 + K2: a face-up card is readable this long after its beat. */
	public static final int READABLE_MS = DEAL_MS + FLIP_MS;
	/** K4 slide (split, fan re-layout). */
	public static final int SLIDE_MS = 200;
	/** K5 gather per card and stagger. */
	public static final int GATHER_MS = 280;
	public static final int GATHER_STAGGER_MS = 25;
	/** K6 muck / fold. */
	public static final int MUCK_MS = 300;
	/** K7 stamp scale-in. */
	public static final int STAMP_MS = 220;
	/** K8 shimmer sweep. */
	public static final int SHIMMER_MS = 420;
	/** K10 total badge roll. */
	public static final int BADGE_MS = 150;
	/** K11 desaturate. */
	public static final int DESAT_MS = 300;
	/** K12 action tag in / hold / out. */
	public static final int TAG_IN_MS = 180;
	public static final int TAG_HOLD_MS = 1500;
	public static final int TAG_OUT_MS = 300;
	/** K13 knock. */
	public static final int KNOCK_MS = 180;
	/** Reduced motion: fade-in at the slot instead of travel; cross-fade instead of a flip. */
	public static final int REDUCED_FADE_MS = 120;
	public static final int REDUCED_FLIP_MS = 100;
	/** Chips (§0.4). */
	public static final int CHIP_FLIGHT_MS = 180;
	public static final int SWEEP_MS = 260;
	public static final int PAYOUT_STEP_MS = 40;
	public static final int TO_BALANCE_MS = 420;
	public static final int PUSH_WIGGLE_MS = 200;
	/** Chemin de fer shoe pass (§4.5). */
	public static final int SHOE_PASS_MS = 500;
	/** Baccarat bead drop (§4.4). */
	public static final int BEAD_DROP_MS = 250;
	/** Blackjack bust badge shake (§1.2). */
	public static final int BUST_SHAKE_MS = 200;
	/** Height of the deal arc (the control point sits this far above the chord's midpoint). */
	public static final double ARC_RISE = 22;
	/** Start rotation of a dealt card (degrees). */
	public static final double DEAL_START_DEG = -14;

	private CardMotion() {}

	/**
	 * A sampled sprite transform. Positions are the card's top-left in layout pixels (callers round per frame);
	 * {@code rot} in degrees about the card centre; {@code scaleX} is the flip squash; {@code face} tells which side
	 * shows.
	 */
	public static final class Pose {
		public double x;
		public double y;
		public double rot;
		public double scale = 1;
		public double scaleX = 1;
		public double alpha = 1;
		public double shadowDx = 1;
		public double shadowDy = 1;
		public double shadowAlpha = 0.22;
		/** 0..1 white highlight over the face (flip phase B). */
		public double highlight;
		public boolean face = true;

		/** Resting at (x, y). */
		public Pose rest(double px, double py, double rotation, boolean faceUp) {
			x = px;
			y = py;
			rot = rotation;
			scale = 1;
			scaleX = 1;
			alpha = 1;
			shadowDx = 1;
			shadowDy = 1;
			shadowAlpha = 0.22;
			highlight = 0;
			face = faceUp;
			return this;
		}
	}

	// ---- seeds ----------------------------------------------------------------------------------------------------

	/** Cosmetic seed of a card slot from public values only: {@code mix(tableHash, roundSeq, slot)} (§0.7.8). */
	public static int seed(int tableHash, int roundSeq, int slot) {
		return SeedMix.mix(tableHash, roundSeq, slot);
	}

	/** Jitter of the deal: ±2° rotation. */
	public static double jitterDeg(int seed) {
		return ((seed >>> 8) % 401) / 100.0 - 2.0;
	}

	/** Jitter of the deal: −1, 0 or +1 px on each axis (x: bits 0..1, y: bits 2..3). */
	public static int jitterPx(int seed, boolean yAxis) {
		int bits = yAxis ? (seed >>> 2) & 3 : seed & 3;
		return bits == 3 ? 0 : bits - 1;
	}

	/** Deal sound pitch 0.95–1.05 (seeded). */
	public static float dealPitch(int seed) {
		return 0.95f + ((seed >>> 16) % 11) / 100f;
	}

	// ---- K1 deal arc ----------------------------------------------------------------------------------------------

	/**
	 * K1 at {@code t} ∈ [0, 1] from the shoe mouth (sx, sy) to the slot (tx, ty) ending at {@code endDeg} (0, or 90
	 * for a sideways card). The card flies face down. Quadratic Bézier with the control point 22 px above the chord's
	 * midpoint (outCubic on t), rotation −14° (+ jitter) → end with an outBack settle over the last 30 %, scale 0.82 →
	 * 1 (outQuad), shadow (4, 4) @ 0.35 → (1, 1) @ 0.22. Reduced motion: a fade-in at the slot.
	 */
	public static Pose deal(Pose out, double sx, double sy, double tx, double ty, double t, double endDeg, int seed, boolean reduced) {
		double u = clamp(t);
		if (reduced) {
			out.rest(tx, ty, endDeg, false);
			out.alpha = u;
			return out;
		}
		double p = Ease.OUT_CUBIC.apply(u);
		double cx = (sx + tx) / 2;
		double cy = (sy + ty) / 2 - ARC_RISE;
		double a = 1 - p;
		out.x = a * a * sx + 2 * a * p * cx + p * p * tx;
		out.y = a * a * sy + 2 * a * p * cy + p * p * ty;
		double start = DEAL_START_DEG + jitterDeg(seed);
		double r = u < 0.7 ? 0.6 * (u / 0.7) : 0.6 + 0.4 * Ease.outBack(1.70158, (u - 0.7) / 0.3);
		out.rot = start + (endDeg - start) * r;
		out.scale = 0.82 + 0.18 * Ease.OUT_QUAD.apply(u);
		out.scaleX = 1;
		out.alpha = 1;
		out.shadowDx = 4 - 3 * p;
		out.shadowDy = 4 - 3 * p;
		out.shadowAlpha = 0.35 - 0.13 * p;
		out.highlight = 0;
		out.face = false;
		if (u >= 1) {
			out.x = tx;
			out.y = ty;
			out.rot = endDeg;
			out.scale = 1;
		}
		return out;
	}

	/** K1b landing settle: y offset (+1 px, then back) over {@link #SETTLE_MS}. */
	public static double settleDy(double t) {
		double u = clamp(t);
		return u >= 1 ? 0 : Math.sin(Math.PI * Ease.OUT_QUAD.apply(u));
	}

	// ---- K2 flip --------------------------------------------------------------------------------------------------

	/**
	 * K2 at {@code t} ∈ [0, 1]: phase A (first half) squashes the back (scaleX 1 → 0 inSine) and lifts it 4 px; the
	 * sprite swaps back → face at scaleX = 0; phase B opens the face (outBack s 1.3, peak ≈ 1.05) and lowers it, with a
	 * 20 % white highlight fading out. Only {@code scaleX}, {@code y} offset (in {@code out.y}, relative), {@code face}
	 * and {@code highlight} are written. Reduced motion: a cross-fade (alpha of the face in {@code highlight}'s place
	 * is {@link #reducedFlipFace}).
	 */
	public static Pose flip(Pose out, double t) {
		double u = clamp(t);
		if (u < 0.5) {
			double v = u / 0.5;
			out.scaleX = 1 - Ease.IN_SINE.apply(v);
			out.y = -4 * Ease.OUT_SINE.apply(v);
			out.face = false;
			out.highlight = 0;
		} else if (u < 1) {
			double v = (u - 0.5) / 0.5;
			out.scaleX = Ease.outBack(1.3, v);
			out.y = -4 * (1 - Ease.IN_OUT_QUAD.apply(v));
			out.face = true;
			out.highlight = 0.2 * (1 - v);
		} else {
			out.scaleX = 1;
			out.y = 0;
			out.face = true;
			out.highlight = 0;
		}
		return out;
	}

	/** Reduced-motion flip: alpha of the face drawn over the back (100 ms cross-fade). */
	public static double reducedFlipFace(double t) {
		return clamp(t);
	}

	// ---- K3 squeeze -----------------------------------------------------------------------------------------------

	/**
	 * Revealed fraction f(u) of a squeezed card (§4.3): corner lift to 0.18, the breath (hold), the middle to 0.60,
	 * slow to 0.72, then the snap to 1 at u = 0.88. Monotonic and the same for every card (§0.7.7).
	 */
	public static double squeeze(double u) {
		double x = clamp(u);
		if (x < 0.20) return 0.18 * Ease.IN_OUT_SINE.apply(x / 0.20);
		if (x < 0.40) return 0.18;
		if (x < 0.80) return 0.18 + 0.42 * Ease.IN_OUT_SINE.apply((x - 0.40) / 0.40);
		if (x < 0.88) return 0.60 + 0.12 * ((x - 0.80) / 0.08);
		return 1.0;
	}

	/** Scale pop of the squeeze snap (1.08 → 1, outBack over u 0.88 → 1). */
	public static double squeezePop(double u) {
		double x = clamp(u);
		if (x < 0.88) return 1;
		return 1.08 - 0.08 * Ease.outBack(1.70158, (x - 0.88) / 0.12);
	}

	/** Lift of the squeezed card during the corner lift (px, 0 → 2 → stays until the snap). */
	public static double squeezeLift(double u) {
		double x = clamp(u);
		if (x >= 0.88) return 0;
		return 2 * Ease.OUT_QUAD.apply(Math.min(1, x / 0.20));
	}

	/** Curl sprite scale at the reveal boundary: {@code 1 + 0.5·sin(πf)}. */
	public static double curlScale(double f) {
		return 1 + 0.5 * Math.sin(Math.PI * clamp(f));
	}

	// ---- K4 slide, K5 gather, K6 muck -----------------------------------------------------------------------------

	/** K4 slide progress (outCubic). */
	public static double slide(double t) {
		return Ease.OUT_CUBIC.apply(t);
	}

	/**
	 * K5 gather of one card from its slot to the tray: inCubic travel, tilt 0 → 8°, fade over the last 80 ms (of 280),
	 * faces squash to backs mid-flight. Reduced motion: a fade in place.
	 */
	public static Pose gather(Pose out, double sx, double sy, double tx, double ty, double t, boolean faceUp, boolean reduced) {
		double u = clamp(t);
		if (reduced) {
			out.rest(sx, sy, 0, faceUp);
			out.alpha = 1 - u;
			return out;
		}
		double p = Ease.IN_CUBIC.apply(u);
		out.x = sx + (tx - sx) * p;
		out.y = sy + (ty - sy) * p;
		out.rot = 8 * u;
		out.scale = 1;
		double fadeFrom = 1 - 80.0 / GATHER_MS;
		out.alpha = u < fadeFrom ? 1 : Math.max(0, 1 - (u - fadeFrom) / (1 - fadeFrom));
		// faces become backs mid-flight: a short squash around u = 0.45
		double sq = Math.abs(u - 0.45) / 0.1;
		out.scaleX = faceUp && sq < 1 ? Math.max(0, sq) : 1;
		out.face = faceUp && u < 0.45;
		out.shadowDx = 1;
		out.shadowDy = 1;
		out.shadowAlpha = 0.22 * out.alpha;
		out.highlight = 0;
		return out;
	}

	/** Gather start (ms) of card {@code index} in the gather order (25 ms stagger). */
	public static int gatherDelay(int index) {
		return Math.max(0, index) * GATHER_STAGGER_MS;
	}

	/** Total gather length for {@code cards} cards. */
	public static int gatherTotal(int cards) {
		return cards <= 0 ? 0 : gatherDelay(cards - 1) + GATHER_MS;
	}

	/** K6 muck: inCubic slide toward the muck, rotate 25°, fade over the last 120 ms (of 300). */
	public static Pose muck(Pose out, double sx, double sy, double tx, double ty, double t, boolean reduced) {
		double u = clamp(t);
		if (reduced) {
			out.rest(sx, sy, 0, false);
			out.alpha = 1 - u;
			return out;
		}
		double p = Ease.IN_CUBIC.apply(u);
		out.rest(sx + (tx - sx) * p, sy + (ty - sy) * p, 25 * u, false);
		double fadeFrom = 1 - 120.0 / MUCK_MS;
		out.alpha = u < fadeFrom ? 1 : Math.max(0, 1 - (u - fadeFrom) / (1 - fadeFrom));
		return out;
	}

	// ---- K7 stamp, K8 shimmer, K9 glow, K10 badge, K11 desaturate --------------------------------------------------

	/** K7 stamp: scale 1.4 → 1 (outBack), alpha 0 → 1, rotation {@code deg}. Reduced motion: fade-in, no rotation. */
	public static Pose stamp(Pose out, double t, double deg, boolean reduced) {
		double u = clamp(t);
		out.rest(0, 0, reduced ? 0 : deg, true);
		if (reduced) {
			out.alpha = clamp(t * STAMP_MS / (double) REDUCED_FADE_MS);
			return out;
		}
		out.scale = 1.4 - 0.4 * Ease.OUT_BACK.apply(u);
		out.alpha = Math.min(1, u * 2);
		return out;
	}

	/** K8 shimmer band position: the band's left edge from {@code -band} to {@code width} (inOutQuad). */
	public static double shimmerX(double t, int width, int band) {
		return -band + (width + band) * Ease.IN_OUT_QUAD.apply(t);
	}

	/** K9 glow ring alpha: 0.35 ↔ 0.8 at 1 Hz (inOutSine); static 0.6 reduced, 0.5 with flashes off. */
	public static double glowAlpha(long ms, boolean reduced, boolean flashes) {
		if (reduced) return 0.6;
		if (!flashes) return 0.5;
		double phase = (Math.floorMod(ms, 1000L)) / 1000.0;
		double tri = phase < 0.5 ? phase * 2 : 2 - phase * 2;
		return 0.35 + 0.45 * Ease.IN_OUT_SINE.apply(tri);
	}

	/** K10 badge roll: the old digits' y offset (0 → −6) and the new ones' (6 → 0) at {@code t}. */
	public static double badgeOldDy(double t) {
		return -6 * Ease.OUT_CUBIC.apply(t);
	}

	public static double badgeNewDy(double t) {
		return 6 * (1 - Ease.OUT_CUBIC.apply(t));
	}

	/** K11 desaturation amount 0 → 1 over {@link #DESAT_MS}. */
	public static double desat(double t) {
		return Ease.OUT_QUAD.apply(t);
	}

	/** Bust badge shake: ±2 px, 3 times over {@link #BUST_SHAKE_MS}. */
	public static int bustShakePx(double t) {
		double u = clamp(t);
		if (u >= 1) return 0;
		return (int) Math.round(2 * Math.sin(u * Math.PI * 6) * (1 - u));
	}

	// ---- K12 tag, K13 knock ---------------------------------------------------------------------------------------

	/** K12 action tag at {@code ms} since it appeared: alpha. */
	public static double tagAlpha(long ms) {
		if (ms < 0) return 0;
		if (ms < TAG_IN_MS) return ms / (double) TAG_IN_MS;
		if (ms < TAG_IN_MS + TAG_HOLD_MS) return 1;
		return Math.max(0, 1 - (ms - TAG_IN_MS - TAG_HOLD_MS) / (double) TAG_OUT_MS);
	}

	/** K12 tag rise: 6 px → 0 (outCubic) while it appears. */
	public static double tagDy(long ms) {
		if (ms >= TAG_IN_MS) return 0;
		return 6 * (1 - Ease.OUT_CUBIC.apply(Math.max(0, ms) / (double) TAG_IN_MS));
	}

	public static int tagTotalMs() {
		return TAG_IN_MS + TAG_HOLD_MS + TAG_OUT_MS;
	}

	/** K13 knock: the plate jumps 2 px down and back twice. */
	public static int knockDy(double t) {
		double u = clamp(t);
		if (u >= 1) return 0;
		return Math.sin(u * Math.PI * 2) > 0 ? 2 : 0;
	}

	// ---- chips (§0.4) ---------------------------------------------------------------------------------------------

	/** Chip flight: Bézier (arc 12 px) with an apex scale of 1.25 and a landing squash (scale back to 1). */
	public static Pose chipFlight(Pose out, double sx, double sy, double tx, double ty, double t, boolean reduced) {
		double u = clamp(t);
		if (reduced) {
			out.rest(tx, ty, 0, true);
			out.alpha = clamp(t * CHIP_FLIGHT_MS / (double) REDUCED_FADE_MS);
			return out;
		}
		double p = Ease.OUT_CUBIC.apply(u);
		double cx = (sx + tx) / 2;
		double cy = Math.min(sy, ty) - 12;
		double a = 1 - p;
		out.rest(a * a * sx + 2 * a * p * cx + p * p * tx, a * a * sy + 2 * a * p * cy + p * p * ty, 0, true);
		out.scale = 1 + 0.25 * Math.sin(Math.PI * u);
		return out;
	}

	/** Sweep toward the rack (inCubic); alpha fades over the last third. */
	public static Pose sweep(Pose out, double sx, double sy, double tx, double ty, double t, boolean reduced) {
		double u = clamp(t);
		if (reduced) {
			out.rest(sx, sy, 0, true);
			out.alpha = 1 - u;
			return out;
		}
		double p = Ease.IN_CUBIC.apply(u);
		out.rest(sx + (tx - sx) * p, sy + (ty - sy) * p, 0, true);
		out.alpha = u < 0.66 ? 1 : Math.max(0, 1 - (u - 0.66) / 0.34);
		return out;
	}

	/** Push wiggle: ±1 px twice over {@link #PUSH_WIGGLE_MS}. */
	public static int wigglePx(double t) {
		double u = clamp(t);
		if (u >= 1) return 0;
		return (int) Math.signum(Math.sin(u * Math.PI * 4));
	}

	/** Chemin de fer shoe pass / pot slide progress (inOutCubic). */
	public static double pass(double t) {
		return Ease.IN_OUT_CUBIC.apply(t);
	}

	/** Bead drop: y offset −10 → 0 (outBounce). */
	public static double beadDropDy(double t) {
		return -10 * (1 - Ease.OUT_BOUNCE.apply(t));
	}

	// ---- helpers --------------------------------------------------------------------------------------------------

	/** Progress of an animation that started at {@code startMs} and lasts {@code durMs}, clamped to [0, 1]. */
	public static double progress(double nowMs, double startMs, int durMs) {
		if (durMs <= 0) return nowMs >= startMs ? 1 : 0;
		return clamp((nowMs - startMs) / durMs);
	}

	/** Catch-up (§0.2): a tween is played only while {@code now − beat < 2 × duration}; otherwise drawn settled. */
	public static boolean playTween(double elapsedMs, int durMs) {
		return elapsedMs < 2.0 * durMs;
	}

	static double clamp(double t) {
		return t <= 0 ? 0 : t >= 1 ? 1 : t;
	}
}
