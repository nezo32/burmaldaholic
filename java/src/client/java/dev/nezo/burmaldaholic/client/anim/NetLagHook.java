package dev.nezo.burmaldaholic.client.anim;

/**
 * Latency-injection hook for client GameTests (SY-SPEC §5, gametest {@code core.NetLag}): NULL in production. When set,
 * {@code ClientTableCache} hands every table state packet, and the time-packet mixin every
 * {@code ClientboundSetTimePacket}, to {@link Delay#defer}, which runs the delivery later (delayed, jittered, bunched).
 */
public final class NetLagHook {
	/** Takes a packet delivery and runs it later (on the client thread). */
	@FunctionalInterface
	public interface Delay {
		void defer(Runnable deliver);
	}

	/** The installed delay, or null (production). */
	public static volatile Delay hook;

	/** True on the thread that is replaying a deferred packet (so the mixin lets it through). */
	private static final ThreadLocal<Boolean> REPLAYING = ThreadLocal.withInitial(() -> false);

	private NetLagHook() {}

	/** Defers {@code deliver} when a hook is installed and this is not a replay; returns true when it was deferred. */
	public static boolean intercept(Runnable deliver) {
		Delay d = hook;
		if (d == null || REPLAYING.get()) return false;
		d.defer(() -> {
			REPLAYING.set(true);
			try {
				deliver.run();
			} finally {
				REPLAYING.set(false);
			}
		});
		return true;
	}
}
