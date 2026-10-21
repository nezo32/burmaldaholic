package dev.nezo.burmaldaholic.core.anim.sync;

/**
 * SY-SPEC §3 Cards row: a dealt card waits in the shoe until this viewer first sees it, then flies. On time (seen at or
 * before its deal) it flies on the shared clock; seen late it starts flying at the first sight, compressed to the
 * remaining time but never shorter than {@code minMs}, so a late card still visibly flies instead of popping into its
 * slot. Pure; shared by the card screens and the in-world boards.
 */
public final class LateFlight {
	/** The shortest flight of a late card (ms). */
	public static final double MIN_MS = 120;

	private LateFlight() {}

	/**
	 * Flight progress 0..1 at {@code nowMs} of a card dealt at {@code dealMs}, first seen at {@code seenMs}
	 * ({@code -∞} = it had already landed when this viewer arrived: settled), on one clock.
	 */
	public static double progress(double nowMs, double dealMs, double seenMs, double durMs, double minMs) {
		if (!(seenMs > dealMs)) return clamp((nowMs - dealMs) / durMs);
		double dur = Math.max(minMs, durMs - (seenMs - dealMs));
		return clamp((nowMs - seenMs) / dur);
	}

	/** {@link #progress(double, double, double, double, double)} with {@link #MIN_MS}. */
	public static double progress(double nowMs, double dealMs, double seenMs, double durMs) {
		return progress(nowMs, dealMs, seenMs, durMs, MIN_MS);
	}

	private static double clamp(double u) {
		return u <= 0 ? 0 : u >= 1 ? 1 : u;
	}
}
