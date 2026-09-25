package dev.nezo.burmaldaholic.games.craps.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.nezo.burmaldaholic.games.craps.logic.BetResolution.Outcome;
import dev.nezo.burmaldaholic.games.craps.logic.CrapsResolver.RollResult;
import dev.nezo.burmaldaholic.games.craps.logic.RollEvent.Kind;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.SplittableRandom;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Payout tables, the come-out / point state machine and bet resolution (GAME_DESIGN §10). */
class CrapsRulesTest {
	static final CrapsRules R = CrapsRules.DEFAULT;
	static final UUID P = UUID.randomUUID();

	static Bet bet(BetKind kind, long flat) {
		return new Bet(1, P, kind, flat, 0, 0);
	}

	static Bet bet(BetKind kind, long flat, long odds, int point) {
		return new Bet(1, P, kind, flat, odds, point);
	}

	static void expect(BetResolution r, Outcome outcome, long totalReturn) {
		assertEquals(outcome, r.outcome(), r.toString());
		assertEquals(totalReturn, r.totalReturn(), r.toString());
	}

	@Test
	void fieldPaysPerSpec() {
		Map<Integer, Long> expected = Map.ofEntries(Map.entry(2, 30L), Map.entry(3, 20L), Map.entry(4, 20L), Map.entry(5, 0L),
			Map.entry(6, 0L), Map.entry(7, 0L), Map.entry(8, 0L), Map.entry(9, 20L), Map.entry(10, 20L), Map.entry(11, 20L), Map.entry(12, 40L));
		for (int t = 2; t <= 12; t++) {
			assertEquals(expected.get(t), CrapsMath.fieldReturn(t, 10, R), "total " + t);
		}
		assertEquals(30, CrapsMath.fieldReturn(12, 10, new CrapsRules(2, 2, 3, 4, 5)));
	}

	@Test
	void takeAndLayOddsPayTrueOdds() {
		assertEquals(20, CrapsMath.oddsWin(OddsSide.TAKE, 4, 10));
		assertEquals(20, CrapsMath.oddsWin(OddsSide.TAKE, 10, 10));
		assertEquals(15, CrapsMath.oddsWin(OddsSide.TAKE, 5, 10));
		assertEquals(15, CrapsMath.oddsWin(OddsSide.TAKE, 9, 10));
		assertEquals(12, CrapsMath.oddsWin(OddsSide.TAKE, 6, 10));
		assertEquals(30, CrapsMath.oddsWin(OddsSide.TAKE, 8, 25));
		assertEquals(10, CrapsMath.oddsWin(OddsSide.LAY, 4, 20));
		assertEquals(20, CrapsMath.oddsWin(OddsSide.LAY, 5, 30));
		assertEquals(50, CrapsMath.oddsWin(OddsSide.LAY, 6, 60));
	}

	@Test
	void oddsUnitsMakeEveryPayoutWhole() {
		int[] take = {1, 2, 5, 5, 2, 1};
		int[] lay = {2, 3, 6, 6, 3, 2};
		for (int i = 0; i < 6; i++) {
			int p = CrapsMath.POINT_NUMBERS[i];
			assertEquals(take[i], CrapsMath.oddsUnit(OddsSide.TAKE, p));
			assertEquals(lay[i], CrapsMath.oddsUnit(OddsSide.LAY, p));
			for (OddsSide side : OddsSide.values()) {
				int u = CrapsMath.oddsUnit(side, p);
				int[] r = CrapsMath.oddsRatio(side, p);
				for (int k = 1; k < 30; k++) {
					assertEquals(0, (long) k * u * r[0] % r[1], "whole payout " + side + " " + p);
				}
			}
		}
	}

