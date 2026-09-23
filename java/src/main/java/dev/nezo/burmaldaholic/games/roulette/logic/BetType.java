package dev.nezo.burmaldaholic.games.roulette.logic;

import java.util.Locale;
import java.util.Optional;

/** Roulette bet types with their "X:1" payouts (GAME_DESIGN.md §9). */
public enum BetType {
	STRAIGHT(35, true),
	SPLIT(17, true),
	STREET(11, true),
	TRIO(11, true),
	CORNER(8, true),
	FIRST_FOUR(8, true),
	SIX_LINE(5, true),
	DOZEN(2, false),
	COLUMN(2, false),
	RED(1, false),
	BLACK(1, false),
	ODD(1, false),
	EVEN(1, false),
	LOW(1, false),
	HIGH(1, false);

	private final int payout;
	private final boolean inside;

	BetType(int payout, boolean inside) {
		this.payout = payout;
		this.inside = inside;
	}

	/** Winnings per chip ("X:1"); a win returns {@code stake × (payout + 1)}. */
	public int payout() {
		return payout;
	}

	public boolean inside() {
		return inside;
	}

	/** Red/black, odd/even, low/high (la partage, pawn stakes). */
	public boolean evenMoney() {
		return payout == 1;
	}

	/** Lower-case id used in lang keys and on the wire ({@code first_four}). */
	public String id() {
		return name().toLowerCase(Locale.ROOT);
	}

	public static Optional<BetType> byId(String id) {
		for (BetType t : values()) {
			if (t.id().equals(id)) {
				return Optional.of(t);
			}
		}
		return Optional.empty();
	}
}
