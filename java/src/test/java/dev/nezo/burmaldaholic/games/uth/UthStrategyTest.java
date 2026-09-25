package dev.nezo.burmaldaholic.games.uth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.nezo.burmaldaholic.games.uth.logic.Decision;
import dev.nezo.burmaldaholic.games.uth.logic.Paytables;
import dev.nezo.burmaldaholic.games.uth.logic.ReferenceStrategy;
import dev.nezo.burmaldaholic.games.uth.logic.SeatDecider;
import dev.nezo.burmaldaholic.games.uth.logic.Settlement;
import dev.nezo.burmaldaholic.games.uth.logic.UthCards;
import dev.nezo.burmaldaholic.games.uth.logic.UthRound;
import java.util.List;
import java.util.SplittableRandom;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

/**
 * Reference strategy R (GAME_DESIGN §21.3) and the Monte-Carlo house-edge check of §21.8: 2.27 % of the
 * Ante (±3.3 σ), ×4 frequency 37.7 %. Rounds: environment {@code UTH_MC_ROUNDS} (default 1.2 × 10⁶, the
 * band widens with the sample; the spec's full run is {@code UTH_MC_ROUNDS=25000000 ./gradlew test}).
 */
class UthStrategyTest {
	private static final Paytables P = Paytables.DEFAULT;

	private static int[] c(String s) {
		return UthCards.parseAll(s);
	}

	@Test
	void preflopRule() {
		assertTrue(ReferenceStrategy.preflopBet(c("3c 3d")));
		assertFalse(ReferenceStrategy.preflopBet(c("2c 2d")));
		assertTrue(ReferenceStrategy.preflopBet(c("Ac 2d")));
		assertTrue(ReferenceStrategy.preflopBet(c("Kc 2c")));
		assertFalse(ReferenceStrategy.preflopBet(c("Kc 4d")));
		assertTrue(ReferenceStrategy.preflopBet(c("Kc 5d")));
		assertTrue(ReferenceStrategy.preflopBet(c("Qc 6c")));
		assertFalse(ReferenceStrategy.preflopBet(c("Qc 5c")));
		assertFalse(ReferenceStrategy.preflopBet(c("Qc 7d")));
		assertTrue(ReferenceStrategy.preflopBet(c("Qc 8d")));
		assertTrue(ReferenceStrategy.preflopBet(c("Jc 8c")));
		assertFalse(ReferenceStrategy.preflopBet(c("Jc 9d")));
		assertTrue(ReferenceStrategy.preflopBet(c("Jc Td")));
		assertFalse(ReferenceStrategy.preflopBet(c("Tc 9c")));
	}

	@Test
	void flopRule() {
		assertTrue(ReferenceStrategy.flopBet(c("7c 3d"), c("7h Kd 2s")), "hole card pairs the board");
		assertFalse(ReferenceStrategy.flopBet(c("2c 2d"), c("9h Kd 5s")), "pocket 2s excluded");
		assertTrue(ReferenceStrategy.flopBet(c("4c 4d"), c("9h Kd 5s")), "pocket pair");
		assertFalse(ReferenceStrategy.flopBet(c("8c 3d"), c("9h 9d 5s")), "board pair only");
		assertTrue(ReferenceStrategy.flopBet(c("8c 3d"), c("9h 9d 9s")), "board trips");
		assertTrue(ReferenceStrategy.flopBet(c("Th 3d"), c("9h 5h 2h")), "four to a flush with the Ten (three on the board)");
		assertTrue(ReferenceStrategy.flopBet(c("Th 3h"), c("9h 5h 2c")), "four to a flush with the Ten");
		assertFalse(ReferenceStrategy.flopBet(c("8h 3h"), c("9h 5h 2c")), "four to a flush, hole cards below Ten");
		assertTrue(ReferenceStrategy.flopBet(c("6c 7d"), c("8h 9d Ts")), "made straight");
	}

