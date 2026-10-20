package dev.nezo.burmaldaholic.games.extras.logic;

import dev.nezo.burmaldaholic.core.anim.dice.DiceEvent;
import dev.nezo.burmaldaholic.core.anim.dice.DiceRoll;
import dev.nezo.burmaldaholic.core.anim.dice.DiceSolver;
import dev.nezo.burmaldaholic.core.anim.dice.DiceTray;
import dev.nezo.burmaldaholic.core.anim.dice.DieSample;
import dev.nezo.burmaldaholic.core.anim.dice.DuelTimeline;
import dev.nezo.burmaldaholic.core.anim.dice.Quat;
import dev.nezo.burmaldaholic.core.anim.dice.ThrowSpec;
import dev.nezo.burmaldaholic.core.anim.motion.MotionEvent;
import java.util.List;

/**
 * The four in-world dice of a PvP Dice Duel (DI §6, PHASE2B §4 DCb), pure and shared by the server (item-display
 * fallback for players without the mod) and the modded client's {@code DuelStageRenderer}: each player's pair is thrown
 * from the chest toward the opponent on {@link DiceTray#worldLane} with {@link ThrowSpec#chest}, solved by
 * {@link DiceSolver} with the SAME key as the duel screens ({@link DuelTimeline#rollSeed}(seed, round, side)) and the
 * same beats (A at {@code throwAt(r, 0)}, B at {@code throwAt(r, 1)}), so the world, both screens and the delayed chat
 * lines tell one story. Faces only come from the relabelled model (the motion never depends on the result); the rest
 * pose is the exact group element of the server's face (tested). A tied round's dice are picked up (scale → 0 over
 * {@link #PICKUP_MS}) at the sweep beat; the last round's dice stay {@link #LINGER_MS} after its reveal.
 *
 * <p>Frames: per side the TRAY origin is the thrower's chest projected on the ground, tray x toward the opponent, tray y
 * to the thrower's right (MC: {@code X = fx·x − fz·y, Z = fz·x + fx·y}), tray z up, {@link DiceTray#WORLD_BLOCKS_PER_U}
 * blocks per tray unit. Orientations are MC model quaternions {@code Ry(yaw) · trayToMc(model)}.
 */
public final class DuelStagePlan {
	/** Die edge in blocks (2 tray units). */
	public static final double DIE = 2 * DiceTray.WORLD_BLOCKS_PER_U;
	/** How long the last round's dice (and totals) stay after its reveal (60 t). */
	public static final int LINGER_MS = 3000;
	/** Pick-up of a tied round's dice. */
	public static final int PICKUP_MS = 250;
	/** Shortest lane the solver gets (players closer than this still see a sensible throw). */
	public static final double MIN_LANE = 1.2;
	/** Longest lane (blocks). */
	public static final double MAX_LANE = 16;

	/** A world point. */
	public record Point(double x, double y, double z) {}

	/** One sample of one die (mutable, reused). */
	public static final class Sample {
		public boolean visible;
		/** World centre of the die. */
		public double x;
		public double y;
		public double z;
		/** Height of the lowest point above the ground, blocks. */
		public double zLow;
		/** Scale factor of the die (1, or shrinking while picked up). */
		public double scale;
		/** MC model orientation (world yaw applied), {@code {w, x, y, z}}. */
		public final double[] q = new double[4];
		/** The die's phase ({@link DieSample#HELD} … {@link DieSample#REST}). */
		public int phase;
		/** The die is at rest (REST phase): its top face is the server's face. */
		public boolean resting;
		/** The face the server rolled for this die (shown on top once resting). */
		public int face;
		public int round;
		/** The tray sample behind this (tray units, model orientation in the tray frame). */
		public final DieSample tray = new DieSample();
	}

	private final List<int[][]> rounds;
	private final DiceRoll[][] rolls;
	private final double[] ox = new double[2];
	private final double[] oz = new double[2];
	private final double[] fx = new double[2];
	private final double[] fz = new double[2];
	private final double[][] yawQ = new double[2][4];
	private final double groundY;
	private final double lane;
	private final double[] mc = new double[4];

