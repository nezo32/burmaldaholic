package dev.nezo.burmaldaholic.chaos.logic;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.nezo.burmaldaholic.chaos.logic.ChaosRules.Difficulty;
import dev.nezo.burmaldaholic.chaos.logic.ChaosRules.Weather;
import dev.nezo.burmaldaholic.core.config.sections.ChaosConfig;
import dev.nezo.burmaldaholic.core.rng.CasinoRng;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SplittableRandom;
import org.junit.jupiter.api.Test;

class ChaosRulesTest {
	private static CasinoRng rng(long seed) {
		return new CasinoRng(new SplittableRandom(seed), p -> p);
	}

	private static Map<ChaosEvent, Integer> defaults() {
		Map<ChaosEvent, Integer> w = new EnumMap<>(ChaosEvent.class);
		for (ChaosEvent e : ChaosEvent.values()) {
			w.put(e, e.defaultWeight());
		}
		return w;
	}

	@Test
	void idsMatchConfigFamilies() {
		ChaosConfig cfg = new ChaosConfig();
		for (ChaosEvent e : ChaosEvent.values()) {
			assertEquals(e.defaultWeight(), cfg.weight.get(e.id()), "weight " + e.id());
			assertTrue(cfg.event.containsKey(e.id()), "toggle " + e.id());
			assertEquals(e, ChaosEvent.byId(e.id().toUpperCase()).orElseThrow());
		}
		assertEquals(cfg.weight.size(), ChaosEvent.values().length);
		assertTrue(ChaosEvent.byId("creeper").isEmpty());
		assertTrue(ChaosEvent.byId(null).isEmpty());
	}

	@Test
	void pickFollowsWeights() {
		Map<ChaosEvent, Integer> w = defaults();
		int total = w.values().stream().mapToInt(Integer::intValue).sum();
		Map<ChaosEvent, Integer> counts = new EnumMap<>(ChaosEvent.class);
		CasinoRng r = rng(1);
		int n = 200_000;
		for (int i = 0; i < n; i++) {
			counts.merge(ChaosRules.pick(r, w), 1, Integer::sum);
		}
		for (ChaosEvent e : ChaosEvent.values()) {
			double expected = (double) e.defaultWeight() / total;
			double actual = counts.getOrDefault(e, 0) / (double) n;
			assertEquals(expected, actual, 0.005, e.id());
		}
	}

	@Test
	void pickSkipsZeroAndExcluded() {
		Map<ChaosEvent, Integer> w = new EnumMap<>(ChaosEvent.class);
		w.put(ChaosEvent.CURSE, 5);
		w.put(ChaosEvent.MOB_WAVE, 0);
		CasinoRng r = rng(2);
		for (int i = 0; i < 100; i++) {
			assertEquals(ChaosEvent.CURSE, ChaosRules.pick(r, w));
		}
		assertNull(ChaosRules.pick(r, w, Set.of(ChaosEvent.CURSE), null));
		assertNull(ChaosRules.pick(r, new EnumMap<>(ChaosEvent.class)));
	}

	@Test
	void peacefulMobWaveRerollsWithoutWaveOrGoldenHour() {
		CasinoRng r = rng(3);
		Set<ChaosEvent> seen = new HashSet<>();
		for (int i = 0; i < 5000; i++) {
			ChaosEvent e = ChaosRules.resolve(r, ChaosEvent.MOB_WAVE, true, true, defaults());
			assertNotEquals(ChaosEvent.MOB_WAVE, e);
			assertNotEquals(ChaosEvent.GOLDEN_HOUR, e);
			seen.add(e);
		}
		assertTrue(seen.contains(ChaosEvent.WEATHER_CHANGE));
		for (int i = 0; i < 2000; i++) {
			assertNotEquals(ChaosEvent.WEATHER_CHANGE, ChaosRules.resolve(r, ChaosEvent.MOB_WAVE, true, false, defaults()));
		}
		assertEquals(ChaosEvent.MOB_WAVE, ChaosRules.resolve(r, ChaosEvent.MOB_WAVE, false, true, defaults()));
	}

