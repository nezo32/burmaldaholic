package dev.nezo.burmaldaholic.games.poker;

import dev.nezo.burmaldaholic.core.bots.BotTable;
import dev.nezo.burmaldaholic.core.bots.TableBots;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * The parts of the bots UI module (task J-B2: table settings screen, hub buttons, header, avatars,
 * advancements) the poker table calls. Optional: until the bots module installs an implementation with
 * {@link #set}, every call is a no-op, so poker works without it. The table itself implements
 * {@link BotTable}, so the UI reaches its {@link TableBots} through {@code PokerTableBlockEntity#tableBots()}.
 * Implementations must not throw (poker logs and ignores failures).
 */
public interface PokerBotsUi {
	PokerBotsUi NONE = new PokerBotsUi() {};

	/** A poker table created its Seats &amp; Bots state (first use after load / placement). Default: core's {@code BotTableUi}. */
	default void attach(BotTable table, TableBots bots) {
		dev.nezo.burmaldaholic.core.bots.BotTableUi.get().attach(table, bots);
	}

	/**
	 * A hand was settled at the table; {@code humans} = humans dealt into it. The bots module grants its
	 * advancements ({@code members_only}, {@code no_robots}) from the hand's play results.
	 */
	default void handPlayed(BotTable table, TableBots bots, List<UUID> humans) {}

	/** The table's session ended (last human left, table stopped): nameplates / avatars can go. */
	default void sessionEnded(BotTable table) {
		TableBots bots = table.tableBots();
		if (bots != null) {
			dev.nezo.burmaldaholic.core.bots.BotTableUi.get().detach(table, bots);
		}
	}

	final class Holder {
		private static PokerBotsUi ui = NONE;

		private Holder() {}
	}

	/** Installed by the bots module. */
	static void set(PokerBotsUi ui) {
		Holder.ui = Objects.requireNonNullElse(ui, NONE);
	}

	static PokerBotsUi get() {
		return Holder.ui;
	}
}
