package dev.nezo.burmaldaholic.games.slots.cabinet;

import dev.nezo.burmaldaholic.games.slots.v2.logic.Machine;

/**
 * Marquee bulb patterns of the cabinet (slots.md §5.2 "Marquee"). PURE: {@link #color} is a function of the
 * pattern, the bulb and the time only, so every spectator sees the same lights. Blinking is ≤ 4 Hz and cell-sized
 * (flash rules, slots.md §2.4); with flashes off every blink becomes a steady glow.
 */
public final class Marquee {
	/** Bulbs along the marquee (front face). */
	public static final int BULBS = 12;

	public enum Pattern {
		/** Idle attract chase: one bulb step per 3 ticks. Never implies a win. */
		IDLE,
		/** Spinning: fast chase, one step per tick. */
		SPIN,
		/** Feature running (free spins, Hoard, Wheel, Hunt): chase in the free-spin theme colour. */
		FEATURE,
		/** Win (1–15 ×): all bulbs blink at 2 Hz. */
		WIN,
		/** Big and above: alternating halves at 4 Hz. */
		BIG,
		/** Jackpot: rainbow cycle. */
		JACKPOT
	}

	private Marquee() {}

	/** Bulb colour of the machine (ARGB, lit). */
	public static int bulbColor(Machine m) {
		return switch (m) {
			case OVERWORLD -> 0xFFFFD640;
			case NETHER -> 0xFFFF7A1A;
			case END -> 0xFFF4ECF8;
		};
	}

	/** Free-spin theme colour (slots.md §3.1: Night Watch, Inferno, Void Walker). */
	public static int featureColor(Machine m) {
		return switch (m) {
			case OVERWORLD -> 0xFF8FB8FF;
			case NETHER -> 0xFFFF3A1A;
			case END -> 0xFFB040FF;
		};
	}

	/** Unlit bulb (dark glass tinted by the machine). */
	public static int offColor(Machine m) {
		return switch (m) {
			case OVERWORLD -> 0xFF4A3A20;
			case NETHER -> 0xFF3A1A10;
			case END -> 0xFF2A2030;
		};
	}

	/**
	 * Colour of bulb {@code i} at {@code ms}.
	 *
	 * @param ms      pattern clock: level ms for IDLE (attract runs on world time), ms since the pattern
	 *                started otherwise
	 * @param flashes {@code anim.flashes}: false turns blinking patterns into a steady glow
	 */
	public static int color(Pattern p, Machine m, int i, double ms, boolean flashes) {
		int on = bulbColor(m);
		int off = offColor(m);
		return switch (p) {
			case IDLE -> chase(i, ms, 150, on, off);
			case SPIN -> chase(i, ms, 50, on, off);
			case FEATURE -> chase(i, ms, 100, featureColor(m), off);
			case WIN -> !flashes || (long) Math.floor(ms / 250) % 2 == 0 ? on : off;
			case BIG -> {
				if (!flashes) yield on;
				boolean phase = (long) Math.floor(ms / 125) % 2 == 0;
				yield (i % 2 == 0) == phase ? on : featureColor(m);
			}
			case JACKPOT -> flashes ? rainbow((i / (double) BULBS + ms / 1200.0) % 1.0) : 0xFFFF40C0;
		};
	}

	/** Marquee texture variant ({@code <m>_marquee[_fs|_jackpot].png}, lane B-L10). */
	public enum Variant {
		BASE(""),
		FREE_SPINS("_fs"),
		JACKPOT("_jackpot");

		public final String suffix;

		Variant(String suffix) {
			this.suffix = suffix;
		}
	}

	/** Frames of the generated marquee strip (64 × 16 each): 0–3 chase, 4 all on, 5 all off, 6–7 alternating halves. */
	public static final int FRAMES = 8;

	public static Variant variant(Pattern p) {
		return switch (p) {
			case FEATURE -> Variant.FREE_SPINS;
			case JACKPOT -> Variant.JACKPOT;
			default -> Variant.BASE;
		};
	}

	/**
	 * Frame of the generated marquee strip for a pattern at {@code ms} (same clocks as {@link #color}); with flashes
	 * off every blinking pattern holds frame 4 (all on).
	 */
	public static int frame(Pattern p, double ms, boolean flashes) {
		long step = switch (p) {
			case IDLE -> (long) Math.floor(ms / 150);
			case SPIN -> (long) Math.floor(ms / 50);
			case FEATURE -> (long) Math.floor(ms / 100);
			case JACKPOT -> (long) Math.floor(ms / 80);
			case WIN -> (long) Math.floor(ms / 250);
			case BIG -> (long) Math.floor(ms / 125);
		};
		return switch (p) {
			case IDLE, SPIN, FEATURE -> (int) Math.floorMod(step, 4L);
			case JACKPOT -> flashes ? (int) Math.floorMod(step, 4L) : 4;
			case WIN -> flashes ? 4 + (int) Math.floorMod(step, 2L) : 4;
			case BIG -> flashes ? 6 + (int) Math.floorMod(step, 2L) : 4;
		};
	}

	/** Chase: the head bulb at full colour, a 2-bulb fading tail, the rest dim. */
	static int chase(int i, double ms, int stepMs, int on, int off) {
		long head = Math.floorMod((long) Math.floor(ms / stepMs), BULBS);
		int d = (int) Math.floorMod(head - i, BULBS);
		return switch (d) {
			case 0 -> on;
			case 1 -> mix(on, off, 0.45f);
			case 2 -> mix(on, off, 0.75f);
			default -> mix(on, off, 0.88f);
		};
	}

	/** Linear ARGB mix: {@code t} = 0 → a, 1 → b. */
	public static int mix(int a, int b, float t) {
		int r = 0;
		for (int s = 0; s <= 24; s += 8) {
			int ca = a >>> s & 0xFF;
			int cb = b >>> s & 0xFF;
			r |= Math.round(ca + (cb - ca) * t) << s;
		}
		return r;
	}

	/** Saturated rainbow hue (0–1) → ARGB. */
	public static int rainbow(double h) {
		double x = (h % 1.0 + 1.0) % 1.0 * 6;
		int k = (int) x;
		float f = (float) (x - k);
		int up = Math.round(255 * f);
		int down = 255 - up;
		int rgb = switch (k) {
			case 0 -> 0xFF0000 | up << 8;
			case 1 -> down << 16 | 0xFF00;
			case 2 -> 0xFF00 | up;
			case 3 -> down << 8 | 0xFF;
			case 4 -> up << 16 | 0xFF;
			default -> 0xFF0000 | down;
		};
		return 0xFF000000 | rgb;
	}
}
