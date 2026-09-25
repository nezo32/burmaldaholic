package dev.nezo.burmaldaholic.core.ui;

import dev.nezo.burmaldaholic.core.anim.Ease;

/**
 * Pure layout and motion maths of the casino UI kit (lane J-L2; global.md §2.2, §4.14; docs/design/visual/extras.md
 * §2.2–§2.4). No Minecraft types: the client kit ({@code client.ui.CasinoScreen}, {@code CasinoButton}) calls these,
 * JUnit pins them. All results are whole GUI pixels (pixel art is never drawn at fractional offsets).
 */
public final class UiLayout {
	/** The full layout (the mockups): 400 × 240. */
	public static final int FULL_W = 400;
	public static final int FULL_H = 240;
	/** Compact layout width (extras.md §2.4 "M"). */
	public static final int COMPACT_W = 320;
	/** Smallest panel of the forced-scale layout ("S"). */
	public static final int MIN_W = 300;
	public static final int MIN_H = 200;

	/** Entrance: fade + scale 0.97 → 1 (global.md §4.14). */
	public static final int ENTRANCE_MS = 150;
	/** Hover lift ease time (global.md §2.2). */
	public static final int HOVER_MS = 80;
	/** Press depth time. */
	public static final int PRESS_MS = 60;
	/** Invalid shake: 3 cycles ±2 px. */
	public static final int SHAKE_MS = 240;
	/** Error line slide-in (4 px from below). */
	public static final int ERROR_SLIDE_MS = 150;

	private UiLayout() {}

	/** Layout class of a GUI size (extras.md §2.4). */
	public enum Size {
		/** {@code guiW ≥ 416} and {@code guiH ≥ 240}: 400 × 240 as in the mockups. */
		L,
		/** {@code 320 ≤ guiW < 416}: 320 × 240, icon buttons, merged side column. */
		M,
		/** Forced GUI scale below 320 × 240: ≥ 300 × 200, drawers. */
		S
	}

	/** An integer rectangle. */
	public record Rect(int x, int y, int w, int h) {
		public int right() {
			return x + w;
		}

		public int bottom() {
			return y + h;
		}

		public int centerX() {
			return x + w / 2;
		}

		public int centerY() {
			return y + h / 2;
		}

		public boolean contains(double px, double py) {
			return px >= x && px < x + w && py >= y && py < y + h;
		}

		/** The same rectangle shrunk by {@code d} on every side. */
		public Rect inset(int d) {
			return new Rect(x + d, y + d, Math.max(0, w - 2 * d), Math.max(0, h - 2 * d));
		}
	}

	public static Size size(int guiW, int guiH) {
		if (guiW >= 416 && guiH >= 240) return Size.L;
		if (guiW >= 320 && guiH >= 240) return Size.M;
		return Size.S;
	}

	/**
	 * The centred panel of a screen: {@code fullW × fullH} in L, {@code min(fullW, 320)} wide in M, the largest box that
	 * fits (never below {@link #MIN_W} × {@link #MIN_H}) in S. Always at integer coordinates, never negative.
	 */
	public static Rect panel(int guiW, int guiH, int fullW, int fullH) {
		int w;
		int h;
		switch (size(guiW, guiH)) {
			case L -> {
				w = Math.min(fullW, guiW);
				h = Math.min(fullH, guiH);
			}
			case M -> {
				w = Math.min(Math.min(fullW, COMPACT_W), guiW);
				h = Math.min(fullH, guiH);
			}
			default -> {
				w = Math.max(Math.min(MIN_W, fullW), Math.min(fullW, guiW - 4));
				h = Math.max(Math.min(MIN_H, fullH), Math.min(fullH, guiH - 4));
			}
		}
		return new Rect(Math.max(0, (guiW - w) / 2), Math.max(0, (guiH - h) / 2), w, h);
	}

	// ---- motion -----------------------------------------------------------------------------------------------------

	private static double progress(long ms, int dur) {
		return ms <= 0 ? 0 : ms >= dur ? 1 : ms / (double) dur;
	}

	/** Panel scale during the entrance (0.97 → 1, {@code outCubic}); 1 with reduce motion. */
	public static float entranceScale(long msSinceOpen, boolean reduceMotion) {
		if (reduceMotion) return 1f;
		return (float) (0.97 + 0.03 * Ease.OUT_CUBIC.apply(progress(msSinceOpen, ENTRANCE_MS)));
	}

	/** Panel opacity during the entrance (0 → 1, {@code outCubic}); 1 with reduce motion. */
	public static float entranceAlpha(long msSinceOpen, boolean reduceMotion) {
		if (reduceMotion) return 1f;
		return (float) Ease.OUT_CUBIC.apply(progress(msSinceOpen, ENTRANCE_MS));
	}

