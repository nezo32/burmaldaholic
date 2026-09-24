package dev.nezo.burmaldaholic.games.slots.v2.present;

/**
 * Slot sound pitches and rate limits (slots.md §8, SLOTS.md §10.7; PURE). Everything is in C major pentatonic:
 * reel stops walk up C D E G A, scatters climb a higher ladder, wins resolve on C; loss is silent.
 */
public final class SoundPlan {
	/** Reel stop ladder 1.0 / 1.12 / 1.26 / 1.5 / 1.68. */
	public static final float[] REEL_STOP = {1.0f, 1.12f, 1.26f, 1.5f, 1.68f};
	/** Scatter ladder by count so far (1st / 2nd / 3rd+). */
	public static final float[] SCATTER = {1.0f, 1.26f, 1.5f};
	/** Tumble ladder step pitches. */
	public static final float[] MULT_UP = {1.0f, 1.12f, 1.26f, 1.5f};
	/** Per-player sound budget (≤ 20/s) and roll-up ticks (≤ 15/s). */
	public static final int MAX_PER_SECOND = 20;

	private SoundPlan() {}

	public static float reelStop(int reel) {
		return REEL_STOP[Math.max(0, Math.min(4, reel))];
	}

	public static float scatter(int nth) {
		return SCATTER[Math.max(0, Math.min(2, nth))];
	}

	public static float multUp(int step) {
		return MULT_UP[Math.max(0, Math.min(3, step))];
	}

	/** Anticipation loop pitch ramp 0.8 → 1.3 over the gap. */
	public static float anticipation(double progress) {
		return (float) (0.8 + 0.5 * Math.max(0, Math.min(1, progress)));
	}

	/** Hoard collect tick pitch: +3 % per coin, cap +45 %. */
	public static float collect(int coin) {
		return (float) Math.min(1.45, 1 + 0.03 * coin);
	}

	/** A minimal-gap gate (roll-up ticks ≤ 15/s, wheel ticks ≥ 50 ms apart, the per-player budget). */
	public static final class Gate {
		private final int gapMs;
		private double last = Double.NEGATIVE_INFINITY;

		public Gate(int gapMs) {
			this.gapMs = gapMs;
		}

		public boolean tryFire(double nowMs) {
			if (nowMs - last < gapMs) return false;
			last = nowMs;
			return true;
		}

		public void reset() {
			last = Double.NEGATIVE_INFINITY;
		}
	}

	/** Sliding one-second budget (≤ {@code max} events in any 1 000 ms). */
	public static final class Budget {
		private final double[] times;
		private int head;

		public Budget(int max) {
			times = new double[max];
			java.util.Arrays.fill(times, Double.NEGATIVE_INFINITY);
		}

		public boolean tryFire(double nowMs) {
			if (nowMs - times[head] < 1000) return false;
			times[head] = nowMs;
			head = (head + 1) % times.length;
			return true;
		}
	}
}
