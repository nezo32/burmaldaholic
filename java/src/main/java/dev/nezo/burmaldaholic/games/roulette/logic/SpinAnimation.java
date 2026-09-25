package dev.nezo.burmaldaholic.games.roulette.logic;

/**
 * Wheel animation curve (UI.md §7): the ball travels {@code laps} full turns plus the distance to the
 * result pocket with a cubic ease-out. The result is decided on the server before the animation starts;
 * this only replays it. Pure Java.
 */
public final class SpinAnimation {
	private SpinAnimation() {}

	/**
	 * Fractional index into {@link Wheel#ORDER} of the pocket under the ball.
	 *
	 * @param progress 0..1 of the spin (clamped); at 1 the value is exactly the result's index (mod 37)
	 */
	public static double position(int result, double progress, int startIndex, int laps) {
		int n = Wheel.POCKETS;
		int target = Wheel.wheelIndex(result);
		int steps = laps * n + Math.floorMod(target - startIndex, n);
		double t = Math.max(0, Math.min(1, progress));
		double eased = 1 - Math.pow(1 - t, 3);
		return startIndex + steps * eased;
	}

	/** Pocket number at a (fractional) wheel position. */
	public static int pocketAt(double position) {
		int n = Wheel.POCKETS;
		return Wheel.ORDER.get(Math.floorMod((int) Math.floor(position + 0.5), n));
	}
}
