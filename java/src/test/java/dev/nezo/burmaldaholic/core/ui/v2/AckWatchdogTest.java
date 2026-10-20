package dev.nezo.burmaldaholic.core.ui.v2;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** The ack watchdog (docs/architecture/ui-v2.md §5.2-3; reviews R-KW M8, M9, R-CDE m6). */
class AckWatchdogTest {
	private static int fire(AckWatchdog w) {
		int fired = 0;
		for (int i = 0; i < AckWatchdog.TIMEOUT_TICKS + 5; i++) if (w.tick()) fired++;
		return fired;
	}

	@Test
	void twoOutstandingClicksWaitForTwoAcks() {
		AckWatchdog w = new AckWatchdog();
		w.state(7, 10);
		w.sent("bet", "", 5);
		w.sent("bet", "", 5);
		assertEquals(12, w.expected());
		w.state(7, 11); // the echo of the first click
		assertTrue(w.pending(), "the second click is still unanswered (review M9)");
		w.state(7, 12);
		assertFalse(w.pending());
		assertEquals(12, w.lastAck());
	}

	@Test
	void anOldStateDoesNotAnswer() {
		AckWatchdog w = new AckWatchdog();
		w.state(1, 3);
		w.sent("bet", "", 25);
		w.state(1, 3); // a timer sync from before the action
		assertTrue(w.pending());
		w.state(1, 4);
		assertFalse(w.pending());
	}

	@Test
	void epochChangeRebasesAndDropsThePending() {
		AckWatchdog w = new AckWatchdog();
		w.state(42, 30);
		w.sent("bet", "", 5);
		w.state(-9, 0); // the table was reloaded: a new counter that starts again (review M8)
		assertFalse(w.pending(), "a reload answers nothing; the pending chip is dropped, not timed out");
		assertEquals(0, w.lastAck());
		assertEquals(-9, w.epoch());
		w.sent("bet", "", 5);
		assertEquals(1, w.expected(), "the next click waits for the new counter's next ack, not 31");
		w.state(-9, 1);
		assertFalse(w.pending());
		assertEquals(0, fire(w));
	}

	@Test
	void firstEpochIsNotAChange() {
		AckWatchdog w = new AckWatchdog();
		w.sent("sit", "", 0);
		w.state(5, 1);
		assertFalse(w.pending());
		w.sent("bet", "", 5);
		w.state(5, 1);
		assertTrue(w.pending());
	}

	@Test
	void errorAnswersEverything() {
		AckWatchdog w = new AckWatchdog();
		w.state(3, 2);
		w.sent("bet", "", 5);
		w.sent("bet", "", 5);
		w.error();
		assertFalse(w.pending());
		assertEquals(0, fire(w));
		w.state(3, 4); // the states that follow the error are no problem
		assertFalse(w.pending());
		w.sent("bet", "", 5);
		assertEquals(5, w.expected());
	}

	@Test
	void timeoutFiresOnceThenTheResyncIsCounted() {
		AckWatchdog w = new AckWatchdog();
		w.state(3, 5);
		w.sent("bet", "red", 5); // lost on the way: the server never counts it
		assertEquals(1, fire(w));
		assertFalse(w.pending());
		w.state(3, 6); // the answer to the untracked resync
		w.sent("bet", "red", 5);
		assertEquals(7, w.expected());
		w.state(3, 7);
		assertFalse(w.pending());
	}

	@Test
	void legacyStateWithoutAckAnswersAnything() {
		AckWatchdog w = new AckWatchdog();
		w.sent("spin", "", 0);
		w.state(-1);
		assertFalse(w.pending());
	}
}
