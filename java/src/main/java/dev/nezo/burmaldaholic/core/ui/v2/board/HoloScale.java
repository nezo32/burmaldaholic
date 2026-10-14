package dev.nezo.burmaldaholic.core.ui.v2.board;

/**
 * Size and detail of the in-world table board and seat plates (docs/architecture/ui-v2.md §7.3). The owner's request:
 * the game on the table must be READABLE from 3 to 8 blocks. A fixed 1/80-block text pixel (REDESIGN.md §9.2) gives a
 * 9.6 px glyph at 8 blocks on a 1080p screen — below the floor. Instead the text pixel grows with distance so the
 * glyph never drops below {@link #MIN_GLYPH_PX} screen px inside the readable range, and never shrinks below the base
 * size up close (no giant text at 1 block: the base is the floor, the cap stops growth past {@link #READ_RANGE}).
 */
public final class HoloScale {
	/** Vanilla glyph height in text px. */
	public static final int GLYPH = 8;
	/** Minimum glyph height in screen px inside {@link #READ_RANGE} (≈ the vanilla nametag at 6 blocks). */
	public static final double MIN_GLYPH_PX = 13;
	public static final double READ_RANGE = 8;
	public static final float BOARD_BASE = 1 / 80f, PLATE_BASE = 1 / 100f;

	public enum Lod {
		/** ≤ 8 blocks: board (all lines), seat plates, cards, chips, tweens. */
		FULL,
		/** 8–16: board state + result lines only, cards and chips, no plates. */
		BOARD,
		/** 16–32: felt, cards and chips settled, no text. */
		NO_TEXT,
		/** > 32: block model only. */
		MODEL
	}

	private HoloScale() {}

	public static Lod lod(double distance) {
		if (distance <= READ_RANGE) return Lod.FULL;
		if (distance <= 16) return Lod.BOARD;
		if (distance <= 32) return Lod.NO_TEXT;
		return Lod.MODEL;
	}

	/** Hysteresis of the LOD thresholds (blocks): no flicker for a player standing on a boundary. */
	public static final double HYSTERESIS = 1.0;

	/** The ink panel behind every board / plate text (90 % opaque) and the muted text colour drawn on it. */
	public static final int PANEL = 0xE6180A28, MUTED = 0xFFB8A8D0;
	/** WCAG AA contrast for body text: the muted ink must keep it on the panel over any background. */
	public static final double MIN_CONTRAST = 4.5;

	/**
	 * The vertical FOV actually rendered (degrees), from the camera's FOV (which includes sprint / effect / zoom
	 * modifiers); falls back to the option when the camera reports none (finding R-KW m14).
	 */
	public static double effectiveFov(double cameraFov, double optionFov) {
		return cameraFov > 1 && cameraFov < 179 ? cameraFov : optionFov;
	}

	/** {@code argb} composited over the opaque {@code bg} (alpha blend), opaque result. */
	public static int over(int argb, int bg) {
		double a = (argb >>> 24) / 255.0;
		int r = (int) Math.round(((argb >> 16) & 0xFF) * a + ((bg >> 16) & 0xFF) * (1 - a));
		int g = (int) Math.round(((argb >> 8) & 0xFF) * a + ((bg >> 8) & 0xFF) * (1 - a));
		int b = (int) Math.round((argb & 0xFF) * a + (bg & 0xFF) * (1 - a));
		return 0xFF000000 | r << 16 | g << 8 | b;
	}

	/** WCAG 2.1 contrast ratio of two opaque colours (1 … 21). */
	public static double contrast(int c1, int c2) {
		double l1 = luminance(c1), l2 = luminance(c2);
		return (Math.max(l1, l2) + 0.05) / (Math.min(l1, l2) + 0.05);
	}

	private static double luminance(int c) {
		return 0.2126 * channel(c >> 16) + 0.7152 * channel(c >> 8) + 0.0722 * channel(c);
	}

	private static double channel(int v) {
		double s = (v & 0xFF) / 255.0;
		return s <= 0.03928 ? s / 12.92 : Math.pow((s + 0.055) / 1.055, 2.4);
	}

