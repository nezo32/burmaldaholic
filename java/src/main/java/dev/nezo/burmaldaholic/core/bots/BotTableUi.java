package dev.nezo.burmaldaholic.core.bots;

import dev.nezo.burmaldaholic.core.bots.logic.BotProfile;
import java.util.Objects;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import org.jspecify.annotations.Nullable;

/**
 * Optional hooks of the bots UI module (J-B2: table settings screen, seat plates / nameplates, header,
 * quip delivery) that games call at their tables. Every member has a no-op default and the registered
 * instance defaults to {@link #NONE}, so a game works unchanged while the bots module is absent or older.
 * The bots module registers its implementation once with {@link #set}. Server thread.
 */
public interface BotTableUi {
	BotTableUi NONE = new BotTableUi() {};

	/** A table's bot session started (first safe point with bots / humans): nameplates, screen buttons. */
	default void attach(BotTable table, TableBots bots) {}

	/** The session ended (last human left, table removed). */
	default void detach(BotTable table, TableBots bots) {}

	/** Header line for the table screen; null = the game's fallback ({@link TableBots#summary}). */
	default @Nullable Component header(BotTable table, TableBots bots, ServerLevel level) {
		return null;
	}

	/**
	 * Delivers a quip through the UI (per-player mute, tin voice). Return false to let the game use the
	 * core chatter queue ({@link TableBots#say}).
	 */
	default boolean quip(BotTable table, TableBots bots, ServerLevel level, BotProfile bot, String event, @Nullable String human) {
		return false;
	}

	/** The registered UI (never null). */
	static BotTableUi get() {
		return Holder.ui;
	}

	/** Bots module only. */
	static void set(BotTableUi ui) {
		Holder.ui = Objects.requireNonNull(ui);
	}

	/** Holder of the registered instance (interfaces cannot have mutable static fields). */
	final class Holder {
		private static BotTableUi ui = NONE;

		private Holder() {}
	}
}
