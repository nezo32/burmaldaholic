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
