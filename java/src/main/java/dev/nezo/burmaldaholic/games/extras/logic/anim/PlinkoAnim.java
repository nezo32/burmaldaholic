package dev.nezo.burmaldaholic.games.extras.logic.anim;

import dev.nezo.burmaldaholic.core.anim.Ease;

/**
 * Plinko ball motion on the 272 × 204 board (visual/extras.md §5.1, extras-pvp.md §5.3). PURE, board-local GUI px.
 * Pegs of row {@code r} (0…11) sit at {@code x = 136 + (j − r/2)·20, y = 30 + 12r}; bin {@code k} is centred at
 * {@code x = 136 + (k − 6)·20} with its cap at y 176. The ball follows the server's path bit by bit (bit r = right at
 * row r), so after row r its column is the number of rights so far, and the terminal position is the centre of bin
 * {@code popcount(path)} for every path (fidelity test 3). Nothing here reads anything but the path.
 *
 * <p>Timeline for a row time {@code R} (server {@code step_ticks × 50}, 200 ms by default): release 0–250 ms, row r
 * from {@code 250 + R·r}, the fall into the bin, then the landing (cap press and label pop) — {@link #totalMs}.
 */
public final class PlinkoAnim {
	public static final int ROWS = 12;
	public static final int CENTER_X = 136;
	public static final int PITCH_X = 20;
	public static final int PITCH_Y = 12;
	public static final int ROW0_Y = 30;
	public static final int CAP_Y = 176;
	/** The ball rests this far above the peg it touches (ball 9 px, peg 7 px). */
	public static final int BALL_ABOVE = 4;
	public static final int RELEASE_MS = 250;
	public static final int FALL_MS = 300;
	public static final int LAND_MS = 300;
	public static final int REDUCED_ROW_MS = 80;
	public static final int SKIP_MS = 150;
	/** Ball rest y inside the landed cap. */
	public static final int BIN_BALL_Y = CAP_Y + 8;

	private PlinkoAnim() {}

	public static double pegX(int row, int j) {
		return CENTER_X + (j - row / 2.0) * PITCH_X;
	}

	public static double pegY(int row) {
		return ROW0_Y + row * PITCH_Y;
	}

	public static double binX(int bin) {
		return CENTER_X + (bin - ROWS / 2.0) * PITCH_X;
	}

	/** Rights in path bits 0…r−1 (the column of the peg touched at row r). */
	public static int rights(int path, int r) {
		int m = r >= 32 ? -1 : (1 << r) - 1;
		return Integer.bitCount(path & m);
	}

	public static int bin(int path) {
		return rights(path, ROWS);
	}

	public static int totalMs(int rowMs) {
		return RELEASE_MS + ROWS * rowMs + FALL_MS + LAND_MS;
	}

	/** When the ball lands in the bin (the cap lights, the landing sound plays). */
	public static int landMs(int rowMs) {
		return RELEASE_MS + ROWS * rowMs + FALL_MS;
	}

	/** Contact time of row {@code r}'s peg. */
	public static int contactMs(int rowMs, int r) {
		return RELEASE_MS + r * rowMs;
	}

	/**
	 * Ball state.
	 *
	 * @param x, y       centre, board-local
	 * @param sx, sy     squash
	 * @param roll       roll frame 0–3
	 * @param row        row being travelled (−1 release, 12 in the bin)
	 * @param landed     in the bin (the cap is lit)
	 * @param press      cap press 0–1 (2 px at 1)
	 * @param pop        multiplier-label pop progress 0–1 (−1 none)
	 */
	public record Ball(double x, double y, double sx, double sy, int roll, int row, boolean landed, double press, double pop) {}

	public static Ball terminal(int path) {
		return new Ball(binX(bin(path)), BIN_BALL_Y, 1, 1, 0, ROWS, true, 0, 1);
	}

