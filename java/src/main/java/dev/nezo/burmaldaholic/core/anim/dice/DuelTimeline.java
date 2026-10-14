package dev.nezo.burmaldaholic.core.anim.dice;

import dev.nezo.burmaldaholic.core.anim.SeedMix;

/**
 * The Dice Duel storyboard (DI §3.3, PHASE2B §4 DCb), ms on the ROUND clock: house = the result clock aligned to the
 * cup (see {@link #shakeOffset}), PvP = {@code levelMs − start·50 − roundStart(i)}. ONE source of truth for the duel
 * screens, the in-world stage and the server: the server posts each PvP round's chat lines at {@link #revealTick} of
 * that round, i.e. at {@link #COMPARE}, so text never runs ahead of the dice on any screen. Pure.
 *
 * <pre>
 *   0      both cups shake (counter-phase), rattle            ≈370  your wind-up (on a reversal), then the flick
 *   480    THROW: your die 0 leaves the cup (die 1 pours 140–200 ms later, the cup holds its tip until then)
 *   830    their die 0 leaves (THEM_DELAY 350; their cup keeps shaking until its own wind-up)
 *   1680   YOU_LAND: your pair rests (T_DUEL 1200), land rim, pip count-up, your plaque
 *   2030   THEM_LAND: their pair rests, count-up (fast), plaque         2030 → 2230 the silent beat
 *   2230   COMPARE: hop / dim / stamp / push shake / tie banner    2380  CHIPS (700 ms), outcome line
 *   3080   END: Roll re-enabled
 *   PvP: a tied round shows "Tie! Re-roll" for 600 ms, the dice are swept back into their own cups at 2580 (300 ms) and
 *   the next round starts {@link #ROUND_PERIOD} = 2900 ms after the previous one.
 * </pre>
 *
 * <p>"Order" 0 / 1 is the screen order (0 = this screen's own pair, thrown first; 1 = the dealer / opponent, +350 ms).
 * The in-world stage throws player A as order 0 and player B as order 1.
 */
public final class DuelTimeline {
	/** Solved duration of one pair, release of die 0 → the later die rests (DiceSolver target). */
	public static final int T_DUEL = 1200;
	/** Nominal release of your die 0 (the cup is aligned so the flick releases exactly here). */
	public static final int THROW = 480;
	/** Their release after yours (suspense offset). */
	public static final int THEM_DELAY = 350;
	public static final int YOU_LAND = THROW + T_DUEL;
	public static final int THEM_LAND = YOU_LAND + THEM_DELAY;
	/** No new sound between their landing and the compare. */
	public static final int SILENT_BEAT = 150;
	public static final int COMPARE = 2230;
	public static final int CHIPS = 2380;
	public static final int CHIPS_MS = 700;
	public static final int END = 3080;
	public static final int TIE_BANNER_MS = 600;
	public static final int SWEEP_AT = COMPARE + 350;
	public static final int SWEEP_MS = 300;
	/** Start-to-start distance of two PvP rounds (a tie: compare + banner + sweep). */
	public static final int ROUND_PERIOD = 2900;
	/** Plaque / badge pop length. */
	public static final int POP_MS = 250;
	/** Land rim after a pair rests, then the count-up starts. */
	public static final int LAND_RIM_MS = 60;
	/** Count-up: ms per pip (fewer when the budget is short, see {@link #pipMs}). */
	public static final int PIP_MS = 30;
	/** Count-up: the plaque rolls to the total. */
	public static final int ROLLUP_MS = 150;

	/**
	 * Shake ms at round time 0 when the result is in early: the wind-up starts on shake reversal 2 (406.5 ms of shaking,
	 * {@code ≈ Shake.SHAKE_MIN}) and the flick releases die 0 exactly at {@link #THROW}. 36.5 ms: the cups start
	 * shaking just before the round begins, so the first frame is almost at rest (no jump).
	 */
	public static final double SHAKE_LEAD = Shake.releaseAt(Shake.reversal(2)) - THROW;

	private DuelTimeline() {}

	/** Start of round {@code i} (0-based). */
	public static int roundStart(int i) {
		return i * ROUND_PERIOD;
	}

	/** Compare beat of round {@code i}: both totals are visible from here. */
	public static int reveal(int i) {
		return roundStart(i) + COMPARE;
	}

	/** Server tick offset (from the duel start) at which round {@code i}'s chat lines are posted (≥ {@link #reveal}). */
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

	/** Release of die 0 of the pair in screen order {@code order} (0 first, 1 = +{@link #THEM_DELAY}) in round {@code i}. */
	public static int throwAt(int i, int order) {
		return roundStart(i) + THROW + (order == 1 ? THEM_DELAY : 0);
	}

	/** Rest of the later die of pair {@code order} in round {@code i}. */
	public static int landAt(int i, int order) {
		return throwAt(i, order) + T_DUEL;
	}

