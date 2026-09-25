package dev.nezo.burmaldaholic.independent;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.nezo.burmaldaholic.games.poker.logic.Cards;
import dev.nezo.burmaldaholic.games.poker.logic.Hand;
import dev.nezo.burmaldaholic.games.poker.logic.HandEvaluator;
import dev.nezo.burmaldaholic.games.poker.logic.PokerRng;
import dev.nezo.burmaldaholic.games.poker.logic.Pots;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.SplittableRandom;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;

/**
 * GAME_DESIGN.md §7: an independent 5-card ranking (compared to the dev evaluator on random 7-card
 * hands), side pots / ties / odd chips / rake re-computed from the spec text for random full hands.
 */
class PokerSpecTest {
	private static final Pots.RakeConfig RAKE = new Pots.RakeConfig(0.05, 3, true);

	// ---- independent evaluator ------------------------------------------------------------------

	/** Comparable key [category, r1..r5] of a 5-card hand (§7.2). */
	private static int[] key5(int[] c) {
		int[] ranks = new int[5];
		boolean flush = true;
		for (int i = 0; i < 5; i++) {
			ranks[i] = Cards.rank(c[i]);
			flush &= Cards.suit(c[i]) == Cards.suit(c[0]);
		}
		int[] count = new int[15];
		for (int r : ranks) {
			count[r]++;
		}
		// order ranks by (count desc, rank desc)
		Integer[] order = Arrays.stream(ranks).boxed().distinct().toArray(Integer[]::new);
		Arrays.sort(order, (a, b) -> count[b] != count[a] ? count[b] - count[a] : b - a);
		int[] sorted = new int[5];
		int k = 0;
		for (int r : order) {
			for (int j = 0; j < count[r]; j++) {
				sorted[k++] = r;
			}
		}
		int distinct = order.length;
		int straightHigh = 0;
		if (distinct == 5) {
			int[] asc = ranks.clone();
			Arrays.sort(asc);
			if (asc[4] - asc[0] == 4) {
				straightHigh = asc[4];
			} else if (asc[4] == 14 && asc[0] == 2 && asc[3] == 5) {
				straightHigh = 5; // wheel
			}
		}
		int cat;
		if (straightHigh > 0 && flush) {
			cat = 8;
		} else if (count[order[0]] == 4) {
			cat = 7;
		} else if (count[order[0]] == 3 && distinct == 2) {
			cat = 6;
		} else if (flush) {
			cat = 5;
		} else if (straightHigh > 0) {
			cat = 4;
		} else if (count[order[0]] == 3) {
			cat = 3;
		} else if (count[order[0]] == 2 && distinct == 3) {
			cat = 2;
		} else if (count[order[0]] == 2) {
			cat = 1;
		} else {
			cat = 0;
		}
		if (cat == 8 || cat == 4) {
			return new int[] {cat, straightHigh, 0, 0, 0, 0};
		}
		return new int[] {cat, sorted[0], sorted[1], sorted[2], sorted[3], sorted[4]};
	}

	private static int[] best7(int[] c) {
		int[] best = null;
		int n = c.length;
		for (int a = 0; a < n; a++) {
			for (int b = a + 1; b < n; b++) {
				int[] five = new int[5];
				int k = 0;
				for (int i = 0; i < n; i++) {
					if (i != a && i != b) {
						five[k++] = c[i];
					}
				}
				int[] key = key5(five);
				if (best == null || Arrays.compare(key, best) > 0) {
					best = key;
				}
			}
		}
		return best;
	}

	@Test
	void evaluatorAgreesWithIndependentRanking() {
		SplittableRandom rnd = new SplittableRandom(11);
		PokerRng rng = PokerRng.of(rnd);
		for (int i = 0; i < 60_000; i++) {
			int[] deck = Cards.shuffledDeck(rng);
			int[] h1 = Arrays.copyOfRange(deck, 0, 7);
			int[] h2 = new int[7];
			System.arraycopy(deck, 7, h2, 0, 2);
			System.arraycopy(h1, 2, h2, 2, 5); // shared board
			int mine = Integer.signum(Arrays.compare(best7(h1), best7(h2)));
			int dev = Integer.signum(Integer.compare(HandEvaluator.evaluate(h1), HandEvaluator.evaluate(h2)));
			assertEquals(mine, dev, () -> Cards.id(h1[0]) + " vs " + Arrays.toString(h2));
			assertEquals(best7(h1)[0], HandEvaluator.category(HandEvaluator.evaluate(h1)));
		}
	}

