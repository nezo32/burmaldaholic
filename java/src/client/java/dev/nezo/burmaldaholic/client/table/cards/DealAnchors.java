package dev.nezo.burmaldaholic.client.table.cards;

import dev.nezo.burmaldaholic.core.anim.sync.LateFlight;
import java.util.HashMap;
import java.util.Map;

/**
 * One first-seen anchor per deal index for one viewer (a card screen or an in-world board; SY-SPEC §3 Cards row). A
 * card first seen after its deal tick (a late or bunched state packet) still flies, compressed to ≥ 120 ms
 * ({@link LateFlight}); cards that had already landed when this viewer first drew the table, or more than
 * {@link #STALE_MS} ago, are settled (no replayed deal on open or on looking back at a table). Keys are the caller's
 * deal indices within a round; a new round key forgets them.
 */
public final class DealAnchors {
	/** A card first seen this long after it should have landed is settled, not flown (a table looked at again). */
	public static final double STALE_MS = 3000;
	private final Map<Long, Double> seen = new HashMap<>();
	private long round = Long.MIN_VALUE;
	/**
	 * True while this viewer still shows the table as it found it: until the first frame that drew a card, or the
	 * first new round, has passed (cards that had already landed then are settled).
	 */
	private boolean fresh = true;
	private boolean queried;

	/** Call once per frame before the cards with the round's key (a deal start tick, a round seq). */
	public void frame(long roundKey) {
		if (queried || round != Long.MIN_VALUE && roundKey != round) fresh = false;
		queried = false;
		if (roundKey != round) {
			round = roundKey;
			seen.clear();
		}
	}

	/** Flight progress 0..1 of card {@code key} dealt at {@code dealMs}, now {@code nowMs} (one clock, ms). */
	public float u(long key, double nowMs, double dealMs, double durMs) {
		queried = true;
		Double s = seen.get(key);
		if (s == null) {
			double landed = nowMs - (dealMs + durMs);
			s = fresh && landed >= 0 || landed > STALE_MS ? Double.NEGATIVE_INFINITY : nowMs;
			seen.put(key, s);
		}
		return (float) LateFlight.progress(nowMs, dealMs, s, durMs);
	}

	/** The first sight of {@code key} (−∞ settled), or NaN when not seen yet (tests). */
	public double seenMs(long key) {
		Double s = seen.get(key);
		return s == null ? Double.NaN : s;
	}
}
