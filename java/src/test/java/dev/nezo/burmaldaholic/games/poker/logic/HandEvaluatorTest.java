package dev.nezo.burmaldaholic.games.poker.logic;

import static dev.nezo.burmaldaholic.games.poker.logic.HandEvaluator.category;
import static dev.nezo.burmaldaholic.games.poker.logic.HandEvaluator.handName;
import static dev.nezo.burmaldaholic.games.poker.logic.HandEvaluator.ranks;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;
import java.util.SplittableRandom;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Evaluator vectors shared with the Bedrock edition + exhaustive distribution checks. */
class HandEvaluatorTest {
	private static int ev(String cards) {
		return HandEvaluator.evaluate(Cards.parseAll(cards));
	}

	@ParameterizedTest
	@CsvSource({
		"As Ks Qs Js Ts 2d 3c, royal_flush",
		"9h 8h 7h 6h 5h Ac Ad, straight_flush",
		"Ah 2h 3h 4h 5h Kd Kc, straight_flush",
		"7s 7h 7d 7c Kd 2c 3h, four_of_a_kind",
		"Qs Qh Qd 9c 9d 2c 3h, full_house",
		"2s 8s Js Ks 4s Ad Ac, flush",
		"Ts 9h 8d 7c 6s 2d 2c, straight",
		"As 2h 3d 4c 5s Kd Qc, straight",
		"8s 8h 8d Kc 2s 4d 6c, three_of_a_kind",
		"Js Jh 4d 4c As 7d 2c, two_pair",
		"Ts Th 4d 8c As 7d 2c, pair",
		"As Jh 8d 6c 4s 3d 2c, high_card",
	})
	void categories(String cards, String name) {
		assertEquals(name, handName(ev(cards)));
	}

	@Test
	void categoryOrderIsStrictlyIncreasing() {
		String[] order = {"As Jh 8d 6c 4s", "Ts Th 4d 8c As", "Js Jh 4d 4c As", "8s 8h 8d Kc 2s", "Ts 9h 8d 7c 6s", "2s 8s Js Ks 4s",
			"Qs Qh Qd 9c 9d", "7s 7h 7d 7c Kd", "9h 8h 7h 6h 5h", "As Ks Qs Js Ts"};
		for (int i = 1; i < order.length; i++) {
			assertTrue(ev(order[i]) > ev(order[i - 1]), order[i]);
		}
	}

	@Test
	void packingContract() {
		int v = ev("Ks Kh 9d 9c 4s");
		assertEquals(HandEvaluator.TWO_PAIR << 20 | 13 << 16 | 13 << 12 | 9 << 8 | 9 << 4 | 4, v);
		assertArrayEquals(new int[] {13, 13, 9, 9, 4}, ranks(v));
	}

	@Test
	void wheelIsTheLowestStraight() {
		int wheel = ev("As 2h 3d 4c 5s");
		assertArrayEquals(new int[] {5, 4, 3, 2, 1}, ranks(wheel));
		assertTrue(wheel < ev("2s 3h 4d 5c 6s"));
		assertTrue(ev("Ts Jh Qd Kc As") > ev("9s Th Jd Qc Ks"));
		assertTrue(ev("Ad 2d 3d 4d 5d") < ev("2c 3c 4c 5c 6c"));
		assertEquals("straight_flush", handName(ev("Ad 2d 3d 4d 5d")));
		assertEquals(HandEvaluator.HIGH_CARD, category(ev("Qs Kh Ad 2c 3s")), "no wrap-around straights");
	}

	@Test
	void rejectsWrongCardCounts() {
		assertThrows(IllegalArgumentException.class, () -> ev("As Ks Qs Js"));
		assertThrows(IllegalArgumentException.class, () -> ev("As Ks Qs Js Ts 9s 8s 7s"));
	}

	@Test
	void bestFiveOfSeven() {
		assertEquals(HandEvaluator.STRAIGHT_FLUSH, category(ev("5s 6s 7s 8s 9s As Ks")));
		assertEquals(HandEvaluator.FLUSH, category(ev("2h 6h 7h 8h 9h Td Js")));
		int twoTrips = ev("9s 9h 9d 4c 4s 4d Ac");
		assertEquals(HandEvaluator.FULL_HOUSE, category(twoTrips));
		assertArrayEquals(new int[] {9, 9, 9, 4, 4}, ranks(twoTrips));
		assertArrayEquals(new int[] {5, 5, 5, 13, 13}, ranks(ev("5s 5h 5d Kc Ks 2d 2c")));
		assertArrayEquals(new int[] {13, 13, 7, 7, 12}, ranks(ev("Ks Kh 7d 7c 3s 3d Qc")), "three pairs: counterfeit");
		assertArrayEquals(new int[] {8, 8, 8, 8, 11}, ranks(ev("8s 8h 8d 8c Js Jd 2c")));
		assertArrayEquals(new int[] {14, 12, 11, 9, 7}, ranks(ev("Ah 3h 7h 9h Jh Qh 2c")));
		assertArrayEquals(new int[] {9, 8, 7, 6, 5}, ranks(ev("4s 5h 6d 7c 8s 9d 2c")));
		assertArrayEquals(new int[] {6, 5, 4, 3, 2}, ranks(ev("As 2h 3d 4c 5s 6d Kc")));
	}

