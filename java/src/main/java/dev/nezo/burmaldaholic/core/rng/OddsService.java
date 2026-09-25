package dev.nezo.burmaldaholic.core.rng;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.function.ToIntFunction;
import java.util.random.RandomGenerator;

/**
 * The ONLY source of randomness for casino outcomes. Pure Java (unit-testable with a seeded
 * {@link RandomGenerator}; config {@code debug.fixedSeed} ≠ 0 makes the server deterministic).
 *
 * <pre>
 * // Table games (blackjack, poker, roulette, craps, dice duel): always fair
 * CasinoRng rng = OddsService.get().rng(new OddsContext(player.getUUID(), "roulette", bet));
 * int pocket = rng.nextInt(37);
 *
 * // RNG games (slots, wheel, plinko, scratch, coin flip): streak re-draw of §14 applies
 * SpinResult r = OddsService.get().play(ctx, 0.8976,                // the game's RTP (§17)
 *     () -&gt; machine.spin(rng),                                      // one fair draw
 *     res -&gt; res.totalReturn() &lt; bet);                              // "losing outcome"
 * </pre>
 *
 * Modifiers ({@link #addModifier}) can additionally tilt {@link CasinoRng#chance}/{@link CasinoRng#weighted}
 * favourable probabilities (order: 200 vip, 300 chaos, 400 lastchance). The streak is NOT a
 * modifier: it is the re-draw rule in {@link #play}, owned by core.
 */
public final class OddsService {
	private static OddsService instance = new OddsService(RandomGenerator.of("L64X128MixRandom"));

	private final List<Registered> modifiers = new CopyOnWriteArrayList<>();
	private final RandomGenerator random;
	private volatile ToIntFunction<UUID> streakLookup = id -> 0;
	private volatile Supplier<StreakRules.Settings> streakSettings = () -> StreakRules.Settings.DEFAULTS;

	public OddsService(RandomGenerator random) {
		this.random = Objects.requireNonNull(random);
	}

	public static OddsService get() {
		return instance;
	}

	/** Tests / core only. */
	public static void set(OddsService service) {
		OddsService previous = instance;
		instance = Objects.requireNonNull(service);
		service.modifiers.addAll(previous.modifiers.stream().filter(m -> !service.modifiers.contains(m)).toList());
		service.streakLookup = previous.streakLookup;
		service.streakSettings = previous.streakSettings;
	}

	/** Core only: where streaks come from (StreakTracker) and the {@code streak.*} config. */
	public void setStreakSource(ToIntFunction<UUID> lookup, Supplier<StreakRules.Settings> settings) {
		this.streakLookup = Objects.requireNonNull(lookup);
		this.streakSettings = Objects.requireNonNull(settings);
	}

	/** Lower {@code order} runs first. Convention: 200 vip, 300 chaos, 400 lastchance. */
	public void addModifier(String ownerModule, int order, OddsModifier modifier) {
		modifiers.add(new Registered(ownerModule, order, modifier));
		modifiers.sort((a, b) -> Integer.compare(a.order, b.order));
	}

	public double adjusted(OddsContext ctx, double base) {
		double p = clamp(base);
		for (Registered r : modifiers) {
			p = clamp(r.modifier.adjust(ctx, p));
		}
		return p;
	}

	public CasinoRng rng(OddsContext ctx) {
		return new CasinoRng(random, p -> adjusted(ctx, p));
	}

	/** Fair RNG not tied to a player (bots, ambient events). */
	public CasinoRng fair() {
		return new CasinoRng(random, p -> p);
	}

	/**
	 * Draws an RNG-game outcome with the streak rule of §14: if the first draw is losing, it is
	 * re-drawn once with probability {@link StreakRules#redrawProbability}; the second draw is final.
	 *
	 * @param rtp     the game's RTP (§17 constants; Plinko: chosen risk; slots: total incl. jackpot)
	 * @param draw    one fair draw (use {@link #rng}/{@link #fair} inside)
	 * @param losing  true if the outcome returns less than the stake
	 */
	public <T> T play(OddsContext ctx, double rtp, Supplier<T> draw, Predicate<T> losing) {
		T first = draw.get();
		if (!losing.test(first) || ctx.playerId() == null) {
			return first;
		}
		double r = redrawProbability(ctx.playerId(), rtp);
		if (r > 0 && random.nextDouble() < r) {
			return draw.get();
		}
		return first;
	}

	/** Current re-draw probability for a player on a game with the given RTP. */
	public double redrawProbability(UUID player, double rtp) {
		return StreakRules.redrawProbability(streakLookup.applyAsInt(player), rtp, streakSettings.get());
	}

	private static double clamp(double p) {
		return Double.isNaN(p) ? 0.0 : Math.max(0.0, Math.min(1.0, p));
	}

	private record Registered(String owner, int order, OddsModifier modifier) {}
}
