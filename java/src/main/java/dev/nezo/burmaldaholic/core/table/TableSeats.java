package dev.nezo.burmaldaholic.core.table;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.OptionalInt;
import java.util.UUID;

/**
 * Seats of one table (pure Java, unit-tested). A seat holds a player id + display name. Seating is
 * runtime state (not saved): after a restart everybody re-joins by using the table.
 */
public final class TableSeats {
	public record Seat(int index, UUID player, String name) {}

	private final Seat[] seats;

	public TableSeats(int count) {
		this.seats = new Seat[Math.max(0, count)];
	}

	public int size() {
		return seats.length;
	}

	/** Seats the player in the first free seat (or returns their current seat). Empty if full. */
	public OptionalInt sit(UUID player, String name) {
		OptionalInt current = seatOf(player);
		if (current.isPresent()) {
			return current;
		}
		for (int i = 0; i < seats.length; i++) {
			if (seats[i] == null) {
				seats[i] = new Seat(i, player, name);
				return OptionalInt.of(i);
			}
		}
		return OptionalInt.empty();
	}

	/** Frees the player's seat; returns the freed index or empty. */
	public OptionalInt leave(UUID player) {
		OptionalInt current = seatOf(player);
		current.ifPresent(i -> seats[i] = null);
		return current;
	}

	public OptionalInt seatOf(UUID player) {
		for (int i = 0; i < seats.length; i++) {
			if (seats[i] != null && seats[i].player().equals(player)) {
				return OptionalInt.of(i);
			}
		}
		return OptionalInt.empty();
	}

	public boolean isSeated(UUID player) {
		return seatOf(player).isPresent();
	}

	public Seat get(int index) {
		return index < 0 || index >= seats.length ? null : seats[index];
	}

	/** Occupied seats in seat order (clockwise). */
	public List<Seat> occupied() {
		List<Seat> list = new ArrayList<>();
		for (Seat s : seats) {
			if (s != null) {
				list.add(s);
			}
		}
		return list;
	}

	public boolean isEmpty() {
		return Arrays.stream(seats).allMatch(s -> s == null);
	}

	public boolean isFull() {
		return Arrays.stream(seats).noneMatch(s -> s == null);
	}

	/** Next occupied seat after {@code index} (wrapping), for dealer buttons / shooter rotation. */
	public OptionalInt nextOccupied(int index) {
		for (int step = 1; step <= seats.length; step++) {
			int i = Math.floorMod(index + step, seats.length);
			if (seats[i] != null) {
				return OptionalInt.of(i);
			}
		}
		return OptionalInt.empty();
	}
}