	@Test
	void specVectors() {
		assertEquals("royal_flush", HandEvaluator.handName(HandEvaluator.evaluate(Cards.parseAll("As Ks Qs Js Ts 2d 3c"))));
		int wheel = HandEvaluator.evaluate(Cards.parseAll("Ah 2d 3c 4s 5h Kd Kc"));
		assertEquals(HandEvaluator.STRAIGHT, HandEvaluator.category(wheel), "A-2-3-4-5 is a straight");
		assertTrue(wheel < HandEvaluator.evaluate(Cards.parseAll("2h 3d 4c 5s 6h Kd Kc")), "wheel is the lowest straight");
		assertEquals(HandEvaluator.evaluate(Cards.parseAll("Ah Kh Qd Jc 9s 2d 3c")), HandEvaluator.evaluate(Cards.parseAll("As Ks Qc Jd 9h 2c 3d")),
			"suits never break ties");
		assertTrue(HandEvaluator.evaluate(Cards.parseAll("Ah Ad Kc Qd 9s 2d 3c")) > HandEvaluator.evaluate(Cards.parseAll("As Ac Kh Qh 8s 2d 3c")),
			"kicker decides");
		// packed value contract: category << 20 | 5 ranks × 4 bits
		int v = HandEvaluator.evaluate(Cards.parseAll("Ah Ad Kc Qd 9s"));
		assertEquals(1 << 20 | 14 << 16 | 14 << 12 | 13 << 8 | 12 << 4 | 9, v);
	}

	// ---- pots ----------------------------------------------------------------------------------

	@Test
	void sidePotsFromSpecExample() {
		// A all-in 50, B all-in 120, C calls 200, D folded after putting 30
		long[] totals = {50, 120, 200, 30};
		boolean[] folded = {false, false, false, true};
		assertEquals(null, Pots.uncalledBet(new long[] {50, 120, 200, 200, 30}), "matched top bet");
		List<Pots.Pot> pots = Pots.buildPots(totals, folded);
		assertEquals(3, pots.size());
		assertEquals(50 * 3 + 30, pots.get(0).amount(), "main pot incl. the folded 30");
		assertEquals(List.of(0, 1, 2), pots.get(0).eligible());
		assertEquals(70 * 2, pots.get(1).amount());
		assertEquals(80, pots.get(2).amount());
		assertEquals(List.of(2), pots.get(2).eligible());
		Pots.Uncalled u = Pots.uncalledBet(totals);
		assertEquals(2, u.player());
		assertEquals(80, u.amount(), "C's uncalled 80 is returned before showdown");
	}

	@Test
	void rakeRules() {
		assertEquals(5, Pots.rakeFor(100, 2, true, 2, RAKE), "5 %");
		assertEquals(6, Pots.rakeFor(1000, 2, true, 2, RAKE), "capped at 3 BB");
		assertEquals(0, Pots.rakeFor(1000, 2, false, 2, RAKE), "no flop, no drop");
		assertEquals(0, Pots.rakeFor(1000, 1, true, 2, RAKE), "human vs bots only: not raked");
		assertArrayEquals(new long[] {34, 33, 33}, Pots.splitPot(100, 3), "odd chip to the first winner left of the button");
	}

