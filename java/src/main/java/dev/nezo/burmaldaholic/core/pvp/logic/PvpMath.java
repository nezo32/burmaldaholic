package dev.nezo.burmaldaholic.core.pvp.logic;

/** Pure money math of PvP (PVP.md §3.4, §6.1). Integer only; identical in both editions (tests §16.1 C1–C5). */
public final class PvpMath {
	private PvpMath() {}

	/** {@code rake = floor((pot × bp + 5000) / 10000)} (round half up). */
	public static long rake(long pot, int basisPoints) {
		if (pot <= 0 || basisPoints <= 0) {
			return 0;
		}
		return Math.floorDiv(Math.addExact(Math.multiplyExact(pot, (long) basisPoints), 5000L), 10000L);
	}

	/**
	 * Splits {@code w} among {@code winners} (participant indices): each gets {@code floor(w / k)}; the
	 * {@code w mod k} odd chips go one each to the winners earliest in {@code seatOrder} (a permutation of
	 * participant indices from the tape). Returns payouts per participant (length {@code n}).
	 */
	public static long[] split(long w, int[] winners, int[] seatOrder, int n) {
		long[] out = new long[n];
		int k = winners.length;
		if (k == 0 || w <= 0) {
			return out;
		}
		long each = w / k;
		long odd = w % k;
		boolean[] isWinner = new boolean[n];
		for (int i : winners) {
			isWinner[i] = true;
			out[i] = each;
		}
		for (int i = 0; i < seatOrder.length && odd > 0; i++) {
			if (isWinner[seatOrder[i]]) {
				out[seatOrder[i]]++;
				odd--;
			}
		}
		return out;
	}

	/** Wheel Party: owner of point {@code u ∈ [0, P)} given slices of size {@code stakes[i]} in join order. */
	public static int sliceOwner(long[] stakes, long u) {
		long c = 0;
		for (int i = 0; i < stakes.length; i++) {
			c += stakes[i];
			if (u < c) {
				return i;
			}
		}
		throw new IllegalArgumentException("u outside the wheel");
	}

	/** Pot = Σ stakes. */
	public static long pot(long[] stakes) {
		long p = 0;
		for (long s : stakes) {
			p = Math.addExact(p, s);
		}
		return p;
	}
}
