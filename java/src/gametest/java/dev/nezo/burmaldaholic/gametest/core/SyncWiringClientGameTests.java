package dev.nezo.burmaldaholic.gametest.core;

import dev.nezo.burmaldaholic.client.anim.SmoothClock;
import dev.nezo.burmaldaholic.client.ui.v2.V2Chrome;
import dev.nezo.burmaldaholic.core.anim.dice.DieSample;
import dev.nezo.burmaldaholic.core.anim.dice.DiceRoll;
import dev.nezo.burmaldaholic.core.anim.dice.DuelTimeline;
import dev.nezo.burmaldaholic.core.anim.sync.SyncLead;
import dev.nezo.burmaldaholic.core.config.CasinoConfig;
import dev.nezo.burmaldaholic.core.ui.v2.KeyMap;
import dev.nezo.burmaldaholic.games.blackjack.BlackjackModule;
import dev.nezo.burmaldaholic.games.blackjack.BlackjackTableBlockEntity;
import dev.nezo.burmaldaholic.games.blackjack.client.BlackjackScreen;
import dev.nezo.burmaldaholic.games.craps.CrapsModule;
import dev.nezo.burmaldaholic.games.craps.CrapsTableBlockEntity;
import dev.nezo.burmaldaholic.games.craps.client.CrapsScreen;
import dev.nezo.burmaldaholic.games.craps.logic.BetKind;
import dev.nezo.burmaldaholic.games.craps.present.CrapsLayout;
import dev.nezo.burmaldaholic.games.craps.present.CrapsRollClock;
import dev.nezo.burmaldaholic.games.extras.ExtrasModule;
import dev.nezo.burmaldaholic.games.extras.block.PlinkoBlockEntity;
import dev.nezo.burmaldaholic.games.extras.block.WheelBlockEntity;
import dev.nezo.burmaldaholic.games.extras.client.DiceScreenProbe;
import dev.nezo.burmaldaholic.games.extras.client.ExtrasSyncProbe;
import dev.nezo.burmaldaholic.games.extras.logic.anim.PlinkoAnim;
import dev.nezo.burmaldaholic.games.roulette.RouletteModule;
import dev.nezo.burmaldaholic.games.roulette.RouletteTableBlockEntity;
import dev.nezo.burmaldaholic.games.roulette.client.RouletteScreen;
import dev.nezo.burmaldaholic.games.roulette.logic.BetType;
import dev.nezo.burmaldaholic.games.roulette.logic.Bets;
import dev.nezo.burmaldaholic.games.roulette.logic.Spot;
import dev.nezo.burmaldaholic.games.roulette.present.RouletteLayout;
import dev.nezo.burmaldaholic.games.slots.SlotMachineBlockEntity;
import dev.nezo.burmaldaholic.games.slots.SlotMachinesV2;
import dev.nezo.burmaldaholic.games.slots.SlotsModule;
import dev.nezo.burmaldaholic.games.slots.client.SlotMachineV2Screen;
import dev.nezo.burmaldaholic.games.slots.client.SlotStage;
import dev.nezo.burmaldaholic.games.slots.logic.Tier;
import dev.nezo.burmaldaholic.games.slots.v2.present.ReelMotion;
import dev.nezo.burmaldaholic.gametest.ClientTestWorlds;
import dev.nezo.burmaldaholic.gametest.v2.RealFlow;
import dev.nezo.burmaldaholic.gametest.v2.blackjack.BlackjackV2ClientGameTests;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;

/**
 * Lane SYW (SY-SPEC §5 acceptance, per game): every game played like a player (block used, real clicks at their
 * pixels, the server's state asserted) under {@link NetLag} — table state and time packets 300 ms late, ±150 ms jitter,
 * bunched every second — and a ±1 s server time jump in the middle of the animation. Per game:
 * <ul>
 *   <li>per frame the drawn pose moves no further than the game's own motion model moves in 1.15 frame times (for a
 *       smooth model: ≤ 1.15 × its top speed × frame time);</li>
 *   <li>the final pose is the server's result (pocket, bin, segment, reel window, dice faces, cards landed);</li>
 *   <li>the animation time never steps back.</li>
 * </ul>
 * Actor-anchored animations (slots, craps, plinko, wheel: the player's own press, local time) must pass every frame
 * under both jumps; shared-clock ones (roulette, cards, PvP dice) may show the ONE forward snap SY allows a spectator
 * (+1 s, more than 500 ms behind), never under −1 s. The client runs on a tick-locked wall ({@code
 * SmoothClock.Test.source}: one client tick = 50 ms), because client GameTests are not paced by wall time; NetLag's
 * delays run on the same wall. Deterministic asserts only, no screenshots.
 */
