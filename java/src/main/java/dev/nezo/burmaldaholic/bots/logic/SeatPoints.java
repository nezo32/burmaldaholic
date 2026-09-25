package dev.nezo.burmaldaholic.bots.logic;

/**
 * Where a seat's avatar nameplate floats (BOTS.md §7.2): seats are spread over the half circle on the
 * players' side of the table (the side the table faces), {@link #RADIUS} blocks from the block centre,
 * {@link #HEIGHT} blocks above the table top. Pure math; the facing is a unit step (fx, fz) such as
 * (0, 1) for south.
 */
public final class SeatPoints {
	public static final double RADIUS = 1.1;
	public static final double HEIGHT = 1.6;

	private SeatPoints() {}

	/** @return {dx, dz}: horizontal offset of seat {@code index} (0-based) of {@code seats} from the block centre */
	public static double[] offset(int index, int seats, int fx, int fz) {
		int n = Math.max(1, seats);
		int i = Math.max(0, Math.min(index, n - 1));
		double angle = Math.PI * (i + 0.5) / n; // 0..π from the table's right to its left
		double rx = -fz;
		double rz = fx; // right-hand vector of the facing
		double along = Math.sin(angle);
		double across = Math.cos(angle);
		return new double[] {RADIUS * (fx * along + rx * across), RADIUS * (fz * along + rz * across)};
	}
}
