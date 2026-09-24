package dev.nezo.burmaldaholic.client.table.fx;

import net.minecraft.client.Minecraft;
import net.minecraft.util.Util;

/**
 * Shared clock of a table screen (docs/architecture/animation.md §3.2): server game time estimated from the last state
 * packet's {@code game_time} plus the client level's ticks since, so every viewer samples the spin / throw at the same
 * tick as the in-world renderer. Falls back to wall time when no level is loaded (previews, tests).
 */
public final class TableClock {
	private double offsetTicks;
	private boolean synced;
	private long wallAtSync;
	private long serverAtSync;

	/** The server's game time as carried by a state packet. */
	public void sync(long serverGameTime) {
		if (serverGameTime <= 0) {
			return;
		}
		Minecraft mc = Minecraft.getInstance();
		serverAtSync = serverGameTime;
		wallAtSync = Util.getMillis();
		if (mc.level != null) {
			offsetTicks = serverGameTime - mc.level.getGameTime();
		}
		synced = true;
	}

	public boolean synced() {
		return synced;
	}

	/** Estimated server time in ticks (with the partial tick). */
	public double nowTicks(float partialTick) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level != null) {
			return mc.level.getGameTime() + (double) partialTick + offsetTicks;
		}
		return serverAtSync + (Util.getMillis() - wallAtSync) / 50.0;
	}

	/** Milliseconds since server tick {@code tick}. */
	public double msSince(long tick, float partialTick) {
		return (nowTicks(partialTick) - tick) * 50.0;
	}
}
