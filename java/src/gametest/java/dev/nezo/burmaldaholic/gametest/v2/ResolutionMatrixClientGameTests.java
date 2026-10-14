package dev.nezo.burmaldaholic.gametest.v2;

import dev.nezo.burmaldaholic.gametest.v2.RealFlow;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;

/**
 * Phase 2b client GameTests of lane RS (RS / X-RS; PHASE2B.md §0.6). Registered in the gametest fabric.mod.json by Wave 0
 * so the owning package only fills in {@link #runTest}. Run alone with {@code BURMALDAHOLIC_CGT=RS}.
 */
public class ResolutionMatrixClientGameTests implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext context) {
		if (!RealFlow.enabled("RS")) return;
		// stub: the owning package adds its checks here
	}
}