	@Test
	void maxOddsAre345AndLayWins6x() {
		for (int p : CrapsMath.POINT_NUMBERS) {
			long take = CrapsMath.maxOdds(OddsSide.TAKE, p, 10, R);
			assertEquals(p == 4 || p == 10 ? 30 : p == 5 || p == 9 ? 40 : 50, take);
			long lay = CrapsMath.maxOdds(OddsSide.LAY, p, 10, R);
			assertEquals(60, lay, "lay wins at most 3-4-5x the flat bet = 60 laid");
			assertEquals(p == 4 || p == 10 ? 30 : p == 5 || p == 9 ? 40 : 50, CrapsMath.oddsWin(OddsSide.LAY, p, lay));
		}
		assertEquals(2, CrapsMath.maxOdds(OddsSide.TAKE, 5, 1, new CrapsRules(2, 3, 3, 3, 5)), "snapped to even");
		assertEquals(0, CrapsMath.maxOdds(OddsSide.TAKE, 6, 3, new CrapsRules(2, 3, 3, 4, 1)), "3 → multiple of 5 → 0");
		assertEquals(0, CrapsMath.maxOdds(OddsSide.LAY, 9, 1, new CrapsRules(2, 3, 3, 1, 5)));
		assertEquals(0, CrapsMath.maxOdds(OddsSide.TAKE, 4, 10, new CrapsRules(2, 3, 0, 4, 5)));
	}

	@Test
	void snapOddsRoundsDown() {
		assertEquals(25, CrapsMath.snapOdds(27, 5));
		assertEquals(0, CrapsMath.snapOdds(4, 5));
		assertEquals(12, CrapsMath.snapOdds(13, 6));
		assertEquals(0, CrapsMath.snapOdds(-3, 2));
	}

	@Test
	void worstCases() {
		assertEquals(20, CrapsMath.flatWorstCase(BetKind.PASS, 10, R));
		assertEquals(40, CrapsMath.flatWorstCase(BetKind.FIELD, 10, R));
		assertEquals(90, CrapsMath.oddsWorstCase(OddsSide.TAKE, 4, 30));
		assertEquals(90, CrapsMath.oddsWorstCase(OddsSide.LAY, 4, 60));
	}

	@Test
	void rollEventStateMachine() {
		assertEquals(Kind.NATURAL, RollEvent.of(0, 7).kind());
		assertEquals(Kind.NATURAL, RollEvent.of(0, 11).kind());
		for (int t : new int[] {2, 3, 12}) {
			assertEquals(Kind.CRAPS, RollEvent.of(0, t).kind());
			assertEquals(0, RollEvent.of(0, t).nextPoint());
		}
		for (int p : CrapsMath.POINT_NUMBERS) {
			RollEvent e = RollEvent.of(0, p);
			assertEquals(Kind.POINT_SET, e.kind());
			assertEquals(p, e.nextPoint());
		}
		assertEquals(new RollEvent(Kind.POINT_MADE, 6, 6), RollEvent.of(6, 6));
		assertEquals(0, RollEvent.of(6, 6).nextPoint());
		assertEquals(Kind.SEVEN_OUT, RollEvent.of(6, 7).kind());
		assertEquals(0, RollEvent.of(6, 7).nextPoint());
		assertEquals(Kind.ROLL, RollEvent.of(6, 11).kind());
		assertEquals(6, RollEvent.of(6, 11).nextPoint());
	}

	@Test
	void passLine() {
		expect(CrapsResolver.resolve(bet(BetKind.PASS, 10), 0, 7, R), Outcome.WIN, 20);
		expect(CrapsResolver.resolve(bet(BetKind.PASS, 10), 0, 11, R), Outcome.WIN, 20);
		for (int t : new int[] {2, 3, 12}) {
			expect(CrapsResolver.resolve(bet(BetKind.PASS, 10), 0, t, R), Outcome.LOSE, 0);
		}
		assertEquals(Outcome.STAY, CrapsResolver.resolve(bet(BetKind.PASS, 10), 0, 6, R).outcome());
		expect(CrapsResolver.resolve(bet(BetKind.PASS, 10, 50, 0), 6, 6, R), Outcome.WIN, 20 + 50 + 60);
		expect(CrapsResolver.resolve(bet(BetKind.PASS, 10, 30, 0), 4, 4, R), Outcome.WIN, 20 + 30 + 60);
		expect(CrapsResolver.resolve(bet(BetKind.PASS, 10, 40, 0), 9, 9, R), Outcome.WIN, 20 + 40 + 60);
		expect(CrapsResolver.resolve(bet(BetKind.PASS, 10, 50, 0), 6, 7, R), Outcome.LOSE, 0);
		assertEquals(Outcome.STAY, CrapsResolver.resolve(bet(BetKind.PASS, 10), 6, 11, R).outcome());
		assertEquals(Outcome.STAY, CrapsResolver.resolve(bet(BetKind.PASS, 10), 6, 2, R).outcome());
	}

