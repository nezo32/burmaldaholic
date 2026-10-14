package dev.nezo.burmaldaholic.core.ui.v2;

/**
 * The frame cap of a table screen (docs/architecture/ui-v2.md §3.7): a layout taller than {@link #CAP_H} or wider than
 * 16:9 is not stretched over the whole window; the screen works in a centred frame of at most {@code 600} px high and
 * 16:9 wide (1280 × 720 → 1067 × 600; an ultrawide 2560 × 1080 at GUI 4, 640 × 270 → 480 × 270). Outside the frame the
 * menu background (the blurred, dimmed world) and the frame edge show. Offsets are whole layout px. Pure.
 *
 * @param x frame left inside the layout (≥ 0)
 * @param y frame top inside the layout (≥ 0)
 * @param w frame width
 * @param h frame height
 */
public record Frame(int x, int y, int w, int h) {
	public static final int CAP_H = 600;

	/** The frame of a {@code w × h} layout: the whole layout when it is at most 600 high and at most 16:9 wide. */
	public static Frame of(int w, int h) {
		int fh = Math.min(h, CAP_H);
		int fw = Math.min(w, (fh * 16 + 8) / 9); // ceil(fh · 16 / 9)
		return new Frame((w - fw) / 2, (h - fh) / 2, fw, fh);
	}

	public boolean framed(int layoutW, int layoutH) {
		return w < layoutW || h < layoutH;
	}
}
