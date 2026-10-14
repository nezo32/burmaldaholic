package dev.nezo.burmaldaholic.core.anim.dice;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Where dice are thrown (PHASE2B §3.3, DI §2.1, CS §2.2): geometry in <b>tray units</b> (1 u = the die half-edge
 * {@code h}; a die is 2 u wide), floor in the {@code x, y} plane, {@code z} up. A renderer multiplies by its own
 * {@code h} through a {@link dev.nezo.burmaldaholic.core.anim.motion.StageMap} (duel screen 16 art px, craps
 * {@code cell/2 − 1} GUI px, world 0.1 block). Immutable; build once and reuse (the solve result depends only on the
 * tray, the {@link ThrowSpec}, the seed and the target time, never on viewer pixels).
 *
 * <p>Craps (CM's {@code CrapsThrow}) builds its own tray with this record: the back wall as a {@link Wall} with
 * {@code pitch > 0} (rubber pyramids) and {@code mustHit = true}, keep-outs for chip stacks / puck / total pill, a
 * COME-row rest zone and a shooter-side {@link ThrowSpec#hand}.
 *
 * @param g         gravity, u/s² (constant for every arc of every die of this tray)
 * @param floor     floor polygon (convex), the die centres stay inside it
 * @param walls     walls; a die centre keeps {@link #R_PLANE} from each wall line it can reach
 * @param bumpers   round bumpers (the duel's "vs" badge)
 * @param restZones rest zone of die i = {@code restZones.get(min(i, size−1))} (centre positions)
 * @param keepOuts  boxes where a die may not land, slide or rest (it may fly over them)
 * @param divider   optional divider between the two pairs (a wall that may be banked off)
 * @param restSep   minimum Chebyshev distance between two resting dice centres (≥ 2.2: axis-aligned rest poses never
 *                  overlap)
 */
public record DiceTray(double g, Poly floor, List<Wall> walls, List<Bumper> bumpers, List<Box> restZones, List<Box> keepOuts,
		Optional<Wall> divider, double restSep) {
	/** In-plane contact radius of a die (rounded cube; between the face half-width 1 and the half-diagonal √2). */
	public static final double R_PLANE = 1.25;
	/** Duel arena: art px per tray unit (16-px die art ×2 = 32 px edge → h = 16). */
	public static final double DUEL_PX = 16;
	/** Duel arena size in tray units (360 × 140 art px). */
	public static final double DUEL_W = 360 / DUEL_PX;
	public static final double DUEL_H = 140 / DUEL_PX;
	/** Duel gravity (DI §2.2), u/s². */
	public static final double G_DUEL = 112;
	/** World lane gravity, u/s² (22 blocks/s²: a little stylised so a chest-high toss settles inside the duel beat). */
	public static final double G_WORLD = 220;
	/** World lane: blocks per tray unit (a 0.2-block die). */
	public static final double WORLD_BLOCKS_PER_U = 0.1;

	public DiceTray {
		walls = List.copyOf(walls);
		bumpers = List.copyOf(bumpers);
		restZones = List.copyOf(restZones);
		keepOuts = List.copyOf(keepOuts);
		if (restZones.isEmpty()) throw new IllegalArgumentException("no rest zone");
		if (g <= 0) throw new IllegalArgumentException("g " + g);
	}

	/** Duel lane side: LEFT = your pair (throws toward +x), RIGHT = the mirror image {@code x → DUEL_W − x}. */
	public enum Side {
		LEFT, RIGHT;

		public boolean mirrored() {
			return this == RIGHT;
		}
	}

	/**
	 * A wall segment {@code a → b}. The tray interior is on the side of the normal {@code n = (−(by−ay), bx−ax)/|ab|}
	 * (the direction rotated by +90° in x, y). Bank deflection: a smooth rail scatters the effective normal by ≤ ±15°,
	 * rubber pyramids ({@code pitch > 0}, the diamond spacing in u) by ≤ ±35° (CS §2.3 step 5).
	 *
	 * @param restitution normal speed kept at a bank (also the overall speed factor: 0.50 rail, 0.58 pyramid, 0.3
	 *                    soft divider)
	 * @param pitch       pyramid pitch in u; 0 = a smooth rail
	 * @param mustHit     every die must bank off this wall at least once (craps back wall)
	 */
	public record Wall(double ax, double ay, double bx, double by, double restitution, double pitch, boolean mustHit) {
		public double length() {
			return StrictMath.hypot(bx - ax, by - ay);
		}

		public double nx() {
			return -(by - ay) / length();
		}

		public double ny() {
			return (bx - ax) / length();
		}

		/** Signed distance of {@code (x, y)} from the wall line, positive inside. */
		public double distance(double x, double y) {
			return (x - ax) * nx() + (y - ay) * ny();
		}

		/** Maximum deflection of the effective normal from {@link #nx}/{@link #ny}, radians. */
		public double jitter() {
			return Math.toRadians(pitch > 0 ? 35 : 15);
		}

		/** The same wall mirrored by {@code x → w − x} (endpoints swapped so the normal still points inside). */
		public Wall mirrored(double w) {
			return new Wall(w - bx, by, w - ax, ay, restitution, pitch, mustHit);
		}
	}

	/** A round bumper (centre, radius in u). */
	public record Bumper(double cx, double cy, double r, double restitution) {
		public Bumper mirrored(double w) {
			return new Bumper(w - cx, cy, r, restitution);
		}
	}

	/** An axis-aligned box {@code [x0, x1] × [y0, y1]} in u. */
	public record Box(double x0, double y0, double x1, double y1) {
		public Box {
			if (x1 < x0 || y1 < y0) throw new IllegalArgumentException("box " + x0 + "," + y0 + "," + x1 + "," + y1);
		}

		public boolean contains(double x, double y) {
			return x >= x0 && x <= x1 && y >= y0 && y <= y1;
		}

		/** Distance from {@code (x, y)} to the box (0 inside). */
		public double distance(double x, double y) {
			double dx = Math.max(0, Math.max(x0 - x, x - x1));
			double dy = Math.max(0, Math.max(y0 - y, y - y1));
			return StrictMath.hypot(dx, dy);
		}

		public double width() {
			return x1 - x0;
		}

		public double height() {
			return y1 - y0;
		}

		public Box mirrored(double w) {
			return new Box(w - x1, y0, w - x0, y1);
		}
	}

	/** A convex polygon {@code {x0, y0, x1, y1, …}} in u. */
	public record Poly(double[] xy) {
		public Poly {
			xy = xy.clone();
			if (xy.length < 6 || xy.length % 2 != 0) throw new IllegalArgumentException("poly");
		}

		public int size() {
			return xy.length / 2;
		}

		public double x(int i) {
			return xy[2 * i];
		}

		public double y(int i) {
			return xy[2 * i + 1];
		}

		/** True when {@code (x, y)} is inside (or on) the polygon (either winding). */
		public boolean contains(double x, double y) {
			int n = size();
			int sign = 0;
			for (int i = 0; i < n; i++) {
				double ax = x(i);
				double ay = y(i);
				double bx = x((i + 1) % n);
				double by = y((i + 1) % n);
				double c = (bx - ax) * (y - ay) - (by - ay) * (x - ax);
				int s = c > 1e-12 ? 1 : c < -1e-12 ? -1 : 0;
				if (s == 0) continue;
				if (sign == 0) sign = s;
				else if (s != sign) return false;
			}
			return true;
		}

		public Poly mirrored(double w) {
			double[] m = new double[xy.length];
			int n = size();
			// reverse the order so the winding stays the same
			for (int i = 0; i < n; i++) {
				m[2 * i] = w - xy[2 * (n - 1 - i)];
				m[2 * i + 1] = xy[2 * (n - 1 - i) + 1];
			}
			return new Poly(m);
		}
	}

	/** Every wall of the tray including the divider (no allocation after the first call per tray is not promised). */
	public List<Wall> allWalls() {
		if (divider.isEmpty()) return walls;
		List<Wall> l = new ArrayList<>(walls);
		l.add(divider.get());
		return l;
	}

	/** Rest zone of die {@code die}. */
	public Box restZone(int die) {
		return restZones.get(Math.min(die, restZones.size() - 1));
	}

	/** This tray mirrored by {@code x → w − x} (the right duel lane). */
	public DiceTray mirrored(double w) {
		List<Wall> ws = walls.stream().map(x -> x.mirrored(w)).toList();
		List<Bumper> bs = bumpers.stream().map(x -> x.mirrored(w)).toList();
		List<Box> rz = restZones.stream().map(x -> x.mirrored(w)).toList();
		List<Box> ko = keepOuts.stream().map(x -> x.mirrored(w)).toList();
		return new DiceTray(g, floor.mirrored(w), ws, bs, rz, ko, divider.map(x -> x.mirrored(w)), restSep);
	}

	/**
	 * Dice Duel lane (DI §3.1), arena art px / 16. Felt octagon {@code oct(11)} of {@code dice_duel_arena_*}: rails at
	 * x 11 / 349, y 11 / 129 with 17.6-px chamfers; the left lane ends at the centre divider ridge (x 178); the "vs"
	 * badge (centre 180, 70, r 16) is a bumper on the divider. Rest zone = the lane box (x 40–163, y 58–112) inset by
	 * half a die (DI §3.1 inset a whole die; half keeps room for two dice side by side across the lane): centres
	 * x ∈ [52, 150], y ∈ [66, 104] px. {@link Side#RIGHT} is the exact mirror {@code x → 360 − x}.
	 */
	public static DiceTray duelLane(Side s) {
		double k = 17.6 / DUEL_PX;
		double x0 = 11 / DUEL_PX;
		double y0 = 11 / DUEL_PX;
		double y1 = 129 / DUEL_PX;
		double div = 178 / DUEL_PX;
		double e = 0.5;
		// clockwise on screen (y down): the interior is on the +90° side of every segment
		List<Wall> walls = List.of(
			new Wall(x0, y0 + k, x0 + k, y0, e, 0, false),        // top-left chamfer
			new Wall(x0 + k, y0, div, y0, e, 0, false),           // top rail
			new Wall(div, y1, x0 + k, y1, e, 0, false),           // bottom rail
			new Wall(x0 + k, y1, x0, y1 - k, e, 0, false),        // bottom-left chamfer
			new Wall(x0, y1 - k, x0, y0 + k, e, 0, false));       // left rail
		Wall divider = new Wall(div, y0, div, y1, 0.45, 0, false);
		Poly floor = new Poly(new double[] {x0 + k, y0, div, y0, div, y1, x0 + k, y1, x0, y1 - k, x0, y0 + k});
		List<Bumper> bumpers = List.of(new Bumper(180 / DUEL_PX, 70 / DUEL_PX, 16 / DUEL_PX, 0.62));
		List<Box> rest = List.of(new Box(52 / DUEL_PX, 66 / DUEL_PX, 150 / DUEL_PX, 104 / DUEL_PX));
		DiceTray left = new DiceTray(G_DUEL, floor, walls, bumpers, rest, List.of(), Optional.of(divider), 2.4);
		return s.mirrored() ? left.mirrored(DUEL_W) : left;
	}

	/**
	 * In-world duel lane (DI §6): open ground from the thrower's chest ({@code x = 0}) toward the opponent standing
	 * {@code lenBlocks} away ({@code x = L = lenBlocks / 0.1}); a soft divider plane at the midpoint between the two
	 * pairs; the pair rests on its own half near the midpoint. No rails.
	 */
	public static DiceTray worldLane(double lenBlocks) {
		if (lenBlocks <= 0.5) throw new IllegalArgumentException("len " + lenBlocks);
		double len = lenBlocks / WORLD_BLOCKS_PER_U;
		double mid = len / 2;
		double half = 9;
		Poly floor = new Poly(new double[] {-6, -half, mid, -half, mid, half, -6, half});
		Wall divider = new Wall(mid, -half, mid, half, 0.3, 0, false);
		double zx1 = mid - 2.2;
		double zx0 = Math.max(2.5, mid - Math.max(5, len * 0.18));
		List<Box> rest = List.of(new Box(zx0, -2.6, zx1, 2.6));
		return new DiceTray(G_WORLD, floor, List.of(), List.of(), rest, List.of(), Optional.of(divider), 2.4);
	}
}
