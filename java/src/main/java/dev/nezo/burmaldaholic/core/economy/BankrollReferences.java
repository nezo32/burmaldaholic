package dev.nezo.burmaldaholic.core.economy;

import dev.nezo.burmaldaholic.Burmaldaholic;
import dev.nezo.burmaldaholic.core.data.CasinoWorldData;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiPredicate;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;

/**
 * Who may still send chips to a bankroll id after its charter closed (review wave 2, M1 follow-up):
 * bots funded by it (their stacks come back when they leave), PvP matches anchored at its machines or with
 * bots from it (refunds, payouts, the rake share), open rounds. A closed bankroll's tombstone is pruned when
 * no registered check refers to it any more and {@link #GRACE_TICKS} passed since its close / last late
 * credit (rounds in flight that no check tracks). Server thread.
 */
public final class BankrollReferences {
	/** Minimum age of an unreferenced tombstone before it is dropped (5 minutes). */
	public static final long GRACE_TICKS = 6000;
	/** How often tombstones are checked. */
	static final int PERIOD_TICKS = 1200;

	private static final List<BiPredicate<MinecraftServer, String>> CHECKS = new CopyOnWriteArrayList<>();

	private BankrollReferences() {}

	/** Registers a check: {@code (server, bankrollId) -> true} while something may still credit that id. */
	public static void add(BiPredicate<MinecraftServer, String> check) {
		CHECKS.add(check);
	}

	/** True while any registered check refers to {@code bankrollId}. A failing check counts as a reference. */
	public static boolean referenced(MinecraftServer server, String bankrollId) {
		for (BiPredicate<MinecraftServer, String> c : CHECKS) {
			try {
				if (c.test(server, bankrollId)) {
					return true;
				}
			} catch (RuntimeException e) {
				Burmaldaholic.LOGGER.error("Bankroll reference check failed for {}", bankrollId, e);
				return true;
			}
		}
		return false;
	}

	/** Drops the tombstones nothing refers to any more; returns the ids removed. */
	public static List<String> prune(MinecraftServer server) {
		CasinoWorldData data = CasinoWorldData.get(server);
		Ledger ledger = data.ledger();
		if (ledger.tombstones().isEmpty()) {
			return List.of();
		}
		long now = server.overworld().getGameTime();
		List<String> removed = ledger.pruneTombstones(now, GRACE_TICKS, id -> referenced(server, id));
		if (!removed.isEmpty()) {
			data.setDirty();
			Burmaldaholic.LOGGER.info("Pruned {} closed-bankroll tombstone(s): {}", removed.size(), removed);
		}
		return removed;
	}

	/** Core only: periodic pruning. */
	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (server.getTickCount() % PERIOD_TICKS == 0) {
				prune(server);
			}
		});
	}
}
