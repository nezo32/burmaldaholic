package dev.nezo.burmaldaholic.games.poker.present;

/**
 * In-world card table geometry (animation/cards.md §1.4, §2.4, §3.4; task J-C10 {@code seatAnchor}): block-local
 * coordinates on the table top (x → right, z → toward the player standing at the front, y = 1 the top face), for the
 * Hold'em and UTH renderers and for anything that must sit over a seat (bot nameplates). Pure.
 *
 * <p>The front of the table (z = 1) is the side the block faces; the dealer side is z = 0.
 */
public final class TableWorld {
	/** Board / dealer / viewer card ("L-equivalent") and seat card sizes in blocks. */
	public static final float BOARD_W = 0.13f, BOARD_H = 0.18f, SEAT_W = 0.085f, SEAT_H = 0.115f;
	public static final float TOP = 1.0f;
	/** Card layers: 1.002 + 0.001 × index (no z-fighting). */
	public static final float LAYER = 0.001f;

	private TableWorld() {}

	/** Board slot {@code i} (0..4): centre x, z. */
	public static float[] board(int i, float z) {
		float pitch = BOARD_W + 0.02f;
		return new float[] {0.5f + (i - 2) * pitch, z};
	}

	/**
	 * Hold'em seat anchor: {@code seat} of {@code size} seats on an ellipse around the felt, seat 0 at the front
	 * (z ≈ 0.9), clockwise seen from above. Returns {x, z, yaw°} where yaw turns the cards toward the centre.
	 */
	public static float[] holdemSeat(int seat, int size) {
		int n = Math.max(2, size);
		double a = Math.PI / 2 + 2 * Math.PI * Math.floorMod(seat, n) / n; // z grows toward the front
		float x = (float) (0.5 + 0.40 * Math.cos(a));
		float z = (float) (0.5 + 0.40 * Math.sin(a));
		float yaw = (float) Math.toDegrees(a - Math.PI / 2);
		return new float[] {x, z, yaw};
	}

	/** Bet spot of a Hold'em seat: 35 % of the way to the centre. */
	public static float[] holdemBet(int seat, int size) {
		float[] s = holdemSeat(seat, size);
		return new float[] {s[0] + 0.35f * (0.5f - s[0]), s[1] + 0.35f * (0.5f - s[1])};
	}

	/**
	 * UTH seat anchor: up to 7 seats in an arc along the near rim (x 0.14 … 0.86), the dealer at the far edge. Returns
	 * {x, z, yaw°}.
	 */
	public static float[] uthSeat(int lane, int seats) {
		int n = Math.max(1, seats);
		float u = n == 1 ? 0.5f : lane / (float) (n - 1);
		float x = 0.14f + 0.72f * u;
		float z = 0.86f - 0.10f * (float) Math.abs(u - 0.5) * 2;
		float yaw = (u - 0.5f) * 50f;
		return new float[] {x, z, yaw};
	}

	/** The deck / shoe on the table (deal origin). */
	public static float[] deck(boolean uth) {
		return uth ? new float[] {0.86f, 0.16f} : new float[] {0.30f, 0.30f};
	}

	/** Deal arc: position along a straight path from {@code a} to {@code b} lifted by {@code lift} at the midpoint. */
	public static float[] arc(float ax, float az, float bx, float bz, double u, float lift) {
		double k = 1 - Math.pow(1 - Math.max(0, Math.min(1, u)), 3); // outCubic
		return new float[] {(float) (ax + (bx - ax) * k), (float) (az + (bz - az) * k), (float) (lift * Math.sin(Math.PI * k))};
	}
}
