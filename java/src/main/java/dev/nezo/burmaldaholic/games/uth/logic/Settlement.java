package dev.nezo.burmaldaholic.games.uth.logic;

/**
 * Settlement of one seat (GAME_DESIGN §21.1). A = Ante = Blind, P = Play bet (0 = none), T = Trips.
 *
 * <pre>
 * Seat result                     Play   Ante   Blind
 * wins, dealer qualifies          +P     +A     Blind paytable, else push
 * wins, dealer does not qualify   +P     push   Blind paytable, else push
 * loses, dealer qualifies         −P     −A     −A
 * loses, dealer does not qualify  −P     push   −A
 * tie                             push   push   push
 * folded                          —      −A     −A
 * Trips: +T × tripsPays[hand] for three of a kind or better, else −T, always (even after a fold).
 * </pre>
 */
public final class Settlement {
	public enum Outcome {
		WIN, LOSE, TIE, FOLDED
	}

	/**
	 * Per-bet nets of one seat. {@link #totalReturn()} = everything staked + net (what the bank pays back;
	 * 0 = all lost).
	 */
	public record Result(Outcome outcome, boolean dealerQualifies, int playerValue, int dealerValue, long ante, long play, long trips,
			long anteNet, long blindNet, long playNet, long tripsNet) {
		public long staked() {
			return 2 * ante + play + trips;
		}

		public long net() {
			return anteNet + blindNet + playNet + tripsNet;
		}

		public long totalReturn() {
			return staked() + net();
		}

		public PayHand hand() {
			return PayHand.of(playerValue);
		}

		/** The Blind paid a bonus (only on a win with a paying hand). */
		public boolean blindBonus() {
			return blindNet > 0;
		}

		public boolean tripsPaid() {
			return tripsNet > 0;
		}
	}

	private Settlement() {}

	/** Whether the dealer's best hand is one pair or better (board pairs count). */
	public static boolean qualifies(int dealerValue) {
		return UthCards.category(dealerValue) >= UthCards.PAIR;
	}

	public static Result settle(long ante, long play, long trips, boolean folded, int playerValue, int dealerValue, Paytables pays) {
		boolean q = qualifies(dealerValue);
		PayHand hand = PayHand.of(playerValue);
		long tripsNet = trips <= 0 ? 0 : (pays.tripsPay(hand) > 0 ? trips * pays.tripsPay(hand) : -trips);
		if (folded) {
			return new Result(Outcome.FOLDED, q, playerValue, dealerValue, ante, 0, trips, -ante, -ante, 0, tripsNet);
		}
		if (playerValue > dealerValue) {
			return new Result(Outcome.WIN, q, playerValue, dealerValue, ante, play, trips, q ? ante : 0, pays.blindWin(hand, ante), play,
				tripsNet);
		}
		if (playerValue < dealerValue) {
			return new Result(Outcome.LOSE, q, playerValue, dealerValue, ante, play, trips, q ? -ante : 0, -ante, -play, tripsNet);
		}
		return new Result(Outcome.TIE, q, playerValue, dealerValue, ante, play, trips, 0, 0, 0, tripsNet);
	}

	/** Convenience: evaluates hole + board and dealer + board. */
	public static Result settle(long ante, long play, long trips, boolean folded, int[] hole, int[] dealer, int[] board, Paytables pays) {
		return settle(ante, play, trips, folded, value(hole, board), value(dealer, board), pays);
	}

	/** Best 5 of the 2 hole + 5 board cards. */
	public static int value(int[] hole, int[] board) {
		int[] seven = new int[7];
		seven[0] = hole[0];
		seven[1] = hole[1];
		System.arraycopy(board, 0, seven, 2, 5);
		return UthCards.evaluate(seven, 7);
	}
}