	@Test
	void weatherOutsideOverworldRerollsGoodEvent() {
		CasinoRng r = rng(4);
		for (int i = 0; i < 2000; i++) {
			ChaosEvent e = ChaosRules.resolve(r, ChaosEvent.WEATHER_CHANGE, false, false, defaults());
			assertEquals(ChaosEvent.Kind.GOOD, e.kind());
			assertNotEquals(ChaosEvent.GOLDEN_HOUR, e);
		}
		assertEquals(ChaosEvent.WEATHER_CHANGE, ChaosRules.resolve(r, ChaosEvent.WEATHER_CHANGE, false, true, defaults()));
	}

	@Test
	void rollRangeInclusiveAndTolerant() {
		CasinoRng r = rng(5);
		Set<Integer> seen = new HashSet<>();
		for (int i = 0; i < 2000; i++) {
			int v = ChaosRules.rollRange(r, 3, 6);
			assertTrue(v >= 3 && v <= 6);
			seen.add(v);
			int s = ChaosRules.rollRange(r, 6, 3);
			assertTrue(s >= 3 && s <= 6);
			assertEquals(0, ChaosRules.rollRange(r, -5, 0));
		}
		assertEquals(Set.of(3, 4, 5, 6), seen);
	}

	@Test
	void diamondRainHardIsOneLess() {
		CasinoRng r = rng(6);
		for (int i = 0; i < 2000; i++) {
			int normal = ChaosRules.diamondRainCount(r, 3, 6, false);
			int hard = ChaosRules.diamondRainCount(r, 3, 6, true);
			assertTrue(normal >= 3 && normal <= 6);
			assertTrue(hard >= 2 && hard <= 5);
		}
		assertEquals(0, ChaosRules.diamondRainCount(r, 0, 0, true));
	}

	@Test
	void mobWaveSizesPerDifficulty() {
		assertEquals(0, ChaosRules.mobWaveSize(Difficulty.PEACEFUL, false, 3, 4, 6));
		assertEquals(3, ChaosRules.mobWaveSize(Difficulty.EASY, false, 3, 4, 6));
		assertEquals(4, ChaosRules.mobWaveSize(Difficulty.NORMAL, false, 3, 4, 6));
		assertEquals(6, ChaosRules.mobWaveSize(Difficulty.HARD, false, 3, 4, 6));
		assertEquals(6, ChaosRules.mobWaveSize(Difficulty.HARD, true, 3, 4, 6));
		assertEquals(Difficulty.NORMAL, Difficulty.of("normal"));
	}

	@Test
	void waveCompositionNeverCreepersAndBalanced() {
		CasinoRng r = rng(7);
		for (String dim : List.of("overworld", "the_nether", "the_end", "custom")) {
			List<String> mobs = ChaosRules.waveComposition(r, dim, 6);
			assertEquals(6, mobs.size());
			assertFalse(mobs.contains("creeper"));
			for (String m : ChaosRules.waveMobs(dim)) {
				long c = mobs.stream().filter(m::equals).count();
				assertEquals(6 / ChaosRules.waveMobs(dim).size(), c, dim + " " + m);
			}
		}
		assertEquals(List.of("skeleton", "magma_cube"), ChaosRules.waveMobs("the_nether"));
		assertEquals(List.of("endermite", "skeleton"), ChaosRules.waveMobs("the_end"));
	}

	@Test
	void chipShowerSplitExact() {
		CasinoRng r = rng(8);
		for (int amount = 0; amount <= 150; amount++) {
			int[] s = ChaosRules.chipShowerSplit(r, amount);
			assertEquals(amount, s[0] * 5 + s[1]);
			assertTrue(s[0] >= 0 && s[1] >= 0);
		}
	}

	@Test
	void splitEvenSumsUp() {
		assertArrayEquals(new int[] {26, 25, 25, 25, 25, 25}, ChaosRules.splitEven(151, 6));
		assertArrayEquals(new int[] {0}, ChaosRules.splitEven(-3, 0));
	}

	@Test
	void offsetsWithinDistance() {
		CasinoRng r = rng(9);
		for (int i = 0; i < 5000; i++) {
			int[] o = ChaosRules.randomOffset(r, 32, 256);
			double d = Math.hypot(o[0], o[1]);
			assertTrue(d >= 31 && d <= 257, "distance " + d);
		}
	}

