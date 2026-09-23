package dev.nezo.burmaldaholic.games.slots.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.nezo.burmaldaholic.core.config.sections.SlotsConfig;
import dev.nezo.burmaldaholic.core.rng.OddsContext;
import dev.nezo.burmaldaholic.core.rng.OddsService;
import dev.nezo.burmaldaholic.core.rng.StreakRules;
import java.util.SplittableRandom;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Exact enumeration (§8.2–8.4, Appendix A) and Monte-Carlo checks (§17: |RTP − expected| &lt; 0.3 %). */
class SlotRtpTest {
	private static final double P = 6e-7;

	@Test
	void copperBandit() {
		SlotRtp.LineStats s = SlotRtp.lineStats(SlotTable.defaults(Tier.COPPER));
		assertEquals(0.8976, s.baseRtp(), 1e-5);
		assertEquals(0.1824, s.p("berry1"), P);
		assertEquals(0.043776, s.p("berry2"), P);
		assertEquals(0.013824, s.p("three:berries"), P);
		assertEquals(0.008, s.p("three:apple"), P);
		assertEquals(0.004096, s.p("three:golden_carrot"), P);
		assertEquals(0.001728, s.p("three:emerald"), P);
		assertEquals(0.000512, s.p("three:diamond"), P);
		assertEquals(0.000125, s.p("three:seven"), P);
		assertEquals(0.003375, s.p("special:creeper"), P);
		assertEquals(0.74554, s.pNoWin(), 1e-5);
		assertEquals(0.254, s.hitRate(), 1e-3);
		assertEquals(296, Math.round(1 / s.p("special:creeper")));
		assertEquals(0.8976, SlotRtp.totalRtp(SlotTable.defaults(Tier.COPPER), 0.01), 1e-4); // no progressive
	}

	@Test
	void goldenReels() {
		SlotTable t = SlotTable.defaults(Tier.GOLD);
		SlotRtp.LineStats s = SlotRtp.lineStats(t);
		assertEquals(0.92713, s.baseRtp(), 1e-5);
		assertEquals(0.16995, s.p("berry1"), P);
		assertEquals(0.0363, s.p("berry2"), P);
		assertEquals(0.015598, s.p("three:berries"), P);
		assertEquals(0.010621, s.p("three:apple"), P);
		assertEquals(0.006832, s.p("three:golden_carrot"), P);
		assertEquals(0.003348, s.p("three:emerald"), P);
		assertEquals(0.001304, s.p("three:diamond"), P);
		assertEquals(0.000485, s.p("three:seven"), P);
		assertEquals(0.000027, s.p("wild"), P);
		assertEquals(0.000125, s.p("special:pearl"), P);
		assertEquals(0.000512, s.p("special:creeper"), P);
		assertEquals(0.000008, s.p("special:star"), P);
		assertEquals(0.75541, s.pNoWin(), 1e-5);
		assertEquals(125_000, Math.round(1 / s.pJackpot()));
		assertEquals(0.9371, SlotRtp.totalRtp(t, 0.01), 1e-4);
	}

	@Test
	void netheriteHighRoller() {
		SlotTable t = SlotTable.defaults(Tier.NETHERITE);
		SlotRtp.LineStats s = SlotRtp.lineStats(t);
		assertEquals(0.94535, s.baseRtp(), 1e-5);
		assertEquals(0.15862, s.p("berry1"), P);
		assertEquals(0.0308, s.p("berry2"), P);
		assertEquals(0.01214, s.p("three:berries"), P);
		assertEquals(0.010621, s.p("three:apple"), P);
		assertEquals(0.006832, s.p("three:golden_carrot"), P);
		assertEquals(0.003348, s.p("three:emerald"), P);
		assertEquals(0.001701, s.p("three:diamond"), P);
		assertEquals(0.000702, s.p("three:seven"), P);
		assertEquals(0.000027, s.p("wild"), P);
		assertEquals(0.000125, s.p("special:pearl"), P);
		assertEquals(0.000008, s.p("special:clock"), P);
		assertEquals(0.000216, s.p("special:tnt"), P);
		assertEquals(0.000008, s.p("special:star"), P);
		assertEquals(0.77508, s.pNoWin(), 1e-5);
		// the spec rounds 0.960347 to 96.04 %
		assertEquals(0.9604, SlotRtp.totalRtp(t, 0.015), 1e-4);
	}

