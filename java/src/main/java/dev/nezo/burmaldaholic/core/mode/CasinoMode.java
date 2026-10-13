package dev.nezo.burmaldaholic.core.mode;

import dev.nezo.burmaldaholic.Burmaldaholic;
import dev.nezo.burmaldaholic.core.mixin.MinecraftServerStorageAccessor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BooleanSupplier;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gamerules.GameRuleMap;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.level.storage.SavedDataStorage;
import org.jspecify.annotations.Nullable;

/**
 * Casino mode is a per-world switch saved with the world in {@code data/burmaldaholic/mode.dat}
 * ({@link CasinoModeData}, default ON since v0.1.2, docs/design/GAME_DESIGN.md §2.1). The player can opt
 * out with the "Casino Mode" button directly below "Difficulty" on the Create World "Game" tab (client mixins
 * {@code CreateWorldGameTabMixin} / {@code CreateWorldScreenMixin}); operators switch it later with
 * {@code /casino mode on|off|status}. It is not a game rule, and config never overrides it. Vanilla
 * difficulty and hardcore are untouched.
 *
 * <p><b>Every feature must check {@link #isEnabled} before doing anything gameplay-related</b>
 * (opening tables, spawning collectors, chaos events, worldgen structures...). React to switches with
 * {@link #onChange}.
 */
public final class CasinoMode {
	/** ON since v0.1.2 (owner decision: casinos must generate in worlds nobody configured). */
	public static final boolean DEFAULT = true;

	/** How {@link #bootstrap(SavedDataStorage, Path, Boolean)} resolved this world's value. */
	public enum Boot {
		/** New world from the Create World screen: the button value. */
		NEW_WORLD,
		/** mode.dat with an explicit value: authoritative. */
		STORED,
		/** mode.dat without a value (v0.1.1 wrote OFF as "no value"): upgraded to the default, ON. */
		LEGACY_UPGRADED,
		/** No (readable) mode.dat: dedicated server, other launcher, pre-mode world. The default, ON. */
		MISSING
	}

	private static volatile Boot lastBoot = Boot.STORED;

	/** Called on the server thread after the stored value actually changed. */
	@FunctionalInterface
	public interface ChangeListener {
		void onChange(MinecraftServer server, boolean enabled);
	}

	private static final List<ChangeListener> LISTENERS = new CopyOnWriteArrayList<>();

	/** Set by the client entrypoint; answers for client-side levels (synced from the server). */
	private static BooleanSupplier clientLookup = () -> false;

	private CasinoMode() {}

	/** Core only: initializes the stored value when a server starts (before levels load or anyone joins). */
	public static void register() {
		ServerLifecycleEvents.SERVER_STARTING.register(CasinoMode::bootstrap);
	}

	public static void onChange(ChangeListener listener) {
		LISTENERS.add(listener);
	}

	public static boolean isEnabled(MinecraftServer server) {
		return server != null && CasinoModeData.get(server).enabled();
	}

	public static boolean isEnabled(Level level) {
		if (level instanceof ServerLevel serverLevel) {
			return isEnabled(serverLevel.getServer());
		}
		return level != null && clientLookup.getAsBoolean();
	}

	public static boolean isEnabled(Player player) {
		return player != null && isEnabled(player.level());
	}

	/**
	 * Stores the value for this world (server thread) and fires the change listeners if it changed.
	 * Always marks the data dirty, so {@code mode.dat} exists after the next save.
	 */
	public static void set(MinecraftServer server, boolean enabled) {
		CasinoModeData data = CasinoModeData.get(server);
		boolean before = data.enabled();
		data.setEnabled(enabled);
		if (before != enabled) {
			for (ChangeListener listener : LISTENERS) {
				listener.onChange(server, enabled);
			}
		}
	}

	public static void setClientLookup(BooleanSupplier lookup) {
		clientLookup = lookup;
	}

	/** How the running server's world resolved its value on start (for the operator notice on join). */
	public static Boot lastBoot() {
		return lastBoot;
	}

	/** SERVER_STARTING. */
	static void bootstrap(MinecraftServer server) {
		// new world from the Create World screen: the button value rides on this world's storage access
		Boolean pending = ((PendingCasinoMode) ((MinecraftServerStorageAccessor) server).burmaldaholic$storageSource())
			.burmaldaholic$takePendingCasinoMode();
		lastBoot = bootstrap(server.getDataStorage(), server.getWorldPath(LevelResource.DATA), pending);
	}

	/**
	 * Resolves and stores the casino mode of the world whose saved data is {@code storage} (public for the
	 * GameTests, which run it against a fresh storage). No change listeners fire: nobody is online yet.
	 * Whenever the value was not read from an explicit key, the file is saved right away.
	 */
	public static Boot bootstrap(SavedDataStorage storage, Path dataDir, @Nullable Boolean pending) {
		// 1. new world from the Create World screen
		if (pending != null) {
			storage.computeIfAbsent(CasinoModeData.TYPE).setEnabled(pending);
			// persist now: a crash before the first autosave must not lose the choice
			storage.scheduleSave();
			Burmaldaholic.LOGGER.info("Casino mode {} for the new world", pending ? "ON" : "OFF");
			return Boot.NEW_WORLD;
		}
		CasinoModeData stored = storage.get(CasinoModeData.TYPE);
		// 2. existing world with an explicit value: it is authoritative (an explicit OFF stays OFF)
		if (stored != null && !stored.legacy()) {
			return Boot.STORED;
		}
		// 3. mode.dat without a value: never set, or v0.1.1's OFF (it did not write the old default).
		//    Upgraded to the new default and written explicitly, so this runs once per world.
		if (stored != null) {
			stored.setEnabled(DEFAULT);
			storage.scheduleSave();
			Burmaldaholic.LOGGER.info("Casino mode is now ON by default; this world had no explicit choice (operators: /casino mode off)");
			return Boot.LEGACY_UPGRADED;
		}
		// 4. no (readable) mode.dat: dedicated server, other launcher, or a development world from the
		//    game-rule era. The default (ON), written once so this runs once per world.
		if (Files.exists(dataDir.resolve("burmaldaholic/mode.dat"))) {
			// vanilla already logged the read error; the file is replaced on the next save
			Burmaldaholic.LOGGER.warn("Unreadable burmaldaholic/mode.dat; casino mode reset to the default, ON (operators: /casino mode off)");
		}
		storage.computeIfAbsent(CasinoModeData.TYPE).setEnabled(DEFAULT);
		// A development world may still carry the removed "burmaldaholic:casino_mode" game rule in
		// game_rules.dat (vanilla skips unknown rules with a log line). Rewrite that file once without it.
		GameRuleMap rules = storage.get(GameRuleMap.TYPE);
		if (rules != null) {
			rules.setDirty();
		}
		storage.scheduleSave();
		return Boot.MISSING;
	}
}
