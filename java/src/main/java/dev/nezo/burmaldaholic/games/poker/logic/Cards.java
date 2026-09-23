package dev.nezo.burmaldaholic.games.poker.logic;

import java.util.Locale;

/**
 * Compact cards (same encoding as the Bedrock edition): a card is an int 0..51 = {@code rankIdx * 4 + suit},
 * rankIdx 0..12 = 2..A (rank value = rankIdx + 2), suit 0..3 = spades, hearts, diamonds, clubs.
 */
public final class Cards {
	public static final int DECK_SIZE = 52;
	public static final String[] SUIT_IDS = {"spades", "hearts", "diamonds", "clubs"};
	private static final String RANK_CHARS = "23456789TJQKA";
	private static final String SUIT_CHARS = "SHDC";

	private Cards() {}

	/** Rank value 2..14 (A = 14). */
	public static int rank(int card) {
		return (card >> 2) + 2;
	}

	/** Suit 0..3 (S H D C). */
	public static int suit(int card) {
		return card & 3;
	}

	public static int make(int rankValue, int suit) {
		if (rankValue < 2 || rankValue > 14 || suit < 0 || suit > 3) {
			throw new IllegalArgumentException("bad card " + rankValue + "/" + suit);
		}
		return (rankValue - 2) * 4 + suit;
	}

	public static boolean valid(int card) {
		return card >= 0 && card < DECK_SIZE;
	}

	/** Parses "As", "Td", "10h", "2C" (test helper). */
	public static int parse(String id) {
		String s = id.trim().toUpperCase(Locale.ROOT);
		if (s.length() < 2) {
			throw new IllegalArgumentException("bad card " + id);
		}
		String r = s.substring(0, s.length() - 1);
		if (r.equals("10")) {
			r = "T";
		}
		int ri = r.length() == 1 ? RANK_CHARS.indexOf(r.charAt(0)) : -1;
		int si = SUIT_CHARS.indexOf(s.charAt(s.length() - 1));
		if (ri < 0 || si < 0) {
			throw new IllegalArgumentException("bad card " + id);
		}
		return ri * 4 + si;
	}

	/** "As Kd 7h" → cards. */
	public static int[] parseAll(String list) {
		String t = list.trim();
		if (t.isEmpty()) {
			return new int[0];
		}
		String[] parts = t.split("\\s+");
		int[] out = new int[parts.length];
		for (int i = 0; i < parts.length; i++) {
			out[i] = parse(parts[i]);
		}
		return out;
	}

	/** Short id, e.g. "As" / "Td" (logs and tests only; the UI renders ranks from lang keys). */
	public static String id(int card) {
		return RANK_CHARS.charAt(card >> 2) + String.valueOf(Character.toLowerCase(SUIT_CHARS.charAt(card & 3)));
	}

	/** Fresh deck shuffled with Fisher-Yates (top of the deck = index 0). */
	public static int[] shuffledDeck(PokerRng rng) {
		int[] a = new int[DECK_SIZE];
		for (int i = 0; i < DECK_SIZE; i++) {
			a[i] = i;
		}
		for (int i = DECK_SIZE - 1; i > 0; i--) {
			int j = rng.nextInt(i + 1);
			int tmp = a[i];
			a[i] = a[j];
			a[j] = tmp;
		}
		return a;
	}
}