	/** Ball at {@code ms} since the drop started (the path is the server's). */
	public static Ball sample(int path, int rowMs, double ms) {
		int total = totalMs(rowMs);
		if (ms >= total) return terminal(path);
		double x0 = CENTER_X;
		if (ms < RELEASE_MS) {
			double e = Ease.IN_CUBIC.apply(Math.max(0, ms) / RELEASE_MS);
			double y = 18 + (pegY(0) - BALL_ABOVE - 18) * e;
			return new Ball(x0, y, 1, 1, 0, -1, false, 0, -1);
		}
		double rowsEnd = RELEASE_MS + ROWS * rowMs;
		if (ms < rowsEnd) {
			double t = (ms - RELEASE_MS) / rowMs;
			int r = Math.min(ROWS - 1, (int) Math.floor(t));
			double q = t - r;
			int c0 = rights(path, r);
			int c1 = rights(path, r + 1);
			double xa = pegX(r, c0);
			double xb = pegX(r + 1, c1);
			double x = xa + (xb - xa) * Ease.IN_OUT_SINE.apply(q);
			double hop = 3.5 * Math.pow(0.93, r);
			double y = pegY(r) + PITCH_Y * q * q - hop * 4 * q * (1 - q) - BALL_ABOVE;
			boolean squash = q < 0.08;
			int dir = c1 > c0 ? 1 : -1;
			int roll = Math.floorMod((int) Math.floor(t * 4) * dir, 4);
			return new Ball(x, y, squash ? 1.15 : 1, squash ? 0.8 : 1, roll, r, false, 0, -1);
		}
		double bx = binX(bin(path));
		double fallEnd = rowsEnd + FALL_MS;
		if (ms < fallEnd) {
			double y0 = pegY(ROWS) - BALL_ABOVE;
			double e = Ease.IN_CUBIC.apply((ms - rowsEnd) / FALL_MS);
			return new Ball(bx, y0 + (BIN_BALL_Y - y0) * e, 1, 1, 0, ROWS, false, 0, -1);
		}
		double u = (ms - fallEnd) / LAND_MS;
		double press = u < 0.27 ? Math.sin(Math.PI * u / 0.27) : 0;
		double v = u / 0.5;
		double bounce = v < 1 ? 2 * 4 * v * (1 - v) : 0;
		return new Ball(bx, BIN_BALL_Y - bounce, 1, 1, 0, ROWS, true, press, Math.min(1, u));
	}

	/** Peg state at {@code ms}: 0 idle, 1 hit (100 ms after its contact), 2 afterglow (next 300 ms). */
	public static int pegState(int path, int rowMs, double ms, int row, int j) {
		if (j != rights(path, row)) return 0;
		double since = ms - contactMs(rowMs, row);
		if (since < 0) return 0;
		if (since < 100) return 1;
		return since < 400 ? 2 : 0;
	}

	/** Reduce motion: rows of the dotted path drawn so far (80 ms per row); the ball appears in the bin at the end. */
	public static int reducedRows(double ms) {
		return (int) Math.min(ROWS, Math.max(0, Math.floor(ms / REDUCED_ROW_MS)));
	}

	public static int reducedMs() {
		return ROWS * REDUCED_ROW_MS;
	}

	/** Peg-sound pitch for row r: left bounces low, right bounces high (0.75 + 0.9·column / max(1, r)). */
	public static float pegPitch(int path, int r) {
		return (float) (0.75 + 0.9 * rights(path, r) / Math.max(1, r));
	}

	/** Bin sound pitch by multiplier: 0.6 below 1× up to 1.6 at ≥ 100×. */
	public static float binPitch(double multiplier) {
		if (multiplier < 1) return 0.6f;
		if (multiplier >= 100) return 1.6f;
		return (float) (0.8 + 0.8 * Math.log10(multiplier) / 2);
	}

	/** Bin cap tier: 0 loss (&lt; 1), 1 even, 2 win (≤ 3), 3 big (≤ 33), 4 top (&gt; 33). */
	public static int tier(double multiplier) {
		if (multiplier < 1) return 0;
		if (multiplier == 1) return 1;
		if (multiplier <= 3) return 2;
		if (multiplier <= 33) return 3;
		return 4;
	}

	// ---- mini boards (Plinko Battle, 56 × 56: 12 rows at a 4 px pitch) -----------------------------------------------

	/**
	 * Unit position of a ball after {@code rowsDone} rows (fractional): {@code {col, row}} where the pixel is
	 * {@code cx + col·pitch, top + row·pitch}; col is relative to the centre ({@code rights − row/2}).
	 */
	public static double[] unit(int path, double rowsDone, int rows) {
		double t = Math.max(0, Math.min(rows, rowsDone));
		int r = Math.min(rows - 1, (int) Math.floor(t));
		double q = t - r;
		if (t >= rows) return new double[] {rights(path, rows) - rows / 2.0, rows};
		double ca = rights(path, r) - r / 2.0;
		double cb = rights(path, r + 1) - (r + 1) / 2.0;
		return new double[] {ca + (cb - ca) * Ease.IN_OUT_SINE.apply(q), r + q};
	}
}
