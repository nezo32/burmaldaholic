package dev.nezo.burmaldaholic.games.slots.v2.logic;

/**
 * Everything the engine needs about one machine, built from config ({@code slots.<m>.*}, SLOTS.md §12) and
 * validated on load. All money values are in FIFTHS of the bet (SLOTS.md §7.5: 5 = 1 × bet) so every
 * evaluation is integer-exact in both editions.
 *
 * @param machine          which machine
 * @param codes            symbol codes in SLOTS.md §2 order (e.g. {@code WD SC BN DI …})
 * @param roles            role per symbol index
 * @param strips           5 circular strips of symbol indices (Appendix A)
 * @param paysFifths       per symbol index: pay for 3 / 4 / 5 of a kind per way, fifths of the bet (0 = none)
 * @param scatterFifths    scatter pay for 3 / 4 / 5 scatters, fifths of the bet
 * @param bonusReelsMask   bit r-1 set when the bonus symbol counts on reel r
 * @param freeSpins        spins for 3 / 4 / 5 scatters
 * @param retrigger        spins added on a retrigger
 * @param fsCap            max spins awarded per feature
 * @param fsMultiplier     Overworld all-wins multiplier in free spins (1 elsewhere)
 * @param ladder           Nether base tumble ladder (empty elsewhere)
 * @param ladderFree       Nether free-spin tumble ladder (empty elsewhere)
 * @param capMultiple      max-win multiple (SLOTS.md §1.3)
 * @param buyPriceFifths   buy-feature price in fifths of the bet (0 = no buy)
 */
public record MachineDef(Machine machine, String[] codes, SymbolRole[] roles, int[][] strips, int[][] paysFifths,
		int[] scatterFifths, int bonusReelsMask, int[] freeSpins, int retrigger, int fsCap, int fsMultiplier, int[] ladder,
		int[] ladderFree, int capMultiple, int buyPriceFifths) {
	public static final int REELS = 5;
	public static final int ROWS = 3;

	public int stripLength(int reel) {
		return strips[reel].length;
	}

	/** Symbol shown at (reel, row) for stop {@code t} (SLOTS.md §1.1). */
	public int symbolAt(int reel, int stop, int row) {
		int[] s = strips[reel];
		return s[Math.floorMod(stop + row, s.length)];
	}
}
