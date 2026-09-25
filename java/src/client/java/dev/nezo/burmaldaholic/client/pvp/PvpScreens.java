package dev.nezo.burmaldaholic.client.pvp;

import com.google.gson.JsonObject;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import net.minecraft.client.gui.screens.Screen;
import org.jspecify.annotations.Nullable;

/**
 * Client registry of per-mode PvP screens (PVP.md §4.5 … §8.5). Mode-owning client modules register
 * their screen factory (extras: coin / wheel / plinko / scratch; slots: slots); the pvp client module
 * opens / updates them from {@code PvpSyncPayload} (match public state + revealed steps only).
 * Core client package, so feature modules need not import each other.
 *
 * <p>The state object (built by the pvp module's {@code PvpMatchView}): {@code id, mode, state, you, host, spectator,
 * pot, rake, rakePercent, entry, min, max, equalStakes, grudge, link, chainOf, policy, botsToFill, ticksLeft, balance,
 * anchorKind, anchorName, params, participants[{index, key, name, bot, tagKey, stake, allIn, pressed, rematch, host,
 * you, wins?, losses?}], steps[{kind, ticks, round, waitForAll, data}], final, result?{order, points, winners,
 * payouts, places}}}. Buttons of a mode screen call {@link #action} (e.g. {@code press}, {@code decide},
 * {@code top_up}, {@code rematch}) and {@link #openTaunts}; the pvp module's result window follows the match.
 */
public final class PvpScreens {
	/** A mode screen: created from the first sync, updated by later ones. */
	public interface ModeScreen {
		Screen screen();

		void update(JsonObject state);
	}

	/** Installed by the pvp client module: sends a {@code PvpActionPayload} / opens the taunt picker. */
	public interface Actions {
		void send(String action, String matchId, String arg, long value);

		void openTaunts(Screen parent);
	}

	private static final Map<String, Function<JsonObject, ModeScreen>> FACTORIES = new HashMap<>();
	private static Actions actions = new Actions() {
		@Override
		public void send(String action, String matchId, String arg, long value) {}

		@Override
		public void openTaunts(Screen parent) {}
	};

	private PvpScreens() {}

	public static void register(String modeId, Function<JsonObject, ModeScreen> factory) {
		if (FACTORIES.putIfAbsent(modeId, factory) != null) {
			throw new IllegalStateException("PvP screen for '" + modeId + "' registered twice");
		}
	}

	public static @Nullable Function<JsonObject, ModeScreen> factory(String modeId) {
		return FACTORIES.get(modeId);
	}

	/** pvp client module only. */
	public static void setActions(Actions a) {
		actions = a;
	}

	/**
	 * Sends a PvP action to the server: {@code press}, {@code decide} (arg = decision id, value = option),
	 * {@code taunt} (value = line 0…7), {@code rematch}, {@code leave}, {@code start}, {@code fill_bots},
	 * {@code join} / {@code top_up} (value = chips), {@code accept} / {@code decline} / {@code withdraw}, {@code show}.
	 */
	public static void action(String action, String matchId, String arg, long value) {
		actions.send(action, matchId == null ? "" : matchId, arg == null ? "" : arg, value);
	}

	/** Opens the 8-line taunt picker over {@code parent} (returns to it). */
	public static void openTaunts(Screen parent) {
		actions.openTaunts(parent);
	}
}
