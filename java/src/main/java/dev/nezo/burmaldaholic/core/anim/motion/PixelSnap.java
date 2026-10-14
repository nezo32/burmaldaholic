package dev.nezo.burmaldaholic.core.anim.motion;

/**
 * Physical-pixel snapping of MOVING things (PL §3.1, RO §3.1, DI §4.1, CS §4.2): a moving sprite is placed at a
 * multiple of {@code 1/k} layout px ({@code k} = physical px per layout px, e.g. the GUI scale) and blitted 1:1, so it
 * moves in 1-physical-px steps (smooth at 4K GUI 9) while every texel stays crisp. Statics stay on the art grid.
 *
 * <p>Rounding is {@link Math#round(double)} (half up), identical everywhere, so two viewers snap the same value alike.
 */
public final class PixelSnap {
	private PixelSnap() {}

	/** {@code gui} rounded to the nearest multiple of {@code 1/k} ({@code k ≥ 1}). */
	public static double snap(double gui, int k) {
		if (k < 1) throw new IllegalArgumentException("k " + k);
		return Math.round(gui * k) / (double) k;
	}

	/** {@code gui} rounded down to a multiple of {@code 1/k} (origins that must not leave their box). */
	public static double floor(double gui, int k) {
		if (k < 1) throw new IllegalArgumentException("k " + k);
		return Math.floor(gui * k) / k;
	}

	/** The physical pixel index of {@code gui} ({@code round(gui·k)}). */
	public static long phys(double gui, int k) {
		return Math.round(gui * k);
	}

	/**
	 * Snapped layout position of an art-space coordinate: {@code snap(origin + art·scale, k)} — the
	 * {@code xGui = round((ox + x·s)·G)/G} rule of the specs.
	 *
	 * @param origin stage origin in layout px (already snapped to 1/k, see {@link ArtScale.Result#originX})
	 * @param art    coordinate in art px (fractional while moving)
	 * @param scale  layout px per art px ({@link ArtScale.Result#scale()})
	 */
	public static double at(double origin, double art, double scale, int k) {
		return snap(origin + art * scale, k);
	}

	/**
	 * Physical-px position of texel edge {@code i} of a sprite drawn at float {@code x} with pose scale {@code scale}
	 * (layout px per art px), snapped first then scaled as {@code SnapDraw}'s scale overloads do:
	 * {@code (snap(x, k) + i·scale)·k}. An integer whenever {@code scale·k} (= n) is an integer.
	 */
	public static double texelEdge(double x, int i, double scale, int k) {
		return (snap(x, k) + i * scale) * k;
	}

	/** Float form of {@link #snap} for pose translations. */
	public static float snapF(double gui, int k) {
		return (float) snap(gui, k);
	}
}
