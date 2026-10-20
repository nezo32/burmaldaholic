package dev.nezo.burmaldaholic.games.craps.present;

import static dev.nezo.burmaldaholic.games.craps.present.CrapsFeltFractions.ARENA_H;
import static dev.nezo.burmaldaholic.games.craps.present.CrapsFeltFractions.ARENA_W;
import static dev.nezo.burmaldaholic.games.craps.present.CrapsFeltFractions.u;
import static dev.nezo.burmaldaholic.games.craps.present.CrapsFeltFractions.v;

import dev.nezo.burmaldaholic.core.anim.dice.DiceRoll;
import dev.nezo.burmaldaholic.core.anim.dice.DiceSolver;
import dev.nezo.burmaldaholic.core.anim.dice.DiceTray;
import dev.nezo.burmaldaholic.core.anim.dice.ThrowSpec;
import dev.nezo.burmaldaholic.core.anim.motion.MotionRng;
import dev.nezo.burmaldaholic.core.anim.SeedMix;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The craps throw (CS §2–§3, PHASE2B §4 CM) on the shared dice kit: one canonical {@link DiceTray} in tray units
 * ({@link CrapsFeltFractions#ARENA_W} × {@link CrapsFeltFractions#ARENA_H}, 1 u = the die half-edge) built from the felt
 * fractions — the rubber-pyramid back wall every die must hit, three padded rails, the COME-row rest zone, keep-outs over
 * the total pill / caption / come stacks and the puck spots — and the shooter's hand at dice home on the Pass line.
 *
 * <p>The solve depends only on {@code (side, seed, faces)}, never on a viewer's pixels, so its {@link DiceRoll#restMs()}
 * is one number for the server (reveal timing), the shooter's screen, every spectator and the in-world board; each viewer
 * maps the arena through its own {@code StageMap} ({@link CrapsLayout#arenaMap()}, {@link CrapsBoardGeometry}). Pure.
 */
public final class CrapsThrow {
	/** Shooter side: the shooter stands at the right end of the Pass line (aims left) / the left end (aims right). */
	public static final int RIGHT = 0, LEFT = 1;
	/** Gravity, u/s² (70 die edges/s²: CS §2.2). */
	public static final double G = 140;
	/** Rubber pyramid pitch in u (4 art px of the wall tile at T2 ≈ 0.45 u). */
	public static final double PITCH = 0.44;
	/** Normal speed kept off the pyramids / the padded rails. */
	public static final double E_WALL = 0.58, E_RAIL = 0.5;

	/** One tray per puck position (point before the throw: 0 = OFF, else 4–10); the puck is a keep-out. */
	private static final DiceTray[] TRAYS = new DiceTray[11];

	static {
		for (int p : new int[] {0, 4, 5, 6, 8, 9, 10}) TRAYS[p] = buildTray(p);
	}

	private CrapsThrow() {}

	/** The canonical craps tray with the puck OFF (immutable, shared). */
	public static DiceTray tray() {
		return TRAYS[0];
	}

	/** The craps tray while the puck sits on {@code pointBefore} (0 = OFF in the Don't Come box). */
	public static DiceTray tray(int pointBefore) {
		DiceTray t = pointBefore >= 0 && pointBefore < TRAYS.length ? TRAYS[pointBefore] : null;
		return t != null ? t : TRAYS[0];
	}

	/** Rest zone of the dice (centres, tray units): the COME row between {@code ZONE_FROM} and the total pill. */
	public static DiceTray.Box restZone() {
		return TRAYS[0].restZone(0);
	}

	/** The back wall (y = 0, interior +y). */
	public static DiceTray.Wall backWall() {
		return TRAYS[0].walls().get(0);
	}

	private static DiceTray buildTray(int puckOn) {
		double w = ARENA_W;
		double h = ARENA_H;
		List<DiceTray.Wall> walls = new ArrayList<>();
		walls.add(new DiceTray.Wall(0, 0, w, 0, E_WALL, PITCH, true)); // back wall: rubber pyramids, every die hits it
		walls.add(new DiceTray.Wall(w, 0, w, h, E_RAIL, 0, false)); // right rail
		walls.add(new DiceTray.Wall(w, h, 0, h, E_RAIL, 0, false)); // shooter's rail
		walls.add(new DiceTray.Wall(0, h, 0, 0, E_RAIL, 0, false)); // left rail
		DiceTray.Poly floor = new DiceTray.Poly(new double[] {0, 0, w, 0, w, h, 0, h});
		double comeTop = v(CrapsFeltFractions.rowTop(1));
		double comeBot = v(CrapsFeltFractions.rowTop(2));
		double zoneX0 = u(CrapsFeltFractions.comeX() + CrapsFeltFractions.comeW() * CrapsFeltFractions.ZONE_FROM);
		double totalX = u(CrapsFeltFractions.comeX() + CrapsFeltFractions.comeW() * CrapsFeltFractions.TOTAL_AT);
		DiceTray.Box zone = new DiceTray.Box(zoneX0 + 1.4, comeTop + 1.5, totalX - 1.8, comeBot - 1.5);
		List<DiceTray.Box> keep = new ArrayList<>();
		// the total pill, the point caption and the come stacks: the rest of the COME row right of the zone
		keep.add(new DiceTray.Box(totalX - 0.6, comeTop, w, comeBot));
		// the puck where it sits during the throw: the top-right corner of its point box, or of the Don't Come box
		int[] points = {4, 5, 6, 8, 9, 10};
		double x1 = u(CrapsFeltFractions.DONT_COME_W);
		for (int i = 0; i < 6; i++) if (points[i] == puckOn) x1 = u(CrapsFeltFractions.pointX1(i));
		keep.add(new DiceTray.Box(x1 - 2.6, 0, x1, 2.8));
		return new DiceTray(G, floor, walls, List.of(), List.of(zone), keep, Optional.empty(), 2.4);
	}

	/** Centre distance of the two dice in the shooter's hand, u (a die edge is 2 u; the rattle turns them ±8°). */
	public static final double HAND_GAP = 2.4;

	/** Release centre of die 0 from the outer end of the Pass line: inboard so die 1 (released outboard) clears the rail. */
	static final double RELEASE_INSET = 3.6;

	/** The hand's spec of {@code side}: die 0 leaves at dice home, die 1 beside it (outboard), toward the back wall. */
	public static ThrowSpec spec(int side) {
		double y = v(CrapsFeltFractions.rowTop(4) + CrapsFeltFractions.PASS / 2);
		double x = side == LEFT ? RELEASE_INSET : ARENA_W - RELEASE_INSET;
		ThrowSpec h = ThrowSpec.hand(side == LEFT ? 1 : 0, x, y);
		// S1: the two dice sit HAND_GAP apart in the hand (the kit's 1.8 u is less than a die edge: two squared-up dice
		// at home and the rattle overlapped by 0.2 u)
		return new ThrowSpec(h.x(), h.y(), h.z0(), h.dir(), h.coneMin(), h.coneMax(), h.speedMin(), h.speedMax(), h.vzMin(), h.vzMax(), h.spinMin(),
			h.spinMax(), Math.copySign(HAND_GAP, h.side()), h.delayMin(), h.delayMax());
	}

	/** Release centre {x, y} (tray units) of {@code die} for {@code side}: where the stick leaves it and the throw starts. */
	public static double[] release(int side, int die) {
		ThrowSpec s = spec(side);
		double px = -StrictMath.sin(s.dir());
		double py = StrictMath.cos(s.dir());
		return new double[] {s.x() + px * s.side() * die, s.y() + py * s.side() * die};
	}

	/** Dice home centre {x, y} of {@code side}: between the two release points. */
	public static double[] home(int side) {
		double[] a = release(side, 0);
		double[] b = release(side, 1);
		return new double[] {(a[0] + b[0]) / 2, (a[1] + b[1]) / 2};
	}

	/**
	 * Solves the throw of {@code d1, d2} from {@code side} with the cosmetic {@code seed}. Callers cache the result
	 * (server: once per roll; clients: {@code DiceRollCache} keyed by the roll counter) — never in a render path.
	 */
	public static DiceRoll solve(int side, int seed, int d1, int d2, int pointBefore) {
		DiceTray tray = tray(pointBefore);
		ThrowSpec spec = spec(side);
		int[] faces = {d1, d2};
		int target = targetMs(seed);
		DiceRoll first = null;
		// a rare relaxed solve throws faster than a hand can (a blur across the felt): re-solve with the next derived
		// seed (deterministic, so every viewer and the server pick the same one); the path never depends on the faces
		for (int k = 0; k < RESEEDS; k++) {
			int s = k == 0 ? seed : SeedMix.mix(seed, k);
			DiceRoll r = DiceSolver.solve(tray, spec, s, faces, target);
			if (first == null) first = r;
			if (!r.fallback() && maxSpeed(r) <= MAX_SPEED) return r;
			if (maxSpeed(r) <= MAX_SPEED && k == RESEEDS - 1) return r;
		}
		return first;
	}

	/** Re-solve attempts (first seed + derived ones). */
	static final int RESEEDS = 4;
	/** Fastest a die may cross the felt, u/s (≈ 1.3 felt widths a second at T2). */
	public static final double MAX_SPEED = 110;

	/** Largest horizontal speed of either die over the throw (sampled every 2 ms; tests and the re-solve guard). */
	public static double maxSpeed(DiceRoll r) {
		dev.nezo.burmaldaholic.core.anim.dice.DieSample a = new dev.nezo.burmaldaholic.core.anim.dice.DieSample();
		dev.nezo.burmaldaholic.core.anim.dice.DieSample b = new dev.nezo.burmaldaholic.core.anim.dice.DieSample();
		double max = 0;
		for (int d = 0; d < r.dice(); d++) {
			r.sample(d, 0, a);
			for (double t = 2; t <= r.restMs(d); t += 2) {
				r.sample(d, t, b);
				max = Math.max(max, Math.hypot(b.x - a.x, b.y - a.y) / 0.002);
				a.set(b);
			}
		}
		return max;
	}

	/** Seeded rest time of the later die (CS §5: 1700 [1500–1950] ms); the solver meets it exactly, no time scaling. */
	public static int targetMs(int seed) {
		return (int) Math.round(MotionRng.of(seed, "CRAPS_REST", 0).range(TARGET_MIN, TARGET_MAX));
	}

	/** Range of {@link #targetMs}. */
	public static final int TARGET_MIN = 1560, TARGET_MAX = 1880;

	/**
	 * Shooter side from the shooter's seat: seats run left to right along the players' rail, so the left half throws
	 * from the left end ({@link #LEFT}), the right half from the right end. {@code seat < 0} (no seat) → {@link #RIGHT}.
	 */
	public static int sideOfSeat(int seat, int seats) {
		if (seat < 0 || seats <= 1) return RIGHT;
		return seat * 2 + 1 < seats ? LEFT : RIGHT;
	}
}
