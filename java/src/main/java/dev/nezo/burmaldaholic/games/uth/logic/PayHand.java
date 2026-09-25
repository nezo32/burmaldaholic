package dev.nezo.burmaldaholic.games.uth.logic;

/**
 * The hands the Blind and Trips paytables know (GAME_DESIGN §21.1), strongest first. {@link #key} is
 * the map key in {@code uth.blindPays} / {@code uth.tripsPays}; {@link #handName} the poker lang suffix.
 */
public enum PayHand {
	ROYAL("royal", "royal_flush"),
	STRAIGHT_FLUSH("straightFlush", "straight_flush"),
	QUADS("quads", "four_of_a_kind"),
	FULL_HOUSE("fullHouse", "full_house"),
	FLUSH("flush", "flush"),
	STRAIGHT("straight", "straight"),
	TRIPS("trips", "three_of_a_kind"),
	/** Anything lower than three of a kind. */
	NONE("", "");

	private final String key;
	private final String handName;

	PayHand(String key, String handName) {
		this.key = key;
		this.handName = handName;
	}

	public String key() {
		return key;
	}

	public String handName() {
		return handName;
	}

	/** Pay class of an evaluated 5-of-7 value. Royal flush = straight flush to the ace. */
	public static PayHand of(int value) {
		return switch (UthCards.category(value)) {
			case 8 -> ((value >>> 16) & 0xf) == 14 ? ROYAL : STRAIGHT_FLUSH;
			case 7 -> QUADS;
			case 6 -> FULL_HOUSE;
			case 5 -> FLUSH;
			case 4 -> STRAIGHT;
			case 3 -> TRIPS;
			default -> NONE;
		};
	}
}