public class SyncWiringClientGameTests implements FabricClientGameTest {
	private static final long TICK_NS = 50_000_000L;
	private static final double FRAME_MS = 50;
	private static final double SLACK = 1.15;
	/** T-SYW: an 800 ms packet spike for 1 s (scenario code, not a jump). */
	private static final double SPIKE = 0.5;
	/** T-SYW: the server paused ({@code /tick freeze}) for 1 s mid-animation (scenario code, not a jump). */
	private static final double FREEZE = 0.25;
	private static final double[] JUMPS = {1000, -1000, SPIKE, FREEZE};
	/** T-SYW: client ticks until the spike / freeze ends (START_CLIENT_TICK countdown), or -1. */
	private static volatile int restoreIn = -1;
	private static volatile Runnable restore;
	private static final AtomicLong WALL = new AtomicLong();
	private static volatile boolean locked;
	private static boolean registered;

	private final List<String> failures = new ArrayList<>();
	private final List<String> report = new ArrayList<>();
	private int seq = 1000;

	@Override
	public void runTest(ClientGameTestContext context) {
		if (!RealFlow.enabled("SYW") && !RealFlow.enabled("SyncWiringClientGameTests")) return; // lane gate (BURMALDAHOLIC_CGT)
		context.runOnClient(mc -> mc.gui.setScreen(null));
		try (TestSingleplayerContext world = ClientTestWorlds.casino(context).create();
			ClientTestWorlds.Quiet quiet = ClientTestWorlds.quiet(context, world)) {
			context.waitTicks(20);
			world.getServer().runCommand("casino balance set @p 1000000");
			world.getServer().runOnServer(server -> CasinoConfig.chaos().enabled = false);
			// flake fix: the wheel's Creeper segment requests a chaos mob wave even with chaos off; its mobs closed the
			// wheel screen mid-run ("no target 'primary' on no screen"). Peaceful removes them.
			world.getServer().runCommand("difficulty peaceful");
			RealFlow f = new RealFlow(context, world);
			f.size(1920, 1080, 3);
			lock(context);
			step(f, "plinko", () -> plinko(f));
			step(f, "wheel", () -> wheel(f));
			step(f, "slots", () -> slots(f));
			step(f, "craps", () -> craps(f));
			step(f, "roulette", () -> roulette(f));
			step(f, "cards", () -> cards(f));
			step(f, "dice pvp", () -> dicePvp(f));
			f.close();
			context.waitTicks(5);
			world.getServer().runOnServer(server -> CasinoConfig.chaos().enabled = true);
		} finally {
			context.runOnClient(mc -> unlock());
			context.getInput().resizeWindow(1280, 720);
			context.runOnClient(mc -> {
				mc.options.guiScale().set(0);
				mc.resizeGui();
			});
		}
		System.out.println("[SYW] " + String.join("\n[SYW] ", report));
		if (!failures.isEmpty()) throw new AssertionError("SYW sync wiring: " + String.join("; ", failures));
	}

	private void step(RealFlow f, String name, Runnable r) {
		try {
			r.run();
		} catch (RuntimeException | AssertionError e) {
			failures.add(name + ": " + e);
		} finally {
			unlag(f);
		}
	}

	private void fail(String msg) {
		failures.add(msg);
	}

	// ---- clock --------------------------------------------------------------------------------------------------

	/** One client tick = 50 ms of wall for SmoothClock, every local anim clock and NetLag (installed for the test). */
	private static void lock(ClientGameTestContext context) {
		context.runOnClient(mc -> {
			if (!registered) {
				registered = true;
				ClientTickEvents.START_CLIENT_TICK.register(m -> {
					if (locked) WALL.addAndGet(TICK_NS);
					if (restoreIn > 0 && --restoreIn == 0) {
						Runnable r = restore;
						restore = null;
						restoreIn = -1;
						if (r != null) r.run();
					}
				});
			}
			// flake fix: a whole-second wall phase (NetLag bunches on whole seconds) and a fresh NetLag (its jitter RNG was
			// consumed by earlier classes), so the class sees the same inputs alone and in the full suite
			WALL.set((System.nanoTime() / 1_000_000_000L + 1) * 1_000_000_000L);
			NetLag.reset();
			locked = true;
			SmoothClock.Test.source(new SmoothClock.TimeSource() {
				@Override
				public long wallNanos() {
					return WALL.get();
				}

				@Override
				public Long serverTick() {
					return mc.level == null ? null : mc.level.getGameTime();
				}

				@Override
				public boolean paused() {
					// T-SYW: the production pause inputs (a /tick freeze mid-animation must pause the shared clock)
					return mc.isPaused() || (mc.level != null && !mc.level.tickRateManager().runsNormally());
				}
			});
			SmoothClock.Test.reset();
		});
	}

	private static void unlock() {
		NetLag.clear();
		locked = false;
		SmoothClock.Test.source(null);
		SmoothClock.Test.reset();
	}

