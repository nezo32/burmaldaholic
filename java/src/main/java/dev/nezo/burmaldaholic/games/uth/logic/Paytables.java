package dev.nezo.burmaldaholic.games.uth.logic;

import java.util.EnumMap;
import java.util.Map;

/**
 * Blind and Trips paytables (GAME_DESIGN §21.1, config {@code uth.blindPays} / {@code uth.tripsPays}).
 * Pays are X:1; a hand missing from the Blind table pushes, a hand missing from the Trips table loses.
 */
public final class Paytables {
	/** Standard tables: Blind 500/50/10/3/1.5/1, Trips 50-40-30-8-6-5-3. */
	public static final Paytables DEFAULT = new Paytables(
		Map.of("royal", 500.0, "straightFlush", 50.0, "quads", 10.0, "fullHouse", 3.0, "flush", 1.5, "straight", 1.0),
		Map.of("royal", 50, "straightFlush", 40, "quads", 30, "fullHouse", 8, "flush", 6, "straight", 5, "trips", 3));

	private final EnumMap<PayHand, Double> blind = new EnumMap<>(PayHand.class);
	private final EnumMap<PayHand, Integer> trips = new EnumMap<>(PayHand.class);

	/** Keys as in CONFIG.md ({@code royal, straightFlush, quads, fullHouse, flush, straight, trips}); unknown keys ignored. */
	public Paytables(Map<String, ? extends Number> blindPays, Map<String, ? extends Number> tripsPays) {
		for (PayHand h : PayHand.values()) {
			if (h == PayHand.NONE) {
				continue;
			}
			Number b = blindPays == null ? null : blindPays.get(h.key());
			if (b != null && h != PayHand.TRIPS && b.doubleValue() > 0 && Double.isFinite(b.doubleValue())) {
				blind.put(h, b.doubleValue());
			}
			Number t = tripsPays == null ? null : tripsPays.get(h.key());
			if (t != null && t.intValue() > 0) {
				trips.put(h, t.intValue());
			}
		}
	}

	/** Blind multiplier (X:1) for a winning hand; 0 = push. */
	public double blindPay(PayHand hand) {
		return blind.getOrDefault(hand, 0.0);
	}

	/** Trips multiplier (X:1); 0 = the Trips bet loses. */
	public int tripsPay(PayHand hand) {
		return trips.getOrDefault(hand, 0);
	}

	/** Blind winnings for {@code ante} (floored, §21.1: odd Antes lose half a chip on the 3:2 flush). */
	public long blindWin(PayHand hand, long ante) {
		return (long) Math.floor(ante * blindPay(hand) + 1e-9);
	}

	/**
	 * Most the table can pay a seat (§21.6): Play ×4 + Ante 1:1 + the best Blind pay on Ante + best Trips
	 * pay on Trips = 505 × Ante + 50 × Trips at defaults.
	 */
	public long worstCase(long ante, long tripsBet) {
		double maxBlind = 0;
		for (double v : blind.values()) {
			maxBlind = Math.max(maxBlind, v);
		}
		int maxTrips = 0;
		for (int v : trips.values()) {
			maxTrips = Math.max(maxTrips, v);
		}
		return 5 * ante + (long) Math.floor(ante * maxBlind + 1e-9) + tripsBet * maxTrips;
	}

	/** Worst-case multiplier per Ante ({@code 505} at defaults) and per Trips chip ({@code 50}). */
	public long anteFactor() {
		return worstCase(1, 0);
	}

	public long tripsFactor() {
		return worstCase(0, 1);
	}
}
