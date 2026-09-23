package dev.nezo.burmaldaholic.games.craps.logic;

import dev.nezo.burmaldaholic.games.craps.logic.CrapsResolver.RollResult;
import dev.nezo.burmaldaholic.games.craps.logic.ShooterRotation.SeatInfo;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Craps table state machine (GAME_DESIGN §10.2). Pure Java, no money: the block entity maps every
 * bet id to an escrowed stake.
 *
 * <pre>
 * COME_OUT (puck OFF): Pass, Don't Pass, Field (+ odds on travelled come bets, which are OFF)
 *   7/11 natural · 2/3/12 craps (12 = bar for Don't) · 4,5,6,8,9,10 → point, puck ON
 * POINT (puck ON): Come, Don't Come, Field, Odds
 *   point → pass wins, puck OFF (same shooter) · 7 → seven-out, puck OFF, next shooter
 * </pre>
 */
public final class CrapsTable {
	public enum PlaceError {
		/** Pass / Don't Pass only on the come-out roll (v1 rule). */
		LINE_ONLY_COME_OUT,
		/** Come / Don't Come need a point. */
		NEEDS_POINT,
		/** One bet of each kind per player (per travelling state). */
		ALREADY_PLACED
	}

	public enum OddsError {
		NO_POINT, MULTIPLE, MAX
	}

	/**
	 * Odds that may go behind a bet.
	 *
	 * @param unit amounts must be a multiple of this
	 * @param max  max total odds behind the bet
	 * @param room how much more may be added (snapped)
	 * @param off  come odds held during the come-out roll are not working
	 */
	public record OddsInfo(Bet bet, OddsSide side, int point, int unit, long max, long room, boolean off) {}

	/** A roll as the table saw it. */
	public record TableRoll(RollResult result, @Nullable UUID shooter, boolean sevenOut, int pointsInRow) {}

	private CrapsRules rules;
	private int point;
	private final List<Bet> bets = new ArrayList<>();
	private @Nullable UUID shooter;
	private int shooterSeat = -1;
	private int lastD1;
	private int lastD2;
	private @Nullable RollEvent lastEvent;
	private int pointsInRow;
	private int rollCount;
	private int seq;

	public CrapsTable(CrapsRules rules) {
		this.rules = rules;
	}

	public CrapsRules rules() {
		return rules;
	}

	public void setRules(CrapsRules rules) {
		this.rules = Objects.requireNonNull(rules);
	}

	/** Current point (0 = puck OFF, come-out roll). */
	public int point() {
		return point;
	}

	public boolean comeOut() {
		return point == 0;
	}

	public @Nullable UUID shooter() {
		return shooter;
	}

	/** Last dice (0,0 before the first roll). */
	public int lastD1() {
		return lastD1;
	}

	public int lastD2() {
		return lastD2;
	}

	public @Nullable RollEvent lastEvent() {
		return lastEvent;
	}

	public int pointsInRow() {
		return pointsInRow;
	}

	/** Rolls thrown at this table (invalidates stale timers, drives client animation). */
	public int rollCount() {
		return rollCount;
	}

	public List<Bet> bets() {
		return List.copyOf(bets);
	}

	public List<Bet> betsOf(UUID owner) {
		return bets.stream().filter(b -> b.owner().equals(owner)).toList();
	}

	public @Nullable Bet bet(int id) {
		for (Bet b : bets) {
			if (b.id() == id) {
				return b;
			}
		}
		return null;
	}

	public boolean hasLineBet(UUID owner) {
		return bets.stream().anyMatch(b -> b.owner().equals(owner) && b.kind().isLine());
	}

	// ---- placing -------------------------------------------------------------------------------

	/** Why {@code kind} cannot be placed now by {@code owner}; null = allowed. */
	public @Nullable PlaceError placeError(UUID owner, BetKind kind) {
		List<Bet> mine = betsOf(owner);
		return switch (kind) {
			case PASS, DONT_PASS -> !comeOut() ? PlaceError.LINE_ONLY_COME_OUT
				: mine.stream().anyMatch(b -> b.kind() == kind) ? PlaceError.ALREADY_PLACED : null;
			case COME, DONT_COME -> comeOut() ? PlaceError.NEEDS_POINT
				: mine.stream().anyMatch(b -> b.kind() == kind && b.point() == 0) ? PlaceError.ALREADY_PLACED : null;
			case FIELD -> mine.stream().anyMatch(b -> b.kind() == BetKind.FIELD) ? PlaceError.ALREADY_PLACED : null;
		};
	}

	/** Adds a flat bet (check {@link #placeError} first). */
	public Bet addBet(UUID owner, BetKind kind, long flat) {
		if (flat <= 0) {
			throw new IllegalArgumentException("flat bet must be positive");
		}
		Bet bet = new Bet(++seq, owner, kind, flat, 0, 0);
		bets.add(bet);
		return bet;
	}

	/** Removes a bet that could not be funded. */
	public void removeBet(int id) {
		bets.removeIf(b -> b.id() == id);
	}

	/** The point odds behind {@code bet} would be on (0 = none yet). */
	public int pointOf(Bet bet) {
		return switch (bet.kind()) {
			case PASS, DONT_PASS -> point;
			case COME, DONT_COME -> bet.point();
			case FIELD -> 0;
		};
	}

	public @Nullable OddsInfo oddsInfo(Bet bet) {
		OddsSide side = bet.kind().oddsSide();
		int p = pointOf(bet);
		if (side == null || p == 0) {
			return null;
		}
		int unit = CrapsMath.oddsUnit(side, p);
		long max = CrapsMath.maxOdds(side, p, bet.flat(), rules);
		long room = Math.max(0, CrapsMath.snapOdds(max - bet.odds(), unit));
		return new OddsInfo(bet, side, p, unit, max, room, bet.kind() == BetKind.COME && comeOut());
	}

	/** Bets of {@code owner} that can take (more) odds now. */
	public List<OddsInfo> oddsTargets(UUID owner) {
		List<OddsInfo> out = new ArrayList<>();
		for (Bet b : betsOf(owner)) {
			OddsInfo i = oddsInfo(b);
			if (i != null && i.room() > 0) {
				out.add(i);
			}
		}
		return out;
	}

	public @Nullable OddsError oddsError(Bet bet, long amount) {
		OddsInfo info = oddsInfo(bet);
		if (info == null) {
			return OddsError.NO_POINT;
		}
		if (amount <= 0 || amount % info.unit() != 0) {
			return OddsError.MULTIPLE;
		}
		if (bet.odds() + amount > info.max()) {
			return OddsError.MAX;
		}
		return null;
	}

	/** Adds odds (check {@link #oddsError} first). */
	public void addOdds(int betId, long amount) {
		Bet b = bet(betId);
		if (b != null) {
			b.addOdds(amount);
		}
	}

	/** Takes every bet of a player off the table (they left); returns them for auto-completion/refund. */
	public List<Bet> removeOwner(UUID owner) {
		List<Bet> mine = betsOf(owner);
		bets.removeIf(b -> b.owner().equals(owner));
		return mine;
	}

	/** Removes everything (refund of the whole table). */
	public List<Bet> clear() {
		List<Bet> all = List.copyOf(bets);
		bets.clear();
		return all;
	}

	// ---- shooter -------------------------------------------------------------------------------

	private List<SeatInfo> withLineBets(List<SeatInfo> seats) {
		return seats.stream().map(s -> new SeatInfo(s.player(), s.seat(), hasLineBet(s.player()))).toList();
	}

	/**
	 * Keeps the shooter valid for the seated players: on the come-out the shooter must have a line
	 * bet (the dice move clockwise to the next player with one); during a point the shooter only has
	 * to be seated. With {@code passIfNoLineBet} false a seated shooter keeps the dice while still
	 * placing their line bet (the betting window closing passes them on). Returns true on change.
	 */
	public boolean ensureShooter(List<SeatInfo> seats, boolean passIfNoLineBet) {
		UUID before = shooter;
		boolean seated = shooter != null && seats.stream().anyMatch(s -> s.player().equals(shooter));
		if (seats.isEmpty()) {
			shooter = null;
		} else if (comeOut()) {
			if (!seated || (passIfNoLineBet && !hasLineBet(shooter))) {
				UUID next = ShooterRotation.next(withLineBets(seats), shooter, true, true, shooterSeat);
				if (next != null) {
					shooter = next;
				} else if (!seated) {
					shooter = ShooterRotation.next(seats, shooter, false, true, shooterSeat);
				}
			}
		} else if (!seated) {
			shooter = ShooterRotation.next(seats, shooter, false, true, shooterSeat);
		}
		updateSeat(seats);
		if (!Objects.equals(shooter, before)) {
			pointsInRow = 0;
			return true;
		}
		return false;
	}

	private void updateSeat(List<SeatInfo> seats) {
		for (SeatInfo s : seats) {
			if (s.player().equals(shooter)) {
				shooterSeat = s.seat();
			}
		}
	}

	/** Whether the shooter may throw now (the come-out needs their line bet). */
	public boolean canRoll() {
		return shooter != null && (!comeOut() || hasLineBet(shooter));
	}

	/** Throws the dice: resolves every bet, moves the puck, passes the dice on a seven-out. */
	public TableRoll roll(int d1, int d2, List<SeatInfo> seats) {
		if (d1 < 1 || d1 > 6 || d2 < 1 || d2 > 6) {
			throw new IllegalArgumentException("dice out of range");
		}
		RollResult r = CrapsResolver.apply(point, bets, d1, d2, rules);
		UUID thrower = shooter;
		bets.clear();
		bets.addAll(r.remaining());
		point = r.event().nextPoint();
		lastD1 = d1;
		lastD2 = d2;
		lastEvent = r.event();
		rollCount++;
		boolean sevenOut = r.event().kind() == RollEvent.Kind.SEVEN_OUT;
		if (r.event().kind() == RollEvent.Kind.POINT_MADE) {
			pointsInRow++;
		}
		int made = pointsInRow;
		if (sevenOut) {
			pointsInRow = 0;
			UUID next = ShooterRotation.next(seats, thrower, false, true, shooterSeat);
			if (next != null) {
				shooter = next;
				updateSeat(seats);
			}
		}
		return new TableRoll(r, thrower, sevenOut, made);
	}
}