	/**
	 * LOD with hysteresis: keeps {@code previous} while the distance is within {@link #HYSTERESIS} of the boundary
	 * that separates it from the plain {@link #lod(double)} answer.
	 */
	public static Lod lod(double distance, Lod previous) {
		Lod plain = lod(distance);
		if (previous == null || plain == previous) return plain;
		Lod nearer = plain.ordinal() < previous.ordinal() ? lod(distance + HYSTERESIS) : lod(distance - HYSTERESIS);
		return nearer == previous ? previous : plain;
	}

	/** Cap height of the vanilla font in text px (letters and digits are 7 px tall in an 8 px box). */
	public static final int CAP = 7;
	/** Readability targets measured on screenshots (orchestrator review): cap height ≥ 18 px up to 5 blocks, ≥ 13 px at 8. */
	public static final double CAP_NEAR_PX = 18, CAP_FAR_PX = 13, NEAR = 5;
	/**
	 * Base text px of the v2 board / plates (the floor up close, before the distance growth): 1/60 and 1/75 block per
	 * text px (design audit W P0) — at 3 blocks the cap is ≈ 26 / 21 px (enough; larger plates collided on 6-max
	 * Hold'em), from ≈ 4 blocks on {@link #readablePx} grows them to the 18 → 13 px targets.
	 */
	public static final float BOARD_READ = 1 / 60f, PLATE_READ = 1 / 75f;

	/** Target cap height in screen px at {@code distance} (18 up to 5 blocks, falling linearly to 13 at 8). */
	public static double capTarget(double distance) {
		if (distance <= NEAR) return CAP_NEAR_PX;
		double d = Math.min(distance, READ_RANGE);
		return CAP_NEAR_PX + (CAP_FAR_PX - CAP_NEAR_PX) * (d - NEAR) / (READ_RANGE - NEAR);
	}

	/**
	 * Text px (block size of one font px) so the CAP height on screen meets {@link #capTarget} at {@code distance}
	 * (1920 × 1080, the player's FOV), never below {@code base}; constant past {@link #READ_RANGE}.
	 */
	public static float readablePx(float base, double distance, double fovYDeg, int screenH) {
		double d = Math.min(Math.max(distance, 0.5), READ_RANGE);
		double need = capTarget(d) / (CAP * pxPerBlock(d, fovYDeg, screenH));
		double px = Math.max(base, need);
		// never larger on screen than CAP_MAX_PX (scaled with the window height; ≥ the 18 px target, so the minimum
		// still holds): up close the base would dominate the scene
		double max = capMax(screenH) / (CAP * pxPerBlock(Math.max(distance, 0.5), fovYDeg, screenH));
		return (float) Math.min(px, max);
	}

	/** Largest cap height on screen at 1080 px (visual QA round 1: ≤ 22 px @ 1080p, ≤ 44 px @ 4K). */
	public static final double CAP_MAX_PX = 22;

	/** {@link #CAP_MAX_PX} for a framebuffer height. */
	public static double capMax(int screenH) {
		return CAP_MAX_PX * screenH / 1080.0;
	}

	/** Cap height in screen px of a text px at a distance. */
	public static double capPx(float textPx, double distance, double fovYDeg, int screenH) {
		return CAP * textPx * pxPerBlock(distance, fovYDeg, screenH);
	}

	/** Screen px per block at {@code distance} for a vertical FOV and a framebuffer height. */
	public static double pxPerBlock(double distance, double fovYDeg, int screenH) {
		double d = Math.max(0.5, distance);
		return screenH / (2 * d * Math.tan(Math.toRadians(fovYDeg) / 2));
	}

	/**
	 * Block size of one text px: {@code base} up close, growing so the glyph stays ≥ {@link #MIN_GLYPH_PX} up to
	 * {@link #READ_RANGE}, then constant (it shrinks on screen with distance, as everything in the world does).
	 */
	public static float textPx(float base, double distance, double fovYDeg, int screenH) {
		double d = Math.min(distance, READ_RANGE);
		double need = MIN_GLYPH_PX / (GLYPH * pxPerBlock(d, fovYDeg, screenH));
		return (float) Math.max(base, need);
	}

	/** Glyph height in screen px for a text px size at a distance (what the tests assert). */
	public static double glyphPx(float textPx, double distance, double fovYDeg, int screenH) {
		return GLYPH * textPx * pxPerBlock(distance, fovYDeg, screenH);
	}
}
