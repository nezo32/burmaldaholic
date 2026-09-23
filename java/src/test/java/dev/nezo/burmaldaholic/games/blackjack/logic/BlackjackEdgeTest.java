package dev.nezo.burmaldaholic.games.blackjack.logic;

import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.nezo.burmaldaholic.games.blackjack.logic.BlackjackRound.Offer;
import dev.nezo.burmaldaholic.games.blackjack.logic.BlackjackRound.Phase;
import dev.nezo.burmaldaholic.games.blackjack.logic.BlackjackRound.SeatBet;
import dev.nezo.burmaldaholic.games.blackjack.logic.BlackjackRound.Turn;
import java.util.List;
import java.util.SplittableRandom;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Monte-Carlo house edge (GAME_DESIGN §6.1, §17) with basic strategy. */
class BlackjackEdgeTest {
	private record Sim(double edge, double insuranceEdge) {}

	private static Sim simulate(int n, BlackjackRules rules, long seed, boolean insure) {
		SplittableRandom rng = new SplittableRandom(seed);
		Shoe shoe = new Shoe(rng::nextInt, rules.decks());
		long bet = 1000;
		UUID p = new UUID(0, 1);
		double wagered = 0;
		double net = 0;
		long insured = 0;
		double insuranceNet = 0;
		for (int i = 0; i < n; i++) {
			if (shoe.needsShuffle(rules.penetration())) {
				shoe.shuffle();
			}
			BlackjackRound r = new BlackjackRound(rules, shoe, List.of(new SeatBet(0, p, bet)));
			if (r.phase() == Phase.INSURANCE) {
				Offer offer = r.offer(0);
				if (offer == Offer.INSURANCE) {
					r.insure(0, insure ? BlackjackRules.maxInsurance(bet) : 0);
				} else {
					r.evenMoney(0, false);
				}
			}
			for (int guard = 0; r.phase() == Phase.TURNS && guard < 50; guard++) {
				Turn t = r.current();
				r.act(0, BasicStrategy.choose(t.hand().cards, r.upCard(), r.legal(0)));
			}
			BlackjackRound.Seat s = r.seat(0);
			double insNet = s.insuranceReturn - s.insurance;
			if (s.insurance > 0) {
				insured++;
				insuranceNet += insNet;
			}
			wagered += bet;
			net += r.returnOf(0) - r.stakedOf(0) - insNet;
		}
		return new Sim(-net / wagered, insured == 0 ? 0 : -insuranceNet / (insured * (bet / 2.0)));
	}

	@Test
	void basicStrategyEdgeNearSpec() {
		// Spec: ≈ 0.41 % (6 decks, S17, DAS, RSP4, no RSA, peek). σ ≈ 1.15/√1e6 ≈ 0.115 %; ±0.45 % ≈ 4σ.
		double edge = simulate(1_000_000, BlackjackRules.DEFAULT, 20260923L, false).edge();
		assertTrue(edge > 0.0041 - 0.0045 && edge < 0.0041 + 0.0045, "edge " + edge);
	}

	@Test
	void insuranceEdgeNearSpec() {
		// Spec: 7.47 % (6 decks). ~30 000 insurance bets, σ ≈ 1.6 %.
		double edge = simulate(400_000, BlackjackRules.DEFAULT, 7L, true).insuranceEdge();
		assertTrue(edge > 0.0747 - 0.06 && edge < 0.0747 + 0.06, "insurance edge " + edge);
	}

	@Test
	void sixToFiveCostsAboutOnePointFourPercent() {
		double a = simulate(300_000, BlackjackRules.DEFAULT, 99L, false).edge();
		double b = simulate(300_000, BlackjackRules.DEFAULT.withBlackjackPayout(1.2), 99L, false).edge();
		assertTrue(b - a > 0.009 && b - a < 0.019, "6:5 delta " + (b - a));
	}

	@Test
	void hitSoft17CostsThePlayer() {
		// Spec: H17 adds ≈ 0.22 %. Same seed → correlated samples; just check the sign within noise.
		double s17 = simulate(400_000, BlackjackRules.DEFAULT, 11L, false).edge();
		double h17 = simulate(400_000, BlackjackRules.DEFAULT.withDealerHitsSoft17(true), 11L, false).edge();
		assertTrue(h17 - s17 > -0.002 && h17 - s17 < 0.008, "H17 delta " + (h17 - s17));
	}
}
