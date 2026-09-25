package dev.nezo.burmaldaholic.games.slots.v2.present;

import dev.nezo.burmaldaholic.core.anim.Ease;

/**
 * The four jackpot meters (slots.md §4.1, §4.11, SLOTS.md §5.2; PURE). Display tweens of KNOWN values only: on open
 * they count up from 90 % (400 ms outCubic); server updates (≤ 1/s) tween linearly over 1 000 ms, always upward;
 * when a pool goes DOWN (another player's jackpot was revealed, F6) the plate flashes once, shows "WON!" for
 * 1 500 ms and then counts down to the reset value in 600 ms {@code inOutQuad} (the only allowed down-count). The
 * own spin's award is dropped by the jackpot celebration instead ({@link #holdDrop}).
 */
public final class MeterModel {
	public static final int TIERS = 4;
	public static final int OPEN_MS = 400;
	public static final int UPDATE_MS = 1000;
	public static final int WON_MS = 1500;
	public static final int DROP_MS = 600;

	private final long[] from = new long[TIERS];
	private final long[] to = new long[TIERS];
	private final double[] startMs = new double[TIERS];
	private final int[] durMs = new int[TIERS];
	private final boolean[] down = new boolean[TIERS];
	private final double[] wonAt = new double[TIERS];
	private final boolean[] held = new boolean[TIERS];
	private boolean opened;

	public MeterModel() {
		java.util.Arrays.fill(wonAt, Double.NEGATIVE_INFINITY);
	}

	/**
	 * New server values ({@code values[i]} for Mini … Grand; 0 = no meter, e.g. owned machines). The first call counts
	 * up from 90 %.
	 */
	public void update(long[] values, double nowMs) {
		for (int i = 0; i < TIERS && i < values.length; i++) {
			long v = values[i];
			long shown = shown(i, nowMs);
			if (!opened) {
				from[i] = v * 9 / 10;
				to[i] = v;
				startMs[i] = nowMs;
				durMs[i] = OPEN_MS;
				down[i] = false;
				continue;
			}
			if (v == to[i]) continue;
			if (v < to[i] && !held[i]) {
				// somebody else's jackpot was revealed: flash + WON, then count down
				wonAt[i] = nowMs;
				from[i] = shown;
				to[i] = v;
				startMs[i] = nowMs + WON_MS;
				durMs[i] = DROP_MS;
				down[i] = true;
			} else if (v > to[i]) {
				from[i] = shown;
				to[i] = v;
				startMs[i] = nowMs;
				durMs[i] = UPDATE_MS;
				down[i] = false;
			} else {
				to[i] = v; // held drop (own award): applied by the celebration
				from[i] = v;
				durMs[i] = 0;
			}
		}
		opened = true;
	}

	/** Own award: the drop of tier i is shown by the jackpot celebration, not as someone else's WON. */
	public void holdDrop(int tierIndex, boolean hold) {
		held[tierIndex] = hold;
	}

	public long shown(int i, double nowMs) {
		if (durMs[i] == 0 || nowMs >= startMs[i] + durMs[i]) return to[i];
		if (nowMs <= startMs[i]) return from[i];
		double u = (nowMs - startMs[i]) / durMs[i];
		double e = down[i] ? Ease.IN_OUT_QUAD.apply(u) : durMs[i] == OPEN_MS ? Ease.OUT_CUBIC.apply(u) : u;
		return from[i] + Math.round((to[i] - from[i]) * e);
	}

	/** "WON!" label visible for meter i. */
	public boolean showWon(int i, double nowMs) {
		return nowMs >= wonAt[i] && nowMs < wonAt[i] + WON_MS;
	}

	/** One flash (≤ 30 %) when the WON label appears. */
	public double flash(int i, double nowMs, boolean flashes) {
		return CelebrationPlan.flash(nowMs - wonAt[i], flashes, 0.3);
	}

	public long target(int i) {
		return to[i];
	}
}
