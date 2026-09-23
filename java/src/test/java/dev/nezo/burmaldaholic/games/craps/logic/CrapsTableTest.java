package dev.nezo.burmaldaholic.games.craps.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.nezo.burmaldaholic.games.craps.logic.BetResolution.Outcome;
import dev.nezo.burmaldaholic.games.craps.logic.CrapsTable.OddsError;
import dev.nezo.burmaldaholic.games.craps.logic.CrapsTable.OddsInfo;
import dev.nezo.burmaldaholic.games.craps.logic.CrapsTable.PlaceError;
import dev.nezo.burmaldaholic.games.craps.logic.CrapsTable.TableRoll;
import dev.nezo.burmaldaholic.games.craps.logic.ShooterRotation.SeatInfo;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Placement rules, odds limits, the point cycle and shooter rotation (GAME_DESIGN §10.2). */
class CrapsTableTest {
	static final UUID A = new UUID(0, 1);
	static final UUID B = new UUID(0, 2);
	static final UUID C = new UUID(0, 3);

	static List<SeatInfo> seats(CrapsTable t, UUID... ids) {
		List<SeatInfo> list = new ArrayList<>();
		for (int i = 0; i < ids.length; i++) {
			list.add(new SeatInfo(ids[i], i, t.hasLineBet(ids[i])));
		}
		return list;
	}

	@Test
	void lineBetsOnlyOnTheComeOutComeBetsOnlyWithAPoint() {
		CrapsTable t = new CrapsTable(CrapsRules.DEFAULT);
		assertNull(t.placeError(A, BetKind.PASS));
		assertEquals(PlaceError.NEEDS_POINT, t.placeError(A, BetKind.COME));
		assertEquals(PlaceError.NEEDS_POINT, t.placeError(A, BetKind.DONT_COME));
		t.addBet(A, BetKind.PASS, 10);
		assertEquals(PlaceError.ALREADY_PLACED, t.placeError(A, BetKind.PASS));
		assertNull(t.placeError(A, BetKind.DONT_PASS));
		assertNull(t.placeError(B, BetKind.PASS), "per player");
		t.roll(3, 3, List.of());
		assertEquals(6, t.point());
		assertEquals(PlaceError.LINE_ONLY_COME_OUT, t.placeError(A, BetKind.DONT_PASS));
		assertNull(t.placeError(A, BetKind.COME));
		t.addBet(A, BetKind.COME, 5);
		assertEquals(PlaceError.ALREADY_PLACED, t.placeError(A, BetKind.COME));
		t.roll(4, 5, List.of()); // come moves to 9
		assertNull(t.placeError(A, BetKind.COME), "a travelled come bet frees the Come area");
		assertEquals(9, t.betsOf(A).stream().filter(b -> b.kind() == BetKind.COME).findFirst().orElseThrow().point());
		assertThrows(IllegalArgumentException.class, () -> t.addBet(A, BetKind.FIELD, 0));
	}

	@Test
	void fieldIsOneRoll() {
		CrapsTable t = new CrapsTable(CrapsRules.DEFAULT);
		t.addBet(A, BetKind.FIELD, 10);
		assertEquals(PlaceError.ALREADY_PLACED, t.placeError(A, BetKind.FIELD));
		TableRoll r = t.roll(1, 1, List.of());
		assertEquals(Outcome.WIN, r.result().resolutions().get(0).outcome());
		assertEquals(30, r.result().resolutions().get(0).totalReturn());
		assertTrue(t.bets().isEmpty());
		t.addBet(A, BetKind.FIELD, 10);
		assertEquals(0, t.roll(3, 4, List.of()).result().resolutions().get(0).totalReturn());
	}

	@Test
	void passOddsNeedAPointAndRespectMultiplesAndMax() {
		CrapsTable t = new CrapsTable(CrapsRules.DEFAULT);
		Bet b = t.addBet(A, BetKind.PASS, 10);
		assertEquals(OddsError.NO_POINT, t.oddsError(b, 10));
		assertTrue(t.oddsTargets(A).isEmpty());
		t.roll(2, 4, List.of()); // point 6
		OddsInfo info = t.oddsInfo(b);
		assertNotNull(info);
		assertEquals(OddsSide.TAKE, info.side());
		assertEquals(5, info.unit());
		assertEquals(50, info.max());
		assertEquals(50, info.room());
		assertFalse(info.off());
		assertEquals(OddsError.MULTIPLE, t.oddsError(b, 7));
		assertEquals(OddsError.MULTIPLE, t.oddsError(b, 0));
		assertEquals(OddsError.MAX, t.oddsError(b, 55));
		assertNull(t.oddsError(b, 20));
		t.addOdds(b.id(), 20);
		assertEquals(30, t.oddsInfo(t.bet(b.id())).room());
		t.addOdds(b.id(), 30);
		assertTrue(t.oddsTargets(A).isEmpty());
		TableRoll r = t.roll(5, 1, List.of());
		assertEquals(RollEvent.Kind.POINT_MADE, r.result().event().kind());
		assertEquals(20 + 50 + 60, r.result().resolutions().get(0).totalReturn());
		assertEquals(1, r.pointsInRow());
		assertTrue(t.comeOut());
	}

