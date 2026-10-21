package dev.nezo.burmaldaholic.gametest.core;

import dev.nezo.burmaldaholic.client.ui.v2.V2Chrome;
import dev.nezo.burmaldaholic.core.config.CasinoConfig;
import dev.nezo.burmaldaholic.core.ui.v2.KeyMap;
import dev.nezo.burmaldaholic.games.extras.ExtrasModule;
import dev.nezo.burmaldaholic.games.extras.block.PlinkoBlockEntity;
import dev.nezo.burmaldaholic.games.extras.block.WheelBlockEntity;
import dev.nezo.burmaldaholic.games.extras.client.ExtrasSyncProbe;
import dev.nezo.burmaldaholic.gametest.ClientTestWorlds;
import dev.nezo.burmaldaholic.gametest.v2.RealFlow;
import dev.nezo.burmaldaholic.gametest.v2.blackjack.BlackjackV2ClientGameTests;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;

/**
 * T-SYW (lane SYWT): the SY server lead on the actor-anchored Plinko / Wheel must not break the re-press contract. The
 * screen animates the actor's drop / spin from the result's ARRIVAL for the full duration and re-enables Drop / Spin
 * when it lands; the server's busy gate now ends at {@code decided + SyncLead + duration}. A player who presses again
 * the moment the screen allows it must be accepted (real clicks, server state asserted; live wall clock, no lag).
 */
public class SywRepressClientGameTests implements FabricClientGameTest {
	private final List<String> failures = new ArrayList<>();
	private final List<String> report = new ArrayList<>();

	@Override
	public void runTest(ClientGameTestContext context) {
		if (!RealFlow.enabled("SYWT") && !RealFlow.enabled("SywRepressClientGameTests")) return; // lane gate (BURMALDAHOLIC_CGT)
		try (TestSingleplayerContext world = ClientTestWorlds.casino(context).create();
			ClientTestWorlds.Quiet quiet = ClientTestWorlds.quiet(context, world)) {
			context.waitTicks(20);
			world.getServer().runCommand("casino balance set @p 1000000");
			world.getServer().runOnServer(server -> CasinoConfig.chaos().enabled = false);
			RealFlow f = new RealFlow(context, world);
			f.size(1920, 1080, 3);
			repress(f, "plinko", ExtrasModule.PLINKO.block(), 1, true);
			repress(f, "wheel", ExtrasModule.WHEEL.block(), 2, false);
			f.close();
			context.waitTicks(5);
			world.getServer().runOnServer(server -> CasinoConfig.chaos().enabled = true);
		}
		System.out.println("[SYWT] " + String.join("\n[SYWT] ", report));
		if (!failures.isEmpty()) throw new AssertionError("SYWT re-press: " + String.join("; ", failures));
	}

	private void repress(RealFlow f, String name, net.minecraft.world.level.block.Block block, int chip, boolean plinko) {
		BlockPos pos = BlackjackV2ClientGameTests.fresh(f, block);
		f.context().waitTicks(3);
		f.use(pos);
		f.context().waitFor(mc -> mc.gui.screen() instanceof V2Chrome.Holder, RealFlow.TIMEOUT);
		f.context().waitTicks(8);
		if (plinko) f.click(KeyMap.Ids.felt("risk.high"));
		f.click(KeyMap.Ids.chip(chip));
		for (int round = 0; round < 3; round++) {
			int seq0 = seq(f, pos, plinko);
			f.click(KeyMap.Ids.PRIMARY);
			// wait until this screen shows the result landed and offers the next press
			boolean started = false;
			int ticks = 0;
			for (; ticks < 400; ticks++) {
				f.context().waitTick();
				double[] p = f.client(mc -> plinko ? ExtrasSyncProbe.plinko(mc.gui.screen()) : ExtrasSyncProbe.wheel(mc.gui.screen()));
				if (p == null) break;
				boolean running = plinko ? p[0] >= 0 : p[3] >= 0;
				boolean done = plinko ? p[3] == 1 && p[0] < 0 : p[1] == 1;
				started |= running;
				if (started && done) break;
			}
			int seq1 = seq(f, pos, plinko);
			if (seq1 <= seq0) {
				failures.add(name + " round " + round + ": first press not accepted (seq " + seq0 + " -> " + seq1 + ")");
				return;
			}
			f.waitChrome(c -> c.primary.gate().enabled(), name + " primary re-enabled");
			// the player presses again right away (one reaction tick)
			f.click(KeyMap.Ids.PRIMARY);
			f.context().waitTicks(6);
			int seq2 = seq(f, pos, plinko);
			String status = f.status();
			report.add(name + " round " + round + ": landed after " + ticks + " ticks; re-press seq " + seq1 + " -> " + seq2 + ", status '" + status + "'");
			if (seq2 <= seq1) {
				// control: the same press SyncLead ticks later is accepted (the refusal is the lead window)
				f.context().waitTicks(5);
				f.click(KeyMap.Ids.PRIMARY);
				f.context().waitTicks(6);
				int seq3 = seq(f, pos, plinko);
				report.add(name + " round " + round + ": control press 5 ticks later seq " + seq2 + " -> " + seq3);
			}
			if (seq2 <= seq1) failures.add(name + " round " + round + ": the immediate re-press was refused by the server (seq " + seq1 + " -> " + seq2 + ", status '" + status + "')");
			// let the accepted (or not) one finish before the next round
			f.context().waitTicks(plinko ? 140 : 120);
		}
		f.close();
	}

	private static int seq(RealFlow f, BlockPos pos, boolean plinko) {
		return f.server(s -> {
			var be = s.overworld().getBlockEntity(pos);
			var tag = plinko ? ((PlinkoBlockEntity) be).writeClientState(RealFlow.player(s)) : ((WheelBlockEntity) be).writeClientState(RealFlow.player(s));
			return tag.getCompoundOrEmpty("result").getIntOr("seq", -1);
		});
	}
}
