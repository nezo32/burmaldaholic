package dev.nezo.burmaldaholic.games.roulette.present;

import dev.nezo.burmaldaholic.core.anim.Ease;
import dev.nezo.burmaldaholic.core.anim.motion.ArtScale;
import dev.nezo.burmaldaholic.core.anim.motion.PixelSnap;
import dev.nezo.burmaldaholic.core.ui.v2.Box;

/**
 * Spin focus (ROULETTE-ANIM-SPEC §3.1, §3.3): during the spin the wheel leaves its column and grows to the largest NATIVE
 * wheel sprite (104 / 144 / 208) times an integer pose multiple that fits {@code min(PlayArea.h − 16, 0.55·PlayArea.w)}
 * ({@link ArtScale#pickNative}), centred at {@code play.x + 8 + D/2}, vertically centred, its origin on the physical
 * pixel grid. Pure: the screen draws it and the unit test checks every window size.
 *
 * <p>Timeline (shared NO_MORE_BETS time, local RESULT time): NMB 0–350 ms the column wheel slides and grows to the focus
 * spot ({@code inOutCubic}), 350–470 ms the native focus sprite fades in over it (same rotor angle, shared head frame
 * index); RESULT 0–400 ms the reverse (the size swaps back at 50 %).
 *
 * @param art     native focus sprite size (104, 144 or 208)
 * @param mult    integer pose multiple (1 or 2)
 * @param size    drawn diameter in layout px ({@code art·mult})
 * @param cx      focus centre x (layout px, on the 1/k grid)
 * @param cy      focus centre y
 * @param colArt  the column wheel's sprite size
 * @param colCx   column wheel centre x
 * @param colCy   column wheel centre y
 * @param k       physical px per layout px
 * @param enabled false when the focus wheel would not be larger than the column wheel (then the wheel stays put)
 */
public record RouletteFocus(int art, int mult, double size, double cx, double cy, int colArt, double colCx, double colCy, int k, boolean enabled) {
	public static final int[] NATIVE = {104, 144, 208};
	public static final double SLIDE_MS = 350, SWAP_END_MS = 470, RETURN_MS = 400;

	public static RouletteFocus of(RouletteLayout l) {
		Box play = l.grid.play;
		int k = Math.max(1, l.grid.viewport.k());
		double avail = Math.min(play.h() - 16, 0.55 * play.w());
		ArtScale.Result r = ArtScale.pickNative(NATIVE, avail, k);
		int art = r.artW();
		int mult = r.integral() ? (int) Math.round(r.scale()) : 1;
		boolean ok = r.integral() && art * mult > l.wheelD;
		double size = ok ? art * mult : l.wheelD;
		double cx = PixelSnap.snap(play.x() + 8 + size / 2.0, k);
		double cy = PixelSnap.snap(play.y() + play.h() / 2.0, k);
		return new RouletteFocus(ok ? art : l.wheelD, ok ? mult : 1, size, cx, cy, l.wheelD, l.wheel.x() + l.wheelD / 2.0, l.wheel.y() + l.wheelD / 2.0, k, ok);
	}

	/** Focus amount 0 (column) … 1 (focus) during NO_MORE_BETS / SPIN at {@code ms} since the launch. */
	public static double inAmount(double ms) {
		return Ease.IN_OUT_CUBIC.apply(Math.max(0, Math.min(1, ms / SLIDE_MS)));
	}

	/** Alpha of the native focus sprite over the grown column sprite at {@code ms} since the launch. */
	public static double swapAlpha(double ms) {
		return Math.max(0, Math.min(1, (ms - SLIDE_MS) / (SWAP_END_MS - SLIDE_MS)));
	}

	/** Focus amount during RESULT at local {@code ms} (1 → 0). */
	public static double outAmount(double ms) {
		return 1 - Ease.IN_OUT_CUBIC.apply(Math.max(0, Math.min(1, ms / RETURN_MS)));
	}

	/** Centre x at focus amount {@code a}, snapped to the physical grid. */
	public double x(double a) {
		return PixelSnap.snap(colCx + (cx - colCx) * a, k);
	}

	public double y(double a) {
		return PixelSnap.snap(colCy + (cy - colCy) * a, k);
	}

	/** Drawn diameter at focus amount {@code a}. */
	public double diameter(double a) {
		return colArt + (size - colArt) * a;
	}

	/** The focus wheel's box at full focus. */
	public Box box() {
		int s = (int) Math.ceil(size);
		return new Box((int) Math.floor(cx - size / 2), (int) Math.floor(cy - size / 2), s, s);
	}
}
