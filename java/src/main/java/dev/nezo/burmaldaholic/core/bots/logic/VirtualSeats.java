package dev.nezo.burmaldaholic.core.bots.logic;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.IntSupplier;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;

/**
 * Seats of ATMOSPHERE bots at a house-banked table (BOTS.md §4.7, pvp-bots.md §4.6). PURE; mirrors
 * Bedrock {@code core/logic/bots/virtual-seats.ts} (Java seat numbers are 0-based {@code TableSeats} indices).
 *
 * <p>Humans are seated by core ({@code TableSeats}, lowest free seat first) and know nothing about bots,
 * so the game keeps its bots here: each bot takes the HIGHEST free seat (so humans, seated lowest-first,
 * rarely collide; the yield rule {@code HIGHEST_SEAT} frees the highest seat first). If core seats a human
 * on a seat a bot holds (possible after other humans left), {@link #resolve()} moves the bot to another
 * free seat, or drops it when none is left (the next {@code TableBots.safePoint} notices and forgets it).
 *
 * <p>Also the shared atmosphere timing rule: bots bet at a random moment in the first 60–160 t of BETTING
 * and always count as ready (they never delay a round).
 */
public final class VirtualSeats {
	/** A seated human as core sees it. */
	public record HumanSeat(UUID id, String name, int seat) {}

	/** A seated atmosphere bot. */
	public static final class VirtualBot {
		public final String key;
		public final SeatOccupant.Bot occupant;
		int seat;

		VirtualBot(SeatOccupant.Bot occupant, int seat) {
			this.key = occupant.key();
			this.occupant = occupant;
			this.seat = seat;
		}

		/** 0-based seat index. */
		public int seat() {
			return seat;
		}

		public BotProfile profile() {
			return occupant.profile();
		}
	}

	/** First / last tick of the atmosphere bet moment after BETTING opens (BOTS.md §7.3). */
	public static final int BET_MIN_TICKS = 60;
	public static final int BET_MAX_TICKS = 160;

	private final IntSupplier count;
	private final Supplier<List<HumanSeat>> humans;
	private final Map<String, VirtualBot> bots = new LinkedHashMap<>();

	public VirtualSeats(IntSupplier count, Supplier<List<HumanSeat>> humans) {
		this.count = count;
		this.humans = humans;
	}

	public int seats() {
		return Math.max(0, count.getAsInt());
	}

	/** Moves colliding bots to free seats; bots without a free seat are removed. Returns removed keys. */
	public List<String> resolve() {
		int n = seats();
		Set<Integer> taken = new HashSet<>();
		for (HumanSeat h : humans.get()) {
			taken.add(h.seat());
		}
		List<String> removed = new ArrayList<>();
		List<VirtualBot> sorted = new ArrayList<>(bots.values());
		sorted.sort(Comparator.comparingInt(VirtualBot::seat));
		List<VirtualBot> moving = new ArrayList<>();
		for (VirtualBot b : sorted) {
			if (b.seat >= 0 && b.seat < n && !taken.contains(b.seat)) {
				taken.add(b.seat);
			} else {
				moving.add(b);
			}
		}
		for (VirtualBot b : moving) {
			int seat = highestFree(n, taken);
			if (seat < 0) {
				bots.remove(b.key);
				removed.add(b.key);
			} else {
				b.seat = seat;
				taken.add(seat);
			}
		}
		return removed;
	}

	/** Seat index i: human, bot or null (free). */
	public List<@Nullable SeatOccupant> occupants() {
		resolve();
		SeatOccupant[] out = new SeatOccupant[seats()];
		for (HumanSeat h : humans.get()) {
			if (h.seat() >= 0 && h.seat() < out.length) {
				out[h.seat()] = new SeatOccupant.Human(h.id(), h.name());
			}
		}
		for (VirtualBot b : bots.values()) {
			out[b.seat] = b.occupant;
		}
		return Arrays.asList(out);
	}

	public int free() {
		int n = 0;
		for (SeatOccupant o : occupants()) {
			if (o == null) {
				n++;
			}
		}
		return n;
	}

	/** Seats a bot on the highest free seat; false when the table is full. */
	public boolean seatBot(SeatOccupant.Bot occupant) {
		resolve();
		if (bots.containsKey(occupant.key())) {
			return true;
		}
		Set<Integer> taken = new HashSet<>();
		for (HumanSeat h : humans.get()) {
			taken.add(h.seat());
		}
		for (VirtualBot b : bots.values()) {
			taken.add(b.seat);
		}
		int seat = highestFree(seats(), taken);
		if (seat < 0) {
			return false;
		}
		bots.put(occupant.key(), new VirtualBot(occupant, seat));
		return true;
	}

	public boolean unseatBot(String key) {
		return bots.remove(key) != null;
	}

	public @Nullable VirtualBot get(String key) {
		return bots.get(key);
	}

	/** The bot on a seat (after {@link #resolve}), or null. */
	public @Nullable VirtualBot at(int seat) {
		for (VirtualBot b : list()) {
			if (b.seat == seat) {
				return b;
			}
		}
		return null;
	}

	/** Seated bots by seat (after {@link #resolve}). */
	public List<VirtualBot> list() {
		resolve();
		List<VirtualBot> out = new ArrayList<>(bots.values());
		out.sort(Comparator.comparingInt(VirtualBot::seat));
		return out;
	}

	public int size() {
		return bots.size();
	}

	public void clear() {
		bots.clear();
	}

	private static int highestFree(int n, Set<Integer> taken) {
		for (int s = n - 1; s >= 0; s--) {
			if (!taken.contains(s)) {
				return s;
			}
		}
		return -1;
	}

	/** Ticks after BETTING opens at which an atmosphere bot places its virtual bet (bot rng only). */
	public static int betDelay(BotRng rng, BotSpeed speed, double fastFactor) {
		if (speed == BotSpeed.INSTANT) {
			return 0;
		}
		int t = rng.between(BET_MIN_TICKS, BET_MAX_TICKS);
		return speed == BotSpeed.FAST ? (int) Math.round(t * fastFactor) : t;
	}

	/** Virtual amount {@code min + k × step}, k uniform in [kMin, kMax], clamped to [min, max] (BOTS.md §4.7). */
	public static long virtualAmount(BotRng rng, long min, long step, int kMin, int kMax, long max) {
		long lo = Math.max(1, min);
		int k = rng.between(Math.min(kMin, kMax), Math.max(kMin, kMax));
		return Math.max(lo, Math.min(max, lo + k * Math.max(1, step)));
	}

	/** A stable UUID standing for a bot where a game's pure logic keys seats / bets by UUID. */
	public static UUID botUuid(String botKey) {
		return UUID.nameUUIDFromBytes(("burmaldaholic:" + botKey).getBytes(java.nio.charset.StandardCharsets.UTF_8));
	}
}
