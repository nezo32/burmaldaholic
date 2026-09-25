package dev.nezo.burmaldaholic.independent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.nezo.burmaldaholic.games.craps.logic.Bet;
import dev.nezo.burmaldaholic.games.craps.logic.BetKind;
import dev.nezo.burmaldaholic.games.craps.logic.BetResolution;
import dev.nezo.burmaldaholic.games.craps.logic.CrapsMath;
import dev.nezo.burmaldaholic.games.craps.logic.CrapsResolver;
import dev.nezo.burmaldaholic.games.craps.logic.CrapsRules;
import dev.nezo.burmaldaholic.games.craps.logic.CrapsTable;
import dev.nezo.burmaldaholic.games.craps.logic.OddsSide;
import dev.nezo.burmaldaholic.games.craps.logic.RollEvent;
import dev.nezo.burmaldaholic.games.craps.logic.ShooterRotation.SeatInfo;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * GAME_DESIGN.md §10 / §17: exact expected values of every bet computed by solving the dice Markov chain
 * with the dev resolver as the transition function, compared to the spec edges; plus the state machine.
 */
class CrapsSpecTest {
	private static final CrapsRules R = CrapsRules.DEFAULT;
	private static final UUID A = new UUID(0, 1);
	private static final UUID B = new UUID(0, 2);

	/** P(total) for two fair dice, ×36. */
	private static int ways(int t) {
		return 6 - Math.abs(t - 7);
	}

	/**
	 * Expected total return of {@code bet} from table point {@code point}, following the table point too
	 * (Pass/DP depend on it). Recursion on (table point, bet state) solved by value iteration.
	 */
	private static double expectedReturn(BetKind kind, long flat, long odds, int betPoint, int tablePoint) {
		// value iteration over at most 7 × 7 states; converge
		double[][] v = new double[11][11];
		for (int it = 0; it < 2000; it++) {
			double[][] nv = new double[11][11];
			for (int tp : new int[] {0, 4, 5, 6, 8, 9, 10}) {
				for (int bp : new int[] {0, 4, 5, 6, 8, 9, 10}) {
					Bet b = new Bet(1, A, kind, flat, odds, bp);
					double e = 0;
					for (int t = 2; t <= 12; t++) {
						BetResolution r = CrapsResolver.resolve(b, tp, t, R);
						int nextTp = RollEvent.of(tp, t).nextPoint();
						double val = switch (r.outcome()) {
							case WIN, LOSE, PUSH -> r.totalReturn();
							case MOVE -> v[idx(nextTp)][idx(r.movedTo())];
							case STAY -> v[idx(nextTp)][idx(bp)];
						};
						e += ways(t) / 36.0 * val;
					}
					nv[idx(tp)][idx(bp)] = e;
				}
			}
			v = nv;
		}
		return v[idx(tablePoint)][idx(betPoint)];
	}

	private static int idx(int p) {
		return p;
	}

	@Test
	void flatBetEdgesMatchSpec() {
		assertEquals(7 / 495.0, 1 - expectedReturn(BetKind.PASS, 1, 0, 0, 0), 1e-9, "Pass 1.41 %");
		assertEquals(0.0141, 1 - expectedReturn(BetKind.PASS, 1, 0, 0, 0), 5e-5);
		// Don't Pass: HE is quoted per bet made (pushes count as bets): 3/220 = 1.364 %
		assertEquals(3 / 220.0, 1 - expectedReturn(BetKind.DONT_PASS, 1, 0, 0, 0), 1e-9, "Don't Pass 1.36 %");
		assertEquals(1 / 36.0, 1 - expectedReturn(BetKind.FIELD, 1, 0, 0, 0), 1e-12, "Field 2.78 %");
		// Come / Don't Come start when a point is on; same edges as Pass / DP
		for (int tp : new int[] {4, 5, 6, 8, 9, 10}) {
			assertEquals(7 / 495.0, 1 - expectedReturn(BetKind.COME, 1, 0, 0, tp), 1e-9, "Come with point " + tp);
			assertEquals(3 / 220.0, 1 - expectedReturn(BetKind.DONT_COME, 1, 0, 0, tp), 1e-9, "Don't Come with point " + tp);
		}
	}