	@Test
	void dontPassWithBar12() {
		expect(CrapsResolver.resolve(bet(BetKind.DONT_PASS, 10), 0, 2, R), Outcome.WIN, 20);
		expect(CrapsResolver.resolve(bet(BetKind.DONT_PASS, 10), 0, 3, R), Outcome.WIN, 20);
		expect(CrapsResolver.resolve(bet(BetKind.DONT_PASS, 10), 0, 12, R), Outcome.PUSH, 10);
		expect(CrapsResolver.resolve(bet(BetKind.DONT_PASS, 10), 0, 7, R), Outcome.LOSE, 0);
		expect(CrapsResolver.resolve(bet(BetKind.DONT_PASS, 10), 0, 11, R), Outcome.LOSE, 0);
		expect(CrapsResolver.resolve(bet(BetKind.DONT_PASS, 10, 60, 0), 4, 7, R), Outcome.WIN, 20 + 60 + 30);
		expect(CrapsResolver.resolve(bet(BetKind.DONT_PASS, 10, 60, 0), 6, 7, R), Outcome.WIN, 20 + 60 + 50);
		expect(CrapsResolver.resolve(bet(BetKind.DONT_PASS, 10, 60, 0), 4, 4, R), Outcome.LOSE, 0);
	}

	@Test
	void comeBetTravelsAndOddsAreOffOnTheComeOut() {
		expect(CrapsResolver.resolve(bet(BetKind.COME, 10), 6, 7, R), Outcome.WIN, 20);
		expect(CrapsResolver.resolve(bet(BetKind.COME, 10), 6, 11, R), Outcome.WIN, 20);
		expect(CrapsResolver.resolve(bet(BetKind.COME, 10), 6, 12, R), Outcome.LOSE, 0);
		BetResolution moved = CrapsResolver.resolve(bet(BetKind.COME, 10), 6, 5, R);
		assertEquals(Outcome.MOVE, moved.outcome());
		assertEquals(5, moved.movedTo());
		Bet onFive = bet(BetKind.COME, 10, 20, 5);
		expect(CrapsResolver.resolve(onFive, 6, 5, R), Outcome.WIN, 20 + 20 + 30);
		expect(CrapsResolver.resolve(onFive, 6, 7, R), Outcome.LOSE, 0);
		BetResolution offWin = CrapsResolver.resolve(onFive, 0, 5, R);
		expect(offWin, Outcome.WIN, 20 + 20);
		assertTrue(offWin.oddsReturned());
		BetResolution offLose = CrapsResolver.resolve(onFive, 0, 7, R);
		expect(offLose, Outcome.LOSE, 20);
		assertTrue(offLose.oddsReturned());
		assertEquals(-10, offLose.net(), "only the flat bet is lost");
		assertEquals(Outcome.STAY, CrapsResolver.resolve(onFive, 0, 6, R).outcome());
	}

	@Test
	void dontComeMirrorsDontPassAndLayOddsAlwaysWork() {
		expect(CrapsResolver.resolve(bet(BetKind.DONT_COME, 10), 6, 12, R), Outcome.PUSH, 10);
		expect(CrapsResolver.resolve(bet(BetKind.DONT_COME, 10), 6, 3, R), Outcome.WIN, 20);
		expect(CrapsResolver.resolve(bet(BetKind.DONT_COME, 10), 6, 11, R), Outcome.LOSE, 0);
		expect(CrapsResolver.resolve(bet(BetKind.DONT_COME, 10), 6, 7, R), Outcome.LOSE, 0);
		assertEquals(9, CrapsResolver.resolve(bet(BetKind.DONT_COME, 10), 6, 9, R).movedTo());
		Bet onNine = bet(BetKind.DONT_COME, 10, 30, 9);
		expect(CrapsResolver.resolve(onNine, 0, 7, R), Outcome.WIN, 20 + 30 + 20);
		expect(CrapsResolver.resolve(onNine, 6, 9, R), Outcome.LOSE, 0);
		assertFalse(CrapsResolver.resolve(onNine, 0, 7, R).oddsReturned());
	}

