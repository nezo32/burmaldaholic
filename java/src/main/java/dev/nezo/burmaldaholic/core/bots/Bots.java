package dev.nezo.burmaldaholic.core.bots;

import dev.nezo.burmaldaholic.core.bots.logic.BotProfile;
import dev.nezo.burmaldaholic.core.bots.logic.BotRng;
import dev.nezo.burmaldaholic.core.bots.logic.BotSpeed;
import dev.nezo.burmaldaholic.core.bots.logic.ThinkTime;
import dev.nezo.burmaldaholic.core.config.CasinoConfig;

/**
 * Static entry point of the Seats &amp; Bots core (BOTS.md). Server thread. Registered by core
 * ({@link #register()} from {@code CoreModule.register}). World-wide limits ({@code bots.maxActive},
 * {@code bots.maxActiveTables}, {@code bots.maxConcurrentJobs}), the heavy-job scheduler
 * ({@link BotJobs}) and the daily ledger ({@link BotLedger}) live here.
 */
public final class Bots {
	private static int active;

	private Bots() {}

	/** Core only. */
	public static void register() {
		BotJobs.register();
		// TODO(J-B1): SERVER_STARTED → return orphaned bot stacks recorded in BotLedgerData to their purses.
	}

	/** New bot random stream; {@code debug.fixedSeed ≠ 0} → deterministic ({@code seed ^ 0xB07}). */
	public static BotRng newRng() {
		long seed = CasinoConfig.debug().fixedSeed;
		return seed != 0 ? BotRng.seeded(seed ^ 0xB07L) : BotRng.fresh();
	}

	public static boolean enabled() {
		return CasinoConfig.bots().enabled;
	}

	/** Bots that may still sit world-wide ({@code bots.maxActive} − active). */
	public static int activeBudgetLeft() {
		return Math.max(0, CasinoConfig.bots().maxActive - active);
	}

	static void countJoined(int n) {
		active = Math.max(0, active + n);
	}

	/** Think ticks with the global / poker ranges (BOTS.md §7.3, §9). */
	public static int thinkTicks(BotRng rng, BotProfile bot, BotSpeed speed, boolean big, int humanTimer, String gameId) {
		var b = CasinoConfig.bots();
		int min = b.think.minTicks;
		int max = b.think.maxTicks;
		if ("poker".equals(gameId)) {
			min = CasinoConfig.poker().botThinkMinTicks;
			max = CasinoConfig.poker().botThinkMaxTicks;
		}
		return ThinkTime.ticks(rng, new ThinkTime.Config(min, max, b.think.fastFactor, b.think.tankTicks), bot, speed, big, humanTimer);
	}
}
