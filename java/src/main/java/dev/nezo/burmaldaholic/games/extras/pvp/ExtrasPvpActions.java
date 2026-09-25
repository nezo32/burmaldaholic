package dev.nezo.burmaldaholic.games.extras.pvp;

import dev.nezo.burmaldaholic.core.pvp.Pvp;
import dev.nezo.burmaldaholic.core.util.Result;
import dev.nezo.burmaldaholic.games.extras.server.ExtrasGames;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;

/**
 * In-match buttons of the extras PvP mode screens (Plinko Battle, Scratch Showdown): {@code extras_action}
 * with game {@code pvp}, actions {@code press} (Drop! / Scratch!: only speeds the timeline up), {@code taunt}
 * (arg {@code line} 0–7) and {@code leave}. Everything is validated by the engine (the player must be a
 * participant of a running match); args are untrusted.
 */
public final class ExtrasPvpActions {
	public static final String GAME = "pvp";

	private ExtrasPvpActions() {}

	public static void action(ServerPlayer player, String action, CompoundTag args) {
		switch (action) {
			case "press" -> Pvp.service().press(player);
			case "taunt" -> {
				int line = args.getIntOr("line", -1);
				if (line < 0 || line > 7) {
					return;
				}
				Result<Void> r = Pvp.service().taunt(player, line);
				if (!r.isOk() && r.error() != null) {
					ExtrasGames.sendError(player, r.error());
				}
			}
			case "leave" -> Pvp.service().leave(player);
			default -> {
			}
		}
	}
}
