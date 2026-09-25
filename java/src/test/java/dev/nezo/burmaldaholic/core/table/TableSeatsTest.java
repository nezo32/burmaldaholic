package dev.nezo.burmaldaholic.core.table;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class TableSeatsTest {
	@Test
	void seatsFillLeaveAndRotate() {
		TableSeats seats = new TableSeats(3);
		UUID a = UUID.randomUUID(), b = UUID.randomUUID(), c = UUID.randomUUID(), d = UUID.randomUUID();
		assertEquals(0, seats.sit(a, "A").getAsInt());
		assertEquals(0, seats.sit(a, "A").getAsInt(), "sitting twice keeps the seat");
		assertEquals(1, seats.sit(b, "B").getAsInt());
		assertEquals(2, seats.sit(c, "C").getAsInt());
		assertTrue(seats.isFull());
		assertTrue(seats.sit(d, "D").isEmpty());
		assertEquals(1, seats.leave(b).getAsInt());
		assertEquals(2, seats.nextOccupied(0).getAsInt(), "skips the empty seat");
		assertEquals(0, seats.nextOccupied(2).getAsInt(), "wraps around");
		assertEquals(1, seats.sit(d, "D").getAsInt(), "first free seat");
		assertEquals(3, seats.occupied().size());
	}
}
