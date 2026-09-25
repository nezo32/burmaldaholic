package dev.nezo.burmaldaholic.games.poker.logic;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.nezo.burmaldaholic.core.bots.logic.BotDifficulty;
import dev.nezo.burmaldaholic.core.bots.logic.BotProfile;
import dev.nezo.burmaldaholic.core.bots.logic.BotRng;
import dev.nezo.burmaldaholic.core.bots.logic.Personality;
import dev.nezo.burmaldaholic.core.bots.logic.Purse;
import dev.nezo.burmaldaholic.core.bots.logic.SeatOccupant;
import dev.nezo.burmaldaholic.core.bots.logic.SeatingMath;
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
	private static final String HUMAN_A = "00000000-0000-0000-0000-00000000000a";
	private static final String HUMAN_B = "00000000-0000-0000-0000-00000000000b";

	private static final PokerBotPolicy POLICY = new PokerBotPolicy(new PokerBotPolicy.Config(60, 60));

	private BotProfile profile(BotDifficulty level) {
		return new BotProfile("b" + ids.incrementAndGet(), "creeper42", level, Personality.TAG);
	}

	/** Seats {@code n} bots with {@code stack} chips (what core TableBots does at the safe point). */
	private List<BotProfile> seatBots(PokerTable t, int n, long stack) {
		List<BotProfile> out = new java.util.ArrayList<>();
		for (int i = 0; i < n; i++) {
			BotProfile p = profile(BotDifficulty.values()[i % 3]);
			if (t.seatBot(p, Purse.BANK, stack) >= 0) {
				out.add(p);
			}
		}
		return out;
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
	void botsSitAndLeaveThroughTheSeatHooks() {
		PokerTable t = new PokerTable(6, 10, RAKE);
		t.addHuman(HUMAN_A, "A", 1000);
		List<BotProfile> bots = seatBots(t, 4, 200);
		assertEquals(4, t.bots().size());
		assertTrue(t.bots().stream().allMatch(s -> s.stack == 200 && s.bot != null && s.purse == Purse.BANK));
		List<SeatOccupant> occ = t.occupants();
		assertEquals(6, occ.size());
		assertEquals(4, occ.stream().filter(o -> o != null && o.isBot()).count());
		assertEquals(bots.getFirst().key(), occ.get(1).key(), "bot seats carry the core key");
		assertEquals(List.of(HUMAN_A), t.seatedHumans());
		assertEquals(200, t.unseatBot(bots.getFirst().key()));
		assertEquals(0, t.unseatBot(bots.getFirst().key()), "gone");
		assertEquals(0, t.unseatBot(HUMAN_A), "never unseats a human");
		assertEquals(3, t.bots().size());
		Hand h = t.startHand(rng);
		assertNotNull(h);
		assertEquals(-1, t.seatBot(profile(BotDifficulty.HARD), Purse.BANK, 200), "bots sit only between hands");
		String dealt = bots.get(1).key();
		int k = h.indexOf(dealt);
		long handStack = h.player(k).stack();
		assertEquals(handStack, t.unseatBot(dealt), "a bot leaving mid-hand takes its hand stack");
	}

	@Test
	void seatedHumansInSitDownOrderWithoutLeavers() {
		PokerTable t = new PokerTable(6, 10, RAKE);
		t.addHuman(HUMAN_B, "B", 1000);
		t.addHuman(HUMAN_A, "A", 1000);
		assertEquals(List.of(HUMAN_B, HUMAN_A), t.seatedHumans());
		t.seatOf(HUMAN_B).leaving = true;
		assertEquals(List.of(HUMAN_A), t.seatedHumans());
	}

	@Test
	void handsSinceBigBlindForThePokerYieldRule() {
		PokerTable t = new PokerTable(6, 10, RAKE);
		t.addHuman(HUMAN_A, "A", 1000);
		List<BotProfile> bots = seatBots(t, 4, 1000);
		assertEquals(Integer.MAX_VALUE, t.handsSinceBigBlind(bots.getFirst().key()), "no hand yet");
		Hand h = t.startHand(rng); // button seat 0, SB seat 1, BB seat 2
		while (!h.complete()) {
			h.apply(h.legal().canCheck() ? Action.check() : Action.fold());
		}
		t.settleHand();
		int bbSeat = t.handSeats()[h.bbIndex()];
		assertEquals(2, bbSeat);
		assertEquals(0, t.handsSinceBigBlind(t.seat(2).id), "just posted the big blind: yields first");
		assertEquals(1, t.handsSinceBigBlind(t.seat(1).id));
		assertEquals(5, t.handsSinceBigBlind(t.seat(3).id), "posts the big blind next");
		List<String> order = SeatingMath.yieldOrder(SeatingMath.YieldRule.POKER_BIG_BLIND, bots.stream()
			.map(b -> new SeatingMath.YieldCandidate(b.key(), t.seatIndexOf(b.key()), t.seatOf(b.key()).stack, 0, false, t.handsSinceBigBlind(b.key())))
			.toList());
		assertEquals(t.seat(2).id, order.getFirst());
	}

	@Test
	void drawnHoldingsFromOnePlayOut() {
		PokerTable t = new PokerTable(6, 10, RAKE);
		t.addHuman(HUMAN_A, "A", 1000);
		t.addHuman(HUMAN_B, "B", 1000);
		seatBots(t, 2, 1000);
		assertTrue(t.drawnHoldings(null).isEmpty(), "no hand");
		Hand h = t.startHand(rng);
		h.apply(h.legal().canCheck() ? Action.check() : Action.call());
		long before = total(h);
		Hand end = h.playOut((x, i) -> x.player(i).human ? (x.legal(i).canCheck() ? Action.check() : Action.fold())
			: POLICY.decideNow(t.seatOf(x.player(i).id).bot, PokerBotPolicy.view(x, i, t::statsOf, false), BotRng.seeded(1), 16), 500);
		assertNotNull(end);
		assertTrue(end.complete());
		assertFalse(h.complete(), "the play-out runs on a copy");
		assertEquals(before, total(h), "the running hand is untouched");
		Map<String, Long> held = t.drawnHoldings(end);
		assertEquals(4, held.size(), "humans and bots from the same play-out");
		long sum = held.values().stream().mapToLong(Long::longValue).sum();
		assertEquals(4000 - end.result().rake(), sum, "no chip minted or burnt");
		t.removeSeat(HUMAN_B);
		assertEquals(3, t.drawnHoldings(end).size(), "a seat that left is not included");
	}

	private static long total(Hand h) {
		long s = h.potTotal();
		for (Hand.Player p : h.players()) {
			s += p.stack();
		}
		return s;
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
		assertEquals(1, t.seatOf("a").history.size());
		assertTrue(t.seatOf("a").history.getFirst().vpip());
		assertTrue(t.seatOf("a").history.getFirst().pfr());
		assertFalse(t.seatOf("b").history.getFirst().vpip());
		assertNull(t.statsOf("a"), "the model needs 10 hands");
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
		seatBots(t, 3, 200);
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

	/** A long session with bots (busted bots replaced like the safe point does): chips of humans + bots + rake stay conserved. */
	@Test
	void longSessionConservesChips() {
		PokerTable t = new PokerTable(6, 10, RAKE);
		t.addHuman("a", "A", 1000);
		t.addHuman("b", "B", 1000);
		BotRng botRng = BotRng.seeded(9);
		long botChipsIn = 0;
		long rake = 0;
		for (int n = 0; n < 400; n++) {
			for (PokerTable.Seat b : List.copyOf(t.bots())) {
				if (b.stack <= 0) {
					botChipsIn -= t.unseatBot(b.id);
				}
			}
			while (t.bots().size() < 3) {
				t.seatBot(profile(BotDifficulty.values()[n % 3]), Purse.BANK, 200);
				botChipsIn += 200;
			}
			for (PokerTable.Seat h : t.humans()) {
				if (h.stack <= 0) {
					t.topUp(h.id, 1000);
				}
			}
			Hand h = t.startHand(rng);
			assertNotNull(h);
			while (!h.complete()) {
				PokerTable.Seat s = t.seatOf(h.player(h.toAct()).id);
				BotProfile p = s.human ? new BotProfile("hx", "creeper42", BotDifficulty.NORMAL, Personality.TAG) : s.bot;
				h.apply(POLICY.decideNow(p, PokerBotPolicy.view(h, h.toAct(), t::statsOf, s.tilt > 0), botRng));
			}
			rake += h.result().rake();
			t.settleHand();
		}
		long humanIn = t.humans().stream().mapToLong(s -> s.invested).sum();
		long onTable = t.occupied().stream().mapToLong(s -> s.stack).sum();
		assertEquals(humanIn + botChipsIn, onTable + rake);
		assertNotNull(t.statsOf("a"), "the opponent model has data after 400 hands");
		assertTrue(t.statsOf("a").vpip() >= 0 && t.statsOf("a").vpip() <= 1);
		assertEquals(Ranges.MODEL_WINDOW, t.seatOf("a").history.size());
	}

	@Test
	void easyTiltAfterABigLoss() {
		PokerTable t = new PokerTable(2, 10, RAKE);
		t.addHuman("a", "A", 1000);
		t.seatBot(profile(BotDifficulty.EASY), Purse.BANK, 1000);
		PokerTable.Seat bot = t.bots().getFirst();
		boolean tilted = false;
		for (int n = 0; n < 50 && !tilted; n++) {
			t.seatOf("a").stack = 1000;
			bot.stack = 1000;
			Hand h = t.startHand(rng);
			while (!h.complete()) {
				h.apply(h.legal().canRaise() ? Action.allIn() : h.legal().toCall() > 0 ? Action.call() : Action.check());
			}
			boolean botLostBig = h.result().net()[h.indexOf(bot.id)] < -400;
			t.settleHand();
			if (botLostBig) {
				assertEquals(5, bot.tilt, "lost a pot > 40 BB: tilts for 5 hands");
				tilted = true;
			}
		}
		assertTrue(tilted);
	}

	/** Review B1: a folded player who stood up and bought in again is a new seat, never the old hand entry. */
	@Test
	void reseatedPlayerIsNotMatchedToTheOldHandEntry() {
		for (boolean abort : new boolean[] {false, true}) {
			PokerTable t = new PokerTable(6, 10, RAKE);
			t.addHuman("a", "A", 1000);
			t.addHuman("b", "B", 1000);
			t.addHuman("c", "C", 1000);
			Hand h = t.startHand(rng);
			String x = h.player(h.toAct()).id;
			h.apply(Action.fold());
			assertTrue(t.dealtInto(x), "folded player still has a hand entry");
			assertEquals(1000, t.liveStack(x));
			t.removeSeat(x); // stood up (cashed out 1000)
			assertEquals(0, t.liveStack(x), "not seated: nothing to pay");
			assertTrue(t.dealtInto(x), "the hand entry outlives the seat: buy-in must wait for the next hand");
			t.addHuman(x, "X", 400); // a re-buy (only possible if the table let it through)
			assertEquals(-1, t.handIndexOf(x), "the new seat was not dealt in");
			assertEquals(400, t.liveStack(x), "cash-out pays the new stack, not the old hand stack");
			assertEquals(400, t.refundableStack(x));
			assertTrue(t.canTopUp(x));
			if (abort) {
				t.abortHand();
			} else {
				while (!h.complete()) {
					h.apply(h.legal().canCheck() ? Action.check() : Action.call());
				}
				t.settleHand();
			}
			assertEquals(400, t.seatOf(x).stack, "hand end never writes the old entry's stack onto the new seat");
		}
	}
}