	@Test
	void effectPoolsMatchSpecAndAreNonLethal() {
		List<String> buffs = ChaosRules.BUFFS.stream().map(ChaosRules.EffectSpec::id).toList();
		List<String> curses = ChaosRules.CURSES.stream().map(ChaosRules.EffectSpec::id).toList();
		assertEquals(List.of("speed", "haste", "regeneration", "strength", "luck", "jump_boost", "fire_resistance"), buffs);
		assertEquals(List.of("slowness", "mining_fatigue", "hunger", "weakness", "unluck", "glowing"), curses);
		for (ChaosRules.EffectSpec e : ChaosRules.BUFFS) {
			assertFalse(ChaosRules.LETHAL_EFFECTS.contains(e.id()));
		}
		for (ChaosRules.EffectSpec e : ChaosRules.CURSES) {
			assertFalse(ChaosRules.LETHAL_EFFECTS.contains(e.id()));
			assertEquals(0, e.amplifier(), "curses are level I");
		}
		CasinoRng r = rng(10);
		for (int i = 0; i < 1000; i++) {
			ChaosRules.RolledEffect re = ChaosRules.rollEffect(r, ChaosRules.CURSES, 600, 1800);
			assertTrue(re.ticks() >= 600 && re.ticks() <= 1800);
		}
	}

	@Test
	void weatherCycle() {
		assertEquals(Weather.RAIN, Weather.CLEAR.next());
		assertEquals(Weather.THUNDER, Weather.RAIN.next());
		assertEquals(Weather.CLEAR, Weather.THUNDER.next());
		assertEquals(Weather.CLEAR, Weather.of(false, true));
		assertEquals(Weather.THUNDER, Weather.of(true, true));
		assertEquals(Weather.RAIN, Weather.of(true, false));
	}

	@Test
	void bigWin() {
		assertTrue(ChaosRules.isBigWin(10, 500, 50, 500));
		assertFalse(ChaosRules.isBigWin(10, 499, 50, 500), "below min chips");
		assertFalse(ChaosRules.isBigWin(100, 4900, 50, 500), "below 50x");
		assertTrue(ChaosRules.isBigWin(100, 5000, 50, 500));
		assertFalse(ChaosRules.isBigWin(0, 5000, 50, 500), "no stake");
	}

	@Test
	void cooldown() {
		assertTrue(ChaosRules.cooldownReady(null, 100, 3000));
		assertFalse(ChaosRules.cooldownReady(100L, 3099, 3000));
		assertTrue(ChaosRules.cooldownReady(100L, 3100, 3000));
		assertTrue(ChaosRules.cooldownReady(5000L, 100, 3000), "clock went backwards");
	}

	@Test
	void sunsetOncePerDay() {
		assertFalse(ChaosRules.sunsetDue(-1, 12000, 0, -1), "unknown previous time");
		assertTrue(ChaosRules.sunsetDue(11980, 12000, 0, -1));
		assertFalse(ChaosRules.sunsetDue(11980, 12000, 0, 0), "already rolled today");
		assertFalse(ChaosRules.sunsetDue(12000, 12020, 0, -1), "not a crossing");
		assertFalse(ChaosRules.sunsetDue(23990, 10, 1, 0), "midnight wrap");
		assertTrue(ChaosRules.sunsetDue(6000, 18000, 3, 2), "time skipped over sunset (e.g. /time add)");
	}

	@Test
	void goldenHourEligibility() {
		assertTrue(ChaosRules.goldenHourEligible("slots"));
		assertTrue(ChaosRules.goldenHourEligible("blackjack"));
		assertTrue(ChaosRules.goldenHourEligible("extras"));
		assertFalse(ChaosRules.goldenHourEligible("poker"));
		assertFalse(ChaosRules.goldenHourEligible("extras.dice_duel"));
		assertFalse(ChaosRules.goldenHourEligible("dice_duel"));
		assertFalse(ChaosRules.goldenHourEligible(null));
	}

	@Test
	void durations() {
		assertEquals(new ChaosRules.Duration(false, 3), ChaosRules.duration(60));
		assertEquals(new ChaosRules.Duration(false, 90), ChaosRules.duration(1800));
		assertEquals(new ChaosRules.Duration(true, 3), ChaosRules.duration(3600));
		assertEquals(new ChaosRules.Duration(true, 2), ChaosRules.duration(2437));
		assertEquals(new ChaosRules.Duration(false, 1), ChaosRules.duration(1));
	}
}
