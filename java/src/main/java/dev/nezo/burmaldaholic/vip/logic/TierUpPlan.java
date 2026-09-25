package dev.nezo.burmaldaholic.vip.logic;

import dev.nezo.burmaldaholic.core.anim.Ease;

/**
 * The VIP tier-up celebration (global.md §4.11), PURE: backdrop, tier-coloured rays, the 48 × 48 badge pop
 * ({@code outBack} 0 → 1.15 → 1), the shine sweep, the two text lines and the exit. A multi-tier jump flicks the
 * earlier badges past (100 ms each) before the final one, so the last badge drawn is always the server's tier.
 * Reduced motion: a static card (no rays, no pop, no shine, no embers) for the same hold.
 */
public final class TierUpPlan {
	public static final int FLICK_MS = 100;
	public static final int BADGE_AT_MS = 100;
	public static final int BADGE_POP_MS = 350;
	public static final int SHINE_AT_MS = 450;
	public static final int SHINE_FRAMES = 8;
	public static final int SHINE_FRAME_MS = 40;
	public static final int TEXT_AT_MS = 500;
	public static final int EXIT_AT_MS = 2200;
	public static final int EXIT_MS = 400;
	/** Arpeggio (bell pitches 1.0 / 1.26 / 1.5 / 2.0, 90 ms apart) from {@link #BADGE_AT_MS}. */
	public static final float[] ARPEGGIO = {1.0f, 1.26f, 1.5f, 2.0f};
	public static final int ARPEGGIO_STEP_MS = 90;
	public static final int EMBERS = 20;
	public static final float RAYS_DEG_PER_S = 12;

	private final int fromTier;
	private final int tier;
	private final boolean reduced;

	/**
	 * @param fromTier the tier before the jump (flicks {@code fromTier + 1 … tier − 1} first)
	 * @param tier     the server's new tier
	 * @param reduced  reduced motion
	 */
	public TierUpPlan(int fromTier, int tier, boolean reduced) {
		this.fromTier = Math.max(0, Math.min(fromTier, tier - 1));
		this.tier = tier;
		this.reduced = reduced;
	}

	public int tier() {
		return tier;
	}

	/** Earlier tiers flicked before the final badge. */
	public int flicks() {
		return reduced ? 0 : Math.max(0, tier - fromTier - 1);
	}

	/** Start of the final tier's timeline (after the flicks). */
	public int offsetMs() {
		return flicks() * FLICK_MS;
	}

	public int totalMs() {
		return offsetMs() + EXIT_AT_MS + EXIT_MS;
	}

	public boolean done(double ms) {
		return ms >= totalMs();
	}

	/** Tier whose badge is drawn at {@code ms}: the flicked tiers first, then (from the offset on) always the final tier. */
	public int badgeTier(double ms) {
		if (ms < offsetMs()) return fromTier + 1 + (int) Math.max(0, Math.floor(ms / FLICK_MS));
		return tier;
	}

	private double local(double ms) {
		return ms - offsetMs();
	}

	/** Overall alpha: 1, fading over the exit. */
	public double alpha(double ms) {
		double t = local(ms);
		if (t < EXIT_AT_MS) return 1;
		return Math.max(0, 1 - (t - EXIT_AT_MS) / EXIT_MS);
	}

	/** Backdrop dim (30 % of {@code bg.darkest}) — in over 200 ms from the very start. */
	public double backdrop(double ms) {
		return 0.30 * Math.min(1, Math.max(0, ms) / 200.0) * alpha(ms);
	}

	/** Rays alpha (0 under reduced motion): fade in over 300 ms. */
	public double rays(double ms) {
		if (reduced) return 0;
		return Math.min(1, Math.max(0, ms) / 300.0) * alpha(ms);
	}

	/** Rays rotation in degrees (12°/s). */
	public double raysDegrees(double ms) {
		return reduced ? 0 : RAYS_DEG_PER_S * Math.max(0, ms) / 1000.0;
	}

	/** Badge scale: flicks at 1; the final badge 0 → 1.15 → 1 ({@code outBack} 350 ms from 100 ms); reduced motion 1. */
	public double badgeScale(double ms) {
		if (reduced) return 1;
		if (ms < offsetMs()) return 0.8;
		double t = local(ms) - BADGE_AT_MS;
		if (t < 0) return 0;
		return Ease.outBack(2.2, t / BADGE_POP_MS);
	}

	/** Shine frame 0–7 at {@code ms}, or −1 (no shine; never under reduced motion). */
	public int shineFrame(double ms) {
		if (reduced) return -1;
		double t = local(ms) - SHINE_AT_MS;
		if (t < 0) return -1;
		int f = (int) (t / SHINE_FRAME_MS);
		return f < SHINE_FRAMES ? f : -1;
	}

	/** Text lines alpha: fade in over 200 ms from {@link #TEXT_AT_MS} (reduced motion: from the badge time). */
	public double textAlpha(double ms) {
		double t = local(ms) - (reduced ? BADGE_AT_MS : TEXT_AT_MS);
		if (t < 0) return 0;
		return Math.min(1, t / 200.0) * alpha(ms);
	}

	/** Index of the arpeggio notes that are due at {@code ms} (count of notes started). */
	public int arpeggioNotes(double ms) {
		double t = local(ms) - BADGE_AT_MS;
		if (t < 0) return 0;
		return Math.min(ARPEGGIO.length, 1 + (int) (t / ARPEGGIO_STEP_MS));
	}

	/** Rising embers of the Netherite tier (never under reduced motion). */
	public boolean embers(int netheriteTier) {
		return !reduced && tier >= netheriteTier;
	}
}
