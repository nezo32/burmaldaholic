package dev.nezo.burmaldaholic.games.poker.logic;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.nezo.burmaldaholic.core.bots.logic.BotEconomyMath;
import dev.nezo.burmaldaholic.games.poker.logic.Hand.Action;
import java.util.List;
import java.util.SplittableRandom;
import org.junit.jupiter.api.Test;

/** Rake without bot chips, heat attribution and VIP share (BOTS.md §5.1, §5.3, §5.4, §12.5) — Bedrock {@code money.test.ts}. */
class PokerMoneyTest {
	private static final Pots.RakeConfig RAKE = new Pots.RakeConfig(0.05, 3, true);

	/** A finished result with one pot: {@code paid} per player, {@code winners} share {@code shares}. */
	private static Hand.Result finished(long[] paid, List<Integer> winners, long[] shares) {
		long pot = 0;
		for (long p : paid) {
			pot += p;
		}
		Hand.PotResult pr = new Hand.PotResult(pot, winners, List.of(), 0, winners, shares, 1, paid);
		return new Hand.Result(false, null, List.of(pr), new long[paid.length], new long[paid.length], new int[paid.length], List.of(), 0);
	}

	@Test
	void theEngineRakesAMixedPotOnTheHumanChipsOnly() {
		// seats: 0 = human h (button), 1 = bot (SB), 2 = human g (BB); h makes a royal flush
		int[] deck = HandTest.rig(3, 0, new String[] {"As Ks", "7c 8d", "2c 3d"}, "Qs Js Ts 5h 9h");
		Hand s = new Hand(List.of(new Hand.Seed("h", true, 1000), new Hand.Seed("bot", false, 1000), new Hand.Seed("g", true, 1000)),
			PokerRng.of(new SplittableRandom(1)), new Hand.Options(5, 10, 0, RAKE, deck));
		s.apply(Action.raiseTo(100)); // h
		s.apply(Action.call()); // bot
		s.apply(Action.call()); // g
		while (!s.complete()) {
			s.apply(Action.check());
		}
		Hand.Result r = s.result();
		assertEquals(1, r.pots().size());
		assertArrayEquals(new long[] {100, 100, 100}, r.pots().getFirst().paid());
		assertEquals(10, r.rake(), "min(floor((300 − 100 bot chips) × 5 %), 30)");
		assertEquals(290, r.won()[0]);
		PokerMoney.Attribution a = PokerMoney.attribute(s, i -> i == 1, i -> i == 1);
		assertEquals(96, a.houseNet()[0], "floor(290 × 100 / 300)");
		assertEquals(0, a.houseNet()[2], "g: floor(100 × 0 / 300)");
		assertEquals(0.5, a.botShare()[0], 1e-9);
	}

	@Test
	void headsUpVsAHouseBot() {
		assertEquals(100, PokerMoney.attribute(finished(new long[] {100, 100}, List.of(0), new long[] {200}), 2, i -> i == 1, i -> i == 1).houseNet()[0]);
		assertEquals(-100, PokerMoney.attribute(finished(new long[] {100, 100}, List.of(1), new long[] {200}), 2, i -> i == 1, i -> i == 1).houseNet()[0]);
	}

	@Test
	void ownerFundedBotsAreNotTrackedForHeatButCountForTheVipShare() {
		PokerMoney.Attribution a = PokerMoney.attribute(finished(new long[] {100, 100}, List.of(0), new long[] {200}), 2, i -> i == 1, i -> false);
		assertEquals(0, a.houseNet()[0]);
		assertEquals(1, a.botShare()[0], 1e-9);
	}

	@Test
	void vipVector() {
		// h put in 400, matched 300 by bots and 100 by a human → share 0.75, credit floor(400 × (1 − 0.5 × 0.75)) = 250
		PokerMoney.Attribution a = PokerMoney.attribute(finished(new long[] {400, 300, 100}, List.of(2), new long[] {800}), 3, i -> i == 1, i -> i == 1);
		assertEquals(0.75, a.botShare()[0], 1e-9);
		assertEquals(250, BotEconomyMath.vipCredit(400, 0.5, a.botShare()[0]));
		assertEquals(0, a.botShare()[1], 1e-9, "bots have no share");
	}
}