	@Test
	void sevenOutSettlesLineAndComeBetsTogether() {
		List<Bet> bets = List.of(
			new Bet(1, P, BetKind.PASS, 10, 30, 0),
			new Bet(2, P, BetKind.DONT_PASS, 10, 0, 0),
			new Bet(3, P, BetKind.COME, 10, 0, 8),
			new Bet(4, P, BetKind.DONT_COME, 10, 0, 5),
			new Bet(5, P, BetKind.COME, 10, 0, 0),
			new Bet(6, P, BetKind.FIELD, 10, 0, 0));
		RollResult r = CrapsResolver.apply(4, bets, 3, 4, R);
		assertEquals(Kind.SEVEN_OUT, r.event().kind());
		assertEquals(0, r.event().nextPoint());
		assertTrue(r.remaining().isEmpty());
		Map<Integer, Long> returns = new HashMap<>();
		r.resolutions().forEach(x -> returns.put(x.bet().id(), x.totalReturn()));
		assertEquals(Map.of(1, 0L, 2, 20L, 3, 0L, 4, 20L, 5, 20L, 6, 0L), returns);
	}

	@Test
	void applyRollKeepsMovedComeBetsWithTheirPoint() {
		Bet come = bet(BetKind.COME, 10);
		RollResult r = CrapsResolver.apply(4, List.of(come), 2, 4, R);
		assertEquals(1, r.remaining().size());
		assertEquals(6, r.remaining().get(0).point());
		assertEquals(0, come.point(), "input not mutated");
	}

	@Test
	void diceAreUniform() {
		SplittableRandom rnd = new SplittableRandom(1);
		int[] counts = new int[7];
		int n = 60_000;
		for (int i = 0; i < n; i++) {
			int[] d = CrapsResolver.rollDice(rnd::nextInt);
			counts[d[0]]++;
			counts[d[1]]++;
		}
		for (int f = 1; f <= 6; f++) {
			assertEquals(1.0 / 6, counts[f] / (2.0 * n), 0.01);
		}
		assertEquals(0, counts[0]);
	}

	@Test
	void autoCompleteResolvesEverything() {
		List<Bet> bets = List.of(
			new Bet(1, P, BetKind.PASS, 10, 50, 0),
			new Bet(2, P, BetKind.COME, 5, 0, 0),
			new Bet(3, P, BetKind.DONT_COME, 5, 6, 8),
			new Bet(4, P, BetKind.FIELD, 3, 0, 0));
		Map<Integer, Long> out = CrapsResolver.autoComplete(6, bets, new SplittableRandom(3)::nextInt, R);
		assertEquals(4, out.size());
		out.values().forEach(v -> assertTrue(v >= 0));
	}

	@Test
	void autoCompleteHasTheSameExpectedReturnAsPlayingOn() {
		SplittableRandom rnd = new SplittableRandom(99);
		double ret = 0;
		int n = 200_000;
		for (int i = 0; i < n; i++) {
			ret += CrapsResolver.autoComplete(0, List.of(bet(BetKind.PASS, 1)), rnd::nextInt, R).get(1);
		}
		assertEquals(1 - 7.0 / 495, ret / n, 0.006);
	}

	@Test
	void autoCompleteReturnsUnresolvedAsPushAtTheSafetyCap() {
		assertEquals(10L, CrapsResolver.autoComplete(0, List.of(bet(BetKind.PASS, 10)), new SplittableRandom(1)::nextInt, R, 0).get(1));
	}

	@Test
	void betKindIds() {
		for (BetKind k : BetKind.values()) {
			assertEquals(k, BetKind.byId(k.id()));
		}
		assertNull(BetKind.byId("hardways"));
		assertNull(BetKind.FIELD.oddsSide());
		assertEquals(OddsSide.LAY, BetKind.DONT_COME.oddsSide());
	}
}
