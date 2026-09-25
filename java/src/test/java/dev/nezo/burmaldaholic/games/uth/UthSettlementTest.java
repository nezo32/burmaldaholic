package dev.nezo.burmaldaholic.games.uth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.nezo.burmaldaholic.games.uth.logic.PayHand;
import dev.nezo.burmaldaholic.games.uth.logic.Paytables;
import dev.nezo.burmaldaholic.games.uth.logic.Settlement;
import dev.nezo.burmaldaholic.games.uth.logic.Settlement.Outcome;
import dev.nezo.burmaldaholic.games.uth.logic.Settlement.Result;
import dev.nezo.burmaldaholic.games.uth.logic.UthCards;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** GAME_DESIGN §21.1: the six test vectors, every row of the settlement table, paytables and rounding. */
class UthSettlementTest {
	private static final Paytables P = Paytables.DEFAULT;

	private static int[] c(String s) {
		return UthCards.parseAll(s);
	}

	private static Result settle(long ante, int playMult, long trips, boolean folded, String hole, String dealer, String board) {
		return Settlement.settle(ante, playMult * ante, trips, folded, c(hole), c(dealer), c(board), P);
	}

	@Test
	void vector1RoyalFlushDealerDoesNotQualify() {
		Result r = settle(10, 4, 5, false, "As Ks", "7d 2c", "Qs Js Ts 3h 4d");
		assertEquals(PayHand.ROYAL, r.hand());
		assertFalse(r.dealerQualifies());
		assertEquals(40, r.playNet());
		assertEquals(0, r.anteNet());
		assertEquals(5000, r.blindNet());
		assertEquals(250, r.tripsNet());
		assertEquals(5290, r.net());
		assertEquals(r.staked() + 5290, r.totalReturn());
	}

	@Test
	void vector2TripsBeatKings() {
		Result r = settle(10, 4, 10, false, "9h 9c", "Kd Kc", "9d 5s 2h Jc 3d");
		assertTrue(r.dealerQualifies());
		assertEquals(Outcome.WIN, r.outcome());
		assertEquals(40, r.playNet());
		assertEquals(10, r.anteNet());
		assertEquals(0, r.blindNet());
		assertEquals(30, r.tripsNet());
		assertEquals(80, r.net());
	}

	@Test
	void vector3FoldLosesAnteBlindAndTrips() {
		Result r = settle(10, 0, 10, true, "Qc 7d", "Ah Ad", "2s 5h 9d Js 3c");
		assertEquals(Outcome.FOLDED, r.outcome());
		assertEquals(-30, r.net());
		assertEquals(0, r.totalReturn());
	}

	@Test
	void vector4BothPlayTheBoard() {
		Result r = settle(5, 1, 5, false, "2c 3d", "4s 5d", "As Ah Kd Kc Qh");
		assertEquals(Outcome.TIE, r.outcome());
		assertEquals(0, r.playNet() + r.anteNet() + r.blindNet());
		assertEquals(-5, r.tripsNet());
		assertEquals(-5, r.net());
	}

	@Test
	void vector5DealerAceHighDoesNotQualifyButWins() {
		Result r = settle(10, 1, 0, false, "7c 2d", "Ac 8d", "Ks Qh 9c 5d 3s");
		assertEquals(Outcome.LOSE, r.outcome());
		assertFalse(r.dealerQualifies());
		assertEquals(-10, r.playNet());
		assertEquals(0, r.anteNet());
		assertEquals(-10, r.blindNet());
		assertEquals(-20, r.net());
	}

	@Test
	void vector6OddAnteFlushFloorsTheBlind() {
		// A = 5, the seat wins with a flush: Blind 3:2 → floor(7.5) = 7
		Result r = settle(5, 4, 0, false, "Ah 2h", "Kc Kd", "9h 7h 4h Ts 3c");
		assertEquals(PayHand.FLUSH, r.hand());
		assertEquals(Outcome.WIN, r.outcome());
		assertEquals(7, r.blindNet());
		assertEquals(P.blindWin(PayHand.FLUSH, 5), 7);
		assertEquals(3, P.blindWin(PayHand.FLUSH, 2));
	}

