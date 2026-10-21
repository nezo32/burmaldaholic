package dev.nezo.burmaldaholic.gametest.core;

import dev.nezo.burmaldaholic.client.anim.NetLagHook;
import dev.nezo.burmaldaholic.client.anim.SmoothClock;
import java.util.ArrayList;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Random;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

/**
 * Latency injection for client GameTests (SY-SPEC §5): installs {@link NetLagHook}, so table state packets
 * ({@code ClientTableCache}) and the server's time packets ({@code SetTimeNetLagMixin}) are delivered late: a fixed
 * delay, ± jitter (which may reorder them, exercising the seq gate) and, optionally, held back and released in bunches
 * every {@code bunchEveryMs}. Released on the client thread at END_CLIENT_TICK. Delays run on {@link SmoothClock#wallMs}
 * (live nano time, or a test's {@code SmoothClock.Test.source}).
 *
 * <pre>{@code
 * NetLag.install(300, 150, 1000);   // 300 ms delay, ±150 ms jitter, bunched every second
 * NetLag.jumpTime(1000);            // the server's time jumps +1 s
 * NetLag.clear();                   // delivers whatever is pending and uninstalls
 * }</pre>
 */
public final class NetLag {
	private record Item(double dueMs, long order, Runnable deliver) {}

	private static final PriorityQueue<Item> QUEUE = new PriorityQueue<>((a, b) -> a.dueMs != b.dueMs
			? Double.compare(a.dueMs, b.dueMs) : Long.compare(a.order, b.order));
	private static final long SEED = 20261020L;
	private static final Random RANDOM = new Random(SEED);
	private static volatile double delayMs;
	private static volatile double jitterMs;
	private static volatile double bunchEveryMs;
	private static long order;
	private static boolean registered;
	/** Packets delayed since {@link #install} (tests assert the hook was really in the path). */
	private static int deferred;

	private NetLag() {}

	/** Installs the delay (call on the client thread). */
	public static synchronized void install(double delayMs, double jitterMs, double bunchEveryMs) {
		NetLag.delayMs = delayMs;
		NetLag.jitterMs = jitterMs;
		NetLag.bunchEveryMs = bunchEveryMs;
		deferred = 0;
		if (!registered) {
			registered = true;
			ClientTickEvents.END_CLIENT_TICK.register(mc -> pump(false));
		}
		NetLagHook.hook = NetLag::defer;
	}

	/**
	 * Uninstalls the hook, drops whatever is pending and reseeds the jitter: a test class starts from the same NetLag state
	 * whether it runs alone or after other classes that used it (call on the client thread).
	 */
	public static synchronized void reset() {
		NetLagHook.hook = null;
		QUEUE.clear();
		RANDOM.setSeed(SEED);
		order = 0;
		deferred = 0;
	}

	/** Simulates a server time jump of {@code ms} (every later time observation is shifted). */
	public static void jumpTime(double ms) {
		SmoothClock.Test.jump(ms);
	}

	/** Uninstalls the hook and delivers everything pending now (call on the client thread). */
	public static void clear() {
		NetLagHook.hook = null;
		pump(true);
	}

	public static synchronized int deferredCount() {
		return deferred;
	}

	private static synchronized void defer(Runnable deliver) {
		double now = SmoothClock.wallMs(); // SYW: the clock's wall (a tick-locked test source drives the delays too)
		double due = now + Math.max(0, delayMs + (RANDOM.nextDouble() * 2 - 1) * jitterMs);
		if (bunchEveryMs > 0) due = Math.ceil(due / bunchEveryMs) * bunchEveryMs;
		QUEUE.add(new Item(due, order++, deliver));
		deferred++;
	}

	private static void pump(boolean all) {
		double now = SmoothClock.wallMs(); // SYW: the clock's wall (a tick-locked test source drives the delays too)
		List<Runnable> due = new ArrayList<>();
		synchronized (NetLag.class) {
			while (!QUEUE.isEmpty() && (all || QUEUE.peek().dueMs <= now)) due.add(QUEUE.poll().deliver);
		}
		due.forEach(Runnable::run);
	}
}
