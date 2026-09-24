package dev.nezo.burmaldaholic.games.extras.pvp.wheel;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.nezo.burmaldaholic.core.bots.logic.BotDifficulty;
import dev.nezo.burmaldaholic.core.bots.logic.BotRng;
import dev.nezo.burmaldaholic.core.pvp.logic.DecisionView;
import dev.nezo.burmaldaholic.core.pvp.logic.Outcome;
import dev.nezo.burmaldaholic.core.pvp.logic.PvpEvent;
import dev.nezo.burmaldaholic.core.pvp.logic.PvpMath;
import dev.nezo.burmaldaholic.core.pvp.logic.PvpRng;
import dev.nezo.burmaldaholic.core.pvp.logic.Step;
import java.util.List;
import java.util.SplittableRandom;
import org.junit.jupiter.api.Test;

/** PVP.md §16.4 W1–W6, §16.8 B1/B3 and BOTS.md §12.3 (Wheel Party vs a HARD whale); same vectors as Bedrock. */
class WheelPartyModeTest {
	private static final WheelPartyMode MODE = new WheelPartyMode();
	private static final long[] STAKES = {50, 150, 800};
	private static final int[] SEAT = {0, 1, 2};

	private static PvpRng fair(long seed) {
		return PvpRng.of(new SplittableRandom(seed));
	}

	private static PvpEvent event(Outcome o, String kind) {
		return o.events().stream().filter(e -> e.kind().equals(kind)).findFirst().orElse(null);
	}

	@Test
	void w1SliceOwners() {
		long[] us = {0, 49, 50, 199, 200, 999};
		int[] owners = {0, 0, 1, 1, 2, 2};
		for (int i = 0; i < us.length; i++) {
			Outcome o = WheelPartyMode.scoreAt(us[i], STAKES, SEAT, 1000);
			assertArrayEquals(new int[] {owners[i]}, o.winners(), "u " + us[i]);
			assertEquals(owners[i], o.rankOrder()[0]);
			assertEquals(STAKES[owners[i]], o.points()[owners[i]]); // points = slices (Bedrock parity)
			assertEquals(us[i], event(o, "spin").data().get("u"));
		}
	}

	@Test
	void spinPointMapsTheTapeOntoThePot() {
		long top = WheelMath.SPIN_RESOLUTION - 1;
		for (long pot : new long[] {1, 2, 3, 1000, 999_983, 8L * 1_000_000_000L}) {
			assertEquals(0, WheelMath.spinPoint(0, pot));
			assertEquals(pot - 1, WheelMath.spinPoint(top, pot));
			// the first r of every u: ceil(u × 2^53 / P) — u's preimage starts exactly there
			for (long u : new long[] {1, pot / 2, pot - 1}) {
				if (u <= 0 || u >= pot) {
					continue;
				}
				java.math.BigInteger first = java.math.BigInteger.valueOf(u).shiftLeft(53).add(java.math.BigInteger.valueOf(pot - 1))
					.divide(java.math.BigInteger.valueOf(pot));
				assertEquals(u, WheelMath.spinPoint(first.longValueExact(), pot));
				assertEquals(u - 1, WheelMath.spinPoint(first.longValueExact() - 1, pot));
			}
		}
		assertThrows(IllegalArgumentException.class, () -> WheelMath.spinPoint(WheelMath.SPIN_RESOLUTION, 10));
		assertThrows(IllegalArgumentException.class, () -> WheelMath.spinPoint(0, 0));
	}

	@Test
	void w2MonteCarloShares() {
		long[] stakes = {10, 30, 60};
		PvpRng rng = fair(0x3EE1L);
		int n = 1_000_000;
		int[] wins = new int[3];
		for (int i = 0; i < n; i++) {
			WheelPartyMode.Tape t = MODE.draw(rng, 3, new WheelPartyMode.Params(100));
			wins[WheelPartyMode.score(t, stakes, 1000).winners()[0]]++;
		}
		assertEquals(0.1, wins[0] / (double) n, 0.002);
		assertEquals(0.3, wins[1] / (double) n, 0.002);
		assertEquals(0.6, wins[2] / (double) n, 0.002);
	}

	@Test
	void w3ExactEv() {
		// Lemma 2 formula, and the same by summing the settlement over every u in [0, P).
		double[] want = {-1.5, -4.5, -24};
		long pot = PvpMath.pot(STAKES);
		long w = pot - PvpMath.rake(pot, 300);
		long[] won = new long[3];
		for (long u = 0; u < pot; u++) {
			won[WheelMath.winner(STAKES, u)] += w;
		}
		for (int i = 0; i < 3; i++) {
			long[] ev = WheelMath.expectedValue(STAKES, i, 300);
			assertEquals(want[i], ev[0] / (double) ev[1], 1e-12);
			// Σ_u payout / P − s_i, exactly as a fraction
			assertEquals(ev[0] * pot, (won[i] - STAKES[i] * pot) * ev[1]);
		}
	}

