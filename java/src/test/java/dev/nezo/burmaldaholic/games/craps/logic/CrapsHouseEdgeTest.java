package dev.nezo.burmaldaholic.games.craps.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.nezo.burmaldaholic.games.craps.logic.BetResolution.Outcome;
import dev.nezo.burmaldaholic.games.craps.logic.CrapsResolver.RollResult;
import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;
import java.util.UUID;
import java.util.function.BiFunction;
import org.junit.jupiter.api.Test;

/**
 * House edge per GAME_DESIGN §10.1 / §17: exact first-step analysis of the resolver and a
 * Monte-Carlo run of the table rules (1 M bets each).
 */
class CrapsHouseEdgeTest {
	static final CrapsRules R = CrapsRules.DEFAULT;
	static final UUID P = UUID.randomUUID();
	static final int[] WAYS = {0, 0, 1, 2, 3, 4, 5, 6, 5, 4, 3, 2, 1};

	static double prob(int total) {
		return WAYS[total] / 36.0;
	}

	static Bet bet(BetKind kind, long flat, long odds, int point) {
		return new Bet(1, P, kind, flat, odds, point);
	}

	/** Exact expected TOTAL return of a bet from a table state (puck position {@code tp}). */
	static double exact(Bet b, int tp, CrapsRules rules, BiFunction<Bet, Integer, Bet> attachOdds, int depth) {
		if (depth > 4) {
			throw new IllegalStateException("state cycle");
		}
		double acc = 0;
		double loop = 0;
		for (int t = 2; t <= 12; t++) {
			BetResolution r = CrapsResolver.resolve(b, tp, t, rules);
			int np = RollEvent.of(tp, t).nextPoint();
			if (r.outcome().resolved()) {
				acc += prob(t) * r.totalReturn();
			} else if (r.outcome() == Outcome.MOVE) {
				acc += prob(t) * exact(bet(b.kind(), b.flat(), b.odds(), r.movedTo()), 4, rules, attachOdds, depth + 1);
			} else if (b.kind().isCome() && b.point() != 0) {
				loop += prob(t); // come bet waiting on its own point; table point irrelevant (no odds)
			} else if (np == tp) {
				loop += prob(t);
			} else {
				Bet next = attachOdds != null && np != 0 ? attachOdds.apply(b, np) : b;
				acc += prob(t) * exact(next, np, rules, attachOdds, depth + 1);
			}
		}
		return acc / (1 - loop);
	}

	static double exact(Bet b, int tp, CrapsRules rules) {
		return exact(b, tp, rules, null, 0);
	}

	@Test
	void passAndCome141() {
		assertEquals(-7.0 / 495, exact(bet(BetKind.PASS, 1, 0, 0), 0, R) - 1, 1e-12);
		assertEquals(-7.0 / 495, exact(bet(BetKind.COME, 1, 0, 0), 4, R) - 1, 1e-12);
		assertEquals(-0.0141, -7.0 / 495, 0.00005);
	}

	@Test
	void dontPassAndDontCome136() {
		assertEquals(-3.0 / 220, exact(bet(BetKind.DONT_PASS, 1, 0, 0), 0, R) - 1, 1e-12);
		assertEquals(-3.0 / 220, exact(bet(BetKind.DONT_COME, 1, 0, 0), 4, R) - 1, 1e-12);
		assertEquals(-0.0136, -3.0 / 220, 0.00005);
	}

	@Test
	void field278And556WhenTwelvePaysDouble() {
		assertEquals(-1.0 / 36, exact(bet(BetKind.FIELD, 1, 0, 0), 0, R) - 1, 1e-12);
		assertEquals(-2.0 / 36, exact(bet(BetKind.FIELD, 1, 0, 0), 0, new CrapsRules(2, 2, 3, 4, 5)) - 1, 1e-12);
	}

	@Test
	void oddsHaveNoEdge() {
		for (int p : CrapsMath.POINT_NUMBERS) {
			long take = 10L * CrapsMath.oddsUnit(OddsSide.TAKE, p);
			assertEquals(take, exact(bet(BetKind.PASS, 0, take, 0), p, R), 1e-9);
			long lay = 10L * CrapsMath.oddsUnit(OddsSide.LAY, p);
			assertEquals(lay, exact(bet(BetKind.DONT_PASS, 0, lay, 0), p, R), 1e-9);
		}
	}

	@Test
	void passWithFull345OddsIsAbout037Percent() {
		long flat = 10;
		double ret = exact(bet(BetKind.PASS, flat, 0, 0), 0, R,
			(b, p) -> bet(b.kind(), b.flat(), CrapsMath.maxOdds(OddsSide.TAKE, p, flat, R), b.point()), 0);
		double wagered = flat;
		for (int p : CrapsMath.POINT_NUMBERS) {
			wagered += prob(p) * CrapsMath.maxOdds(OddsSide.TAKE, p, flat, R);
		}
		assertEquals(0.00374, (wagered - ret) / wagered, 0.0001);
	}

	// ---- Monte-Carlo --------------------------------------------------------------------------

	static double rtp(BetKind kind, long seed, boolean withOdds) {
		SplittableRandom rnd = new SplittableRandom(seed);
		long staked = 0;
		long returned = 0;
		for (int i = 0; i < 1_000_000; i++) {
			List<Bet> live = new ArrayList<>(List.of(bet(kind, 10, 0, 0)));
			// Come / Don't Come are placed with a point on (table point 4 stands in for "puck ON").
			int point = kind.isCome() ? 4 : 0;
			staked += 10;
			while (!live.isEmpty()) {
				int[] d = CrapsResolver.rollDice(rnd::nextInt);
				RollResult r = CrapsResolver.apply(point, live, d[0], d[1], R);
				for (BetResolution x : r.resolutions()) {
					if (x.outcome().resolved()) {
						returned += x.totalReturn();
					}
				}
				live = new ArrayList<>(r.remaining());
				point = kind.isCome() ? (r.event().nextPoint() == 0 ? 4 : r.event().nextPoint()) : r.event().nextPoint();
				if (withOdds && r.event().kind() == RollEvent.Kind.POINT_SET && !live.isEmpty()) {
					long o = CrapsMath.maxOdds(kind == BetKind.PASS ? OddsSide.TAKE : OddsSide.LAY, r.event().point(), 10, R);
					live.get(0).addOdds(o);
					staked += o;
				}
			}
		}
		return (double) returned / staked;
	}

	@Test
	void monteCarloPass() {
		assertEquals(0.98586, rtp(BetKind.PASS, 11, false), 0.003);
	}

	@Test
	void monteCarloDontPass() {
		assertEquals(0.98636, rtp(BetKind.DONT_PASS, 12, false), 0.003);
	}

	@Test
	void monteCarloCome() {
		assertEquals(0.98586, rtp(BetKind.COME, 13, false), 0.003);
	}

	@Test
	void monteCarloDontCome() {
		assertEquals(0.98636, rtp(BetKind.DONT_COME, 14, false), 0.003);
	}

	@Test
	void monteCarloField() {
		assertEquals(0.97222, rtp(BetKind.FIELD, 15, false), 0.003);
	}

	@Test
	void monteCarloPassWithOdds() {
		assertEquals(0.99626, rtp(BetKind.PASS, 16, true), 0.003);
	}

	@Test
	void monteCarloDontPassWithLayOdds() {
		// Lay odds are fair: the flat bet's loss (10 × 3/220) spread over 10 + 2/3 × 60 wagered → 99.73 %.
		assertEquals(1 - (10 * 3.0 / 220) / 50, rtp(BetKind.DONT_PASS, 17, true), 0.003);
	}
}
