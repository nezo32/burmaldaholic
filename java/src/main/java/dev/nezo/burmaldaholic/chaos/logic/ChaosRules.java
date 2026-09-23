package dev.nezo.burmaldaholic.chaos.logic;

import dev.nezo.burmaldaholic.core.rng.CasinoRng;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Chaos selection rules and amounts (GAME_DESIGN.md §13.1–§13.2, §2.3). PURE: no Minecraft
 * classes; all draws through a (fair) {@link CasinoRng}.
 */
public final class ChaosRules {
	private ChaosRules() {}

	// ---- selection -----------------------------------------------------------------------------

	/** Weighted pick among events with weight &gt; 0, not excluded and matching {@code only}; null if none. */
	public static ChaosEvent pick(CasinoRng rng, Map<ChaosEvent, Integer> weights, Set<ChaosEvent> exclude, Predicate<ChaosEvent> only) {
		long total = 0;
		List<ChaosEvent> events = new ArrayList<>();
		List<Integer> ws = new ArrayList<>();
		for (ChaosEvent e : ChaosEvent.values()) {
			int w = Math.max(0, weights.getOrDefault(e, 0));
			if (w > 0 && !exclude.contains(e) && (only == null || only.test(e))) {
				events.add(e);
				ws.add(w);
				total += w;
			}
		}
		if (total <= 0) {
			return null;
		}
		int roll = rng.nextInt((int) Math.min(Integer.MAX_VALUE, total));
		for (int i = 0; i < events.size(); i++) {
			roll -= ws.get(i);
			if (roll < 0) {
				return events.get(i);
			}
		}
		return events.getLast();
	}

	public static ChaosEvent pick(CasinoRng rng, Map<ChaosEvent, Integer> weights) {
		return pick(rng, weights, Set.of(), null);
	}

	/**
	 * The event actually run for a requested one (§13.4 rerolls): {@code mob_wave} in Peaceful is
	 * rerolled from the remaining events (ambient weights); {@code weather_change} outside the
	 * Overworld is rerolled once from the good events. Golden Hour is never a reroll result.
	 * Returns null when nothing is left to run.
	 */
	public static ChaosEvent resolve(CasinoRng rng, ChaosEvent requested, boolean peaceful, boolean overworld, Map<ChaosEvent, Integer> weights) {
		if (requested == ChaosEvent.MOB_WAVE && peaceful) {
			Set<ChaosEvent> exclude = EnumSet.of(ChaosEvent.GOLDEN_HOUR, ChaosEvent.MOB_WAVE);
			if (!overworld) {
				exclude.add(ChaosEvent.WEATHER_CHANGE);
			}
			return pick(rng, weights, exclude, null);
		}
		if (requested == ChaosEvent.WEATHER_CHANGE && !overworld) {
			return pick(rng, weights, EnumSet.of(ChaosEvent.GOLDEN_HOUR), e -> e.kind() == ChaosEvent.Kind.GOOD);
		}
		return requested;
	}

	// ---- amounts -------------------------------------------------------------------------------

	/** Uniform integer in [min, max]; tolerates min &gt; max (swapped) and negatives (clamped to 0). */
	public static int rollRange(CasinoRng rng, int min, int max) {
		int lo = Math.max(0, Math.min(min, max));
		int hi = Math.max(0, Math.max(min, max));
		return lo + rng.nextInt(hi - lo + 1);
	}

	/** Diamond count: Hard/Hardcore get one less at both ends (§13.2 "Hard/Hardcore: 2–5"). */
	public static int diamondRainCount(CasinoRng rng, int min, int max, boolean hard) {
		int d = hard ? 1 : 0;
		return rollRange(rng, Math.max(0, min - d), Math.max(0, max - d));
	}

	/** Difficulty names as used by {@link #mobWaveSize}. */
	public enum Difficulty {
		PEACEFUL, EASY, NORMAL, HARD;

		public static Difficulty of(String name) {
			return valueOf(name.toUpperCase(Locale.ROOT));
		}
	}

	/** Mob-wave size (§2.3): Peaceful never; Hardcore uses the Hard value. */
	public static int mobWaveSize(Difficulty difficulty, boolean hardcore, int easy, int normal, int hard) {
		if (hardcore) {
			return Math.max(0, hard);
		}
		return switch (difficulty) {
			case PEACEFUL -> 0;
			case EASY -> Math.max(0, easy);
			case NORMAL -> Math.max(0, normal);
			case HARD -> Math.max(0, hard);
		};
	}

	/** Mob-wave composition by dimension (§13.2). Never creepers. Magma cubes spawn at size 2. */
	public static List<String> waveMobs(String dimension) {
		return switch (dimension) {
			case "the_nether" -> List.of("skeleton", "magma_cube");
			case "the_end" -> List.of("endermite", "skeleton");
			default -> List.of("zombie", "skeleton", "spider");
		};
	}

	/** {@code n} mob ids in equal proportion (round-robin from a random start). */
	public static List<String> waveComposition(CasinoRng rng, String dimension, int n) {
		List<String> pool = waveMobs(dimension);
		int start = rng.nextInt(pool.size());
		List<String> out = new ArrayList<>(Math.max(0, n));
		for (int i = 0; i < n; i++) {
			out.add(pool.get((start + i) % pool.size()));
		}
		return out;
	}

	/** Splits a chip-shower amount into {fives, ones} (random mix, exact total). */
	public static int[] chipShowerSplit(CasinoRng rng, int amount) {
		int total = Math.max(0, amount);
		int maxFives = total / 5;
		int lo = maxFives / 2;
		int fives = lo + rng.nextInt(maxFives - lo + 1);
		return new int[] {fives, total - fives * 5};
	}