	@Test
	void everyRowOfTheSettlementTable() {
		int flushWin = UthCards.evaluate(c("Ah 2h 9h 7h 4h Ts 3c"));
		int pairDealer = UthCards.evaluate(c("Kc Kd 9h 7h 4h Ts 3c"));
		int highDealer = UthCards.evaluate(c("Qc 8d 9s 7h 4h Ts 3c"));
		int weak = UthCards.evaluate(c("2c 5d 9s 7h 4h Ts 3c"));
		int pairPlayer = UthCards.evaluate(c("Ac Ad 9s 7h 4h Ts 2c"));
		// wins, dealer qualifies: +P +A Blind paytable
		Result r = Settlement.settle(10, 20, 0, false, flushWin, pairDealer, P);
		assertEquals(20, r.playNet());
		assertEquals(10, r.anteNet());
		assertEquals(15, r.blindNet());
		// wins with a non-paying hand: Blind pushes
		r = Settlement.settle(10, 20, 0, false, pairPlayer, pairDealer, P);
		assertEquals(Outcome.WIN, r.outcome());
		assertEquals(0, r.blindNet());
		// wins, dealer does not qualify: +P, Ante push, Blind paytable
		r = Settlement.settle(10, 40, 0, false, flushWin, highDealer, P);
		assertEquals(40, r.playNet());
		assertEquals(0, r.anteNet());
		assertEquals(15, r.blindNet());
		// loses, dealer qualifies: −P −A −A
		r = Settlement.settle(10, 20, 0, false, weak, pairDealer, P);
		assertEquals(-20, r.playNet());
		assertEquals(-10, r.anteNet());
		assertEquals(-10, r.blindNet());
		// loses, dealer does not qualify: −P, Ante push, −A
		r = Settlement.settle(10, 20, 0, false, weak, highDealer, P);
		assertEquals(-20, r.playNet());
		assertEquals(0, r.anteNet());
		assertEquals(-10, r.blindNet());
		// tie: everything pushes
		r = Settlement.settle(10, 20, 0, false, pairDealer, pairDealer, P);
		assertEquals(0, r.net());
		// folded: −A −A, no Play
		r = Settlement.settle(10, 0, 0, true, flushWin, pairDealer, P);
		assertEquals(-20, r.net());
		assertEquals(0, r.playNet());
	}

	@Test
	void tripsPaysWinOrLoseEvenAfterAFold() {
		int trips = UthCards.evaluate(c("9h 9c 9d 5s 2h Jc 3d"));
		int quadsDealer = UthCards.evaluate(c("Kh Kc Kd Ks 2h Jc 3d"));
		Result r = Settlement.settle(10, 0, 10, true, trips, quadsDealer, P);
		assertEquals(30, r.tripsNet());
		assertEquals(-20 + 30, r.net());
		r = Settlement.settle(10, 40, 10, false, trips, quadsDealer, P);
		assertEquals(30, r.tripsNet());
	}

	@Test
	void payHandClassesAndPaytables() {
		assertEquals(PayHand.ROYAL, PayHand.of(UthCards.evaluate(c("As Ks Qs Js Ts 2c 3d"))));
		assertEquals(PayHand.STRAIGHT_FLUSH, PayHand.of(UthCards.evaluate(c("9s Ks Qs Js Ts 2c 3d"))));
		assertEquals(PayHand.STRAIGHT_FLUSH, PayHand.of(UthCards.evaluate(c("As 2s 3s 4s 5s 9c Td"))));
		assertEquals(PayHand.QUADS, PayHand.of(UthCards.evaluate(c("9s 9h 9d 9c Ts 2c 3d"))));
		assertEquals(PayHand.FULL_HOUSE, PayHand.of(UthCards.evaluate(c("9s 9h 9d Tc Ts 2c 3d"))));
		assertEquals(PayHand.STRAIGHT, PayHand.of(UthCards.evaluate(c("As 2h 3d 4c 5s 9c Td"))));
		assertEquals(PayHand.NONE, PayHand.of(UthCards.evaluate(c("As Ah 3d 3c 5s 9c Td"))));
		int[] trips = {50, 40, 30, 8, 6, 5, 3};
		double[] blind = {500, 50, 10, 3, 1.5, 1, 0};
		PayHand[] order = {PayHand.ROYAL, PayHand.STRAIGHT_FLUSH, PayHand.QUADS, PayHand.FULL_HOUSE, PayHand.FLUSH, PayHand.STRAIGHT, PayHand.TRIPS};
		for (int i = 0; i < order.length; i++) {
			assertEquals(trips[i], P.tripsPay(order[i]), order[i].name());
			assertEquals(blind[i], P.blindPay(order[i]), order[i].name());
		}
		assertEquals(0, P.tripsPay(PayHand.NONE));
	}

	@Test
	void worstCaseReservation() {
		assertEquals(505, P.anteFactor());
		assertEquals(50, P.tripsFactor());
		assertEquals(5550, P.worstCase(10, 10)); // §21.6 example
		Paytables variant = new Paytables(Map.of("royal", 1000.0), Map.of("royal", 100));
		assertEquals(1005, variant.anteFactor());
		assertEquals(100, variant.tripsFactor());
	}
}
