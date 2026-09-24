package dev.nezo.burmaldaholic.games.uth.logic;

/**
 * Reference strategy R of GAME_DESIGN §21.3 (tests and the Monte-Carlo house-edge check; a ready-made
 * policy for future bots — never shown to players as advice).
 *
 * <ul>
 *   <li>Preflop Bet ×4 with any pair 3-3+, any Ace, K-x suited, K-5+ offsuit, Q-6+ suited, Q-8+ offsuit,
 *       J-8+ suited, J-T+ offsuit; otherwise check.</li>
 *   <li>Flop Bet ×2 with a pair using a hole card (not pocket 2s), any made two pair or better on the 5
 *       known cards, or four to a flush including a hole card of that suit ranked 10+; otherwise check.</li>
 *   <li>River: over the dealer's 990 possible hole pairs, Bet ×1 iff the mean result of betting (win:
 *       {@code 1 + q + blindPay}; loss: {@code −(2 + q)}; tie 0, in Antes) beats the fold's −2.</li>
 * </ul>
 */
public final class ReferenceStrategy implements SeatDecider {
	private final Paytables pays;

	public ReferenceStrategy(Paytables pays) {
		this.pays = pays;
	}

	@Override
	public Decision decide(UthRound round, UthRound.Seat seat, Context ctx) {
		int[] board = round.visibleBoardCards();
		Decision d = switch (round.street()) {
			case PREFLOP -> preflopBet(seat.hole) ? Decision.BET_4X : Decision.CHECK;
			case FLOP -> flopBet(seat.hole, board) ? Decision.BET_2X : Decision.CHECK;
			case RIVER -> riverBet(seat.hole, board, pays) ? Decision.BET_1X : Decision.FOLD;
			case SHOWDOWN -> Decision.CHECK;
		};
		if (d.isBet() && ctx.balance() < (long) d.multiple() * seat.ante) {
			return round.street() == UthRound.Street.RIVER ? Decision.FOLD : Decision.CHECK;
		}
		return d;
	}

	public static boolean preflopBet(int[] hole) {
		int a = UthCards.rank(hole[0]);
		int b = UthCards.rank(hole[1]);
		int hi = Math.max(a, b);
		int lo = Math.min(a, b);
		boolean suited = UthCards.suit(hole[0]) == UthCards.suit(hole[1]);
		if (a == b) {
			return a >= 3;
		}
		return switch (hi) {
			case 14 -> true;
			case 13 -> suited || lo >= 5;
			case 12 -> suited ? lo >= 6 : lo >= 8;
			case 11 -> suited ? lo >= 8 : lo >= 10;
			default -> false;
		};
	}

	/** {@code board} = the 3 flop cards. */
	public static boolean flopBet(int[] hole, int[] board) {
		int h0 = UthCards.rank(hole[0]);
		int h1 = UthCards.rank(hole[1]);
		// a pair that uses at least one hole card (pocket 2s excluded)
		if (h0 == h1) {
			if (h0 > 2) {
				return true;
			}
		} else {
			for (int c : board) {
				int r = UthCards.rank(c);
				if (r == h0 || r == h1) {
					return true;
				}
			}
		}
		int[] five = {hole[0], hole[1], board[0], board[1], board[2]};
		if (UthCards.category(UthCards.evaluate(five, 5)) >= 2) {
			return true; // two pair or better on the 5 known cards
		}
		for (int suit = 0; suit < 4; suit++) {
			int n = 0;
			for (int c : five) {
				if (UthCards.suit(c) == suit) {
					n++;
				}
			}
			if (n == 4) {
				for (int c : hole) {
					if (UthCards.suit(c) == suit && UthCards.rank(c) >= 10) {
						return true;
					}
				}
			}
		}
		return false;
	}

	/** River rule: exact mean over the dealer's possible hole pairs. */
	public static boolean riverBet(int[] hole, int[] board, Paytables pays) {
		return riverBetEv(hole, board, pays) > -2.0;
	}

	/**
	 * Mean result (in Antes) of Bet ×1 at the river over all C(45,2) = 990 dealer hole pairs. Unseen cards
	 * that cannot change the dealer's hand value (suits that cannot make a flush on this board) are grouped
	 * by rank, so at most a few hundred dealer hands are evaluated.
	 */
	public static double riverBetEv(int[] hole, int[] board, Paytables pays) {
		int pv = Settlement.value(hole, board);
		double blindPay = pays.blindPay(PayHand.of(pv));
		long[] c = dealerOutcomes(pv, board, hole);
		// c = {win & dealer qualifies, win & not, lose & qualifies, lose & not, tie}
		double sum = c[0] * (2 + blindPay) + c[1] * (1 + blindPay) - c[2] * 3.0 - c[3] * 2.0;
		long total = c[0] + c[1] + c[2] + c[3] + c[4];
		return sum / total;
	}

	/**
	 * Weighted counts of the dealer's possible hole pairs given the player's final value {@code pv}:
	 * {win & q, win & !q, lose & q, lose & !q, tie} (sums to 990).
	 */
	public static long[] dealerOutcomes(int pv, int[] board, int[] hole) {
		boolean[] seen = new boolean[52];
		for (int c : board) {
			seen[c] = true;
		}
		for (int c : hole) {
			seen[c] = true;
		}
		int[] suitCount = new int[4];
		for (int c : board) {
			suitCount[UthCards.suit(c)]++;
		}
		int fs = -1;
		for (int s = 0; s < 4; s++) {
			if (suitCount[s] >= 3) {
				fs = s;
			}
		}
		int[] special = new int[13];
		int ns = 0;
		int[] cnt = new int[15];
		int[][] rep = new int[15][2];
		for (int card = 0; card < 52; card++) {
			if (seen[card]) {
				continue;
			}
			if (UthCards.suit(card) == fs) {
				special[ns++] = card;
			} else {
				int r = UthCards.rank(card);
				if (cnt[r] < 2) {
					rep[r][cnt[r]] = card;
				}
				cnt[r]++;
			}
		}
		long[] out = new long[5];
		int[] seven = new int[7];
		System.arraycopy(board, 0, seven, 2, 5);
		// special × special
		for (int i = 0; i < ns; i++) {
			for (int j = i + 1; j < ns; j++) {
				tally(out, pv, seven, special[i], special[j], 1);
			}
		}
		// special × rank group
		for (int i = 0; i < ns; i++) {
			for (int r = 2; r <= 14; r++) {
				if (cnt[r] > 0) {
					tally(out, pv, seven, special[i], rep[r][0], cnt[r]);
				}
			}
		}
		// rank group × rank group
		for (int r = 2; r <= 14; r++) {
			if (cnt[r] >= 2) {
				tally(out, pv, seven, rep[r][0], rep[r][1], (long) cnt[r] * (cnt[r] - 1) / 2);
			}
			for (int q = r + 1; q <= 14; q++) {
				if (cnt[r] > 0 && cnt[q] > 0) {
					tally(out, pv, seven, rep[r][0], rep[q][0], (long) cnt[r] * cnt[q]);
				}
			}
		}
		return out;
	}

	private static void tally(long[] out, int pv, int[] seven, int d0, int d1, long weight) {
		seven[0] = d0;
		seven[1] = d1;
		int dv = UthCards.evaluate(seven, 7);
		boolean q = Settlement.qualifies(dv);
		if (pv > dv) {
			out[q ? 0 : 1] += weight;
		} else if (pv < dv) {
			out[q ? 2 : 3] += weight;
		} else {
			out[4] += weight;
		}
	}
}