	/** Hover lift in whole pixels (0 → 1 over 80 ms {@code outQuad}); the caller passes −1 when not hovered. */
	public static int hoverLift(long msSinceHover, boolean reduceMotion) {
		if (msSinceHover < 0) return 0;
		if (reduceMotion) return 1;
		return Ease.OUT_QUAD.apply(progress(msSinceHover, HOVER_MS)) >= 0.5 ? 1 : 0;
	}

	/** Press depth (y + 1 for 60 ms); −1 = never pressed. */
	public static int pressDepth(long msSincePress) {
		return msSincePress >= 0 && msSincePress < PRESS_MS ? 1 : 0;
	}

	/** Invalid shake: ±2 px, 3 cycles over 240 ms, decaying; 0 with reduce motion or when not shaking (−1). */
	public static int shake(long msSinceShake, boolean reduceMotion) {
		if (reduceMotion || msSinceShake < 0 || msSinceShake >= SHAKE_MS) return 0;
		double t = msSinceShake / (double) SHAKE_MS;
		return (int) Math.round(2 * Math.sin(t * Math.PI * 6) * (1 - t));
	}

	/** Error line vertical offset: slides in 4 px from below over 150 ms {@code outCubic}; 0 with reduce motion. */
	public static int errorSlide(long msSinceError, boolean reduceMotion) {
		if (reduceMotion || msSinceError < 0) return 0;
		return (int) Math.round(4 * (1 - Ease.OUT_CUBIC.apply(progress(msSinceError, ERROR_SLIDE_MS))));
	}

	/**
	 * Tab content cross-fade (global.md §4.2): the new page fades in over 120 ms after an 80 ms fade-out of the old one;
	 * returns the new page's alpha. 1 with reduce motion.
	 */
	public static float tabFadeIn(long msSinceSwitch, boolean reduceMotion) {
		if (reduceMotion) return 1f;
		return (float) Ease.OUT_CUBIC.apply(progress(msSinceSwitch - 40, 120));
	}

	/** Tab content slide (6 px in the tab's direction {@code dir} = ±1, 120 ms {@code outCubic}); 0 with reduce motion. */
	public static int tabSlide(long msSinceSwitch, int dir, boolean reduceMotion) {
		if (reduceMotion || dir == 0) return 0;
		return (int) Math.round(6 * Integer.signum(dir) * (1 - Ease.OUT_CUBIC.apply(progress(msSinceSwitch, 120))));
	}

	/** A progress bar fill that grows from 0 on open (600 ms {@code outCubic}, global.md §4.2); final value with reduce motion. */
	public static double barFill(double fraction, long msSinceOpen, boolean reduceMotion) {
		double f = Math.max(0, Math.min(1, fraction));
		if (reduceMotion) return f;
		return f * Ease.OUT_CUBIC.apply(progress(msSinceOpen, 600));
	}

	/** Filled pixels of a bar of inner width {@code innerW}: never 0 for a positive fraction, exact at 1. */
	public static int fillPixels(int innerW, double fraction) {
		double f = Math.max(0, Math.min(1, fraction));
		if (f <= 0 || innerW <= 0) return 0;
		return Math.max(1, (int) Math.floor(innerW * f + 1e-9));
	}

	/** Button width rule (global.md §2.2): {@code max(minWidth, textWidth + 8)}, plus room for an icon. */
	public static int buttonWidth(int minWidth, int textWidth, int iconWidth) {
		return Math.max(minWidth, textWidth + 8 + (iconWidth > 0 ? iconWidth + 3 : 0));
	}

	/** Balance plaque of the screen header: width and its gap to the panel's right edge (CasinoScreen). */
	public static final int PLAQUE_W = 90;
	public static final int PLAQUE_RIGHT = 16;

	/**
	 * Widest title banner centred on a panel of width {@code panelW} that stays clear of the balance plaque (4 px gap) when
	 * {@code balance} is shown, and inside the 12 px frame border otherwise; at most 220.
	 */
	public static int bannerMaxW(int panelW, boolean balance) {
		int half = balance ? panelW / 2 - PLAQUE_RIGHT - PLAQUE_W - 4 : panelW / 2 - 12;
		return Math.max(0, Math.min(220, 2 * half));
	}

	/** The plaque width for a centred title (extras.md §2.2: {@code max(110, textWidth + 32)}), capped at {@code maxW}. */
	public static int bannerWidth(int textWidth, int maxW) {
		return Math.min(maxW, Math.max(110, textWidth + 32));
	}
}
