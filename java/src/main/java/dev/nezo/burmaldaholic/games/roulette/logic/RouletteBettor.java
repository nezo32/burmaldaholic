package dev.nezo.burmaldaholic.games.roulette.logic;

import dev.nezo.burmaldaholic.core.bots.logic.BotPolicy;
import dev.nezo.burmaldaholic.core.bots.logic.BotProfile;
import dev.nezo.burmaldaholic.core.bots.logic.BotRng;
import dev.nezo.burmaldaholic.core.bots.logic.Personality;
import dev.nezo.burmaldaholic.games.roulette.logic.Bets.Bet;
import java.util.ArrayList;
import java.util.List;

/**
 * Roulette atmosphere bettors (BOTS.md §4.7, pvp-bots.md §4.6). PURE; mirrors Bedrock
 * {@code games/roulette/logic/bots.ts}.
 *
 * <p>Roulette has no decision that matters (every bet carries the same fixed edge), so a bot only has a
 * BETTING STYLE from its personality; difficulty is hidden. Bets are VIRTUAL: they live in the bot's own
 * slip beside the table, never in the shared round, never in a stake; they are settled with the same
 * {@link Bets#totalReturn} only to show "Bots bet (for fun)" results. Every choice uses the BOT rng.
 * <pre>
 *   ROCK    Red Lover       red (or black); flips colour after 3 losses in a row
 *   STATION Dozen Grinder   two dozens
 *   MANIAC  Sprinkler       5–8 random inside chips
 *   TAG     Lucky Number    a favourite number + its two wheel neighbours
 *   LAG     Martingale Fan  one even-money bet, doubled after a loss, reset once past 8 ×
 * </pre>
 * Amounts {@code min + k × min} with k = 1–2 (ROCK, STATION), 2–5 (TAG), 5–10 (MANIAC, LAG), within limits.
 */
public final class RouletteBettor implements BotPolicy<RouletteBettor.View, List<Bet>> {
	public static final RouletteBettor INSTANCE = new RouletteBettor();

	/** Even-money bet types (LAG's Martingale). */
	public static final List<BetType> EVEN_MONEY = List.of(BetType.RED, BetType.BLACK, BetType.ODD, BetType.EVEN, BetType.LOW, BetType.HIGH);
	private static final List<BetType> INSIDE_SPRINKLE = List.of(BetType.STRAIGHT, BetType.SPLIT, BetType.CORNER, BetType.STREET);

	/**
	 * A bot's memory between spins (kept by the table beside the round).
	 *
	 * @param color    ROCK: RED or BLACK
	 * @param losses   ROCK: losses in a row
	 * @param dozen1   STATION: the two dozens (1..3, dozen1 &lt; dozen2)
	 * @param favourite TAG: favourite pocket
	 * @param evenType LAG: the even-money bet
	 * @param unit     LAG: base unit
	 * @param multiple LAG: current multiple of the unit
	 */
	public record Memory(BetType color, int losses, int dozen1, int dozen2, int favourite, BetType evenType, long unit, long multiple) {}

	/**
	 * @param minBet    table minimum per bet
	 * @param insideMax max on one inside spot
	 * @param totalMax  max per spin
	 */
	public record View(long minBet, long insideMax, long totalMax, Memory memory) {}

	private RouletteBettor() {}

	/** k range of {@code min + k × min} per personality. */
	public static int[] kRange(Personality p) {
		return switch (p) {
			case ROCK, STATION -> new int[] {1, 2};
			case TAG -> new int[] {2, 5};
			case MANIAC, LAG -> new int[] {5, 10};
		};
	}

	private static long amount(BotRng rng, Personality p, long min) {
		int[] k = kRange(p);
		return min + rng.between(k[0], k[1]) * min;
	}

	/** Fresh memory for a bot sitting down (bot rng). */
	public static Memory newMemory(BotRng rng, Personality p, long minBet) {
		int d1 = rng.between(1, 3);
		int d2 = rng.between(1, 2);
		if (d2 >= d1) {
			d2++;
		}
		BetType color = rng.nextDouble() < 0.5 ? BetType.RED : BetType.BLACK;
		int favourite = rng.between(0, 36);
		BetType even = rng.pick(EVEN_MONEY);
		long unit = p == Personality.LAG ? amount(rng, p, Math.max(1, minBet)) : Math.max(1, minBet);
		return new Memory(color, 0, Math.min(d1, d2), Math.max(d1, d2), favourite, even, unit, 1);
	}

