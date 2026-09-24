package dev.nezo.burmaldaholic.games.slots.v2.logic;

/**
 * Slot Showdown v2 scoring (SLOTS.md §9, replaces PVP.md §5). Each participant's spin is a solo v2 spin at
 * bet = 1 unit; points = 10 × win / bet. Fits the PvP engine contract ({@code PvpMode}) on the PvP-bots
 * branch: the mode's {@code draw} calls {@link SlotDraw} per spin and stores {@link Spin} records.
 * SKELETON — lane S-J8 (after the PvP-bots merge; pure part can start now).
 */
public final class Showdown {
	/** Hazards (SLOTS.md §9.2; {@code pvp.slots.hazardWeights} none/KABOOM/SWAP/TIME WARP). */
	public enum Hazard {
		NONE,
		KABOOM,
		SWAP,
		TIME_WARP
	}

	/**
	 * One participant spin as stored in the match tape (SLOTS.md §9.4).
	 *
	 * @param stops       5 base stops
	 * @param hazard      drawn hazard
	 * @param points      spin points before round modifiers (HOT applied)
	 * @param featureCode 0 none, 1 free spins, 2 hunt, 3 hoard, 4 wheel
	 * @param featureArg  free spins played, or bonus points
	 */
	public record Spin(int[] stops, Hazard hazard, long points, int featureCode, long featureArg) {}

	private Showdown() {}

	/** {@code 10 × win ÷ bet} from a total in fifths of the bet: {@code 2 × fifths} (always even). */
	public static long points(long totalFifths) {
		return 2 * totalFifths;
	}
}
