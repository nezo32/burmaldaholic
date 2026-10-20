package dev.nezo.burmaldaholic.gametest.core;

import dev.nezo.burmaldaholic.client.anim.SmoothClock;
import dev.nezo.burmaldaholic.gametest.ClientTestWorlds;
import dev.nezo.burmaldaholic.gametest.v2.RealFlow;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.world.level.Level;

/**
 * T-SY (lane SY): tries to break the shared clock in a real client: pause (with and without a reader), /tick freeze,
 * /tick rate 5 and 60, a dimension change (epoch), a +60 s jump, repeated -1 s jumps (never visibly frozen) and no
 * level (title screen). Every check is collected; the test fails at the end with all of them.
 */
public class SyncAdversarialClientGameTests implements FabricClientGameTest {
	private final StringBuilder fails = new StringBuilder();

	@Override
	public void runTest(ClientGameTestContext context) {
		if (!RealFlow.enabled("SY") && !RealFlow.enabled("SyncAdversarialClientGameTests")) return;
		context.runOnClient(mc -> mc.gui.setScreen(null));
		int epochInWorld;
		try (TestSingleplayerContext world = ClientTestWorlds.casino(context).create();
			ClientTestWorlds.Quiet quiet = ClientTestWorlds.quiet(context, world)) {
			context.waitFor(mc -> mc.level != null && mc.player != null, 1200);
			context.waitTicks(40);
			log(context, "start");

			// 1) pause, sampled every tick: frozen, then resumes near the level time
			context.runOnClient(mc -> mc.pauseGame(false));
			context.waitTicks(3);
			boolean paused = context.computeOnClient(mc -> mc.isPaused());
			double p0 = now(context);
			for (int i = 0; i < 40; i++) {
				context.waitTicks(1);
				now(context);
			}
			double p1 = now(context);
			check(!paused || p1 - p0 < 60, "paused (sampled): clock moved " + (p1 - p0) + " ms (isPaused=" + paused + ")");
			context.runOnClient(mc -> mc.gui.setScreen(null));
			context.waitTicks(30);
			log(context, "after sampled pause");

			// 2) pause with NO reader for 5 s (no casino block in view): resumes near the level time
			context.runOnClient(mc -> mc.pauseGame(false));
			context.waitTicks(100);
			context.runOnClient(mc -> mc.gui.setScreen(null));
			context.waitTicks(10);
			log(context, "right after unobserved 5 s pause");
			context.waitTicks(200);
			log(context, "10 s after unobserved pause");

			// 3) /tick freeze
			world.getServer().runCommand("tick freeze");
			context.waitTicks(5);
			double f0 = now(context);
			for (int i = 0; i < 40; i++) {
				context.waitTicks(1);
				now(context);
			}
			double f1 = now(context);
			check(f1 - f0 < 60, "/tick freeze: clock moved " + (f1 - f0) + " ms");
			world.getServer().runCommand("tick unfreeze");
			context.waitTicks(40);
			log(context, "after unfreeze");

			// 4) /tick rate 60 and 5
			rate(context, world, 60, 240);
			rate(context, world, 5, 40);
			world.getServer().runCommand("tick rate 20");
			context.waitTicks(200);
			log(context, "back at rate 20");

			// 5) dimension change: new epoch, clock restarts at the level time
			int e0 = context.computeOnClient(mc -> SmoothClock.epoch());
			world.getServer().runCommand("execute in minecraft:the_nether run tp @p 0 100 0");
			context.waitFor(mc -> mc.level != null && mc.level.dimension() == Level.NETHER, 400);
			context.waitTicks(20);
			int e1 = context.computeOnClient(mc -> SmoothClock.epoch());
			check(e1 > e0, "dimension change bumps the epoch: " + e0 + " -> " + e1);
			log(context, "nether");
			world.getServer().runCommand("execute in minecraft:overworld run tp @p 0 100 0");
			context.waitFor(mc -> mc.level != null && mc.level.dimension() == Level.OVERWORLD, 400);
			context.waitTicks(20);

			// 6) +60 s jump: one snap forward, monotonic
			double j0 = now(context);
			context.runOnClient(mc -> SmoothClock.Test.jump(60_000));
			context.waitTicks(3);
			double j1 = now(context);
			check(j1 - j0 >= 60_000, "+60 s: " + j0 + " -> " + j1);
			context.runOnClient(mc -> SmoothClock.Test.reset());
			context.waitTicks(10);

			// 7) repeated -1 s jumps every 2 s: never visibly frozen (>= 0.45x wall time, frame gaps > MAX_STEP_MS excluded: the
			//    clock deliberately holds for one frame after a hitch while ahead), never back
			double prev = now(context);
			double prevWall = context.computeOnClient(mc -> SmoothClock.wallMs());
			double minRatio = Double.POSITIVE_INFINITY;
			for (int k = 0; k < 4; k++) {
				context.runOnClient(mc -> SmoothClock.Test.jump(-1000));
				for (int i = 0; i < 40; i++) {
					context.waitTicks(1);
					double v = now(context);
					double w = context.computeOnClient(mc -> SmoothClock.wallMs());
					check(v >= prev, "-1 s jumps: stepped back " + prev + " -> " + v);
					if (w - prevWall > 5 && w - prevWall <= dev.nezo.burmaldaholic.core.anim.sync.SmoothClockCore.MAX_STEP_MS) minRatio = Math.min(minRatio, (v - prev) / (w - prevWall));
					prev = v;
					prevWall = w;
				}
			}
			check(minRatio >= 0.45, "-1 s jumps: the clock ran at only " + minRatio + "x wall time (visibly frozen)");
			log(context, "after 4x -1 s");
			context.runOnClient(mc -> SmoothClock.Test.reset());
			context.waitTicks(5);
			epochInWorld = context.computeOnClient(mc -> SmoothClock.epoch());
		} finally {
			context.runOnClient(mc -> SmoothClock.Test.reset());
		}

		// 8) no level (title screen)
		context.waitTicks(10);
		context.runOnClient(mc -> {
			check(mc.level == null, "title screen has no level");
			check(!SmoothClock.started(), "no level: clock cleared");
			check(SmoothClock.nowMs() == 0, "no level: nowMs 0, got " + SmoothClock.nowMs());
		});
		check(context.computeOnClient(mc -> SmoothClock.epoch()) > epochInWorld, "no level bumps the epoch");

		if (fails.length() > 0) throw new AssertionError("T-SY adversarial:\n" + fails);
		System.out.println("[T-SY] all adversarial client checks passed");
	}

