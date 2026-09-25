package dev.nezo.burmaldaholic.games.slots.v2.logic;

import java.util.Arrays;

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
 * @param ladder           Nether base tumble ladder (empty elsewhere = no tumbles)
 * @param ladderFree       Nether free-spin tumble ladder (empty elsewhere)
 * @param capMultiple      max-win multiple (SLOTS.md §1.3)
 * @param buyPriceFifths   buy-feature price in fifths of the bet (0 = no buy)
 * @param features         bonus games and jackpots (SLOTS.md §3, §5)
 */
public record MachineDef(Machine machine, String[] codes, SymbolRole[] roles, int[][] strips, int[][] paysFifths,
		int[] scatterFifths, int bonusReelsMask, int[] freeSpins, int retrigger, int fsCap, int fsMultiplier, int[] ladder,
		int[] ladderFree, int capMultiple, int buyPriceFifths, Features features) {
	public static final int REELS = 5;
	public static final int ROWS = 3;

	/**
	 * Bonus games and progressive jackpots. Value codes (shared by the tape, SLOTS.md §1.2): a positive value is
	 * a multiple of the bet, {@code -1 … -4} a Mini … Grand jackpot, {@code 0} the Creeper (hunt) or UP (wheel).
	 *
	 * @param pickBoard       Treasure Hunt chests (Overworld; 0 elsewhere)
	 * @param pickValues      hunt contents (value codes)
	 * @param pickWeights     hunt weights (integers)
	 * @param holdTrigger     coins that start Piglin's Hoard (Nether; 0 elsewhere)
	 * @param holdRespins     respins (reset on every new coin)
	 * @param holdCoinPpm     chance per empty cell per respin, parts per million (0.04 = 40 000)
	 * @param holdValues      coin values (value codes, no Grand)
	 * @param holdWeights     coin weights (×10 of SLOTS.md §3.2)
	 * @param wheelRings      Dragon Wheel wedges per ring in clockwise order (End; empty elsewhere)
	 * @param jackpotRef      reference bet: full jackpot at this bet or above
	 * @param jackpotSeedMult pool seeds (Mini … Grand) as multiples of {@code jackpotRef}
	 * @param contributionPpm contribution per stake (Mini … Grand), parts per million
	 * @param ownedMult       fixed jackpot multiples of the bet at owned machines (Mini … Grand)
	 */
	public record Features(int pickBoard, int[] pickValues, int[] pickWeights, int holdTrigger, int holdRespins, int holdCoinPpm,
			int[] holdValues, int[] holdWeights, int[][] wheelRings, long jackpotRef, int[] jackpotSeedMult, int[] contributionPpm,
			int[] ownedMult) {
		/** Pool seed of a tier (1 Mini … 4 Grand) in chips. */
		public long seedChips(int tier) {
			return jackpotSeedMult[tier - 1] * jackpotRef;
		}

		@Override
		public boolean equals(Object o) {
			return o instanceof Features f && f.pickBoard == pickBoard && Arrays.equals(f.pickValues, pickValues)
				&& Arrays.equals(f.pickWeights, pickWeights) && f.holdTrigger == holdTrigger && f.holdRespins == holdRespins
				&& f.holdCoinPpm == holdCoinPpm && Arrays.equals(f.holdValues, holdValues) && Arrays.equals(f.holdWeights, holdWeights)
				&& Arrays.deepEquals(f.wheelRings, wheelRings) && f.jackpotRef == jackpotRef && Arrays.equals(f.jackpotSeedMult, jackpotSeedMult)
				&& Arrays.equals(f.contributionPpm, contributionPpm) && Arrays.equals(f.ownedMult, ownedMult);
		}

		@Override
		public int hashCode() {
			return Arrays.hashCode(new int[] {pickBoard, Arrays.hashCode(pickWeights), holdTrigger, holdCoinPpm, Arrays.hashCode(holdWeights),
				Arrays.deepHashCode(wheelRings), Long.hashCode(jackpotRef), Arrays.hashCode(jackpotSeedMult), Arrays.hashCode(contributionPpm),
				Arrays.hashCode(ownedMult)});
		}
	}

	public int stripLength(int reel) {
		return strips[reel].length;
	}

	/** Symbol shown at (reel, row) for stop {@code t} (SLOTS.md §1.1). */
	public int symbolAt(int reel, int stop, int row) {
		int[] s = strips[reel];
		return s[Math.floorMod(stop + row, s.length)];
	}

	private int indexOf(SymbolRole role) {
		for (int i = 0; i < roles.length; i++) if (roles[i] == role) return i;
		return -1;
	}

	/** Index of the Wild symbol (-1 if none). */
	public int wild() {
		return indexOf(SymbolRole.WILD);
	}

	public int scatter() {
		return indexOf(SymbolRole.SCATTER);
	}

	/** Chest / crystal (-1 on Nether). */
	public int bonus() {
		return indexOf(SymbolRole.BONUS);
	}

	/** Piglin coin (-1 except on Nether). */
	public int coin() {
		return indexOf(SymbolRole.COIN);
	}

	/** The machine's top paying symbol (Diamond, Wither Skeleton Skull, Dragon Head): the first PAY symbol. */
	public int topSymbol() {
		return indexOf(SymbolRole.PAY);
	}

	/** Tumbling reels (Nether). */
	public boolean tumbles() {
		return ladder.length > 0;
	}

	/** End Void: Dragon Eggs expand and stick in free spins (SLOTS.md §3.3). */
	public boolean stickyWilds() {
		return machine == Machine.END;
	}

	/** Base stake that the owned-casino reservation and the cap refer to. */
	public long capChips(long bet) {
		return (long) capMultiple * bet;
	}

	/** Buy price in chips for {@code bet} (bets are multiples of 5, so exact). */
	public long buyPrice(long bet) {
		return buyPriceFifths * bet / 5;
	}

	@Override
	public boolean equals(Object o) {
		return o instanceof MachineDef d && d.machine == machine && Arrays.equals(d.codes, codes) && Arrays.equals(d.roles, roles)
			&& Arrays.deepEquals(d.strips, strips) && Arrays.deepEquals(d.paysFifths, paysFifths) && Arrays.equals(d.scatterFifths, scatterFifths)
			&& d.bonusReelsMask == bonusReelsMask && Arrays.equals(d.freeSpins, freeSpins) && d.retrigger == retrigger && d.fsCap == fsCap
			&& d.fsMultiplier == fsMultiplier && Arrays.equals(d.ladder, ladder) && Arrays.equals(d.ladderFree, ladderFree)
			&& d.capMultiple == capMultiple && d.buyPriceFifths == buyPriceFifths && d.features.equals(features);
	}

	@Override
	public int hashCode() {
		return Arrays.hashCode(new int[] {machine.ordinal(), Arrays.deepHashCode(strips), Arrays.deepHashCode(paysFifths), capMultiple,
			buyPriceFifths, features.hashCode()});
	}
}
