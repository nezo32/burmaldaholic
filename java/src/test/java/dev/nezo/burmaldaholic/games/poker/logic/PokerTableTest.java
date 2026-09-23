package dev.nezo.burmaldaholic.games.poker.logic;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.nezo.burmaldaholic.games.poker.logic.Hand.Action;
import java.util.List;
import java.util.Map;
import java.util.SplittableRandom;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class PokerTableTest {
	private static final Pots.RakeConfig RAKE = new Pots.RakeConfig(0.05, 3, true);
	private final PokerRng rng = PokerRng.of(new SplittableRandom(3));
	private final AtomicInteger ids = new AtomicInteger();

	private PokerTable.FillResult fill(PokerTable t, boolean enabled) {
		return t.fillBots(new PokerTable.BotFill(enabled, new int[] {50, 40, 10}, 200), rng, () -> "bot:" + ids.incrementAndGet());
	}

	@Test
	void stakeLevels() {
		assertEquals(1, StakeLevel.smallBlind(2));
		assertEquals(1, StakeLevel.smallBlind(3));
		assertEquals(100, StakeLevel.smallBlind(200));
		assertEquals(StakeLevel.MID, StakeLevel.byId("mid"));
		assertNull(StakeLevel.byId("huge"));
		assertEquals(4, StakeLevel.HIGH.minTier(), "High needs Diamond");
		assertArrayEquals(new long[] {80, 200}, StakeLevel.buyInRange(2, 40, 100, 10_000, 0));
		assertArrayEquals(new long[] {80, 150}, StakeLevel.buyInRange(2, 40, 100, 150, 0), "clamped by the balance");
		assertArrayEquals(new long[] {80, 50}, StakeLevel.buyInRange(2, 40, 100, 50, 0), "max < min: cannot sit");
		assertArrayEquals(new long[] {1, 70}, StakeLevel.buyInRange(2, 40, 100, 10_000, 130), "top up to 100 BB");
		assertArrayEquals(new long[] {1, 0}, StakeLevel.buyInRange(2, 40, 100, 10_000, 250), "already above the max");
	}

	@Test
	void botTargetKeepsASeatForWalkIns() {
		PokerTable t = new PokerTable(6, 10, RAKE);
		assertEquals(0, t.botTarget(true), "no humans, no bots");
		t.addHuman("a", "A", 1000);
		assertEquals(4, t.botTarget(true));
		assertEquals(0, t.botTarget(false));
		t.addHuman("b", "B", 1000);
		assertEquals(3, t.botTarget(true));
		PokerTable two = new PokerTable(2, 10, RAKE);
		two.addHuman("a", "A", 1000);
		assertEquals(1, two.botTarget(true), "a lone human always gets an opponent");
	}

	@Test
	void fillBotsAddsRemovesAndReplacesBustedBots() {
		PokerTable t = new PokerTable(6, 10, RAKE);
		t.addHuman("a", "A", 1000);
		PokerTable.FillResult r = fill(t, true);
		assertEquals(4, r.joined().size());
		assertEquals(4, t.bots().size());
		assertEquals(4, t.bots().stream().map(s -> s.name).distinct().count(), "unique names");
		assertTrue(t.bots().stream().allMatch(s -> s.stack == 200 && s.tier != null));
		t.bots().get(0).stack = 0;
		r = fill(t, true);
		assertEquals(1, r.left().size());
		assertEquals(1, r.joined().size());
		t.addHuman("b", "B", 1000);
		r = fill(t, true);
		assertEquals(1, r.left().size(), "surplus bot leaves for the second human");
		assertEquals(3, t.bots().size());
		fill(t, false);
		assertEquals(0, t.bots().size());
	}

	@Test
	void buttonMovesClockwiseAndSkipsEmptySeats() {
		PokerTable t = new PokerTable(6, 10, RAKE);
		t.addHuman("a", "A", 1000);
		t.addHuman("b", "B", 1000);
		t.addHuman("c", "C", 1000);
		t.removeSeat("b");
		Hand h = t.startHand(rng);
		assertNotNull(h);
		assertEquals(0, t.button());
		h.apply(Action.fold());
		assertTrue(h.complete());
		t.settleHand();
		t.startHand(rng);
		assertEquals(2, t.button(), "seat 1 is empty");
		assertEquals(2, t.handNo());
	}

	@Test
	void settleCopiesStacksAndTracksVpip() {
		PokerTable t = new PokerTable(6, 10, RAKE);
		t.addHuman("a", "A", 1000);
		t.addHuman("b", "B", 1000);
		Hand h = t.startHand(rng);
		// heads-up: button (a) posts SB and acts first
		h.apply(Action.raiseTo(40));
		h.apply(Action.fold());
		assertEquals(1000, t.seatOf("a").stack, "seat stacks update only at settle");
		assertEquals(1010, t.liveStack("a"));
		t.settleHand();
		assertEquals(1010, t.seatOf("a").stack);
		assertEquals(990, t.seatOf("b").stack);
		assertEquals(List.of(true), List.copyOf(t.seatOf("a").vpipHistory));
		assertEquals(List.of(false), List.copyOf(t.seatOf("b").vpipHistory));
	}

	@Test
	void abortHandRestoresStartStacks() {
		PokerTable t = new PokerTable(6, 10, RAKE);
		t.addHuman("a", "A", 1000);
		t.addHuman("b", "B", 1000);
		Hand h = t.startHand(rng);
		h.apply(Action.raiseTo(500));
		assertEquals(1000, t.refundableStack("a"));
		t.abortHand();
		assertFalse(t.inHand());
		assertEquals(1000, t.seatOf("a").stack);
		assertEquals(1000, t.seatOf("b").stack);
	}

	@Test
	void timeoutsSitOutAndRemoval() {
		PokerTable t = new PokerTable(6, 10, RAKE);
		t.addHuman("a", "A", 1000);
		assertFalse(t.recordTimeout("a", 2));
		assertTrue(t.recordTimeout("a", 2), "second consecutive timeout sits out");
		assertTrue(t.seatOf("a").sittingOut);
		assertTrue(t.toRemove(3).isEmpty());
		t.countIdleHand();
		t.countIdleHand();
		t.countIdleHand();
		assertEquals(1, t.toRemove(3).size(), "sitting out for 3 hands");
		t.sitIn("a");
		assertTrue(t.toRemove(3).isEmpty());
		t.recordTimeout("a", 2);
		t.recordAction("a");
		assertFalse(t.recordTimeout("a", 2), "a manual action resets the streak");
		t.seatOf("a").leaving = true;
		assertEquals(1, t.toRemove(3).size());
	}

	@Test
	void sittingOutHumansAloneDoNotStartHands() {
		PokerTable t = new PokerTable(6, 10, RAKE);
		t.addHuman("a", "A", 1000);
		fill(t, true);
		t.sitOut("a");
		assertFalse(t.canStart());
		t.sitIn("a");
		assertTrue(t.canStart());
	}

	@Test
	void topUpOnlyBetweenHandsAndUpToTheMax() {
		PokerTable t = new PokerTable(6, 10, RAKE);
		t.addHuman("a", "A", 400);
		t.addHuman("b", "B", 1000);
		t.startHand(rng);
		assertFalse(t.canTopUp("a"));
		assertFalse(t.topUp("a", 100));
		t.hand().apply(Action.fold());
		t.settleHand();
		assertTrue(t.topUp("a", 100));
		assertEquals(495, t.seatOf("a").stack);
		assertEquals(500, t.seatOf("a").invested);
	}

	@Test
	void makeRoomRemovesABotBetweenHandsOrMarksItDuringAHand() {
		PokerTable t = new PokerTable(3, 10, RAKE);
		t.addHuman("a", "A", 1000);
		t.addHuman("b", "B", 1000);
		t.addHuman("c", "C", 1000);
		assertNull(t.makeRoom(), "only humans");
		t.removeSeat("c");
		fill(t, true);
		assertEquals(0, t.bots().size(), "3 seats, 2 humans: one seat is kept free");
		PokerTable u = new PokerTable(2, 10, RAKE);
		u.addHuman("a", "A", 1000);
		fill(u, true);
		assertEquals(1, u.bots().size());
		assertEquals(-1, u.addHuman("b", "B", 1000));
		assertNotNull(u.makeRoom());
		assertEquals(1, u.addHuman("b", "B", 1000));
	}

	/** A long session with bots: chips of humans + bots + rake stay conserved. */
	@Test
	void longSessionConservesChips() {
		PokerTable t = new PokerTable(6, 10, RAKE);
		t.addHuman("a", "A", 1000);
		t.addHuman("b", "B", 1000);
		long botChipsIn = 0;
		long rake = 0;
		for (int n = 0; n < 400; n++) {
			PokerTable.FillResult r = fill(t, true);
			botChipsIn += r.joined().stream().mapToLong(s -> s.stack).sum();
			botChipsIn -= r.left().stream().mapToLong(s -> s.stack).sum();
			for (PokerTable.Seat h : t.humans()) {
				if (h.stack <= 0) {
					t.topUp(h.id, 1000);
				}
			}
			Hand h = t.startHand(rng);
			assertNotNull(h);
			while (!h.complete()) {
				PokerTable.Seat s = t.seatOf(h.player(h.toAct()).id);
				Action a = Bots.decide(h, s.human ? Bots.Tier.REGULAR : s.tier, t.vpipMap(), 60, 60, rng);
				h.apply(a);
			}
			rake += h.result().rake();
			t.settleHand();
		}
		long humanIn = t.humans().stream().mapToLong(s -> s.invested).sum();
		long onTable = t.occupied().stream().mapToLong(s -> s.stack).sum();
		assertEquals(humanIn + botChipsIn, onTable + rake);
		assertTrue(t.vpipMap().keySet().containsAll(List.of("a", "b")));
		Map<String, Double> vpip = t.vpipMap();
		assertTrue(vpip.get("a") >= 0 && vpip.get("a") <= 1);
	}
}
