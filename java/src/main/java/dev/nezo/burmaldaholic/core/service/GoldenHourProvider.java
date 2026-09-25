package dev.nezo.burmaldaholic.core.service;

import net.minecraft.server.MinecraftServer;

/** Golden Hour state (chaos module implements it; default: never active). Core shows it on the HUD. */
@FunctionalInterface
public interface GoldenHourProvider {
	GoldenHourProvider NONE = server -> 0;

	/** Remaining world ticks of the current Golden Hour, 0 if not active. */
	long remainingTicks(MinecraftServer server);
}
