package dev.nezo.burmaldaholic.core.pvp;

import dev.nezo.burmaldaholic.core.bots.logic.SeatOccupant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * One participant of a match (PVP.md §3.1; bots: PVP.md §3.15.2). {@code index} is the join order and
 * the index used by the mode's tape / outcome; {@code stake} what is escrowed (bots: from their purse).
 */
public final class Participant {
	public final int index;
	public final SeatOccupant occupant;
	private long stake;
	private boolean allIn;
	private boolean pressed;
	private int taunts;
	private boolean wantsRematch;
	/** Engine-internal pacing (transient). */
	long lastTaunt = Long.MIN_VALUE;
	/** Bot: tick at which it presses / answers the pending decision (-1 = none). */
	long botActAt = -1;
	/** Answered the rematch question (yes or no). */
	boolean answered;

	public Participant(int index, SeatOccupant occupant, long stake) {
		this.index = index;
		this.occupant = occupant;
		this.stake = stake;
	}

	public long stake() {
		return stake;
	}

	/** Wheel Party top-up (engine only, after escrow). */
	void addStake(long extra) {
		stake += extra;
	}

	public boolean allIn() {
		return allIn;
	}

	void setAllIn(boolean v) {
		allIn = v;
	}

	/** Pressed the mode's advance button (Spin! / Drop! / Scratch!) in the current wait step. */
	public boolean pressed() {
		return pressed;
	}

	void setPressed(boolean v) {
		pressed = v;
	}

	public int taunts() {
		return taunts;
	}

	void countTaunt() {
		taunts++;
	}

	public boolean wantsRematch() {
		return wantsRematch;
	}

	void setWantsRematch(boolean v) {
		wantsRematch = v;
	}

	/** The human's UUID, or null for a bot. */
	public @Nullable UUID humanId() {
		return occupant instanceof SeatOccupant.Human h ? h.id() : null;
	}

	public boolean isBot() {
		return occupant.isBot();
	}
}
