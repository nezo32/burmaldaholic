package dev.nezo.burmaldaholic.games.baccarat.logic;

import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.Set;

/**
 * Seat indices (0 … seats−1) of bots at a table whose human seats are kept by core's {@code TableSeats}
 * (which does not know bots). PURE. A human who got a bot's seat index pushes that bot to the lowest free
 * seat (bots are virtual; admission through {@code TableBots.admit} keeps humans + bots ≤ seats).
 * Same behaviour as Bedrock {@code BotSeatMap} (which is 1-based).
 */
public final class BotSeatMap {
	private final Map<String, Integer> seatOf = new LinkedHashMap<>();

	public List<String> keys() {
		return List.copyOf(seatOf.keySet());
	}

	public int size() {
		return seatOf.size();
	}

	public boolean has(String key) {
		return seatOf.containsKey(key);
	}

	public OptionalInt seat(String key) {
		Integer s = seatOf.get(key);
		return s == null ? OptionalInt.empty() : OptionalInt.of(s);
	}

	/** Seats a bot at the lowest seat free of humans and bots; false when none. */
	public boolean add(String key, Collection<Integer> humanSeats, int seats) {
		OptionalInt s = freeSeat(humanSeats, seats);
		if (s.isEmpty()) {
			return false;
		}
		seatOf.put(key, s.getAsInt());
		return true;
	}

	public void remove(String key) {
		seatOf.remove(key);
	}

	/** Moves bots off seats humans now hold (or out of range / doubled). False if some bot found no seat. */
	public boolean resolve(Collection<Integer> humanSeats, int seats) {
		boolean ok = true;
		Set<Integer> humans = new HashSet<>(humanSeats);
		for (Map.Entry<String, Integer> e : List.copyOf(seatOf.entrySet())) {
			String key = e.getKey();
			int s = e.getValue();
			boolean clash = false;
			for (Map.Entry<String, Integer> o : seatOf.entrySet()) {
				if (!o.getKey().equals(key) && o.getValue() == s) {
					clash = true;
					break;
				}
			}
			if (!humans.contains(s) && s >= 0 && s < seats && !clash) {
				continue;
			}
			seatOf.remove(key);
			OptionalInt n = freeSeat(humanSeats, seats);
			if (n.isEmpty()) {
				ok = false;
			} else {
				seatOf.put(key, n.getAsInt());
			}
		}
		return ok;
	}

	private OptionalInt freeSeat(Collection<Integer> humanSeats, int seats) {
		Set<Integer> taken = new HashSet<>(humanSeats);
		taken.addAll(seatOf.values());
		for (int s = 0; s < seats; s++) {
			if (!taken.contains(s)) {
				return OptionalInt.of(s);
			}
		}
		return OptionalInt.empty();
	}
}
