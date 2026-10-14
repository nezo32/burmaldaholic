package dev.nezo.burmaldaholic.core.anim.motion;

/**
 * Crisp art scale for an art stage in a layout box (PHASE2B §2.2.4; PL §3.1, DI §4.1, RO §3.1, CS §4.1).
 *
 * <p>With {@code k} physical px per layout px (the layout scale; the GUI scale until RS changes it) the available
 * ratio is {@code r = min(boxW·k/artW, boxH·k/artH)} physical px per art px. The stage uses {@code n = floor(r)} when
 * {@code n ≥ 0.8·r} (every art px becomes exactly n physical px: no uneven 8/9 px columns), else the fractional
 * {@code r} (accepted only when the integer would lose more than 20 % of the size). The layout scale of the stage is
 * {@code s = n/k} layout px per art px and its origin is snapped to {@code 1/k}.
 *
 * <p>Pure; a {@link Result} is built at layout time (one small allocation per layout, never per frame).
 */
public final class ArtScale {
	/** The integer scale is kept when it is at least this fraction of the available ratio. */
	public static final double INTEGER_KEEP = 0.8;

	/** Smallest ratio (physical px per art px) ever returned, for empty or degenerate boxes. */
	public static final double MIN_RATIO = 1e-3;

	private ArtScale() {}

	/**
	 * A chosen art scale.
	 *
	 * @param artW     art width (art px)
	 * @param artH     art height (art px)
	 * @param k        physical px per layout px
	 * @param r        available ratio (physical px per art px)
	 * @param n        chosen physical px per art px (an integer value when {@code integral})
	 * @param integral true when {@code n} is an integer (crisp, uniform art pixels)
	 * @param scale    layout px per art px ({@code n/k}); the pose scale of the stage
	 * @param w        stage width in layout px ({@code artW·scale})
	 * @param h        stage height in layout px ({@code artH·scale})
	 */
	public record Result(int artW, int artH, int k, double r, double n, boolean integral, double scale, double w, double h) {
		/**
		 * Stage origin x centred in a box starting at {@code boxX} (layout px) of width {@code boxW}, rounded DOWN to a
		 * multiple of {@code 1/k}: for an integer {@code boxX} the stage never leaves the box.
		 */
		public double originX(double boxX, double boxW) {
			return PixelSnap.floor(boxX + (boxW - w) / 2, k);
		}

		/** Stage origin y centred in a box (see {@link #originX}). */
		public double originY(double boxY, double boxH) {
			return PixelSnap.floor(boxY + (boxH - h) / 2, k);
		}

		/** Stage origin snapped to {@code 1/k} at a given (not centred) position. */
		public double snapOrigin(double gui) {
			return PixelSnap.floor(gui, k);
		}

		/** Physical px per art px as an int ({@code n} rounded; exact when {@link #integral}). */
		public int physPerArt() {
			return (int) Math.round(n);
		}

		/** True when the stage fits a {@code boxW × boxH} box (with a 1e-9 tolerance). */
		public boolean fits(double boxW, double boxH) {
			return w <= boxW + 1e-9 && h <= boxH + 1e-9;
		}
	}

	/**
	 * Picks the art scale of a {@code artW × artH} stage in a {@code boxW × boxH} layout box.
	 *
	 * @param artW art width in art px (e.g. Plinko board 272, dice arena 360)
	 * @param artH art height in art px (e.g. 204, 140)
	 * @param boxW available width in layout px
	 * @param boxH available height in layout px
	 * @param k    physical px per layout px (≥ 1)
	 */
	public static Result pick(int artW, int artH, double boxW, double boxH, int k) {
		if (artW <= 0 || artH <= 0 || k < 1) throw new IllegalArgumentException(artW + "x" + artH + " k" + k);
		double r = Math.min(boxW * k / artW, boxH * k / artH);
		// a degenerate box (≤ 0, NaN) must never give scale 0: draws would divide by it or throw in render
		if (!(r >= MIN_RATIO)) r = MIN_RATIO;
		double fl = Math.floor(r);
		boolean integral = fl >= 1 && fl >= INTEGER_KEEP * r;
		double n = integral ? fl : r;
		double s = n / k;
		return new Result(artW, artH, k, r, n, integral, s, artW * s, artH * s);
	}

	/**
	 * Picks a NATIVE pre-rendered sprite size and an integer pose multiple (RO §3.1 focus wheel): the largest size in
	 * {@code sizes} that fits {@code availGui} at 1×, then the largest integer multiple {@code m} of it that still
	 * fits. Physical px per art px is then {@code k·m} (always integral). When no size fits at 1×, the smallest size is
	 * scaled fractionally ({@link #pick}).
	 *
	 * @param sizes    native square sprite sizes in art px (= layout px at 1×), any order (e.g. 104, 144, 208)
	 * @param availGui available square edge in layout px
	 * @param k        physical px per layout px
	 */
	public static Result pickNative(int[] sizes, double availGui, int k) {
		if (sizes.length == 0 || k < 1) throw new IllegalArgumentException("sizes / k");
		int best = -1;
		int smallest = Integer.MAX_VALUE;
		for (int s : sizes) {
			if (s <= 0) throw new IllegalArgumentException("size " + s);
			smallest = Math.min(smallest, s);
			if (s <= availGui + 1e-9 && s > best) best = s;
		}
		if (best < 0) return pick(smallest, smallest, availGui, availGui, k);
		int m = Math.max(1, (int) Math.floor((availGui + 1e-9) / best));
		double r = availGui * k / best;
		return new Result(best, best, k, r, (double) k * m, true, m, (double) best * m, (double) best * m);
	}
}
