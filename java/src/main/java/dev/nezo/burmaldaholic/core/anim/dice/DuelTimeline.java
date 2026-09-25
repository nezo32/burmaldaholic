package dev.nezo.burmaldaholic.core.anim.dice;

/**
 * The Dice Duel storyboard (docs/design/animation/tables.md §3.3), ms after the result reached the screen (house) or
 * after the duel started (PvP). ONE source of truth for the screen and the server: the server posts each PvP round's
 * chat lines at {@link #revealTick} of that round, so text trails the dice. Pure.
 *
 * <pre>
 *   0      cups shake (≥ 300 ms)            300   cups tip, both pairs thrown (no-wall path, T = 900 ms)
 *   1200   your dice rest, your total pops  1550  their dice rest (a 350 ms suspense offset), their total pops
 *   1750   compare: crush / crack / push shake / house stamp
 *   1900   chips move, outcome line          2600  done (Roll re-enabled)
 *   PvP: a tied round shows "Tie! Re-roll" for 600 ms, the dice are swept back into the cups, and the next round
 *   starts {@link #ROUND_PERIOD} ms after the previous one.
 * </pre>
 */
public final class DuelTimeline {
	public static final int SHAKE = 0;
	public static final int SHAKE_MIN = 300;
	public static final int THROW = 300;
	public static final int TIP_MS = 100;
	public static final int THEM_DELAY = 350;
	public static final int YOU_LAND = THROW + DiceThrowPath.DUEL_MS;
	public static final int THEM_LAND = YOU_LAND + THEM_DELAY;
	public static final int COMPARE = 1750;
	public static final int CHIPS = 1900;
	public static final int CHIPS_MS = 700;
	public static final int END = 2600;
	public static final int TIE_BANNER_MS = 600;
	public static final int SWEEP_AT = COMPARE + 350;
	public static final int SWEEP_MS = 300;
	/** Start-to-start distance of two PvP rounds (a tie: compare + banner + sweep). */
	public static final int ROUND_PERIOD = 2400;
	/** Badge pop length. */
	public static final int POP_MS = 250;

	private DuelTimeline() {}

	/** Start of round {@code i} (0-based). */
	public static int roundStart(int i) {
		return i * ROUND_PERIOD;
	}

	/** Compare beat of round {@code i}: both totals are visible from here. */
	public static int reveal(int i) {
		return roundStart(i) + COMPARE;
	}

	/** Server tick offset (from the duel start) at which round {@code i}'s chat lines are posted. */
	public static int revealTick(int i) {
		return (reveal(i) + 49) / 50;
	}

	/** Whole duel in ms (the last round plays to {@link #END}). */
	public static int endMs(int rounds) {
		return roundStart(Math.max(1, rounds) - 1) + END;
	}

	/** Round being played at {@code t} (clamped to the last one). */
	public static int roundAt(double t, int rounds) {
		int i = (int) Math.floor(Math.max(0, t) / ROUND_PERIOD);
		return Math.max(0, Math.min(rounds - 1, i));
	}

	/** Throw start of die pair {@code side} (0 you, 1 them) in round {@code i}. */
	public static int throwAt(int i, int side) {
		return roundStart(i) + THROW + (side == 1 ? THEM_DELAY : 0);
	}

	/** Cup frame (0 rest, 1 shake L, 2 shake R, 3 shake up, 4 tip 20°, 5 tip 35°) {@code t} ms into a round. */
	public static int cupFrame(double t, boolean reduced) {
		if (t < 0 || reduced) {
			return t >= THROW && t < THROW + 900 && !reduced ? 5 : 0;
		}
		if (t < THROW) {
			return 1 + (int) (t / 60) % 3;
		}
		if (t < THROW + TIP_MS / 2.0) {
			return 4;
		}
		if (t < THROW + 900) {
			return 5;
		}
		return 0;
	}

	/** Cup shake while waiting for the server's answer (frames 1–3 at 60 ms), {@code t} ms after the click. */
	public static int waitingCupFrame(double t) {
		return 1 + (int) (Math.max(0, t) / 60) % 3;
	}
}