	@Test
	void w4ByAHair() {
		assertEquals(20, WheelMath.hairWindow(1000));
		assertEquals(1, WheelMath.byAHair(STAKES, 219)); // 19 from 200 → names P2
		assertEquals(-1, WheelMath.byAHair(STAKES, 221));
		assertEquals(0, WheelMath.byAHair(STAKES, 995)); // wraps to P1
		assertEquals(1, WheelMath.byAHair(STAKES, 30)); // 19 from 50 → names P2
		Outcome o = WheelPartyMode.scoreAt(219, STAKES, SEAT, 1000);
		assertEquals(1, event(o, "by_a_hair").seat());
		assertNull(event(WheelPartyMode.scoreAt(221, STAKES, SEAT, 1000), "by_a_hair"));
		// two players, boundary wrap never names the winner itself
		assertEquals(1, WheelMath.byAHair(new long[] {500, 500}, 0));
		assertEquals(0, WheelMath.byAHair(new long[] {500, 500}, 999));
	}

	@Test
	void w5Underdog() {
		assertTrue(WheelMath.underdog(100, 1000, 1000));
		assertFalse(WheelMath.underdog(101, 1000, 1000));
		Outcome o = WheelPartyMode.scoreAt(10, new long[] {100, 900}, new int[] {1, 0}, 1000);
		PvpEvent e = event(o, "underdog");
		assertEquals(0, e.seat());
		assertEquals(1000L, e.data().get("shareBp"));
		assertNull(event(WheelPartyMode.scoreAt(10, new long[] {101, 899}, new int[] {1, 0}, 1000), "underdog"));
		assertNull(event(WheelPartyMode.scoreAt(500, new long[] {100, 900}, new int[] {1, 0}, 1000), "underdog"));
	}

	@Test
	void w6StakeAndTopUpChecks() {
		// cap 1000, tier max 5000, min 10
		assertNull(WheelMath.checkStake(0, 1000, 1000, 5000, 10, false));
		assertEquals(WheelMath.ERROR_OVER_CAP, WheelMath.checkStake(900, 101, 1000, 5000, 10, false));
		assertEquals(WheelMath.ERROR_TIER_MAX, WheelMath.checkStake(400, 200, 1000, 500, 10, false));
		assertEquals(WheelMath.ERROR_NO_MORE_BETS, WheelMath.checkStake(100, 10, 1000, 5000, 10, true));
		assertEquals(WheelMath.ERROR_STAKE_MIN, WheelMath.checkStake(0, 9, 1000, 5000, 10, false));
		assertEquals(WheelMath.ERROR_STAKE_MIN, WheelMath.checkStake(100, 0, 1000, 5000, 10, false));
		assertNull(WheelMath.checkStake(100, 1, 1000, 5000, 10, false)); // a top-up may be below the minimum
		assertEquals("gui.burmaldaholic.pvp.error.over_cap", WheelMath.ERROR_OVER_CAP);
		assertEquals("gui.burmaldaholic.pvp.error.no_more_bets", WheelMath.ERROR_NO_MORE_BETS);
		assertEquals(WheelMath.ERROR_STAKE_MIN, WheelPartyMode.validate(new WheelPartyMode.Params(9), 10));
		assertNull(WheelPartyMode.validate(new WheelPartyMode.Params(10), 10));
	}

	@Test
	void timelineAndJson() {
		WheelPartyMode.Params p = new WheelPartyMode.Params(1000);
		WheelPartyMode.Tape t = MODE.draw(fair(5), 3, p);
		Outcome o = WheelPartyMode.score(t, STAKES, 1000);
		List<Step> steps = MODE.timeline(t, o, p);
		assertEquals(List.of("spin", "result"), steps.stream().map(Step::kind).toList());
		assertEquals(100, steps.get(0).ticks());
		assertEquals(o.winners()[0], steps.get(0).data().get("winner").getAsInt());
		long u = steps.get(0).data().get("u").getAsLong();
		assertEquals(Math.round(WheelMath.pointerDegrees(u, 1000) * 1000), steps.get(0).data().get("angle1000").getAsLong());
		WheelPartyMode.Tape back = MODE.decodeTape(MODE.encodeTape(t));
		assertEquals(t.r(), back.r());
		assertArrayEquals(t.seatOrder(), back.seatOrder());
		assertEquals(p, MODE.decodeParams(MODE.encodeParams(p)));
		// 16 players: still tiny
		assertTrue(MODE.encodeTape(MODE.draw(fair(6), 16, p)).toString().length() < 120);
		assertEquals(180.0, WheelMath.pointerDegrees(499, 1000) + 0.18, 1e-9);
	}

	private static DecisionView stakeView(long median) {
		return new DecisionView("wheel.stake", 1, 0, 0, 10, 800, 0, median, 400);
	}

