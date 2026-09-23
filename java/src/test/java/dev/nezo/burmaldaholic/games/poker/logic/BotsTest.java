package dev.nezo.burmaldaholic.games.poker.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.nezo.burmaldaholic.games.poker.logic.Bots.Tier;
import dev.nezo.burmaldaholic.games.poker.logic.Hand.Action;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.SplittableRandom;
import org.junit.jupiter.api.Test;

class BotsTest {
	private static int chen(String hand) {
		int[] c = Cards.parseAll(hand);
		return Equity.chen(c[0], c[1]);
	}

	@Test
	void chenScores() {
		assertEquals(20, chen("As Ad"));
		assertEquals(16, chen("Ks Kd"));
		assertEquals(12, chen("As Ks"), "AKs: 10 + 2");
		assertEquals(10, chen("Ah Kd"));
		assertEquals(5, chen("2s 2d"), "pairs score at least 5");
		assertEquals(7, chen("7s 7d"));
		assertEquals(-1, chen("7s 2d"), "3.5 − 5 = −1.5 → −1");
		assertEquals(9, chen("Js Ts"), "6 + 2 (suited) + 1 (connector below Q)");
		assertEquals(6, chen("5h 4h"), "2.5 + 2 + 1 = 5.5 → 6");
	}

	@Test
	void chenThresholdTop40() {
		int t = Equity.top40Chen();
		int count = 0;
		for (int i = 0; i < 52; i++) {
			for (int j = i + 1; j < 52; j++) {
				if (Equity.chen(i, j) >= t) {
					count++;
				}
			}
		}
		assertTrue(count >= 0.4 * 1326 && count < 0.6 * 1326, "top-40 % cut-off covers ~40 % of hands: " + count);
	}

	@Test
	void equityKnownMatchups() {
		PokerRng rng = PokerRng.of(new SplittableRandom(3));
		double aa = Equity.equity(Cards.parseAll("As Ah"), new int[0], 1, 40_000, null, rng);
		assertEquals(0.852, aa, 0.01, "AA vs a random hand");
		double sevenTwo = Equity.equity(Cards.parseAll("7s 2d"), new int[0], 1, 40_000, null, rng);
		assertEquals(0.346, sevenTwo, 0.01, "72o vs a random hand");
		double nuts = Equity.equity(Cards.parseAll("As Ks"), Cards.parseAll("Qs Js Ts"), 3, 2_000, null, rng);
		assertEquals(1.0, nuts, 1e-9, "royal flush on the flop");
		assertEquals(1.0, Equity.equity(Cards.parseAll("2s 3d"), new int[0], 0, 100, null, rng), "no opponents");
		double ranged = Equity.equity(Cards.parseAll("7s 2d"), new int[0], 1, 20_000, new int[] {Equity.top40Chen()}, rng);
		assertTrue(ranged < sevenTwo, "a strong range hurts a weak hand");
	}

	@Test
	void madeCategoryIgnoresTheBoard() {
		assertEquals(0, Bots.madeCategory(Cards.parseAll("2s 3d"), Cards.parseAll("Ks Kd 9c")), "board pair only");
		assertEquals(HandEvaluator.PAIR, Bots.madeCategory(Cards.parseAll("9s 3d"), Cards.parseAll("Ks 7d 9c")));
		assertEquals(HandEvaluator.TWO_PAIR, Bots.madeCategory(Cards.parseAll("9s 3d"), Cards.parseAll("Ks Kd 9c")));
		assertEquals(0, Bots.madeCategory(Cards.parseAll("9s 3d"), new int[0]));
	}

	@Test
	void pickTierFollowsTheMix() {
		PokerRng rng = PokerRng.of(new SplittableRandom(5));
		int[] counts = new int[3];
		for (int i = 0; i < 30_000; i++) {
			counts[Bots.pickTier(rng, new int[] {50, 40, 10}).ordinal()]++;
		}
		assertEquals(0.5, counts[0] / 30_000.0, 0.02);
		assertEquals(0.4, counts[1] / 30_000.0, 0.02);
		assertEquals(0.1, counts[2] / 30_000.0, 0.02);
		assertEquals(Tier.REGULAR, Bots.pickTier(rng, new int[] {0, 0, 0}));
		assertEquals(Tier.SHARK, Bots.pickTier(rng, new int[] {0, 0, 7}));
	}

	@Test
	void positions() {
		List<Hand.Seed> seeds = new ArrayList<>();
		for (int i = 0; i < 6; i++) {
			seeds.add(new Hand.Seed("p" + i, false, 1000));
		}
		Hand h = new Hand(seeds, PokerRng.of(new SplittableRandom(1)), new Hand.Options(5, 10, 0, null, null));
		assertEquals(Bots.Position.LATE, Bots.positionOf(h, 0));
		assertEquals(Bots.Position.SB, Bots.positionOf(h, 1));
		assertEquals(Bots.Position.BB, Bots.positionOf(h, 2));
		assertEquals(Bots.Position.EARLY, Bots.positionOf(h, 3));
		assertEquals(Bots.Position.EARLY, Bots.positionOf(h, 4));
		assertEquals(Bots.Position.LATE, Bots.positionOf(h, 5), "cutoff");
	}

