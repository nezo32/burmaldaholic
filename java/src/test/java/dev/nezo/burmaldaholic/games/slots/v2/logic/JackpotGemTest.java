package dev.nezo.burmaldaholic.games.slots.v2.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayDeque;
import java.util.Deque;
import org.junit.jupiter.api.Test;

/** SLOTS.md §5.4: the base-game jackpot gem is the LAST draw of a paid base spin and pays from the pool. */
class JackpotGemTest {
	/** Replays given draws; the last call (the gem) returns {@code gem}. */
	private static SlotRng scripted(int gem) {
		Deque<Integer> q = new ArrayDeque<>(java.util.List.of(0, 0, 0, 0, 0));
		return bound -> q.isEmpty() ? gem : q.poll() % bound;
	}

	@Test
	void gemTiersFollowThePpmBands() {
		MachineDef.Features f = SlotDefaults.def(Machine.OVERWORLD).features();
		assertEquals(1, SlotDraw.gemTier(f, 0));
		assertEquals(1, SlotDraw.gemTier(f, 249));
		assertEquals(2, SlotDraw.gemTier(f, 250));
		assertEquals(2, SlotDraw.gemTier(f, 299));
		assertEquals(0, SlotDraw.gemTier(f, 300));
	}

	@Test
	void aMiniGemPaysThePoolShareAndRoundTrips() {
		MachineDef d = SlotDefaults.def(Machine.OVERWORLD);
		long[] pools = {0, 1500, 2500, 10000, 50000};
		SpinTape t = SlotDraw.draw(new SlotDraw.Request(d, 50, false, false, pools), scripted(10));
		assertEquals(1, t.gem());
		assertEquals(750, t.gemAward().chips(), "1 500 × 50 / 100");
		SpinTape back = TapeCodec.decode(TapeCodec.encode(t));
		assertEquals(t, back);
		SpinTape none = SlotDraw.draw(new SlotDraw.Request(d, 50, false, false, pools), scripted(999_999));
		assertEquals(0, none.gem());
		assertNull(none.gemAward());
		assertThrows(IllegalArgumentException.class, () -> TapeCodec.decode(TapeCodec.encode(none).replace(";;;", ";g1;;").replaceFirst(";;0;", ";g1;0;")));
	}

	@Test
	void ownedGemIsFixedAndInsideTheTotal() {
		MachineDef d = SlotDefaults.def(Machine.NETHER);
		SpinTape t = SlotDraw.draw(new SlotDraw.Request(d, 20, false, true, null), scripted(200));
		assertEquals(2, t.gem());
		assertEquals(30 * 20, t.gemAward().chips());
		assertEquals(t.totalChips(), t.payoutChips());
	}
}
