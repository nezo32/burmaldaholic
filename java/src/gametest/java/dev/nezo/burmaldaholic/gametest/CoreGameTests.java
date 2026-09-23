package dev.nezo.burmaldaholic.gametest;

import dev.nezo.burmaldaholic.core.mode.CasinoMode;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

/**
 * In-world tests, run headless with {@code ./gradlew runGameTest}. Add per-module test classes in
 * {@code dev.nezo.burmaldaholic.gametest.<module>} and list them in src/gametest/resources/fabric.mod.json.
 */
public class CoreGameTests {
	@GameTest
	public void casinoModeFollowsGameRule(GameTestHelper helper) {
		var level = helper.getLevel();
		boolean before = level.getGameRules().get(CasinoMode.rule());
		try {
			level.getGameRules().set(CasinoMode.rule(), true, level.getServer());
			helper.assertTrue(CasinoMode.isEnabled(level), "casino mode should be enabled");
			level.getGameRules().set(CasinoMode.rule(), false, level.getServer());
			helper.assertFalse(CasinoMode.isEnabled(level), "casino mode should be disabled");
		} finally {
			level.getGameRules().set(CasinoMode.rule(), before, level.getServer());
		}
		helper.succeed();
	}
}
