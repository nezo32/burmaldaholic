package dev.nezo.burmaldaholic.core.bots;

import dev.nezo.burmaldaholic.Burmaldaholic;
import dev.nezo.burmaldaholic.core.bots.logic.BotWork;
import dev.nezo.burmaldaholic.core.bots.logic.JobQueue;
import dev.nezo.burmaldaholic.core.config.CasinoConfig;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

/**
 * Heavy bot work scheduler (BOTS.md §7.5): a FIFO ({@link JobQueue}); at most {@code bots.maxConcurrentJobs}
 * jobs step per tick, sharing a budget of units per tick (Java: 2 000 evaluations, edition constant
 * {@link #UNITS_PER_TICK}). A job that reaches its deadline is finished with its partial result, even
 * if it waited for a slot the whole time; stale jobs are dropped silently; a failing job calls back with
 * null (the policy's fallback). Server thread; cleared on server stop. The Bedrock twin is
 * {@code core/bots/jobs.ts} ({@code system.runJob}).
 */
public final class BotJobs {
	public static final int UNITS_PER_TICK = 2000;

	private static final JobQueue QUEUE = new JobQueue(e -> Burmaldaholic.LOGGER.error("Bot job failed", e));
	private static long tick;

	private BotJobs() {}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(server -> step());
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> QUEUE.clear());
	}

	/**
	 * Runs {@code work} over the coming ticks; calls {@code onDone(result)} on the server thread when it is
	 * done or at {@code deadlineTicks} from now, unless {@code stillWanted} turned false (state changed:
	 * the decision is stale and dropped — the poker {@code botSeq} pattern).
	 */
	public static void submit(BotWork work, int deadlineTicks, BooleanSupplier stillWanted, Consumer<Object> onDone) {
		QUEUE.submit(work, tick + Math.max(0, deadlineTicks), stillWanted, onDone);
	}

	/** Jobs waiting or running (admin list, tests). */
	public static int pending() {
		return QUEUE.size();
	}

	static void step() {
		tick++;
		if (QUEUE.size() > 0) {
			QUEUE.step(tick, Math.max(1, CasinoConfig.bots().maxConcurrentJobs), UNITS_PER_TICK);
		}
	}
}
