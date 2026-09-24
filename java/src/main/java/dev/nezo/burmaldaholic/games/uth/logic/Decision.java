package dev.nezo.burmaldaholic.games.uth.logic;

import java.util.Locale;
import org.jspecify.annotations.Nullable;

/** A seat's decision on one street (GAME_DESIGN §21.1). {@link #multiple} = Play bet in Antes (0 = none). */
public enum Decision {
	CHECK(0),
	BET_4X(4),
	BET_3X(3),
	BET_2X(2),
	BET_1X(1),
	FOLD(0);

	private final int multiple;

	Decision(int multiple) {
		this.multiple = multiple;
	}

	public int multiple() {
		return multiple;
	}

	public boolean isBet() {
		return multiple > 0;
	}

	/** Action / lang id: {@code check, bet_4x, bet_3x, bet_2x, bet_1x, fold}. */
	public String id() {
		return name().toLowerCase(Locale.ROOT);
	}

	public static @Nullable Decision byId(String id) {
		for (Decision d : values()) {
			if (d.id().equals(id)) {
				return d;
			}
		}
		return null;
	}
}