	/**
	 * Before an animation: a fresh clock epoch (the previous run's −1 s jump leaves the clock ahead for seconds, which
	 * would swallow this run's +1 s), then NetLag on.
	 */
	private static void lag(RealFlow f) {
		f.context().runOnClient(mc -> SmoothClock.Test.reset());
		f.context().waitTicks(2);
		f.context().runOnClient(mc -> NetLag.install(300, 150, 1000));
	}

	private static void unlag(RealFlow f) {
		f.context().runOnClient(mc -> {
			Runnable r = restore;
			restore = null;
			restoreIn = -1;
			if (r != null) r.run();
			NetLag.clear();
		});
	}

	private static void jump(RealFlow f, double ms) {
		if (ms == SPIKE) {
			// an 800 ms (+-100) delay spike for 20 ticks, then back to the base lag
			f.context().runOnClient(mc -> {
				NetLag.install(800, 100, 0);
				restore = () -> {
					if (dev.nezo.burmaldaholic.client.anim.NetLagHook.hook != null) NetLag.install(300, 150, 1000);
				};
				restoreIn = 20;
			});
		} else if (ms == FREEZE) {
			f.world().getServer().runCommand("tick freeze");
			f.context().runOnClient(mc -> {
				var server = mc.getSingleplayerServer();
				restore = () -> server.execute(() -> server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "tick unfreeze"));
				restoreIn = 20;
			});
		} else {
			f.context().runOnClient(mc -> NetLag.jumpTime(ms));
		}
	}

	// ---- the per-frame check ------------------------------------------------------------------------------------

	private static double dist(double[] a, double[] b) {
		double s = 0;
		for (int i = 0; i < Math.min(a.length, b.length); i++) s += (a[i] - b[i]) * (a[i] - b[i]);
		return Math.sqrt(s);
	}

	/**
	 * One animation's frames: the pose drawn at time t of the game's model {@code model}. A frame passes when the
	 * pose moved no further than the model itself moves within {@link #SLACK} frame times from the last frame's t;
	 * t must never step back.
	 */
	private static final class Track {
		final String what;
		double prevT = Double.NaN;
		double[] prev;
		int frames;
		int over;
		int backwards;
		double worst;
		String worstAt = "";

		Track(String what) {
			this.what = what;
		}

		void frame(double t, double[] pose, Function<Double, double[]> model) {
			if (prev != null) {
				if (t < prevT - 1e-6) backwards++;
				double d = dist(prev, pose);
				double[] p0 = model.apply(prevT);
				double legit = 0;
				double span = SLACK * FRAME_MS;
				for (double tau = prevT; tau <= prevT + span + 1e-9; tau += 1) legit = Math.max(legit, dist(p0, model.apply(tau)));
				legit = Math.max(legit, dist(p0, model.apply(prevT + span)));
				// the drawn pose of the previous frame vs the model's own: a model rebuilt in between (a result joining a
				// result-free pre-roll) must continue from where the drawn pose was
				legit += dist(prev, p0);
				if (d > legit + 1e-6) {
					over++;
					if (d - legit > worst) {
						worst = d - legit;
						worstAt = "t " + Math.round(prevT) + "->" + Math.round(t) + " moved " + d + " legit " + legit;
					}
				}
			}
			prev = pose;
			prevT = t;
			frames++;
		}

		String summary() {
			return what + ": " + frames + " frames, over " + over + (over > 0 ? " (worst " + worstAt + ")" : "") + ", backwards " + backwards;
		}
	}

	/** Actor: no frame over, no step back. Shared clock: +1 s may snap once (SY-SPEC §1), −1 s never. */
	private void judge(Track tr, boolean actor, double jump, int minFrames) {
		report.add(tr.summary());
		if (tr.frames < minFrames) fail(tr.what + ": only " + tr.frames + " frames sampled");
		if (tr.backwards > 0) fail(tr.what + ": time stepped back " + tr.backwards + "x");
		int allowed = actor || jump < 0 ? 0 : 1;
		if (tr.over > allowed) fail(tr.summary());
	}

	// ---- Plinko (actor: local time from the result's arrival) ---------------------------------------------------

	private static BlockPos machine(RealFlow f, net.minecraft.world.level.block.Block block) {
		BlockPos pos = BlackjackV2ClientGameTests.fresh(f, block); // a clear spot 7 blocks on
		f.context().waitTicks(3);
		f.use(pos);
		f.context().waitFor(mc -> mc.gui.screen() instanceof V2Chrome.Holder, RealFlow.TIMEOUT);
		f.context().waitTicks(8);
		return pos;
	}

	/** Presses like a player: waits (≤ 10 s) until the screen enables the primary (T-SYW D1: the server's busy gate). */
	private void awaitPrimary(RealFlow f, String what) {
		for (int t = 0; t < 200; t++) {
			if (f.client(mc -> mc.gui.screen() instanceof V2Chrome.Holder h && h.chrome().primary.gate().enabled())) return;
			f.context().waitTick();
		}
		String gate = f.client(mc -> mc.gui.screen() instanceof V2Chrome.Holder h ? String.valueOf(h.chrome().primary.gate()) : String.valueOf(mc.gui.screen()));
		fail(what + ": the primary never re-enabled before the press (gate " + gate + ", status '" + f.status() + "')");
	}

	private void plinko(RealFlow f) {
		BlockPos pos = machine(f, ExtrasModule.PLINKO.block());
		f.click(KeyMap.Ids.felt("risk.high"));
		f.click(KeyMap.Ids.chip(1));
		for (double jump : JUMPS) {
			Track tr = new Track("plinko jump " + jump);
			awaitPrimary(f, "plinko jump " + jump);
			lag(f);
			f.click(KeyMap.Ids.PRIMARY);
			boolean started = false;
			boolean jumped = false;
			double[] last = null;
			for (int k = 0; k < 600; k++) {
				f.context().waitTick();
				double[] p = f.client(mc -> ExtrasSyncProbe.plinko(mc.gui.screen()));
				if (p == null) break;
				if (p[0] >= 0 && !Double.isNaN(p[1])) {
					started = true;
					int path = (int) p[5];
					int rowMs = (int) p[6];
					tr.frame(p[0], new double[] {p[1], p[2]}, ms -> {
						PlinkoAnim.Ball b = PlinkoAnim.sample(path, rowMs, ms);
						return new double[] {b.x(), b.y()};
					});
					if (!jumped && p[0] > 600) {
						jump(f, jump);
						jumped = true;
					}
				}
				last = p;
				if (started && p[3] == 1) break;
			}
			unlag(f);
			f.context().waitTicks(10);
			judge(tr, true, jump, 10);
			int bin = f.server(s -> ((PlinkoBlockEntity) s.overworld().getBlockEntity(pos)).writeClientState(RealFlow.player(s)).getCompoundOrEmpty("result").getIntOr("bin", -2));
			if (!jumped) fail("plinko: the drop never ran on screen (jump " + jump + ")");
			if (last == null || last[3] != 1 || (int) last[4] != bin) fail("plinko: ended " + Arrays.toString(last) + ", server bin " + bin);
			f.context().waitTicks(20);
		}
		f.close();
	}

	// ---- Wheel (actor) ------------------------------------------------------------------------------------------

	private void wheel(RealFlow f) {
		BlockPos pos = machine(f, ExtrasModule.WHEEL.block());
		f.click(KeyMap.Ids.chip(2));
		for (double jump : JUMPS) {
			Track tr = new Track("wheel jump " + jump);
			awaitPrimary(f, "wheel jump " + jump);
			lag(f);
			f.click(KeyMap.Ids.PRIMARY);
			boolean jumped = false;
			boolean started = false;
			double[] end = null;
			for (int k = 0; k < 600; k++) {
				f.context().waitTick();
				double[][] p = f.client(mc -> {
					var s = mc.gui.screen();
					double[] q = ExtrasSyncProbe.wheel(s);
					if (q == null) return null;
					if (q[3] >= 0) tr.frame(q[3], new double[] {q[0]}, tau -> new double[] {ExtrasSyncProbe.wheelAngleAt(s, tau)});
					return new double[][] {q, {ExtrasSyncProbe.wheelEnd(s)}};
				});
				if (p == null) break;
				double[] q = p[0];
				if (q[3] >= 0) {
					started = true;
					double ms = q[3];
					if (!jumped && ms > 500) {
						jump(f, jump);
						jumped = true;
					}
				} else if (started && q[1] == 1) {
					end = new double[] {q[0], p[1][0], q[2]};
					break;
				}
			}
			unlag(f);
			f.context().waitTicks(10);
			judge(tr, true, jump, 10);
			int index = f.server(s -> ((WheelBlockEntity) s.overworld().getBlockEntity(pos)).writeClientState(RealFlow.player(s)).getCompoundOrEmpty("result").getIntOr("index", -2));
			if (!jumped || end == null) fail("wheel: the spin never ran / settled (jump " + jump + ")");
			else if (Math.abs(end[0] - end[1]) > 1e-6 || (int) end[2] != index) fail("wheel: rests at " + end[0] + " (end " + end[1] + "), index " + end[2] + " vs server " + index);
			f.context().waitTicks(20);
		}
		f.close();
	}

	// ---- Slots (actor: the pre-roll hands off into the result) ---------------------------------------------------

	private static SlotStage stage(Minecraft mc) {
		return mc.gui.screen() instanceof SlotMachineV2Screen s && s.body() != null ? s.body().stage() : null;
	}

	private void slots(RealFlow f) {
		BlockPos at = f.server(server -> {
			ServerPlayer player = RealFlow.player(server);
			BlockPos pos = player.blockPosition().offset(2, 0, 0);
			server.overworld().setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
			server.overworld().setBlockAndUpdate(pos, SlotsModule.MACHINES.get(Tier.COPPER).block().defaultBlockState());
			if (server.overworld().getBlockEntity(pos) instanceof SlotMachineBlockEntity be) SlotMachinesV2.cfg(be.machineV2()).minVipTier = 0;
			return pos;
		});
		f.use(at);
		f.context().waitFor(mc -> mc.gui.screen() instanceof SlotMachineV2Screen s && s.body() != null && s.body().model().def != null, RealFlow.TIMEOUT);
		f.context().waitTicks(10);
		double bound = SLACK * 3.5 * ReelMotion.V * FRAME_MS; // SLT's handoff band (3.5 V) per frame
		for (double jump : JUMPS) {
			lag(f);
			double[] px = f.client(mc -> {
				SlotMachineV2Screen s = (SlotMachineV2Screen) mc.gui.screen();
				var vp = s.chrome().viewport();
				return s.chrome().hits().box("primary").map(b -> new double[] {vp.windowX(b.x() + b.w() / 2.0), vp.windowY(b.y() + b.h() / 2.0)})
					.orElseThrow(() -> new AssertionError("no slots primary"));
			});
			boolean rolling = false;
			for (int attempt = 0; attempt < 5 && !rolling; attempt++) {
				f.settle();
				f.context().getInput().setCursorPos(px[0], px[1]);
				f.context().waitTick();
				f.context().getInput().pressMouse(dev.nezo.burmaldaholic.client.input.Inputs.MOUSE_LEFT);
				f.context().waitTick();
				rolling = f.client(mc -> stage(mc) != null && stage(mc).preRolling());
				if (!rolling) {
					report.add("slots jump " + jump + ": click " + attempt + " did not start the pre-roll: " + f.client(mc -> mc.gui.screen() instanceof SlotMachineV2Screen sc
						? "pressed '" + sc.chrome().lastPressed() + "' gate " + sc.chrome().primary.gate() + " playable " + sc.body().model().playable + " lock "
							+ sc.body().model().lockReason + " finished " + stage(mc).finished() : "screen " + mc.gui.screen()));
					f.context().waitTicks(40);
				}
			}
			boolean handoff = false;
			boolean sawPreRoll = rolling;
			boolean wasPreRolling = false;
			boolean jumped = false;
			double[] prev = null;
			double lastT = Double.NEGATIVE_INFINITY;
			int frames = 0;
			int over = 0;
			int back = 0;
			double worst = 0;
			for (int tick = 0; tick < 800; tick++) {
				f.context().waitTick();
				Object[] snap = f.client(mc -> {
					SlotStage st = stage(mc);
					if (st == null) return null;
					double[] top = new double[5];
					int[] len = new int[5];
					// the pre-roll is drawn while it runs (the last spin's sampler stays on the stage until the result):
					// its pose this frame from the pre-roll model at this frame's wall time (preRollTop is only refreshed
					// by the draw, so it can be a frame stale)
					double[][] pre = st.preRolling() ? st.reels().preRollPose(dev.nezo.burmaldaholic.games.slots.client.SpinClock.wallNow()) : null;
					for (int r = 0; r < 5; r++) {
						top[r] = pre != null ? pre[0][r] : st.frames() == null ? st.reels().preRollTop(r) : st.frames().top[r];
						len[r] = st.def().stripLength(r);
					}
					return new Object[] {st.frames() != null && st.frames().handoff() != null, top, st.frames() == null ? -1 : st.frames().phase, st.finished(), len,
						st.clock() == null ? Double.NaN : st.clock().lastT(), st.preRolling()};
				});
				if (snap == null) {
					report.add("slots jump " + jump + ": the stage went away at tick " + tick + ": " + f.client(mc -> String.valueOf(mc.gui.screen())));
					break;
				}
				// this run's spin: the pre-roll seen, then the result handed off from it (the last spin's sampler and
				// "finished" stay on the stage until the new result arrives)
				sawPreRoll |= (boolean) snap[6];
				handoff |= sawPreRoll && !(boolean) snap[6] && (boolean) snap[0];
				double[] top = (double[]) snap[1];
				int phase = (int) snap[2];
				double t = (double) snap[5];
				if (handoff && !Double.isNaN(t)) {
					if (t < lastT - 1e-6) back++;
					lastT = Math.max(lastT, t);
					if (!jumped && t > 200) {
						jump(f, jump);
						jumped = true;
					}
				}
				// the press: the pre-roll starts at the rest stops (the last sampler's frame uses another convention)
				if ((boolean) snap[6] && !wasPreRolling) prev = null;
				wasPreRolling = (boolean) snap[6];
				if ((phase == 0 || (boolean) snap[6]) && prev != null) {
					for (int r = 0; r < 5; r++) {
						int len = ((int[]) snap[4])[r];
						double d = prev[r] - top[r];
						d -= len * Math.floor(d / len + 0.5); // whole strip turns are the same cells
						if (d < -0.2 || d > bound) {
							over++;
							worst = Math.max(worst, Math.abs(d));
						}
					}
					frames++;
				}
				prev = phase == 0 || (boolean) snap[6] ? top : null;
				if ((boolean) snap[3] && handoff) break;
			}
			unlag(f);
			f.context().waitTicks(10);
			report.add("slots jump " + jump + ": " + frames + " frames, over " + over + " (worst " + worst + " cells, bound " + bound + "), back " + back);
			if (!handoff) fail("slots: the result did not hand off from the pre-roll (jump " + jump + ")");
			if (!jumped) fail("slots: the jump was never injected");
			if (over > 0 || back > 0) fail("slots jump " + jump + ": " + over + " reel frames over the band (worst " + worst + "), t back " + back);
			int[] rest = f.server(server -> server.overworld().getBlockEntity(at) instanceof SlotMachineBlockEntity be ? be.restCells() : null);
			String end = f.client(mc -> {
				SlotStage st = stage(mc);
				if (st == null || !st.finished()) return "the spin did not finish";
				return rest != null && Arrays.equals(st.frames().snapshot().cells(), rest) ? null : "screen ends off the server's rest window " + Arrays.toString(rest);
			});
			if (end != null) fail("slots jump " + jump + ": " + end + " (handoff " + handoff + ", frames " + frames + ")");
			f.context().waitTicks(30);
		}
		f.server(server -> {
			RealFlow.player(server).closeContainer();
			return null;
		});
		f.context().waitTicks(5);
	}

	// ---- Craps (actor: the roller's own throw on local time) -----------------------------------------------------

	private void craps(RealFlow f) {
		BlockPos pos = BlackjackV2ClientGameTests.fresh(f, CrapsModule.TABLE.block());
		f.open(pos, CrapsScreen.class);
		for (double jump : JUMPS) {
			boolean pass = f.server(s -> ((CrapsTableBlockEntity) s.overworld().getBlockEntity(pos)).table().bets().stream().anyMatch(b -> b.kind() == BetKind.PASS));
			if (!pass) {
				f.click(CrapsLayout.PASS);
				f.waitServer(pos, CrapsTableBlockEntity.class, be -> be.table().bets().stream().anyMatch(b -> b.kind() == BetKind.PASS), "a Pass Line bet");
			}
			f.waitChrome(c -> c.primary.gate().enabled() && "roll".equals(c.primary.gate().action()), "Roll enabled");
			int n = f.server(s -> ((CrapsTableBlockEntity) s.overworld().getBlockEntity(pos)).table().rollCount());
			Track tr = new Track("craps jump " + jump);
			lag(f);
			f.click(KeyMap.Ids.PRIMARY);
			boolean jumped = false;
			int[] faces = null;
			for (int k = 0; k < 800; k++) {
				f.context().waitTick();
				Object[] snap = f.client(mc -> {
					if (!(mc.gui.screen() instanceof CrapsScreen sc)) return null;
					CrapsRollClock c = sc.throwClock();
					DiceRoll r = c.roll();
					double t = c.t();
					if (c.rolls() != n + 1 || r == null || !(t >= 0) || Double.isInfinite(t)) return new Object[] {Double.NaN};
					DieSample a = new DieSample();
					c.pose(0, t, a);
					DieSample b = new DieSample();
					c.pose(1, t, b);
					tr.frame(t, new double[] {a.x, a.y, b.x, b.y}, tau -> {
						DieSample x = new DieSample();
						c.pose(0, tau, x);
						DieSample y = new DieSample();
						c.pose(1, tau, y);
						return new double[] {x.x, x.y, y.x, y.y};
					});
					return new Object[] {t, null, (double) r.restMs(), r.faces()};
				});
				if (snap == null) break;
				double t = (double) snap[0];
				if (Double.isNaN(t)) continue;
				if (!jumped && t > 300) {
					jump(f, jump);
					jumped = true;
				}
				faces = (int[]) snap[3];
				if (t > (double) snap[2] + 300) break;
			}
			unlag(f);
			f.context().waitTicks(10);
			judge(tr, true, jump, 10);
			int[] server = f.server(s -> {
				var tb = ((CrapsTableBlockEntity) s.overworld().getBlockEntity(pos)).table();
				return new int[] {tb.lastD1(), tb.lastD2()};
			});
			if (!jumped || faces == null) fail("craps: the throw never ran on screen (jump " + jump + ")");
			else if (!Arrays.equals(faces, server)) fail("craps: the dice rest on " + Arrays.toString(faces) + ", the server rolled " + Arrays.toString(server));
			f.context().waitTicks(30);
		}
		f.close();
	}

	// ---- Roulette (shared clock: every viewer samples the same spin) ---------------------------------------------

	private void roulette(RealFlow f) {
		BlockPos pos = BlackjackV2ClientGameTests.fresh(f, RouletteModule.TABLE.block());
		f.open(pos, RouletteScreen.class);
		Spot spot = Spot.of(BetType.STRAIGHT, 17);
		for (double jump : JUMPS) {
			f.waitServer(pos, RouletteTableBlockEntity.class, be -> be.roundPhase().equals("betting"), "betting open", 600);
			f.click(RouletteLayout.spotId(spot));
			java.util.UUID me = f.server(s -> RealFlow.player(s).getUUID());
			f.waitServer(pos, RouletteTableBlockEntity.class, be -> Bets.totalStaked(be.betsOf(me)) > 0, "a bet on 17");
			Track tr = new Track("roulette jump " + jump);
			double seq0 = f.client(mc -> {
				double[] q = mc.gui.screen() instanceof RouletteScreen s ? s.poseProbe() : null;
				return q == null ? -1.0 : q[7];
			});
			lag(f);
			f.click(KeyMap.Ids.PRIMARY);
			boolean jumped = false;
			boolean fresh = false;
			double[] last = null;
			for (int k = 0; k < 1200; k++) {
				f.context().waitTick();
				double[] p = f.client(mc -> {
					if (!(mc.gui.screen() instanceof RouletteScreen s)) return null;
					double[] q = s.poseProbe();
					if (q != null && q[0] >= 0 && q[3] == 1) tr.frame(q[0], new double[] {q[1], q[2]}, s::ballAtProbe);
					return q;
				});
				if (p == null || p[0] < 0) continue;
				if (p[7] != seq0) fresh = true; // this round's spin, not the last one at rest
				if (!fresh) {
					tr.prev = null;
					tr.frames = 0;
					continue;
				}
				if (!jumped && p[0] > 3000) {
					jump(f, jump);
					jumped = true;
				}
				last = p;
				if (p[4] == 1 && p[0] > p[6] + 300) break;
			}
			unlag(f);
			f.context().waitTicks(10);
			judge(tr, false, jump, 40);
			f.waitServer(pos, RouletteTableBlockEntity.class, be -> be.roundPhase().equals("result") || be.roundPhase().equals("betting"), "the spin settled", 400);
			int result = f.server(s -> ((RouletteTableBlockEntity) s.overworld().getBlockEntity(pos)).sync().result());
			if (!jumped || last == null) fail("roulette: the spin never ran on screen (jump " + jump + ")");
			else if ((int) last[5] != result) fail("roulette: the ball rests in " + last[5] + ", the server drew " + result);
		}
		f.close();
	}

	// ---- Cards (shared clock + first-seen anchors: a late card still flies) --------------------------------------

	private void cards(RealFlow f) {
		for (double jump : JUMPS) {
			BlockPos pos = BlackjackV2ClientGameTests.fresh(f, BlackjackModule.TABLE.block());
			f.open(pos, BlackjackScreen.class);
			f.click(KeyMap.Ids.PRIMARY); // Sit down
			java.util.UUID me = f.server(s -> RealFlow.player(s).getUUID());
			f.waitServer(pos, BlackjackTableBlockEntity.class, t -> t.seats().seatOf(me).isPresent(), "seated");
			long[] chips = f.client(mc -> ((V2Chrome.Holder) mc.gui.screen()).chrome().bet.chips());
			int idx = 0;
			for (int i = 0; i < chips.length; i++) if (chips[i] == 25) idx = i;
			f.click(KeyMap.Ids.chip(idx));
			f.click(KeyMap.Ids.chip(idx));
			f.waitChrome(c -> c.primary.gate().enabled(), "Deal enabled");
			lag(f);
			f.click(KeyMap.Ids.PRIMARY); // Deal
			Map<Long, Float> lastU = new HashMap<>();
			Map<Long, Boolean> flew = new HashMap<>();
			int back = 0;
			int popped = 0;
			boolean jumped = false;
			int calm = 0;
			for (int k = 0; k < 600 && calm < 30; k++) {
				f.context().waitTick();
				Map<Long, Float> u = f.client(mc -> mc.gui.screen() instanceof BlackjackScreen s ? new HashMap<>(s.dealProbe()) : null);
				if (u == null) break;
				boolean moving = false;
				for (var e : u.entrySet()) {
					float v = e.getValue();
					Float was = lastU.get(e.getKey());
					if (was != null && v < was - 1e-6) back++;
					if (v > 0 && v < 1) flew.put(e.getKey(), true);
					if (was == null && v >= 1) popped++;
					if (v < 1) moving = true;
					lastU.put(e.getKey(), v);
				}
				if (!jumped && u.size() >= 2) {
					jump(f, jump);
					jumped = true;
				}
				calm = !u.isEmpty() && !moving && jumped ? calm + 1 : 0;
			}
			unlag(f);
			f.context().waitTicks(10);
			int published = f.server(s -> {
				var t = (BlackjackTableBlockEntity) s.overworld().getBlockEntity(pos);
				return t.beats() == null ? -1 : t.beats().publishedCount(s.overworld().getGameTime());
			});
			long landed = lastU.values().stream().filter(v -> v >= 1).count();
			long flown = flew.size();
			report.add("cards jump " + jump + ": " + lastU.size() + " cards, flew " + flown + ", popped in landed " + popped + ", back " + back + ", landed " + landed
				+ ", server published " + published);
			// +1 s: the shared clock may snap once, landing at most the cards in flight at that frame
			int allowed = jump > 0 ? 2 : 0;
			if (back > 0) fail("cards jump " + jump + ": a card slid back " + back + "x");
			if (popped > allowed) fail("cards jump " + jump + ": " + popped + " cards popped into their slots without a flight");
			if (lastU.size() < 4 || landed != lastU.size() || published != lastU.size()) {
				fail("cards jump " + jump + ": " + landed + "/" + lastU.size() + " cards landed, the server published " + published);
			}
			f.close();
		}
	}

	// ---- PvP dice (shared clock: levelMs − start·50 on both screens) ---------------------------------------------

	private void dicePvp(RealFlow f) {
		if (!f.client(mc -> DiceScreenProbe.isDice(mc.gui.screen()))) {
			f.server(s -> {
				RealFlow.player(s).getInventory().setItem(0, new ItemStack(ExtrasModule.DICE, 1));
				return null;
			});
			f.context().waitTicks(4);
			f.context().runOnClient(mc -> {
				mc.player.getInventory().setSelectedSlot(0);
				mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND);
			});
			f.context().waitFor(mc -> DiceScreenProbe.isDice(mc.gui.screen()), RealFlow.TIMEOUT);
			f.context().waitTicks(8);
		}
		int[] you = {5, 4};
		int[] them = {2, 1};
		for (double jump : JUMPS) {
			long start = SyncLead.startTick(f.server(s -> s.overworld().getGameTime()), SyncLead.PVP_DICE);
			CompoundTag r = pvp(start, you, them);
			lag(f);
			f.context().runOnClient(mc -> DiceScreenProbe.deliver(mc.gui.screen(), r));
			Track tr = new Track("dice pvp jump " + jump);
			boolean jumped = false;
			double end = DuelTimeline.endMs(1);
			int[] top = null;
			for (int k = 0; k < 400; k++) {
				f.context().waitTick();
				Object[] snap = f.client(mc -> {
					var s = mc.gui.screen();
					if (!DiceScreenProbe.isDice(s)) return null;
					double t = DiceScreenProbe.t(s);
					if (t < 0) return new Object[] {t};
					tr.frame(t, pose(s, t), tau -> pose(s, tau));
					return new Object[] {t, null, new int[] {(int) DiceScreenProbe.die(s, 0, 0, t)[6], (int) DiceScreenProbe.die(s, 0, 1, t)[6]}};
				});
				if (snap == null) break;
				double t = (double) snap[0];
				if (t < 0) continue;
				top = (int[]) snap[2];
				if (!jumped && t > 800) {
					jump(f, jump);
					jumped = true;
				}
				if (t > end + 200) break;
			}
			unlag(f);
			f.context().waitTicks(10);
			judge(tr, false, jump, 20);
			if (!jumped || top == null) fail("dice pvp: the duel never ran on screen (jump " + jump + ")");
			else if (!Arrays.equals(top, you)) fail("dice pvp: this screen's dice rest on " + Arrays.toString(top) + ", the server rolled " + Arrays.toString(you));
		}
		f.close();
	}

	/** Both pairs' drawn centres (layout px) at duel ms {@code t}. */
	private static double[] pose(net.minecraft.client.gui.screens.Screen s, double t) {
		double[] out = new double[8];
		for (int o = 0; o < 2; o++) {
			for (int d = 0; d < 2; d++) {
				double[] v = DiceScreenProbe.die(s, o, d, t);
				out[o * 4 + d * 2] = v[0];
				out[o * 4 + d * 2 + 1] = v[1];
			}
		}
		return out;
	}

	/** A one-round PvP result as the server sends it (side a, this player wins), starting at {@code start}. */
	private CompoundTag pvp(long start, int[] you, int[] them) {
		CompoundTag r = new CompoundTag();
		r.putInt("seq", ++seq);
		r.putInt("seed", 4242 + seq);
		r.putString("mode", "pvp");
		r.putString("side", "a");
		r.putLong("start", start);
		r.putString("opponent", "Bob");
		ListTag list = new ListTag();
		CompoundTag rt = new CompoundTag();
		rt.put("you", new IntArrayTag(you));
		rt.put("them", new IntArrayTag(them));
		list.add(rt);
		r.put("rounds", list);
		r.put("you", new IntArrayTag(you));
		r.put("them", new IntArrayTag(them));
		r.putString("outcome", "win");
		r.putLong("net", 10);
		return r;
	}
}
