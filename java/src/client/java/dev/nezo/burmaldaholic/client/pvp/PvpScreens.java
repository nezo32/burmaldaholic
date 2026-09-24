package dev.nezo.burmaldaholic.client.pvp;

import com.google.gson.JsonObject;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import net.minecraft.client.gui.screens.Screen;

/**
 * Client registry of per-mode PvP screens (PVP.md §4.5 … §8.5). Mode-owning client modules register
 * their screen factory (extras: coin / wheel / plinko / scratch; slots: slots); the pvp client module
 * opens / updates them from {@code PvpMatchSyncPayload} (match public state + revealed steps only).
 * Core client package, so feature modules need not import each other.
 */
public final class PvpScreens {
	/** A mode screen: created from the first sync, updated by later ones. */
	public interface ModeScreen {
		Screen screen();

		void update(JsonObject state);
	}

	private static final Map<String, Function<JsonObject, ModeScreen>> FACTORIES = new HashMap<>();

	private PvpScreens() {}

	public static void register(String modeId, Function<JsonObject, ModeScreen> factory) {
		if (FACTORIES.putIfAbsent(modeId, factory) != null) {
			throw new IllegalStateException("PvP screen for '" + modeId + "' registered twice");
		}
	}

	public static Function<JsonObject, ModeScreen> factory(String modeId) {
		return FACTORIES.get(modeId);
	}
}
