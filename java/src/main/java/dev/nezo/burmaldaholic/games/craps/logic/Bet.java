package dev.nezo.burmaldaholic.games.craps.logic;

import java.util.UUID;

/**
 * One flat bet on the layout with the odds behind it. {@code point} is the Come / Don't Come point
 * once the bet has travelled (0 = not moved; Pass / Don't Pass use the table point).
 */
public final class Bet {
	private final int id;
	private final UUID owner;
	private final BetKind kind;
	private final long flat;
	private long odds;
	private int point;

	public Bet(int id, UUID owner, BetKind kind, long flat, long odds, int point) {
		this.id = id;
		this.owner = owner;
		this.kind = kind;
		this.flat = flat;
		this.odds = odds;
		this.point = point;
	}

	public int id() {
		return id;
	}

	public UUID owner() {
		return owner;
	}

	public BetKind kind() {
		return kind;
	}

	public long flat() {
		return flat;
	}

	public long odds() {
		return odds;
	}

	public int point() {
		return point;
	}

	/** flat + odds: what this bet has at risk. */
	public long staked() {
		return flat + odds;
	}

	void addOdds(long amount) {
		odds += amount;
	}

	void moveTo(int newPoint) {
		point = newPoint;
	}

	public Bet copy() {
		return new Bet(id, owner, kind, flat, odds, point);
	}

	@Override
	public String toString() {
		return kind.id() + "#" + id + "(" + flat + "+" + odds + (point != 0 ? "@" + point : "") + ")";
	}
}