	/**
	 * @param seed    the duel's cosmetic seed (the one the screens get)
	 * @param rounds  per round {@code [side][die]} faces, side 0 = player A, 1 = player B
	 * @param chestA  player A's chest
	 * @param chestB  player B's chest
	 * @param groundY height of the ground under the midpoint (the dice rest on it)
	 */
	public DuelStagePlan(int seed, List<int[][]> rounds, Point chestA, Point chestB, double groundY) {
		if (rounds.isEmpty()) throw new IllegalArgumentException("no rounds");
		this.rounds = List.copyOf(rounds);
		this.groundY = groundY;
		double dx = chestB.x() - chestA.x();
		double dz = chestB.z() - chestA.z();
		double len = Math.hypot(dx, dz);
		if (len < 1e-6) {
			dx = 1;
			dz = 0;
			len = 1;
		}
		dx /= len;
		dz /= len;
		this.lane = Math.max(MIN_LANE, Math.min(MAX_LANE, len));
		// both pairs meet at the real midpoint: a lane longer than the gap starts each thrower a little behind the chest
		double back = (lane - len) / 2;
		Point[] chest = {chestA, chestB};
		DiceTray tray = DiceTray.worldLane(lane);
		for (int s = 0; s < 2; s++) {
			double k = s == 0 ? 1 : -1;
			fx[s] = dx * k;
			fz[s] = dz * k;
			ox[s] = chest[s].x() - fx[s] * back;
			oz[s] = chest[s].z() - fz[s] * back;
			// MC yaw about +Y by −θ, θ = atan2(fz, fx): tray x → (fx, 0, fz)
			double half = -Math.atan2(fz[s], fx[s]) / 2;
			Quat.set(yawQ[s], Math.cos(half), 0, Math.sin(half), 0);
		}
		this.rolls = new DiceRoll[this.rounds.size()][2];
		for (int i = 0; i < this.rounds.size(); i++) {
			int[][] rd = this.rounds.get(i);
			for (int s = 0; s < 2; s++) {
				double h = Math.max(0.4, Math.min(2.0, chest[s].y() - groundY));
				int[] faces = {clampFace(rd[s][0]), clampFace(rd[s][1])};
				rolls[i][s] = DiceSolver.solve(tray, ThrowSpec.chest(h), DuelTimeline.rollSeed(seed, i, s), faces, DuelTimeline.T_DUEL);
			}
		}
	}

	private static int clampFace(int f) {
		return Math.max(1, Math.min(6, f));
	}

	public int roundCount() {
		return rounds.size();
	}

	/** The solved roll of {@code side} in round {@code round} (roll clock: 0 = die 0 leaves the hand). */
	public DiceRoll roll(int round, int side) {
		return rolls[round][side];
	}

	/** Lane length in blocks the rolls were solved on. */
	public double lane() {
		return lane;
	}

	public double groundY() {
		return groundY;
	}

	/** The whole show, ms from the duel start: the last reveal plus the linger. */
	public int endMs() {
		return DuelTimeline.reveal(rounds.size() - 1) + LINGER_MS;
	}

	/** Release of side's die 0 in round {@code round} (duel ms): A first, B {@link DuelTimeline#THEM_DELAY} later. */
	public int throwAt(int round, int side) {
		return DuelTimeline.throwAt(round, side);
	}

	/** Rest of side's later die in round {@code round} (duel ms). */
	public double restAt(int round, int side) {
		return throwAt(round, side) + rolls[round][side].restMs();
	}

	/** Total of {@code side} in round {@code round}. */
	public int total(int round, int side) {
		int[][] rd = rounds.get(round);
		return rd[side][0] + rd[side][1];
	}

	/** Faces of {@code side} in round {@code round} (a copy). */
	public int[] faces(int round, int side) {
		return rounds.get(round)[side].clone();
	}

	/** The round shown at {@code tMs}. */
	public int roundAt(double tMs) {
		return DuelTimeline.roundAt(tMs, rounds.size());
	}

	/** Pick-up progress 0..1 of the round at {@code tMs} (0 = on the ground; only tied rounds are picked up). */
	public double pickup(int round, double tMs) {
		if (round >= rounds.size() - 1) return 0;
		double since = tMs - (DuelTimeline.roundStart(round) + DuelTimeline.SWEEP_AT);
		return since <= 0 ? 0 : Math.min(1, since / PICKUP_MS);
	}

