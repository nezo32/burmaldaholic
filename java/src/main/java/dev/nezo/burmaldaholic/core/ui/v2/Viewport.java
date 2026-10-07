package dev.nezo.burmaldaholic.core.ui.v2;

/**
 * The virtual GUI a table screen works in (docs/architecture/ui-v2.md §3.1). The player's own GUI scale is kept when
 * the GUI is between 320 × 240 and 539 px high; a smaller GUI steps DOWN to the largest whole scale that fits 320 × 240
 * (today's {@code FitScaled}); a GUI of 540 px or more steps UP by the whole factor {@code m = floor(H / 270)} (4K at
 * scale 4, 1080p at scale 2, …) so the table is not drawn at a postage-stamp size. Every art pixel stays a whole number
 * of framebuffer pixels: the effective scale {@link #k} is always an integer.
 *
 * <p>{@link #factor} = {@code k / guiScale} is what the screen draws its pose with and what the mouse mixin divides the
 * GUI mouse position by (one transform for drawing and hit-testing).
 *
 * @param w       virtual GUI width (layout space)
 * @param h       virtual GUI height
 * @param k       effective whole GUI scale (framebuffer px per layout px)
 * @param factor  {@code k / guiScale}; 1 = the player's GUI as is
 */
public record Viewport(int w, int h, int k, float factor) {
	public static final int MIN_W = 320, MIN_H = 240, STEP_UP_H = 540, BASE_H = 270;

	public static Viewport of(int fbW, int fbH, int guiScale) {
		int gs = Math.max(1, guiScale);
		int gw = ceilDiv(fbW, gs);
		int gh = ceilDiv(fbH, gs);
		int k = gs;
		if (gh < MIN_H || gw < MIN_W) {
			while (k > 1 && (ceilDiv(fbW, k) < MIN_W || ceilDiv(fbH, k) < MIN_H)) k--;
		} else if (gh >= STEP_UP_H) {
			int m = gh / BASE_H;
			while (m > 1 && ceilDiv(fbW, gs * m) < MIN_W) m--;
			k = gs * m;
		}
		return new Viewport(ceilDiv(fbW, k), ceilDiv(fbH, k), k, k / (float) gs);
	}

	/** GUI mouse (player's GUI px) → layout px. */
	public double toLayout(double guiCoord) {
		return guiCoord / factor;
	}

	/** Layout px → framebuffer (window) px: what a client GameTest moves the cursor to. */
	public double toWindow(double layoutCoord) {
		return layoutCoord * k;
	}

	private static int ceilDiv(int a, int b) {
		return (a + b - 1) / b;
	}
}