	/** The rank-grouped river enumeration equals the plain 990-pair enumeration. */
	@Test
	void riverEnumerationMatchesBruteForce() {
		SplittableRandom rnd = new SplittableRandom(7);
		for (int t = 0; t < 300; t++) {
			int[] deck = UthCards.shuffledDeck(rnd::nextInt);
			int[] hole = {deck[0], deck[1]};
			int[] board = t % 3 == 0 ? flushBoard(deck, rnd) : new int[] {deck[2], deck[3], deck[4], deck[5], deck[6]};
			if (overlaps(hole, board)) {
				continue;
			}
			int pv = Settlement.value(hole, board);
			long[] fast = ReferenceStrategy.dealerOutcomes(pv, board, hole);
			long[] slow = new long[5];
			boolean[] seen = new boolean[52];
			for (int x : hole) {
				seen[x] = true;
			}
			for (int x : board) {
				seen[x] = true;
			}
			for (int a = 0; a < 52; a++) {
				for (int b = a + 1; b < 52; b++) {
					if (seen[a] || seen[b]) {
						continue;
					}
					int dv = Settlement.value(new int[] {a, b}, board);
					boolean q = Settlement.qualifies(dv);
					slow[pv > dv ? (q ? 0 : 1) : pv < dv ? (q ? 2 : 3) : 4]++;
				}
			}
			assertEquals(java.util.Arrays.toString(slow), java.util.Arrays.toString(fast), "case " + t);
			assertEquals(990, slow[0] + slow[1] + slow[2] + slow[3] + slow[4]);
		}
	}

	private static int[] flushBoard(int[] deck, SplittableRandom rnd) {
		int suit = rnd.nextInt(4);
		int[] b = new int[5];
		int n = 0;
		for (int card : deck) {
			if (n < 3 + rnd.nextInt(2) && UthCards.suit(card) == suit && card != deck[0] && card != deck[1]) {
				b[n++] = card;
			}
		}
		for (int card : deck) {
			if (n < 5 && card != deck[0] && card != deck[1] && UthCards.suit(card) != suit) {
				b[n++] = card;
			}
		}
		return b;
	}

	private static boolean overlaps(int[] a, int[] b) {
		for (int x : a) {
			for (int y : b) {
				if (x == y) {
					return true;
				}
			}
		}
		return false;
	}

	/** Result of a Monte-Carlo run: net in chips, counts. */
	record Stats(long rounds, double net, double netSq, long four, long two, long one, long fold, long wagered) {
		Stats plus(Stats o) {
			return new Stats(rounds + o.rounds, net + o.net, netSq + o.netSq, four + o.four, two + o.two, one + o.one, fold + o.fold,
				wagered + o.wagered);
		}
	}

	static Stats simulate(long seed, long rounds, long ante) {
		SplittableRandom rnd = new SplittableRandom(seed);
		SeatDecider r = new ReferenceStrategy(P);
		SeatDecider.Context ctx = new SeatDecider.Context(false, true, Long.MAX_VALUE / 4);
		UUID id = new UUID(0, 1);
		List<UthRound.Entry> entries = List.of(new UthRound.Entry(0, id, "", ante, 0));
		double net = 0, netSq = 0;
		long four = 0, two = 0, one = 0, fold = 0, wagered = 0;
		for (long i = 0; i < rounds; i++) {
			UthRound round = UthRound.deal(entries, UthCards.shuffledDeck(rnd::nextInt));
			UthRound.Seat s = round.seats().getFirst();
			while (!round.settled()) {
				if (round.pending(s)) {
					Decision d = r.decide(round, s, ctx);
					round.decide(s, d, false);
				}
				round.advance(P);
			}
			switch (s.playMultiple) {
				case 4 -> four++;
				case 2 -> two++;
				case 1 -> one++;
				default -> fold++;
			}
			long n = s.result.net();
			net += n;
			netSq += (double) n * n;
			wagered += s.result.staked();
		}
		return new Stats(rounds, net, netSq, four, two, one, fold, wagered);
	}

