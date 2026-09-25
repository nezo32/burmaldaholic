package dev.nezo.burmaldaholic.games.blackjack.logic;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.nezo.burmaldaholic.games.blackjack.logic.BlackjackRound.Action;
import dev.nezo.burmaldaholic.games.blackjack.logic.BlackjackRound.Hand;
import dev.nezo.burmaldaholic.games.blackjack.logic.BlackjackRound.Offer;
import dev.nezo.burmaldaholic.games.blackjack.logic.BlackjackRound.Outcome;
import dev.nezo.burmaldaholic.games.blackjack.logic.BlackjackRound.Phase;
import dev.nezo.burmaldaholic.games.blackjack.logic.BlackjackRound.SeatBet;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.SplittableRandom;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class BlackjackRoundTest {
	private static final BlackjackRules R = BlackjackRules.DEFAULT;

	/** "AS", "10D", "KH", "7C" ... */
	static Card c(String id) {
		String rank = id.substring(0, id.length() - 1);
		int suit = "SHDC".indexOf(id.charAt(id.length() - 1));
		int r = switch (rank) {
			case "A" -> 1;
			case "J" -> 11;
			case "Q" -> 12;
			case "K" -> 13;
			default -> Integer.parseInt(rank);
		};
		return Card.of(r, suit);
	}

	static List<Card> cards(String... ids) {
		return Arrays.stream(ids).map(BlackjackRoundTest::c).toList();
	}

	/** Deal order: seat1 c1, seat2 c1…, dealer up, seat1 c2…, dealer hole, then draws. */
	static BlackjackRound round(List<String> deal, long[] bets, BlackjackRules rules) {
		List<SeatBet> sb = new ArrayList<>();
		for (int i = 0; i < bets.length; i++) {
			sb.add(new SeatBet(i, new UUID(0, i), bets[i]));
		}
		CardSource src = CardSource.stacked(cards(deal.toArray(String[]::new)), new Shoe(new SplittableRandom(0)::nextInt, 6));
		return new BlackjackRound(rules, src, sb);
	}

	static BlackjackRound round(String... deal) {
		return round(List.of(deal), new long[] {10}, R);
	}

	static String ranks(Hand h) {
		return h.cards.stream().map(Card::rankLabel).collect(Collectors.joining("+"));
	}

	// ---- cards & hands ------------------------------------------------------------------------

	@Test
	void cardCodesRoundTrip() {
		Set<Integer> seen = new HashSet<>();
		for (int code = 0; code < 52; code++) {
			Card card = Card.fromCode(code);
			assertEquals(code, card.code());
			seen.add(card.rank() * 10 + card.suit());
		}
		assertEquals(52, seen.size());
		assertEquals(11, c("AS").value());
		assertEquals(10, c("QD").value());
		assertTrue(c("5H").isRed());
	}

	@Test
	void handValues() {
		assertEquals(new Hands.Value(21, true), Hands.value(cards("AS", "KD")));
		assertEquals(new Hands.Value(21, true), Hands.value(cards("AS", "AD", "9C")));
		assertEquals(new Hands.Value(25, false), Hands.value(cards("KS", "QD", "5C")));
		assertEquals(new Hands.Value(12, true), Hands.value(cards("AS", "AD")));
		assertEquals(new Hands.Value(16, false), Hands.value(cards("AS", "6D", "9C")));
	}

	@Test
	void naturalsAndPairs() {
		assertTrue(Hands.isNatural(cards("AS", "KD")));
		assertFalse(Hands.isNatural(cards("AS", "KD"), true));
		assertFalse(Hands.isNatural(cards("7S", "7D", "7C")));
		assertTrue(Hands.isPair(cards("KS", "KD")));
		assertFalse(Hands.isPair(cards("KS", "QD")));
	}

	@Test
	void displayTotals() {
		assertArrayEquals(new int[] {7, 17}, Hands.displayTotals(cards("AS", "6D")));
		assertArrayEquals(new int[] {21}, Hands.displayTotals(cards("AS", "KD")));
		assertArrayEquals(new int[] {16}, Hands.displayTotals(cards("AS", "6D", "9C")));
	}

	@Test
	void payoutsFloored() {
		assertEquals(25, R.blackjackReturn(10));
		assertEquals(12, R.blackjackReturn(5)); // 3:2 on 5 -> +7
		assertEquals(1 + 1, R.blackjackReturn(1)); // floor(1.5) = 1
		assertEquals(10 + 12, R.withBlackjackPayout(1.2).blackjackReturn(10)); // 6:5
		assertEquals(2, BlackjackRules.surrenderReturn(5));
		assertEquals(2, BlackjackRules.maxInsurance(5));
	}

	@Test
	void rulesAreClamped() {
		BlackjackRules r = new BlackjackRules(20, 0.99, false, 5, true, 9, false, true, false);
		assertEquals(8, r.decks());
		assertEquals(0.9, r.penetration());
		assertEquals(4, r.maxHands());
		assertEquals(2.0, r.blackjackPayout());
	}

	// ---- naturals, peek, insurance --------------------------------------------------------------

	@Test
	void playerBlackjackVsNinePaysAtOnce() {
		BlackjackRound r = round("AS", "9D", "KH", "7C");
		assertEquals(Phase.DONE, r.phase());
		assertEquals(Outcome.BLACKJACK, r.seats().getFirst().hands.getFirst().outcome);
		assertEquals(25, r.returnOf(0));
		assertEquals(2, r.dealerCards().size());
	}

	@Test
	void dealerBlackjackUnderTen() {
		BlackjackRound r = round(List.of("AS", "9C", "KD", "KH", "7C", "AH"), new long[] {10, 10}, R);
		assertTrue(r.peeked());
		assertTrue(r.dealerBlackjack());
		assertTrue(r.holeRevealed());
		assertEquals(Phase.DONE, r.phase());
		assertEquals(Outcome.PUSH, r.seat(0).hands.getFirst().outcome);
		assertEquals(10, r.returnOf(0));
		assertEquals(Outcome.DEALER_BLACKJACK, r.seat(1).hands.getFirst().outcome);
		assertEquals(0, r.returnOf(1));
	}

	@Test
	void fullInsurancePaysTwoToOne() {
		BlackjackRound r = round("9S", "AD", "9H", "KC");
		assertEquals(Phase.INSURANCE, r.phase());
		assertEquals(Offer.INSURANCE, r.offer(0));
		assertTrue(r.insure(0, 5));
		assertEquals(Phase.DONE, r.phase());
		assertEquals(15, r.stakedOf(0));
		assertEquals(15, r.returnOf(0)); // main lost, insurance 5 -> 15
	}

	@Test
	void partialInsuranceAmountsAndRange() {
		BlackjackRound r = round(List.of("9S", "AD", "9H", "KC"), new long[] {100}, R);
		assertFalse(r.insure(0, 51), "more than half the bet");
		assertFalse(r.insure(0, -1), "negative");
		assertEquals(Phase.INSURANCE, r.phase());
		assertTrue(r.insure(0, 20));
		assertEquals(120, r.stakedOf(0));
		assertEquals(60, r.returnOf(0));
		assertFalse(r.insure(0, 10), "decided already");
	}

	@Test
	void zeroInsuranceIsDecline() {
		BlackjackRound r = round("9S", "AD", "9H", "KC");
		assertTrue(r.insure(0, 0));
		assertEquals(10, r.stakedOf(0));
		assertEquals(0, r.returnOf(0));
	}

	@Test
	void insuranceLostWithoutDealerBlackjack() {
		BlackjackRound r = round("9S", "AD", "9H", "5C", "KS");
		r.insure(0, 5);
		assertEquals(Phase.TURNS, r.phase());
		assertEquals(0, r.current().seat().seat);
		r.act(0, Action.STAND); // 18 vs A+5 -> K -> 16 -> draws from the shoe
		assertEquals(Phase.DONE, r.phase());
		assertEquals(0, r.seat(0).insuranceReturn);
		assertEquals(15, r.stakedOf(0));
	}

	@Test
	void evenMoney() {
		BlackjackRound r = round("AS", "AD", "KH", "KC");
		assertEquals(Offer.EVEN_MONEY, r.offer(0));
		assertFalse(r.insure(0, 5), "a natural is offered even money, not insurance");
		assertTrue(r.evenMoney(0, true));
		assertTrue(r.seat(0).settled);
		assertEquals(20, r.returnOf(0));
		assertEquals(Outcome.EVEN_MONEY, r.seat(0).hands.getFirst().outcome);
	}

	@Test
	void declinedEvenMoneyVsDealerBlackjackPushes() {
		BlackjackRound r = round("AS", "AD", "KH", "KC");
		r.evenMoney(0, false);
		assertEquals(10, r.returnOf(0));
		assertEquals(Outcome.PUSH, r.seat(0).hands.getFirst().outcome);
	}

	@Test
	void declinedEvenMoneyWithoutDealerBlackjackPaysThreeToTwo() {
		BlackjackRound r = round("AS", "AD", "KH", "5C");
		r.evenMoney(0, false);
		assertEquals(Outcome.BLACKJACK, r.seat(0).hands.getFirst().outcome);
		assertEquals(25, r.returnOf(0));
	}

	@Test
	void noInsuranceOnOneChipOrWhenDisabled() {
		assertEquals(Phase.TURNS, round(List.of("9S", "AD", "9H", "5C", "KS"), new long[] {1}, R).phase());
		assertEquals(Phase.TURNS, round(List.of("9S", "AD", "9H", "5C", "KS"), new long[] {10}, R.withInsurance(false)).phase());
	}

	@Test
	void standAllDeclinesPendingInsurance() {
		BlackjackRound r = round(List.of("9S", "8S", "AD", "9H", "8H", "5C"), new long[] {10, 10}, R);
		r.standAll(0);
		assertEquals(Phase.INSURANCE, r.phase());
		assertEquals(1, r.pendingInsurance().size());
		r.decline(1);
		assertEquals(Phase.TURNS, r.phase());
		assertEquals(1, r.current().seat().seat);
	}

	@Test
	void peekOnTenWithoutBlackjack() {
		BlackjackRound r = round("9S", "KD", "9H", "5C");
		assertTrue(r.peeked());
		assertFalse(r.holeRevealed());
		assertEquals(Phase.TURNS, r.phase());
		assertFalse(round("9S", "7D", "9H", "5C").peeked());
	}

	// ---- player actions ---------------------------------------------------------------------------

	@Test
	void bustAndDealerSkipsDrawing() {
		BlackjackRound r = round("KS", "6D", "6H", "5C", "QH");
		assertEquals(List.of(Action.HIT, Action.STAND, Action.DOUBLE), r.legal(0));
		r.act(0, Action.HIT);
		assertEquals(Phase.DONE, r.phase());
		assertEquals(Outcome.BUST, r.seat(0).hands.getFirst().outcome);
		assertEquals(2, r.dealerCards().size());
	}

	@Test
	void autoStandsAt21() {
		BlackjackRound r = round("KS", "6D", "5H", "KC", "6C", "2D");
		r.act(0, Action.HIT);
		assertEquals(Phase.DONE, r.phase());
		assertEquals(Outcome.WIN, r.seat(0).hands.getFirst().outcome);
		assertEquals(20, r.returnOf(0));
	}

	@Test
	void doubleTakesOneCard() {
		BlackjackRound r = round("6S", "9D", "5H", "7C", "KH", "10D");
		assertEquals(10, r.extraStake(0, Action.DOUBLE));
		r.act(0, Action.DOUBLE);
		Hand h = r.seat(0).hands.getFirst();
		assertEquals(3, h.cards.size());
		assertEquals(20, h.bet);
		assertTrue(h.doubled);
		assertEquals(Outcome.DEALER_BUST, h.outcome);
		assertEquals(40, r.returnOf(0));
	}

	@Test
	void doubleAndSplitNeedBalance() {
		BlackjackRound r = round("8S", "9D", "8H", "7C");
		assertEquals(List.of(Action.HIT, Action.STAND), r.legal(0, 9));
		assertEquals(List.of(Action.HIT, Action.STAND, Action.DOUBLE, Action.SPLIT), r.legal(0, 10));
	}

	@Test
	void kingQueenCannotSplit() {
		assertFalse(round("KS", "9D", "QH", "7C").legal(0).contains(Action.SPLIT));
	}

	@Test
	void splitResplitToFourHandsAndDoubleAfterSplit() {
		BlackjackRound r = round("8S", "6D", "8H", "KC", "8D", "3C", "8C", "2S", "10S", "9S");
		r.act(0, Action.SPLIT);
		assertEquals(2, r.seat(0).hands.size());
		r.act(0, Action.SPLIT);
		assertEquals(3, r.seat(0).hands.size());
		assertTrue(r.legal(0).contains(Action.SPLIT));
		r.act(0, Action.SPLIT);
		assertEquals(4, r.seat(0).hands.size());
		assertFalse(r.legal(0).contains(Action.SPLIT), "4 hands: no more splits");
		assertEquals(List.of("8+10", "8+9", "8+2", "8+3"), r.seat(0).hands.stream().map(BlackjackRoundTest::ranks).toList());
		r.act(0, Action.STAND);
		r.act(0, Action.STAND);
		assertTrue(r.legal(0).contains(Action.DOUBLE), "DAS");
		r.act(0, Action.DOUBLE);
		assertEquals(3, r.current().handIndex());
		assertEquals(50, r.stakedOf(0));
	}

	@Test
	void maxHandsConfigurable() {
		BlackjackRound r = round(List.of("8S", "6D", "8H", "KC", "8D", "3C"), new long[] {10}, R.withMaxHands(2));
		r.act(0, Action.SPLIT);
		assertFalse(r.legal(0).contains(Action.SPLIT));
	}

	@Test
	void noDoubleAfterSplitWhenDisabled() {
		BlackjackRound r = round(List.of("8S", "6D", "8H", "KC", "2D", "3C"), new long[] {10}, R.withDoubleAfterSplit(false));
		r.act(0, Action.SPLIT);
		assertEquals(List.of(Action.HIT, Action.STAND), r.legal(0));
	}

	@Test
	void splitAcesOneCardEachAndTwentyOneIsNotBlackjack() {
		BlackjackRound r = round("AS", "6D", "AH", "KC", "KD", "9C", "10H");
		r.act(0, Action.SPLIT);
		assertEquals(Phase.DONE, r.phase(), "both ace hands closed automatically");
		Hand h1 = r.seat(0).hands.get(0);
		Hand h2 = r.seat(0).hands.get(1);
		assertEquals("A+K", ranks(h1));
		assertEquals(Outcome.DEALER_BUST, h1.outcome);
		assertEquals(20, h1.ret, "1:1, not 3:2");
		assertEquals("A+9", ranks(h2));
		assertEquals(40, r.returnOf(0));
	}

	@Test
	void resplitAcesOnlyWhenEnabled() {
		BlackjackRound noRsa = round("AS", "6D", "AH", "KC", "AD", "9C", "10H");
		noRsa.act(0, Action.SPLIT);
		assertEquals(Phase.DONE, noRsa.phase());
		BlackjackRound rsa = round(List.of("AS", "6D", "AH", "KC", "AD", "9C", "5H", "7H", "10H"), new long[] {10}, R.withResplitAces(true));
		rsa.act(0, Action.SPLIT);
		assertEquals(List.of(Action.STAND, Action.SPLIT), rsa.legal(0));
		rsa.act(0, Action.SPLIT);
		assertEquals(3, rsa.seat(0).hands.size());
	}

	@Test
	void surrenderOnlyWhenEnabledOnFirstTwoCards() {
		assertFalse(round("10S", "KD", "6H", "7C").legal(0).contains(Action.SURRENDER));
		BlackjackRound r = round(List.of("10S", "KD", "6H", "7C"), new long[] {10}, R.withLateSurrender(true));
		assertTrue(r.legal(0).contains(Action.SURRENDER));
		r.act(0, Action.SURRENDER);
		assertEquals(Phase.DONE, r.phase());
		assertEquals(5, r.returnOf(0));
		assertEquals(Outcome.SURRENDER, r.seat(0).hands.getFirst().outcome);
		assertEquals(2, r.dealerCards().size());
		BlackjackRound afterHit = round(List.of("5S", "KD", "3H", "7C", "2C"), new long[] {10}, R.withLateSurrender(true));
		afterHit.act(0, Action.HIT);
		assertFalse(afterHit.legal(0).contains(Action.SURRENDER));
	}

	@Test
	void pushReturnsStake() {
		BlackjackRound r = round("10S", "KD", "8H", "8C");
		r.act(0, Action.STAND);
		assertEquals(Outcome.PUSH, r.seat(0).hands.getFirst().outcome);
		assertEquals(10, r.returnOf(0));
	}

	@Test
	void dealerSoft17() {
		BlackjackRound s17 = round(List.of("10S", "AD", "8H", "6C"), new long[] {10}, R.withInsurance(false));
		s17.act(0, Action.STAND);
		assertEquals(2, s17.dealerCards().size());
		assertEquals(Outcome.WIN, s17.seat(0).hands.getFirst().outcome);
		BlackjackRound h17 = round(List.of("10S", "AD", "8H", "6C", "4D"), new long[] {10}, R.withInsurance(false).withDealerHitsSoft17(true));
		h17.act(0, Action.STAND);
		assertEquals(3, h17.dealerCards().size());
		assertEquals(Outcome.LOSE, h17.seat(0).hands.getFirst().outcome);
		assertTrue(BlackjackRound.dealerShouldHit(cards("10S", "6D"), false));
		assertFalse(BlackjackRound.dealerShouldHit(cards("10S", "7D"), true));
		assertFalse(BlackjackRound.dealerShouldHit(cards("AS", "6D"), false));
		assertTrue(BlackjackRound.dealerShouldHit(cards("AS", "6D"), true));
	}

	@Test
	void multiSeatDealAndTurnOrder() {
		BlackjackRound r = round(List.of("2S", "3S", "4S", "9D", "5S", "6S", "7S", "8D"), new long[] {10, 20, 30}, R);
		assertEquals(List.of("2+5", "3+6", "4+7"), r.seats().stream().map(s -> ranks(s.hands.getFirst())).toList());
		assertEquals(9, r.upCard().rank());
		assertEquals(0, r.current().seat().seat);
		assertFalse(r.act(1, Action.STAND), "not seat 1's turn");
		r.standAll(0);
		assertEquals(1, r.current().seat().seat);
		r.act(1, Action.STAND);
		r.standAll(2);
		assertEquals(Phase.DONE, r.phase());
		assertEquals(2, r.dealerCards().size());
		assertEquals(List.of(Outcome.LOSE, Outcome.LOSE, Outcome.LOSE), r.seats().stream().map(s -> s.hands.getFirst().outcome).toList());
		assertNull(r.current());
	}

	@Test
	void seatsSortedAndZeroBetsSkipped() {
		List<SeatBet> bets = List.of(new SeatBet(3, new UUID(0, 3), 10), new SeatBet(1, new UUID(0, 1), 0), new SeatBet(0, new UUID(0, 0), 5));
		BlackjackRound r = new BlackjackRound(R, new Shoe(new SplittableRandom(3)::nextInt, 6), bets);
		assertEquals(List.of(0, 3), r.seats().stream().map(s -> s.seat).toList());
	}

	// ---- shoe ---------------------------------------------------------------------------------------

	@Test
	void shoeHasCutCard() {
		Shoe s = new Shoe(new SplittableRandom(1)::nextInt, 6);
		assertEquals(312, s.size());
		for (int i = 0; i < 233; i++) {
			s.draw();
		}
		assertFalse(s.needsShuffle(0.75));
		s.draw();
		assertTrue(s.needsShuffle(0.75));
		s.shuffle();
		assertEquals(0, s.dealt());
	}

	@Test
	void shoeHasEveryCardDecksTimes() {
		Shoe s = new Shoe(new SplittableRandom(5)::nextInt, 2);
		int[] counts = new int[52];
		for (int i = 0; i < 104; i++) {
			counts[s.draw().code()]++;
		}
		for (int n : counts) {
			assertEquals(2, n);
		}
	}

	@Test
	void shoeReshufflesWhenDry() {
		Shoe s = new Shoe(new SplittableRandom(2)::nextInt, 1);
		for (int i = 0; i < 60; i++) {
			s.draw();
		}
		assertEquals(8, s.dealt());
	}
}
