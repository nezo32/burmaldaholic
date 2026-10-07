package dev.nezo.burmaldaholic.games.extras.present;

import dev.nezo.burmaldaholic.games.extras.logic.Payouts;
import java.util.Arrays;

/**
 * The PUBLIC face of a Plinko or Wheel of Fortune machine for the in-world board (docs/architecture/ui-v2.md §7.6, lane
 * E): who played last (the plate) and, for Plinko, the multiplier table of the risk that was dropped (the bins of the
 * upright board). Travels next to the machine's {@code PlinkoSync} / {@code WheelSync} in the block entity's update tag
 * (one int array + the name), sent once per round; nothing in it is secret (the player stands at the machine and the
 * result is shown only when the ball lands / the wheel stops — the renderers gate it on the sync's timeline).
 *
 * <p>PURE: the encoding and the compact multiplier text are unit-tested ({@code ExtrasPresentTest}).
 *
 * @param name  the player at the machine (≤ 16 chars), "" = nobody yet
 * @param risk  Plinko risk ordinal of the last drop (0 low, 1 medium, 2 high); 0 for the wheel
 * @param centi Plinko bin multipliers ×100 of that risk (13), empty for the wheel
 */
public record MachinePub(String name, int risk, int[] centi) {
	public static final int VERSION = 1;
	public static final int MAX_NAME = 16;
	public static final MachinePub EMPTY = new MachinePub("", 0, new int[0]);

	public MachinePub {
		name = name == null ? "" : name.length() > MAX_NAME ? name.substring(0, MAX_NAME) : name;
		centi = centi == null ? new int[0] : centi.clone();
		risk = Math.max(0, Math.min(2, risk));
	}

	public static MachinePub plinko(String name, int risk, double[] table) {
		int[] c = new int[table.length];
		for (int i = 0; i < table.length; i++) c[i] = (int) Math.round(table[i] * 100);
		return new MachinePub(name, risk, c);
	}

	public static MachinePub wheel(String name) {
		return new MachinePub(name, 0, new int[0]);
	}

	/** The bin multipliers back as doubles (empty when none were published). */
	public double[] table() {
		double[] t = new double[centi.length];
		for (int i = 0; i < centi.length; i++) t[i] = centi[i] / 100.0;
		return t;
	}

	public int[] encode() {
		int[] a = new int[2 + centi.length];
		a[0] = VERSION;
		a[1] = risk;
		System.arraycopy(centi, 0, a, 2, centi.length);
		return a;
	}

	public static MachinePub decode(String name, int[] a) {
		if (a == null || a.length < 2 || a[0] != VERSION) return EMPTY;
		return new MachinePub(name, a[1], Arrays.copyOfRange(a, 2, a.length));
	}

	/**
	 * The multiplier as the bins print it (REDESIGN.md §7.7 mockup): whole numbers as is ({@code 33}), fractions
	 * below one without the leading zero ({@code .6}, {@code .25}) so two digits fit an 18 px cap.
	 */
	public static String compact(double mult) {
		String s = Payouts.formatMultiplier(mult);
		return s.startsWith("0.") ? s.substring(1) : s;
	}

	@Override
	public boolean equals(Object o) {
		return o instanceof MachinePub p && p.name.equals(name) && p.risk == risk && Arrays.equals(p.centi, centi);
	}

	@Override
	public int hashCode() {
		return name.hashCode() * 31 + risk * 7 + Arrays.hashCode(centi);
	}

	@Override
	public String toString() {
		return "MachinePub[" + name + ", " + risk + ", " + Arrays.toString(centi) + "]";
	}
}
