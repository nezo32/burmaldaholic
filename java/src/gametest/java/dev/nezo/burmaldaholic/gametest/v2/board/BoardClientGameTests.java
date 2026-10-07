package dev.nezo.burmaldaholic.gametest.v2.board;

import dev.nezo.burmaldaholic.gametest.v2.RealFlow;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;

/**
 * Lane W (in-world board kit): REAL-FLOW client GameTests of the v2 screens (docs/architecture/ui-v2.md §11.2). Registered up
 * front by the kit lane (K) so no lane edits {@code fabric.mod.json}; lane W owns this file and replaces the stub
 * body. Run only this lane: {@code BURMALDAHOLIC_CGT=W}.
 */
public class BoardClientGameTests implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext context) {
		if (!RealFlow.enabled("W")) return;
		// stub: lane W fills this in
	}
}