	@Test
	void layOddsBehindDontPass() {
		CrapsTable t = new CrapsTable(CrapsRules.DEFAULT);
		Bet b = t.addBet(A, BetKind.DONT_PASS, 10);
		t.roll(1, 3, List.of()); // point 4
		OddsInfo info = t.oddsInfo(b);
		assertEquals(OddsSide.LAY, info.side());
		assertEquals(2, info.unit());
		assertEquals(60, info.max());
		t.addOdds(b.id(), 60);
		assertEquals(20 + 60 + 30, t.roll(3, 4, List.of()).result().resolutions().get(0).totalReturn());
	}

	@Test
	void comeOddsAreOffOnTheComeOutAndReturnedOnASeven() {
		CrapsTable t = new CrapsTable(CrapsRules.DEFAULT);
		t.addBet(A, BetKind.PASS, 10);
		t.roll(4, 4, List.of()); // point 8
		Bet c = t.addBet(A, BetKind.COME, 10);
		t.roll(2, 3, List.of()); // come → 5
		assertEquals(OddsSide.TAKE, t.oddsInfo(t.bet(c.id())).side());
		t.addOdds(c.id(), 20);
		t.roll(4, 4, List.of()); // point made → come-out
		assertTrue(t.comeOut());
		assertTrue(t.oddsInfo(t.bet(c.id())).off());
		assertEquals(1, t.oddsTargets(A).size(), "odds may still be adjusted during the come-out");
		TableRoll r = t.roll(3, 4, List.of()); // 7 on the come-out: come on 5 loses the flat, odds returned
		BetResolution res = r.result().resolutions().stream().filter(x -> x.bet().id() == c.id()).findFirst().orElseThrow();
		assertEquals(Outcome.LOSE, res.outcome());
		assertEquals(20, res.totalReturn());
		assertTrue(res.oddsReturned());
	}

	@Test
	void comeBetOnTheComeOutWinsWithoutOddsPayout() {
		CrapsTable t = new CrapsTable(CrapsRules.DEFAULT);
		t.roll(3, 3, List.of()); // point 6
		Bet c = t.addBet(A, BetKind.COME, 10);
		t.roll(4, 5, List.of()); // → 9
		t.addOdds(c.id(), 20);
		t.roll(3, 3, List.of()); // point made
		TableRoll r = t.roll(4, 5, List.of()); // 9 on come-out: flat wins, odds just returned
		assertEquals(20 + 20, r.result().resolutions().get(0).totalReturn());
		assertEquals(RollEvent.Kind.POINT_SET, r.result().event().kind());
	}

	@Test
	void removeOwnerAndClear() {
		CrapsTable t = new CrapsTable(CrapsRules.DEFAULT);
		t.addBet(A, BetKind.PASS, 10);
		t.addBet(B, BetKind.PASS, 10);
		t.addBet(A, BetKind.FIELD, 5);
		assertEquals(2, t.removeOwner(A).size());
		assertEquals(1, t.bets().size());
		assertEquals(1, t.clear().size());
		assertTrue(t.bets().isEmpty());
	}

	@Test
	void rollRejectsImpossibleDice() {
		CrapsTable t = new CrapsTable(CrapsRules.DEFAULT);
		assertThrows(IllegalArgumentException.class, () -> t.roll(0, 3, List.of()));
		assertThrows(IllegalArgumentException.class, () -> t.roll(3, 7, List.of()));
	}

	// ---- shooter ------------------------------------------------------------------------------

