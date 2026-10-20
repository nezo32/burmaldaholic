package dev.nezo.burmaldaholic.gametest.v2.dice;

import dev.nezo.burmaldaholic.gametest.v2.RealFlow;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;

/**
 * Phase 2b client GameTests of lane DK (DCa; PHASE2B.md §0.6). Registered in the gametest fabric.mod.json by Wave 0
 * so the owning package only fills in {@link #runTest}. Run alone with {@code BURMALDAHOLIC_CGT=DK}.
 */
public class DiceKitClientGameTests implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext context) {
		if (!RealFlow.enabled("DK")) return;
		// stub: the owning package adds its checks here
	}
}
