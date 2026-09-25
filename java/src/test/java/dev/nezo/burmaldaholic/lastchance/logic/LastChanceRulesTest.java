package dev.nezo.burmaldaholic.lastchance.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.nezo.burmaldaholic.lastchance.logic.LastChanceRules.Decision;
import dev.nezo.burmaldaholic.lastchance.logic.LastChanceRules.Difficulty;
import dev.nezo.burmaldaholic.lastchance.logic.LastChanceRules.HardcoreMode;
import dev.nezo.burmaldaholic.lastchance.logic.LastChanceRules.Hit;
import dev.nezo.burmaldaholic.lastchance.logic.LastChanceRules.Mode;
import dev.nezo.burmaldaholic.lastchance.logic.LastChanceRules.PlayerFacts;
import dev.nezo.burmaldaholic.lastchance.logic.LastChanceRules.Settings;
import dev.nezo.burmaldaholic.lastchance.logic.LastChanceRules.SkipReason;
import dev.nezo.burmaldaholic.lastchance.logic.LastChanceRules.SuccessPlan;
import dev.nezo.burmaldaholic.lastchance.logic.LastChanceRules.WorldFacts;
import java.util.List;
import java.util.SplittableRandom;
import org.junit.jupiter.api.Test;

class LastChanceRulesTest {
	private static final Settings DEF = Settings.DEFAULTS;
	private static final Settings HS = DEF.withHardcoreMode(HardcoreMode.HIGH_STAKES);

	private static Hit hit() {
		return new Hit(false, false, List.of("minecraft:zombie"));
	}

	private static PlayerFacts player() {
		return new PlayerFacts(1000, 0, 20, false, false, 0);
	}

	private static WorldFacts world() {
		return new WorldFacts(true, false, Difficulty.NORMAL, 100_000);
	}

	private static WorldFacts hardcore() {
		return new WorldFacts(true, true, Difficulty.HARD, 100_000);
	}

	private static SkipReason reason(Hit h, PlayerFacts p, WorldFacts w, Settings s) {
		return LastChanceRules.decide(h, p, w, s).skip();
	}

	@Test
	void defaultsMatchConfigSpec() {
		assertEquals(0.6, LastChanceRules.chanceFor(Difficulty.PEACEFUL, DEF));
		assertEquals(0.6, LastChanceRules.chanceFor(Difficulty.EASY, DEF));
		assertEquals(0.5, LastChanceRules.chanceFor(Difficulty.NORMAL, DEF));
		assertEquals(0.4, LastChanceRules.chanceFor(Difficulty.HARD, DEF));
		assertEquals(24000, LastChanceRules.cooldownFor(Mode.STANDARD, DEF));
		assertEquals(120000, LastChanceRules.cooldownFor(Mode.HIGH_STAKES, DEF));
		assertEquals(HardcoreMode.DISABLED, DEF.hardcoreMode());
	}

	@Test
	void chancesAreClamped() {
		Settings odd = new Settings(true, 2, -1, Double.NaN, 0, 10, HardcoreMode.DISABLED, 3, 0, 0, 2, 8);
		assertEquals(1.0, LastChanceRules.chanceFor(Difficulty.EASY, odd));
		assertEquals(0.0, LastChanceRules.chanceFor(Difficulty.NORMAL, odd));
		assertEquals(0.0, LastChanceRules.chanceFor(Difficulty.HARD, odd));
	}

	@Test
	void hardcoreIsDisabledByDefault() {
		assertEquals(Mode.STANDARD, LastChanceRules.modeFor(false, DEF));
		assertNull(LastChanceRules.modeFor(true, DEF));
		assertEquals(Mode.HIGH_STAKES, LastChanceRules.modeFor(true, HS));
		assertNull(LastChanceRules.modeFor(false, DEF.withEnabled(false)));
		assertNull(LastChanceRules.modeFor(true, HS.withEnabled(false)));
		assertEquals(SkipReason.HARDCORE_DISABLED, reason(hit(), player(), hardcore(), DEF));
	}

	@Test
	void flipsWithDifficultyChance() {
		Decision d = LastChanceRules.decide(hit(), player(), world(), DEF);
		assertTrue(d.flips());
		assertEquals(Mode.STANDARD, d.mode());
		assertEquals(0.5, d.chance());
		assertEquals(24000, d.cooldownTicks());
		assertEquals(0.4, LastChanceRules.decide(hit(), player(), new WorldFacts(true, false, Difficulty.HARD, 0), DEF).chance());
		assertEquals(0.6, LastChanceRules.decide(hit(), player(), new WorldFacts(true, false, Difficulty.PEACEFUL, 0), DEF).chance());
	}

	@Test
	void exclusions() {
		assertEquals(SkipReason.CASINO_OFF, reason(hit(), player(), new WorldFacts(false, false, Difficulty.NORMAL, 0), DEF));
		assertEquals(SkipReason.DISABLED, reason(hit(), player(), world(), DEF.withEnabled(false)));
		assertEquals(SkipReason.TOTEM, reason(hit(), new PlayerFacts(1000, 0, 20, true, false, 0), world(), DEF));
		assertEquals(SkipReason.EXCLUDED_CAUSE, reason(new Hit(true, false, List.of()), player(), world(), DEF));
		assertEquals(SkipReason.SOUL_WAGER, reason(new Hit(false, true, List.of()), player(), hardcore(), HS));
		// debt collectors: only final in Hardcore
		Hit collector = new Hit(false, false, List.of("burmaldaholic:enforcer"));
		assertEquals(SkipReason.SQUAD_HARDCORE, reason(collector, player(), hardcore(), HS));
		assertNull(reason(collector, player(), world(), DEF), "outside Hardcore collectors are ordinary mobs");
		Hit arrow = new Hit(false, false, List.of("minecraft:arrow", "burmaldaholic:repo_man"));
		assertEquals(SkipReason.SQUAD_HARDCORE, reason(arrow, player(), hardcore(), HS));
	}

