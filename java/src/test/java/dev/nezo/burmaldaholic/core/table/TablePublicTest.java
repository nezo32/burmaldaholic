package dev.nezo.burmaldaholic.core.table;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

/** The public seat header of the in-world board (docs/architecture/ui-v2.md §7.2). */
class TablePublicTest {
	@Test
	void seatsRoundTripThroughTheUpdateTag() {
		UUID steve = UUID.randomUUID();
		TableSeats seats = new TableSeats(7);
		seats.sit(UUID.randomUUID(), "Alex");
		seats.sit(steve, "Steve_with_a_very_long_name");
		seats.leave(seats.get(0).player());
		CompoundTag tag = new CompoundTag();
		List<TablePublic.SeatPub> pub = TablePublic.of(seats);
		pub = new java.util.ArrayList<>(pub);
		pub.add(new TablePublic.SeatPub(4, null, "gui.burmaldaholic.bots.name.creeper42", true));
		TablePublic.writeSeats(tag, 7, pub);
		TablePublic.SeatPub[] read = TablePublic.readSeats(tag);
		assertEquals(7, read.length);
		assertEquals(7, TablePublic.seatCount(tag));
		assertNull(read[0]);
		assertEquals("Steve_with_a_ver", read[1].name());
		assertTrue(read[1].is(steve));
		assertFalse(read[1].bot());
		assertTrue(read[4].bot());
		assertNull(read[4].uuid());
		assertFalse(read[4].is(steve));
		assertEquals("gui.burmaldaholic.bots.name.creeper42", read[4].name());
	}

	@Test
	void anAbsentHeaderReadsEmpty() {
		assertEquals(0, TablePublic.readSeats(new CompoundTag()).length);
		assertEquals(0, TablePublic.seatCount(new CompoundTag()));
	}
}
