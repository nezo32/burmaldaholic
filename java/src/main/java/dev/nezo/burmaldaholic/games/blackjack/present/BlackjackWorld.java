package dev.nezo.burmaldaholic.games.blackjack.present;

import dev.nezo.burmaldaholic.core.ui.v2.board.BoardGeometry;
import dev.nezo.burmaldaholic.core.ui.v2.board.BoardGeometry.Felt;

/**
 * Blackjack / baccarat placement on the crescent felt of the board kit ({@link BoardGeometry}, docs/architecture/
 * ui-v2.md §7): block-local table coordinates (x right, z toward the players, the dealer along the far edge). Seats
 * sit on the kit's crescent arc, so the kit's plate anchors match. Pure; sizes in blocks.
 */
public final class BlackjackWorld {
	private BlackjackWorld() {}

	public static final Felt FELT = Felt.CRESCENT;
	/** Dealer cards (the kit's 1.5 × screen size) and seat cards (a little smaller: five seats share the arc). */
	public static final float DEALER_W = BoardGeometry.CARD_W, DEALER_H = BoardGeometry.CARD_H, SEAT_W = 0.17f, SEAT_H = 0.23f;
	/** Fan step of a seat's hand along the seat's own x axis. */
	public static final float SEAT_STEP = 0.06f;

	/** Dealer card {@code i} of {@code n}: {x, z}. */
	public static float[] dealer(int i, int n) {
		float[] d = BoardGeometry.dealerSlot(FELT, i, n);
		return new float[] {d[0], d[1]};
	}

	/** The shoe (deal origin) at the dealer's right. */
	public static float[] shoe() {
		return new float[] {FELT.x1() - 0.32f, FELT.z0() + BoardGeometry.RAIL + 0.18f};
	}

	/** Seat {@code seat} of {@code seats}: the kit's crescent spot {x, z, yaw°}. */
	public static float[] seat(int seat, int seats) {
		return BoardGeometry.crescentSeat(seat, seats);
	}

	/**
	 * Centre of card {@code k} (of {@code n}) of hand {@code h} (of {@code hands}) at a seat: hands side by side across
	 * the seat, each fanned along the seat's x axis, later cards a little toward the dealer. Returns {x, z}.
	 */
	public static float[] seatCard(float[] seat, int h, int hands, int k, int n) {
		float handPitch = SEAT_W + SEAT_STEP + 0.02f;
		float off = (h - (hands - 1) / 2f) * handPitch + (k - (n - 1) / 2f) * SEAT_STEP;
		float up = k * 0.025f;
		double a = Math.toRadians(seat[2]);
		// seat x axis = (cos yaw, sin yaw); toward the dealer = (sin yaw, −cos yaw)
		return new float[] {(float) (seat[0] + off * Math.cos(a) + up * Math.sin(a)), (float) (seat[1] + off * Math.sin(a) - up * Math.cos(a))};
	}

	/** The seat's bet stack: on the player's side of the cards. */
	public static float[] stack(float[] seat) {
		double a = Math.toRadians(seat[2]);
		float d = SEAT_H / 2 + 0.09f;
		return new float[] {(float) (seat[0] - d * Math.sin(a)), (float) (seat[1] + d * Math.cos(a))};
	}

	/** W-atlas face index ({@code (rank − 2) × 4 + suit}, ace high) of a game card code ({@code suit × 13 + rank − 1}); −1 = back. */
	public static int face(int code) {
		if (code < 0 || code >= 52) return -1;
		int rank = code % 13 + 1;
		int suit = code / 13;
		int high = rank == 1 ? 14 : rank;
		return (high - 2) * 4 + suit;
	}
}