	private static Hand rigged(String hole, long... stacks) {
		List<Hand.Seed> seeds = new ArrayList<>();
		for (int i = 0; i < stacks.length; i++) {
			seeds.add(new Hand.Seed("p" + i, false, stacks[i]));
		}
		String[] all = {hole, "2c 3c", "2d 3d", "2h 4c", "4d 5c", "6c 7c"};
		String[] used = java.util.Arrays.copyOf(all, stacks.length);
		int[] deck = HandTest.rig(stacks.length, 0, used, "9s 9h Tc Jd Qh");
		return new Hand(seeds, PokerRng.of(new SplittableRandom(1)), new Hand.Options(5, 10, 0, null, deck));
	}

	@Test
	void preflopDecisions() {
		PokerRng rng = PokerRng.of(new SplittableRandom(9));
		// 4-handed: button 0, SB 1, BB 2, UTG 3 acts first; make UTG fold so the button decides.
		Hand h = rigged("As Ad", 1000, 1000, 1000, 1000);
		h.apply(Action.fold());
		assertEquals(0, h.toAct());
		Bots.View v = Bots.view(h, 0, Map.of());
		for (Tier t : Tier.values()) {
			Action a = Bots.decide(t, v, 0, rng);
			assertTrue(a.kind() == Action.Kind.RAISE || a.kind() == Action.Kind.CALL, t + " plays aces: " + a);
		}
		assertEquals(Action.raiseTo(30), Bots.decide(Tier.SHARK, v, 0, rng), "open to 3 BB");
		Hand trash = rigged("7s 2d", 1000, 1000, 1000, 1000);
		trash.apply(Action.fold());
		Bots.View tv = Bots.view(trash, 0, Map.of());
		for (Tier t : Tier.values()) {
			assertEquals(Action.fold(), Bots.decide(t, tv, 0, rng), t + " folds 72o");
		}
	}

	@Test
	void legalizeClampsSizes() {
		Hand h = rigged("As Ad", 1000, 1000, 1000, 1000);
		h.apply(Action.fold());
		Bots.View v = Bots.view(h, 0, Map.of());
		assertEquals(Action.raiseTo(20), Bots.legalize(v, Action.raiseTo(3)));
		assertEquals(Action.allIn(), Bots.legalize(v, Action.raiseTo(5000)));
		assertEquals(Action.fold(), Bots.legalize(v, Action.check()));
	}

	/** Bot-only tables run thousands of hands without illegal actions; chips are conserved. */
	@Test
	void botsPlayLegalHands() {
		SplittableRandom random = new SplittableRandom(11);
		PokerRng rng = PokerRng.of(random);
		long[] stacks = {1000, 1000, 1000, 1000, 1000, 1000};
		Tier[] tiers = {Tier.FISH, Tier.REGULAR, Tier.SHARK, Tier.FISH, Tier.REGULAR, Tier.SHARK};
		for (int n = 0; n < 1500; n++) {
			List<Hand.Seed> seeds = new ArrayList<>();
			List<Integer> map = new ArrayList<>();
			for (int i = 0; i < stacks.length; i++) {
				if (stacks[i] > 0) {
					seeds.add(new Hand.Seed("p" + i, false, stacks[i]));
					map.add(i);
				}
			}
			if (seeds.size() < 2) {
				java.util.Arrays.fill(stacks, 1000);
				continue;
			}
			long before = java.util.Arrays.stream(stacks).sum();
			Hand h = new Hand(seeds, rng, new Hand.Options(5, 10, n % seeds.size(), null, null));
			while (!h.complete()) {
				Tier t = tiers[map.get(h.toAct())];
				Action a = Bots.decide(h, t, Map.of(), 60, 60, rng);
				h.apply(a); // must be legal as decided
			}
			for (int k = 0; k < map.size(); k++) {
				stacks[map.get(k)] = h.player(k).stack();
			}
			assertEquals(before, java.util.Arrays.stream(stacks).sum());
		}
	}

	/** Skill ordering (§7.4): over many heads-up hands a Shark beats a Fish. */
	@Test
	void sharkBeatsFishHeadsUp() {
		SplittableRandom random = new SplittableRandom(2024);
		PokerRng rng = PokerRng.of(random);
		long sharkNet = 0;
		int flops = 0;
		for (int n = 0; n < 3000; n++) {
			List<Hand.Seed> seeds = List.of(new Hand.Seed("shark", false, 1000), new Hand.Seed("fish", false, 1000));
			Hand h = new Hand(seeds, rng, new Hand.Options(5, 10, n % 2, null, null));
			while (!h.complete()) {
				Tier t = h.toAct() == 0 ? Tier.SHARK : Tier.FISH;
				h.apply(Bots.decide(h, t, Map.of(), 80, 80, rng));
			}
			flops += h.sawFlop() ? 1 : 0;
			sharkNet += h.player(0).stack() - 1000;
		}
		assertTrue(flops > 300, "enough post-flop play: " + flops);
		assertTrue(sharkNet > 0, "shark net " + sharkNet);
	}
}
