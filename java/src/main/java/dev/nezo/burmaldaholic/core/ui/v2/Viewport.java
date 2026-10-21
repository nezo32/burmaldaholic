package dev.nezo.burmaldaholic.core.ui.v2;

/**
 * The virtual GUI a table screen works in (docs/architecture/ui-v2.md §3.1, §3.7; computed by {@link ViewportPolicy}).
 * Every art pixel stays a whole number of framebuffer pixels: the effective scale {@link #k} is always an integer.
 *
 * <p>{@link #factor} = {@code k / guiScale} is what the screen draws its pose with and what the mouse mixin divides the
 * GUI mouse position by (one transform for drawing and hit-testing). The screen itself sees only the FRAME
 * ({@link #frameW} × {@link #frameH} at (0, 0), D3 preserved); the frame sits at ({@link #ox}, {@link #oy}) layout px
 * inside the full layout, so the pose is translated by {@link #guiOx()} / {@link #guiOy()} player-GUI px first.
 *
 * @param w       full virtual GUI width (layout px)
 * @param h       full virtual GUI height
 * @param k       effective whole GUI scale (framebuffer px per layout px)
 * @param factor  {@code k / guiScale}; 1 = the player's GUI as is
 * @param ox      frame left inside the layout (layout px)
 * @param oy      frame top inside the layout
 * @param frameW  frame width (what the screen and its grid lay out in)
 * @param frameH  frame height
 */
public record Viewport(int w, int h, int k, float factor, int ox, int oy, int frameW, int frameH) {
	public static final int MIN_W = ViewportPolicy.MIN_W, MIN_H = ViewportPolicy.MIN_H;

	/** An unframed viewport (tests, defaults). */
	public Viewport(int w, int h, int k, float factor) {
		this(w, h, k, factor, 0, 0, w, h);
	}

	public static Viewport of(int fbW, int fbH, int guiScale) {
		return ViewportPolicy.of(fbW, fbH, guiScale);
	}

	/** Whether the screen works in a frame smaller than the window. */
	public boolean framed() {
		return frameW < w || frameH < h;
	}

	/** The room of the frame. */
	public Room room() {
		return Room.of(frameW, frameH);
	}

	/** Frame offset in the player's GUI px (the pose translation and the mouse offset). */
	public float guiOx() {
		return ox * factor;
	}

	public float guiOy() {
		return oy * factor;
	}

	/** GUI mouse (player's GUI px) → layout px (scale only; frame-relative positions subtract the offset first). */
	public double toLayout(double guiCoord) {
		return guiCoord / factor;
	}

	/** Layout px → framebuffer px, scale only (no frame offset: use {@link #windowX} / {@link #windowY} for positions). */
	public double toWindow(double layoutCoord) {
		return layoutCoord * k;
	}

	/** A frame-relative layout x → the window (framebuffer) px a test moves the cursor to. */
	public double windowX(double frameX) {
		return (frameX + ox) * k;
	}

	public double windowY(double frameY) {
		return (frameY + oy) * k;
	}
}
