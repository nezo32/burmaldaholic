package dev.nezo.burmaldaholic.games.baccarat.logic;

import java.util.Optional;

/** The five Punto Banco betting boxes (GAME_DESIGN §20.1). PURE. */
public enum BetKind {
	PLAYER("player", false),
	BANKER("banker", false),
	TIE("tie", true),
	PLAYER_PAIR("player_pair", true),
	BANKER_PAIR("banker_pair", true);

	private final String id;
	private final boolean side;

	BetKind(String id, boolean side) {
		this.id = id;
		this.side = side;
	}

	/** Network / lang id ({@code gui.burmaldaholic.baccarat.<id>}). */
	public String id() {
		return id;
	}

	/** Tie and pairs: capped at {@code max × sideMaxFraction} each (§20.4). */
	public boolean side() {
		return side;
	}

	public boolean pair() {
		return this == PLAYER_PAIR || this == BANKER_PAIR;
	}

	public static Optional<BetKind> byId(String id) {
		for (BetKind k : values()) {
			if (k.id.equals(id)) {
				return Optional.of(k);
			}
		}
		return Optional.empty();
	}
}