	/** When side's total billboard appears in round {@code round}: the pair's rest + the screens' count-up. */
	public double totalAt(int round, int side) {
		int[] f = rounds.get(round)[side];
		return restAt(round, side) + DuelTimeline.countUpMs(side, f[0], f[1]);
	}

	/** Whether side's total is shown at {@code tMs} (from its count-up until the pick-up / the end). */
	public boolean totalVisible(int side, double tMs) {
		int r = roundAt(tMs);
		return tMs >= totalAt(r, side) && pickup(r, tMs) <= 0 && tMs < endMs();
	}

	/** Centre of side's resting pair in round {@code round} (for the total billboard), on the ground. */
	public Point restCentre(int round, int side) {
		DiceRoll roll = rolls[round][side];
		double[] a = roll.restPoint(0);
		double[] b = roll.restPoint(1);
		return world(side, (a[0] + b[0]) / 2, (a[1] + b[1]) / 2, 0);
	}

	/** World point of tray point (x, y) at height z (tray units) of {@code side}. */
	public Point world(int side, double x, double y, double z) {
		double h = DiceTray.WORLD_BLOCKS_PER_U;
		return new Point(ox[side] + (fx[side] * x - fz[side] * y) * h, groundY + z * h, oz[side] + (fz[side] * x + fx[side] * y) * h);
	}

	/** World x of tray point (x, y) of {@code side} (allocation-free). */
	public double worldX(int side, double x, double y) {
		return ox[side] + (fx[side] * x - fz[side] * y) * DiceTray.WORLD_BLOCKS_PER_U;
	}

	/** World z of tray point (x, y) of {@code side} (allocation-free). */
	public double worldZ(int side, double x, double y) {
		return oz[side] + (fz[side] * x + fx[side] * y) * DiceTray.WORLD_BLOCKS_PER_U;
	}

	/** Tray origin x of {@code side} (the thrower's chest on the ground, moved back for short lanes). */
	public double originX(int side) {
		return ox[side];
	}

	public double originZ(int side) {
		return oz[side];
	}

	/** Yaw of {@code side}'s tray in degrees about +Y (MC: rotate the tray frame by this to get world axes). */
	public double yawDeg(int side) {
		return Math.toDegrees(-Math.atan2(fz[side], fx[side]));
	}

	/** Events of side's roll in round {@code round}, shifted to duel ms (a new array). */
	public MotionEvent[] events(int round, int side) {
		MotionEvent[] ev = rolls[round][side].events();
		MotionEvent[] out = new MotionEvent[ev.length];
		double at = throwAt(round, side);
		for (int i = 0; i < ev.length; i++) out[i] = ev[i].shifted(at);
		return out;
	}

	/** Duel ms of side's first floor impact in round {@code round} (−1 if none). */
	public double firstFloor(int round, int side) {
		for (MotionEvent e : rolls[round][side].events()) if (e.kind() == DiceEvent.FLOOR) return throwAt(round, side) + e.tMs();
		return -1;
	}

	/** Samples die {@code die} (0 / 1) of {@code side} (0 = A, 1 = B) at {@code tMs} after the duel start. */
	public void sample(int side, int die, double tMs, Sample out) {
		int r = roundAt(tMs);
		out.round = r;
		out.face = rounds.get(r)[side][die];
		double tt = tMs - throwAt(r, side);
		double pick = pickup(r, tMs);
		DiceRoll roll = rolls[r][side];
		DieSample s = out.tray;
		roll.sample(die, tt, s);
		out.phase = s.phase;
		if (s.phase == DieSample.HELD || pick >= 1 || tMs >= endMs()) {
			out.visible = false;
			out.resting = false;
			out.scale = 0;
			return;
		}
		double h = DiceTray.WORLD_BLOCKS_PER_U;
		out.visible = true;
		out.x = worldX(side, s.x, s.y);
		out.z = worldZ(side, s.x, s.y);
		out.y = groundY + s.zc * h;
		out.zLow = s.zLow * h;
		out.scale = 1 - pick;
		out.resting = s.phase == DieSample.REST;
		Quat.trayToMc(s.q, mc);
		Quat.mul(yawQ[side], mc, out.q);
	}
}