	@Test
	void ownedMachines() {
		assertEquals(0.9351, SlotRtp.totalRtp(SlotTable.defaults(Tier.GOLD, true, 1000), 0.01), 1e-4);
		assertEquals(0.9534, SlotRtp.totalRtp(SlotTable.defaults(Tier.NETHERITE, true, 1000), 0.015), 1e-4);
	}

	@Test
	void configDefaultsMatchTheSpecTables() {
		SlotsConfig c = new SlotsConfig();
		assertEquals(SlotTable.defaults(Tier.COPPER).toString(),
			SlotTable.of(c.copper.weights, c.copper.pays, c.copper.berryPartial, 1, false).toString());
		assertEquals(SlotTable.defaults(Tier.GOLD).toString(), SlotTable.of(c.gold.weights, c.gold.pays, c.gold.berryPartial, 3, true).toString());
		assertEquals(SlotTable.defaults(Tier.NETHERITE).toString(),
			SlotTable.of(c.netherite.weights, c.netherite.pays, c.netherite.berryPartial, 5, true).toString());
	}

	@Test
	void emptyTableIsSafe() {
		SlotRtp.LineStats s = SlotRtp.lineStats(SlotTable.of(java.util.Map.of(), java.util.Map.of(), null, 1, false));
		assertEquals(0, s.baseRtp());
		assertEquals(1, s.pNoWin());
	}

	// ---- Monte-Carlo ------------------------------------------------------------------------------

	private record Sim(double rtp, double sd) {}

	private static Sim simulate(Tier tier, int spins, long seed, OddsService odds, UUID player, double rtpForStreak) {
		SlotTable table = SlotTable.defaults(tier);
		var rng = odds.rng(new OddsContext(player, "slots", tier.lines()));
		long staked = 0;
		long returned = 0;
		long spinBet = table.lines();
		for (int i = 0; i < spins; i++) {
			SlotEngine.SpinEval e = odds.play(new OddsContext(player, "slots", spinBet), rtpForStreak,
				() -> SlotEngine.spin(table, 1, rng::nextInt), r -> SlotEngine.losing(r, spinBet));
			staked += spinBet;
			returned += e.basePayout();
		}
		SlotRtp.LineStats s = SlotRtp.lineStats(table);
		// lines are identically distributed; diagonals share cells, so bound with full correlation
		double lineSd = Math.sqrt(s.secondMoment() - s.baseRtp() * s.baseRtp());
		return new Sim(returned / (double) staked, lineSd / Math.sqrt(spins));
	}

	private static OddsService odds(long seed, int streak) {
		OddsService o = new OddsService(new SplittableRandom(seed));
		o.setStreakSource(id -> streak, () -> StreakRules.Settings.DEFAULTS);
		return o;
	}

	@Test
	void monteCarloMatchesExactRtpForEveryTier() {
		for (Tier tier : Tier.values()) {
			double exact = SlotRtp.lineStats(SlotTable.defaults(tier)).baseRtp();
			int spins = tier == Tier.NETHERITE ? 20_000_000 : 10_000_000;
			Sim sim = simulate(tier, spins, 12345 + tier.ordinal(), odds(12345 + tier.ordinal(), 0), UUID.randomUUID(), exact);
			double diff = Math.abs(sim.rtp() - exact);
			assertTrue(diff < 0.003, tier + ": simulated " + sim.rtp() + " vs exact " + exact);
			assertTrue(diff < 5 * sim.sd(), tier + ": " + diff + " > 5 sd " + sim.sd());
		}
	}

	@Test
	void streakRedrawAtItsCapKeepsTheHouseEdgeAboveOnePercent() {
		SlotTable copper = SlotTable.defaults(Tier.COPPER);
		double rtp = SlotRtp.totalRtp(copper, 0);
		OddsService o = odds(99, 10);
		UUID p = UUID.randomUUID();
		assertEquals(0.05, o.redrawProbability(p, rtp), 1e-9); // full 5 % applies (cap 0.103)
		Sim sim = simulate(Tier.COPPER, 2_000_000, 99, o, p, rtp);
		assertTrue(sim.rtp() > SlotRtp.lineStats(copper).baseRtp());
		assertTrue(sim.rtp() < 0.99);
		// the expected boosted RTP is at most RTP × (1 + r)
		assertTrue(sim.rtp() < rtp * 1.05 + 0.005);
	}
}