	private static Bet outside(BetType type, int index, long amount) {
		return new Bet(Spot.of(type, Spot.outsideNumbers(type, index)), amount);
	}

	private static Bet straight(int n, long amount) {
		return new Bet(Spot.of(BetType.STRAIGHT, n), amount);
	}

	/** The two wheel neighbours of a pocket (left and right on the wheel). */
	public static int[] wheelNeighbours(int n) {
		int i = Wheel.wheelIndex(n);
		int len = Wheel.ORDER.size();
		return new int[] {Wheel.ORDER.get((i - 1 + len) % len), Wheel.ORDER.get((i + 1) % len)};
	}

	@Override
	public List<Bet> decide(BotProfile bot, View view, Object work, BotRng rng) {
		Memory m = view.memory();
		long min = Math.max(1, view.minBet());
		Personality p = bot.personality();
		return switch (p) {
			case ROCK -> List.of(outside(m.color(), 1, amount(rng, p, min)));
			case STATION -> {
				long a = amount(rng, p, min);
				yield List.of(outside(BetType.DOZEN, m.dozen1(), a), outside(BetType.DOZEN, m.dozen2(), a));
			}
			case MANIAC -> {
				int n = rng.between(5, 8);
				long a = amount(rng, p, min);
				List<Bet> out = new ArrayList<>();
				for (int i = 0; i < n; i++) {
					out.add(new Bet(rng.pick(Spot.all(rng.pick(INSIDE_SPRINKLE))), a));
				}
				yield out;
			}
			case TAG -> {
				long a = amount(rng, p, min);
				int[] nb = wheelNeighbours(m.favourite());
				yield List.of(straight(m.favourite(), a), straight(nb[0], a), straight(nb[1], a));
			}
			case LAG -> List.of(outside(m.evenType(), 1, m.unit() * m.multiple()));
		};
	}

	/** Only valid spots, amounts ≥ min, inside ≤ insideMax, total ≤ totalMax (bets that don't fit are dropped). */
	@Override
	public List<Bet> legalize(View view, List<Bet> bets) {
		long min = Math.max(1, view.minBet());
		List<Bet> slip = new ArrayList<>();
		for (Bet b : bets) {
			if (b == null || !b.spot().isValid()) {
				continue;
			}
			long amt = Math.max(min, b.amount());
			if (b.type().inside()) {
				amt = Math.min(amt, view.insideMax() - Bets.amountOn(slip, b.spot()));
			}
			amt = Math.min(amt, view.totalMax() - Bets.totalStaked(slip));
			if (amt < min) {
				continue;
			}
			slip = Bets.merge(slip, new Bet(b.spot(), amt));
		}
		return slip;
	}

	/** Memory after a spin: ROCK flips after 3 losses; LAG doubles after a loss, resets past 8 ×. */
	public static Memory afterSpin(Personality p, Memory m, List<Bet> bets, int result, boolean laPartage) {
		if (bets.isEmpty()) {
			return m;
		}
		boolean lost = Bets.totalReturn(bets, result, laPartage) < Bets.totalStaked(bets);
		if (p == Personality.ROCK) {
			int losses = lost ? m.losses() + 1 : 0;
			if (losses >= 3) {
				return new Memory(m.color() == BetType.RED ? BetType.BLACK : BetType.RED, 0, m.dozen1(), m.dozen2(), m.favourite(), m.evenType(),
					m.unit(), m.multiple());
			}
			return new Memory(m.color(), losses, m.dozen1(), m.dozen2(), m.favourite(), m.evenType(), m.unit(), m.multiple());
		}
		if (p == Personality.LAG) {
			long next = lost ? m.multiple() * 2 : 1;
			return new Memory(m.color(), m.losses(), m.dozen1(), m.dozen2(), m.favourite(), m.evenType(), m.unit(), next > 8 ? 1 : next);
		}
		return m;
	}
}
