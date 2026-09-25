package dev.nezo.burmaldaholic.core.economy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class LedgerTest {
	private static final long MAX = 1_000_000_000L;
	private final UUID a = UUID.randomUUID();
	private final UUID b = UUID.randomUUID();

	private static Ledger.Leg leg(AccountId id, long delta) {
		return new Ledger.Leg(id, delta);
	}

	@Test
	void debitNeedsFundsAndIsAllOrNothing() {
		Ledger l = new Ledger();
		l.setBalance(a, 100, MAX);
		Ledger.Commit c = l.commit(List.of(leg(AccountId.player(a), -60), leg(AccountId.player(b), 60)), MAX, null);
		assertTrue(c.ok());
		assertEquals(40, l.balance(a));
		assertEquals(60, l.balance(b));
		Ledger.Commit fail = l.commit(List.of(leg(AccountId.player(b), 10), leg(AccountId.player(a), -50)), MAX, null);
		assertFalse(fail.ok());
		assertEquals(AccountId.player(a), fail.failed());
		assertEquals(40, l.balance(a), "nothing applied");
		assertEquals(60, l.balance(b), "nothing applied");
	}

	@Test
	void legsAreNettedPerAccount() {
		Ledger l = new Ledger();
		l.setBalance(a, 10, MAX);
		// debit 15 + credit 20 on the same account nets +5: allowed even though 15 > 10
		assertTrue(l.commit(List.of(leg(AccountId.player(a), -15), leg(AccountId.player(a), 20)), MAX, null).ok());
		assertEquals(15, l.balance(a));
	}

	@Test
	void houseIsInfiniteAndBalanceIsCapped() {
		Ledger l = new Ledger();
		l.setBalance(a, 990, 1000);
		Ledger.Commit c = l.commit(List.of(leg(AccountId.HOUSE, -50), leg(AccountId.player(a), 50)), 1000, null);
		assertTrue(c.ok());
		assertEquals(1000, l.balance(a));
		assertEquals(40, c.lostToCap());
	}

	@Test
	void creditFilterGarnishesOnlyPositiveNet() {
		Ledger l = new Ledger();
		Ledger.Commit c = l.commit(List.of(leg(AccountId.HOUSE, -100), leg(AccountId.player(a), 100)), MAX, e -> e.getValue() / 2);
		assertTrue(c.ok());
		assertEquals(50, l.balance(a));
	}

	@Test
	void bankrollReservationsLimitAvailableFunds() {
		Ledger l = new Ledger();
		l.openBankroll("casino", a);
		l.setBalance(b, 500, MAX);
		assertTrue(l.commit(List.of(leg(AccountId.player(b), -500), leg(AccountId.bankroll("casino"), 500)), MAX, null).ok());
		assertTrue(l.reserve("casino", 300));
		assertFalse(l.reserve("casino", 201), "available = 500 − 300");
		assertEquals(200, l.bankroll("casino").orElseThrow().available());
		l.release("casino", 300);
		assertEquals(500, l.bankroll("casino").orElseThrow().available());
		assertFalse(l.commit(List.of(leg(AccountId.bankroll("casino"), -600), leg(AccountId.player(b), 600)), MAX, null).ok());
		assertFalse(l.commit(List.of(leg(AccountId.bankroll("missing"), -1), leg(AccountId.player(b), 1)), MAX, null).ok());
		assertEquals(500, l.closeBankroll("casino"));
	}

	@Test
	void lateCreditsToAClosedBankrollGoToItsOwner() {
		// review wave 2, M1: bots / PvP / chemin de fer chips that come back after the charter closed
		Ledger l = new Ledger();
		UUID owner = UUID.randomUUID();
		l.setNow(100);
		l.openBankroll("c1", owner);
		l.setBalance(b, 1000, MAX);
		assertTrue(l.commit(List.of(leg(AccountId.player(b), -300), leg(AccountId.bankroll("c1"), 300)), MAX, null).ok());
		assertEquals(300, l.closeBankroll("c1"));
		assertEquals(owner, l.closedOwner("c1").orElseThrow());
		// a late credit (house → closed bankroll) reaches the owner; the bankroll is not re-created
		l.setNow(500);
		Ledger.Commit late = l.commit(List.of(leg(AccountId.HOUSE, -120), leg(AccountId.bankroll("c1"), 120)), MAX, null);
		assertTrue(late.ok());
		assertEquals(120, l.balance(owner));
		assertEquals(java.util.Map.of("c1", 120L), late.lateReturns());
		assertTrue(l.bankroll("c1").isEmpty(), "never re-created");
		assertEquals(500, l.tombstones().get("c1").touched(), "a late credit refreshes the grace");
		// a net debit of a closed bankroll fails the whole batch
		Ledger.Commit debit = l.commit(List.of(leg(AccountId.bankroll("c1"), -1), leg(AccountId.player(b), 1)), MAX, null);
		assertFalse(debit.ok());
		assertEquals(AccountId.bankroll("c1"), debit.failed());
		assertEquals(700, l.balance(b));
		// reservations are refused
		assertFalse(l.reserve("c1", 1));
		// an unknown id still fails (no tombstone, nothing to route to)
		assertFalse(l.commit(List.of(leg(AccountId.HOUSE, -5), leg(AccountId.bankroll("never"), 5)), MAX, null).ok());
	}

	@Test
	void tombstonesArePrunedOnceNothingRefersToThem() {
		Ledger l = new Ledger();
		UUID owner = UUID.randomUUID();
		l.setNow(0);
		l.openBankroll("c1", owner);
		l.openBankroll("c2", owner);
		l.closeBankroll("c1");
		l.closeBankroll("c2");
		// inside the grace: kept even without references
		assertEquals(List.of(), l.pruneTombstones(100, 6000, id -> false));
		// after the grace: a referenced id stays (bots funded by it, a live PvP match), the other goes
		assertEquals(List.of("c2"), l.pruneTombstones(6000, 6000, id -> id.equals("c1")));
		assertTrue(l.closedOwner("c1").isPresent());
		assertTrue(l.closedOwner("c2").isEmpty());
		assertEquals(List.of("c1"), l.pruneTombstones(7000, 6000, id -> false));
		assertTrue(l.tombstones().isEmpty());
		// a new charter re-using the id clears a tombstone
		l.openBankroll("c3", owner);
		l.closeBankroll("c3");
		l.openBankroll("c3", owner);
		assertTrue(l.closedOwner("c3").isEmpty());
	}
}
