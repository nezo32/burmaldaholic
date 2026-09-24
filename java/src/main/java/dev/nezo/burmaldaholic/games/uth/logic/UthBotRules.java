package dev.nezo.burmaldaholic.games.uth.logic;

import dev.nezo.burmaldaholic.core.bots.logic.BotDifficulty;
import dev.nezo.burmaldaholic.core.bots.logic.BotProfile;
import dev.nezo.burmaldaholic.core.bots.logic.BotRng;
import dev.nezo.burmaldaholic.core.bots.logic.BotSpeed;
import dev.nezo.burmaldaholic.core.bots.logic.Personality;
import dev.nezo.burmaldaholic.core.bots.logic.SeatPolicy;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * Table-side rules of the UTH atmosphere bots (BOTS.md §4.5–§4.7, §7.4): virtual bets and their timing,
 * player-banked tables (bots watch under a human banker; the dealer-plate stand-in), the virtual seat
 * slots and the round quip. Pure; twin of the matching parts of Bedrock {@code games/uth/logic/bots.ts}.
 */
public final class UthBotRules {
	private UthBotRules() {}

	// ---- virtual bets ------------------------------------------------------------------------------

	/** A bot's virtual bets for a round (never debited, never paid). */
	public record VirtualBet(long ante, long trips) {}

	/** Ante = min + k × min, k by personality (BOTS.md §4.7 virtual amounts). */
	public static int[] personalityUnits(Personality p) {
		return switch (p) {
			case ROCK, STATION -> new int[] {1, 2};
			case TAG -> new int[] {2, 5};
			case MANIAC, LAG -> new int[] {5, 10};
		};
	}

	/** Virtual Ante (within {@code [minAnte, maxAnte]}) and Trips (EASY: 50 % of rounds, 1 × Ante, when the table has Trips). */
	public static VirtualBet virtualBet(BotProfile bot, BotRng rng, long minAnte, long maxAnte, boolean tripsEnabled) {
		long min = Math.max(1, minAnte);
		long max = Math.max(min, maxAnte);
		int[] u = personalityUnits(bot.personality());
		int k = rng.between(u[0], u[1]);
		long ante = Math.min(max, min + k * min);
		long trips = bot.level() == BotDifficulty.EASY && tripsEnabled && rng.chance(UthBotPolicy.EASY_TRIPS) ? ante : 0;
		return new VirtualBet(ante, trips);
	}

	/** When an atmosphere bot puts its chips down: a random moment 60–160 t into BETTING (FAST × factor, INSTANT at once). */
	public static int virtualBetDelay(BotRng rng, BotSpeed speed, double fastFactor) {
		if (speed == BotSpeed.INSTANT) {
			return 0;
		}
		int t = rng.between(60, 160);
		return speed == BotSpeed.FAST ? Math.max(1, (int) Math.round(t * fastFactor)) : t;
	}

	// ---- player-banked tables (BOTS.md §4.6) ------------------------------------------------------------

	/** Player-banked table under a human banker: bots are not dealt in (they show *Watching*). */
	public static boolean botsDealtIn(boolean humanBanker) {
		return !humanBanker;
	}

	/**
	 * @param humanBanker            a human holds (or is about to hold) the dealer seat
	 * @param policy                 the table session's seat policy
	 * @param houseRoundsWhenNoBanker {@code uth.pvp.houseRoundsWhenNoBanker}
	 */
	public record StandInFacts(boolean playerBanked, boolean botsEnabled, boolean humanBanker, SeatPolicy policy, boolean houseRoundsWhenNoBanker) {}

	/**
	 * The dealer plate shows a bot stand-in ("Dealer seat: [BOT] Madame Ender") when nobody banks, the house
	 * deals anyway and the policy lets bots in. Flavour only: the round is a house round in every respect.
	 * Without house rounds there is no bot banker (the table waits for a human).
	 */
	public static boolean standInShown(StandInFacts f) {
		return f.playerBanked() && f.botsEnabled() && !f.humanBanker() && f.policy() != SeatPolicy.HUMANS_ONLY && f.houseRoundsWhenNoBanker();
	}

	/** A stand-in offers the dealer seat to the humans at every rotation point ({@code uth.pvp.bankerRounds} house rounds). */
	public static boolean standInOfferDue(int houseRounds, int bankerRounds) {
		return bankerRounds > 0 && houseRounds > 0 && houseRounds % bankerRounds == 0;
	}

