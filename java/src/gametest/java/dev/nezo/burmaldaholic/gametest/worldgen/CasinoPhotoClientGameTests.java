package dev.nezo.burmaldaholic.gametest.worldgen;

import dev.nezo.burmaldaholic.gametest.ClientTestWorlds;
import dev.nezo.burmaldaholic.gametest.worldgen.CasinoQa.Placed;
import dev.nezo.burmaldaholic.gametest.worldgen.CasinoQa.Target;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;

/**
 * In-game photos of the casinos (lane CB-5, architecture §8.2): studio = the superflat test world at noon, the
 * building placed like worldgen does, the HUD hidden, FOV 70, a spectator camera at five vantage points computed from
 * the building's box and entrance side (front 28 blocks out, 45° aerial, door looking in, back of the floor looking at
 * the door, the bar side) plus the facade at midnight — 1920×1080 each, {@code jcasino_<casino>_<view>.png} in
 * {@code run/clientGameTest/screenshots/}. Default: the tudor casino (or the v0.1 plains casino before v0.2 is merged);
 * {@code -Dburmaldaholic.casinoPhotos=all} shoots every casino (styles, annexes, Parlor, Lounge), {@code =standalone} every
 * standalone structure (each Grand Casino variant), {@code =singles} every annex, the Parlor and the Lounge.
 */
public class CasinoPhotoClientGameTests implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext context) {
		String mode = System.getProperty("burmaldaholic.casinoPhotos", "");
		boolean all = "all".equals(mode);
		boolean standalone = "standalone".equals(mode); // every standalone structure (each Grand Casino variant)
		boolean singles = "singles".equals(mode); // every single-template casino: village annexes, Parlor, Lounge (CR-2)
		int fov = context.computeOnClient(mc -> mc.options.fov().get());
		int distance = context.computeOnClient(mc -> mc.options.renderDistance().get());
		context.runOnClient(mc -> mc.gui.setScreen(null));
		List<String> shots = new ArrayList<>();
		try (TestSingleplayerContext world = ClientTestWorlds.casino(context).create(); var q = ClientTestWorlds.quiet(context, world)) {
			context.waitTicks(20);
			context.runOnClient(mc -> {
				mc.gui.setScreen(null);
				mc.options.fov().set(70);
				mc.options.renderDistance().set(Math.max(8, distance));
				if (!mc.gui.hud.isHidden()) {
					mc.gui.hud.toggle();
				}
			});
			world.getServer().runCommand("gamemode spectator @p");
			world.getServer().runCommand("weather clear");
			List<Target> targets = world.getServer().computeOnServer(server -> {
				if (standalone && !CasinoQa.standalone(server).isEmpty()) {
					return CasinoQa.standalone(server);
				}
				if (singles) {
					return CasinoQa.v2Singles();
				}
				if (!all) {
					return List.of(CasinoClientQa.defaultTarget(server, "riviera"));
				}
				List<Target> out = new ArrayList<>(CasinoQa.standalone(server));
				out.addAll(CasinoQa.v2Singles());
				if (out.isEmpty()) {
					out.addAll(CasinoQa.legacy());
				}
				return out;
			});
			int k = 0;
			for (Target t : targets) {
				world.getServer().runCommand("time set noon");
				Placed p = CasinoClientQa.place(world, t, new BlockPos(k * 192, 0, 96), 2);
				k++;
				int floorY = p.spots().stream().mapToInt(s -> s.pos().getY()).min().orElse(p.box().minY() + 1);
				List<CasinoClientQa.Shot> views = CasinoClientQa.vantagePoints(p, floorY);
				for (CasinoClientQa.Shot s : views) {
					shots.add(CasinoClientQa.photograph(context, world, s, "jcasino_" + t.name() + "_" + s.view()).toString());
				}
				world.getServer().runCommand("time set midnight");
				context.waitTicks(10);
				shots.add(CasinoClientQa.photograph(context, world, views.getFirst(), "jcasino_" + t.name() + "_front_night").toString());
				shots.add(CasinoClientQa.photograph(context, world, views.get(1), "jcasino_" + t.name() + "_aerial_night").toString());
				world.getServer().runCommand("time set noon");
				CasinoQa.log("photos of {} ({} game blocks, entrance {}): {}", t, p.spots().size(), CasinoClientQa.front(p), shots);
			}
			world.getServer().runCommand("gamemode creative @p");
			context.waitTicks(5);
		} finally {
			context.runOnClient(mc -> {
				mc.options.fov().set(fov);
				mc.options.renderDistance().set(distance);
				if (mc.gui.hud.isHidden()) {
					mc.gui.hud.toggle();
				}
			});
		}
		if (shots.isEmpty()) {
			throw new AssertionError("no casino photographed");
		}
	}
}
