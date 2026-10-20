package dev.nezo.burmaldaholic.gametest.tables;

import dev.nezo.burmaldaholic.gametest.v2.RealFlow;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;

/**
 * Phase 2b client GameTests of lane MT (MT; PHASE2B.md §0.6). Registered in the gametest fabric.mod.json by Wave 0
 * so the owning package only fills in {@link #runTest}. Run alone with {@code BURMALDAHOLIC_CGT=MT}.
 */
public class MultiBlockTableClientGameTests implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext context) {
		if (!RealFlow.enabled("MT")) return;
		// stub: the owning package adds its checks here
	}
}
