package dev.nezo.burmaldaholic.core.bots.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.nezo.burmaldaholic.core.bots.logic.VirtualSeats.HumanSeat;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Atmosphere bot seats beside core's human seats (same vectors as Bedrock {@code virtual-seats.test.ts}, 0-based). */
class VirtualSeatsTest {
	private final List<HumanSeat> humans = new ArrayList<>();

	private VirtualSeats seats(int n) {
		return new VirtualSeats(() -> n, () -> humans);
	}

	private static SeatOccupant.Bot bot(String id) {
		return new SeatOccupant.Bot(new BotProfile(id, "creeper42", BotDifficulty.NORMAL, Personality.TAG), BotRole.ATMOSPHERE, Purse.NONE);
	}

	private void human(int seat) {
		humans.add(new HumanSeat(new UUID(0, seat + 1), "p" + seat, seat));
	}

	@Test
	void botsTakeTheHighestFreeSeatsHumansKeepTheirs() {
		human(0);
		VirtualSeats v = seats(5);
		assertTrue(v.seatBot(bot("a")));
		assertTrue(v.seatBot(bot("b")));
		assertEquals(List.of(3, 4), v.list().stream().map(VirtualSeats.VirtualBot::seat).toList());
		List<SeatOccupant> occ = v.occupants();
		assertFalse(occ.get(0).isBot());
		assertNull(occ.get(1));
		assertEquals("bot:b", occ.get(3).key());
		assertEquals("bot:a", occ.get(4).key());
		assertEquals(2, v.free());
		assertTrue(v.seatBot(bot("a")), "already seated");
		assertEquals(2, v.size());
	}

	@Test
	void refusesABotOnAFullTable() {
		human(0);
		human(1);
		VirtualSeats v = seats(2);
		assertFalse(v.seatBot(bot("a")));
	}

	@Test
	void aHumanSeatedOnABotSeatMovesTheBot() {
		VirtualSeats v = seats(3);
		v.seatBot(bot("a")); // seat 2
		human(2); // core seats a human there
		assertEquals(List.of(), v.resolve());
		assertEquals(1, v.get("bot:a").seat());
		assertEquals(2, v.occupants().stream().filter(o -> o != null).count());
	}

	@Test
	void aCollidingBotWithoutAFreeSeatIsDropped() {
		VirtualSeats v = seats(2);
		v.seatBot(bot("a"));
		human(0);
		human(1);
		assertEquals(List.of("bot:a"), v.resolve());
		assertEquals(0, v.size());
	}

	@Test
	void unseatFreesTheSeat() {
		VirtualSeats v = seats(3);
		v.seatBot(bot("a"));
		assertTrue(v.unseatBot("bot:a"));
		assertEquals(3, v.free());
		assertNull(v.at(2));
	}

	@Test
	void betMomentIsInTheFirst60To160TicksFastHalvesInstantIsZero() {
		BotRng rng = BotRng.seeded(1);
		int lo = Integer.MAX_VALUE;
		int hi = 0;
		for (int i = 0; i < 5000; i++) {
			int t = VirtualSeats.betDelay(rng, BotSpeed.NORMAL, 0.5);
			lo = Math.min(lo, t);
			hi = Math.max(hi, t);
			int f = VirtualSeats.betDelay(rng, BotSpeed.FAST, 0.5);
			assertTrue(f >= 30 && f <= 80);
		}
		assertEquals(60, lo);
		assertEquals(160, hi);
		assertEquals(0, VirtualSeats.betDelay(rng, BotSpeed.INSTANT, 0.5));
	}

	@Test
	void virtualAmountsAreMinPlusKStepsWithinTheLimits() {
		BotRng rng = BotRng.seeded(2);
		for (int i = 0; i < 2000; i++) {
			long a = VirtualSeats.virtualAmount(rng, 10, 10, 2, 5, Long.MAX_VALUE);
			assertTrue(a >= 30 && a <= 60 && a % 10 == 0, "" + a);
			assertTrue(VirtualSeats.virtualAmount(rng, 10, 10, 5, 10, 70) <= 70);
		}
	}

	@Test
	void botUuidIsStablePerKey() {
		assertEquals(VirtualSeats.botUuid("bot:b1234567"), VirtualSeats.botUuid("bot:b1234567"));
		assertFalse(VirtualSeats.botUuid("bot:a").equals(VirtualSeats.botUuid("bot:b")));
	}
}
