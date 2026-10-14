package dev.nezo.burmaldaholic.games.uth.present;

import java.util.ArrayList;
import java.util.List;

/**
 * The UTH bet being built on the v2 BetBar before it is confirmed (REDESIGN.md §7.4; the v0.1.1 report "the UTH bet
 * can't be changed"): Ante (the Blind mirrors it) and Trips, a chip target ("armed" spot), and an undo history. Every
 * edit is explicit and reversible — add a chip, remove one (right click), Undo, Clear, Rebet, ×2 — and a confirmed bet
 * is loaded back into the draft with {@link #load} when the player changes it (the screen sends {@code clear}, the
 * server refunds, the draft keeps the amounts). Immutable; pure.
 *
 * @param ante   Ante (the Blind is the same amount)
 * @param trips  Trips side bet (0 = none)
 * @param spot   where chips go now
 * @param history previous {@code [ante, trips]} states, newest last (≤ {@link #HISTORY})
 */
public record UthBetDraft(long ante, long trips, Spot spot, List<long[]> history) {
	public enum Spot {
		ANTE, TRIPS
	}

	public static final int HISTORY = 32;

	public UthBetDraft {
		ante = Math.max(0, ante);
		trips = Math.max(0, trips);
		history = List.copyOf(history);
	}

	public static UthBetDraft empty() {
		return new UthBetDraft(0, 0, Spot.ANTE, List.of());
	}

	/** Ante + Blind + Trips: what confirming debits. */
	public long cost() {
		return 2 * ante + trips;
	}

	public boolean isEmpty() {
		return ante == 0 && trips == 0;
	}

	public UthBetDraft arm(Spot s) {
		return new UthBetDraft(ante, trips, s, history);
	}

	/** One chip onto {@code s} (and arms it). */
	public UthBetDraft add(Spot s, long chip) {
		if (chip <= 0) return arm(s);
		return s == Spot.TRIPS ? push(ante, trips + chip, s) : push(ante + chip, trips, s);
	}

	/** One chip of {@code chip} off {@code s} (never below 0; nothing to remove = unchanged). */
	public UthBetDraft remove(Spot s, long chip) {
		long cur = s == Spot.TRIPS ? trips : ante;
		if (cur <= 0 || chip <= 0) return this;
		long next = Math.max(0, cur - chip);
		return s == Spot.TRIPS ? push(ante, next, spot) : push(next, trips, spot);
	}

	public boolean canUndo() {
		return !history.isEmpty();
	}

	public UthBetDraft undo() {
		if (history.isEmpty()) return this;
		long[] last = history.get(history.size() - 1);
		return new UthBetDraft(last[0], last[1], spot, history.subList(0, history.size() - 1));
	}

	public UthBetDraft clear() {
		return isEmpty() ? this : push(0, 0, Spot.ANTE);
	}

	public UthBetDraft doubled() {
		return isEmpty() ? this : push(ante * 2, trips * 2, spot);
	}

	/** The bet of the previous round (or a confirmed bet being changed). */
	public UthBetDraft load(long a, long t) {
		return a == ante && t == trips ? this : push(a, t, spot);
	}

	public boolean equalsBet(long a, long t) {
		return ante == a && trips == t;
	}

	private UthBetDraft push(long a, long t, Spot s) {
		List<long[]> h = new ArrayList<>(history);
		h.add(new long[] {ante, trips});
		while (h.size() > HISTORY) h.remove(0);
		return new UthBetDraft(a, t, s, h);
	}
}
