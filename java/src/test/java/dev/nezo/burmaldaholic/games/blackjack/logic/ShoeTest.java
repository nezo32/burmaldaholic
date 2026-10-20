package dev.nezo.burmaldaholic.games.blackjack.logic;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;
import java.util.SplittableRandom;
import org.junit.jupiter.api.Test;

/** E5: a countable shoe: cut card, discard tray, save/restore, and the deterministic dry-shoe rule. */
class ShoeTest {
	@Test
	void cutCardPositionMatchesNeedsShuffle() {
		Shoe s = new Shoe(new SplittableRandom(1)::nextInt, 6);
		assertEquals(234, s.cut(0.75));
		Shoe one = new Shoe(new SplittableRandom(1)::nextInt, 1);
		assertEquals(47, one.cut(0.9)); // ceil(46.8)
		for (int i = 0; i < 46; i++) one.draw();
		assertFalse(one.needsShuffle(0.9));
		one.draw();
		assertTrue(one.needsShuffle(0.9));
	}

	@Test
	void discardTrayFollowsRounds() {
		Shoe s = new Shoe(new SplittableRandom(2)::nextInt, 2);
		s.beginRound();
		for (int i = 0; i < 7; i++) s.draw();
		assertEquals(0, s.discards());
		s.endRound();
		assertEquals(7, s.discards());
		s.beginRound();
		s.draw();
		assertEquals(7, s.discards());
		assertEquals(8, s.dealt());
		assertEquals(104 - 8, s.remaining());
	}

	@Test
	void restoreKeepsOrderAndPositionWithoutRng() {
		Shoe s = new Shoe(new SplittableRandom(3)::nextInt, 6);
		for (int i = 0; i < 50; i++) s.draw();
		s.endRound();
		Shoe r = Shoe.restore(b -> {
			throw new AssertionError("a restore must not shuffle");
		}, 6, s.codes(), s.dealt(), s.discards(), false);
		assertNotNull(r);
		assertArrayEquals(s.codes(), r.codes());
		assertEquals(50, r.dealt());
		assertEquals(50, r.discards());
		for (int i = 0; i < 100; i++) assertEquals(s.draw(), r.draw());
	}

	@Test
	void restoreRejectsBadData() {
		int[] codes = new Shoe(new SplittableRandom(4)::nextInt, 1).codes();
		assertNull(Shoe.restore(b -> 0, 2, codes, 0, 0, false)); // wrong length
		int[] dup = codes.clone();
		dup[0] = dup[1];
		assertNull(Shoe.restore(b -> 0, 1, dup, 0, 0, false)); // a card twice in a 1-deck shoe
		assertNull(Shoe.restore(b -> 0, 1, codes, 53, 0, false));
		assertNull(Shoe.restore(b -> 0, 1, codes, 5, 6, false));
		assertNotNull(Shoe.restore(b -> 0, 1, codes, 52, 52, false));
	}

	/**
	 * Dry shoe (documented rule): the round's cards stay out, the discard tray is shuffled behind them, no card repeats in
	 * the round, the continuation is deterministic, and the next round starts from a full reshuffle.
	 */
	@Test
	void dryShoeRefillsFromTheTrayOnly() {
		Shoe s = dryShoe(5);
		Map<Integer, Integer> round = new HashMap<>();
		for (int i = 0; i < 12; i++) round.merge(s.draw().code(), 1, Integer::sum); // the last 12, then dry
		for (int i = 0; i < 30; i++) round.merge(s.draw().code(), 1, Integer::sum); // from the shuffled tray
		round.values().forEach(n -> assertEquals(1, n, "a card came twice in one round"));
		assertTrue(s.refilled());
		assertTrue(s.needsShuffle(0.9));
		assertTrue(s.needsShuffle(0.25));
		assertEquals(0, s.discards());
		Shoe t = dryShoe(5);
		for (int i = 0; i < 12; i++) t.draw();
		int[] a = new int[30];
		for (int i = 0; i < 30; i++) a[i] = t.draw().code();
		assertArrayEquals(java.util.Arrays.copyOfRange(s.codes(), 12, 42), a);
		s.endRound();
		s.shuffle();
		assertFalse(s.refilled());
		assertEquals(0, s.dealt());
	}

	private static Shoe dryShoe(long seed) {
		Shoe s = new Shoe(new SplittableRandom(seed)::nextInt, 1);
		for (int i = 0; i < 40; i++) s.draw();
		s.endRound();
		s.beginRound();
		return s;
	}
}
