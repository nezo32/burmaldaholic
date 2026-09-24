package dev.nezo.burmaldaholic.core.pvp.logic;

/** PvP match lifecycle (PVP.md §3.6). Only LOBBY, DRAWN and SETTLED are persisted. */
public enum MatchState {
	/** Duel invite pending; nothing escrowed; not saved. */
	INVITED,
	/** Lobby open; entries escrowed; saved → refunded on load. */
	LOBBY,
	/** Transient: rules re-checked, tape being drawn. */
	STARTING,
	/** Tape drawn and saved BEFORE any reveal; revealing; settle from the tape on load/stop/casino off. */
	DRAWN,
	/** Paid out; rematch window; kept {@code pvp.historyTicks}. */
	SETTLED,
	/** Lobby cancelled and refunded, or invite declined / expired / withdrawn. */
	CANCELLED,
	CLOSED;

	public boolean persisted() {
		return this == LOBBY || this == DRAWN || this == SETTLED;
	}
}
