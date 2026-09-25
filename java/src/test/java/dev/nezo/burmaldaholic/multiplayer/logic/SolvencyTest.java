package dev.nezo.burmaldaholic.multiplayer.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.nezo.burmaldaholic.core.economy.AccountId;
import dev.nezo.burmaldaholic.core.economy.Economy;
import dev.nezo.burmaldaholic.core.economy.Ledger;
import java.util.List;
import java.util.SplittableRandom;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SolvencyTest {
	@Test
	void worstCaseTableMatchesTheSpecExamples() {
		assertEquals(36, Solvency.worstCasePerChip("roulette", "roulette_table"));
		assertEquals(17.5, Solvency.worstCasePerChip("blackjack", "blackjack_table"));
		assertEquals(13, Solvency.worstCasePerChip("craps", null));
		assertEquals(500, Solvency.worstCasePerChip("slots", "slot_machine_copper"));
		assertEquals(2000, Solvency.worstCasePerChip("slots", "slot_machine_gold"));
		assertEquals(5000, Solvency.worstCasePerChip("slots", "slot_machine_netherite"));
		assertEquals(5000, Solvency.worstCasePerChip("slots", "unknown_machine"));
		assertEquals(170, Solvency.worstCasePerChip("extras", "plinko_machine"));
		assertEquals(0, Solvency.worstCasePerChip("poker", "poker_table"));
		assertEquals(Solvency.UNKNOWN_WORST_CASE_PER_CHIP, Solvency.worstCasePerChip("mystery", "mystery_table"));
	}

	@Test
	void worstCaseRoundsUpToWholeChips() {
		assertEquals(18, Solvency.worstCaseAt(17.5, 1));
		assertEquals(35, Solvency.worstCaseAt(17.5, 2));
		assertEquals(0, Solvency.worstCaseAt(0, 100));
		assertEquals(0, Solvency.worstCaseAt(36, 0));
	}

	@Test
	void insolvencyUsesTheCheapestOpenExposedTable() {
		List<Solvency.ExposedTable> tables = List.of(
			new Solvency.ExposedTable("roulette", "roulette_table", 5, true),      // 180
			new Solvency.ExposedTable("blackjack", "blackjack_table", 10, true),   // 175
			new Solvency.ExposedTable("extras", "wheel_of_fortune", 1, false),     // closed: ignored
			new Solvency.ExposedTable("poker", "poker_table", 1, true));           // no exposure
		assertEquals(175, Solvency.cheapestWorstCase(tables).orElseThrow());
		assertTrue(Solvency.isBroke(174, tables));
		assertFalse(Solvency.isBroke(175, tables));
	}

	@Test
	void casinoWithoutExposureIsNeverBroke() {
		assertFalse(Solvency.isBroke(0, List.of(new Solvency.ExposedTable("poker", "poker_table", 1, true))));
		assertFalse(Solvency.isBroke(0, List.of()));
		assertEquals(18, Solvency.cheapestWorstCase(List.of(new Solvency.ExposedTable("blackjack", null, 0, true))).orElseThrow(),
			"minimum bet below 1 counts as 1");
	}

	@Test
	void withdrawalsAreLimitedToTheUnreservedBankroll() {
		assertEquals(400, Solvency.withdrawable(1000, 600));
		assertEquals(0, Solvency.withdrawable(500, 600));
		assertEquals(500, Solvency.withdrawable(500, 0));
	}

	// ---- Monte-Carlo with core's real ledger (same legs as CasinoTableBlockEntity#placeBet / #settle) -------

	private static final long MAX = 1_000_000_000_000L;
	private static final String BANK = "multiplayer:charter/c1";

	private record Sim(double edge, int refused, int violations) {}

	/** Plays rounds against an owner bankroll: reserve worst case, stake → bankroll, payout ← bankroll, release. */
	private static Sim simulate(String game, int rounds, long bankroll, long bet, long seed) {
		Ledger ledger = new Ledger();
		UUID owner = UUID.randomUUID();
		UUID player = UUID.randomUUID();
		ledger.openBankroll(BANK, owner);
		ledger.commit(List.of(new Ledger.Leg(AccountId.bankroll(BANK), bankroll)), MAX, null);
		SplittableRandom rng = new SplittableRandom(seed);
		long handle = 0;
		int refused = 0;
		int violations = 0;
		for (int i = 0; i < rounds; i++) {
			ledger.setBalance(player, 1_000_000, MAX);
			long wc = Solvency.worstCaseAt(Solvency.worstCasePerChip(game, null), bet);
			if (!ledger.reserve(BANK, wc)) {
				refused++;
				continue;
			}
			assertTrue(ledger.commit(List.of(new Ledger.Leg(AccountId.player(player), -bet), new Ledger.Leg(AccountId.bankroll(BANK), bet)), MAX, null).ok());
			long payout = switch (game) {
				case "roulette" -> rng.nextInt(37) == 17 ? bet * 36 : 0;          // straight-up 35:1
				case "coin_flip" -> rng.nextBoolean() ? (long) Math.floor(bet * 1.96) : 0;
				default -> throw new IllegalArgumentException(game);
			};
			if (payout > wc) {
				violations++;
			}
			ledger.release(BANK, wc);
			if (payout > 0 && !ledger.commit(List.of(new Ledger.Leg(AccountId.bankroll(BANK), -payout), new Ledger.Leg(AccountId.player(player), payout)), MAX, null).ok()) {
				violations++;
			}
			Economy.BankrollInfo info = ledger.bankroll(BANK).orElseThrow();
			if (info.balance() < 0 || info.reserved() != 0) {
				violations++;
			}
			handle += bet;
		}
		long end = ledger.bankroll(BANK).orElseThrow().balance();
		return new Sim((double) (end - bankroll) / handle, refused, violations);
	}

	@Test
	void ownerEarnsTheRouletteEdge() {
		Sim s = simulate("roulette", 2_000_000, 100_000, 25, 7);
		assertEquals(0, s.refused());
		assertEquals(0, s.violations());
		// edge 1/37 = 2.70 %; σ ≈ 5.84 / √2e6 ≈ 0.41 % → tolerance ≈ 3.6σ
		assertEquals(1.0 / 37, s.edge(), 0.015);
	}

	@Test
	void ownerEarnsTheCoinFlipEdge() {
		Sim s = simulate("coin_flip", 1_000_000, 100_000, 25, 11);
		assertEquals(0, s.violations());
		// 25 × 1.96 = 49 whole chips: exact 2 % edge; σ ≈ 0.1 %
		assertEquals(0.02, s.edge(), 0.004);
	}

	@Test
	void tinyBankrollRefusesUncoverableBetsAndNeverGoesNegative() {
		Sim s = simulate("roulette", 20_000, 100, 5, 3);
		assertEquals(0, s.violations());
		assertTrue(s.refused() > 0, "a 100-chip bankroll cannot cover 180-chip exposures");
	}
}
