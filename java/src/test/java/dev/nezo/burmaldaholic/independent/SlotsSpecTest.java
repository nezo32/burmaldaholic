package dev.nezo.burmaldaholic.independent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.nezo.burmaldaholic.games.slots.logic.JackpotPool;
import dev.nezo.burmaldaholic.games.slots.logic.SlotEngine;
import dev.nezo.burmaldaholic.games.slots.logic.SlotTable;
import dev.nezo.burmaldaholic.games.slots.logic.Symbol;
import dev.nezo.burmaldaholic.games.slots.logic.Tier;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.SplittableRandom;
import org.junit.jupiter.api.Test;

/**
 * Independent re-derivation of GAME_DESIGN.md §8 / appendix A from the spec tables (own line evaluator,
 * own weights), then a cross-check of the dev {@link SlotEngine} over every possible payline.
 */
class SlotsSpecTest {
	/** Spec symbols of this test (own naming). S = special (pays only as 3 natural), W = wild. */
	private record Sym(String name, int weight, double pays, char kind) {}

	private static List<Sym> copper() {
		return List.of(new Sym("berries", 24, 10, 'R'), new Sym("apple", 20, 10, 'R'), new Sym("golden_carrot", 16, 20, 'R'),
			new Sym("emerald", 12, 30, 'R'), new Sym("diamond", 8, 60, 'R'), new Sym("seven", 5, 150, 'R'), new Sym("creeper", 15, 0, 'S'));
	}

	private static List<Sym> gold(double starPays) {
		return List.of(new Sym("berries", 22, 8, 'R'), new Sym("apple", 19, 7, 'R'), new Sym("golden_carrot", 16, 11, 'R'),
			new Sym("emerald", 12, 25, 'R'), new Sym("diamond", 8, 50, 'R'), new Sym("seven", 5, 100, 'R'), new Sym("wild", 3, 200, 'W'),
			new Sym("creeper", 8, 0, 'S'), new Sym("pearl", 5, 10, 'S'), new Sym("star", 2, starPays, 'S'));
	}

	private static List<Sym> netherite(double starPays) {
		return List.of(new Sym("berries", 20, 8, 'R'), new Sym("apple", 19, 9, 'R'), new Sym("golden_carrot", 16, 14, 'R'),
			new Sym("emerald", 12, 25, 'R'), new Sym("diamond", 9, 50, 'R'), new Sym("seven", 6, 100, 'R'), new Sym("wild", 3, 250, 'W'),
			new Sym("tnt", 6, 0, 'S'), new Sym("pearl", 5, 10, 'S'), new Sym("clock", 2, 50, 'S'), new Sym("star", 2, starPays, 'S'));
	}

	/** §8.1 line rule, written from the spec text. Returns {multiplier, label}. */
	private static Object[] line(Sym a, Sym b, Sym c) {
		if (a == b && b == c && a.kind == 'S') {
			return new Object[] {a.pays, "3 " + a.name};
		}
		if (a == b && b == c && a.kind == 'W') {
			return new Object[] {a.pays, "3 wild"};
		}
		double best = -1;
		String label = null;
		for (Sym s : List.of(a, b, c)) {
			if (s.kind != 'R') {
				continue;
			}
			boolean ok = (a == s || a.kind == 'W') && (b == s || b.kind == 'W') && (c == s || c.kind == 'W');
			if (ok && s.pays > best) {
				best = s.pays;
				label = "3 " + s.name;
			}
		}
		if (label != null) {
			return new Object[] {best, label};
		}
		if (a.name.equals("berries")) {
			return b.name.equals("berries") ? new Object[] {3.0, "2 berry"} : new Object[] {2.0, "1 berry"};
		}
		return new Object[] {0.0, null};
	}

	private record Stats(double rtp, double pNoWin, Map<String, Double> p) {}