	// ---- virtual seat slots ------------------------------------------------------------------------------

	/**
	 * Player-seat slots (0 … seats − 1) shared by humans (their core seat index) and bots. Bots take the
	 * highest free slot, so they sit "at the far end" and the highest seat yields first (YieldRule
	 * HIGHEST_SEAT). A bot whose slot a human took moves to another free slot.
	 */
	public static final class BotSlots {
		private final Map<String, Integer> slots = new LinkedHashMap<>();

		public List<String> keys() {
			return List.copyOf(slots.keySet());
		}

		public @Nullable Integer slotOf(String key) {
			return slots.get(key);
		}

		public boolean isEmpty() {
			return slots.isEmpty();
		}

		private @Nullable Integer freeSlot(int seats, Set<Integer> humans, @Nullable String except) {
			Set<Integer> bots = new HashSet<>();
			slots.forEach((k, s) -> {
				if (!k.equals(except)) {
					bots.add(s);
				}
			});
			for (int s = seats - 1; s >= 0; s--) {
				if (!humans.contains(s) && !bots.contains(s)) {
					return s;
				}
			}
			return null;
		}

		/** Seats a bot in the highest free slot; null when every slot is taken. */
		public @Nullable Integer place(String key, int seats, Set<Integer> humans) {
			Integer s = freeSlot(seats, humans, key);
			if (s != null) {
				slots.put(key, s);
			}
			return s;
		}

		public boolean remove(String key) {
			return slots.remove(key) != null;
		}

		public void clear() {
			slots.clear();
		}

		/** Moves bots out of slots a human took (or beyond {@code seats}); returns the bots that no longer fit. */
		public List<String> settle(int seats, Set<Integer> humans) {
			List<String> homeless = new ArrayList<>();
			List<Map.Entry<String, Integer>> order = new ArrayList<>(slots.entrySet());
			order.sort(Map.Entry.comparingByValue()); // lowest first: the bot at the far end keeps its seat
			for (Map.Entry<String, Integer> e : order) {
				int s = e.getValue();
				if (s < seats && !humans.contains(s)) {
					continue;
				}
				Integer to = freeSlot(seats, humans, e.getKey());
				if (to == null) {
					slots.remove(e.getKey());
					homeless.add(e.getKey());
				} else {
					slots.put(e.getKey(), to);
				}
			}
			return homeless;
		}

		/** Occupant list of {@code seats} slots (null = empty): humans at their slots, bots at theirs. */
		public <T> List<@Nullable T> occupants(int seats, Map<Integer, T> humans, Map<String, T> bots) {
			List<@Nullable T> out = new ArrayList<>();
			for (int i = 0; i < seats; i++) {
				out.add(null);
			}
			humans.forEach((s, h) -> {
				if (s >= 0 && s < seats) {
					out.set(s, h);
				}
			});
			slots.forEach((key, s) -> {
				T b = bots.get(key);
				if (b != null && s < seats && out.get(s) == null) {
					out.set(s, b);
				}
			});
			return out;
		}
	}

	// ---- chatter (BOTS.md §7.4) --------------------------------------------------------------------------

	public enum Outcome {
		WIN, LOSE, TIE, FOLDED
	}

	/** @param netAntes net in Antes (virtual for bots); {@code blindPaid} = the Blind paid (straight or better) */
	public record SeatResultFacts(String key, boolean bot, Outcome outcome, double netAntes, boolean blindPaid) {}

	public record Quip(String event, String key) {}

	/**
	 * One quip per round at most (lines about a hand only after the reveal): a bot whose Blind paid →
	 * {@code win_big}; else the biggest human win of ≥ 10 Antes → {@code human_wins}.
	 */
	public static @Nullable Quip roundQuip(List<SeatResultFacts> results) {
		for (SeatResultFacts r : results) {
			if (r.bot() && r.outcome() == Outcome.WIN && r.blindPaid()) {
				return new Quip("win_big", r.key());
			}
		}
		return results.stream().filter(r -> !r.bot() && r.outcome() == Outcome.WIN && r.netAntes() >= 10)
			.max(Comparator.comparingDouble(SeatResultFacts::netAntes)).map(r -> new Quip("human_wins", r.key())).orElse(null);
	}
}
