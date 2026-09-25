package dev.nezo.burmaldaholic.independent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.nezo.burmaldaholic.games.blackjack.logic.BasicStrategy;
import dev.nezo.burmaldaholic.games.blackjack.logic.BlackjackRound;
import dev.nezo.burmaldaholic.games.blackjack.logic.BlackjackRound.Action;
import dev.nezo.burmaldaholic.games.blackjack.logic.BlackjackRound.Outcome;
import dev.nezo.burmaldaholic.games.blackjack.logic.BlackjackRules;
import dev.nezo.burmaldaholic.games.blackjack.logic.Card;
import dev.nezo.burmaldaholic.games.blackjack.logic.CardSource;
import dev.nezo.burmaldaholic.games.blackjack.logic.Shoe;
import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** GAME_DESIGN.md §6 edge cases with stacked shoes (deal order: seat, dealer up, seat, dealer hole, then draws). */
class BlackjackSpecTest {
	private static final UUID P = new UUID(1, 1);
	private static final UUID Q = new UUID(1, 2);
	private static final BlackjackRules RULES = BlackjackRules.DEFAULT;

	private static BlackjackRound one(long bet, int... ranks) {
		return one(RULES, bet, ranks);
	}

	private static BlackjackRound one(BlackjackRules rules, long bet, int... ranks) {
		List<Card> cards = new ArrayList<>();
		for (int r : ranks) {
			cards.add(Card.of(r));
		}
		return new BlackjackRound(rules, CardSource.stacked(cards, null), List.of(new BlackjackRound.SeatBet(1, P, bet)));
	}

	@Test
	void defaultsMatchSpec() {
		assertEquals(6, RULES.decks());
		assertEquals(0.75, RULES.penetration());
		assertFalse(RULES.dealerHitsSoft17(), "S17");
		assertEquals(1.5, RULES.blackjackPayout());
		assertTrue(RULES.doubleAfterSplit());
		assertEquals(4, RULES.maxHands());
		assertFalse(RULES.resplitAces());
		assertFalse(RULES.lateSurrender());
	}

	@Test
	void naturalPays3to2Floored() {
		// player A,K; dealer 9 up, 7 hole → no peek, BJ paid at once: bet 5 → 5 + 7
		BlackjackRound r = one(5, 1, 9, 13, 7);
		assertEquals(BlackjackRound.Phase.DONE, r.phase(), "only seat has BJ: nothing to play, dealer skips");
		assertEquals(12, r.returnOf(1));
		assertEquals(Outcome.BLACKJACK, r.seat(1).hands.getFirst().outcome);
		assertEquals(2, r.dealerCards().size(), "dealer does not draw when nothing is live");
	}

	@Test
	void naturalVsDealerNaturalPushes() {
		// player A,K; dealer K up, A hole → peek finds BJ
		BlackjackRound r = one(10, 1, 13, 13, 1);
		assertEquals(BlackjackRound.Phase.DONE, r.phase());
		assertEquals(10, r.returnOf(1));
		assertTrue(r.dealerBlackjack());
	}

	@Test
	void insurancePays2to1AndMainBetLoses() {
		// player 10,9; dealer A up, K hole
		BlackjackRound r = one(10, 10, 1, 9, 13);
		assertEquals(BlackjackRound.Phase.INSURANCE, r.phase());
		assertEquals(BlackjackRound.Offer.INSURANCE, r.offer(1));
		assertFalse(r.insure(1, 6), "at most half the bet");
		assertTrue(r.insure(1, 5));
		assertEquals(BlackjackRound.Phase.DONE, r.phase());
		assertEquals(15, r.returnOf(1), "insurance 5 → 15, main bet lost");
		assertEquals(15, r.stakedOf(1));
	}

	@Test
	void insuranceLostWhenNoDealerBlackjack() {
		// player 10,9 stands on 19; dealer A up, 6 hole → 17 soft, S17 stands; 19 wins
		BlackjackRound r = one(10, 10, 1, 9, 6);
		r.insure(1, 5);
		assertEquals(BlackjackRound.Phase.TURNS, r.phase());
		r.act(1, Action.STAND);
		assertEquals(20, r.returnOf(1));
		assertEquals(15, r.stakedOf(1));
	}

	@Test
	void evenMoneyPays1to1Immediately() {
		BlackjackRound r = one(10, 1, 1, 13, 13); // player A,K vs dealer A (hole K)
		assertEquals(BlackjackRound.Offer.EVEN_MONEY, r.offer(1));
		assertTrue(r.evenMoney(1, true));
		assertEquals(20, r.returnOf(1), "even money even though the dealer had BJ");
	}

	@Test
	void splitAcesGetOneCardAnd21IsNotBlackjack() {
		// player A,A; dealer 9 up, 7 hole; split → A+K, A+2; dealer 16 draws 5 → 21?? use 2 → 18
		BlackjackRound r = one(10, 1, 9, 1, 7, 13, 2, 2);
		assertTrue(r.legal(1).contains(Action.SPLIT));
		r.act(1, Action.SPLIT);
		BlackjackRound.Seat s = r.seat(1);
		assertEquals(2, s.hands.size());
		assertEquals(BlackjackRound.Phase.DONE, r.phase(), "split aces stand automatically");
		assertEquals(Outcome.WIN, s.hands.get(0).outcome, "A+K after split is a plain 21");
		assertEquals(20, s.hands.get(0).ret, "pays 1:1, not 3:2");
		assertEquals(Outcome.LOSE, s.hands.get(1).outcome, "A+2 = 13 vs 18");
		assertEquals(20, r.returnOf(1));
		assertEquals(20, r.stakedOf(1));
	}

