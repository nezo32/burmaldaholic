package dev.nezo.burmaldaholic.gametest.core;

import dev.nezo.burmaldaholic.client.anim.AnimClock;
import dev.nezo.burmaldaholic.client.anim.SmoothClock;
import dev.nezo.burmaldaholic.client.fx.motion.MotionClock;
import dev.nezo.burmaldaholic.client.table.ClientTableCache;
import dev.nezo.burmaldaholic.games.roulette.RouletteModule;
import dev.nezo.burmaldaholic.games.roulette.client.RouletteScreen;
import dev.nezo.burmaldaholic.gametest.ClientTestWorlds;
import dev.nezo.burmaldaholic.gametest.v2.RealFlow;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;

/**
 * Lane SY: the shared clock in a real client (SY-SPEC §1, §5). A roulette table is opened by using it; the state packet
 * passes the seq gate; {@link SmoothClock} tracks the level time and never steps back, also with packets delayed
 * 300 ms ± 150 ms and bunched every second ({@link NetLag}), and across a +1 s and a −1 s server time jump (the first
 * snaps forward, the second slows down). Every clock reader ({@link AnimClock}, {@link MotionClock}) sees it.
 * Deterministic asserts only, no screenshots.
 */
public class SyncClientGameTests implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext context) {
		if (!RealFlow.enabled("SY") && !RealFlow.enabled("SyncClientGameTests")) return; // lane gate (BURMALDAHOLIC_CGT)
		context.runOnClient(mc -> mc.gui.setScreen(null));
		try (TestSingleplayerContext world = ClientTestWorlds.casino(context).create();
			ClientTestWorlds.Quiet quiet = ClientTestWorlds.quiet(context, world)) {
			context.waitTicks(20);
			RealFlow f = new RealFlow(context, world);
			f.size(1920, 1080, 4);
			BlockPos pos = f.place(RouletteModule.TABLE);
			f.open(pos, RouletteScreen.class);
			long seq = context.computeOnClient(mc -> ClientTableCache.lastSeq(pos));
			check(seq > 0, "the state packet carried sync_seq through the gate: " + seq);
			check(context.computeOnClient(mc -> SmoothClock.started()), "the clock observed the server");

			double[] last = {Double.NEGATIVE_INFINITY};
			// 1) live wall clock (client GameTests run client and server lock-step, NOT paced by wall time, so only
			//    relative checks here): monotonic, moving, every reader agrees
			double l0 = context.computeOnClient(mc -> SmoothClock.nowMs());
			sample(context, last, 60, Double.NaN, "live");
			check(last[0] > l0, "the live clock moves: " + l0 + " -> " + last[0]);
			context.runOnClient(mc -> {
				double a = AnimClock.levelMs(0f);
				double m = MotionClock.levelMs(0f);
				check(Math.abs(a - m) < 60, "AnimClock and MotionClock read the same clock: " + a + " vs " + m);
			});

			// 2) delayed, jittered, bunched packets on the live clock: never steps back
			context.runOnClient(mc -> NetLag.install(300, 150, 1000));
			sample(context, last, 100, Double.NaN, "netlag");
			check(context.computeOnClient(mc -> NetLag.deferredCount()) > 0, "the hook delayed real packets");
			context.runOnClient(mc -> NetLag.clear());

			// 3) tick-locked wall (1 tick = 50 ms of wall): absolute tracking of the level time, then jumps
			context.runOnClient(mc -> {
				SmoothClock.Test.source(new SmoothClock.TimeSource() {
					@Override
					public long wallNanos() {
						return mc.level == null ? 0 : mc.level.getGameTime() * 50_000_000L;
					}

					@Override
					public Long serverTick() {
						return mc.level == null ? null : mc.level.getGameTime();
					}

					@Override
					public boolean paused() {
						return false;
					}
				});
				SmoothClock.Test.reset();
			});
			int epoch = context.computeOnClient(mc -> SmoothClock.epoch());
			last[0] = Double.NEGATIVE_INFINITY;
			sample(context, last, 60, 120, "tick-locked");
			double before = context.computeOnClient(mc -> SmoothClock.nowMs());
			context.runOnClient(mc -> NetLag.jumpTime(1000));
			context.waitTicks(10);
			double after = context.computeOnClient(mc -> SmoothClock.nowMs());
			check(after - before >= 1000, "+1 s jump snaps forward: " + before + " -> " + after);
			last[0] = Math.max(last[0], after);
			sample(context, last, 20, Double.NaN, "after +1 s");
			context.runOnClient(mc -> NetLag.jumpTime(-1000));
			sample(context, last, 240, Double.NaN, "after -1 s");
			double err = context.computeOnClient(mc -> SmoothClock.nowMs() - mc.level.getGameTime() * 50.0);
			check(Math.abs(err) < 400, "converging again after -1 s: " + err);
			check(context.computeOnClient(mc -> SmoothClock.epoch()) == epoch, "no epoch change without a new level");
			context.runOnClient(mc -> {
				SmoothClock.Test.source(null);
				SmoothClock.Test.reset();
			});

			f.close();
			context.waitTicks(5);
		} finally {
			context.runOnClient(mc -> {
				NetLag.clear();
				SmoothClock.Test.source(null);
				SmoothClock.Test.reset();
			});
			context.getInput().resizeWindow(1280, 720);
			context.runOnClient(mc -> {
				mc.options.guiScale().set(0);
				mc.resizeGui();
			});
		}
	}

	/** {@code ticks} client ticks: the clock never steps back and (unless NaN) stays within {@code maxErrMs} of level time. */
	private static void sample(ClientGameTestContext context, double[] last, int ticks, double maxErrMs, String what) {
		for (int i = 0; i < ticks; i++) {
			context.waitTicks(1);
			double[] v = context.computeOnClient(mc -> new double[] {SmoothClock.nowMs(), mc.level.getGameTime() * 50.0});
			check(v[0] >= last[0], what + ": the clock stepped back " + last[0] + " -> " + v[0]);
			last[0] = v[0];
			if (!Double.isNaN(maxErrMs) && i > 10) {
				check(Math.abs(v[0] - v[1]) <= maxErrMs, what + ": off the level time by " + (v[0] - v[1]) + " ms");
			}
		}
	}

	private static void check(boolean ok, String msg) {
		if (!ok) throw new AssertionError(msg);
	}
}
