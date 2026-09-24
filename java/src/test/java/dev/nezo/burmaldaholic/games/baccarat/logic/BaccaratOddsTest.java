package dev.nezo.burmaldaholic.games.baccarat.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.nezo.burmaldaholic.games.baccarat.logic.BaccaratRules.Coup;
import dev.nezo.burmaldaholic.games.baccarat.logic.BaccaratRules.Side;
import java.util.SplittableRandom;
import org.junit.jupiter.api.Test;

/** GAME_DESIGN §20.2 / §20.8: exact enumeration by value classes, edges, and a shoe simulation. */
class BaccaratOddsTest {
	private static final double EPS = 5e-7;

	@Test
	void eightDeckCountsMatchTheSpecExactly() {
		BaccaratOdds.Counts c = BaccaratOdds.of(8);
		assertEquals(4_998_398_275_503_360L, c.denominator(), "416·415·414·413·412·411");
		assertEquals(2_292_252_566_437_888L, c.banker());
		assertEquals(2_230_518_282_592_256L, c.player());
		assertEquals(475_627_426_473_216L, c.tie());
		assertEquals(c.denominator(), c.banker() + c.player() + c.tie(), "every leaf counted once");
	}

	@Test
	void everyDeckCountSumsToTheDenominator() {
		for (int d = 1; d <= 8; d++) {
			BaccaratOdds.Counts c = BaccaratOdds.of(d);
			long n = 52L * d;
			assertEquals(n * (n - 1) * (n - 2) * (n - 3) * (n - 4) * (n - 5), c.denominator());
			assertEquals(c.denominator(), c.banker() + c.player() + c.tie(), "decks " + d);
			assertTrue(c.banker() > c.player(), "Banker is always favoured, decks " + d);
		}
	}

	@Test
	void pairProbabilityIsByRank() {
		assertEquals(31.0 / 415, BaccaratOdds.of(8).pPair(), 1e-15);
		assertEquals(3.0 / 51, BaccaratOdds.of(1).pPair(), 1e-15);
	}

	@Test
	void houseEdgesOfTheDefaultPaytable() {
		BaccaratOdds.Counts c = BaccaratOdds.of(8);
		Paytable pay = Paytable.DEFAULT;
		assertEquals(0.458597, c.pBanker(), EPS);
		assertEquals(0.446247, c.pPlayer(), EPS);
		assertEquals(0.095156, c.pTie(), EPS);
		assertEquals(0.010579, c.edge(BetKind.BANKER, pay), 1e-6, "Banker 1.06 %");
		assertEquals(0.012351, c.edge(BetKind.PLAYER, pay), 1e-6, "Player 1.24 %");
		assertEquals(0.143596, c.edge(BetKind.TIE, pay), 1e-6, "Tie 14.36 %");
		assertEquals(43.0 / 415, c.edge(BetKind.PLAYER_PAIR, pay), 1e-12, "pairs 10.36 %");
		assertEquals(43.0 / 415, c.edge(BetKind.BANKER_PAIR, pay), 1e-12);
		assertEquals(0.0484, c.edge(BetKind.TIE, new Paytable(500, 9, 11)), 1e-4, "Tie 9:1 → 4.84 %");
		assertTrue(c.edge(BetKind.PLAYER_PAIR, new Paytable(500, 8, 12)) > 0, "pairPays 12 still favours the house");
		assertTrue(c.edge(BetKind.PLAYER_PAIR, new Paytable(500, 8, 13)) < 0, "13:1 would favour the player (hence the cap)");
	}

	@Test
	void chemmyEdges() {
		BaccaratOdds.Counts c = BaccaratOdds.of(8);
		// §20.9: banker −1.06 %, punters −1.24 %, the house rake +2.29 % of covered chips
		double banker = 0.95 * c.pBanker() - c.pPlayer();
		assertEquals(-0.010579, banker, 1e-6);
		assertEquals(0.0229, 0.05 * c.pBanker(), 1e-4);
	}

	/** §20.8: 10⁶ coups from a real shoe (burn, penetration) within 0.3 % of the exact probabilities. */
	@Test
	void shoeSimulationMatchesTheExactOdds() {
		SplittableRandom random = new SplittableRandom(20260924L);
		BaccaratShoe shoe = new BaccaratShoe(8);
		int coups = 1_000_000;
		long banker = 0, player = 0, tie = 0, pPair = 0, bPair = 0;
		for (int i = 0; i < coups; i++) {
			if (shoe.needsShuffle(0.8, 8)) {
				int burned = shoe.shuffle(random::nextInt, 8, true);
				assertTrue(burned >= 2 && burned <= 11, "burn 2–11");
			}
			Coup coup = BaccaratRules.deal(() -> shoe.draw(random::nextInt));
			Side w = coup.winner();
			if (w == Side.BANKER) {
				banker++;
			} else if (w == Side.PLAYER) {
				player++;
			} else {
				tie++;
			}
			pPair += coup.playerPair() ? 1 : 0;
			bPair += coup.bankerPair() ? 1 : 0;
		}
		BaccaratOdds.Counts c = BaccaratOdds.of(8);
		assertEquals(c.pBanker(), (double) banker / coups, 0.003);
		assertEquals(c.pPlayer(), (double) player / coups, 0.003);
		assertEquals(c.pTie(), (double) tie / coups, 0.003);
		assertEquals(c.pPair(), (double) pPair / coups, 0.003);
		assertEquals(c.pPair(), (double) bPair / coups, 0.003);
	}
}
