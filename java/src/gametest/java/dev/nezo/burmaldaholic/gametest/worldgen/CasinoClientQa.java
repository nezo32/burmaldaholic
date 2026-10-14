package dev.nezo.burmaldaholic.gametest.worldgen;

import dev.nezo.burmaldaholic.client.input.Inputs;
import dev.nezo.burmaldaholic.gametest.worldgen.CasinoQa.Placed;
import dev.nezo.burmaldaholic.gametest.worldgen.CasinoQa.Spot;
import dev.nezo.burmaldaholic.gametest.worldgen.CasinoQa.Target;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.Vec3;

/**
 * Client-side casino QA helpers (lane CB-5): placing the casino under test in a client test world, walking the
 * player with real key input along a path computed on the server, right-clicking blocks with the mouse, and
 * photographing a building. Every wait has a timeout (client GameTest rules).
 */
public final class CasinoClientQa {
	/** Ticks a single waypoint may take before the walk counts as stuck. */
	static final int STEP_TIMEOUT = 50;

	private CasinoClientQa() {}

	/** v0.2 tudor if it exists (or the requested style), else the v0.1 plains village casino. */
	public static Target defaultTarget(MinecraftServer server, String style) {
		List<Target> standalone = CasinoQa.standalone(server);
		for (Target t : standalone) {
			if (t.name().equals("casino_" + style)) {
				return t;
			}
		}
		if (!standalone.isEmpty()) {
			return standalone.getFirst();
		}
		return CasinoQa.legacy().stream().filter(t -> t.name().equals("village_casino_plains")).findFirst()
			.orElse(CasinoQa.legacy().getFirst());
	}

	public static ServerPlayer player(MinecraftServer server) {
		return server.getPlayerList().getPlayers().getFirst();
	}

	/** Places the target on the server (test setup), {@code at} = x/z of the placement. */
	public static Placed place(TestSingleplayerContext world, Target t, BlockPos at, int turns) {
		return world.getServer().computeOnServer(server -> CasinoQa.place(server.overworld(), t, at, turns));
	}

	// ---- paths ---------------------------------------------------------------------------------------------------------

	/** Walkable = feet cell passable, head free, something to stand on. */
	static boolean stand(ServerLevel level, BlockPos p) {
		return CasinoQa.stand(level, p);
	}

	/**
	 * Shortest walk (4-neighbour, steps of ±1 block) from {@code from} to {@code to} inside {@code area}; empty if the
	 * target cannot be reached on foot.
	 */
	public static Optional<List<BlockPos>> path(ServerLevel level, BlockPos from, BlockPos to, BoundingBox area) {
		Map<BlockPos, BlockPos> prev = new HashMap<>();
		ArrayDeque<BlockPos> queue = new ArrayDeque<>();
		BlockPos start = from;
		if (!stand(level, start) && stand(level, start.above())) {
			start = start.above();
		}
		prev.put(start, start);
		queue.add(start);
		while (!queue.isEmpty()) {
			BlockPos c = queue.poll();
			if (c.equals(to)) {
				List<BlockPos> out = new ArrayList<>();
				for (BlockPos p = c; !p.equals(start); p = prev.get(p)) {
					out.add(p);
				}
				java.util.Collections.reverse(out);
				return Optional.of(out);
			}
			for (Direction d : Direction.Plane.HORIZONTAL) {
				for (int dy : new int[] {0, 1, -1}) {
					BlockPos n = c.relative(d).above(dy);
					if (!area.isInside(n) || prev.containsKey(n) || !stand(level, n)) {
						continue;
					}
					if (dy == 1 && !level.getBlockState(c.above(2)).getCollisionShape(level, c.above(2)).isEmpty()) {
						continue; // no head room to jump up
					}
					if (dy == -1 && !CasinoQa.walkable(level, c.relative(d))) {
						continue; // the step down must be open at the current height
					}
					prev.put(n, c);
					queue.add(n);
					break;
				}
			}
		}
		return Optional.empty();
	}

	/** A walkable cell outside the building, {@code distance} blocks in front of the cell nearest to {@code toward}. */
	public static BlockPos outside(ServerLevel level, BoundingBox box, BlockPos toward, int distance) {
		int cx = (box.minX() + box.maxX()) / 2;
		int cz = (box.minZ() + box.maxZ()) / 2;
		Direction side = Direction.getApproximateNearest(toward.getX() - cx, 0, toward.getZ() - cz);
		BlockPos edge = switch (side) {
			case NORTH -> new BlockPos(toward.getX(), toward.getY(), box.minZ() - distance);
			case SOUTH -> new BlockPos(toward.getX(), toward.getY(), box.maxZ() + distance);
			case WEST -> new BlockPos(box.minX() - distance, toward.getY(), toward.getZ());
			default -> new BlockPos(box.maxX() + distance, toward.getY(), toward.getZ());
		};
		int y = CasinoQa.groundY(level, edge.getX(), edge.getZ()) + 1;
		return new BlockPos(edge.getX(), y, edge.getZ());
	}

