package dev.nezo.burmaldaholic.gametest.slots;

import dev.nezo.burmaldaholic.core.config.CasinoConfig;
import dev.nezo.burmaldaholic.core.economy.Economies;
import dev.nezo.burmaldaholic.games.slots.SlotMachineBlockEntity;
import dev.nezo.burmaldaholic.games.slots.SlotMachinesV2;
import dev.nezo.burmaldaholic.games.slots.SlotsModule;
import dev.nezo.burmaldaholic.games.slots.client.SlotBody;
import dev.nezo.burmaldaholic.games.slots.client.SlotMachineV2Screen;
import dev.nezo.burmaldaholic.games.slots.client.SlotStage;
import dev.nezo.burmaldaholic.games.slots.client.panels.SlotLayout;
import dev.nezo.burmaldaholic.games.slots.logic.Tier;
import dev.nezo.burmaldaholic.games.slots.v2.present.ReelMotion;
import dev.nezo.burmaldaholic.gametest.ClientTestWorlds;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;

/**
 * SLT real flow (owner R3: S1 sync, E1 jackpot clarity, coin × multiplier). Opens the Overworld machine like a player,
 * clicks the real SPIN button at its window pixel and asserts:
 * <ul>
 *   <li>the click starts the outcome-free pre-roll, and the arriving result HANDS OFF from it (a handoff plan exists);</li>
 *   <li>per tick no reel moves backwards beyond the landing bounce or faster than the handoff band (no jump, no snap);</li>
 *   <li>the screen ends on the SERVER's rest window and the server debited the bet;</li>
 *   <li>the bet label reads "coin × multiplier" and the Mini meter's tooltip says where the Mini comes from.</li>
 * </ul>
 * No screenshots (owner 2026-10-20): every check is a deterministic assert.
 */
public class SlotsSyncClientGameTests implements FabricClientGameTest {
	private final List<String> failures = new ArrayList<>();

	@Override
	public void runTest(ClientGameTestContext context) {
		if (!dev.nezo.burmaldaholic.gametest.v2.RealFlow.enabled("SLOTS") && !dev.nezo.burmaldaholic.gametest.v2.RealFlow.enabled("SlotsSyncClientGameTests")) return; // lane gate (BURMALDAHOLIC_CGT)
		context.getInput().resizeWindow(854, 480);
		context.runOnClient(mc -> mc.options.guiScale().set(2));
		try (TestSingleplayerContext world = ClientTestWorlds.casino(context).create();
			ClientTestWorlds.Quiet quiet = ClientTestWorlds.quiet(context, world)) {
			context.waitTicks(20);
			world.getServer().runCommand("casino balance set @p 100000");
			world.getServer().runOnServer(server -> CasinoConfig.chaos().enabled = false);
			spin(context, world);
			world.getServer().runOnServer(server -> CasinoConfig.chaos().enabled = true);
			context.runOnClient(mc -> mc.gui.setScreen(null));
			context.waitTicks(10);
		} finally {
			context.runOnClient(mc -> mc.options.guiScale().set(0));
		}
		if (!failures.isEmpty()) throw new AssertionError("slots sync real flow: " + String.join("; ", failures));
	}

	private static SlotStage stage(Minecraft mc) {
		return mc.gui.screen() instanceof SlotMachineV2Screen s && s.body() != null ? s.body().stage() : null;
	}

