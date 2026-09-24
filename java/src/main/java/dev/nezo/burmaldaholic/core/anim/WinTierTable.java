package dev.nezo.burmaldaholic.core.anim;

/**
 * Per-game win-tier thresholds (lead decision 2026-09-24; docs/architecture/animation.md §4). One API,
 * several tables: {@link #DEFAULT} for table / extras / PvP games and for broadcasts and the chaos big-win
 * rule, {@link #SLOTS} for the slot screens. A game may build its own with {@link #of}.
 *
 * <p>A tier T is reached when {@code ret ≥ multiple[T] × stake} and {@code net ≥ minNet[T]}; EPIC is also
 * reached when {@code net ≥ epicNet} (the {@code core.bigWinThreshold} rule, 0 = off). Index order of the
 * arrays: NICE, BIG, MEGA, EPIC. A multiple of 0 means "no threshold" (the tier is only reachable through a
 * floor).
 *
 * @param multiples  return multiples (×stake) for NICE, BIG, MEGA, EPIC
 * @param minNet     net-chip floors for NICE, BIG, MEGA, EPIC
 * @param epicNet    absolute net that always means EPIC (0 = off)
 * @param evenIsPush return = stake is PUSH (true for tables; false for slots, where 1× is a WIN)
 */
public record WinTierTable(int[] multiples, long[] minNet, long epicNet, boolean evenIsPush) {
	/** WIN / BIG / MEGA / EPIC at 10 / 25 / 50 × with the global net floors (global.md §2.4). */
	public static final WinTierTable DEFAULT = of(new int[] {0, 10, 25, 50}, new long[] {0, 100, 250, 500}, 5000, true);
	/** NICE / BIG / MEGA / EPIC at 5 / 15 / 40 / 100 × (SLOTS.md §10.1, {@code slots.bigWinTiers}). */
	public static final WinTierTable SLOTS = of(new int[] {5, 15, 40, 100}, new long[] {0, 0, 0, 0}, 0, false);

	public WinTierTable {
		if (multiples.length != 4 || minNet.length != 4) throw new IllegalArgumentException("4 tiers expected");
		multiples = multiples.clone();
		minNet = minNet.clone();
	}

	public static WinTierTable of(int[] multiples, long[] minNet, long epicNet, boolean evenIsPush) {
		return new WinTierTable(multiples, minNet, epicNet, evenIsPush);
	}

	/** Same table with config-driven values ({@code core.winTiers} + {@code core.bigWinThreshold}). */
	public WinTierTable withMultiples(int nice, int big, int mega, int epic) {
		return new WinTierTable(new int[] {nice, big, mega, epic}, minNet, epicNet, evenIsPush);
	}

	public WinTierTable withEpicNet(long net) {
		return new WinTierTable(multiples, minNet, net, evenIsPush);
	}

	private static int index(WinTier t) {
		return switch (t) {
			case NICE -> 0;
			case BIG -> 1;
			case MEGA -> 2;
			case EPIC -> 3;
			default -> -1;
		};
	}

	/** Return multiple of a tier (0 when the table has none). */
	public int multiple(WinTier t) {
		int i = index(t);
		return i < 0 ? 0 : multiples[i];
	}

	public boolean reaches(WinTier t, long ret, long stake) {
		int i = index(t);
		if (i < 0) return false;
		long net = ret - stake;
		if (t == WinTier.EPIC && epicNet > 0 && net >= epicNet) return true;
		int m = multiples[i];
		return m > 0 && ret >= (long) m * stake && net >= minNet[i];
	}

	/**
	 * Roll-up upgrade points (global.md §2.6 "tier upgrade beat"): the running amount at which the overlay
	 * word upgrades, for every threshold tier above {@code start} up to {@code finalTier}. Returns pairs
	 * {@code [tierOrdinal, amount]}; an amount is {@code max(multiple × stake, minNet + stake)}.
	 */
	public long[][] upgradePoints(long stake, WinTier start, WinTier finalTier) {
		java.util.List<long[]> out = new java.util.ArrayList<>();
		for (WinTier t : new WinTier[] {WinTier.NICE, WinTier.BIG, WinTier.MEGA, WinTier.EPIC}) {
			if (t.ordinal() <= start.ordinal() || t.ordinal() > finalTier.ordinal()) continue;
			int i = index(t);
			if (multiples[i] <= 0) continue;
			out.add(new long[] {t.ordinal(), Math.max((long) multiples[i] * stake, minNet[i] + stake)});
		}
		return out.toArray(new long[0][]);
	}
}