	@Test
	void cooldown() {
		PlayerFacts recent = new PlayerFacts(1000, 0, 20, false, true, 90_000);
		assertEquals(SkipReason.COOLDOWN, reason(hit(), recent, world(), DEF));
		PlayerFacts old = new PlayerFacts(1000, 0, 20, false, true, 100_000 - 24_000);
		assertNull(reason(hit(), old, world(), DEF), "ready exactly at usedAt + cooldown");
		assertEquals(0, LastChanceRules.cooldownRemaining(5, false, 0, 24000));
		assertEquals(14000, LastChanceRules.cooldownRemaining(100_000, true, 90_000, 24000));
		// the Hardcore cooldown is longer
		PlayerFacts hsRecent = new PlayerFacts(1000, 0, 20, false, true, 100_000 - 100_000);
		assertEquals(SkipReason.COOLDOWN, reason(hit(), hsRecent, hardcore(), HS));
	}

	@Test
	void highStakesEligibility() {
		assertTrue(LastChanceRules.highStakesEligible(60, 40, 20, HS), "balance + carried chips");
		assertFalse(LastChanceRules.highStakesEligible(60, 39, 20, HS));
		assertFalse(LastChanceRules.highStakesEligible(1000, 0, 6, HS), "too scarred");
		assertTrue(LastChanceRules.highStakesEligible(1000, 0, 8, HS));
		assertEquals(SkipReason.NOT_ELIGIBLE, reason(hit(), new PlayerFacts(50, 0, 20, false, false, 0), hardcore(), HS));
		Decision d = LastChanceRules.decide(hit(), player(), hardcore(), HS);
		assertTrue(d.flips());
		assertEquals(Mode.HIGH_STAKES, d.mode());
		assertEquals(0.5, d.chance(), "fixed, independent of difficulty");
		assertEquals(120000, d.cooldownTicks());
	}

	@Test
	void successCosts() {
		SuccessPlan std = LastChanceRules.planSuccess(Mode.STANDARD, DEF, 1234);
		assertEquals(123, std.fee(), "floor(10 %)");
		assertFalse(std.destroyChips());
		assertEquals(0, std.addScarHp());
		assertEquals(0, LastChanceRules.planSuccess(Mode.STANDARD, DEF, 9).fee());
		assertEquals(0, LastChanceRules.planSuccess(Mode.STANDARD, DEF, -5).fee());
		assertEquals(Long.MAX_VALUE / 10, LastChanceRules.planSuccess(Mode.STANDARD, DEF, Long.MAX_VALUE).fee(), "no overflow");
		SuccessPlan hs = LastChanceRules.planSuccess(Mode.HIGH_STAKES, HS, 777);
		assertEquals(777, hs.fee(), "all chips");
		assertTrue(hs.destroyChips());
		assertEquals(2, hs.addScarHp());
	}

	@Test
	void reviveHealthIsHalfRoundedUp() {
		assertEquals(10f, LastChanceRules.reviveHealth(20));
		assertEquals(9f, LastChanceRules.reviveHealth(18));
		assertEquals(9f, LastChanceRules.reviveHealth(17));
		assertEquals(1f, LastChanceRules.reviveHealth(1));
	}

	@Test
	void readyMessage() {
		assertFalse(LastChanceRules.readyDue(false, false, 0, 99_999, 24000));
		assertFalse(LastChanceRules.readyDue(true, false, 90_000, 100_000, 24000));
		assertTrue(LastChanceRules.readyDue(true, false, 76_000, 100_000, 24000));
		assertFalse(LastChanceRules.readyDue(true, true, 76_000, 100_000, 24000), "sent once");
		assertFalse(LastChanceRules.readyDue(true, false, 76_000, 100_000, 0), "no cooldown, no message");
	}

	@Test
	void formatting() {
		assertEquals("1", LastChanceRules.heartsNumber(2));
		assertEquals("1½", LastChanceRules.heartsNumber(3));
		assertEquals("½", LastChanceRules.heartsNumber(1));
		assertEquals(20, LastChanceRules.wholeMinutes(24000));
		assertEquals(100, LastChanceRules.wholeMinutes(120000));
		assertEquals(2, LastChanceRules.wholeMinutes(1201));
		assertEquals(-1, LastChanceRules.wholeMinutes(1199));
		assertEquals(60, LastChanceRules.wholeSeconds(1199));
	}

	@Test
	void flipEdges() {
		assertTrue(LastChanceRules.flip(0.999999, 1.0));
		assertFalse(LastChanceRules.flip(0.0, 0.0));
		assertFalse(LastChanceRules.flip(0.5, 0.5));
		assertTrue(LastChanceRules.flip(0.4999, 0.5));
	}

	@Test
	void monteCarloSuccessRateMatchesChance() {
		SplittableRandom rng = new SplittableRandom(42);
		for (Difficulty diff : Difficulty.values()) {
			double p = LastChanceRules.chanceFor(diff, DEF);
			int n = 200_000;
			int ok = 0;
			for (int i = 0; i < n; i++) {
				if (LastChanceRules.flip(rng.nextDouble(), p)) {
					ok++;
				}
			}
			assertEquals(p, ok / (double) n, 0.005, diff.name());
		}
	}
}
