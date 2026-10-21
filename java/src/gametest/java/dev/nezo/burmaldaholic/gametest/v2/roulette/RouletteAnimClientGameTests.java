package dev.nezo.burmaldaholic.gametest.v2.roulette;

import dev.nezo.burmaldaholic.client.anim.SmoothClock;
import dev.nezo.burmaldaholic.core.config.CasinoConfig;
import dev.nezo.burmaldaholic.core.rng.OddsService;
import dev.nezo.burmaldaholic.core.table.MultiBlockTableBlock;
import dev.nezo.burmaldaholic.core.table.parts.TableParts;
import dev.nezo.burmaldaholic.games.roulette.RouletteModule;
import dev.nezo.burmaldaholic.games.roulette.RouletteTableBlockEntity;
import dev.nezo.burmaldaholic.games.roulette.client.RouletteBoardRenderer;
import dev.nezo.burmaldaholic.games.roulette.client.RouletteScreen;
import dev.nezo.burmaldaholic.games.roulette.logic.RouletteSpin;
import dev.nezo.burmaldaholic.games.roulette.logic.RouletteSync;
import dev.nezo.burmaldaholic.gametest.ClientTestWorlds;
import dev.nezo.burmaldaholic.gametest.v2.RealFlow;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.random.RandomGenerator;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;

/**
 * Lane RO (ROULETTE-ANIM-SPEC §9.2; PHASE2B §4 RO): the roulette motion on the real screen and in the world. A real
 * spin is started by the player (bets placed through the block entity, Spin), the result forced to 26 for the shots
 * (a fixed fair-RNG stand-in for this test only), the server frozen ({@code /tick freeze}) mid-SPIN and the screen's
 * test clock set to each beat: launch, focus swap, orbit streak, hunting, deflector, rattle, rest + glow/loupe; then the
 * RESULT beats (dolly, rake, payout, flight to the balance) and reduced motion; at 427×240 (1280×720 GUI 3), 480×270,
 * 640×360, 960×540 and 1280×720 framed. Checks: the rotor never snaps at RESULT → BETTING (head frame Δ ≤ 1 at equal
 * time); in the world with a frozen AnimClock the ball is visible at 5 and 12 blocks and NOT at 24 blocks before rest,
 * and at 24 blocks it rests in the result pocket afterwards. Shots {@code p2b_ro_*}; report {@code p2b_ro_report.txt}.
 * Run alone with {@code BURMALDAHOLIC_CGT=RO}.
 */
public class RouletteAnimClientGameTests implements FabricClientGameTest {
	private static final int RESULT = 26;
	private final List<String> report = new ArrayList<>();
	private final List<String> failures = new ArrayList<>();

	@Override
	public void runTest(ClientGameTestContext context) {
		if (!RealFlow.enabled("RO")) return;
		context.runOnClient(mc -> mc.gui.setScreen(null));
		OddsService previous = OddsService.get();
		try (TestSingleplayerContext world = ClientTestWorlds.casino(context).create();
			ClientTestWorlds.Quiet quiet = ClientTestWorlds.quiet(context, world)) {
			context.waitTicks(20);
			world.getServer().runCommand("gamemode survival @a");
			world.getServer().runCommand("casino balance set @p 50000");
			world.getServer().runCommand("time set noon");
			world.getServer().runCommand("gamerule doDaylightCycle false");
			world.getServer().runOnServer(server -> CasinoConfig.chaos().enabled = false);
			OddsService.set(new OddsService(fixed(RESULT)));
			RealFlow f = new RealFlow(context, world);
			BlockPos table = arena(world);
			step("427", () -> screen(f, world, table, 1280, 720, 3, "427", true));
			step("480", () -> screen(f, world, table, 1920, 1080, 4, "480", true));
			step("640", () -> screen(f, world, table, 1920, 1080, 3, "640", false));
			step("960", () -> screen(f, world, table, 1920, 1080, 2, "960", true));
			step("1280 framed", () -> screen(f, world, table, 2560, 1440, 2, "1280", false));
			f.close();
			context.getInput().resizeWindow(1920, 1080);
			RouletteV2ClientGameTests.guiScale(context, 0);
			step("world", () -> inWorld(context, world, f, table));
			world.getServer().runCommand("tick unfreeze");
			f.close();
		} finally {
			OddsService.set(previous);
			RouletteScreen.testClock = null;
			RouletteScreen.testResultClock = null;
			RouletteBoardRenderer.testProbe = false;
			context.runOnClient(mc -> SmoothClock.Test.freezeLevelMs(Double.NaN));
			RouletteV2ClientGameTests.guiScale(context, 0);
			write(context);
		}
		if (!failures.isEmpty()) throw new AssertionError("roulette RO: " + String.join("; ", failures));
	}