	/**
	 * The cosmetic seed of one PvP (or house) roll, keyed by PLAYER side, never by screen side (DI §3.4): A's dice move
	 * the same on A's screen, on B's screen (mirrored) and in the world.
	 *
	 * @param seed   the duel's cosmetic seed (from the server result)
	 * @param round  0-based round
	 * @param sideAB 0 = player A (or you vs the house), 1 = player B (or the dealer)
	 */
	public static int rollSeed(int seed, int round, int sideAB) {
		return SeedMix.mix(seed, 0x44554C, round, sideAB);
	}

	/**
	 * House mode: the shake ms at which round time 0 falls, when the result reached the screen {@code readyShakeMs}
	 * after the cups started shaking (Roll pressed). The wind-up waits for the first reversal at or after both the
	 * result and reversal 2; the flick then releases die 0 and that instant is {@link #THROW} on the round clock. The
	 * shift is local only (nothing on the server waits on a house roll). PvP always uses {@link #SHAKE_LEAD}.
	 */
	public static double shakeOffset(double readyShakeMs) {
		double windUp = Shake.reversal(Shake.reversalAtOrAfter(Math.max(readyShakeMs, Shake.reversal(2))));
		return Shake.releaseAt(windUp) - THROW;
	}

	/** Wind-up start (shake clock) of the cup of pair {@code order} for a round whose time 0 is shake ms {@code offset}. */
	public static double windUp(int order, double offset) {
		return throwAt(0, order) + offset - Shake.WIND_UP - Shake.RELEASE_IN_FLICK;
	}

	/**
	 * Pip interval of a count-up of {@code pips} pips for pair {@code order}: {@link #PIP_MS}, shortened so your
	 * count-up ends by their landing and theirs by the silent beat's end (both totals are known at the compare).
	 */
	public static double pipMs(int order, int pips) {
		double budget = order == 0 ? THEM_LAND - YOU_LAND - LAND_RIM_MS : COMPARE - THEM_LAND - LAND_RIM_MS - 20;
		return Math.min(PIP_MS, budget / Math.max(1, pips));
	}

	/** Length of the count-up of pair {@code order} with dice {@code a}, {@code b} (land rim + pips; 0 in reduced motion). */
	public static double countUpMs(int order, int a, int b) {
		return LAND_RIM_MS + pipMs(order, a + b) * (a + b);
	}

	/**
	 * One duel cup on the SHAKE clock (DI §3.2 + the DCa pour note): {@link Shake} until the wind-up, then the wind-up
	 * and the flick, and the cup HOLDS its 70° tip until die 1 has poured out ({@code DiceRoll.releaseMs(1)} + 40 ms)
	 * before it recovers. Pure; a cup per side and round.
	 */
	public static final class Cup {
		/** The cup stays tipped this long after the last die left. */
		public static final double POUR_TAIL = 40;
		private final Shake shake;
		private final double windUp;
		private final double hold;

		/**
		 * @param shake      the cup's shake (seeded; counter-phase for the opponent / dealer)
		 * @param windUp     wind-up start on the shake clock ({@code +∞} while waiting for the result)
		 * @param lastPourMs release of the last die after die 0 (roll ms; 0 for one die)
		 */
		public Cup(Shake shake, double windUp, double lastPourMs) {
			this.shake = shake;
			this.windUp = windUp;
			this.hold = Math.max(Shake.FLICK, Shake.RELEASE_IN_FLICK + lastPourMs + POUR_TAIL);
		}

		public double windUp() {
			return windUp;
		}

		/** Start of the recover (shake clock). */
		public double recoverAt() {
			return windUp + Shake.WIND_UP + hold;
		}

		/** Cup frame ({@link Shake#F_REST} … {@link Shake#F_RECOVER}) at shake ms {@code ms}. */
		public int frame(double ms) {
			if (ms < windUp + Shake.WIND_UP + Shake.FLICK) return shake.frame(ms, windUp);
			if (ms < recoverAt()) return Shake.F_TIP70;
			double r = (ms - recoverAt()) / Shake.RECOVER;
			if (r < 0.22) return Shake.F_TIP45;
			if (r < 0.5) return Shake.F_TIP20;
			if (r < 0.8) return Shake.F_RECOVER;
			return Shake.F_REST;
		}

		/** Cup x offset in px (shake only). */
		public double offsetX(double ms) {
			return shake.offsetX(ms, windUp);
		}

		/** Cup lift in px: up during the wind-up, held through the flick and the pour, down over the recover. */
		public double lift(double ms) {
			double f = ms - windUp;
			if (f <= 0) return 0;
			if (f < Shake.WIND_UP) {
				double s = f / Shake.WIND_UP;
				return Shake.LIFT_PX * (1 - (1 - s) * (1 - s));
			}
			if (ms < recoverAt()) return Shake.LIFT_PX;
			double r = (ms - recoverAt()) / Shake.RECOVER;
			return r < 1 ? Shake.LIFT_PX * (1 - r) * (1 - r) : 0;
		}

		/** True while the cup is tipped (TIP frames, flick + pour hold). */
		public boolean tipped(double ms) {
			int f = frame(ms);
			return f == Shake.F_TIP20 || f == Shake.F_TIP45 || f == Shake.F_TIP70;
		}
	}
}
