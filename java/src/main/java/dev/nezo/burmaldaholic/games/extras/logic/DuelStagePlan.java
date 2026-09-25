package dev.nezo.burmaldaholic.games.extras.logic;

import dev.nezo.burmaldaholic.core.anim.dice.DiceThrowPath;
import dev.nezo.burmaldaholic.core.anim.dice.DuelTimeline;
import java.util.List;

/**
 * Where the four in-world dice of a PvP Dice Duel are at any moment (docs/design/animation/tables.md §3.4): each
 * player's pair is thrown from their chest (0.4 block toward the opponent) to the midpoint between the two players, on
 * the same no-wall {@link DiceThrowPath} and the same round beats ({@link DuelTimeline}) as the duel screens, so the
 * world, both screens and the delayed chat lines tell one story. 1 path px = 1/40 block. A tied round's dice are
 * picked up (scale → 0 over 250 ms) at the sweep beat and thrown again with the next round; the last round's dice
 * stay {@link #LINGER_MS} after its reveal. The final pose shows the server's faces (tested). Pure.
 */
public final class DuelStagePlan {
	/** Blocks per path pixel. */
	public static final double PX = 1 / 40.0;
	/** Die edge in blocks. */
	public static final double DIE = 0.2;
	/** How long the last round's dice (and totals) stay after its reveal (60 t). */
	public static final int LINGER_MS = 3000;
	/** Pick-up of a tied round's dice. */
	public static final int PICKUP_MS = 250;
	/** Throw origin ahead of the chest, toward the opponent. */
	public static final double AHEAD = 0.4;

	/** A world point. */
	public record Point(double x, double y, double z) {}

	/** One sample of one die (mutable, reused). */
	public static final class Sample {
		public boolean visible;
		public double x;
		public double y;
		public double z;
		/** Scale factor of the die (1, or shrinking while picked up). */
		public double scale;
		/** The face that is up once {@link #faceShown} (always the server's face at rest). */
		public int face;
		/** The real face is up (≥ 82 % of the throw); before that the die tumbles. */
		public boolean faceShown;
		/** Heading of the die about the vertical axis, degrees. */
		public double yaw;
		/** Tumble about the horizontal axis across the throw, degrees (0 once the face shows). */
		public double tumble;
		public boolean resting;
		public int round;
	}

	private final List<int[][]> rounds;
	private final DiceThrowPath[][] paths;
	private final double[] sx = new double[2];
	private final double[] sy = new double[2];
	private final double[] sz = new double[2];
	private final double[] fx = new double[2];
	private final double[] fz = new double[2];
	private final double[] heading = new double[2];
	private final double restY;
	private final DiceThrowPath.Sample tmp = new DiceThrowPath.Sample();

	/**
	 * @param seed    the duel's cosmetic seed (the one the screens get)
	 * @param rounds  per round {@code [side][die]} faces, side 0 = player A, 1 = player B
	 * @param chestA  player A's chest
	 * @param chestB  player B's chest
	 * @param groundY height of the felt / ground under the midpoint (the dice rest on it)
	 */
	public DuelStagePlan(int seed, List<int[][]> rounds, Point chestA, Point chestB, double groundY) {
		if (rounds.isEmpty()) {
			throw new IllegalArgumentException("no rounds");
		}
		this.rounds = List.copyOf(rounds);
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
		Point[] chest = {chestA, chestB};
		for (int s = 0; s < 2; s++) {
			double k = s == 0 ? 1 : -1;
			fx[s] = dx * k;
			fz[s] = dz * k;
			double ahead = Math.min(AHEAD, len / 4);
			sx[s] = chest[s].x() + fx[s] * ahead;
			sy[s] = chest[s].y();
			sz[s] = chest[s].z() + fz[s] * ahead;
			heading[s] = Math.toDegrees(Math.atan2(-fx[s], fz[s]));
		}
		this.restY = groundY + DIE / 2;
		// in each side's frame: x along the throw, y to the thrower's left; both pairs land short of the midpoint,
		// on opposite sides of the centre line (the frames are mirrored, so the same y band is the other side)
		double half = Math.max(0, len / 2 - AHEAD);
		double reach = half / PX;
		double gap = DIE / PX + 2;
		double restX0 = Math.max(4, reach - 2 * gap);
		this.paths = new DiceThrowPath[this.rounds.size()][2];
		for (int i = 0; i < this.rounds.size(); i++) {
			int[][] rd = this.rounds.get(i);
			for (int s = 0; s < 2; s++) {
				paths[i][s] = DiceThrowPath.of(seed + i * 2 + s, rd[s][0], rd[s][1], DiceThrowPath.Params.duel(0, 0, restX0, -10, restX0 + 4, -5, gap));
			}
		}
	}

