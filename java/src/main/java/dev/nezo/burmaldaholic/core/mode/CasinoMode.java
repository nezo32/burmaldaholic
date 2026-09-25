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

/**
 * Casino mode is a per-world switch saved with the world in {@code data/burmaldaholic/mode.dat}
 * ({@link CasinoModeData}, default OFF, docs/design/GAME_DESIGN.md §2.1). The player opts in with the
 * "Casino Mode" button directly below "Difficulty" on the Create World "Game" tab (client mixins
 * {@code CreateWorldGameTabMixin} / {@code CreateWorldScreenMixin}); operators switch it later with
 * {@code /casino mode on|off|status}. It is not a game rule, and config never overrides it. Vanilla
 * difficulty and hardcore are untouched.
 *
 * <p><b>Every feature must check {@link #isEnabled} before doing anything gameplay-related</b>
 * (opening tables, spawning collectors, chaos events, worldgen structures...). React to switches with
 * {@link #onChange}.
 */
public final class CasinoMode {
	public static final boolean DEFAULT = false;

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

	/** SERVER_STARTING. */
	static void bootstrap(MinecraftServer server) {
		// 1. new world from the Create World screen: the button value rides on this world's storage access
		Boolean pending = ((PendingCasinoMode) ((MinecraftServerStorageAccessor) server).burmaldaholic$storageSource())
			.burmaldaholic$takePendingCasinoMode();
		if (pending != null) {
			CasinoModeData.get(server).setEnabled(pending); // no listeners: nobody is online yet
			// persist now: a crash before the first autosave must not lose the choice
			server.getDataStorage().scheduleSave();
			Burmaldaholic.LOGGER.info("Casino mode {} for the new world", pending ? "ON" : "OFF");
			return;
		}
		// 2. existing world with mode.dat: it is authoritative
		if (server.getDataStorage().get(CasinoModeData.TYPE) != null) {
			return;
		}
		// 3. no (readable) mode.dat: dedicated server, other launcher, or a development world from the
		//    game-rule era. Default OFF, written once so this runs once per world.
		Path dataDir = server.getWorldPath(LevelResource.DATA);
		if (Files.exists(dataDir.resolve("burmaldaholic/mode.dat"))) {
			// vanilla already logged the read error; the file is replaced on the next save
			Burmaldaholic.LOGGER.warn("Unreadable burmaldaholic/mode.dat; casino mode reset to OFF (operators: /casino mode on)");
		}
		CasinoModeData.get(server).setEnabled(DEFAULT);
		// A development world may still carry the removed "burmaldaholic:casino_mode" game rule in
		// game_rules.dat (vanilla skips unknown rules with a log line). Rewrite that file once without it.
		GameRuleMap rules = server.getDataStorage().get(GameRuleMap.TYPE);
		if (rules != null) {
			rules.setDirty();
		}
		server.getDataStorage().scheduleSave();
	}
}
