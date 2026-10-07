package dev.nezo.burmaldaholic.gametest.core;

import dev.nezo.burmaldaholic.core.mode.CasinoMode;
import dev.nezo.burmaldaholic.core.mode.CasinoModeData;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.level.storage.SavedDataStorage;

/**
 * Casino mode ON by default (v0.1.2, GAME_DESIGN.md §2.1) through the real start-up resolution
 * ({@link CasinoMode#bootstrap(SavedDataStorage, Path, Boolean)}, the code SERVER_STARTING runs) and the
 * vanilla saved-data storage writing and reading {@code data/burmaldaholic/mode.dat} on disk:
 * a fresh world (no file) is ON, a v0.1.1 file without a value is upgraded to ON, and an explicit OFF —
 * the Create World button or the real {@code /casino mode off} command — survives save and reload.
 */
public class CasinoModeDefaultGameTests {
	/** A world nobody configured (dedicated server first start): ON, written explicitly, read back ON. */
	@GameTest
	public void freshWorldDefaultsOn(GameTestHelper helper) {
		MinecraftServer server = helper.getLevel().getServer();
		Path dir = tempDir();
		try {
			try (SavedDataStorage storage = storage(server, dir)) {
				CasinoMode.Boot boot = CasinoMode.bootstrap(storage, dir, null);
				helper.assertTrue(boot == CasinoMode.Boot.MISSING, "fresh world boot: " + boot);
				helper.assertTrue(storage.get(CasinoModeData.TYPE).enabled(), "fresh world is not ON");
				storage.saveAndJoin();
			}
			helper.assertTrue(Boolean.TRUE.equals(storedValue(dir)), "mode.dat of a fresh world has no explicit enabled=true");
			try (SavedDataStorage storage = storage(server, dir)) {
				CasinoMode.Boot boot = CasinoMode.bootstrap(storage, dir, null);
				helper.assertTrue(boot == CasinoMode.Boot.STORED && storage.get(CasinoModeData.TYPE).enabled(),
					"reloaded fresh world: " + boot);
			}
		} finally {
			delete(dir);
		}
		helper.succeed();
	}

	/**
	 * v0.1.1 wrote OFF (its default) as a file without the key: it reads as "never set" and is upgraded to
	 * ON once, written back explicitly. A world created with the button OFF stays OFF after save + reload.
	 */
	@GameTest
	public void legacyFileUpgradedExplicitOffKept(GameTestHelper helper) {
		MinecraftServer server = helper.getLevel().getServer();
		Path dir = tempDir();
		try {
			// a world created with the Create World button turned OFF
			try (SavedDataStorage storage = storage(server, dir)) {
				CasinoMode.Boot boot = CasinoMode.bootstrap(storage, dir, false);
				helper.assertTrue(boot == CasinoMode.Boot.NEW_WORLD && !storage.get(CasinoModeData.TYPE).enabled(), "new world OFF: " + boot);
				storage.saveAndJoin();
			}
			helper.assertTrue(Boolean.FALSE.equals(storedValue(dir)), "explicit OFF not written as enabled=false");
			try (SavedDataStorage storage = storage(server, dir)) {
				CasinoMode.Boot boot = CasinoMode.bootstrap(storage, dir, null);
				helper.assertTrue(boot == CasinoMode.Boot.STORED && !storage.get(CasinoModeData.TYPE).enabled(),
					"explicit OFF lost on reload: " + boot);
			}
			// the same file as v0.1.1 wrote it for OFF: no "enabled" key
			Path file = dir.resolve("burmaldaholic/mode.dat");
			CompoundTag root = read(file);
			root.getCompoundOrEmpty("data").remove("enabled");
			NbtIo.writeCompressed(root, file);
			try (SavedDataStorage storage = storage(server, dir)) {
				CasinoMode.Boot boot = CasinoMode.bootstrap(storage, dir, null);
				helper.assertTrue(boot == CasinoMode.Boot.LEGACY_UPGRADED && storage.get(CasinoModeData.TYPE).enabled(),
					"v0.1.1 file without a value: " + boot);
				storage.saveAndJoin();
			}
			helper.assertTrue(Boolean.TRUE.equals(storedValue(dir)), "upgraded world not written back as enabled=true");
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		} finally {
			delete(dir);
		}
		helper.succeed();
	}

	/**
	 * The real {@code /casino mode off} on this server is saved as enabled=false in the world's own mode.dat
	 * and read back OFF by a new storage on the same folder (what the next server start does); then ON again.
	 * Own environment (a separate batch): switching the mode touches every live table and match.
	 */
	@GameTest(environment = "burmaldaholic:casino_mode_persist")
	public void explicitOffCommandPersists(GameTestHelper helper) {
		MinecraftServer server = helper.getLevel().getServer();
		Path dataDir = server.getWorldPath(LevelResource.DATA);
		boolean before = CasinoMode.isEnabled(server);
		try {
			server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "casino mode off");
			helper.assertTrue(!CasinoMode.isEnabled(server), "/casino mode off did not switch the mode off");
			server.getDataStorage().saveAndJoin();
			helper.assertTrue(Boolean.FALSE.equals(storedValue(dataDir)), "/casino mode off not saved as enabled=false");
			try (SavedDataStorage reloaded = storage(server, dataDir)) {
				CasinoMode.Boot boot = CasinoMode.bootstrap(reloaded, dataDir, null);
				helper.assertTrue(boot == CasinoMode.Boot.STORED && !reloaded.get(CasinoModeData.TYPE).enabled(),
					"explicit OFF not kept on the next start: " + boot);
			}
			server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "casino mode on");
			server.getDataStorage().saveAndJoin();
			helper.assertTrue(Boolean.TRUE.equals(storedValue(dataDir)), "/casino mode on not saved as enabled=true");
		} finally {
			CasinoMode.set(server, before);
		}
		helper.succeed();
	}

	private static SavedDataStorage storage(MinecraftServer server, Path dir) {
		return new SavedDataStorage(dir, server.getFixerUpper(), server.registryAccess());
	}

	/** The {@code enabled} value in mode.dat, or null if the file or the key is missing. */
	private static Boolean storedValue(Path dataDir) {
		Path file = dataDir.resolve("burmaldaholic/mode.dat");
		if (!Files.isRegularFile(file)) {
			return null;
		}
		CompoundTag data = read(file).getCompoundOrEmpty("data");
		return data.contains("enabled") ? data.getBooleanOr("enabled", false) : null;
	}

	private static CompoundTag read(Path file) {
		try {
			return NbtIo.readCompressed(file, NbtAccounter.unlimitedHeap());
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	private static Path tempDir() {
		try {
			return Files.createTempDirectory("burmaldaholic-mode");
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	private static void delete(Path dir) {
		try (Stream<Path> walk = Files.walk(dir)) {
			walk.sorted(Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
		} catch (IOException e) {
			// best effort
		}
	}
}
