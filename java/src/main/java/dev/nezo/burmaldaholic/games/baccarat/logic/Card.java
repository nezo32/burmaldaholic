package dev.nezo.burmaldaholic.games.baccarat.logic;

/**
 * A playing card. PURE. {@code rank} 1..13 (1 = ace, 11 = J, 12 = Q, 13 = K), {@code suit} 0..3
 * (spades, hearts, diamonds, clubs). {@link #code()} packs it into 0..51 for sync and persistence
 * (same packing as the blackjack module, so both screens agree on card codes).
 */
public record Card(int rank, int suit) {
	public static final int ACE = 1, JACK = 11, QUEEN = 12, KING = 13;
	public static final int SPADES = 0, HEARTS = 1, DIAMONDS = 2, CLUBS = 3;

	public Card {
		if (rank < 1 || rank > 13 || suit < 0 || suit > 3) {
			throw new IllegalArgumentException("bad card " + rank + "/" + suit);
		}
	}

	public static Card of(int rank, int suit) {
		return new Card(rank, suit);
	}

	public static Card fromCode(int code) {
		if (code < 0 || code >= 52) {
			throw new IllegalArgumentException("bad card code " + code);
		}
		return new Card(code % 13 + 1, code / 13);
	}

	public int code() {
		return suit * 13 + rank - 1;
	}

	/** Baccarat point value (GAME_DESIGN §20.1): A = 1, 2–9 face, 10/J/Q/K = 0. */
	public int points() {
		return rank >= 10 ? 0 : rank;
	}

	/** Burn count value (§20.1): A = 1, 2–9 face, 10/J/Q/K = 10. */
	public int burnValue() {
		return Math.min(10, rank);
	}

	public boolean isRed() {
		return suit == HEARTS || suit == DIAMONDS;
	}

	/** "A", "2".."10", "J", "Q", "K" (language-neutral symbols). */
	public String rankLabel() {
		return switch (rank) {
			case ACE -> "A";
			case JACK -> "J";
			case QUEEN -> "Q";
			case KING -> "K";
			default -> Integer.toString(rank);
		};
	}

	/** Unicode suit symbol (present in Minecraft's default font). */
	public String suitSymbol() {
		return switch (suit) {
			case SPADES -> "♠";
			case HEARTS -> "♥";
			case DIAMONDS -> "♦";
			default -> "♣";
		};
	}

	@Override
	public String toString() {
		return rankLabel() + suitSymbol();
	}
}
