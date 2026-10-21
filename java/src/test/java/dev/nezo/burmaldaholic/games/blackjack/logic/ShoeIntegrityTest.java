package dev.nezo.burmaldaholic.games.blackjack.logic;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.SplittableRandom;
import org.junit.jupiter.api.Test;

/**
 * Tester T-A2: shoe integrity under the table's contract (beginRound → draws → endRound → reshuffle only between rounds
 * once {@code needsShuffle}): across thousands of random rounds, every deck count and penetration, no card appears more
 * often than the shoe holds it — per shoe when it never ran dry, per round always (dry shoe included) — and a save /
 * restore at any point (mid-round, refilled) continues identically.
 */
class ShoeIntegrityTest {
	@Test
	void noCardRepeatsWithinAShoeOrARound() {
		SplittableRandom meta = new SplittableRandom(42);
		int dry = 0;
		for (int run = 0; run < 400; run++) {
			int decks = 1 + meta.nextInt(8);
			double pen = 0.25 + meta.nextInt(66) / 100.0; // 0.25 .. 0.90
			Shoe s = new Shoe(new SplittableRandom(run)::nextInt, decks);
			int[] perShoe = new int[52];
			boolean shoeRanDry = false;
			for (int round = 0; round < 60; round++) {
				if (s.needsShuffle(pen)) {
					s.shuffle();
					perShoe = new int[52];
					shoeRanDry = false;
				}
				s.beginRound();
				int start = s.dealt();
				assertTrue(start < s.cut(pen), "a round never starts past the cut card");
				int[] perRound = new int[52];
				int n = 4 + meta.nextInt(decks == 1 ? 40 : 30); // up to 7 seats × 4 hands worth, and silly long rounds for 1 deck
				for (int i = 0; i < n; i++) {
					int c = s.draw().code();
					assertTrue(++perRound[c] <= decks, "card " + c + " more than " + decks + "× in one round (run " + run + ")");
					if (s.refilled()) shoeRanDry = true;
					if (!shoeRanDry) assertTrue(++perShoe[c] <= decks, "card " + c + " repeats within the shoe (run " + run + ")");
				}
				if (s.refilled()) dry++;
				s.endRound();
				assertEquals(s.dealt(), s.discards());
			}
		}
		assertTrue(dry > 0, "the dry-shoe path was exercised");
	}

	@Test
	void restoreAtAnyPointContinuesIdentically() {
		SplittableRandom meta = new SplittableRandom(7);
		for (int run = 0; run < 200; run++) {
			int decks = 1 + meta.nextInt(3);
			Shoe s = new Shoe(new SplittableRandom(run)::nextInt, decks);
			int pre = meta.nextInt(decks * 52);
			for (int i = 0; i < pre; i++) s.draw();
			s.endRound();
			s.beginRound();
			int mid = meta.nextInt(decks * 52); // may run dry
			for (int i = 0; i < mid; i++) s.draw();
			// the table saves round_start = discards() mid-round
			Shoe r = Shoe.restore(new SplittableRandom(run + 1000)::nextInt, decks, s.codes(), s.dealt(), s.discards(), s.refilled());
			assertNotNull(r, "a live shoe always restores");
			assertArrayEquals(s.codes(), r.codes());
			assertEquals(s.refilled(), r.refilled());
			assertEquals(s.needsShuffle(0.75), r.needsShuffle(0.75));
			// continue until just before the shoe would run dry (the refill uses the rng, which differs here on purpose)
			int k = Math.min(s.remaining(), 30);
			for (int i = 0; i < k; i++) assertEquals(s.draw(), r.draw());
		}
	}

	@Test
	void refilledFlagForcesTheNextShuffleOnlyBetweenRounds() {
		Shoe s = new Shoe(new SplittableRandom(9)::nextInt, 1);
		for (int i = 0; i < 46; i++) s.draw();
		s.endRound();
		s.beginRound();
		assertFalse(s.needsShuffle(0.9));
		for (int i = 0; i < 10; i++) s.draw(); // past the end: refilled
		assertTrue(s.refilled());
		assertTrue(s.needsShuffle(0.9));
		assertEquals(52, s.size());
		int[] count = new int[52];
		for (int c : s.codes()) count[c]++;
		for (int c : count) assertEquals(1, c, "still a valid 1-deck shoe after the refill");
	}
}