	@Test
	void rotationMovesClockwiseAndWraps() {
		List<SeatInfo> s = List.of(new SeatInfo(A, 1, true), new SeatInfo(B, 3, false), new SeatInfo(C, 4, true));
		assertEquals(B, ShooterRotation.next(s, A, false));
		assertEquals(C, ShooterRotation.next(s, A, true));
		assertEquals(A, ShooterRotation.next(s, C, true));
		assertEquals(A, ShooterRotation.next(s, C, false));
		assertEquals(A, ShooterRotation.next(s, null, true));
		assertEquals(A, ShooterRotation.next(List.of(s.get(0)), A, false), "single player keeps the dice");
		assertNull(ShooterRotation.next(List.of(s.get(0)), A, false, false, -1));
		assertNull(ShooterRotation.next(List.of(), A, false));
		assertNull(ShooterRotation.next(List.of(s.get(1)), B, true));
		assertEquals(A, ShooterRotation.next(s, UUID.randomUUID(), false), "unknown current → first seat");
		assertEquals(C, ShooterRotation.next(s, UUID.randomUUID(), false, true, 3), "left shooter: the seat after theirs");
	}

	@Test
	void shooterNeedsALineBetOnTheComeOut() {
		CrapsTable t = new CrapsTable(CrapsRules.DEFAULT);
		assertFalse(t.ensureShooter(List.of(), true));
		assertTrue(t.ensureShooter(seats(t, A, B), false));
		assertEquals(A, t.shooter(), "first seat gets the dice");
		assertFalse(t.canRoll());
		// Window still open: A keeps the dice while choosing a bet.
		t.addBet(B, BetKind.PASS, 5);
		assertFalse(t.ensureShooter(seats(t, A, B), false));
		assertEquals(A, t.shooter());
		// Window closed: A has no line bet → dice pass to B.
		assertTrue(t.ensureShooter(seats(t, A, B), true));
		assertEquals(B, t.shooter());
		assertTrue(t.canRoll());
	}

	@Test
	void loneShooterWithoutLineBetKeepsTheDice() {
		CrapsTable t = new CrapsTable(CrapsRules.DEFAULT);
		t.ensureShooter(seats(t, A), true);
		assertEquals(A, t.shooter());
		assertFalse(t.canRoll());
		t.addBet(A, BetKind.DONT_PASS, 5);
		assertTrue(t.canRoll());
	}

	@Test
	void sevenOutPassesTheDiceClockwisePointMadeKeepsThem() {
		CrapsTable t = new CrapsTable(CrapsRules.DEFAULT);
		t.addBet(A, BetKind.PASS, 5);
		t.addBet(B, BetKind.PASS, 5);
		t.ensureShooter(seats(t, A, B, C), true);
		assertEquals(A, t.shooter());
		t.roll(2, 2, seats(t, A, B, C)); // point 4
		TableRoll made = t.roll(1, 3, seats(t, A, B, C));
		assertEquals(A, t.shooter(), "point made: same shooter");
		assertEquals(1, made.pointsInRow());
		t.addBet(A, BetKind.PASS, 5);
		t.roll(3, 3, seats(t, A, B, C)); // point 6
		assertTrue(t.canRoll(), "during a point the shooter only needs to be seated");
		TableRoll out = t.roll(3, 4, seats(t, A, B, C));
		assertTrue(out.sevenOut());
		assertEquals(A, out.shooter());
		assertEquals(0, t.pointsInRow());
		assertEquals(B, t.shooter(), "next clockwise");
	}

	@Test
	void shooterWhoLeavesMidPointIsReplaced() {
		CrapsTable t = new CrapsTable(CrapsRules.DEFAULT);
		t.addBet(A, BetKind.PASS, 5);
		t.ensureShooter(seats(t, A, B, C), true);
		t.roll(5, 5, seats(t, A, B, C)); // point 10
		t.removeOwner(A);
		assertTrue(t.ensureShooter(List.of(new SeatInfo(C, 2, false), new SeatInfo(B, 1, false)), false));
		assertEquals(B, t.shooter());
		assertTrue(t.canRoll(), "point stays on for the new shooter");
	}

	@Test
	void hotShooterCountsPointsInARow() {
		CrapsTable t = new CrapsTable(CrapsRules.DEFAULT);
		t.addBet(A, BetKind.PASS, 5);
		t.ensureShooter(seats(t, A), true);
		for (int i = 1; i <= 3; i++) {
			t.roll(3, 3, seats(t, A));
			assertEquals(i, t.roll(4, 2, seats(t, A)).pointsInRow());
		}
		t.roll(3, 3, seats(t, A));
		t.roll(3, 4, seats(t, A));
		assertEquals(0, t.pointsInRow());
	}
}