	@Test
	void oddsAreFairAndWholeAtMax() {
		for (int p : CrapsMath.POINT_NUMBERS) {
			for (OddsSide side : OddsSide.values()) {
				long flat = 10;
				long max = CrapsMath.maxOdds(side, p, flat, R);
				assertEquals(0, max % CrapsMath.oddsUnit(side, p), "snapped");
				// flat bet already on its point, with max odds: EV(odds part) = 0
				BetKind kind = side == OddsSide.TAKE ? BetKind.PASS : BetKind.DONT_PASS;
				double withOdds = expectedReturn(kind, flat, max, 0, p);
				double without = expectedReturn(kind, flat, 0, 0, p);
				assertEquals(without + max, withOdds, 1e-9, side + " odds on " + p + " are 0 % edge");
				// payouts whole: win × unit divisible
				long win = CrapsMath.oddsWin(side, p, CrapsMath.oddsUnit(side, p));
				int[] ratio = CrapsMath.oddsRatio(side, p);
				assertEquals((double) CrapsMath.oddsUnit(side, p) * ratio[0] / ratio[1], win, 0);
			}
			// 3-4-5×: take max = 3/4/5 × flat; lay max = 6 × flat
			long mult = p == 4 || p == 10 ? 3 : p == 5 || p == 9 ? 4 : 5;
			assertEquals(mult * 10, CrapsMath.maxOdds(OddsSide.TAKE, p, 10, R), "take " + p);
			assertEquals(60, CrapsMath.maxOdds(OddsSide.LAY, p, 10, R), "lay " + p + " = 6× flat");
		}
		// UI snaps odds down: take 5/9 even, 6/8 ×5; lay 4/10 ×2, 5/9 ×3, 6/8 ×6
		assertEquals(2, CrapsMath.oddsUnit(OddsSide.TAKE, 5));
		assertEquals(5, CrapsMath.oddsUnit(OddsSide.TAKE, 8));
		assertEquals(2, CrapsMath.oddsUnit(OddsSide.LAY, 10));
		assertEquals(3, CrapsMath.oddsUnit(OddsSide.LAY, 9));
		assertEquals(6, CrapsMath.oddsUnit(OddsSide.LAY, 6));
	}

	@Test
	void passWithFullOddsCombinedEdge() {
		// Combined Pass + full 3-4-5× odds ≈ 0.37 %: E[loss] / E[total staked]
		double loss = 0, staked = 0;
		for (int t = 2; t <= 12; t++) {
			double p = ways(t) / 36.0;
			if (CrapsMath.isPoint(t)) {
				long odds = CrapsMath.maxOdds(OddsSide.TAKE, t, 1, R);
				loss += p * (1 + odds - expectedReturn(BetKind.PASS, 1, odds, 0, t));
				staked += p * (1 + odds);
			} else {
				loss += p * (1 - resolveNow(t)); // natural / craps resolve at once
				staked += p;
			}
		}
		assertEquals(0.0037, loss / staked, 1e-4);
	}

	private static double resolveNow(int t) {
		return CrapsResolver.resolve(new Bet(1, A, BetKind.PASS, 1, 0, 0), 0, t, R).totalReturn();
	}

	@Test
	void fieldPays() {
		assertEquals(30, CrapsMath.fieldReturn(2, 10, R), "2 pays 2:1");
		assertEquals(40, CrapsMath.fieldReturn(12, 10, R), "12 pays 3:1");
		for (int t : new int[] {3, 4, 9, 10, 11}) {
			assertEquals(20, CrapsMath.fieldReturn(t, 10, R));
		}
		for (int t : new int[] {5, 6, 7, 8}) {
			assertEquals(0, CrapsMath.fieldReturn(t, 10, R));
		}
	}

