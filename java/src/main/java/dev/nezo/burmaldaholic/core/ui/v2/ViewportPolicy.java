package dev.nezo.burmaldaholic.core.ui.v2;

/**
 * The resolution policy of every v2 screen (docs/architecture/ui-v2.md §3.7, PHASE2B §2): which whole scale {@code k}
 * (framebuffer px per layout px) a window and GUI scale get, and the frame inside it. Pure.
 *
 * <ol>
 *   <li>The player's GUI scale is kept while the GUI is 240–720 px high (and ≥ 400 wide): text is always one layout px
 *       per font px at the player's chosen size, so 1080p at GUI 2 is a ROOMY 960 × 540, not a "zoomed in" 480 × 270.</li>
 *   <li>Below 400 × 240 the scale steps DOWN to the largest whole scale that fits. 400 is the narrowest the Dock lays out
 *       in (T-K2 D5: a 5:4 window at GUI Auto, 1280 × 1024 → 320 × 256, cut the status and overflowed the Dock; it now
 *       gets 427 × 342 at k 3).</li>
 *   <li>Above 720 px high it steps UP by the smallest whole {@code m} that brings the GUI to ≤ 720 (4K at GUI 2 →
 *       960 × 540 at k 4).</li>
 *   <li>The {@link Frame} caps the working area at 600 high and 16:9 wide, centred.</li>
 * </ol>
 */
public final class ViewportPolicy {
	private ViewportPolicy() {}

	public static final int MIN_W = 400, MIN_H = 240, MAX_H = 720;

	public static Viewport of(int fbW, int fbH, int guiScale) {
		int gs = Math.max(1, guiScale);
		int k = gs;
		if (ceilDiv(fbH, gs) < MIN_H || ceilDiv(fbW, gs) < MIN_W) {
			while (k > 1 && (ceilDiv(fbW, k) < MIN_W || ceilDiv(fbH, k) < MIN_H)) k--;
		} else if (ceilDiv(fbH, gs) > MAX_H) {
			// step up, but never below MIN_W wide (a portrait window keeps a taller GUI; the frame caps what it uses)
			int m = 1;
			while (ceilDiv(fbH, gs * m) > MAX_H && ceilDiv(fbW, gs * (m + 1)) >= MIN_W) m++;
			k = gs * m;
		}
		int w = ceilDiv(fbW, k);
		int h = ceilDiv(fbH, k);
		Frame f = Frame.of(w, h);
		return new Viewport(w, h, k, k / (float) gs, f.x(), f.y(), f.w(), f.h());
	}

	static int ceilDiv(int a, int b) {
		return (a + b - 1) / b;
	}
}