	/** A fair-RNG stand-in that always draws {@code n} (this test's shots only; restored in finally). */
	private static RandomGenerator fixed(int n) {
		RandomGenerator base = RandomGenerator.of("L64X128MixRandom");
		return new RandomGenerator() {
			@Override
			public long nextLong() {
				return base.nextLong();
			}

			@Override
			public int nextInt(int bound) {
				return bound == 37 ? n : base.nextInt(bound);
			}
		};
	}

	private void step(String name, Runnable r) {
		try {
			r.run();
			report.add(name + ": ok");
		} catch (RuntimeException | AssertionError e) {
			failures.add(name + ": " + e);
			report.add(name + ": FAIL " + e);
		}
	}

	/** A cleared, lit floor with a FULL (3×2) roulette table facing north; the player stands north of it. */
	private static BlockPos arena(TestSingleplayerContext world) {
		return world.getServer().computeOnServer(server -> {
			ServerPlayer p = player(server);
			BlockPos base = p.blockPosition().offset(0, 0, 4);
			var level = server.overworld();
			for (int dx = -6; dx <= 6; dx++) {
				for (int dz = -30; dz <= 6; dz++) {
					for (int dy = 0; dy <= 5; dy++) level.setBlockAndUpdate(base.offset(dx, dy, dz), Blocks.AIR.defaultBlockState());
					level.setBlockAndUpdate(base.offset(dx, -1, dz), (dx + dz & 1) == 0 ? Blocks.DARK_OAK_PLANKS.defaultBlockState()
						: Blocks.SPRUCE_PLANKS.defaultBlockState());
				}
			}
			TableParts.placeFull(level, base, (MultiBlockTableBlock) RouletteModule.TABLE.block(), Direction.NORTH);
			if (level.getBlockEntity(base) instanceof RouletteTableBlockEntity be) be.setThemeForTesting(0);
			p.teleportTo(base.getX() + 0.5, base.getY(), base.getZ() - 1.5);
			return base;
		});
	}

	private static ServerPlayer player(MinecraftServer server) {
		return server.getPlayerList().getPlayers().getFirst();
	}

	private static RouletteTableBlockEntity be(MinecraftServer server, BlockPos pos) {
		return server.overworld().getBlockEntity(pos) instanceof RouletteTableBlockEntity be ? be : null;
	}

	/** Bets (win 26 straight, dozen 3 and black; lose red, 1st dozen, a split) then spins; waits for SPIN and freezes. */
	private static void betAndSpin(RealFlow f, TestSingleplayerContext world, BlockPos table) {
		world.getServer().runCommand("tick unfreeze");
		f.waitServer(table, RouletteTableBlockEntity.class, be -> "betting".equals(be.roundPhase()), "betting", 600);
		world.getServer().runOnServer(server -> {
			ServerPlayer p = player(server);
			p.teleportTo(table.getX() + 0.5, table.getY(), table.getZ() - 1.5);
			RouletteTableBlockEntity be = be(server, table);
			if (!be.isSeated(p)) be.sit(p);
			RouletteV2ClientGameTests.bet(be, p, "straight", 25, RESULT);
			RouletteV2ClientGameTests.bet(be, p, "dozen", 50, RouletteV2ClientGameTests.range(25, 36));
			RouletteV2ClientGameTests.bet(be, p, "split", 10, 8, 11);
			RouletteV2ClientGameTests.bet(be, p, "red", 25, RouletteV2ClientGameTests.redNumbers());
			RouletteV2ClientGameTests.bet(be, p, "dozen", 25, RouletteV2ClientGameTests.range(1, 12));
			RouletteV2ClientGameTests.bet(be, p, "corner", 5, 20, 21, 23, 24);
		});
		f.context().waitTicks(4);
	}

