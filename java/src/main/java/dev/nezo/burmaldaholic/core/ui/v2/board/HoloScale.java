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
	public static final double HYSTERESIS = 0.5;

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
	/** Base text px of the v2 board / plates (up close, before the distance growth). */
	public static final float BOARD_READ = 1 / 48f, PLATE_READ = 1 / 60f;

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
		double d = Math.min(Math.max(distance, 1), READ_RANGE);
		double need = capTarget(d) / (CAP * pxPerBlock(d, fovYDeg, screenH));
		return (float) Math.max(base, need);
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
