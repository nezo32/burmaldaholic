package dev.nezo.burmaldaholic.games.roulette.logic;

import dev.nezo.burmaldaholic.games.roulette.logic.Bets.Bet;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.IntSupplier;
import org.jspecify.annotations.Nullable;

/**
 * Shared-spin state machine of one roulette table (GAME_DESIGN.md §9). Pure Java; money is handled by the caller.
 *
 * <pre>
 * BETTING ──(every bettor ready / left | bet timer expired)──▶ NO_MORE_BETS (20 t) ──draw──▶ SPIN (spinTicks)
 *    ▲                                                                                           │
 *    └────────────────────────────── RESULT (60 t, settle) ◀────────────────────────────────────┘
 * </pre>
 *
 * <ul>
 *   <li>The bet timer starts at the first bet once 2+ players are seated; a single player spins with "Spin"
 *       (= ready). Bettors no longer seated count as ready, so a round whose only bettor walked away or
 *       disconnected spins right away ("roulette: spin proceeds", §4.1).</li>
 *   <li>When betting closes, slips below {@code minTotal} (High-Roller) are dropped; the caller refunds them.</li>
 * </ul>
 *
 * @param <K> player key (UUID in game, String in tests)
 */
public final class RouletteRound<K> {
	public enum Phase {
		BETTING, NO_MORE_BETS, SPIN, RESULT;

		public String id() {
			return name().toLowerCase(java.util.Locale.ROOT);
		}
	}

	public record Timings(int betTicks, int noMoreBetsTicks, int spinTicks, int resultTicks) {
		public static final Timings DEFAULT = new Timings(500, 20, 100, 60);
	}

	/** What {@link #update} did (at most one transition per call). */
	public sealed interface Transition<K> {
		/** Betting closed; {@code dropped} slips (below min total) must be refunded. */
		record NoMoreBets<K>(List<K> dropped) implements Transition<K> {}

		/** Betting closed but nothing was left to spin (all slips dropped); still betting. */
		record Abandoned<K>(List<K> dropped) implements Transition<K> {}

		record Spin<K>(int result) implements Transition<K> {}

		/** Settle every slip against {@code result}. */
		record Result<K>(int result, Map<K, List<Bet>> slips) implements Transition<K> {}

		/** Back to betting after the result display. */
		record Reset<K>() implements Transition<K> {}
	}

	private static final class Slip {
		List<Bet> bets = List.of();
		boolean ready;
	}

	private Timings timings;
	private int historyLength;
	private Phase phase = Phase.BETTING;
	private long endsAt = -1;
	private int result = -1;
	private final List<Integer> history = new ArrayList<>();
	private final Map<K, Slip> slips = new LinkedHashMap<>();

	public RouletteRound(Timings timings, int historyLength) {
		this.timings = timings;
		this.historyLength = historyLength;
	}

	/** Applies new config values (take effect from the next phase). */
	public void configure(Timings timings, int historyLength) {
		this.timings = timings;
		this.historyLength = historyLength;
		trimHistory();
	}

	public Timings timings() {
		return timings;
	}

	public Phase phase() {
		return phase;
	}

	public boolean canBet() {
		return phase == Phase.BETTING;
	}

	/** Result of the current spin (-1 before the draw). */
	public int result() {
		return result;
	}

	/** Most recent first. */
	public List<Integer> history() {
		return List.copyOf(history);
	}

	public void setHistory(Collection<Integer> values) {
		history.clear();
		values.stream().filter(Wheel::isPocket).forEach(history::add);
		trimHistory();
	}

	private void trimHistory() {
		while (history.size() > Math.max(0, historyLength)) {
			history.remove(history.size() - 1);
		}
	}

	/** Tick at which the current phase ends; -1 = no timer (betting without a running bet timer). */
	public long endsAt() {
		return endsAt;
	}

	public long remaining(long now) {
		return endsAt < 0 ? -1 : Math.max(0, endsAt - now);
	}

	public List<Bet> bets(K id) {
		Slip s = slips.get(id);
		return s == null ? List.of() : s.bets;
	}

	public long total(K id) {
		return Bets.totalStaked(bets(id));
	}

