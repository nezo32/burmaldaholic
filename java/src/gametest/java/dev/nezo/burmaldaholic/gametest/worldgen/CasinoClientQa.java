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
		return world.getServer().computeOnServer(server -> {
			Placed p = CasinoQa.place(server.overworld(), t, at, turns);
			if (p.rotation() < 0) {
				MARQUEE.put(p.box(), marqueeSide(server.overworld(), p.box()));
			}
			return p;
		});
	}

	/** Entrance side of placed standalone structures (their rotation is unknown): see {@link #marqueeSide}. */
	private static final Map<BoundingBox, Direction> MARQUEE = new java.util.concurrent.ConcurrentHashMap<>();

	/**
	 * The side of a building whose outer 10-block band holds the most full-cube light blocks above the ground floor —
	 * the illuminated sign / marquee side (end rods and lanterns on the other facades do not count).
	 */
	static Direction marqueeSide(ServerLevel level, BoundingBox b) {
		Direction best = Direction.SOUTH;
		int bestN = -1;
		for (Direction d : Direction.Plane.HORIZONTAL) {
			int n = 0;
			for (BlockPos p : BlockPos.betweenClosed(b.minX(), b.minY() + 6, b.minZ(), b.maxX(), b.maxY(), b.maxZ())) {
				int edge = switch (d) {
					case NORTH -> p.getZ() - b.minZ();
					case SOUTH -> b.maxZ() - p.getZ();
					case WEST -> p.getX() - b.minX();
					default -> b.maxX() - p.getX();
				};
				if (edge <= 10) {
					var s = level.getBlockState(p);
					if (s.getLightEmission() >= 14 && s.isCollisionShapeFullBlock(level, p)) {
						n++;
					}
				}
			}
			if (n > bestN) {
				bestN = n;
				best = d;
			}
		}
		return best;
	}

	// ---- paths ---------------------------------------------------------------------------------------------------------

	/** Walkable = feet cell passable, head free, something to stand on. */
	static boolean stand(ServerLevel level, BlockPos p) {
		return CasinoQa.stand(level, p);
	}

	/** See {@link CasinoQa#floorPath}. */
	public static Optional<List<BlockPos>> path(ServerLevel level, BlockPos from, BlockPos to, BoundingBox area) {
		return CasinoQa.floorPath(level, from, to, area);
	}

	/** See {@link CasinoQa#outside}. */
	public static BlockPos outside(ServerLevel level, BoundingBox box, BlockPos toward, int distance) {
		return CasinoQa.outside(level, box, toward, distance);
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
						context.getInput().holdKey(o -> o.keyJump); // held: a tap between ticks can be missed
					} else {
						context.getInput().releaseKey(o -> o.keyJump);
					}
					if (++t > STEP_TIMEOUT) {
						return "stuck before " + w.toShortString() + " (" + String.format("%.2f, %.2f", pos[0], pos[1]) + " away; "
							+ context.computeOnClient(mc -> blockersNear(mc, target)) + ")";
					}
					context.waitTick();
				}
			}
			return null;
		} finally {
			context.getInput().releaseKey(o -> o.keyUp);
			context.getInput().releaseKey(o -> o.keyJump);
			context.waitTicks(4);
		}
	}

	/** What stops the player: its position and every block / entity whose collision touches its box nudged toward {@code target}. */
	static String blockersNear(net.minecraft.client.Minecraft mc, Vec3 target) {
		var p = mc.player;
		Vec3 dir = new Vec3(target.x - p.getX(), 0, target.z - p.getZ());
		dir = dir.lengthSqr() < 1e-6 ? Vec3.ZERO : dir.normalize().scale(0.15);
		net.minecraft.world.phys.AABB box = p.getBoundingBox().move(dir).deflate(0, 0.01, 0).move(0, 0.01, 0);
		List<String> out = new ArrayList<>();
		for (BlockPos c : BlockPos.betweenClosed(BlockPos.containing(box.minX, box.minY, box.minZ), BlockPos.containing(box.maxX, box.maxY, box.maxZ))) {
			var st = mc.level.getBlockState(c);
			var shape = st.getCollisionShape(mc.level, c);
			if (!shape.isEmpty() && shape.move(c.getX(), c.getY(), c.getZ()).toAabbs().stream().anyMatch(box::intersects)) {
				out.add(net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(st.getBlock()).getPath() + "@" + c.toShortString()
					+ (st.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING)
						? "/" + st.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING) : ""));
			}
		}
		for (var e : mc.level.getEntities(p, box.inflate(0.3))) {
			out.add("entity " + e.getType().toShortString() + "@" + String.format("%.2f,%.2f,%.2f", e.getX(), e.getY(), e.getZ()));
		}
		return String.format("player %.2f,%.2f,%.2f onGround %s; blockers %s", p.getX(), p.getY(), p.getZ(), p.onGround(), out);
	}

	/** Walks from wherever the player stands to {@code to} (path computed on the server around {@code box}). */
	public static String walkTo(ClientGameTestContext context, TestSingleplayerContext world, BoundingBox box, BlockPos to) {
		Object[] r = world.getServer().computeOnServer(server -> {
			ServerLevel level = server.overworld();
			BoundingBox area = box.inflatedBy(14, 4, 14);
			BlockPos at = player(server).blockPosition();
			Optional<List<BlockPos>> p = CasinoQa.floorPath(level, at, to, area);
			// the player may stand off a floor cell (on a stool, half over a step): start from the nearest floor cell
			for (int d = 1; p.isEmpty() && d <= 2; d++) {
				for (BlockPos c : BlockPos.betweenClosed(at.offset(-d, -1, -d), at.offset(d, 1, d))) {
					if (CasinoQa.stand(level, c)) {
						Optional<List<BlockPos>> q = CasinoQa.floorPath(level, c, to, area);
						if (q.isPresent()) {
							List<BlockPos> full = new ArrayList<>(List.of(c.immutable()));
							full.addAll(q.get());
							p = Optional.of(full);
							break;
						}
					}
				}
			}
			String why = p.isPresent() ? null : "player at " + at.toShortString() + " in " + level.getBlockState(at).getBlock()
				+ " on " + level.getBlockState(at.below()).getBlock() + ", target in " + level.getBlockState(to).getBlock() + " on "
				+ level.getBlockState(to.below()).getBlock();
			return new Object[] {p, why};
		});
		@SuppressWarnings("unchecked")
		Optional<List<BlockPos>> path = (Optional<List<BlockPos>>) r[0];
		if (path.isEmpty()) {
			return "no walkable path to " + to.toShortString() + " (" + r[1] + ")";
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
			if (mc.player != null && mc.gui.screen() instanceof net.minecraft.client.gui.screens.inventory.MenuAccess<?>) {
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
		if (MARQUEE.containsKey(p.box())) {
			return MARQUEE.get(p.box());
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
		if (halfDepth >= 17) {
			// big standalone casinos (the Grand Casino): the double-height hall seen from the lobby arch, high up
			Vec3 high = c.add(out.scale(-1.5)).add(0, 1.62 + 6, 0);
			shots.add(look("hall_high", high, c.add(out.scale(-halfDepth * 0.6)).add(0, 1, 0)));
		}
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
