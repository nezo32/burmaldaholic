package dev.nezo.burmaldaholic.core.anim.dice;

/**
 * Contacts-first dice solver (PHASE2B §3.1, §3.3). See {@link #solve}.
 */
public final class DiceSolver {
	/** {@code targetMs} value: no target, the roll rests at its natural time. */
	public static final int NATURAL = -1;

	private DiceSolver() {}

	/**
	 * Solves one throw of {@code faces.length} dice on {@code tray}.
	 *
	 * @param tray     where the dice land
	 * @param spec     how they leave the cup / hand / chest
	 * @param seed     cosmetic seed (public data, never CasinoRng)
	 * @param faces    the server's faces (1–6) per die; only the relabelling reads them
	 * @param targetMs rest time of the last die on the roll clock (0 = die 0 released), or {@link #NATURAL}
	 */
	public static DiceRoll solve(DiceTray tray, ThrowSpec spec, int seed, int[] faces, int targetMs) {
		throw new UnsupportedOperationException("DCa-B");
	}
}