	public Set<K> bettors() {
		return Set.copyOf(slips.keySet());
	}

	public boolean hasBets() {
		return !slips.isEmpty();
	}

	public boolean isReady(K id) {
		Slip s = slips.get(id);
		return s != null && s.ready;
	}

	public int readyCount() {
		return (int) slips.values().stream().filter(s -> s.ready).count();
	}

	/** Adds bets (already validated and paid by the caller). Clears the player's ready flag. */
	public void addBets(K id, List<Bet> bets) {
		if (!canBet()) {
			throw new IllegalStateException("betting is closed");
		}
		Slip slip = slips.computeIfAbsent(id, k -> new Slip());
		slip.bets = List.copyOf(Bets.mergeAll(slip.bets, bets));
		slip.ready = false;
	}

	/** Removes a player's slip (clear bets / refund). Returns what was removed. */
	public List<Bet> clear(K id) {
		Slip s = slips.remove(id);
		return s == null ? List.of() : s.bets;
	}

	public void setReady(K id, boolean ready) {
		Slip s = slips.get(id);
		if (s != null) {
			s.ready = ready;
		}
	}

	/** Drops every slip (e.g. table broken / casino closed; the caller refunds) and resets to betting. */
	public Map<K, List<Bet>> abort() {
		Map<K, List<Bet>> out = new LinkedHashMap<>();
		slips.forEach((k, s) -> out.put(k, s.bets));
		slips.clear();
		phase = Phase.BETTING;
		endsAt = -1;
		result = -1;
		return out;
	}

	/**
	 * Advances the machine.
	 *
	 * @param seated   players currently seated at the table
	 * @param draw     draws the result 0..36 (fair RNG)
	 * @param minTotal High-Roller minimum per spin (0 = none)
	 */
	public @Nullable Transition<K> update(long now, Collection<K> seated, IntSupplier draw, long minTotal) {
		switch (phase) {
			case BETTING -> {
				if (slips.isEmpty()) {
					endsAt = -1;
					return null;
				}
				if (endsAt < 0 && seated.size() >= 2) {
					endsAt = now + timings.betTicks();
				}
				Set<K> seatedSet = new HashSet<>(seated);
				boolean allReady = slips.entrySet().stream().allMatch(e -> e.getValue().ready || !seatedSet.contains(e.getKey()));
				boolean timeUp = endsAt >= 0 && now >= endsAt;
				if (!timeUp && !allReady) {
					return null;
				}
				List<K> dropped = new ArrayList<>();
				if (minTotal > 0) {
					slips.forEach((k, s) -> {
						if (Bets.totalStaked(s.bets) < minTotal) {
							dropped.add(k);
						}
					});
					dropped.forEach(slips::remove);
				}
				if (slips.isEmpty()) {
					endsAt = -1;
					return new Transition.Abandoned<>(dropped);
				}
				phase = Phase.NO_MORE_BETS;
				endsAt = now + timings.noMoreBetsTicks();
				return new Transition.NoMoreBets<>(dropped);
			}
			case NO_MORE_BETS -> {
				if (now < endsAt) {
					return null;
				}
				int r = draw.getAsInt();
				if (!Wheel.isPocket(r)) {
					throw new IllegalStateException("draw returned " + r);
				}
				result = r;
				phase = Phase.SPIN;
				endsAt = now + timings.spinTicks();
				return new Transition.Spin<>(r);
			}
			case SPIN -> {
				if (now < endsAt) {
					return null;
				}
				phase = Phase.RESULT;
				endsAt = now + timings.resultTicks();
				if (historyLength > 0) {
					history.add(0, result);
					trimHistory();
				}
				Map<K, List<Bet>> settled = new LinkedHashMap<>();
				slips.forEach((k, s) -> settled.put(k, s.bets));
				slips.clear();
				return new Transition.Result<>(result, settled);
			}
			case RESULT -> {
				if (now < endsAt) {
					return null;
				}
				phase = Phase.BETTING;
				endsAt = -1;
				result = -1;
				return new Transition.Reset<>();
			}
		}
		return null;
	}
}
