package dev.nezo.burmaldaholic.core.service;

import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/**
 * Keeps the charter's per-table "Bots" switch (multiplayer, {@link TableOwnershipProvider.OwnedTable#bots}) and
 * the table's Seats &amp; Bots owner mode ({@code OwnerControls.botsMode}, bots module) in step (BOTS.md §6.2):
 * the owner saving Bots = Off in the table settings turns the charter switch off, any other mode turns it on;
 * the multiplayer module does the reverse when the charter's switch changes. Server thread.
 */
public final class CharterBots {
	/** Sets the charter switch of the owned table at {@code pos}; false if it is not an owned table. */
	@FunctionalInterface
	public interface Switch {
		boolean set(ServerLevel level, BlockPos pos, boolean allowed);
	}

	private static Switch sw = (level, pos, allowed) -> false;

	private CharterBots() {}

	/** Multiplayer module only. */
	public static void install(Switch s) {
		sw = Objects.requireNonNull(s);
	}

	/** Bots module: the owner changed the table's bots mode ({@code allowed} = not Off). */
	public static boolean set(ServerLevel level, BlockPos pos, boolean allowed) {
		return sw.set(level, pos, allowed);
	}
}