	private void screen(RealFlow f, TestSingleplayerContext world, BlockPos table, int fbW, int fbH, int gui, String w, boolean full) {
		ClientGameTestContext c = f.context();
		f.close();
		f.language("en_us");
		f.size(fbW, fbH, gui);
		RouletteScreen.testClock = null;
		RouletteScreen.testResultClock = null;
		betAndSpin(f, world, table);
		f.open(table, RouletteScreen.class);
		report.add(w + ": viewport " + f.viewport());
		f.shot("p2b_ro_roulette_betting_en_" + w);
		world.getServer().runOnServer(server -> be(server, table).onAction(player(server), "spin", new net.minecraft.nbt.CompoundTag()));
		f.waitServer(table, RouletteTableBlockEntity.class, be -> "spin".equals(be.roundPhase()), "spin", 200);
		c.waitTicks(3);
		world.getServer().runCommand("tick freeze");
		c.waitTicks(3);
		RouletteSpin sp = c.computeOnClient(mc -> mc.gui.screen() instanceof RouletteScreen s ? s.spin() : null);
		if (sp == null || !sp.solved()) throw new AssertionError("no solved spin on the screen");
		if (sp.result != RESULT) throw new AssertionError("forced result " + sp.result);
		report.add(w + String.format(": spin drop %.0f contact %.0f rest %.0f end %.0f deflector %s scatter %.1f hops %d", sp.dropMs(), sp.contactMs(),
			sp.restMs(), sp.spinEndMs(), sp.deflectorHit(), sp.scatter(), sp.rattle().hops()));
		List<double[]> beats = new ArrayList<>();
		beats.add(new double[] {300});
		beats.add(new double[] {800});
		beats.add(new double[] {1800});
		if (full) {
			beats.add(new double[] {sp.dropMs() - 100});
			if (sp.deflectorHit()) beats.add(new double[] {sp.deflectorMs() + 25});
			beats.add(new double[] {sp.contactMs() + 300});
		}
		beats.add(new double[] {sp.restMs() + 100});
		if (full) beats.add(new double[] {sp.restMs() + 400});
		String[] names = full ? (sp.deflectorHit() ? new String[] {"launch", "swap", "orbit", "hunt", "deflector", "rattle", "rest", "loupe"}
			: new String[] {"launch", "swap", "orbit", "hunt", "rattle", "rest", "loupe"}) : new String[] {"launch", "swap", "orbit", "rest"};
		for (int i = 0; i < beats.size(); i++) {
			double t = beats.get(i)[0];
			RouletteScreen.testClock = () -> t;
			c.waitTicks(2);
			f.shot("p2b_ro_roulette_" + names[i] + "_en_" + w);
		}
		// reduced motion: no focus, ball hidden until rest, then it fades into the pocket
		if (full) {
			c.runOnClient(mc -> dev.nezo.burmaldaholic.client.fx.FxSettings.update(d -> d.reduceMotion = true));
			RouletteScreen.testClock = () -> 2000.0;
			c.waitTicks(2);
			f.shot("p2b_ro_roulette_reduced_spin_en_" + w);
			RouletteScreen.testClock = () -> sp.restMs() + 400;
			c.waitTicks(2);
			f.shot("p2b_ro_roulette_reduced_rest_en_" + w);
			c.runOnClient(mc -> dev.nezo.burmaldaholic.client.fx.FxSettings.update(d -> d.reduceMotion = false));
		}
		RouletteScreen.testClock = null;
		// RESULT beats at fixed local times
		world.getServer().runCommand("tick unfreeze");
		f.waitServer(table, RouletteTableBlockEntity.class, be -> "result".equals(be.roundPhase()), "result", 400);
		world.getServer().runCommand("tick freeze");
		c.waitTicks(3);
		double[] rBeats = full ? new double[] {200, 500, 850, 1300, 1700, 2400} : new double[] {500, 1300};
		String[] rNames = full ? new String[] {"return", "dolly", "rake", "payout", "fly", "banner"} : new String[] {"dolly", "payout"};
		for (int i = 0; i < rBeats.length; i++) {
			double r = rBeats[i];
			RouletteScreen.testResultClock = () -> r;
			c.waitTicks(2);
			f.shot("p2b_ro_roulette_result_" + rNames[i] + "_en_" + w);
		}
		RouletteScreen.testResultClock = null;
		// no head snap at RESULT → BETTING: the frame drawn at the same time on either side differs by ≤ 1
		if (full) {
			double t0 = sp.spinEndMs() + 2500;
			RouletteScreen.testClock = () -> t0;
			c.waitTicks(2);
			int before = c.computeOnClient(mc -> mc.gui.screen() instanceof RouletteScreen s ? s.lastHeadFrame() : -1);
			world.getServer().runCommand("tick unfreeze");
			f.waitServer(table, RouletteTableBlockEntity.class, be -> "betting".equals(be.roundPhase()), "betting", 200);
			world.getServer().runCommand("tick freeze");
			c.waitTicks(3);
			int after = c.computeOnClient(mc -> mc.gui.screen() instanceof RouletteScreen s ? s.lastHeadFrame() : -1);
			int d = Math.floorMod(after - before, 222);
			d = Math.min(d, 222 - d);
			report.add(w + ": head frame RESULT " + before + " → BETTING " + after + " (Δ " + d + ")");
			if (before < 0 || after < 0 || d > 1) failures.add(w + ": head snap Δ" + d);
			f.shot("p2b_ro_roulette_idle_after_en_" + w);
			RouletteScreen.testClock = null;
		}
		world.getServer().runCommand("tick unfreeze");
		f.close();
	}

