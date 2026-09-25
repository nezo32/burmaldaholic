package dev.nezo.burmaldaholic.gametest;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.nezo.burmaldaholic.core.mode.CasinoMode;
import dev.nezo.burmaldaholic.core.mode.CasinoModeData;
import java.util.concurrent.atomic.AtomicInteger;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.permissions.PermissionSet;

/**
 * In-world tests, run headless with {@code ./gradlew runGameTest}. Add per-module test classes in
 * {@code dev.nezo.burmaldaholic.gametest.<module>} and list them in src/gametest/resources/fabric.mod.json.
 */
public class CoreGameTests {
	private static int run(MinecraftServer server, CommandSourceStack source, String command) throws CommandSyntaxException {
		return server.getCommands().getDispatcher().execute(command, source);
	}

	/** Casino mode lives in the world's saved data (data/burmaldaholic/mode.dat) and fires change listeners. */
	@GameTest
	public void casinoModeFollowsSavedData(GameTestHelper helper) {
		var level = helper.getLevel();
		MinecraftServer server = level.getServer();
		boolean before = CasinoMode.isEnabled(level);
		AtomicInteger changes = new AtomicInteger();
		CasinoMode.onChange((s, value) -> changes.incrementAndGet());
		try {
			CasinoMode.set(server, true);
			helper.assertTrue(CasinoMode.isEnabled(level), "casino mode should be enabled");
			CasinoModeData data = server.getDataStorage().get(CasinoModeData.TYPE);
			helper.assertTrue(data != null && data.enabled() && data.isDirty(), "stored in mode.dat saved data");
			int afterOn = changes.get();
			CasinoMode.set(server, true);
			helper.assertValueEqual(changes.get(), afterOn, "no change event when the value is unchanged");
			CasinoMode.set(server, false);
			helper.assertFalse(CasinoMode.isEnabled(level), "casino mode should be disabled");
			helper.assertFalse(CasinoMode.isEnabled(server), "server view agrees");
			helper.assertValueEqual(changes.get(), afterOn + 1, "change event on OFF");
		} finally {
			CasinoMode.set(server, before);
		}
		helper.succeed();
	}

	/** {@code /casino mode on|off|status} (bare {@code /casino mode} = status), permission level 2. */
	@GameTest
	public void casinoModeCommand(GameTestHelper helper) throws CommandSyntaxException {
		MinecraftServer server = helper.getLevel().getServer();
		boolean before = CasinoMode.isEnabled(server);
		CommandSourceStack op = server.createCommandSourceStack();
		try {
			helper.assertValueEqual(run(server, op, "casino mode off"), 0, "off result");
			helper.assertFalse(CasinoMode.isEnabled(server), "off stored");
			helper.assertValueEqual(run(server, op, "casino mode status"), 0, "status off");
			helper.assertValueEqual(run(server, op, "casino mode"), 0, "bare = status");
			helper.assertValueEqual(run(server, op, "casino mode on"), 1, "on result");
			helper.assertTrue(CasinoMode.isEnabled(server), "on stored");
			helper.assertValueEqual(run(server, op, "burmaldaholic mode status"), 1, "alias status on");
			helper.assertTrue(server.getDataStorage().get(CasinoModeData.TYPE).isDirty(), "marked dirty for saving");

			CommandSourceStack nobody = op.withPermission(PermissionSet.NO_PERMISSIONS);
			for (String cmd : new String[] {"casino mode off", "casino mode status", "casino mode"}) {
				try {
					run(server, nobody, cmd);
					helper.fail("non-op could run /" + cmd);
				} catch (CommandSyntaxException expected) {
					// requires() hides /casino from non-ops
				}
			}
			helper.assertTrue(CasinoMode.isEnabled(server), "unchanged by non-op");
		} finally {
			CasinoMode.set(server, before);
		}
		helper.succeed();
	}

	/** The mode is not a game rule any more: /gamerule does not know burmaldaholic:casino_mode. */
	@GameTest
	public void casinoModeIsNotAGameRule(GameTestHelper helper) {
		MinecraftServer server = helper.getLevel().getServer();
		CommandSourceStack op = server.createCommandSourceStack();
		for (String cmd : new String[] {"gamerule burmaldaholic:casino_mode", "gamerule burmaldaholic:casino_mode true"}) {
			try {
				run(server, op, cmd);
				helper.fail("/" + cmd + " still works");
			} catch (CommandSyntaxException expected) {
				// unknown game rule
			}
		}
		helper.succeed();
	}
}
