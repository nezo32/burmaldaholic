package dev.nezo.burmaldaholic.games.extras.logic.anim;

import dev.nezo.burmaldaholic.core.anim.SeedMix;

/**
 * One Wheel of Fortune spin as the machine publishes it to every nearby client (extras-pvp.md §3.4 {@code SpinSync}):
 * sent ONCE when the spin starts, so spectators' in-world wheel turns on the same {@link WheelAnim} curve as the
 * player's screen and rests on the same seeded angle inside the drawn segment. The spin is solo and already settled
 * (the player's screen has the same data); nothing about it is secret from the table's neighbours, but the far LOD
 * still shows the previous rest until the spin has ended ({@link #shownAngle}), so the outcome is never on display
 * before the wheel stops.
 *
 * <p>PURE. Wire form: an {@code int[]} ({@link #encode} / {@link #decode}) in the block entity's update tag.
 *
 * @param seq       the machine's spin counter (the screen's {@code result.seq}: same rest-angle seed)
 * @param startTick level game time of the spin start
 * @param index     drawn segment
 * @param prevIndex segment of the previous spin at this machine (−1: none, the wheel starts at 0°)
 * @param prevSeq   seq of the previous spin (its rest-angle seed)
 * @param spinTicks spin length ({@code WheelBlockEntity.SPIN_TICKS})
 * @param segments  the machine's segment codes in wheel order (config; Appendix B by default)
 */
public record WheelSync(int seq, long startTick, int index, int prevIndex, int prevSeq, int spinTicks, String segments) {
	public static final int VERSION = 1;
	/** Codes in {@code wheel_icons} order: B C H M D T E X. */
	public static final String CODES = "BCHMDTEX";

	public WheelSync {
		if (segments == null || segments.isEmpty()) {
			throw new IllegalArgumentException("wheel sync: no segments");
		}
		if (index < 0 || index >= segments.length()) {
			throw new IllegalArgumentException("wheel sync: index " + index + " of " + segments.length());
		}
		spinTicks = Math.max(1, spinTicks);
	}

	public int segmentCount() {
		return segments.length();
	}

	/** The rest-angle seed of a spin (shared with the screen: {@code SeedMix.mix(hash("wheel"), seq)}). */
	public static int seed(int seq) {
		return SeedMix.mix(SeedMix.hash("wheel"), seq);
	}

	/** Rotation before this spin: the previous spin's rest angle (0° when there was none). */
	public double from() {
		int n = segmentCount();
		return prevIndex < 0 || prevIndex >= n ? 0 : WheelAnim.restAngle(prevIndex, n, seed(prevSeq));
	}

	/** Rotation after this spin: the drawn segment's seeded rest angle. */
	public double rest() {
		return WheelAnim.restAngle(index, segmentCount(), seed(seq));
	}

	public int totalMs() {
		return spinTicks * 50;
	}

	/** Rotation {@code ms} after the start: the screen's curve (reduce motion: the straight 600 ms turn). */
	public double angle(double ms, boolean reduceMotion) {
		double from = from();
		double rest = rest();
		if (ms <= 0) return from;
		if (reduceMotion) return WheelAnim.reduced(from, rest, ms);
		return WheelAnim.angle(from, rest, totalMs(), ms, WheelAnim.settleRoom(rest, index, segmentCount()));
	}

	/** True once the wheel has stopped (the stop beat may start). */
	public boolean stopped(double ms, boolean reduceMotion) {
		return ms >= (reduceMotion ? WheelAnim.REDUCED_MS : totalMs());
	}

	/**
	 * The rotation a viewer is shown: the full curve when {@code animate}, else the previous rest until the spin has
	 * ended and the new rest afterwards (far LOD and late joiners never see the outcome ahead of the stop).
	 */
	public double shownAngle(double ms, boolean animate, boolean reduceMotion) {
		if (animate) return angle(ms, reduceMotion);
		return stopped(ms, false) ? rest() : from();
	}

	public int[] encode() {
		int n = segmentCount();
		int[] out = new int[9 + n];
		out[0] = VERSION;
		out[1] = seq;
		out[2] = (int) (startTick >>> 32);
		out[3] = (int) startTick;
		out[4] = index;
		out[5] = prevIndex;
		out[6] = prevSeq;
		out[7] = spinTicks;
		out[8] = n;
		for (int i = 0; i < n; i++) {
			out[9 + i] = Math.max(0, CODES.indexOf(segments.charAt(i)));
		}
		return out;
	}

	public static WheelSync decode(int[] a) {
		if (a == null || a.length < 9 || a[0] != VERSION || a[8] <= 0 || a.length != 9 + a[8]) {
			throw new IllegalArgumentException("wheel sync: bad data");
		}
		StringBuilder s = new StringBuilder(a[8]);
		for (int i = 0; i < a[8]; i++) {
			int c = a[9 + i];
			if (c < 0 || c >= CODES.length()) {
				throw new IllegalArgumentException("wheel sync: bad segment code " + c);
			}
			s.append(CODES.charAt(c));
		}
		long start = ((long) a[2] << 32) | (a[3] & 0xFFFFFFFFL);
		return new WheelSync(a[1], start, a[4], a[5], a[6], a[7], s.toString());
	}
}
