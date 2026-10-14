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

	private static final DiceTray TRAY = buildTray();

	private CrapsThrow() {}

	/** The canonical craps tray (immutable, shared). */
	public static DiceTray tray() {
		return TRAY;
	}

	/** Rest zone of the dice (centres, tray units): the COME row between {@code ZONE_FROM} and the total pill. */
	public static DiceTray.Box restZone() {
		return TRAY.restZone(0);
	}

	/** The back wall (y = 0, interior +y). */
	public static DiceTray.Wall backWall() {
		return TRAY.walls().get(0);
	}

	private static DiceTray buildTray() {
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
		DiceTray.Box zone = new DiceTray.Box(zoneX0 + 1.5, comeTop + 1.5, totalX - 1.8, comeBot - 1.5);
		List<DiceTray.Box> keep = new ArrayList<>();
		// the total pill, the point caption and the come stacks: the rest of the COME row right of the zone
		keep.add(new DiceTray.Box(totalX - 0.6, comeTop, w, comeBot));
		// the puck spots: the top-right corner of each point box and of the Don't Come box
		for (int i = 0; i < 6; i++) {
			double x1 = u(CrapsFeltFractions.pointX1(i));
			keep.add(new DiceTray.Box(x1 - 2.6, 0, x1, 2.8));
		}
		double dc = u(CrapsFeltFractions.DONT_COME_W);
		keep.add(new DiceTray.Box(dc - 2.6, 0, dc, 2.8));
		return new DiceTray(G, floor, walls, List.of(), List.of(zone), keep, Optional.empty(), 2.4);
	}

	/** Dice home centre {x, y} (tray units) of {@code side}: the far end of the Pass line. */
	public static double[] home(int side) {
		double fy = CrapsFeltFractions.rowTop(4) + CrapsFeltFractions.PASS / 2;
		double fx = side == LEFT ? CrapsFeltFractions.HOME / 2 : 1 - CrapsFeltFractions.HOME / 2;
		return new double[] {u(fx), v(fy)};
	}

	/** Where the hand releases die 0 (dice home, nudged inboard so die 1 clears the rail). */
	public static ThrowSpec spec(int side) {
		double[] p = home(side);
		double x = side == LEFT ? Math.max(p[0], 2.6) : Math.min(p[0], ARENA_W - 2.6);
		return ThrowSpec.hand(side == LEFT ? 1 : 0, x, Math.min(p[1], ARENA_H - 2.2));
	}

	/**
	 * Solves the throw of {@code d1, d2} from {@code side} with the cosmetic {@code seed}. Callers cache the result
	 * (server: once per roll; clients: {@code DiceRollCache} keyed by the roll counter) — never in a render path.
	 */
	public static DiceRoll solve(int side, int seed, int d1, int d2) {
		return DiceSolver.solve(TRAY, spec(side), seed, new int[] {d1, d2}, targetMs(seed));
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