	private static Stats stats(List<Sym> syms) {
		int total = syms.stream().mapToInt(Sym::weight).sum();
		assertEquals(100, total, "weights sum to 100");
		double rtp = 0;
		double none = 0;
		Map<String, Double> p = new LinkedHashMap<>();
		for (Sym a : syms) {
			for (Sym b : syms) {
				for (Sym c : syms) {
					double pr = (double) a.weight * b.weight * c.weight / ((double) total * total * total);
					Object[] r = line(a, b, c);
					rtp += pr * (double) r[0];
					if (r[1] != null) {
						p.merge((String) r[1], pr, Double::sum);
					}
					// appendix A counts zero-pay specials (Creeper, TNT) as "no win"; the star (jackpot) is a win
					if (r[1] == null || ((double) r[0] == 0 && !"3 star".equals(r[1]))) {
						none += pr;
					}
				}
			}
		}
		return new Stats(rtp, none, p);
	}

	@Test
	void copperMatchesSpec() {
		Stats s = stats(copper());
		assertEquals(0.89760, s.rtp, 5e-6, "Copper RTP 89.76 %");
		assertEquals(0.74554, s.pNoWin, 5e-6, "appendix A P(no win)");
		assertEquals(0.254, 1 - s.pNoWin, 5e-4, "hit frequency 25.4 %");
		assertEquals(0.182400, s.p.get("1 berry"), 1e-6);
		assertEquals(0.043776, s.p.get("2 berry"), 1e-6);
		assertEquals(0.013824, s.p.get("3 berries"), 1e-6);
		assertEquals(0.003375, s.p.get("3 creeper"), 1e-6);
		assertEquals(296, Math.round(1 / s.p.get("3 creeper")), "3 creepers 1 in 296 spins");
	}

	@Test
	void goldMatchesSpec() {
		Stats s = stats(gold(0));
		assertEquals(0.92713, s.rtp, 5e-6, "Gold base RTP");
		assertEquals(0.75541, s.pNoWin, 1e-5);
		assertEquals(0.245, 1 - s.pNoWin, 5e-4, "per-line hit 24.5 %");
		assertEquals(0.015598, s.p.get("3 berries"), 1e-6);
		assertEquals(0.010621, s.p.get("3 apple"), 1e-6);
		assertEquals(0.000008, s.p.get("3 star"), 1e-9);
		assertEquals(125_000, Math.round(1 / s.p.get("3 star")), "jackpot 1 in 125 000 lines");
		assertEquals(0.9371, s.rtp + 0.01, 5e-5, "+1 % jackpot contribution = 93.71 %");
		assertEquals(0.93513, stats(gold(1000)).rtp, 5e-6, "owned machine: 3 stars pay 1000× → 93.51 %");
	}

	@Test
	void netheriteMatchesSpec() {
		Stats s = stats(netherite(0));
		assertEquals(0.94535, s.rtp, 5e-6, "Netherite base RTP");
		assertEquals(0.77508, s.pNoWin, 2e-5);
		assertEquals(0.225, 1 - s.pNoWin, 5e-4, "per-line hit 22.5 %");
		assertEquals(0.012140, s.p.get("3 berries"), 1e-6);
		assertEquals(0.001701, s.p.get("3 diamond"), 1e-6);
		assertEquals(0.000702, s.p.get("3 seven"), 1e-6);
		assertEquals(0.000216, s.p.get("3 tnt"), 1e-6);
		assertEquals(0.9604, s.rtp + 0.015, 1e-4, "+1.5 % jackpot = 96.04 % (spec rounds 96.035 up)");
		assertEquals(0.95335, stats(netherite(1000)).rtp, 5e-6, "owned: 95.33 %");
	}

