package dev.nezo.burmaldaholic.core.anim.dice;

/**
 * How the dice leave the cup / hand / chest (PHASE2B §3.3, DI §3.2, CS §2.3 step 2). Tray units (u = die half-edge),
 * angles in radians, speeds in u/s, times in ms. Die 0 is released at roll time 0; die {@code i > 0} is released
 * {@code [delayMin, delayMax]} ms later (seeded), {@code side·i} u to the left of the throw direction.
 *
 * <p>The solver picks every contact first and derives the speeds from the target time (PHASE2B §3.1), so the speed and
 * cone ranges are plausibility bounds it prefers, not inputs it integrates.
 *
 * @param x        release centre x of die 0
 * @param y        release centre y of die 0
 * @param z0       release height of the die's lowest point
 * @param dir      throw direction (radians in the tray x, y plane; 0 = +x)
 * @param coneMin  smallest deflection from {@code dir} (radians; the sign is seeded)
 * @param coneMax  largest deflection from {@code dir}
 * @param speedMin preferred horizontal launch speed range
 * @param speedMax
 * @param vzMin    vertical launch speed range
 * @param vzMax
 * @param spinMin  release spin range, rad/s (the axis is biased horizontal, perpendicular to the throw)
 * @param spinMax
 * @param side     lateral offset between consecutive dice at release, u
 * @param delayMin release stagger of each further die, ms
 * @param delayMax
 */
public record ThrowSpec(double x, double y, double z0, double dir, double coneMin, double coneMax, double speedMin, double speedMax,
		double vzMin, double vzMax, double spinMin, double spinMax, double side, double delayMin, double delayMax) {
	/** Hard cap of any angular speed the solver produces (DI §6: ≤ 2 ticks × 22 rad/s < 180° per display step). */
	public static final double MAX_SPIN = 22;

	public ThrowSpec {
		if (speedMax < speedMin || vzMax < vzMin || spinMax < spinMin || delayMax < delayMin || coneMax < coneMin)
			throw new IllegalArgumentException("range");
		if (spinMax > MAX_SPIN) throw new IllegalArgumentException("spin " + spinMax);
	}

	/**
	 * Dice Duel cup (DI §3.2): mouth at (46, 80) art px, lowest point 1.1 u up, toward the divider (+x; −x on the
	 * mirrored right lane) within ±[6°, 32°], 14–22 rad/s top/back spin, die 1 poured 30–60 ms after die 0.
	 */
	public static ThrowSpec cup(DiceTray.Side s) {
		double x = 46 / DiceTray.DUEL_PX;
		double y = 80 / DiceTray.DUEL_PX;
		boolean m = s.mirrored();
		return new ThrowSpec(m ? DiceTray.DUEL_W - x : x, y, 1.1, m ? Math.PI : 0, Math.toRadians(6), Math.toRadians(32), 6, 30, 2.5, 8,
			14, 22, m ? -0.8 : 0.8, 140, 200);
	}

	/**
	 * Craps shooter's hand (CS §2.3 step 2): the dice leave the hand at {@code (x, y)} (dice home on the shooter's
	 * rail), lowest point 1.2 u up, thrown toward {@code −y} (the back wall) and angled toward the table centre:
	 * {@code side} 0 = the shooter stands at the right end (aims left), 1 = at the left end (aims right). Spread ±4–14°;
	 * the second die leaves 3.5 u toward the table centre (whole-flight separation, as CM's hand).
	 */
	public static ThrowSpec hand(int side, double x, double y) {
		double base = -Math.PI / 2 + (side == 0 ? -1 : 1) * Math.toRadians(14);
		return new ThrowSpec(x, y, 1.2, base, 0, Math.toRadians(14), 14, 60, 18, 25, 12, 22, side == 0 ? -3.5 : 3.5, 15, 35);
	}

	/**
	 * In-world chest throw (DI §6): from the thrower's chest ({@code x = 0, y = 0}, {@code heightBlocks} above the
	 * ground) toward {@code +x}, within ±8°, a soft underhand toss.
	 */
	public static ThrowSpec chest(double heightBlocks) {
		double z0 = heightBlocks / DiceTray.WORLD_BLOCKS_PER_U;
		return new ThrowSpec(0, 0, z0, 0, 0, Math.toRadians(8), 6, 70, 0, 6, 10, 20, 3.0, 30, 60);
	}

	/** {@link #chest(double)} at 1.0 block (the hand in front of the chest). */
	public static ThrowSpec chest() {
		return chest(1.0);
	}
}
