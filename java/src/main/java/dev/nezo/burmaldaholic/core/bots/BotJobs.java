package dev.nezo.burmaldaholic.core.bots;

import dev.nezo.burmaldaholic.Burmaldaholic;
import dev.nezo.burmaldaholic.core.bots.logic.BotWork;
import dev.nezo.burmaldaholic.core.config.CasinoConfig;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

/**
 * Heavy bot work scheduler (BOTS.md §7.5): at most {@code bots.maxConcurrentJobs} jobs step per tick,
 * sharing a budget of units per tick (Java: 2 000 evaluations, edition constant {@link #UNITS_PER_TICK}).
 * A job that reaches its deadline (world tick) is finished with its partial result. The Bedrock twin is
 * {@code core/bots/jobs.ts} ({@code system.runJob}).
 */
public final class BotJobs {
	public static final int UNITS_PER_TICK = 2000;

	private record Job(BotWork work, long deadline, BooleanSupplier stillWanted, Consumer<Object> onDone) {}

	private static final Deque<Job> QUEUE = new ArrayDeque<>();
	private static long tick;

	private BotJobs() {}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(server -> step());
	}

	/**
	 * Runs {@code work} over the coming ticks; calls {@code onDone(result)} on the server thread when it is
	 * done or at {@code deadlineTicks} from now, unless {@code stillWanted} turned false (state changed:
	 * the decision is stale and dropped — the poker {@code botSeq} pattern).
	 */
	public static void submit(BotWork work, int deadlineTicks, BooleanSupplier stillWanted, Consumer<Object> onDone) {
		QUEUE.add(new Job(work, tick + Math.max(0, deadlineTicks), stillWanted, onDone));
	}

	static void step() {
		tick++;
		if (QUEUE.isEmpty()) {
			return;
		}
		int slots = Math.max(1, CasinoConfig.bots().maxConcurrentJobs);
		int budget = UNITS_PER_TICK;
		List<Job> active = new ArrayList<>();
		for (Job j : QUEUE) {
			if (active.size() >= slots) {
				break;
			}
			active.add(j);
		}
		for (Job j : active) {
			try {
				if (!j.stillWanted().getAsBoolean()) {
					QUEUE.remove(j);
					continue;
				}
				budget -= j.work().step(Math.max(1, budget / active.size()));
				if (j.work().done() || tick >= j.deadline()) {
					QUEUE.remove(j);
					j.onDone().accept(j.work().result());
				}
			} catch (RuntimeException e) {
				QUEUE.remove(j);
				Burmaldaholic.LOGGER.error("Bot job failed", e);
				j.onDone().accept(null); // policies treat null work as "fall back"
			}
		}
	}
}