	/** Every payline triple through the dev engine must pay exactly what the spec rule says. */
	@Test
	void devEngineAgreesOnEveryLine() {
		for (Tier tier : Tier.values()) {
			for (boolean owned : new boolean[] {false, true}) {
				SlotTable table = SlotTable.defaults(tier, owned, 1000);
				List<Sym> spec = switch (tier) {
					case COPPER -> copper();
					case GOLD -> gold(owned ? 1000 : 0);
					case NETHERITE -> netherite(owned ? 1000 : 0);
				};
				double devRtp = 0;
				for (Sym a : spec) {
					for (Sym b : spec) {
						for (Sym c : spec) {
							Symbol sa = sym(a), sb = sym(b), sc = sym(c);
							assertEquals(a.weight, table.weight(sa), tier + " weight " + a.name);
							SlotEngine.LineResult r = SlotEngine.evaluateLine(sa, sb, sc, table);
							double devMult = r == null ? 0 : r.multiplier();
							double specMult = (double) line(a, b, c)[0];
							assertEquals(specMult, devMult, 1e-12, tier + (owned ? " owned " : " ") + a.name + "/" + b.name + "/" + c.name);
							devRtp += devMult * a.weight * b.weight * c.weight / 1e6;
						}
					}
				}
				assertTrue(devRtp < 0.99, tier + " RTP below 99 %");
			}
		}
	}

	private static Symbol sym(Sym s) {
		Symbol x = Symbol.byId(s.name);
		assertTrue(x != null, "symbol " + s.name);
		return x;
	}

	/** Full spins: line payouts are floored per line and summed; 3 stars on a progressive machine never pay a multiplier. */
	@Test
	void spinPayoutsAreFlooredPerLineAndSummed() {
		SlotTable gold = SlotTable.defaults(Tier.GOLD);
		Symbol B = Symbol.BERRIES, A = Symbol.APPLE, S = Symbol.STAR, W = Symbol.WILD, P = Symbol.PEARL;
		Symbol[][] grid = {{B, A, A}, {S, S, S}, {W, W, W}};
		SlotEngine.SpinEval e = SlotEngine.evaluate(grid, gold, 3);
		// top: 1 berry → 2×3 = 6; middle: jackpot (0); bottom: 3 wild 200×3 = 600
		assertEquals(606, e.basePayout());
		assertTrue(e.jackpotHit());
		assertEquals(Symbol.STAR, e.special());
		Symbol[][] pearls = {{P, P, P}, {Symbol.CREEPER, Symbol.CREEPER, Symbol.CREEPER}, {A, A, A}};
		SlotEngine.SpinEval e2 = SlotEngine.evaluate(pearls, gold, 1);
		assertEquals(10 + 0 + 7, e2.basePayout());
		assertEquals(Symbol.PEARL, e2.special(), "priority Pearl > Creeper");
	}

	@Test
	void monteCarloCopperWithinTolerance() {
		SlotTable copper = SlotTable.defaults(Tier.COPPER);
		SplittableRandom rnd = new SplittableRandom(42);
		long staked = 0, returned = 0;
		int n = 2_000_000;
		for (int i = 0; i < n; i++) {
			SlotEngine.SpinEval e = SlotEngine.spin(copper, 1, rnd::nextInt);
			staked += 1;
			returned += e.basePayout();
		}
		// σ per spin ≈ 1.9 → SE ≈ 0.0013 at 2M spins; 4 σ
		assertEquals(0.8976, (double) returned / staked, 0.006);
	}

	@Test
	void jackpotPoolContributionAndAward() {
		// §8.5: floor(spinBet × 1 %) with the fraction kept → 1000 spins of 3 chips add exactly 30.
		JackpotPool pool = JackpotPool.seeded(5000);
		long added = 0;
		for (int i = 0; i < 1000; i++) {
			JackpotPool.Contribution c = pool.contribute(3, 0.01);
			pool = c.state();
			added += c.added();
		}
		assertEquals(30, added);
		assertEquals(5030, pool.pool());
		// award = floor(pool × min(1, bet / max)); pool below seed is topped up by the bank
		JackpotPool.Payout half = pool.pay(150, 300, 5000);
		assertEquals(2515, half.award());
		assertEquals(5000, half.state().pool());
		assertEquals(2485, half.toppedUp());
		assertEquals(5030, pool.pay(300, 300, 5000).award(), "max bet wins the whole pool");
		assertEquals(5030, pool.pay(900, 300, 5000).award(), "never more than the pool");
	}
}
