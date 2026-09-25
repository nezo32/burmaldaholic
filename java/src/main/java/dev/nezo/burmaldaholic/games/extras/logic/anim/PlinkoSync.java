package dev.nezo.burmaldaholic.games.extras.logic.anim;

/**
 * One Plinko drop as the machine publishes it to every nearby client (extras-pvp.md §5.4 {@code DropSync}): sent ONCE
 * when the ball is released, so the in-world ball follows the same {@link PlinkoAnim} path as the player's screen. The
 * drop is solo and already settled; the landed lamp lights only at the landing time ({@link #lampLit}), never before,
 * and the far LOD shows no ball at all, so the result is not on display before the ball lands.
 *
 * <p>PURE. Wire form: an {@code int[]} in the block entity's update tag. The face of the machine has 13 lamps under a
 * 12 × 12 texel peg area: {@link #faceU} / {@link #faceV} map the board-local ball position onto face texels.
 *
 * @param seq       the machine's drop counter
 * @param startTick level game time of the release
 * @param path      the server's 12-bit path (bit r = right at row r)
 * @param stepTicks ticks per row ({@code PlinkoBlockEntity.STEP_TICKS})
 * @param tier      the landed bin's cap tier ({@link PlinkoAnim#tier}: 0 loss … 4 top)
 * @param edge      edge bin on High (the jackpot lamp is gold)
 */
public record PlinkoSync(int seq, long startTick, int path, int stepTicks, int tier, boolean edge) {
	public static final int VERSION = 1;
	/** How long the landed lamp stays lit after the landing (ms). */
	public static final int LAMP_MS = 3000;
	/** Blinks of the landed lamp (2 Hz) before it stays lit. */
	public static final int BLINK_MS = 1000;

	public PlinkoSync {
		path &= (1 << PlinkoAnim.ROWS) - 1;
		stepTicks = Math.max(1, stepTicks);
		tier = Math.max(0, Math.min(4, tier));
	}

	public int rowMs() {
		return stepTicks * 50;
	}

	public int bin() {
		return PlinkoAnim.bin(path);
	}

	public int landMs() {
		return PlinkoAnim.landMs(rowMs());
	}

	/** Ball on the board {@code ms} after the release (the screen's motion; reduce motion: the ball appears in the bin). */
	public PlinkoAnim.Ball ball(double ms, boolean reduceMotion) {
		if (reduceMotion) {
			return ms >= PlinkoAnim.reducedMs() ? PlinkoAnim.terminal(path) : null;
		}
		return PlinkoAnim.sample(path, rowMs(), ms);
	}

	/** Landed lamp state at {@code ms}: 0 dark (before the landing or after {@link #LAMP_MS}), 1 lit, 2 blink-off. */
	public int lampLit(double ms, boolean flashes) {
		double since = ms - landMs();
		if (since < 0 || since >= LAMP_MS) return 0;
		if (flashes && since < BLINK_MS && ((int) (since / 250)) % 2 == 1) return 2;
		return 1;
	}

	/** True while the ball is on the board (the BER draws it). */
	public boolean ballVisible(double ms) {
		return ms >= 0 && ms < PlinkoAnim.totalMs(rowMs()) + LAMP_MS;
	}

	/** Face texel column (0…16) of a board-local x: bin centres 0…12 map to texels 2 … 14 (the 13 one-texel lamps). */
	public static double faceU(double boardX) {
		return 2 + (boardX - (PlinkoAnim.CENTER_X - 6 * PlinkoAnim.PITCH_X)) / (12.0 * PlinkoAnim.PITCH_X) * 12;
	}

	/** Face texel row (0…16) of a board-local y: the chute at texel 1, the bins at texel 13.5. */
	public static double faceV(double boardY) {
		return 1 + (boardY - 18) / (PlinkoAnim.BIN_BALL_Y - 18.0) * 12.5;
	}

	/** Face texel column of lamp {@code bin} (its centre). */
	public static double lampU(int bin) {
		return faceU(PlinkoAnim.binX(bin));
	}

	public int[] encode() {
		return new int[] {VERSION, seq, (int) (startTick >>> 32), (int) startTick, path, stepTicks, tier, edge ? 1 : 0};
	}

	public static PlinkoSync decode(int[] a) {
		if (a == null || a.length != 8 || a[0] != VERSION) {
			throw new IllegalArgumentException("plinko sync: bad data");
		}
		long start = ((long) a[2] << 32) | (a[3] & 0xFFFFFFFFL);
		return new PlinkoSync(a[1], start, a[4], a[5], a[6], a[7] != 0);
	}
}
