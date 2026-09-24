package dev.nezo.burmaldaholic.core.bots;

import dev.nezo.burmaldaholic.Burmaldaholic;
import dev.nezo.burmaldaholic.core.bots.logic.BotProfile;
import dev.nezo.burmaldaholic.core.bots.logic.BotRng;
import dev.nezo.burmaldaholic.core.bots.logic.BotSpeed;
import dev.nezo.burmaldaholic.core.bots.logic.Purse;
import dev.nezo.burmaldaholic.core.bots.logic.ThinkTime;
import dev.nezo.burmaldaholic.core.config.CasinoConfig;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.server.MinecraftServer;

/**
 * Static entry point of the Seats &amp; Bots core (BOTS.md). Server thread. Registered by core
 * ({@link #register()} from {@code CoreModule.register}). World-wide limits ({@code bots.maxActive},
 * {@code bots.maxActiveTables}, {@code bots.maxConcurrentJobs}), the registry of tables with bots, the
 * heavy-job scheduler ({@link BotJobs}), the chatter queue ({@link BotChatter}) and the daily ledger
 * ({@link BotLedger}) live here. On SERVER_STARTED every bankroll-bot holding a crash left behind is
 * returned to its bankroll (§3.5).
 */
public final class Bots {
	/** Tables that currently hold bots, by table key. */
	private static final Map<String, TableBots> LIVE = new LinkedHashMap<>();
	/** Bots seated outside tables (PvP matches, tournaments), see {@link #acquire}. */
	private static int external;

	private Bots() {}

	/** Core only. */
	public static void register() {
		BotJobs.register();
		BotChatter.register();
		// a closed bankroll's tombstone stays while bots funded by it may still bring chips back (review wave 2, M1)
		dev.nezo.burmaldaholic.core.economy.BankrollReferences.add((server, id) -> fundedBy(id)
			|| BotLedgerData.get(server).escrows().stream().anyMatch(e -> e.bankrollId().equals(id)));
		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			long back = BotLedger.returnOrphans(server, null);
			if (back > 0) {
				Burmaldaholic.LOGGER.info("Returned {} chips held by bots before a crash to their bankrolls", back);
			}
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			LIVE.clear();
			external = 0;
		});
	}

	/** New bot random stream; {@code debug.fixedSeed ≠ 0} → deterministic ({@code seed ^ 0xB07}). */
	public static BotRng newRng() {
		long seed = CasinoConfig.debug().fixedSeed;
		return seed != 0 ? BotRng.seeded(seed ^ 0xB07L) : BotRng.fresh();
	}

	public static boolean enabled() {
		return CasinoConfig.bots().enabled;
	}

	/** Bots seated world-wide right now (tables + external). */
	public static int active() {
		prune();
		int n = external;
		for (TableBots t : LIVE.values()) {
			n += t.bots().size();
		}
		return n;
	}

	/** Bots that may still sit world-wide ({@code bots.maxActive} − active). */
	public static int activeBudgetLeft() {
		return Math.max(0, CasinoConfig.bots().maxActive - active());
	}

	/** Tables with bots right now. */
	public static int activeTables() {
		prune();
		return LIVE.size();
	}

	/** May one more table get bots ({@code bots.maxActiveTables})? */
	public static boolean tableSlotAvailable() {
		return activeTables() < CasinoConfig.bots().maxActiveTables;
	}

	/**
	 * Seats {@code n} bots outside tables (PvP engine, tournaments) within the world budget; returns how
	 * many were granted. Give them back with {@link #release}.
	 */
	public static int acquire(int n) {
		int granted = Math.max(0, Math.min(n, activeBudgetLeft()));
		external += granted;
		return granted;
	}

	public static void release(int n) {
		external = Math.max(0, external - Math.max(0, n));
	}

	/** Tables with bots (admin list {@code /casino bots list}). */
	public static List<TableBots> tables() {
		prune();
		return List.copyOf(LIVE.values());
	}

	/** {@code /casino bots clear all}: every table's bots leave at its next safe point. */
	public static void clearAll() {
		for (TableBots t : tables()) {
			t.clear();
		}
	}

	/** Chips bankroll-funded bots of {@code bankrollId} hold right now (charter "Bot stacks out", §5.1). */
	public static long stacksOut(String bankrollId) {
		long sum = 0;
		for (TableBots t : tables()) {
			for (TableBots.SeatedBot b : t.bots()) {
				if (b.purse.kind() == Purse.Kind.BANKROLL && b.purse.bankrollId().equals(bankrollId)) {
					sum += b.stack + b.bankEscrow;
				}
			}
		}
		return sum;
	}

	/** Some seated bot is funded by bankroll {@code bankrollId}. */
	public static boolean fundedBy(String bankrollId) {
		for (TableBots t : tables()) {
			for (TableBots.SeatedBot b : t.bots()) {
				if (b.purse.kind() == Purse.Kind.BANKROLL && b.purse.bankrollId().equals(bankrollId)) {
					return true;
				}
			}
		}
		return false;
	}

	/** Returns crash leftovers of every table now (also run on SERVER_STARTED); for ops / tests. */
	public static long returnOrphans(MinecraftServer server) {
		return BotLedger.returnOrphans(server, null);
	}

	static void track(TableBots table) {
		if (table.bots().isEmpty()) {
			LIVE.remove(table.key(), table);
		} else {
			LIVE.put(table.key(), table);
		}
	}

	static void untrack(TableBots table) {
		LIVE.remove(table.key(), table);
	}

	private static void prune() {
		for (TableBots t : new ArrayList<>(LIVE.values())) {
			if (!t.tableActive()) {
				LIVE.remove(t.key(), t);
			}
		}
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