	/** In-world LOD with a frozen animation clock: 5 / 12 / 24 blocks before rest, and 24 blocks after rest. */
	private void inWorld(ClientGameTestContext c, TestSingleplayerContext world, RealFlow f, BlockPos table) {
		RouletteBoardRenderer.testProbe = true;
		betAndSpin(f, world, table);
		world.getServer().runOnServer(server -> be(server, table).onAction(player(server), "spin", new net.minecraft.nbt.CompoundTag()));
		f.waitServer(table, RouletteTableBlockEntity.class, be -> "spin".equals(be.roundPhase()), "spin", 200);
		c.waitTicks(3);
		world.getServer().runCommand("tick freeze");
		world.getServer().runCommand("gamemode spectator @a"); // free camera for the views (no falling, no hand)
		c.waitTicks(3);
		RouletteSync sync = c.computeOnClient(mc -> mc.level.getBlockEntity(table) instanceof RouletteTableBlockEntity be ? be.clientSync() : null);
		if (sync == null || sync.result() != RESULT) throw new AssertionError("client sync " + sync);
		RouletteSpin sp = sync.spin(sync.lastResult());
		double launchMs = sync.launchTick() * 50.0;
		double mid = (sp.dropMs() + sp.contactMs()) / 2; // the ball on the stator: moving, before rest
		freeze(c, launchMs + (sp.dropMs() - 600));
		standHigh(c, world, table);
		c.takeScreenshot("p2b_ro_world_orbit_high");
		freeze(c, launchMs + sp.contactMs() + 150);
		standHigh(c, world, table);
		c.takeScreenshot("p2b_ro_world_rattle_high");
		for (float d : new float[] {3f, 5f, 12f, 24f}) {
			freeze(c, launchMs + (d == 3f ? sp.contactMs() + 200 : mid));
			stand(c, world, table, d);
			c.takeScreenshot("p2b_ro_world_moving_" + (int) d + "m");
			double[] probe = c.computeOnClient(mc -> RouletteBoardRenderer.TEST_BALL.get(table.asLong()));
			boolean ball = probe != null && probe[0] > 0;
			report.add(String.format("world %dm (dist %.1f): ball %s", (int) d, probe == null ? -1 : probe[3], ball));
			if (d <= 12 && !ball) failures.add("no ball at " + d + " m while moving");
			if (d >= 24 && ball) failures.add("SPOILER: ball drawn at " + d + " m before rest");
		}
		for (float d : new float[] {24f, 5f}) {
			freeze(c, launchMs + sp.restMs() + 300);
			stand(c, world, table, d);
			c.takeScreenshot("p2b_ro_world_rest_" + (int) d + "m");
			double[] probe = c.computeOnClient(mc -> RouletteBoardRenderer.TEST_BALL.get(table.asLong()));
			boolean ball = probe != null && probe[0] > 0;
			double rel = probe == null ? Double.NaN : RouletteSpin.wrap360(probe[1]);
			double want = RouletteSpin.wrap360(RouletteSpin.pocketAngle(RESULT));
			double diff = Math.abs(((rel - want) % 360 + 540) % 360 - 180);
			report.add(String.format("world rest %dm: ball %s rel %.2f want %.2f", (int) d, ball, rel, want));
			if (!ball || diff > 0.01) failures.add("after rest at " + d + " m the ball must rest in " + RESULT + " (rel " + rel + ")");
		}
		// a raised view of the whole table (the one wheel in its bowl)
		freeze(c, launchMs + sp.restMs() + 300);
		standHigh(c, world, table);
		c.takeScreenshot("p2b_ro_world_rest_high");
		c.runOnClient(mc -> SmoothClock.Test.freezeLevelMs(Double.NaN));
		world.getServer().runCommand("tick unfreeze");
		f.waitServer(table, RouletteTableBlockEntity.class, be -> "result".equals(be.roundPhase()), "result", 400);
		stand(c, world, table, 3f);
		c.waitTicks(16);
		c.takeScreenshot("p2b_ro_world_result_rake_3m");
		RouletteBoardRenderer.testProbe = false;
		world.getServer().runCommand("gamemode survival @a");
	}

