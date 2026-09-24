package dev.nezo.burmaldaholic.games.roulette.logic;

/**
 * What a roulette table publishes to spectators and the in-world wheel (block entity update tag; tables.md §1.7):
 * phase, the spin's start / length / result / seed and the last result, as an int array. Sent once per phase change,
 * never per tick. The result is only included from SPIN on (bets are closed then, §0.6.7). Pure.
 *
 * @param phase      0 betting, 1 no more bets, 2 spin, 3 result
 * @param phaseStart game time the phase began
 * @param spinStart  game time the last spin began
 * @param spinTicks  spin length in ticks
 * @param result     result of the current / last spin, -1 = none
 * @param seed       cosmetic seed of the ball path
 * @param lastResult last settled number (-1 = none): the idle wheel rests on it
 * @param theme      0 village, 1 bastion, 2 end
 * @param seq        spin counter (a change = a new spin)
 */
public record RouletteSync(int phase, long phaseStart, long spinStart, int spinTicks, int result, int seed, int lastResult, int theme, int seq) {
	public static final int VERSION = 1;
	public static final int BETTING = 0;
	public static final int NO_MORE_BETS = 1;
	public static final int SPIN = 2;
	public static final int RESULT = 3;

	public int[] encode() {
		return new int[] {VERSION, phase, (int) (phaseStart >>> 32), (int) phaseStart, (int) (spinStart >>> 32), (int) spinStart, spinTicks, result, seed,
			lastResult, theme, seq};
	}

	public static RouletteSync decode(int[] a) {
		if (a.length != 12 || a[0] != VERSION) {
			throw new IllegalArgumentException("roulette sync v" + (a.length > 0 ? a[0] : -1));
		}
		return new RouletteSync(a[1], ((long) a[2] << 32) | (a[3] & 0xFFFFFFFFL), ((long) a[4] << 32) | (a[5] & 0xFFFFFFFFL), a[6], a[7], a[8], a[9], a[10],
			a[11]);
	}

	public static int phaseOf(String id) {
		return switch (id) {
			case "no_more_bets" -> NO_MORE_BETS;
			case "spin" -> SPIN;
			case "result" -> RESULT;
			default -> BETTING;
		};
	}
}
