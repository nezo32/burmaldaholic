package dev.nezo.burmaldaholic.chaos.logic;

import dev.nezo.burmaldaholic.core.anim.Ease;

/**
 * PURE presentation maths of the chaos layer (global.md §4.5, §4.6): kind tone (colour + sound), the chaos card
 * flip, curse shake / vignette, teleport fade, spawn patterns (rings, spirals, glint columns, motes) and the Golden
 * Hour vignette envelope. Time is milliseconds from the beat start; colours are ARGB palette tokens (global §2.1).
 * No Minecraft types, so JUnit pins every curve ({@code ChaosFxMathTest}).
 *
 * <p>Fidelity (global §6): these functions only shape <em>where</em> and <em>how</em> FX show; every position comes
 * from the server (real item / mob / teleport spots). Nothing here draws or predicts an outcome.
 */
public final class ChaosFxMath {
	/** Mob-wave rune lead: the rune appears this many ticks before the mob (global §4.6: 900 ms). */
	public static final int RUNE_LEAD_TICKS = 18;
	/** Diamond-rain glint column fall time: the real item spawns when the column lands (400 ms). */
	public static final int COLUMN_TICKS = 8;
	public static final int COLUMN_MS = COLUMN_TICKS * 50;
	/** Column top above the drop point, blocks. */
	public static final double COLUMN_HEIGHT = 6;
	/** Teleport: fade out, teleport tick, fade in (250 ms each side). */
	public static final int TELEPORT_LEAD_TICKS = 5;
	public static final int TELEPORT_FADE_MS = 250;
	/** Chaos card: flip in, hold for the title (5 / 50 / 15 ticks), flip out. */
	public static final int CARD_IN_MS = 250;
	public static final int CARD_TOTAL_MS = (5 + 50 + 15) * 50;
	public static final int CARD_OUT_MS = 250;
	/** Curse: 300 ms shake (3 px), 400 ms vignette at ≤ 30 %. */
	public static final int SHAKE_MS = 300;
	public static final int SHAKE_PX = 3;
	public static final int CURSE_VIGNETTE_MS = 400;

	/** Golden Hour start: vignette 0 → 30 % over 600 ms, settles to 12 % by 3 000 ms; end fades out over 1 000 ms. */
	public static final int GH_RISE_MS = 600;
	public static final int GH_SETTLE_START_MS = 2400;
	public static final int GH_SETTLE_MS = 3000;
	public static final int GH_END_MS = 1000;
	public static final double GH_PEAK = 0.30;
	public static final double GH_STEADY = 0.12;
	/** 24 golden motes rise around the player between 300 and 2 300 ms. */
	public static final int GH_MOTES = 24;
	public static final int GH_MOTES_FROM_MS = 300;
	public static final int GH_MOTES_TO_MS = 2300;

	private ChaosFxMath() {}

	/** How an event reads before the player reads the title: colour + sound (global §4.6 "kind colour"). */
	public enum Tone {
		GOOD(0xFF80FF40, 0xFFFFD640, "chaos_good"),
		BAD(0xFFD83440, 0xFF6FA86A, "chaos_bad"),
		NEUTRAL(0xFFD696FF, 0xFFD696FF, "chaos_teleport");

		/** Main colour (title / card edge / ring tint). */
		public final int color;
		/** Accent (gold for good, curse green for bad). */
		public final int accent;
		/** Sound catalog id ({@code chaos} module). */
		public final String sound;

		Tone(int color, int accent, String sound) {
			this.color = color;
			this.accent = accent;
			this.sound = sound;
		}

		public static Tone of(ChaosEvent.Kind kind) {
			return switch (kind) {
				case GOOD -> GOOD;
				case BAD -> BAD;
				case NEUTRAL -> NEUTRAL;
			};
		}
	}

	private static double clamp01(double v) {
		return v <= 0 ? 0 : v >= 1 ? 1 : v;
	}

	// ---- chaos card ------------------------------------------------------------------------