	// ---- walking with keys -------------------------------------------------------------------------------------------

	/**
	 * Walks the player along {@code path} by holding W and turning towards each waypoint (jump on a step up). Returns
	 * null when the last waypoint was reached, else where the player got stuck.
	 */
	public static String walk(ClientGameTestContext context, List<BlockPos> path) {
		try {
			context.getInput().holdKey(o -> o.keyUp);
			for (BlockPos w : path) {
				Vec3 target = Vec3.atBottomCenterOf(w);
				int t = 0;
				while (true) {
					double[] pos = context.computeOnClient(mc -> {
						Vec3 p = mc.player.position();
						double dx = target.x - p.x;
						double dz = target.z - p.z;
						mc.player.setYRot((float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0));
						mc.player.setXRot(10f);
						return new double[] {dx, dz, target.y - p.y};
					});
					if (Math.abs(pos[0]) < 0.35 && Math.abs(pos[1]) < 0.35) {
						break;
					}
					if (pos[2] > 0.6) {
						context.getInput().pressKey(o -> o.keyJump);
					}
					if (++t > STEP_TIMEOUT) {
						return "stuck before " + w.toShortString() + " (" + String.format("%.2f, %.2f", pos[0], pos[1]) + " away)";
					}
					context.waitTick();
				}
			}
			return null;
		} finally {
			context.getInput().releaseKey(o -> o.keyUp);
			context.waitTicks(4);
		}
	}

	/** Walks from wherever the player stands to {@code to} (path computed on the server around {@code box}). */
	public static String walkTo(ClientGameTestContext context, TestSingleplayerContext world, BoundingBox box, BlockPos to) {
		Optional<List<BlockPos>> path = world.getServer().computeOnServer(server -> {
			ServerLevel level = server.overworld();
			return CasinoClientQa.path(level, player(server).blockPosition(), to, box.inflatedBy(14, 4, 14));
		});
		if (path.isEmpty()) {
			return "no walkable path to " + to.toShortString();
		}
		return walk(context, path.get());
	}

	// ---- mouse ---------------------------------------------------------------------------------------------------------

	/** Turns to the block and right-clicks it with the mouse (the vanilla use key path). */
	public static void rightClick(ClientGameTestContext context, BlockPos pos) {
		context.getInput().lookAt(pos);
		context.waitTicks(2);
		context.getInput().pressMouse(Inputs.MOUSE_RIGHT);
	}

	/** Centre of a widget in window pixels (GUI coords × fit scale × GUI scale). */
	public static double[] windowPoint(net.minecraft.client.Minecraft mc, net.minecraft.client.gui.components.AbstractWidget w) {
		float fit = mc.gui.screen() instanceof dev.nezo.burmaldaholic.client.ui.FitScaled f ? f.fitScale() : 1f;
		double s = mc.getWindow().getGuiScale() * fit;
		return new double[] {(w.getX() + w.getWidth() / 2.0) * s, (w.getY() + w.getHeight() / 2.0) * s};
	}

	public static String key(net.minecraft.network.chat.Component c) {
		return c.getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents t ? t.getKey() : c.getString();
	}

	/** {@code waitFor} that returns false at the timeout instead of failing the test (the caller decides). */
	public static boolean safeWait(ClientGameTestContext context, java.util.function.Predicate<net.minecraft.client.Minecraft> p, int ticks) {
		try {
			context.waitFor(p, ticks);
			return true;
		} catch (RuntimeException | AssertionError e) {
			return false;
		}
	}

	public static void guiScale(ClientGameTestContext context, int scale) {
		context.runOnClient(mc -> {
			mc.options.guiScale().set(scale);
			mc.resizeGui();
		});
		context.waitTicks(3);
	}

	public static void closeScreen(ClientGameTestContext context) {
		// the player's own close (sends the container-close packet); never ESC: a screen that already closed would
		// turn the key into the pause menu, which pauses the integrated server (and deadlocks the world close)
		context.runOnClient(mc -> {
			if (mc.player != null && mc.gui.screen() instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?>) {
				mc.player.closeContainer();
			}
			if (mc.gui.screen() != null) {
				mc.gui.setScreen(null);
			}
		});
		safeWait(context, mc -> mc.gui.screen() == null, 40);
	}

