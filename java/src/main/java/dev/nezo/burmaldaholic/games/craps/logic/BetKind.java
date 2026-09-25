package dev.nezo.burmaldaholic.games.craps.logic;

import java.util.Locale;
import org.jspecify.annotations.Nullable;

/** Flat (contract) bets of the craps layout (GAME_DESIGN §10.1). Pure Java. */
public enum BetKind {
	PASS, DONT_PASS, COME, DONT_COME, FIELD;

	/** Wire / lang id: {@code pass}, {@code dont_pass}, ... ({@code gui.burmaldaholic.craps.<id>}). */
	public String id() {
		return name().toLowerCase(Locale.ROOT);
	}

	public static @Nullable BetKind byId(String id) {
		for (BetKind k : values()) {
			if (k.id().equals(id)) {
				return k;
			}
		}
		return null;
	}

	/** Pass / Don't Pass. */
	public boolean isLine() {
		return this == PASS || this == DONT_PASS;
	}

	/** Come / Don't Come (travel to their own point). */
	public boolean isCome() {
		return this == COME || this == DONT_COME;
	}

	/** Side of the odds bet behind this flat bet, or null (Field takes no odds). */
	public @Nullable OddsSide oddsSide() {
		return switch (this) {
			case PASS, COME -> OddsSide.TAKE;
			case DONT_PASS, DONT_COME -> OddsSide.LAY;
			case FIELD -> null;
		};
	}
}
