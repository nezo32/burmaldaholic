package dev.nezo.burmaldaholic.games.baccarat.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.nezo.burmaldaholic.games.baccarat.logic.BaccaratRules.Side;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** §20.9 chemin de fer bank: coverage, snapping, Banco, settlement and rake. */
class ChemmyBankTest {
	@Test
	void coverageAndSnapping() {
		ChemmyBank<String> bank = new ChemmyBank<>();
		bank.take("alex", 1200);
		assertEquals(1000, bank.coverage(1000), "C = min(B, banker's max)");
		ChemmyBank.Punt p = bank.checkPunt("bob", 700, 1, 1000, 1000);
		assertEquals(700, p.accepted());
		bank.addPunt("bob", 700);
		ChemmyBank.Punt q = bank.checkPunt("cid", 500, 1, 1000, 1000);
		assertEquals(300, q.accepted(), "the bet crossing C is snapped to the open coverage");
		bank.addPunt("cid", 300);
		assertEquals(ChemmyBank.PuntError.COVERAGE, bank.checkPunt("dan", 5, 1, 1000, 1000).error().orElseThrow());
		assertEquals(ChemmyBank.PuntError.IS_BANKER, bank.checkPunt("alex", 5, 1, 1000, 1000).error().orElseThrow());
		assertEquals(ChemmyBank.PuntError.OVER_MAX, bank.checkPunt("bob", 1, 1, 700, 1000).error().orElseThrow(), "own max reached");
		ChemmyBank<String> small = new ChemmyBank<>();
		small.take("x", 100);
		assertEquals(ChemmyBank.PuntError.TOO_LOW, small.checkPunt("y", 5, 10, 100, 100).error().orElseThrow());
	}

	@Test
	void bancoRefundsTheOthers() {
		ChemmyBank<String> bank = new ChemmyBank<>();
		bank.take("alex", 500);
		bank.addPunt("bob", 100);
		bank.addPunt("cid", 50);
		Map<String, Long> refunded = bank.banco("dan");
		assertEquals(Map.of("bob", 100L, "cid", 50L), refunded);
		bank.addPunt("dan", 500);
		assertEquals("dan", bank.banco());
		assertEquals(ChemmyBank.PuntError.BANCO_CALLED, bank.checkPunt("bob", 10, 1, 1000, 1000).error().orElseThrow());
	}

	@Test
	void settlementConservesChipsMinusRake() {
		// bank wins: W = 300, rake 5 % = 15, B += 285
		ChemmyBank<String> bank = new ChemmyBank<>();
		bank.take("alex", 1000);
		bank.addPunt("bob", 200);
		bank.addPunt("cid", 100);
		ChemmyBank.Settlement<String> s = bank.settle(Side.BANKER, 500);
		assertEquals(300, s.bankerWin());
		assertEquals(15, s.rake());
		assertEquals(1285, bank.bank());
		assertEquals(0L, s.punterReturns().get("bob"));
		assertEquals(1, bank.wins());
		// player wins: each punter paid 1:1 from the bank
		bank.addPunt("bob", 85);
		s = bank.settle(Side.PLAYER, 500);
		assertEquals(170L, s.punterReturns().get("bob"));
		assertEquals(1200, bank.bank());
		assertEquals(0, bank.wins(), "a lost coup ends the run");
		// tie: stakes push
		bank.addPunt("cid", 40);
		s = bank.settle(Side.TIE, 500);
		assertEquals(40L, s.punterReturns().get("cid"));
		assertEquals(1200, bank.bank());
		assertEquals(1000, bank.invested());
		assertEquals(1200, bank.close());
		assertTrue(!bank.held());
	}

	@Test
	void rakeIsFloored() {
		ChemmyBank<String> bank = new ChemmyBank<>();
		bank.take("alex", 100);
		bank.addPunt("bob", 19);
		assertEquals(0, bank.settle(Side.BANKER, 500).rake(), "floor(19 × 0.05) = 0");
	}
}