	private void rate(ClientGameTestContext context, TestSingleplayerContext world, int tps, int ticks) {
		world.getServer().runCommand("tick rate " + tps);
		context.waitTicks(Math.max(10, tps / 2));
		double prev = now(context);
		int snaps = 0;
		double maxErr = 0;
		for (int i = 0; i < ticks; i++) {
			context.waitTicks(1);
			double[] v = context.computeOnClient(mc -> new double[] {SmoothClock.nowMs(), SmoothClock.lastSnap() ? 1 : 0,
					SmoothClock.nowMs() - mc.level.getGameTime() * 50.0});
			check(v[0] >= prev, "rate " + tps + ": stepped back");
			prev = v[0];
			if (v[1] > 0) snaps++;
			if (i > ticks / 2) maxErr = Math.max(maxErr, Math.abs(v[2]));
		}
		System.out.println("[T-SY] rate " + tps + ": snaps=" + snaps + " maxErr(2nd half)=" + maxErr);

	}

	private static double now(ClientGameTestContext context) {
		return context.computeOnClient(mc -> SmoothClock.nowMs());
	}

	private static double err(ClientGameTestContext context) {
		return context.computeOnClient(mc -> SmoothClock.nowMs() - mc.level.getGameTime() * 50.0);
	}

	private void log(ClientGameTestContext context, String what) {
		System.out.println("[T-SY] " + what + ": errVsLevel=" + err(context) + " rate=" + context.computeOnClient(mc -> SmoothClock.lastRate()));
	}

	private void check(boolean ok, String msg) {
		if (!ok) {
			fails.append(" - ").append(msg).append('\n');
			System.out.println("[T-SY] FAIL " + msg);
		}
	}
}
