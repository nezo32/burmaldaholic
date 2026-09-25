package dev.nezo.burmaldaholic.games.blackjack.logic;

/**
 * A playing card. PURE. {@code rank} 1..13 (1 = ace, 11 = J, 12 = Q, 13 = K), {@code suit} 0..3
 * (spades, hearts, diamonds, clubs). {@link #code()} packs it into 0..51 for network sync.
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

	/** Suit does not matter for the rules; handy for tests. */
	public static Card of(int rank) {
		return new Card(rank, SPADES);
	}

	public static Card fromCode(int code) {
		return new Card(code % 13 + 1, code / 13);
	}

	public int code() {
		return suit * 13 + rank - 1;
	}

	public boolean isAce() {
		return rank == ACE;
	}

	/** Blackjack value with the ace counted as 11 (GAME_DESIGN §6.2). */
	public int value() {
		return rank == ACE ? 11 : Math.min(10, rank);
	}

	public boolean isTenValue() {
		return rank >= 10;
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
}
