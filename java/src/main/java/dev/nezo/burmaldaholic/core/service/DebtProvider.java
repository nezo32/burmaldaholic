package dev.nezo.burmaldaholic.core.service;

import java.util.UUID;
import net.minecraft.server.MinecraftServer;

/**
 * Outstanding loan debt of a player (loan module implements it; default: no debt). Core uses it for
 * the Cashier ({@code withdrawable = max(0, balance − debt)}, no withdrawals in default, §3.2) and the HUD.
 */
public interface DebtProvider {
	DebtProvider NONE = new DebtProvider() {
		@Override
		public long owed(MinecraftServer server, UUID player) {
			return 0;
		}

		@Override
		public boolean inDefault(MinecraftServer server, UUID player) {
			return false;
		}
	};

	long owed(MinecraftServer server, UUID player);

	boolean inDefault(MinecraftServer server, UUID player);

	/** World ticks until the deadline (for the HUD); negative/0 if none. */
	default long ticksToDeadline(MinecraftServer server, UUID player) {
		return 0;
	}

	/** Principal of the current loan (the debt meter's scale, extras.md §8.4); 0 = none / unknown. */
	default long principal(MinecraftServer server, UUID player) {
		return 0;
	}
}