	/** Splits {@code total} into {@code parts} near-equal non-negative integers. */
	public static int[] splitEven(int total, int parts) {
		int n = Math.max(1, parts);
		int t = Math.max(0, total);
		int base = t / n;
		int rest = t - base * n;
		int[] out = new int[n];
		for (int i = 0; i < n; i++) {
			out[i] = base + (i < rest ? 1 : 0);
		}
		return out;
	}

	/** Horizontal offset {dx, dz} at a distance in [min, max] in a uniformly random direction. */
	public static int[] randomOffset(CasinoRng rng, double min, double max) {
		double lo = Math.min(min, max);
		double hi = Math.max(min, max);
		double dist = lo + rng.nextDouble() * (hi - lo);
		double a = rng.nextDouble() * Math.PI * 2;
		return new int[] {(int) Math.round(Math.cos(a) * dist), (int) Math.round(Math.sin(a) * dist)};
	}

	// ---- buffs / curses ------------------------------------------------------------------------

	/** An effect by vanilla registry path with a 0-based amplifier (Speed II = 1). */
	public record EffectSpec(String id, int amplifier) {}

	/** §13.2 {@code lucky_buff}: Speed II, Haste II, Regeneration I, Strength I, Luck I, Jump Boost II, Fire Resistance. */
	public static final List<EffectSpec> BUFFS = List.of(new EffectSpec("speed", 1), new EffectSpec("haste", 1),
		new EffectSpec("regeneration", 0), new EffectSpec("strength", 0), new EffectSpec("luck", 0), new EffectSpec("jump_boost", 1),
		new EffectSpec("fire_resistance", 0));

	/** §13.2 {@code curse}: Slowness I, Mining Fatigue I, Hunger I, Weakness I, Bad Luck I, Glowing. */
	public static final List<EffectSpec> CURSES = List.of(new EffectSpec("slowness", 0), new EffectSpec("mining_fatigue", 0),
		new EffectSpec("hunger", 0), new EffectSpec("weakness", 0), new EffectSpec("unluck", 0), new EffectSpec("glowing", 0));

	/** Never applied by chaos (§13.4). */
	public static final Set<String> LETHAL_EFFECTS = Set.of("poison", "wither", "instant_damage", "levitation");

	public record RolledEffect(EffectSpec effect, int ticks) {}

	public static RolledEffect rollEffect(CasinoRng rng, List<EffectSpec> pool, int minTicks, int maxTicks) {
		EffectSpec e = pool.get(rng.nextInt(pool.size()));
		return new RolledEffect(e, Math.max(20, rollRange(rng, minTicks, maxTicks)));
	}

	// ---- weather -------------------------------------------------------------------------------

	public enum Weather {
		CLEAR, RAIN, THUNDER;

		/** clear → rain → thunder → clear (§13.2). */
		public Weather next() {
			return switch (this) {
				case CLEAR -> RAIN;
				case RAIN -> THUNDER;
				case THUNDER -> CLEAR;
			};
		}

		public String id() {
			return name().toLowerCase(Locale.ROOT);
		}

		public static Weather of(boolean raining, boolean thundering) {
			return !raining ? CLEAR : thundering ? THUNDER : RAIN;
		}
	}

	// ---- triggers ------------------------------------------------------------------------------

	/** §13.1.3: a single settlement with net profit ≥ multiple × stake AND ≥ minChips. */
	public static boolean isBigWin(long stake, long net, long multiple, long minChips) {
		return stake >= 1 && net > 0 && net >= multiple * stake && net >= minChips;
	}

	/** Per-player cooldown (world ticks). A clock that went backwards resets it. */
	public static boolean cooldownReady(Long lastEventTick, long now, long cooldownTicks) {
		return lastEventTick == null || now - lastEventTick >= cooldownTicks || now < lastEventTick;
	}

	/**
	 * Sunset roll (§13.1.5): true once per Minecraft day when the time of day crosses 12000.
	 * {@code prevTimeOfDay} &lt; 0 means unknown (first tick after start: no roll).
	 */
	public static boolean sunsetDue(long prevTimeOfDay, long timeOfDay, long day, long lastRolledDay) {
		if (lastRolledDay == day || prevTimeOfDay < 0) {
			return false;
		}
		return prevTimeOfDay <= timeOfDay && prevTimeOfDay < 12000 && timeOfDay >= 12000;
	}

	/**
	 * Games whose wins get NO Golden Hour bonus (§13.3: all house-banked games except PvP poker and
	 * PvP dice). {@code PLAY_RESOLVED} carries only the game id, so this goes by id.
	 */
	public static boolean goldenHourEligible(String gameId) {
		if (gameId == null) {
			return false;
		}
		String g = gameId.toLowerCase(Locale.ROOT);
		return !(g.equals("poker") || g.startsWith("poker") || g.contains("dice_duel") || g.contains("pvp"));
	}

	// ---- durations -----------------------------------------------------------------------------

	/** How to show a duration in text: as seconds (&lt; 2 min) or as rounded minutes. */
	public record Duration(boolean minutes, long amount) {}

	public static Duration duration(long ticks) {
		long seconds = (Math.max(0, ticks) + 19) / 20;
		if (seconds < 120) {
			return new Duration(false, seconds);
		}
		return new Duration(true, Math.round(seconds / 60.0));
	}
}