	/**
	 * Horizontal scale of the chaos card (y-axis squash flip, global §4.6): 0 → 1 over {@link #CARD_IN_MS}
	 * ({@code outBack}), 1 while the title shows, 1 → 0 over the last {@link #CARD_OUT_MS}. Reduced motion: 1 (no flip).
	 */
	public static double cardScaleX(double ms, boolean reduced) {
		if (ms < 0 || ms >= CARD_TOTAL_MS) return 0;
		if (reduced) return 1;
		if (ms < CARD_IN_MS) return Ease.OUT_BACK.apply(ms / CARD_IN_MS);
		double out = CARD_TOTAL_MS - CARD_OUT_MS;
		if (ms > out) return 1 - Ease.IN_CUBIC.apply((ms - out) / CARD_OUT_MS);
		return 1;
	}

	/** Card alpha: fades in with the flip, fades out over the last 250 ms (reduced motion: the same, no flip). */
	public static double cardAlpha(double ms) {
		if (ms < 0 || ms >= CARD_TOTAL_MS) return 0;
		double in = clamp01(ms / CARD_IN_MS);
		double out = clamp01((CARD_TOTAL_MS - ms) / CARD_OUT_MS);
		return Math.min(in, out);
	}

	// ---- bad-event shake / curse vignette ----------------------------------------------------

	/** Horizontal GUI shake offset (px) of a bad event: {@code 3·shake(t)} over 300 ms; 0 under reduced motion. */
	public static double shakePx(double ms, boolean reduced) {
		if (reduced || ms < 0 || ms >= SHAKE_MS) return 0;
		return SHAKE_PX * Ease.SHAKE.apply(ms / SHAKE_MS);
	}

	/**
	 * Curse vignette alpha (green-purple): up to 30 % in 150 ms, down to 0 at 400 ms (a change slower than the 250 ms
	 * flash rule, ≤ 30 % otherwise). Flashes off: a static 10 % tint for the same 400 ms.
	 */
	public static double curseVignette(double ms, boolean flashes) {
		if (ms < 0 || ms >= CURSE_VIGNETTE_MS) return 0;
		if (!flashes) return 0.10;
		if (ms < 150) return 0.30 * Ease.OUT_QUAD.apply(ms / 150.0);
		return 0.30 * (1 - Ease.IN_OUT_QUAD.apply((ms - 150) / (CURSE_VIGNETTE_MS - 150.0)));
	}

	/**
	 * Teleport veil (white-lilac full screen) around the teleport tick at {@link #TELEPORT_LEAD_TICKS}: 0 → 85 % over
	 * the 250 ms before it, 85 % → 0 over the 250 ms after it. Flashes off / reduced motion: none.
	 */
	public static double teleportVeil(double ms, boolean flashes) {
		if (!flashes || ms < 0) return 0;
		double at = TELEPORT_LEAD_TICKS * 50.0;
		if (ms < at) return 0.85 * Ease.IN_OUT_QUAD.apply(ms / at);
		double after = ms - at;
		if (after >= TELEPORT_FADE_MS) return 0;
		return 0.85 * (1 - Ease.IN_OUT_QUAD.apply(after / TELEPORT_FADE_MS));
	}

	// ---- world spawn patterns ----------------------------------------------------------------

	/** Point {@code i} of an {@code n}-point ring of {@code radius} (x, z offsets), rotated by {@code phase} radians. */
	public static double[] ring(int i, int n, double radius, double phase) {
		double a = phase + i * Math.PI * 2 / Math.max(1, n);
		return new double[] {Math.cos(a) * radius, Math.sin(a) * radius};
	}

