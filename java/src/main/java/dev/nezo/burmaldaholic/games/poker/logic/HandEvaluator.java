package dev.nezo.burmaldaholic.games.poker.logic;

/**
 * Texas Hold'em hand evaluator (GAME_DESIGN.md §7.2), same contract as the Bedrock edition:
 * {@code evaluate(cards) -> int}, higher is better, {@code category << 20 | ranks packed 4 bits × 5}
 * (most significant rank first). Ranks 2..14; the ace of a wheel (A-2-3-4-5) is packed as 1. Suits
 * never break ties. Accepts 5, 6 or 7 cards and returns the value of the best 5-card hand.
 * Allocation-free (bots run it thousands of times per decision).
 */
public final class HandEvaluator {
	public static final int HIGH_CARD = 0, PAIR = 1, TWO_PAIR = 2, THREE_OF_A_KIND = 3, STRAIGHT = 4, FLUSH = 5,
		FULL_HOUSE = 6, FOUR_OF_A_KIND = 7, STRAIGHT_FLUSH = 8;
	/** Lang suffixes ({@code gui.burmaldaholic.poker.hand.<name>}) per category. */
	public static final String[] CATEGORY_NAMES = {"high_card", "pair", "two_pair", "three_of_a_kind", "straight", "flush",
		"full_house", "four_of_a_kind", "straight_flush"};

	private HandEvaluator() {}

	/** Highest straight in a rank mask (bit r = rank r present, ace mirrored to bit 1), 0 if none. */
	private static int straightHigh(int mask) {
		for (int h = 14; h >= 5; h--) {
			if (((mask >> (h - 4)) & 0x1f) == 0x1f) {
				return h;
			}
		}
		return 0;
	}

	private static int straightValue(int cat, int high) {
		int v = cat;
		for (int i = 0; i < 5; i++) {
			int r = high - i;
			v = v << 4 | (r == 1 ? 1 : r);
		}
		return v;
	}

	/** Appends the top {@code n} ranks of {@code mask} (bits 2..14) to the packed value {@code v}. */
	private static int appendTop(int v, int mask, int n) {
		int k = 0;
		for (int r = 14; r >= 2 && k < n; r--) {
			if ((mask & (1 << r)) != 0) {
				v = v << 4 | r;
				k++;
			}
		}
		for (; k < n; k++) {
			v <<= 4;
		}
		return v;
	}

	public static int evaluate(int... cards) {
		return evaluate(cards, cards.length);
	}

	/** Evaluates the first {@code n} (5..7) entries of {@code cards}. */
	public static int evaluate(int[] cards, int n) {
		if (n < 5 || n > 7) {
			throw new IllegalArgumentException("evaluate needs 5-7 cards, got " + n);
		}
		long counts = 0; // 4 bits per rank value
		int s0 = 0, s1 = 0, s2 = 0, s3 = 0;
		int c0 = 0, c1 = 0, c2 = 0, c3 = 0;
		int mask = 0;
		for (int i = 0; i < n; i++) {
			int c = cards[i];
			int r = (c >> 2) + 2;
			counts += 1L << (r * 4);
			int bit = 1 << r;
			mask |= bit;
			switch (c & 3) {
				case 0 -> { s0 |= bit; c0++; }
				case 1 -> { s1 |= bit; c1++; }
				case 2 -> { s2 |= bit; c2++; }
				default -> { s3 |= bit; c3++; }
			}
		}
		int flushMask = c0 >= 5 ? s0 : c1 >= 5 ? s1 : c2 >= 5 ? s2 : c3 >= 5 ? s3 : 0;
		if (flushMask != 0) {
			int sf = straightHigh((flushMask & (1 << 14)) != 0 ? flushMask | 2 : flushMask);
			if (sf != 0) {
				return straightValue(STRAIGHT_FLUSH, sf);
			}
		}
		int quad = 0, trip1 = 0, trip2 = 0, pair1 = 0, pair2 = 0, pairs = 0;
		for (int r = 14; r >= 2; r--) {
			int cnt = (int) ((counts >>> (r * 4)) & 0xf);
			if (cnt == 4) {
				quad = r;
			} else if (cnt == 3) {
				if (trip1 == 0) {
					trip1 = r;
				} else if (trip2 == 0) {
					trip2 = r;
				}
			} else if (cnt == 2) {
				pairs++;
				if (pair1 == 0) {
					pair1 = r;
				} else if (pair2 == 0) {
					pair2 = r;
				}
			}
		}
		if (quad != 0) {
			return appendTop(((((FOUR_OF_A_KIND << 4 | quad) << 4 | quad) << 4 | quad) << 4 | quad), mask & ~(1 << quad), 1);
		}
		if (trip1 != 0 && (trip2 != 0 || pair1 != 0)) {
			int p = Math.max(trip2, pair1);
			return ((((FULL_HOUSE << 4 | trip1) << 4 | trip1) << 4 | trip1) << 4 | p) << 4 | p;
		}
		if (flushMask != 0) {
			return appendTop(FLUSH, flushMask, 5);
		}
		int st = straightHigh((mask & (1 << 14)) != 0 ? mask | 2 : mask);
		if (st != 0) {
			return straightValue(STRAIGHT, st);
		}
		if (trip1 != 0) {
			return appendTop(((THREE_OF_A_KIND << 4 | trip1) << 4 | trip1) << 4 | trip1, mask & ~(1 << trip1), 2);
		}
		if (pairs >= 2) {
			return appendTop((((TWO_PAIR << 4 | pair1) << 4 | pair1) << 4 | pair2) << 4 | pair2, mask & ~(1 << pair1) & ~(1 << pair2), 1);
		}
		if (pairs == 1) {
			return appendTop((PAIR << 4 | pair1) << 4 | pair1, mask & ~(1 << pair1), 3);
		}
		return appendTop(HIGH_CARD, mask, 5);
	}

	/** Category 0..8 of an evaluated value. */
	public static int category(int value) {
		return value >>> 20;
	}

	/** The 5 packed ranks (most significant first). */
	public static int[] ranks(int value) {
		int[] out = new int[5];
		for (int i = 0; i < 5; i++) {
			out[i] = (value >>> ((4 - i) * 4)) & 0xf;
		}
		return out;
	}

	/** Lang suffix of an evaluated value; a straight flush to the ace is a royal flush. */
	public static String handName(int value) {
		int cat = category(value);
		if (cat == STRAIGHT_FLUSH && ranks(value)[0] == 14) {
			return "royal_flush";
		}
		return CATEGORY_NAMES[cat];
	}
}
