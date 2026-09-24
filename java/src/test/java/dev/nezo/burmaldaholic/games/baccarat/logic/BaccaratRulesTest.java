package dev.nezo.burmaldaholic.games.baccarat.logic;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.nezo.burmaldaholic.games.baccarat.logic.BaccaratRules.Coup;
import dev.nezo.burmaldaholic.games.baccarat.logic.BaccaratRules.Side;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.SplittableRandom;
import org.junit.jupiter.api.Test;

/** §20.1 / §20.3: values, drawing table and the five test vectors. */
class BaccaratRulesTest {
	private static final int S = Card.SPADES, H = Card.HEARTS, D = Card.DIAMONDS, C = Card.CLUBS;

	private static Coup deal(Card... cards) {
		Deque<Card> q = new ArrayDeque<>(List.of(cards));
		Coup coup = BaccaratRules.deal(q::removeFirst);
		assertTrue(q.isEmpty(), "every given card was used");
		return coup;
	}

	private static Card c(int rank, int suit) {
		return Card.of(rank, suit);
	}

	@Test
	void cardPointsAndTotals() {
		assertEquals(1, c(1, S).points());
		assertEquals(9, c(9, S).points());
		for (int r = 10; r <= 13; r++) {
			assertEquals(0, c(r, S).points());
			assertEquals(10, c(r, S).burnValue());
		}
		assertEquals(5, BaccaratRules.total(List.of(c(7, S), c(8, H))), "7 + 8 = 5");
	}

	@Test
	void vector1PlayerNaturalEight() {
		Coup coup = deal(c(8, S), c(9, D), c(13, H), c(7, C));
		assertEquals(8, coup.playerTotal());
		assertEquals(6, coup.bankerTotal());
		assertEquals(2, coup.player().size());
		assertEquals(2, coup.banker().size());
		assertEquals(Side.PLAYER, coup.winner());
	}

	@Test
	void vector2BankerThreeStandsOnEight() {
		Coup coup = deal(c(2, C), c(13, D), c(3, H), c(3, S), c(8, D));
		assertEquals(3, coup.playerTotal());
		assertEquals(3, coup.bankerTotal());
		assertEquals(2, coup.banker().size(), "Banker 3 stands on a Player third card of 8");
		assertEquals(Side.TIE, coup.winner());
	}

	@Test
	void vector3BankerFiveDrawsOnFour() {
		Coup coup = deal(c(1, S), c(5, H), c(4, D), c(13, C), c(4, H), c(3, C));
		assertEquals(9, coup.playerTotal());
		assertEquals(8, coup.bankerTotal());
		assertEquals(Side.PLAYER, coup.winner());
	}

	@Test
	void vector4PlayerStandsBankerDraws() {
		Coup coup = deal(c(7, D), c(2, S), c(12, H), c(3, H), c(4, S));
		assertEquals(7, coup.playerTotal());
		assertEquals(2, coup.player().size());
		assertEquals(9, coup.bankerTotal());
		assertEquals(Side.BANKER, coup.winner());
		assertEquals(78, Paytable.DEFAULT.returnOf(BetKind.BANKER, 40, coup), "a Banker bet of 40 wins +38");
	}

	@Test
	void vector5PlayerPair() {
		Coup coup = deal(c(12, S), c(6, D), c(12, D), c(1, C), c(5, S));
		assertTrue(coup.playerPair(), "Q, Q");
		assertFalse(coup.bankerPair());
		assertEquals(3, coup.player().size(), "Player 0 draws");
		assertEquals(7, coup.bankerTotal(), "Banker 7 stands");
		assertEquals(12 * 10, Paytable.DEFAULT.returnOf(BetKind.PLAYER_PAIR, 10, coup), "pair pays 11:1 whatever follows");
		Coup kq = deal(c(13, S), c(6, D), c(12, D), c(1, C), c(5, S));
		assertFalse(kq.playerPair(), "K + Q is not a pair");
	}

	@Test
	void bankerDrawingTable() {
		String[] expected = {
			"DDDDDDDDDD", "DDDDDDDDDD", "DDDDDDDDDD",
			"DDDDDDDDSD",
			"SSDDDDDDSS",
			"SSSSDDDDSS",
			"SSSSSSDDSS",
			"SSSSSSSSSS"};
		for (int total = 0; total <= 7; total++) {
			for (int third = 0; third <= 9; third++) {
				assertEquals(expected[total].charAt(third) == 'D', BaccaratRules.bankerDraws(total, third), "banker " + total + " vs " + third);
			}
			assertEquals(total <= 5, BaccaratRules.bankerDraws(total, -1), "Player stood, banker " + total);
		}
		for (int t = 0; t <= 7; t++) {
			assertEquals(t <= 5, BaccaratRules.playerDraws(t));
		}
	}

	@Test
	void naturalNineOfTheWinner() {
		assertTrue(deal(c(9, S), c(2, D), c(10, H), c(3, C)).winnerNaturalNine());
		assertFalse(deal(c(8, S), c(9, D), c(13, H), c(7, C)).winnerNaturalNine(), "natural eight");
	}

	@Test
	void shoePersistsAndBurns() {
		SplittableRandom random = new SplittableRandom(7);
		BaccaratShoe shoe = new BaccaratShoe(8);
		assertTrue(shoe.needsShuffle(0.8, 8), "a new shoe must be shuffled");
		int burned = shoe.shuffle(random::nextInt, 8, true);
		assertEquals(416, shoe.size());
		assertEquals(burned, shoe.dealt());
		for (int i = 0; i < 20; i++) {
			shoe.draw(random::nextInt);
		}
		BaccaratShoe copy = new BaccaratShoe(1);
		copy.restore(shoe.codes(), shoe.dealt(), shoe.decks());
		assertEquals(shoe.remaining(), copy.remaining());
		assertArrayEquals(shoe.codes(), copy.codes());
		for (int i = 0; i < 30; i++) {
			assertEquals(shoe.draw(random::nextInt), copy.draw(random::nextInt), "same order after restore");
		}
		assertTrue(copy.needsShuffle(0.8, 6), "a changed deck count reshuffles");
		BaccaratShoe bad = new BaccaratShoe(8);
		bad.restore(new int[] {1, 2, 3}, 0, 8);
		assertEquals(0, bad.size(), "corrupt data → empty shoe (shuffled before the next coup)");
	}
}