	private void spin(ClientGameTestContext context, TestSingleplayerContext world) {
		long[] start = new long[1];
		world.getServer().runOnServer(server -> {
			ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
			BlockPos pos = player.blockPosition().offset(2, 0, 0);
			server.overworld().setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
			server.overworld().setBlockAndUpdate(pos, SlotsModule.MACHINES.get(Tier.COPPER).block().defaultBlockState());
			if (server.overworld().getBlockEntity(pos) instanceof SlotMachineBlockEntity be) {
				SlotMachinesV2.cfg(be.machineV2()).minVipTier = 0;
				player.openMenu(be);
				start[0] = Economies.get().balance(player);
			}
		});
		context.waitFor(mc -> mc.gui.screen() instanceof SlotMachineV2Screen s && s.body() != null && s.body().model().def != null, 200);
		context.waitTicks(10);
		// E1: coin × multiplier label and the Mini meter's source tooltip
		String problem = context.computeOnClient(mc -> {
			SlotBody body = ((SlotMachineV2Screen) mc.gui.screen()).body();
			String label = SlotBody.betLabel(body.model()).getString();
			if (!label.contains("×") || !label.contains("5")) return "bet label is not coin × multiplier: " + label;
			SlotLayout l = body.layout();
			for (int i = 0; i < l.meterTiers.length; i++) {
				if (l.meterTiers[i] != 1) continue;
				String tip = body.meterTip(l, l.meterX(i) + 4, l.meterY + 4);
				if (tip == null || !tip.contains("Treasure Hunt") || !tip.contains("jackpot gem")) return "Mini meter tooltip: " + tip;
			}
			return null;
		});
		if (problem != null) failures.add(problem);
		// the real SPIN (the v2 Dock primary) at its window pixel
		double[] p = context.computeOnClient(mc -> {
			SlotMachineV2Screen s = (SlotMachineV2Screen) mc.gui.screen();
			var vp = s.chrome().viewport();
			return s.chrome().hits().box("primary").map(b -> new double[] {vp.toWindow(b.x() + b.w() / 2.0), vp.toWindow(b.y() + b.h() / 2.0)})
				.orElseThrow(() -> new AssertionError("no primary target"));
		});
		context.getInput().setCursorPos(p[0], p[1]);
		context.waitTick();
		context.getInput().pressMouse(dev.nezo.burmaldaholic.client.input.Inputs.MOUSE_LEFT);
		boolean sawPreRoll = false;
		boolean sawHandoff = false;
		double[] prev = null;
		double maxStep = 3.5 * ReelMotion.V * 50 * 2; // per tick (≥ 50 ms; 2× for a slow frame)
		for (int tick = 0; tick < 600; tick++) {
			context.waitTick();
			Object[] snap = context.computeOnClient(mc -> {
				SlotStage st = stage(mc);
				if (st == null) return null;
				boolean handoff = st.frames() != null && st.frames().handoff() != null;
				double[] top = new double[5];
				boolean moving = false;
				int phase = st.frames() == null ? -1 : st.frames().phase;
				for (int r = 0; r < 5; r++) {
					top[r] = st.frames() == null ? st.reels().preRollTop(r) : st.frames().top[r];
					moving |= st.frames() != null && st.frames().moving[r];
				}
				int[] len = new int[5];
				for (int r = 0; r < 5; r++) len[r] = st.def().stripLength(r);
				return new Object[] {st.preRolling(), handoff, top, phase, st.finished(), len};
			});
			if (snap == null) break;
			sawPreRoll |= (boolean) snap[0];
			sawHandoff |= (boolean) snap[1];
			double[] top = (double[]) snap[2];
			if ((int) snap[3] == 0 && prev != null) {
				for (int r = 0; r < 5; r++) {
					int len = ((int[]) snap[5])[r];
					double d = prev[r] - top[r];
					d -= len * Math.floor(d / len + 0.5); // whole strip turns are invisible (same cells)
					if (d < -0.2 || d > maxStep) failures.add("reel " + r + " jumped " + d + " cells in one tick");
				}
			}
			prev = (int) snap[3] == 0 ? top : null;
			if ((boolean) snap[4]) break;
		}
		if (!sawHandoff) failures.add("the result did not hand off from the pre-roll (pre-roll seen: " + sawPreRoll + ")");
		context.waitTicks(10);
		int[] rest = world.getServer().computeOnServer(server -> {
			ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
			BlockPos pos = player.blockPosition().offset(2, 0, 0);
			return server.overworld().getBlockEntity(pos) instanceof SlotMachineBlockEntity be ? be.restCells() : null;
		});
		long now = world.getServer().computeOnServer(server -> Economies.get().balance(server.getPlayerList().getPlayers().getFirst()));
		String end = context.computeOnClient(mc -> {
			SlotStage st = stage(mc);
			if (st == null) return "no stage";
			if (!st.finished()) return "spin did not finish";
			return rest != null && Arrays.equals(st.frames().snapshot().cells(), rest) ? null
				: "screen ends off the server's rest window " + Arrays.toString(rest);
		});
		if (end != null) failures.add(end);
		if (now == start[0]) failures.add("the server did not take the spin (balance unchanged)");
		world.getServer().runOnServer(server -> server.getPlayerList().getPlayers().getFirst().closeContainer());
		context.waitTicks(5);
	}
}