	@Test
	void monteCarloHouseEdgeOfStrategyR() {
		String env = System.getenv("UTH_MC_ROUNDS");
		long rounds = Long.getLong("uth.mc.rounds", env != null && env.matches("\\d+") ? Long.parseLong(env) : 1_200_000L);
		String seedEnv = System.getenv("UTH_MC_SEED");
		long seed = seedEnv != null && seedEnv.matches("\\d+") ? Long.parseLong(seedEnv) : 0x5EEDL;
		long ante = 2; // the 3:2 flush pays exactly
		int chunks = 8;
		Stats total = IntStream.range(0, chunks).parallel()
			.mapToObj(k -> simulate(new java.util.SplittableRandom(seed).split().nextLong() + 7919L * k, rounds / chunks, ante))
			.reduce(Stats::plus).orElseThrow();
		double n = total.rounds();
		double meanAntes = total.net() / n / ante;
		double sd = Math.sqrt(total.netSq() / n - Math.pow(total.net() / n, 2)) / ante;
		double se = sd / Math.sqrt(n);
		double he = -meanAntes;
		double band = 3.3 * se;
		System.out.printf("UTH MC: %d rounds, HE %.4f %% of the Ante (SE %.4f %%), sd %.3f Antes, x4 %.3f %%, x2 %.3f %%, x1 %.3f %%, fold %.3f %%, wagered %.3f Antes%n",
			total.rounds(), he * 100, se * 100, sd, 100.0 * total.four() / n, 100.0 * total.two() / n, 100.0 * total.one() / n,
			100.0 * total.fold() / n, total.wagered() / n / ante);
		assertTrue(Math.abs(he - 0.0227) <= Math.max(band, 0.0006) + 0.0006,
			"house edge " + he * 100 + " % not within 2.27 % ± " + (band + 0.0006) * 100 + " %");
		if (n >= 2.5e7) {
			assertTrue(he >= 0.0195 && he <= 0.0260, "spec band 1.95 % … 2.60 % for 2.5 × 10⁷ rounds");
		}
		double fourRate = total.four() / n;
		assertEquals(0.377, fourRate, 3.3 * Math.sqrt(0.377 * 0.623 / n) + 0.003, "x4 frequency");
		assertEquals(0.191, total.fold() / n, 3.3 * Math.sqrt(0.191 * 0.809 / n) + 0.003, "fold frequency");
		assertEquals(0.214, total.two() / n, 3.3 * Math.sqrt(0.214 * 0.786 / n) + 0.003, "x2 frequency");
		assertEquals(0.217, total.one() / n, 3.3 * Math.sqrt(0.217 * 0.783 / n) + 0.003, "x1 frequency");
		assertEquals(4.15, total.wagered() / n / ante, 0.02, "average total wagered in Antes");
	}

	@Test
	void safeDefaultsAreTheTimeoutRule() {
		UUID id = new UUID(0, 2);
		// hole 7c 2d; board gives the seat a straight (3 4 5 6 7)
		int[] deck = deck("7c 2d", "Kd Kc", "3s 4h 5d 6c Ah");
		UthRound r = UthRound.deal(List.of(new UthRound.Entry(0, id, "", 10, 0)), deck);
		UthRound.Seat s = r.seats().getFirst();
		assertEquals(Decision.CHECK, SeatDecider.SAFE_DEFAULT.decide(r, s, new SeatDecider.Context(true, true, 100)));
		r.decide(s, Decision.CHECK, true);
		r.advance(P);
		assertEquals(Decision.CHECK, SeatDecider.SAFE_DEFAULT.decide(r, s, new SeatDecider.Context(true, true, 100)));
		r.decide(s, Decision.CHECK, true);
		r.advance(P);
		assertEquals(Decision.BET_1X, SeatDecider.SAFE_DEFAULT.decide(r, s, new SeatDecider.Context(true, true, 100)), "straight auto-plays");
		assertEquals(Decision.FOLD, SeatDecider.SAFE_DEFAULT.decide(r, s, new SeatDecider.Context(true, false, 100)), "autoPlayMadeHands off");
		assertEquals(Decision.FOLD, SeatDecider.SAFE_DEFAULT.decide(r, s, new SeatDecider.Context(true, true, 9)), "cannot afford 1 × Ante");
	}

	/** A deck whose deal (one seat) gives these hole, dealer and board cards; the rest follows. */
	static int[] deck(String hole, String dealer, String board) {
		int[] h = c(hole);
		int[] d = c(dealer);
		int[] b = c(board);
		int[] order = {h[0], d[0], h[1], d[1], b[0], b[1], b[2], b[3], b[4]};
		int[] deck = new int[52];
		boolean[] used = new boolean[52];
		int k = 0;
		for (int x : order) {
			deck[k++] = x;
			used[x] = true;
		}
		for (int x = 0; x < 52; x++) {
			if (!used[x]) {
				deck[k++] = x;
			}
		}
		return deck;
	}
}