	// ---- photos --------------------------------------------------------------------------------------------------------

	/** The side of the box the entrance is on: the side nearest to the cashier (the entrance piece holds it). */
	public static Direction front(Placed p) {
		if (p.rotation() >= 0) {
			return dev.nezo.burmaldaholic.worldgen.CasinoStructures.rotation(p.rotation()).rotate(Direction.SOUTH);
		}
		BoundingBox b = p.box();
		BlockPos ref = p.spots().stream().filter(s -> s.game().family() == dev.nezo.burmaldaholic.worldgen.logic.blueprint.Game.Family.CASHIER)
			.map(Spot::pos).findFirst().orElse(b.getCenter());
		int dn = ref.getZ() - b.minZ();
		int ds = b.maxZ() - ref.getZ();
		int dw = ref.getX() - b.minX();
		int de = b.maxX() - ref.getX();
		int m = Math.min(Math.min(dn, ds), Math.min(dw, de));
		return m == ds ? Direction.SOUTH : m == dn ? Direction.NORTH : m == de ? Direction.EAST : Direction.WEST;
	}

	/** A camera pose: position + yaw/pitch, built from the box and the front side. */
	public record Shot(String view, Vec3 eye, float yaw, float pitch) {}

	public static List<Shot> vantagePoints(Placed p, int floorY) {
		BoundingBox b = p.box();
		Direction f = front(p);
		Vec3 c = new Vec3((b.minX() + b.maxX() + 1) / 2.0, floorY, (b.minZ() + b.maxZ() + 1) / 2.0);
		double halfDepth = (f.getAxis() == Direction.Axis.Z ? b.getZSpan() : b.getXSpan()) / 2.0;
		double halfWidth = (f.getAxis() == Direction.Axis.Z ? b.getXSpan() : b.getZSpan()) / 2.0;
		Vec3 out = new Vec3(f.getStepX(), 0, f.getStepZ());
		Vec3 side = new Vec3(f.getClockWise().getStepX(), 0, f.getClockWise().getStepZ());
		List<Shot> shots = new ArrayList<>();
		Vec3 frontEye = c.add(out.scale(halfDepth + 28)).add(0, 1.62 + 4, 0);
		shots.add(look("front", frontEye, c.add(0, 5, 0)));
		Vec3 aerial = c.add(out.scale(halfDepth + 22)).add(side.scale(halfWidth + 22)).add(0, 30, 0);
		shots.add(look("aerial", aerial, c));
		Vec3 door = c.add(out.scale(halfDepth - 2)).add(0, 1.62 + 0.5, 0);
		shots.add(look("door_in", door, c.add(out.scale(-halfDepth)).add(0, 1.5, 0)));
		Vec3 back = c.add(out.scale(-halfDepth + 3)).add(side.scale(halfWidth * 0.4)).add(0, 1.62 + 1.5, 0);
		shots.add(look("floor_to_door", back, c.add(out.scale(halfDepth)).add(0, 1, 0)));
		Vec3 corner = c.add(side.scale(halfWidth - 2)).add(out.scale(-halfDepth * 0.3)).add(0, 1.62 + 2.5, 0);
		shots.add(look("bar_side", corner, c.add(side.scale(-halfWidth)).add(0, 1, 0)));
		return shots;
	}

	static Shot look(String view, Vec3 eye, Vec3 at) {
		double dx = at.x - eye.x;
		double dy = at.y - eye.y;
		double dz = at.z - eye.z;
		float yaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
		float pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
		return new Shot(view, eye, yaw, pitch);
	}

	/** Puts the (spectator) camera at the shot and takes a 1920×1080 screenshot after the chunks around render. */
	public static java.nio.file.Path photograph(ClientGameTestContext context, TestSingleplayerContext world, Shot s, String name) {
		world.getServer().runCommand(String.format(java.util.Locale.ROOT, "tp @p %.2f %.2f %.2f %.1f %.1f", s.eye().x, s.eye().y - 1.62, s.eye().z,
			s.yaw(), s.pitch()));
		context.waitTicks(4);
		context.runOnClient(mc -> {
			mc.player.setYRot(s.yaw());
			mc.player.setXRot(s.pitch());
		});
		// chunks + sections compile asynchronously: give the renderer time (bounded)
		safeWait(context, mc -> mc.levelRenderer.hasRenderedAllSections(), 400);
		context.waitTicks(40);
		return context.takeScreenshot(TestScreenshotOptions.of(name).withSize(1920, 1080).disableCounterPrefix());
	}
}