	@Test
	void botStakesBots48() {
		BotRng rng = BotRng.seeded(7);
		assertEquals(10, MODE.botDecide(stakeView(100), BotDifficulty.EASY, rng));
		assertEquals(100, MODE.botDecide(stakeView(100), BotDifficulty.NORMAL, rng));
		assertEquals(800, MODE.botDecide(stakeView(100), BotDifficulty.HARD, rng));
		assertEquals(800, MODE.botDecide(stakeView(5000), BotDifficulty.NORMAL, rng)); // clamped to the cap
		for (int i = 0; i < 1000; i++) {
			long s = MODE.botDecide(stakeView(0), BotDifficulty.NORMAL, rng); // no human stake yet: 20–60 % of the cap
			assertTrue(s >= 160 && s <= 480, "stake " + s);
		}
		// top-ups: Steady never, Cool-headed to the cap, Wild sometimes the minimum; never past the cap
		DecisionView top = new DecisionView("wheel.top_up", 1, 0, 0, 10, 800, 795, 100, 100);
		assertEquals(0, MODE.botDecide(top, BotDifficulty.NORMAL, rng));
		assertEquals(5, MODE.botDecide(top, BotDifficulty.HARD, rng));
		int easyTops = 0;
		for (int i = 0; i < 10_000; i++) {
			long x = MODE.botDecide(new DecisionView("wheel.top_up", 1, 0, 0, 10, 800, 100, 100, 100), BotDifficulty.EASY, rng);
			assertTrue(x == 0 || x == 10);
			easyTops += x > 0 ? 1 : 0;
		}
		assertEquals(0.25, easyTops / 10_000.0, 0.02);
		for (BotDifficulty d : new BotDifficulty[] {BotDifficulty.EASY, BotDifficulty.NORMAL, BotDifficulty.HARD}) {
			int delay = WheelMath.botJoinDelay(d, 600, 60, rng);
			assertTrue(delay >= 1 && delay < 540);
		}
		assertEquals(100, WheelMath.median(new long[] {300, 50, 100}));
		assertEquals(50, WheelMath.median(new long[] {100, 50}));
		assertEquals(0, WheelMath.median(new long[0]));
	}

	@Test
	void b1TapesIgnoreBots() {
		// Same fair seed, bots of different styles: identical r and seat order (bots use their own rng).
		PvpRng a = fair(11);
		PvpRng b = fair(11);
		BotRng ba = BotRng.seeded(1);
		BotRng bb = BotRng.seeded(2);
		for (int i = 0; i < 1000; i++) {
			MODE.botDecide(stakeView(0), BotDifficulty.EASY, ba);
			MODE.botDecide(stakeView(0), BotDifficulty.NORMAL, bb);
			MODE.botDecide(stakeView(0), BotDifficulty.NORMAL, bb);
			WheelPartyMode.Tape ta = MODE.draw(a, 4, new WheelPartyMode.Params(800));
			WheelPartyMode.Tape tb = MODE.draw(b, 4, new WheelPartyMode.Params(800));
			assertEquals(ta.r(), tb.r());
			assertArrayEquals(ta.seatOrder(), tb.seatOrder());
		}
	}

	@Test
	void b3HumanWinShareMatchesTheSliceForEveryStyle() {
		for (BotDifficulty d : new BotDifficulty[] {BotDifficulty.EASY, BotDifficulty.NORMAL, BotDifficulty.HARD}) {
			PvpRng rng = fair(0xB3L + d.ordinal());
			BotRng bots = BotRng.seeded(0xB07L ^ d.ordinal());
			int n = 1_000_000;
			int wins = 0;
			double expected = 0;
			for (int i = 0; i < n; i++) {
				int nBots = 1 + (i % 3);
				long[] stakes = new long[nBots + 1];
				stakes[0] = 100; // the human, joined first
				for (int k = 1; k <= nBots; k++) {
					stakes[k] = MODE.botDecide(stakeView(100), d, bots);
					stakes[k] += WheelMath.botTopUp(d, 10, 800, stakes[k], bots);
				}
				long pot = PvpMath.pot(stakes);
				expected += 100.0 / pot;
				WheelPartyMode.Tape t = MODE.draw(rng, stakes.length, new WheelPartyMode.Params(800));
				if (WheelPartyMode.score(t, stakes, 1000).winners()[0] == 0) {
					wins++;
				}
				// EV per chip = −R/P exactly, for every party
				long[] ev = WheelMath.expectedValue(stakes, 0, 300);
				assertEquals(-PvpMath.rake(pot, 300) * 100, ev[0]);
			}
			assertEquals(expected / n, wins / (double) n, 0.002, d.name());
		}
	}

	@Test
	void hardWhaleBots123() {
		// BOTS.md §12.3: human 100 vs a HARD bot at cap 800 → human wins 1/9 = 11.1 % ± 0.1 %, EV −3.0
		long bot = WheelMath.botStake(BotDifficulty.HARD, 10, 800, 100, BotRng.seeded(3));
		long[] stakes = {100, bot};
		assertEquals(800, bot);
		PvpRng rng = fair(0x3A1EL);
		int n = 2_000_000;
		int wins = 0;
		for (int i = 0; i < n; i++) {
			if (WheelPartyMode.score(MODE.draw(rng, 2, new WheelPartyMode.Params(800)), stakes, 1000).winners()[0] == 0) {
				wins++;
			}
		}
		assertEquals(1 / 9.0, wins / (double) n, 0.001);
		long[] ev = WheelMath.expectedValue(stakes, 0, 300);
		assertEquals(-3.0, ev[0] / (double) ev[1], 0.5);
	}
}