	/** Random full hands: chips conserved, and every award equals the spec re-computation. */
	@Test
	void randomHandsConserveChipsAndAwardPerSpec() {
		SplittableRandom rnd = new SplittableRandom(5);
		PokerRng rng = PokerRng.of(rnd);
		int showdowns = 0, sidePotHands = 0;
		for (int h = 0; h < 20_000; h++) {
			int n = 2 + rnd.nextInt(5);
			List<Hand.Seed> seeds = new ArrayList<>();
			long before = 0;
			for (int i = 0; i < n; i++) {
				long stack = 1 + rnd.nextInt(rnd.nextBoolean() ? 30 : 400);
				seeds.add(new Hand.Seed("p" + i, true, stack));
				before += stack;
			}
			Hand hand = new Hand(seeds, rng, new Hand.Options(1, 2, rnd.nextInt(n), RAKE, null));
			int guard = 0;
			while (!hand.complete()) {
				assertTrue(++guard < 1000, "hand terminates");
				Hand.Legal l = hand.legal();
				int roll = rnd.nextInt(100);
				Hand.Action a;
				if (roll < 8) {
					a = Hand.Action.fold();
				} else if (roll < 60) {
					a = Hand.Action.call();
				} else if (roll < 75) {
					a = Hand.Action.check();
				} else if (roll < 92) {
					a = Hand.Action.raiseTo(l.minRaiseTo() + rnd.nextInt(20));
				} else {
					a = Hand.Action.allIn();
				}
				hand.apply(hand.coerce(a));
			}
			Hand.Result r = hand.result();
			long after = 0;
			for (Hand.Player p : hand.players()) {
				assertTrue(p.stack() >= 0);
				after += p.stack();
			}
			assertEquals(before, after + r.rake(), "chips conserved (rake is the only sink)");
			if (!r.uncontested()) {
				showdowns++;
				if (r.pots().size() > 1) {
					sidePotHands++;
				}
				assertArrayEquals(expectedWinnings(hand), r.won(), "hand " + h);
			}
		}
		assertTrue(showdowns > 3000 && sidePotHands > 300, "coverage: " + showdowns + " showdowns, " + sidePotHands + " with side pots");
	}

	/** §7.3 from the text: levels of non-folded contributions, pot per level, eligible, rake per pot, odd chip. */
	private static long[] expectedWinnings(Hand hand) {
		int n = hand.players().size();
		long[] c = new long[n];
		boolean[] folded = new boolean[n];
		int[][] key = new int[n][];
		for (int i = 0; i < n; i++) {
			Hand.Player p = hand.players().get(i);
			c[i] = p.total(); // uncalled already returned by the dev code; re-check below
			folded[i] = p.folded();
			if (!folded[i]) {
				int[] seven = new int[7];
				seven[0] = p.hole()[0];
				seven[1] = p.hole()[1];
				for (int k = 0; k < 5; k++) {
					seven[2 + k] = hand.board().get(k);
				}
				key[i] = best7(seven);
			}
		}
		// the uncalled rule: nobody may have more in than the second-largest contribution
		long[] sorted = c.clone();
		Arrays.sort(sorted);
		assertEquals(sorted[n - 1], sorted[n - 2], "uncalled excess returned");
		TreeSet<Long> levels = new TreeSet<>();
		for (int i = 0; i < n; i++) {
			if (!folded[i]) {
				levels.add(c[i]);
			}
		}
		long[] won = new long[n];
		long prev = 0;
		long grand = Arrays.stream(c).sum();
		long assigned = 0;
		for (long level : levels) {
			long amount = 0;
			int humans = 0;
			for (int i = 0; i < n; i++) {
				long part = Math.min(c[i], level) - Math.min(c[i], prev);
				amount += part;
				if (part > 0) {
					humans++;
				}
			}
			if (level == levels.last()) {
				amount = grand - assigned; // folded chips above the top level
			}
			assigned += amount;
			prev = level;
			List<Integer> eligible = new ArrayList<>();
			for (int i = 0; i < n; i++) {
				if (!folded[i] && c[i] >= level) {
					eligible.add(i);
				}
			}
			long rake = Pots.rakeFor(amount, humans, hand.sawFlop(), hand.bb(), RAKE);
			int[] best = null;
			for (int i : eligible) {
				if (best == null || Arrays.compare(key[i], best) > 0) {
					best = key[i];
				}
			}
			List<Integer> winners = new ArrayList<>();
			for (int k = 1; k <= n; k++) {
				int i = (hand.button() + k) % n; // clockwise from the seat left of the button
				if (eligible.contains(i) && Arrays.equals(key[i], best)) {
					winners.add(i);
				}
			}
			long net = amount - rake;
			for (int k = 0; k < winners.size(); k++) {
				won[winners.get(k)] += net / winners.size() + (k < net % winners.size() ? 1 : 0);
			}
		}
		return won;
	}
}
