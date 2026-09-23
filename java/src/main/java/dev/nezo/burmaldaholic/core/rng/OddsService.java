package dev.nezo.burmaldaholic.core.rng;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.random.RandomGenerator;

/**
 * The ONLY source of randomness for casino outcomes. Pure Java (unit-testable with a seeded
 * {@link RandomGenerator}).
 *
 * <pre>
 * CasinoRng rng = OddsService.get().rng(new OddsContext(player.getUUID(), "slots", bet));
 * int card = rng.nextInt(52);              // fair, never modified (shuffles, card draws)
 * boolean jackpot = rng.chance(0.001);     // favourable event: passes through all modifiers
 * </pre>
 *
 * Modifiers are registered by the modules that own the effect (chaos, vip, lastchance...).
 * The streak hook is simply a modifier that reads {@link StreakTracker}.
 */
public final class OddsService {
	private static OddsService instance = new OddsService(RandomGenerator.of("L64X128MixRandom"));

	private final List<Registered> modifiers = new CopyOnWriteArrayList<>();
	private final RandomGenerator random;

	public OddsService(RandomGenerator random) {
		this.random = Objects.requireNonNull(random);
	}

	public static OddsService get() {
		return instance;
	}

	/** Tests / core only. */
	public static void set(OddsService service) {
		instance = Objects.requireNonNull(service);
	}

	/** Lower {@code order} runs first. Convention: 100 streak, 200 vip, 300 chaos, 400 lastchance. */
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

	private static double clamp(double p) {
		return Double.isNaN(p) ? 0.0 : Math.max(0.0, Math.min(1.0, p));
	}

	private record Registered(String owner, int order, OddsModifier modifier) {}
}