	public int roundCount() {
		return rounds.size();
	}

	/** The whole show, ms from the duel start: the last reveal plus the linger. */
	public int endMs() {
		return DuelTimeline.reveal(rounds.size() - 1) + LINGER_MS;
	}

	/** Throw duration of one pair. */
	public int throwMs() {
		return DiceThrowPath.DUEL_MS;
	}

	/** Total of {@code side} in round {@code round}. */
	public int total(int round, int side) {
		int[][] rd = rounds.get(round);
		return rd[side][0] + rd[side][1];
	}

	/** The round shown at {@code tMs}. */
	public int roundAt(double tMs) {
		return DuelTimeline.roundAt(tMs, rounds.size());
	}

	/** Pick-up progress 0..1 of the round at {@code tMs} (0 = on the ground; only tied rounds are picked up). */
	private double pickup(int round, double tMs) {
		if (round >= rounds.size() - 1) {
			return 0;
		}
		double since = tMs - (DuelTimeline.roundStart(round) + DuelTimeline.SWEEP_AT);
		return since <= 0 ? 0 : Math.min(1, since / PICKUP_MS);
	}

	/** Whether side's total is shown at {@code tMs} (from its landing until the pick-up / the end). */
	public boolean totalVisible(int side, double tMs) {
		int r = roundAt(tMs);
		double landed = DuelTimeline.throwAt(r, side) + throwMs();
		return tMs >= landed && pickup(r, tMs) <= 0 && tMs < endMs();
	}

	/** Centre of side's resting pair in round {@code round} (for the total billboard). */
	public Point restCentre(int round, int side) {
		DiceThrowPath p = paths[round][side];
		double lx = (p.restX(0) + p.restX(1)) / 2;
		double ly = (p.restY(0) + p.restY(1)) / 2;
		return world(side, lx, ly, restY);
	}

	private Point world(int side, double lx, double ly, double y) {
		// left of the throw direction (fx, fz) is (fz, -fx)
		double x = sx[side] + (fx[side] * lx + fz[side] * ly) * PX;
		double z = sz[side] + (fz[side] * lx - fx[side] * ly) * PX;
		return new Point(x, y, z);
	}

	/** Samples die {@code die} (0 / 1) of {@code side} (0 = A, 1 = B) at {@code tMs} after the duel start. */
	public void sample(int side, int die, double tMs, Sample out) {
		int r = roundAt(tMs);
		out.round = r;
		out.face = rounds.get(r)[side][die];
		double tt = tMs - DuelTimeline.throwAt(r, side);
		double pick = pickup(r, tMs);
		if (tt < 0 || pick >= 1 || tMs >= endMs()) {
			out.visible = false;
			out.scale = 0;
			return;
		}
		DiceThrowPath p = paths[r][side];
		p.sample(die, tt, tmp);
		double T = p.durationMs();
		double e = Math.min(1, tt / (0.45 * T));
		double base = sy[side] + (restY - sy[side]) * e * e;
		Point w = world(side, tmp.x, tmp.y, base + tmp.z * PX);
		out.visible = true;
		out.x = w.x();
		out.y = tmp.resting ? restY : w.y();
		out.z = w.z();
		out.scale = 1 - pick;
		out.faceShown = tmp.frame < 0;
		out.tumble = out.faceShown ? 0 : tmp.frame * 45.0;
		out.yaw = heading[side] + (tmp.resting ? Math.round(tmp.theta / 90.0) * 90.0 : tmp.theta);
		out.resting = tmp.resting;
	}
}
