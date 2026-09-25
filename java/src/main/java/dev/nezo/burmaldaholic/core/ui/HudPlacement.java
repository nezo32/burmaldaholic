package dev.nezo.burmaldaholic.core.ui;

/**
 * Where the casino HUD block goes so it never covers a vanilla HUD element (UI.md §1, extras.md §9; lane J-L2), at
 * every GUI scale. Pure, GUI px. Vanilla geometry: boss bars 182 px wide centred from y 12, one every 19 px until a
 * third of the screen height (names centred 9 px above each bar); the hotbar 182 px centred at the bottom with the
 * off-hand slot 29 px beside it, hearts / food at −39, armour / air at −49 (more heart rows go higher, so the lift keeps
 * a margin); status effects in the top-right corner, 25 px per icon, beneficial row y 1–25, harmful row y 27–51.
 */
public final class HudPlacement {
	public static final int MARGIN = 4;
	/** Lift above the bottom edge when the block is clear of the hotbar column (the hotbar is 22 px, the XP bar above). */
	public static final int BOTTOM_LIFT = 40;
	/** Lift when the block crosses the hotbar column: above armour / air (−49) and one extra heart row. */
	public static final int BOTTOM_LIFT_HOTBAR = 60;
	static final int HOTBAR_HALF = 91 + 29 + 2;
	static final int BOSS_TOP = 12;
	static final int BOSS_STEP = 19;
	static final int BOSS_HALF = 91;
	static final int EFFECT_ROW = 26;

	private HudPlacement() {}

	/** True when [x, x + w) crosses [from, to). */
	static boolean crosses(int x, int w, int from, int to) {
		return x < to && x + w > from;
	}

	/** Boss bars vanilla actually draws: it stops once the next bar would start at or below a third of the screen. */
	public static int visibleBossBars(int bars, int guiH) {
		int shown = 0;
		int y = BOSS_TOP;
		for (int i = 0; i < bars; i++) {
			shown++;
			y += BOSS_STEP;
			if (y >= guiH / 3) break;
		}
		return shown;
	}

	/**
	 * Top y of a block in a top corner at [x, x + w): below the boss bars when it crosses them (their span is the bar
	 * or the widest name, {@code bossNameW}); in the top-right corner below the effect rows ({@code effectRows} 0–2).
	 */
	public static int top(int x, int w, int guiW, int guiH, int bossBars, int bossNameW, boolean right, int effectRows) {
		int y = MARGIN;
		if (right && effectRows > 0) y = Math.max(y, 1 + EFFECT_ROW * effectRows + 2);
		int shown = visibleBossBars(bossBars, guiH);
		if (shown > 0) {
			int half = Math.max(BOSS_HALF, (bossNameW + 1) / 2) + 2;
			if (crosses(x, w, guiW / 2 - half, guiW / 2 + half)) {
				int lastBar = BOSS_TOP + (shown - 1) * BOSS_STEP;
				y = Math.max(y, lastBar + 5 + 3);
			}
		}
		return y;
	}

	/** Top y of a block of height {@code h} in a bottom corner at [x, x + w): above the hotbar and the status bars. */
	public static int bottom(int x, int w, int h, int guiW, int guiH) {
		boolean hotbar = crosses(x, w, guiW / 2 - HOTBAR_HALF, guiW / 2 + HOTBAR_HALF);
		return guiH - h - MARGIN - (hotbar ? BOTTOM_LIFT_HOTBAR : BOTTOM_LIFT);
	}
}