	/**
	 * Curse spiral (global §4.6: descending onto the player over 1.2 s): wisp {@code i} of {@code n} at progress
	 * {@code t} ∈ [0, 1] → (x, y, z) offsets from the player's feet. Starts 2.6 blocks up at radius 1.2, ends at the
	 * shoulders, radius 0.3, turning 1.5 times.
	 */
	public static double[] spiral(int i, int n, double t) {
		double u = clamp01(t);
		double a = (i * Math.PI * 2 / Math.max(1, n)) + u * Math.PI * 3;
		double r = 1.2 - 0.9 * Ease.IN_OUT_QUAD.apply(u);
		double y = 2.6 - 1.3 * u * u;
		return new double[] {Math.cos(a) * r, y, Math.sin(a) * r};
	}

	/** Height above the drop point of a diamond-rain glint column head at {@code ms} (6 → 0 over 400 ms, accelerating). */
	public static double columnHeight(double ms) {
		double t = clamp01(ms / COLUMN_MS);
		return COLUMN_HEIGHT * (1 - t * t);
	}

	/** Teleport ring radius: 0.5 → 2 blocks over 300 ms ({@code outCubic}). */
	public static double teleportRingRadius(double ms) {
		return 0.5 + 1.5 * Ease.OUT_CUBIC.apply(ms / 300.0);
	}

	/** Spawn time (ms from the start) of Golden Hour mote {@code i} of {@link #GH_MOTES}. */
	public static int moteSpawnMs(int i) {
		return GH_MOTES_FROM_MS + (int) Math.floor((GH_MOTES_TO_MS - GH_MOTES_FROM_MS) * (double) i / GH_MOTES);
	}

	// ---- Golden Hour -------------------------------------------------------------------------

	/**
	 * Golden Hour vignette alpha at {@code ms} after the start (global §4.5): rises to 30 % over 600 ms
	 * ({@code outCubic}), holds, settles to 12 % between 2 400 and 3 000 ms and stays there. Flashes off: 0 (the HUD and
	 * boss bar carry the information).
	 */
	public static double goldenVignette(double ms, boolean flashes) {
		if (!flashes || ms < 0) return 0;
		if (ms < GH_RISE_MS) return GH_PEAK * Ease.OUT_CUBIC.apply(ms / GH_RISE_MS);
		if (ms < GH_SETTLE_START_MS) return GH_PEAK;
		if (ms < GH_SETTLE_MS) return GH_PEAK - (GH_PEAK - GH_STEADY) * Ease.IN_OUT_QUAD.apply((ms - GH_SETTLE_START_MS) / (GH_SETTLE_MS - GH_SETTLE_START_MS));
		return GH_STEADY;
	}

	/** Golden Hour end: the steady vignette ({@code from}) fades to 0 over 1 000 ms. */
	public static double goldenVignetteEnd(double ms, double from) {
		if (ms < 0) return from;
		return from * (1 - Ease.IN_OUT_QUAD.apply(ms / GH_END_MS));
	}

	/**
	 * Client estimate of the remaining Golden Hour ticks: the server's last value minus the client ticks since it
	 * arrived, never below 1 while the server still reports it active (a timer may pause at 0 but never ends early,
	 * global §6.6).
	 */
	public static long remainingTicks(long serverTicks, long ticksSinceSync) {
		if (serverTicks <= 0) return 0;
		return Math.max(1, serverTicks - Math.max(0, ticksSinceSync));
	}

	/** "m:ss" of a tick count, rounded up to whole seconds (the timer reads 0:01 until the server ends it). */
	public static String clock(long ticks) {
		long s = Math.max(0, (ticks + 19) / 20);
		long m = s / 60;
		long r = s % 60;
		return m + ":" + (r < 10 ? "0" : "") + r;
	}

	/**
	 * Countdown punch of the last ten seconds: scale 1.25 → 1 over 300 ms at each whole second (reduced motion: 1).
	 * {@code ticks} is the remaining time; {@code partialMs} the time inside the current second.
	 */
	public static double countdownScale(long ticks, double msIntoSecond, boolean reduced) {
		if (reduced || ticks > 200 || ticks <= 0) return 1;
		double t = clamp01(msIntoSecond / 300.0);
		return 1 + 0.25 * (1 - Ease.OUT_CUBIC.apply(t));
	}
}
