package dev.nezo.burmaldaholic.client.fx;

import dev.nezo.burmaldaholic.client.anim.AnimClock;
import dev.nezo.burmaldaholic.core.anim.RollUp;
import dev.nezo.burmaldaholic.core.anim.WinTier;

/**
 * The shared celebration kit (global.md §2.6): banner / overlay, roll-up with tier-upgrade beats, coin burst,
 * rays, skip. One instance per client. SKELETON: owns the state machine and the timing maths; drawing
 * ({@code extract(GuiGraphicsExtractor, …)} on a HUD element above chat and on top of casino screens) and
 * sounds are lane J-L1's task. Not registered anywhere yet — no behaviour change.
 *
 * <p>Games never draw their own big-win sequence: they call {@link #play} with their {@link CelebrationRequest}
 * (slots pass {@code SlotTiers.WORDS} + {@code WinTierTable.SLOTS}).
 */
public final class CelebrationOverlay {
	/** Tier maxima of the roll-up (global.md §2.6 table), ms. */
	private static final int[] MAX_MS = {0, 0, 0, 400, 600, 1200, 1800, 2400, 3000};

	private static final CelebrationOverlay INSTANCE = new CelebrationOverlay();

	private CelebrationRequest active;
	private long startMs;
	private int rollUpMs;
	private boolean skipped;

	public static CelebrationOverlay get() {
		return INSTANCE;
	}

	/** Starts a celebration (replaces a running one: the old one jumps to its end, overrun rule). */
	public void play(CelebrationRequest request) {
		this.active = request;
		this.startMs = AnimClock.localMs();
		int max = MAX_MS[request.tier().ordinal()];
		if (FxSettings.reduceMotion()) max = Math.min(max, 300);
		this.rollUpMs = request.tier().isWin() ? RollUp.durationMs(request.ret(), request.stake(), Math.min(400, max), Math.max(400, max)) : 0;
		this.skipped = false;
	}

	/** Click / Space / Enter / Esc: jump to the final frame (hold 300 ms, then exit). */
	public void skip() {
		skipped = true;
	}

	public boolean isActive() {
		return active != null;
	}

	/** Amount shown at {@code nowMs} (exact server amount at the end or after a skip). */
	public long shownAmount(long nowMs) {
		if (active == null) return 0;
		if (skipped || rollUpMs == 0) return active.ret();
		return RollUp.valueAt(active.ret(), (nowMs - startMs) / (double) rollUpMs);
	}

	/** Tier word currently shown (upgrades as the rolling amount passes the caller's thresholds). */
	public WinTier shownTier(long nowMs) {
		if (active == null) return WinTier.LOSS;
		if (skipped || !active.tier().isOverlay()) return active.tier();
		long shown = shownAmount(nowMs);
		WinTier word = WinTier.WIN;
		for (long[] p : active.table().upgradePoints(active.stake(), WinTier.WIN, active.tier()))
			if (shown >= p[1]) word = WinTier.values()[(int) p[0]];
		return word.ordinal() > WinTier.WIN.ordinal() ? word : (active.table().multiple(WinTier.NICE) > 0 ? WinTier.NICE : WinTier.BIG);
	}

	public void clear() {
		active = null;
	}
}