	@Test
	void kickersAndTies() {
		assertTrue(ev("As Ah Kd 9c 5s 3d 2c") > ev("Ad Ac Qd Jc 9s 3h 2d"));
		assertTrue(ev("As Ah Kd 9c 6s 3d 2c") > ev("Ad Ac Kh 9d 5s 3h 2d"));
		assertEquals(ev("Ks Kh 7d 7c Qs 3d 2c"), ev("Kd Kc 7s 7h Qd 4d 2d"), "two pair: one kicker plays");
		assertTrue(ev("As Kh 9d 7c 4s") > ev("Ad Kc 9h 7s 3d"));
		assertTrue(ev("8s 8h 8d Ac 2s") > ev("8c 8h 8d Kc Qs"));
		assertTrue(ev("3s 3h 3d 2c 2s") > ev("2d 2h 2c As Ad"));
		assertEquals(ev("As Kh Qd Jc 9s"), ev("Ah Ks Qc Jd 9h"), "suits never break ties");
		assertEquals(ev("2c 3d Ts Js Qd Kc Ah"), ev("4h 5h Ts Js Qd Kc Ah"), "board plays");
		assertEquals(ev("Ts 9h 8d 7c 6s Ad Kc"), ev("Th 9d 8s 7h 6d 2c 3c"));
		assertTrue(ev("Ks Js 8s 6s 2s") > ev("Kh Jh 8h 5h 4h"));
	}

	/** All 2 598 960 five-card hands: the textbook category counts and the 7 462 distinct values. */
	@Test
	void exhaustiveFiveCardDistribution() {
		long[] counts = new long[10];
		Set<Integer> distinct = new HashSet<>();
		int[] h = new int[5];
		for (int a = 0; a < 52; a++) {
			for (int b = a + 1; b < 52; b++) {
				for (int c = b + 1; c < 52; c++) {
					for (int d = c + 1; d < 52; d++) {
						for (int e = d + 1; e < 52; e++) {
							h[0] = a;
							h[1] = b;
							h[2] = c;
							h[3] = d;
							h[4] = e;
							int v = HandEvaluator.evaluate(h, 5);
							distinct.add(v);
							int k = category(v);
							counts[k == HandEvaluator.STRAIGHT_FLUSH && ranks(v)[0] == 14 ? 9 : k]++;
						}
					}
				}
			}
		}
		assertArrayEquals(new long[] {1302540, 1098240, 123552, 54912, 10200, 5108, 3744, 624, 36, 4}, counts);
		assertEquals(7462, distinct.size());
	}

	/** All 133 784 560 seven-card hands: the known best-hand category counts. */
	@Test
	void exhaustiveSevenCardDistribution() {
		long[] counts = new long[9];
		int[] h = new int[7];
		for (int a = 0; a < 52; a++) {
			h[0] = a;
			for (int b = a + 1; b < 52; b++) {
				h[1] = b;
				for (int c = b + 1; c < 52; c++) {
					h[2] = c;
					for (int d = c + 1; d < 52; d++) {
						h[3] = d;
						for (int e = d + 1; e < 52; e++) {
							h[4] = e;
							for (int f = e + 1; f < 52; f++) {
								h[5] = f;
								for (int g = f + 1; g < 52; g++) {
									h[6] = g;
									counts[HandEvaluator.evaluate(h, 7) >>> 20]++;
								}
							}
						}
					}
				}
			}
		}
		assertArrayEquals(new long[] {23294460, 58627800, 31433400, 6461620, 6180020, 4047644, 3473184, 224848, 41584}, counts);
	}

	/** The 7-card fast path equals the best of the 21 five-card subsets (random hands). */
	@Test
	void sevenCardEqualsBestFiveSubset() {
		SplittableRandom random = new SplittableRandom(7);
		PokerRng rng = PokerRng.of(random);
		int[] five = new int[5];
		for (int n = 0; n < 200_000; n++) {
			int[] deck = Cards.shuffledDeck(rng);
			int size = 5 + random.nextInt(3);
			int best = 0;
			for (int mask = 0; mask < 1 << size; mask++) {
				if (Integer.bitCount(mask) != 5) {
					continue;
				}
				int k = 0;
				for (int i = 0; i < size; i++) {
					if ((mask & 1 << i) != 0) {
						five[k++] = deck[i];
					}
				}
				best = Math.max(best, HandEvaluator.evaluate(five, 5));
			}
			assertEquals(best, HandEvaluator.evaluate(deck, size));
		}
	}

	@Test
	void cardEncoding() {
		assertEquals(0, Cards.parse("2s"));
		assertEquals(51, Cards.parse("Ac"));
		assertEquals(Cards.parse("Td"), Cards.parse("10d"));
		assertEquals("Td", Cards.id(Cards.parse("td")));
		assertEquals(14, Cards.rank(Cards.make(14, 2)));
		assertEquals(2, Cards.suit(Cards.make(14, 2)));
		int[] deck = Cards.shuffledDeck(PokerRng.of(new SplittableRandom(1)));
		boolean[] seen = new boolean[52];
		for (int c : deck) {
			assertTrue(Cards.valid(c) && !seen[c]);
			seen[c] = true;
		}
	}
}
