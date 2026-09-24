package dev.nezo.burmaldaholic.core.wager;

import dev.nezo.burmaldaholic.Burmaldaholic;
import dev.nezo.burmaldaholic.core.mode.CasinoMode;
import dev.nezo.burmaldaholic.core.service.CoreServices;
import dev.nezo.burmaldaholic.core.service.TableOwnershipProvider.OwnedTable;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.Nullable;

/**
 * Central wager gate: every NEW stake of any game passes {@link #check} (called by
 * {@link BetLimits#validate}, the table {@code placeBet}, {@link Stakes} pawn stakes and PvP entries).
 *
 * <p>Built-in rules: casino mode on; at an owned table (§18.2) the owner cannot play and a closed /
 * insolvent casino refuses bets. Modules add their own with {@link #addVeto} (loan: Asset Freeze §5.6).
 */
public final class Wagers {
	private static final List<WagerVeto> VETOES = new CopyOnWriteArrayList<>();

	private Wagers() {}

	/** Adds a veto (module {@code register}). */
	public static void addVeto(WagerVeto veto) {
		VETOES.add(veto);
	}

	/** @return null if the player may place this stake, else the translated reason. */
	public static @Nullable Component check(ServerPlayer player, WagerVeto.Context ctx) {
		if (!CasinoMode.isEnabled(player)) {
			return Component.translatable("gui.burmaldaholic.error.casino_off");
		}
		if (ctx.table() != null) {
			Optional<OwnedTable> owned = CoreServices.tableOwnership().owner(player.level(), ctx.table());
			if (owned.isPresent()) {
				if (owned.get().owner().equals(player.getUUID())) {
					return Component.translatable("gui.burmaldaholic.error.owner_cannot_play");
				}
				if (!owned.get().open()) {
					return Component.translatable("gui.burmaldaholic.error.table_closed");
				}
			}
		}
		for (WagerVeto v : VETOES) {
			try {
				Component err = v.check(player, ctx);
				if (err != null) {
					return err;
				}
			} catch (RuntimeException e) {
				Burmaldaholic.LOGGER.error("Wager veto failed", e);
			}
		}
		return null;
	}
}
