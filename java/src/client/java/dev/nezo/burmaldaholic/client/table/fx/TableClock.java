package dev.nezo.burmaldaholic.client.table.fx;

import dev.nezo.burmaldaholic.client.anim.SmoothClock;

/**
 * A table screen's view of the ONE shared clock (SY-SPEC §1): a facade over {@link SmoothClock}. {@link #sync} only
 * OBSERVES a state packet's {@code game_time} (it no longer resets an offset per packet, which made the clock jump on
 * every bunched or late packet); {@link #nowTicks} is the smoothed server time. There is one clock per level, so every
 * screen and the in-world renderer agree.
 */
public final class TableClock {
	private boolean synced;

	/** The server's game time as carried by a state packet: one observation for the shared clock. */
	public void sync(long serverGameTime) {
		if (serverGameTime <= 0) {
			return;
		}
		SmoothClock.observe(serverGameTime);
		synced = true;
	}

	public boolean synced() {
		return synced;
	}

	/** Estimated server time in ticks ({@link SmoothClock#nowMs} / 50; a test freeze wins when set). */
	public double nowTicks(float partialTick) {
		return SmoothClock.nowMs(partialTick) / 50.0;
	}

	/** Milliseconds since server tick {@code tick}. */
	public double msSince(long tick, float partialTick) {
		return (nowTicks(partialTick) - tick) * 50.0;
	}
}