	@Test
	void comeOddsAreOffOnTheComeOut() {
		Bet come = new Bet(1, A, BetKind.COME, 10, 10, 6);
		BetResolution seven = CrapsResolver.resolve(come, 0, 7, R);
		assertEquals(BetResolution.Outcome.LOSE, seven.outcome());
		assertEquals(10, seven.totalReturn(), "flat lost, odds returned");
		BetResolution six = CrapsResolver.resolve(come, 0, 6, R);
		assertEquals(30, six.totalReturn(), "flat wins 1:1, odds returned without winnings");
		BetResolution sixOn = CrapsResolver.resolve(come, 4, 6, R);
		assertEquals(20 + 10 + 12, sixOn.totalReturn(), "with a point on the odds work: 6:5");
	}

	@Test
	void stateMachine() {
		CrapsTable t = new CrapsTable(R);
		List<SeatInfo> seats = List.of(new SeatInfo(A, 0, true), new SeatInfo(B, 1, true));
		assertNull(t.placeError(A, BetKind.PASS));
		assertEquals(CrapsTable.PlaceError.NEEDS_POINT, t.placeError(A, BetKind.COME));
		t.addBet(A, BetKind.PASS, 10);
		t.addBet(B, BetKind.DONT_PASS, 10);
		t.ensureShooter(seats, true);
		assertEquals(A, t.shooter());
		assertTrue(t.canRoll());
		// come-out 12: pass loses, DP pushes (bar 12)
		CrapsTable.TableRoll r = t.roll(6, 6, seats);
		assertEquals(0, total(r, A));
		assertEquals(10, total(r, B));
		assertEquals(0, t.point());
		assertTrue(t.bets().isEmpty());
		t.addBet(A, BetKind.PASS, 10);
		r = t.roll(2, 2, seats);
		assertEquals(4, t.point(), "point set");
		assertEquals(CrapsTable.PlaceError.LINE_ONLY_COME_OUT, t.placeError(B, BetKind.PASS));
		assertNull(t.placeError(B, BetKind.COME));
		Bet pass = t.betsOf(A).getFirst();
		assertNotNull(t.oddsInfo(pass));
		assertEquals(CrapsTable.OddsError.MAX, t.oddsError(pass, 31));
		assertNull(t.oddsError(pass, 30));
		t.addOdds(pass.id(), 30);
		t.addBet(B, BetKind.COME, 5);
		r = t.roll(3, 3, seats); // 6: come travels to 6
		assertEquals(6, t.betsOf(B).getFirst().point());
		r = t.roll(1, 3, seats); // point made: pass + odds 2:1
		assertEquals(20 + 30 + 60, total(r, A));
		assertEquals(A, t.shooter(), "same shooter after making the point");
		assertEquals(1, r.pointsInRow());
		assertEquals(0, t.point());
		// come bet at 6 still works on the come-out (without odds)
		t.addBet(A, BetKind.PASS, 10);
		r = t.roll(4, 4, seats); // point 8
		r = t.roll(3, 4, seats); // seven-out
		assertTrue(r.sevenOut());
		assertEquals(0, total(r, A));
		assertEquals(0, total(r, B), "come bet on 6 loses on seven-out");
		assertEquals(B, t.shooter(), "dice pass clockwise");
		assertEquals(0, t.pointsInRow());
	}

	private static long total(CrapsTable.TableRoll r, UUID who) {
		return r.result().resolutions().stream().filter(x -> x.bet().owner().equals(who) && x.outcome().resolved())
			.mapToLong(BetResolution::totalReturn).sum();
	}

	@Test
	void autoCompleteConservesExpectedValue() {
		// §4.1: a leaver's bets are rolled out; with a fair die source EV equals staying.
		java.util.SplittableRandom rnd = new java.util.SplittableRandom(7);
		long staked = 0, back = 0;
		for (int i = 0; i < 300_000; i++) {
			Bet b = new Bet(1, A, BetKind.PASS, 10, 0, 0);
			back += CrapsResolver.autoComplete(0, List.of(b), rnd::nextInt, R).get(1);
			staked += 10;
		}
		assertEquals(1 - 7 / 495.0, (double) back / staked, 0.006);
	}
}