	private static void freeze(ClientGameTestContext c, double levelMs) {
		c.runOnClient(mc -> SmoothClock.Test.freezeLevelMs(levelMs));
	}

	/** World x / z of the wheel bowl's centre (FeltFrame wheel centre of the north-facing table). */
	private static double[] bowl(BlockPos table) {
		var f = dev.nezo.burmaldaholic.core.table.parts.FeltFrame.of(dev.nezo.burmaldaholic.core.table.parts.TableGame.ROULETTE,
			dev.nezo.burmaldaholic.core.table.parts.TablePart.MAIN);
		double[] w = dev.nezo.burmaldaholic.core.table.parts.TableFootprint.toWorld(f.wheelCx(), f.wheelCz(), Direction.NORTH.getStepX(),
			Direction.NORTH.getStepZ());
		return new double[] {table.getX() + w[0], table.getZ() + w[1]};
	}

	/** Stands {@code d} blocks north of the wheel bowl (the players' side), eyes at standing height, looking at it. */
	private static void stand(ClientGameTestContext c, TestSingleplayerContext world, BlockPos table, float d) {
		look(c, world, table, d, 0);
	}

	/** A raised close view into the bowl (about 1.2 blocks above it, 1.1 blocks away). */
	private static void standHigh(ClientGameTestContext c, TestSingleplayerContext world, BlockPos table) {
		look(c, world, table, 1.1f, 0.9f);
	}

	private static void look(ClientGameTestContext c, TestSingleplayerContext world, BlockPos table, float d, float up) {
		double[] b = bowl(table);
		double eye = 1.62;
		double feet = table.getY() + up;
		world.getServer().runCommand("tp @p " + b[0] + " " + feet + " " + (b[1] - d));
		float pitch = (float) Math.toDegrees(Math.atan2(feet + eye - (table.getY() + 0.95), d));
		for (int i = 0; i < 3; i++) {
			c.runOnClient(mc -> {
				if (mc.player != null) {
					mc.player.setYRot(0f);
					mc.player.setXRot(pitch);
					mc.player.setYHeadRot(0f);
				}
			});
			c.waitTicks(2);
		}
		c.waitTicks(4);
	}

	private void write(ClientGameTestContext context) {
		try {
			Path dir = context.computeOnClient(mc -> mc.gameDirectory.toPath().resolve("screenshots"));
			Files.createDirectories(dir);
			List<String> lines = new ArrayList<>(report);
			failures.forEach(f -> lines.add("FAIL " + f));
			Files.writeString(dir.resolve("p2b_ro_report.txt"), String.join("\n", lines) + "\n", StandardCharsets.UTF_8);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}
}