	@Test
	void noResplitOfAcesByDefault() {
		BlackjackRound r = one(10, 1, 9, 1, 7, 1, 5, 2);
		r.act(1, Action.SPLIT);
		assertEquals(BlackjackRound.Phase.DONE, r.phase(), "A,A after split: no re-split, one card only");
	}

	@Test
	void resplitUpToFourHandsThenDoubleAfterSplit() {
		// 8,8 vs dealer 6 (hole 10 = 16). Draws: 8 (→ 8,8 again), 8, 8, then 3 and 2 ...
		BlackjackRound r = one(10, 8, 6, 8, 10, 8, 8, 8, 3, 2, 10, 10, 10, 10);
		r.act(1, Action.SPLIT); // hands: [8,8] [8,8]
		r.act(1, Action.SPLIT); // [8,8] [8,?] [8,8]
		r.act(1, Action.SPLIT);
		BlackjackRound.Seat s = r.seat(1);
		assertEquals(4, s.hands.size());
		assertFalse(r.legal(1).contains(Action.SPLIT), "max 4 hands");
		assertTrue(r.legal(1).contains(Action.DOUBLE), "double after split");
		assertEquals(10, r.extraStake(1, Action.DOUBLE));
	}

	@Test
	void doubleTakesExactlyOneCard() {
		BlackjackRound r = one(10, 6, 9, 5, 7, 2, 10); // 11 vs 9 (hole 7 = 16)
		r.act(1, Action.DOUBLE);
		BlackjackRound.Hand h = r.seat(1).hands.getFirst();
		assertEquals(3, h.cards.size());
		assertEquals(20, h.bet);
		// player 13; dealer 16 draws 10 → bust
		assertEquals(Outcome.DEALER_BUST, h.outcome);
		assertEquals(40, r.returnOf(1));
	}

	@Test
	void dealerS17StandsAndH17Hits() {
		List<Card> soft17 = List.of(Card.of(1), Card.of(6));
		assertFalse(BlackjackRound.dealerShouldHit(soft17, false));
		assertTrue(BlackjackRound.dealerShouldHit(soft17, true));
		assertFalse(BlackjackRound.dealerShouldHit(List.of(Card.of(10), Card.of(7)), true), "hard 17 always stands");
		assertTrue(BlackjackRound.dealerShouldHit(List.of(Card.of(10), Card.of(6)), false));
	}

	@Test
	void autoStandAt21AndBustSettles() {
		BlackjackRound r = one(10, 10, 10, 5, 7, 6); // 15 vs 17; hit 6 → 21 auto-stand
		r.act(1, Action.HIT);
		assertEquals(BlackjackRound.Phase.DONE, r.phase());
		assertEquals(20, r.returnOf(1));
		BlackjackRound b = one(10, 10, 10, 5, 7, 9);
		b.act(1, Action.HIT);
		assertEquals(Outcome.BUST, b.seat(1).hands.getFirst().outcome);
		assertEquals(0, b.returnOf(1));
	}

	@Test
	void twoSeatsDealtInSeatOrderAndStandAllOnDisconnect() {
		// seat1 10,10; seat2 9,9; dealer up 10, hole 7 (peek: no BJ)
		List<Card> cards = List.of(Card.of(10), Card.of(9), Card.of(10), Card.of(10), Card.of(9), Card.of(7));
		BlackjackRound r = new BlackjackRound(RULES, CardSource.stacked(cards, null),
			List.of(new BlackjackRound.SeatBet(2, Q, 10), new BlackjackRound.SeatBet(1, P, 10)));
		assertEquals(20, r.seat(1).hands.getFirst().cards.stream().mapToInt(Card::value).sum());
		r.standAll(1); // disconnect → stand
		r.standAll(2);
		assertEquals(BlackjackRound.Phase.DONE, r.phase());
		assertEquals(20, r.returnOf(1), "20 beats 17");
		assertEquals(20, r.returnOf(2), "18 beats 17");
	}

	@Test
	void worstCaseReservation() {
		assertEquals(8 * 100 + 2 * 50, BlackjackRules.worstCasePayout(100), "§18.2: 8× bet + insurance");
	}

	/** Basic strategy Monte Carlo: house edge ≈ 0.41 % (§6.1) — must be a positive, small edge. */
	@Test
	void basicStrategyEdgeIsSmallAndPositive() {
		SplittableRandom rnd = new SplittableRandom(2026);
		Shoe shoe = new Shoe(rnd::nextInt, 6);
		shoe.shuffle();
		double staked = 0, returned = 0;
		int hands = 1_500_000;
		for (int i = 0; i < hands; i++) {
			if (shoe.needsShuffle(RULES.penetration())) {
				shoe.shuffle();
			}
			BlackjackRound r = new BlackjackRound(RULES, shoe, List.of(new BlackjackRound.SeatBet(1, P, 100)));
			if (r.phase() == BlackjackRound.Phase.INSURANCE) {
				r.decline(1);
			}
			while (r.phase() == BlackjackRound.Phase.TURNS && r.current() != null) {
				BlackjackRound.Turn t = r.current();
				List<Action> legal = r.legal(1);
				r.act(1, BasicStrategy.choose(t.hand().cards, r.upCard(), legal));
			}
			staked += 100; // initial wager (industry definition of the edge)
			returned += r.returnOf(1) - (r.stakedOf(1) - 100);
		}
		double edge = 1 - returned / staked;
		// SE ≈ 1.15 / sqrt(1.5M) ≈ 0.094 %; allow ~3.5 σ around 0.41 %
		assertTrue(edge > 0.0005 && edge < 0.0075, "blackjack edge " + edge);
	}
}
